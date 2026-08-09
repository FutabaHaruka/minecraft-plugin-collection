package cn.licry.breedconsume.service;

import cn.licry.breedconsume.BreedConsumePlugin;
import com.pixelmonmod.pixelmon.Pixelmon;
import com.pixelmonmod.pixelmon.api.events.BreedEvent;
import com.pixelmonmod.pixelmon.api.events.EggHatchEvent;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.storage.PCStorage;
import com.pixelmonmod.pixelmon.api.storage.PokemonStorage;
import com.pixelmonmod.pixelmon.api.storage.StoragePosition;
import com.pixelmonmod.pixelmon.enums.EnumNature;
import com.pixelmonmod.pixelmon.entities.pixelmon.stats.StatsType;
import com.pixelmonmod.pixelmon.blocks.tileEntities.TileEntityRanchBlock;
import com.pixelmonmod.pixelmon.blocks.tileEntities.TileEntityRanchBlock.RanchPoke;
import com.pixelmonmod.pixelmon.storage.PlayerPartyStorage;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.eventhandler.Event;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** Pixelmon 8.4.2 API implementation, called through CatServer's ForgeEvent wrapper. */
public final class BreedConsumeService {
    private static final String PREFIX = "BreedConsume.";
    private static final String MARKER = PREFIX + "Enabled";
    private static final String VERSION = PREFIX + "Version";
    private static final String OWNER = PREFIX + "Owner";
    private static final String PARENT_1 = PREFIX + "Parent1";
    private static final String PARENT_2 = PREFIX + "Parent2";
    private static final String DIM = PREFIX + "Dimension";
    private static final String X = PREFIX + "X";
    private static final String Y = PREFIX + "Y";
    private static final String Z = PREFIX + "Z";
    private static final String STRICT_MARKER = PREFIX + "StrictEnabled";
    private static final String STRICT_VERSION = PREFIX + "StrictVersion";
    private static final String STRICT_TARGET_V = PREFIX + "StrictTargetV";
    private static final String STRICT_NATURE = PREFIX + "StrictNature";
    private static final String STRICT_IV_PREFIX = PREFIX + "StrictIV";
    private static final int STRICT_DATA_VERSION = 181;
    private static final int PREVIOUS_STRICT_DATA_VERSION = 180;
    private static final int LEGACY_STRICT_DATA_VERSION = 170;

    private static final int MAX_IV = 31;
    private static final String[] IV_NAMES = {"HP", "攻击", "防御", "特攻", "特防", "速度"};

    private final BreedConsumePlugin plugin;
    private final Random random = new Random();
    private final AtomicLong rejectedPairs = new AtomicLong();
    private final AtomicLong rejectedUpgradePairs = new AtomicLong();
    private final AtomicLong upgradedEggs = new AtomicLong();
    private final AtomicLong zeroTierEggs = new AtomicLong();
    private final AtomicLong consumedAtMake = new AtomicLong();
    private final AtomicLong legacyTagsCleaned = new AtomicLong();
    private final AtomicLong removedParents = new AtomicLong();
    private final AtomicLong failedTransactions = new AtomicLong();
    private volatile String lastAction = "never";

    public BreedConsumeService(BreedConsumePlugin plugin) {
        this.plugin = plugin;
    }

    public void handleAddPokemon(BreedEvent.AddPokemon event) {
        if (event.ranch == null || event.pokemon == null) return;
        UUID ranchOwner = event.ranch.getOwnerUUID();
        if (ranchOwner == null) ranchOwner = nativeUuid(event.player);
        PCStorage pc = requirePc(ranchOwner);
        Player ownerPlayer = player(event.player);
        boolean incomingShiny = event.pokemon.isShiny();

        for (RanchPoke entry : safeRanchEntries(event.ranch)) {
            Pokemon existing = entry.getPokemon(pc);
            if (existing == null) continue;

            if (plugin.isShinyPairingEnabled() && existing.isShiny() != incomingShiny) {
                rejectedPairs.incrementAndGet();
                lastAction = "AddPokemon denied mixed shiny: " + existing.getUUID()
                        + " / " + event.pokemon.getUUID();
                denyAddPokemon(event);
                plugin.sendMessage(ownerPlayer, plugin.getMismatchMessage());
                return;
            }

            if (plugin.isSynthesisUpgradeEnabled()
                    && createSynthesisPlan(existing, event.pokemon, ownerPlayer, "AddPokemon") == null) {
                denyAddPokemon(event);
                return;
            }
        }
    }

    public void handleMakeEgg(final BreedEvent.MakeEgg event) {
        if (event.ranch == null || event.parent1 == null || event.parent2 == null || event.getEgg() == null) {
            fail("MakeEgg missing ranch/egg/parent; native egg flow was left untouched.");
            return;
        }

        final Player ownerPlayer = player(event.owner);
        if (rejectMixedParents(event.parent1, event.parent2, ownerPlayer, "MakeEgg")) {
            event.setCanceled(true);
            return;
        }

        final SynthesisPlan plan;
        final int[] targetIvs;
        if (plugin.isSynthesisUpgradeEnabled()) {
            plan = createSynthesisPlan(event.parent1, event.parent2, ownerPlayer, "MakeEgg");
            if (plan == null) {
                event.setCanceled(true);
                return;
            }
            if (plugin.isParentRangeIvMode() || plugin.isStrictSynthesisMode() || plugin.isForceExactResultV()) {
                try {
                    targetIvs = buildUpgradeIvs(event.parent1, event.parent2, event.getEgg(), plan);
                    if (plugin.isParentRangeIvMode()) plan.targetV = countPerfectIvs(targetIvs);
                    applyExactIvs(event.getEgg(), targetIvs);
                    applyPowerItemLock(event.getEgg(), plan, targetIvs, "MakeEgg");
                } catch (Throwable error) {
                    fail("Unable to build synthesis egg IVs: " + rootMessage(error));
                    plugin.sendMessage(ownerPlayer, plugin.getSynthesisFailedMessage());
                    event.setCanceled(true);
                    return;
                }
            } else {
                targetIvs = null;
            }
        } else {
            plan = null;
            targetIvs = null;
        }

        if (plan != null) {
            try {
                applyConfiguredNature(event.parent1, event.parent2, event.getEgg(), plan);
                if (targetIvs != null) writeStrictEggState(event.getEgg(), targetIvs, plan.lockedNature);
            } catch (Throwable error) {
                fail("Unable to apply/store strict egg nature and IV state: " + rootMessage(error));
                plugin.sendMessage(ownerPlayer, plugin.getSynthesisFailedMessage());
                event.setCanceled(true);
                return;
            }
        }

        final boolean consumeParents = plugin.isConsumeParentsEnabled()
                && (plan == null || !plan.containsZeroParent || plugin.isConsumeZeroVParentsEnabled());
        final boolean needPostCommit = consumeParents || plan != null;
        if (!needPostCommit) return;

        final TileEntityRanchBlock ranch = event.ranch;
        final UUID eventOwner = event.owner;
        final UUID eggUuid = event.getEgg().getUUID();
        final UUID parent1 = event.parent1.getUUID();
        final UUID parent2 = event.parent2.getUUID();
        if (eggUuid == null || parent1 == null || parent2 == null || parent1.equals(parent2)) {
            fail("MakeEgg supplied invalid egg/parent UUIDs; native egg flow was left untouched.");
            return;
        }

        try {
            plugin.getServer().getScheduler().runTaskLater(plugin, new Runnable() {
                @Override
                public void run() {
                    completeSynthesisAfterEggCreated(ranch, eventOwner, eggUuid,
                            parent1, parent2, ownerPlayer, plan, targetIvs, consumeParents);
                }
            }, plugin.getParentConsumeDelayTicks());
            lastAction = "MakeEgg queued post-commit synthesis egg=" + eggUuid
                    + " target=" + (plan == null ? "native" : plan.targetV + "V")
                    + " consume=" + consumeParents + " parents=" + parent1 + "," + parent2;
        } catch (Throwable scheduleError) {
            fail("Unable to queue post-egg synthesis: " + rootMessage(scheduleError));
            plugin.sendMessage(ownerPlayer, plugin.getSynthesisFailedMessage());
            // Do not cancel MakeEgg here: Pixelmon must be allowed to complete native egg creation.
        }
    }

    /** Remove only legacy 1.2.x tracking keys before an egg leaves the ranch. */
    public void handleCollectEgg(BreedEvent.CollectEgg event) {
        Pokemon egg = event.getEgg();
        if (egg == null) return;
        enforceStrictEggState(egg, "CollectEgg");
        if (clearLegacyTracking(egg.getPersistentData())) {
            legacyTagsCleaned.incrementAndGet();
            lastAction = "CollectEgg cleared legacy parent tracking: " + egg.getUUID();
        }
        if (plugin.isShowEggIvsEnabled() && plugin.isShowEggIvsOnCollectEnabled()) {
            plugin.sendMessage(player(event.owner), formatMessage(plugin.getEggIvsMessage(), egg, null));
        }
    }

    /**
     * Compatibility cleanup only. Version 1.4.1 never consumes parents at hatch.
     * Old eggs created by 1.2.x may still contain BreedConsume.* keys, so remove
     * those keys without reading or acting on the stored parent UUIDs.
     */
    public void handleHatchPre(EggHatchEvent.Pre event) {
        Pokemon egg = event == null ? null : event.getPokemon();
        if (egg != null) enforceStrictEggState(egg, "EggHatchEvent.Pre");
    }

    public void handleHatchPost(EggHatchEvent.Post event) {
        Pokemon hatched = event.getPokemon();
        if (hatched == null) return;
        enforceStrictEggState(hatched, "EggHatchEvent.Post");
        clearStrictEggState(hatched.getPersistentData());
        if (clearLegacyTracking(hatched.getPersistentData())) {
            legacyTagsCleaned.incrementAndGet();
            lastAction = "EggHatchEvent.Post cleared legacy parent tracking: " + hatched.getUUID();
        }
    }

    private void completeSynthesisAfterEggCreated(final TileEntityRanchBlock ranch, UUID eventOwner,
                                                   UUID expectedEgg, UUID parent1, UUID parent2,
                                                   Player ownerPlayer, SynthesisPlan plan, int[] targetIvs,
                                                   boolean consumeParents) {
        Pokemon committedEgg;
        try {
            committedEgg = ranch.getEgg();
        } catch (Throwable error) {
            fail("Post-commit egg verification failed: " + rootMessage(error));
            return;
        }

        if (committedEgg == null || committedEgg.getUUID() == null
                || !expectedEgg.equals(committedEgg.getUUID())) {
            lastAction = "Post-commit check found no matching egg; parents preserved. expected="
                    + expectedEgg;
            plugin.getLogger().warning(lastAction);
            return;
        }

        if (targetIvs != null) {
            try {
                applyExactIvs(committedEgg, targetIvs);
                applyPowerItemLock(committedEgg, plan, targetIvs, "PostCommit");
            } catch (Throwable error) {
                consumeFailedAfterEgg(ranch, expectedEgg, ownerPlayer,
                        "Unable to commit synthesis IVs: " + rootMessage(error));
                return;
            }
        }

        if (plan != null && plan.lockedNature != null) {
            try {
                applyLockedNature(committedEgg, plan.lockedNature);
            } catch (Throwable error) {
                consumeFailedAfterEgg(ranch, expectedEgg, ownerPlayer,
                        "Unable to reapply locked nature after egg commit: " + rootMessage(error));
                return;
            }
        }
        if (targetIvs != null) {
            try {
                writeStrictEggState(committedEgg, targetIvs, plan == null ? null : plan.lockedNature);
            } catch (Throwable error) {
                consumeFailedAfterEgg(ranch, expectedEgg, ownerPlayer,
                        "Unable to persist strict egg state: " + rootMessage(error));
                return;
            }
        }

        try {
            clearTracking(committedEgg.getPersistentData());
        } catch (Throwable cleanupError) {
            plugin.getLogger().warning("Unable to clear legacy egg tracking keys, continuing: "
                    + rootMessage(cleanupError));
        }

        if (!consumeParents) {
            recordSynthesis(plan);
            lastAction = "Synthesis egg committed without parent consume egg=" + expectedEgg
                    + " target=" + (plan == null ? "native" : plan.targetV + "V");
            if (plan != null) notifyEggIvs(ownerPlayer, committedEgg, true, plan);
            queueStrictVerification(ranch, expectedEgg, 5L);
            queueStrictVerification(ranch, expectedEgg, 20L);
            return;
        }

        final UUID owner = ranch.getOwnerUUID() == null ? eventOwner : ranch.getOwnerUUID();
        if (owner == null) {
            consumeFailedAfterEgg(ranch, expectedEgg, ownerPlayer,
                    "Post-commit consume has null ranch owner");
            return;
        }

        final PCStorage pc;
        final PlayerPartyStorage party;
        try {
            pc = requirePc(owner);
            party = Pixelmon.storageManager.getParty(owner);
        } catch (Throwable error) {
            consumeFailedAfterEgg(ranch, expectedEgg, ownerPlayer,
                    "Unable to open owner storage: " + rootMessage(error));
            return;
        }

        StoredPokemon first = find(pc, party, parent1);
        StoredPokemon second = find(pc, party, parent2);
        if (first == null || second == null || !ranchContainsExactly(ranch, owner, parent1, parent2)) {
            int found = (first == null ? 0 : 1) + (second == null ? 0 : 1);
            consumeFailedAfterEgg(ranch, expectedEgg, ownerPlayer,
                    "Post-commit preflight found " + found + "/2 parents or ranch changed; UUIDs="
                            + parent1 + "," + parent2);
            return;
        }

        List<StoredPokemon> changed = new ArrayList<StoredPokemon>(2);
        try {
            changed.add(first);
            remove(first);
            changed.add(second);
            remove(second);

            if (find(pc, party, parent1) != null || find(pc, party, parent2) != null) {
                throw new IllegalStateException("storage verification still found a removed parent");
            }

            first.pokemon.setInRanch(false);
            second.pokemon.setInRanch(false);
            refresh(pc, party);
        } catch (Throwable error) {
            rollback(changed);
            try {
                refresh(pc, party);
            } catch (Throwable refreshError) {
                plugin.getLogger().severe("Storage refresh after rollback failed: "
                        + rootMessage(refreshError));
            }
            consumeFailedAfterEgg(ranch, expectedEgg, ownerPlayer,
                    "Post-commit parent transaction rolled back: " + rootMessage(error));
            return;
        }

        try {
            ranch.removeAllPokemon();
        } catch (Throwable cleanupError) {
            plugin.getLogger().severe("Parents were consumed but ranch cleanup failed for "
                    + parent1 + "," + parent2 + ": " + rootMessage(cleanupError));
        }

        consumedAtMake.incrementAndGet();
        removedParents.addAndGet(2L);
        recordSynthesis(plan);
        lastAction = "Post-commit synthesis completed egg=" + expectedEgg
                + " target=" + (plan == null ? "native" : plan.targetV + "V")
                + " parents=" + parent1 + "," + parent2;
        plugin.sendMessage(ownerPlayer, plugin.getParentsConsumedMessage());
        if (plan != null) notifyEggIvs(ownerPlayer, committedEgg, true, plan);
        queueStrictVerification(ranch, expectedEgg, 5L);
        queueStrictVerification(ranch, expectedEgg, 20L);
    }

    private void recordSynthesis(SynthesisPlan plan) {
        if (plan == null) return;
        if (plan.zeroTier) zeroTierEggs.incrementAndGet();
        else upgradedEggs.incrementAndGet();
    }

    private void consumeFailedAfterEgg(TileEntityRanchBlock ranch, UUID expectedEgg,
                                       Player ownerPlayer, String message) {
        fail(message);
        plugin.sendMessage(ownerPlayer, plugin.getConsumeFailedMessage());
        if (plugin.isFailClosedWhenConsumeFails()) {
            if (clearGeneratedEgg(ranch, expectedEgg)) {
                lastAction = message + "; generated egg removed (fail-closed).";
            } else {
                plugin.getLogger().severe("Fail-closed was requested but the generated egg could not be removed: "
                        + expectedEgg);
            }
        }
    }

    private boolean clearGeneratedEgg(TileEntityRanchBlock ranch, UUID expectedEgg) {
        if (ranch == null || expectedEgg == null) return false;
        try {
            Pokemon current = ranch.getEgg();
            if (current == null || current.getUUID() == null || !expectedEgg.equals(current.getUUID())) {
                return false;
            }
            java.lang.reflect.Field eggField = null;
            for (Class<?> currentType = ranch.getClass(); currentType != null; currentType = currentType.getSuperclass()) {
                try {
                    eggField = currentType.getDeclaredField("egg");
                    break;
                } catch (NoSuchFieldException ignored) { }
            }
            if (eggField == null) return false;
            eggField.setAccessible(true);
            eggField.set(ranch, null);
            invoke(ranch, new String[]{"markDirty", "func_70296_d"});
            return ranch.getEgg() == null;
        } catch (Throwable error) {
            plugin.getLogger().severe("Unable to remove generated egg after consume failure: "
                    + rootMessage(error));
            return false;
        }
    }

    private SynthesisPlan createSynthesisPlan(Pokemon first, Pokemon second, Player player, String stage) {
        int firstV = countPerfectIvs(first);
        int secondV = countPerfectIvs(second);
        int difference = Math.abs(firstV - secondV);
        boolean containsZero = firstV == 0 || secondV == 0;

        if (plugin.isStrictSynthesisMode()) {
            return createStrictEqualPlan(first, second, firstV, secondV, player, stage);
        }

        if (containsZero && !plugin.isAllowZeroVBreeding()) {
            rejectedUpgradePairs.incrementAndGet();
            lastAction = stage + " denied breeding with a 0V parent: " + firstV + "V/" + secondV + "V";
            plugin.sendMessage(player, replacePlanValues(plugin.getSynthesisZeroDisabledMessage(),
                    firstV, secondV, Math.max(firstV, secondV), -1));
            return null;
        }

        if (difference == 0) {
            if (!plugin.isAllowEqualParentV()) {
                rejectedUpgradePairs.incrementAndGet();
                lastAction = stage + " denied equal-V synthesis: " + firstV + "V/" + secondV + "V";
                plugin.sendMessage(player, replacePlanValues(plugin.getSynthesisMismatchMessage(),
                        firstV, secondV, firstV, -1));
                return null;
            }
        } else if (!plugin.isAllowAdjacentParentV()
                || difference > plugin.getMaximumParentVDifference()) {
            rejectedUpgradePairs.incrementAndGet();
            lastAction = stage + " denied V-gap synthesis: " + firstV + "V/" + secondV
                    + "V gap=" + difference;
            plugin.sendMessage(player, replacePlanValues(plugin.getSynthesisMismatchMessage(),
                    firstV, secondV, Math.max(firstV, secondV), -1));
            return null;
        }

        if (firstV == 0 && secondV == 0) {
            return finalizePlan(first, second,
                    new SynthesisPlan(firstV, secondV, 0, plugin.getZeroVResultV(), true, true),
                    player, stage);
        }

        int baseV;
        if (firstV == secondV) {
            baseV = firstV;
        } else if ("lower".equals(plugin.getResultVBasis())) {
            baseV = Math.min(firstV, secondV);
        } else {
            baseV = Math.max(firstV, secondV);
        }

        int maximumResult = plugin.getMaximumResultV();
        if (baseV >= maximumResult) {
            if (!plugin.isAllowMaximumVBreeding()) {
                rejectedUpgradePairs.incrementAndGet();
                lastAction = stage + " denied maximum synthesis level: " + firstV + "V/" + secondV + "V";
                plugin.sendMessage(player, replacePlanValues(plugin.getSynthesisMaximumMessage(),
                        firstV, secondV, baseV, maximumResult));
                return null;
            }
            return finalizePlan(first, second,
                    new SynthesisPlan(firstV, secondV, baseV, maximumResult,
                            maximumResult == 0, containsZero), player, stage);
        }

        if (baseV < plugin.getMinimumUpgradeParentV() || baseV > plugin.getMaximumUpgradeParentV()) {
            rejectedUpgradePairs.incrementAndGet();
            lastAction = stage + " denied synthesis outside configured parent range: "
                    + firstV + "V/" + secondV + "V base=" + baseV;
            plugin.sendMessage(player, replacePlanValues(plugin.getSynthesisMinimumMessage(),
                    firstV, secondV, baseV, -1));
            return null;
        }

        int targetV = clamp(baseV + plugin.getResultVOffset(), 0, maximumResult);
        return finalizePlan(first, second,
                new SynthesisPlan(firstV, secondV, baseV, targetV,
                        targetV == 0, containsZero), player, stage);
    }


    private SynthesisPlan createStrictEqualPlan(Pokemon first, Pokemon second, int firstV, int secondV,
                                                Player player, String stage) {
        boolean containsZero = firstV == 0 || secondV == 0;
        if (containsZero && !plugin.isAllowZeroVBreeding()) {
            rejectedUpgradePairs.incrementAndGet();
            lastAction = stage + " denied strict breeding with a 0V parent: " + firstV + "V/" + secondV + "V";
            plugin.sendMessage(player, replacePlanValues(plugin.getSynthesisZeroDisabledMessage(),
                    firstV, secondV, Math.max(firstV, secondV), -1));
            return null;
        }
        if (firstV != secondV) {
            rejectedUpgradePairs.incrementAndGet();
            lastAction = stage + " denied strict unequal-tier pair: " + firstV + "V/" + secondV + "V";
            plugin.sendMessage(player, replacePlanValues(plugin.getSynthesisMismatchMessage(),
                    firstV, secondV, Math.max(firstV, secondV), -1));
            return null;
        }
        if (firstV == 0) {
            return finalizePlan(first, second,
                    new SynthesisPlan(0, 0, 0, plugin.getZeroVResultV(),
                            plugin.getZeroVResultV() == 0, true), player, stage);
        }
        if (firstV < plugin.getMinimumUpgradeParentV() || firstV > plugin.getMaximumUpgradeParentV()) {
            rejectedUpgradePairs.incrementAndGet();
            lastAction = stage + " denied strict tier outside configured parent range: " + firstV + "V";
            plugin.sendMessage(player, replacePlanValues(plugin.getSynthesisMinimumMessage(),
                    firstV, secondV, firstV, -1));
            return null;
        }
        int maximumResult = plugin.getMaximumResultV();
        if (firstV >= maximumResult) {
            if (!plugin.isAllowMaximumVBreeding()) {
                rejectedUpgradePairs.incrementAndGet();
                lastAction = stage + " denied strict maximum tier: " + firstV + "V/" + secondV + "V";
                plugin.sendMessage(player, replacePlanValues(plugin.getSynthesisMaximumMessage(),
                        firstV, secondV, firstV, maximumResult));
                return null;
            }
            return finalizePlan(first, second,
                    new SynthesisPlan(firstV, secondV, firstV, maximumResult,
                            maximumResult == 0, false), player, stage);
        }
        int targetV = Math.min(maximumResult, firstV + 1);
        return finalizePlan(first, second,
                new SynthesisPlan(firstV, secondV, firstV, targetV, false, false), player, stage);
    }

    private SynthesisPlan finalizePlan(Pokemon first, Pokemon second, SynthesisPlan plan,
                                        Player player, String stage) {
        if (plan == null) return null;
        if (!validateItemLocks(first, second, plan, player, stage)) return null;
        return plan;
    }

    private boolean validateItemLocks(Pokemon first, Pokemon second, SynthesisPlan plan,
                                      Player player, String stage) {
        if (!plugin.isItemLockModeEnabled()) return true;
        if (plan.zeroTier && !plugin.isItemLockApplyToZeroTier()) return true;

        boolean firstEverstone = holdsEverstone(first);
        boolean secondEverstone = holdsEverstone(second);
        PowerItem firstPower = powerItem(first);
        PowerItem secondPower = powerItem(second);
        int everstoneCount = (firstEverstone ? 1 : 0) + (secondEverstone ? 1 : 0);
        int powerCount = (firstPower == null ? 0 : 1) + (secondPower == null ? 0 : 1);
        boolean dualPower = powerCount == 2;

        // A parent can hold only one item. In dual-Power-Item mode both held slots are
        // occupied by Power Items, so the Everstone requirement is intentionally waived.
        boolean invalid = plugin.isRequireAtLeastOnePowerItem() && powerCount == 0;
        invalid |= !plugin.isAllowTwoPowerItems() && powerCount > 1;
        invalid |= !dualPower && plugin.isRequireExactlyOneEverstone() && everstoneCount != 1;
        if (invalid) {
            rejectedUpgradePairs.incrementAndGet();
            String itemDiagnostic = " firstHeld={" + heldItemDiagnostic(first)
                    + "} secondHeld={" + heldItemDiagnostic(second) + "}";
            lastAction = stage + " denied item-lock combination: everstones=" + everstoneCount
                    + " powerItems=" + powerCount + " parents=" + plan.firstV + "V/" + plan.secondV + "V"
                    + itemDiagnostic;
            if (powerCount == 0) {
                plugin.getLogger().warning("Power Item (狗圈) was not recognized at " + stage + ":" + itemDiagnostic);
            }
            plugin.sendMessage(player, replacePlanValues(plugin.getItemLockRequiredMessage(),
                    plan.firstV, plan.secondV, plan.baseV, plan.targetV));
            return false;
        }

        int everstoneParent = 0;
        if (firstEverstone && secondEverstone) everstoneParent = random.nextBoolean() ? 1 : 2;
        else if (firstEverstone) everstoneParent = 1;
        else if (secondEverstone) everstoneParent = 2;

        if (!dualPower && plugin.isRequireDifferentLockParents() && everstoneParent != 0) {
            int singlePowerParent = firstPower != null ? 1 : (secondPower != null ? 2 : 0);
            if (singlePowerParent != 0 && everstoneParent == singlePowerParent) {
                rejectedUpgradePairs.incrementAndGet();
                lastAction = stage + " denied item locks on the same parent";
                plugin.sendMessage(player, replacePlanValues(plugin.getItemLockRequiredMessage(),
                        plan.firstV, plan.secondV, plan.baseV, plan.targetV));
                return false;
            }
        }

        if (!matchesParentRole(everstoneParent, plugin.getEverstoneParentRole(), plan.firstV, plan.secondV)) {
            rejectedUpgradePairs.incrementAndGet();
            lastAction = stage + " denied Everstone parent role: parent=" + everstoneParent
                    + " expected=" + plugin.getEverstoneParentRole();
            plugin.sendMessage(player, replacePlanValues(plugin.getItemLockRoleMismatchMessage(),
                    plan.firstV, plan.secondV, plan.baseV, plan.targetV));
            return false;
        }
        if (firstPower != null && !matchesParentRole(1, plugin.getPowerItemParentRole(), plan.firstV, plan.secondV)
                || secondPower != null && !matchesParentRole(2, plugin.getPowerItemParentRole(), plan.firstV, plan.secondV)) {
            rejectedUpgradePairs.incrementAndGet();
            lastAction = stage + " denied Power Item parent role(s): first=" + (firstPower != null)
                    + ", second=" + (secondPower != null) + ", expected=" + plugin.getPowerItemParentRole();
            plugin.sendMessage(player, replacePlanValues(plugin.getItemLockRoleMismatchMessage(),
                    plan.firstV, plan.secondV, plan.baseV, plan.targetV));
            return false;
        }

        plan.everstoneParent = everstoneParent;
        plan.itemLockApplied = everstoneParent != 0 || powerCount > 0;
        if (firstPower != null && !addPowerLock(first, firstPower, 1, plan, player, stage)) return false;
        if (secondPower != null && !addPowerLock(second, secondPower, 2, plan, player, stage)) return false;

        // Two identical Power Items target the same child stat. A single stat cannot hold
        // two different values; when both values differ, choose one source fairly. If the
        // values match (normally both are 31), both holders are recorded as contributors.
        plan.resolveDuplicatePowerStats(random, plugin.getLogger(), stage);
        return true;
    }

    private boolean addPowerLock(Pokemon source, PowerItem item, int parentIndex,
                                 SynthesisPlan plan, Player player, String stage) {
        int[] sourceIvs = source.getIVs().getArray();
        if (sourceIvs == null || sourceIvs.length != 6) {
            rejectedUpgradePairs.incrementAndGet();
            plugin.sendMessage(player, plugin.getSynthesisFailedMessage());
            return false;
        }
        int lockedIv = source.getIVs().getStat(item.statType);
        plan.setMessagePowerLock(item, parentIndex, lockedIv);
        if (plugin.isRequirePowerItemPerfectIv() && lockedIv != MAX_IV) {
            rejectedUpgradePairs.incrementAndGet();
            lastAction = stage + " denied non-perfect Power Item source: parent=" + parentIndex
                    + " " + item.id + " " + IV_NAMES[item.statIndex] + "=" + lockedIv;
            plugin.sendMessage(player, replaceLockValues(plugin.getPowerItemIvNotPerfectMessage(), plan));
            return false;
        }
        if (!plugin.isParentRangeIvMode() && plugin.isForceIvFromPowerItem() && plugin.isForceExactResultV()
                && lockedIv != MAX_IV && plan.targetV >= 6) {
            rejectedUpgradePairs.incrementAndGet();
            lastAction = stage + " denied impossible 6V result with non-perfect locked Power Item IV";
            plugin.sendMessage(player, replaceLockValues(plugin.getPowerItemIvNotPerfectMessage(), plan));
            return false;
        }
        plan.powerLocks.add(new PowerLock(parentIndex, item, lockedIv));
        return true;
    }

    private static boolean matchesParentRole(int parentIndex, String role, int firstV, int secondV) {
        if (parentIndex == 0 || role == null || "any".equals(role) || firstV == secondV) return true;
        int parentV = parentIndex == 1 ? firstV : secondV;
        if ("lower-v".equals(role)) return parentV == Math.min(firstV, secondV);
        if ("higher-v".equals(role)) return parentV == Math.max(firstV, secondV);
        return true;
    }

    /**
     * Optional nature override. "native" deliberately does nothing, allowing Pixelmon's
     * normal Everstone rule to choose the nature. Other modes are admin-selectable and
     * can optionally require the chosen parent to hold an Everstone.
     */
    private void applyConfiguredNature(Pokemon first, Pokemon second, Pokemon egg, SynthesisPlan plan) {
        if (first == null || second == null || egg == null || plan == null) return;

        if (plan.everstoneParent != 0 && plugin.isForceNatureFromEverstone()) {
            Pokemon source = plan.everstoneParent == 1 ? first : second;
            EnumNature nature = source.getBaseNature();
            if (nature == null) nature = source.getNature();
            if (nature != null) {
                applyLockedNature(egg, nature);
                plan.lockedNature = nature;
                plan.natureSource = plan.everstoneParent == 1 ? "父母1的不变之石" : "父母2的不变之石";
            }
            return;
        }

        String mode = plugin.getNatureInheritanceMode();
        if (mode == null || "native".equals(mode)) {
            plan.natureSource = "Pixelmon原版/不变石";
            return;
        }

        Pokemon source = null;
        String sourceLabel = mode;
        boolean firstStone = holdsEverstone(first);
        boolean secondStone = holdsEverstone(second);

        if ("everstone".equals(mode)) {
            if (firstStone && secondStone) source = random.nextBoolean() ? first : second;
            else if (firstStone) source = first;
            else if (secondStone) source = second;
            else return;
            sourceLabel = "不变石父母";
        } else if ("lower-v".equals(mode)) {
            source = chooseByV(first, second, plan.firstV, plan.secondV, false, firstStone, secondStone);
            sourceLabel = "低V父母";
        } else if ("higher-v".equals(mode)) {
            source = chooseByV(first, second, plan.firstV, plan.secondV, true, firstStone, secondStone);
            sourceLabel = "高V父母";
        } else if ("first-parent".equals(mode)) {
            source = first;
            sourceLabel = "父母1";
        } else if ("second-parent".equals(mode)) {
            source = second;
            sourceLabel = "父母2";
        }

        if (source == null) return;
        if (plugin.isNatureLockRequireEverstone() && !holdsEverstone(source)) return;

        EnumNature nature = source.getBaseNature();
        if (nature == null) nature = source.getNature();
        if (nature == null) return;
        applyLockedNature(egg, nature);
        plan.lockedNature = nature;
        plan.natureSource = sourceLabel;
    }

    private Pokemon chooseByV(Pokemon first, Pokemon second, int firstV, int secondV,
                              boolean higher, boolean firstStone, boolean secondStone) {
        if (firstV != secondV) {
            if (higher) return firstV > secondV ? first : second;
            return firstV < secondV ? first : second;
        }
        // Same tier has no lower/higher side. Let an Everstone disambiguate, then first.
        if (firstStone != secondStone) return firstStone ? first : second;
        return first;
    }

    private static boolean holdsEverstone(Pokemon pokemon) {
        String registry = heldItemRegistry(pokemon);
        if ("pixelmon:everstone".equals(registry) || registry.endsWith(":everstone")) return true;
        String identity = heldItemIdentity(pokemon);
        return identity.endsWith("everstone") || identity.contains("itemeverstone");
    }

    private static PowerItem powerItem(Pokemon pokemon) {
        if (pokemon == null) return null;

        /*
         * Primary path for Pixelmon 8.4.2: all six breeding "dog collars" are
         * EVAdjusting items. Read EVAdjusting.type.statAffected directly rather
         * than relying on Java object identity or a registry-name wrapper.
         */
        Object heldView = heldItemAsItemHeld(pokemon);
        PowerItem resolved = powerItemFromEvAdjusting(heldView, "getHeldItemAsItemHeld");
        if (resolved != null) return resolved;

        Object item = heldItemObject(pokemon);
        resolved = powerItemFromEvAdjusting(item, "raw-item");
        if (resolved != null) return resolved;

        // Reuse the hybrid-safe helper bundled in the previously patched Pixelmon
        // build when present. The plugin also works when that helper is absent.
        resolved = powerItemFromPatchedPixelmonHelper(item);
        if (resolved != null) return resolved;
        resolved = powerItemFromPatchedPixelmonHelper(heldView);
        if (resolved != null) return resolved;

        // Pixelmon singleton identity remains a fast, exact fallback.
        if (matchesPixelmonHeldItem(item, "powerWeight")) return new PowerItem("pixelmon:power_weight", "力量负重", StatsType.HP, "singleton");
        if (matchesPixelmonHeldItem(item, "powerBracer")) return new PowerItem("pixelmon:power_bracer", "力量护腕", StatsType.Attack, "singleton");
        if (matchesPixelmonHeldItem(item, "powerBelt")) return new PowerItem("pixelmon:power_belt", "力量腰带", StatsType.Defence, "singleton");
        if (matchesPixelmonHeldItem(item, "powerLens")) return new PowerItem("pixelmon:power_lens", "力量镜", StatsType.SpecialAttack, "singleton");
        if (matchesPixelmonHeldItem(item, "powerBand")) return new PowerItem("pixelmon:power_band", "力量束带", StatsType.SpecialDefence, "singleton");
        if (matchesPixelmonHeldItem(item, "powerAnklet")) return new PowerItem("pixelmon:power_anklet", "力量护踝", StatsType.Speed, "singleton");

        String registry = heldItemRegistry(pokemon);
        resolved = powerItemFromIdentifier(registry, "registry");
        if (resolved != null) return resolved;

        // Last-resort compatibility for unusual hybrid wrappers that hide both the
        // singleton identity and registry name.
        String identity = heldItemIdentity(pokemon);
        resolved = powerItemFromIdentifier(identity, "identity");
        if (resolved != null) return resolved;
        return null;
    }

    private static Object heldItemAsItemHeld(Pokemon pokemon) {
        if (pokemon == null) return null;
        try {
            return invoke(pokemon, new String[]{"getHeldItemAsItemHeld"});
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static PowerItem powerItemFromEvAdjusting(Object candidate, String resolver) {
        if (candidate == null) return null;
        Object type = reflectedField(candidate, "type");
        if (type == null) return null;

        Object affected = reflectedField(type, "statAffected");
        if (affected instanceof StatsType) {
            PowerItem result = powerItemFromStat((StatsType) affected, "ev-adjusting:" + resolver);
            if (result != null) return result;
        }

        String enumName;
        if (type instanceof Enum) enumName = ((Enum<?>) type).name();
        else enumName = String.valueOf(type);
        return powerItemFromIdentifier(enumName, "ev-adjusting-enum:" + resolver);
    }

    private static Object reflectedField(Object target, String name) {
        if (target == null || name == null) return null;
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                java.lang.reflect.Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                // Continue into the superclass; hybrid wrappers may subclass the item.
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static PowerItem powerItemFromPatchedPixelmonHelper(Object candidate) {
        if (candidate == null) return null;
        try {
            ClassLoader loader = candidate.getClass().getClassLoader();
            Class<?> helper = Class.forName("com.pixelmonmod.pixelmon.util.helpers.BreedPowerItemFix", false, loader);
            if (staticBoolean(helper, "isPowerWeight", candidate)) return new PowerItem("pixelmon:power_weight", "力量负重", StatsType.HP, "BreedPowerItemFix");
            if (staticBoolean(helper, "isPowerBracer", candidate)) return new PowerItem("pixelmon:power_bracer", "力量护腕", StatsType.Attack, "BreedPowerItemFix");
            if (staticBoolean(helper, "isPowerBelt", candidate)) return new PowerItem("pixelmon:power_belt", "力量腰带", StatsType.Defence, "BreedPowerItemFix");
            if (staticBoolean(helper, "isPowerLens", candidate)) return new PowerItem("pixelmon:power_lens", "力量镜", StatsType.SpecialAttack, "BreedPowerItemFix");
            if (staticBoolean(helper, "isPowerBand", candidate)) return new PowerItem("pixelmon:power_band", "力量束带", StatsType.SpecialDefence, "BreedPowerItemFix");
            if (staticBoolean(helper, "isPowerAnklet", candidate)) return new PowerItem("pixelmon:power_anklet", "力量护踝", StatsType.Speed, "BreedPowerItemFix");
        } catch (Throwable ignored) { }
        return null;
    }

    private static boolean staticBoolean(Class<?> type, String method, Object argument) {
        try {
            Object result = type.getMethod(method, Object.class).invoke(null, argument);
            return Boolean.TRUE.equals(result);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static PowerItem powerItemFromIdentifier(String input, String resolver) {
        if (input == null) return null;
        String raw = input.trim().toLowerCase(java.util.Locale.ROOT);
        StringBuilder compactBuilder = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (ch >= 'a' && ch <= 'z') compactBuilder.append(ch);
            else if (ch >= '0' && ch <= '9') compactBuilder.append(ch);
        }
        String compact = compactBuilder.toString();
        if (raw.endsWith(":power_weight") || compact.endsWith("powerweight") || compact.contains("itempowerweight")) return new PowerItem("pixelmon:power_weight", "力量负重", StatsType.HP, resolver);
        if (raw.endsWith(":power_bracer") || compact.endsWith("powerbracer") || compact.contains("itempowerbracer")) return new PowerItem("pixelmon:power_bracer", "力量护腕", StatsType.Attack, resolver);
        if (raw.endsWith(":power_belt") || compact.endsWith("powerbelt") || compact.contains("itempowerbelt")) return new PowerItem("pixelmon:power_belt", "力量腰带", StatsType.Defence, resolver);
        if (raw.endsWith(":power_lens") || compact.endsWith("powerlens") || compact.contains("itempowerlens")) return new PowerItem("pixelmon:power_lens", "力量镜", StatsType.SpecialAttack, resolver);
        if (raw.endsWith(":power_band") || compact.endsWith("powerband") || compact.contains("itempowerband")) return new PowerItem("pixelmon:power_band", "力量束带", StatsType.SpecialDefence, resolver);
        if (raw.endsWith(":power_anklet") || compact.endsWith("poweranklet") || compact.contains("itempoweranklet")) return new PowerItem("pixelmon:power_anklet", "力量护踝", StatsType.Speed, resolver);
        return null;
    }

    private static PowerItem powerItemFromStat(StatsType stat, String resolver) {
        if (stat == StatsType.HP) return new PowerItem("pixelmon:power_weight", "力量负重", StatsType.HP, resolver);
        if (stat == StatsType.Attack) return new PowerItem("pixelmon:power_bracer", "力量护腕", StatsType.Attack, resolver);
        if (stat == StatsType.Defence) return new PowerItem("pixelmon:power_belt", "力量腰带", StatsType.Defence, resolver);
        if (stat == StatsType.SpecialAttack) return new PowerItem("pixelmon:power_lens", "力量镜", StatsType.SpecialAttack, resolver);
        if (stat == StatsType.SpecialDefence) return new PowerItem("pixelmon:power_band", "力量束带", StatsType.SpecialDefence, resolver);
        if (stat == StatsType.Speed) return new PowerItem("pixelmon:power_anklet", "力量护踝", StatsType.Speed, resolver);
        return null;
    }

    private static String heldItemDiagnostic(Pokemon pokemon) {
        if (pokemon == null) return "pokemon=null";
        Object heldView = heldItemAsItemHeld(pokemon);
        Object item = heldItemObject(pokemon);
        String registry = heldItemRegistry(pokemon);
        String identity = heldItemIdentity(pokemon);
        String heldType = heldView == null ? "null" : heldView.getClass().getName();
        String itemType = item == null ? "null" : item.getClass().getName();
        if (identity.length() > 180) identity = identity.substring(0, 180);
        return "registry=" + registry + ", heldView=" + heldType
                + ", rawItem=" + itemType + ", identity=" + identity;
    }

    private static Object heldItemObject(Pokemon pokemon) {
        if (pokemon == null) return null;
        try {
            Object stack = invoke(pokemon, new String[]{"getHeldItem"});
            if (stack == null) return null;
            return invoke(stack, new String[]{"getItem", "func_77973_b"});
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean matchesPixelmonHeldItem(Object item, String fieldName) {
        if (item == null || fieldName == null) return false;
        try {
            Class<?> held = Class.forName("com.pixelmonmod.pixelmon.config.PixelmonItemsHeld", false, item.getClass().getClassLoader());
            Object singleton = held.getField(fieldName).get(null);
            return item == singleton;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String heldItemRegistry(Pokemon pokemon) {
        if (pokemon == null) return "";
        try {
            Object stack = invoke(pokemon, new String[]{"getHeldItem"});
            if (stack == null) return "";
            Object item = invoke(stack, new String[]{"getItem", "func_77973_b"});
            if (item == null) return "";
            Object registry = invoke(item, new String[]{"getRegistryName"});
            return registry == null ? "" : String.valueOf(registry).trim().toLowerCase(java.util.Locale.ROOT);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String heldItemIdentity(Pokemon pokemon) {
        if (pokemon == null) return "";
        StringBuilder text = new StringBuilder();
        try {
            Object stack = invoke(pokemon, new String[]{"getHeldItem"});
            if (stack == null) return "";
            Object item = invoke(stack, new String[]{"getItem", "func_77973_b"});
            if (item == null) return "";
            text.append(item.getClass().getName()).append('|');
            appendReflected(text, item, new String[]{"getRegistryName"});
            appendReflected(text, item, new String[]{"getUnlocalizedName", "func_77658_a"});
            appendReflected(text, item, new String[]{"getTranslationKey"});
        } catch (Throwable ignored) { }
        String raw = text.toString().toLowerCase(java.util.Locale.ROOT);
        StringBuilder normalized = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (ch >= 'a' && ch <= 'z') normalized.append(ch);
            else if (ch >= '0' && ch <= '9') normalized.append(ch);
        }
        return normalized.toString();
    }

    private static void appendReflected(StringBuilder text, Object target, String[] names) {
        try {
            Object value = invoke(target, names);
            if (value != null) text.append(value).append('|');
        } catch (Throwable ignored) { }
    }

    private int[] buildUpgradeIvs(Pokemon first, Pokemon second, Pokemon egg, SynthesisPlan plan) {
        if (plugin.isParentRangeIvMode()) {
            return buildParentRangeIvs(first, second, plan);
        }
        return buildExactTargetIvs(first, second, egg, plan);
    }

    /**
     * For every Pixelmon IV independently, choose an integer uniformly from the
     * inclusive interval formed by the two parents' matching IV values.
     * Example: parent values 25 and 11 produce any value in 11..25.
     */
    private int[] buildParentRangeIvs(Pokemon first, Pokemon second, SynthesisPlan plan) {
        if (first == null || second == null || plan == null) throw new IllegalArgumentException("parents/plan");
        int[] firstIvs = first.getIVs().getArray();
        int[] secondIvs = second.getIVs().getArray();
        if (firstIvs == null || secondIvs == null || firstIvs.length != 6 || secondIvs.length != 6) {
            throw new IllegalStateException("Pixelmon IV array length is not six");
        }
        int[] result = new int[6];
        for (int i = 0; i < 6; i++) {
            int low = Math.min(clamp(firstIvs[i], 0, MAX_IV), clamp(secondIvs[i], 0, MAX_IV));
            int high = Math.max(clamp(firstIvs[i], 0, MAX_IV), clamp(secondIvs[i], 0, MAX_IV));
            result[i] = low + random.nextInt(high - low + 1);
        }
        if (plugin.isForceIvFromPowerItem()) {
            for (PowerLock lock : plan.powerLocks) {
                result[lock.statIndex] = clamp(lock.lockedIv, 0, MAX_IV);
            }
        }
        for (int i = 0; i < 6; i++) {
            int low = Math.min(firstIvs[i], secondIvs[i]);
            int high = Math.max(firstIvs[i], secondIvs[i]);
            if (result[i] < low || result[i] > high) {
                throw new IllegalStateException("parent-range IV escaped bounds at " + IV_NAMES[i]
                        + ": " + result[i] + " not in " + low + ".." + high);
            }
        }
        return result;
    }

    private int[] buildExactTargetIvs(Pokemon first, Pokemon second, Pokemon egg, SynthesisPlan plan) {
        if (plan == null) throw new IllegalArgumentException("plan");
        int targetV = plan.targetV;
        int[] firstIvs = first.getIVs().getArray();
        int[] secondIvs = second.getIVs().getArray();
        int[] result = egg.getIVs().getArray();
        if (firstIvs.length != 6 || secondIvs.length != 6 || result.length != 6) {
            throw new IllegalStateException("Pixelmon IV array length is not six");
        }

        boolean[] locked = new boolean[6];
        int[] lockedValues = new int[6];
        boolean[] selected = new boolean[6];
        int selectedCount = 0;
        if (plugin.isForceIvFromPowerItem()) {
            for (PowerLock lock : plan.powerLocks) {
                locked[lock.statIndex] = true;
                lockedValues[lock.statIndex] = clamp(lock.lockedIv, 0, MAX_IV);
                if (lockedValues[lock.statIndex] == MAX_IV && !selected[lock.statIndex]) {
                    selected[lock.statIndex] = true;
                    selectedCount++;
                }
            }
        }
        if (selectedCount > targetV) {
            throw new IllegalStateException("Power Item locks require " + selectedCount
                    + " perfect IVs but target is only " + targetV + "V");
        }

        List<Integer> inheritedPerfect = new ArrayList<Integer>(6);
        for (int i = 0; i < 6; i++) {
            if (locked[i]) continue;
            if (firstIvs[i] == MAX_IV || secondIvs[i] == MAX_IV) inheritedPerfect.add(i);
        }
        Collections.shuffle(inheritedPerfect, random);
        for (Integer index : inheritedPerfect) {
            if (selectedCount >= targetV) break;
            selected[index] = true;
            selectedCount++;
        }

        if (selectedCount < targetV) {
            List<Integer> candidates = new ArrayList<Integer>(6);
            for (int i = 0; i < 6; i++) {
                if (!selected[i] && !locked[i]) candidates.add(i);
            }
            Collections.shuffle(candidates, random);
            final int[] firstSnapshot = firstIvs;
            final int[] secondSnapshot = secondIvs;
            Collections.sort(candidates, new Comparator<Integer>() {
                @Override
                public int compare(Integer left, Integer right) {
                    int leftValue = Math.max(firstSnapshot[left], secondSnapshot[left]);
                    int rightValue = Math.max(firstSnapshot[right], secondSnapshot[right]);
                    return Integer.compare(rightValue, leftValue);
                }
            });
            for (Integer index : candidates) {
                if (selectedCount >= targetV) break;
                selected[index] = true;
                selectedCount++;
            }
        }

        for (int i = 0; i < 6; i++) {
            if (locked[i]) {
                result[i] = lockedValues[i];
            } else if (selected[i]) {
                result[i] = MAX_IV;
            } else {
                int value = result[i];
                if (value >= MAX_IV) {
                    int firstNonPerfect = firstIvs[i] >= MAX_IV ? MAX_IV - 1 : firstIvs[i];
                    int secondNonPerfect = secondIvs[i] >= MAX_IV ? MAX_IV - 1 : secondIvs[i];
                    value = Math.max(firstNonPerfect, secondNonPerfect);
                }
                result[i] = clamp(value, 0, MAX_IV - 1);
            }
        }

        if (countPerfectIvs(result) != targetV) {
            throw new IllegalStateException("synthesis IV result is not exactly " + targetV
                    + "V after " + plan.powerLocks.size() + " Power Item lock(s)");
        }
        return result;
    }

    private static void applyExactIvs(Pokemon egg, int[] ivs) {
        if (egg == null || ivs == null || ivs.length != 6) {
            throw new IllegalArgumentException("egg/ivs");
        }
        int[] expected = ivs.clone();
        egg.getIVs().fillFromArray(expected);
        egg.getIVs().markDirty();
        int[] actual = egg.getIVs().getArray();
        if (actual == null || actual.length != 6) {
            throw new IllegalStateException("Pixelmon returned an invalid IV array after write");
        }
        for (int i = 0; i < 6; i++) {
            if (actual[i] != expected[i]) {
                throw new IllegalStateException("IV write verification failed at " + IV_NAMES[i]
                        + ": expected=" + expected[i] + ", actual=" + actual[i]);
            }
        }
    }

    private void applyPowerItemLock(Pokemon egg, SynthesisPlan plan, int[] expectedIvs, String stage) {
        if (egg == null || plan == null || !plugin.isForceIvFromPowerItem()
                || plan.powerLocks.isEmpty()) return;
        for (PowerLock lock : plan.powerLocks) {
            int expected = clamp(lock.lockedIv, 0, MAX_IV);
            egg.getIVs().setStat(lock.statType, expected);
            egg.getIVs().markDirty();
            int actual = egg.getIVs().getStat(lock.statType);
            if (actual != expected) {
                throw new IllegalStateException(stage + " Power Item lock verification failed: item="
                        + lock.itemName + ", stat=" + lock.statName
                        + ", expected=" + expected + ", actual=" + actual);
            }
            if (expectedIvs != null && expectedIvs.length == 6) {
                expectedIvs[lock.statIndex] = actual;
            }
            plugin.getLogger().info("Power Item lock verified: item=" + lock.itemName
                    + ", stat=" + lock.statName + ", IV=" + actual
                    + ", sourceParent=" + lock.parentIndex + ", resolver=" + lock.resolver
                    + ", stage=" + stage);
        }
    }

    private static void applyLockedNature(Pokemon pokemon, EnumNature nature) {
        if (pokemon == null || nature == null) throw new IllegalArgumentException("pokemon/nature");
        pokemon.setNature(nature);
        // A mint changes getNature() while getBaseNature() remains the genetic nature.
        // Eggs must not retain a mint override, otherwise the displayed/effective nature is wrong.
        pokemon.setMintNature(null);
        if (pokemon.getBaseNature() != nature || pokemon.getNature() != nature) {
            throw new IllegalStateException("nature write verification failed: expected=" + nature
                    + ", base=" + pokemon.getBaseNature() + ", effective=" + pokemon.getNature());
        }
    }

    private void writeStrictEggState(Pokemon pokemon, int[] ivs, EnumNature nature) {
        if (pokemon == null || ivs == null || ivs.length != 6) throw new IllegalArgumentException("pokemon/ivs");
        Object tag = pokemon.getPersistentData();
        if (tag == null) throw new IllegalStateException("Pokemon persistent data is unavailable");
        invoke(tag, new String[]{"setBoolean", "putBoolean", "func_74757_a"}, STRICT_MARKER, true);
        invoke(tag, new String[]{"setInteger", "setInt", "putInt", "func_74768_a"}, STRICT_VERSION, STRICT_DATA_VERSION);
        invoke(tag, new String[]{"setInteger", "setInt", "putInt", "func_74768_a"}, STRICT_TARGET_V, countPerfectIvs(ivs));
        for (int i = 0; i < 6; i++) {
            invoke(tag, new String[]{"setInteger", "setInt", "putInt", "func_74768_a"}, STRICT_IV_PREFIX + i, ivs[i]);
        }
        invoke(tag, new String[]{"setString", "putString", "func_74778_a"}, STRICT_NATURE,
                nature == null ? "" : nature.name());
    }

    private StrictEggState readStrictEggState(Pokemon pokemon) {
        if (pokemon == null) return null;
        Object tag = pokemon.getPersistentData();
        if (tag == null || !getBoolean(tag, STRICT_MARKER)) return null;
        Object versionValue = invoke(tag, new String[]{"getInteger", "getInt", "func_74762_e"}, STRICT_VERSION);
        int version = versionValue instanceof Number ? ((Number) versionValue).intValue() : 0;
        if (version != STRICT_DATA_VERSION && version != PREVIOUS_STRICT_DATA_VERSION
                && version != LEGACY_STRICT_DATA_VERSION) {
            throw new IllegalStateException("unsupported strict egg data version: " + version);
        }
        int[] ivs = new int[6];
        for (int i = 0; i < 6; i++) {
            Object value = invoke(tag, new String[]{"getInteger", "getInt", "func_74762_e"}, STRICT_IV_PREFIX + i);
            if (!(value instanceof Number)) throw new IllegalStateException("missing strict IV index " + i);
            ivs[i] = clamp(((Number) value).intValue(), 0, MAX_IV);
        }
        Object natureValue = invoke(tag, new String[]{"getString", "func_74779_i"}, STRICT_NATURE);
        EnumNature nature = null;
        String natureName = natureValue == null ? "" : String.valueOf(natureValue).trim();
        if (!natureName.isEmpty()) nature = EnumNature.valueOf(natureName);
        Object targetValue = invoke(tag, new String[]{"getInteger", "getInt", "func_74762_e"}, STRICT_TARGET_V);
        int target = targetValue instanceof Number ? ((Number) targetValue).intValue() : countPerfectIvs(ivs);
        if (countPerfectIvs(ivs) != target) {
            throw new IllegalStateException("stored strict IV target mismatch: target=" + target
                    + ", actual=" + countPerfectIvs(ivs));
        }
        return new StrictEggState(ivs, nature, target);
    }

    private void enforceStrictEggState(Pokemon pokemon, String stage) {
        StrictEggState state = readStrictEggState(pokemon);
        if (state == null) return;
        applyExactIvs(pokemon, state.ivs);
        if (state.nature != null) applyLockedNature(pokemon, state.nature);
        int actualV = countPerfectIvs(pokemon);
        if (actualV != state.targetV) {
            throw new IllegalStateException(stage + " strict V verification failed: expected="
                    + state.targetV + ", actual=" + actualV);
        }
        lastAction = stage + " enforced strict egg state " + state.targetV + "V uuid=" + pokemon.getUUID();
    }

    private static void clearStrictEggState(Object tag) {
        if (tag == null) return;
        invoke(tag, new String[]{"removeTag", "remove", "func_82580_o"}, STRICT_MARKER);
        invoke(tag, new String[]{"removeTag", "remove", "func_82580_o"}, STRICT_VERSION);
        invoke(tag, new String[]{"removeTag", "remove", "func_82580_o"}, STRICT_TARGET_V);
        invoke(tag, new String[]{"removeTag", "remove", "func_82580_o"}, STRICT_NATURE);
        for (int i = 0; i < 6; i++) {
            invoke(tag, new String[]{"removeTag", "remove", "func_82580_o"}, STRICT_IV_PREFIX + i);
        }
    }

    private void queueStrictVerification(final TileEntityRanchBlock ranch, final UUID expectedEgg, long delay) {
        try {
            plugin.getServer().getScheduler().runTaskLater(plugin, new Runnable() {
                @Override
                public void run() {
                    try {
                        Pokemon current = ranch == null ? null : ranch.getEgg();
                        if (current != null && current.getUUID() != null && expectedEgg.equals(current.getUUID())) {
                            enforceStrictEggState(current, "DelayedVerification");
                        }
                    } catch (Throwable error) {
                        fail("Delayed strict egg verification failed: " + rootMessage(error));
                    }
                }
            }, delay);
        } catch (Throwable error) {
            fail("Unable to queue strict verification: " + rootMessage(error));
        }
    }

    private void notifyEggIvs(Player player, Pokemon egg, boolean success, SynthesisPlan plan) {
        if (player == null || egg == null || !plugin.isShowEggIvsOnCreateEnabled()) return;
        if (success && plugin.isShowSynthesisSuccessEnabled()) {
            plugin.sendMessage(player, formatMessage(plugin.getSynthesisSuccessMessage(), egg, plan));
        }
        if (plugin.isShowEggIvsEnabled()) {
            plugin.sendMessage(player, formatMessage(plugin.getEggIvsMessage(), egg, plan));
        }
        if (plan != null && plan.lockedNature != null) {
            plugin.sendMessage(player, formatMessage(plugin.getNatureLockedMessage(), egg, plan));
        }
        if (plan != null && !plan.powerLocks.isEmpty()) {
            plugin.sendMessage(player, formatMessage(plugin.getPowerItemLockedMessage(), egg, plan));
        }
    }

    private static String formatMessage(String template, Pokemon pokemon, SynthesisPlan plan) {
        int[] ivs = pokemon.getIVs().getArray();
        StringBuilder detail = new StringBuilder();
        for (int i = 0; i < ivs.length && i < IV_NAMES.length; i++) {
            if (detail.length() > 0) detail.append(" &8| ");
            detail.append("&f").append(IV_NAMES[i]).append(":")
                    .append(ivs[i] == MAX_IV ? "&a" : "&7").append(ivs[i]);
        }
        String result = (template == null ? "" : template)
                .replace("{v}", String.valueOf(countPerfectIvs(ivs)))
                .replace("{ivs}", detail.toString())
                .replace("{nature}", natureName(pokemon));
        if (plan != null) {
            result = result.replace("{first}", String.valueOf(plan.firstV))
                    .replace("{second}", String.valueOf(plan.secondV))
                    .replace("{base}", String.valueOf(plan.baseV))
                    .replace("{target}", String.valueOf(plan.targetV))
                    .replace("{nature-source}", plan.natureSource == null ? "Pixelmon原版" : plan.natureSource)
                    .replace("{power-item}", plan.powerItemsText())
                    .replace("{power-stat}", plan.powerStatsText())
                    .replace("{locked-iv}", plan.powerIvsText());
        }
        return result;
    }

    private String replaceLockValues(String template, SynthesisPlan plan) {
        if (plan == null) return template == null ? "" : template;
        return replacePlanValues(template, plan.firstV, plan.secondV, plan.baseV, plan.targetV)
                .replace("{power-item}", plan.powerItemsText())
                .replace("{power-stat}", plan.powerStatsText())
                .replace("{locked-iv}", plan.powerIvsText());
    }

    private String replacePlanValues(String template, int firstV, int secondV, int baseV, int targetV) {
        return (template == null ? "" : template)
                .replace("{first}", String.valueOf(firstV))
                .replace("{second}", String.valueOf(secondV))
                .replace("{base}", String.valueOf(baseV))
                .replace("{target}", String.valueOf(targetV))
                .replace("{min}", String.valueOf(plugin.getMinimumUpgradeParentV()))
                .replace("{parent-max}", String.valueOf(plugin.getMaximumUpgradeParentV()))
                .replace("{max}", String.valueOf(plugin.getMaximumResultV()))
                .replace("{step}", String.valueOf(plugin.getUpgradeStep()))
                .replace("{gap}", String.valueOf(plugin.getMaximumParentVDifference()));
    }

    private static String natureName(Pokemon pokemon) {
        if (pokemon == null) return "未知";
        Object nature = pokemon.getNature();
        return nature == null ? "未知" : String.valueOf(nature);
    }

    private static int countPerfectIvs(Pokemon pokemon) {
        return pokemon == null ? 0 : countPerfectIvs(pokemon.getIVs().getArray());
    }

    private static int countPerfectIvs(int[] ivs) {
        int count = 0;
        if (ivs != null) {
            for (int value : ivs) if (value == MAX_IV) count++;
        }
        return count;
    }

    private static int ivIndex(StatsType stat) {
        if (stat == StatsType.HP) return 0;
        if (stat == StatsType.Attack) return 1;
        if (stat == StatsType.Defence) return 2;
        if (stat == StatsType.SpecialAttack) return 3;
        if (stat == StatsType.SpecialDefence) return 4;
        if (stat == StatsType.Speed) return 5;
        return -1;
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }

    private static void denyAddPokemon(BreedEvent.AddPokemon event) {
        event.setResult(Event.Result.DENY);
        event.setCanceled(true);
    }

    private boolean rejectMixedParents(Pokemon first, Pokemon second, Player player, String stage) {
        if (!plugin.isShinyPairingEnabled()) return false;
        if (first.isShiny() == second.isShiny()) return false;
        rejectedPairs.incrementAndGet();
        lastAction = stage + " denied mixed shiny parents=" + first.getUUID() + "," + second.getUUID();
        plugin.sendMessage(player, plugin.getMismatchMessage());
        return true;
    }

    private void cancelConsumeFailure(Event event, Player player) {
        plugin.sendMessage(player, plugin.getConsumeFailedMessage());
        if (plugin.isFailClosedWhenConsumeFails() && event != null && event.isCancelable()) {
            event.setCanceled(true);
        }
    }

    private static List<RanchPoke> safeRanchEntries(TileEntityRanchBlock ranch) {
        List<RanchPoke> entries = ranch.getPokemonData();
        return entries == null ? new ArrayList<RanchPoke>() : new ArrayList<RanchPoke>(entries);
    }

    private static PCStorage requirePc(UUID owner) {
        if (owner == null) throw new IllegalStateException("ranch owner UUID is null");
        PCStorage pc = Pixelmon.storageManager.getPCForPlayer(owner);
        if (pc == null) throw new IllegalStateException("owner PC is unavailable: " + owner);
        return pc;
    }

    private static StoredPokemon find(PCStorage pc, PlayerPartyStorage party, UUID uuid) {
        StoredPokemon found = findInStorage(pc, uuid);
        return found != null ? found : findInStorage(party, uuid);
    }

    private static StoredPokemon findInStorage(PokemonStorage storage, UUID uuid) {
        if (storage == null || uuid == null) return null;
        Pokemon pokemon = storage.find(uuid);
        if (pokemon == null) return null;
        StoragePosition position = storage.getPosition(pokemon);
        if (position == null) return null;
        return new StoredPokemon(storage, position, pokemon, pokemon.isInRanch());
    }

    private static void remove(StoredPokemon stored) {
        stored.storage.set(stored.position, null);
        stored.storage.setNeedsSaving();
    }

    private void rollback(List<StoredPokemon> changed) {
        for (int i = changed.size() - 1; i >= 0; i--) {
            StoredPokemon stored = changed.get(i);
            try {
                stored.storage.set(stored.position, stored.pokemon);
                stored.pokemon.setInRanch(stored.wasInRanch);
                stored.storage.setNeedsSaving();
            } catch (Throwable rollbackError) {
                plugin.getLogger().severe("Parent rollback failed for " + stored.pokemon.getUUID()
                        + ": " + rootMessage(rollbackError));
            }
        }
    }

    private static void refresh(PCStorage pc, PlayerPartyStorage party) {
        pc.setNeedsSaving();
        if (party != null) party.setNeedsSaving();
        EntityPlayerMP ownerPlayer = party == null ? null : party.getPlayer();
        if (ownerPlayer != null) pc.sendContents(ownerPlayer);
    }

    private boolean ranchContainsExactly(TileEntityRanchBlock ranch, UUID owner, UUID p1, UUID p2) {
        UUID ranchOwner = ranch.getOwnerUUID();
        if (ranchOwner != null && !owner.equals(ranchOwner)) return false;
        List<RanchPoke> entries = safeRanchEntries(ranch);
        if (entries.size() != 2) return false;
        boolean first = false;
        boolean second = false;
        for (RanchPoke entry : entries) {
            if (p1.equals(entry.uuid)) first = true;
            else if (p2.equals(entry.uuid)) second = true;
            else return false;
        }
        return first && second;
    }

    private static boolean clearLegacyTracking(Object tag) {
        if (tag == null || !getBoolean(tag, MARKER)) return false;
        clearTracking(tag);
        return true;
    }

    private static void clearTracking(Object tag) {
        if (tag == null) return;
        for (String key : new String[]{MARKER, VERSION, OWNER, PARENT_1, PARENT_2, DIM, X, Y, Z}) {
            invoke(tag, new String[]{"removeTag", "remove", "func_82580_o"}, key);
        }
    }

    private static Player player(UUID uuid) {
        return uuid == null ? null : Bukkit.getPlayer(uuid);
    }

    private static Player player(EntityPlayerMP nativePlayer) {
        return player(nativeUuid(nativePlayer));
    }

    private static UUID nativeUuid(Object nativePlayer) {
        if (nativePlayer == null) return null;
        try {
            Object value = invoke(nativePlayer, new String[]{"getUniqueID", "getUniqueId", "func_110124_au"});
            return value instanceof UUID ? (UUID) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean getBoolean(Object tag, String key) {
        Object value = invoke(tag, new String[]{"getBoolean", "func_74767_n"}, key);
        return value instanceof Boolean && (Boolean) value;
    }

    private void fail(String message) {
        failedTransactions.incrementAndGet();
        lastAction = message;
        plugin.getLogger().severe(message);
    }

    private static Object invoke(Object target, String[] names, Object... args) {
        if (target == null) throw new IllegalStateException("reflection target is null");
        Method method = method(target.getClass(), names, args);
        if (method == null) throw new IllegalStateException(target.getClass().getName() + " missing "
                + join(names) + " for args " + argumentTypes(args));
        try {
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getTargetException();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new IllegalStateException(cause);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }

    private static Method method(Class<?> type, String[] names, Object[] args) {
        // Prefer well-known MCP, CraftBukkit/Spigot and SRG names first.
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method candidate : current.getDeclaredMethods()) {
                if (!contains(names, candidate.getName()) || candidate.getParameterTypes().length != args.length) continue;
                if (compatible(candidate.getParameterTypes(), args)) return candidate;
            }
        }

        // CatServer and other 1.12.2 hybrids may remap NBTTagCompound to CraftBukkit
        // names (for example setInt/getInt/remove), custom names, or obfuscated names.
        // For NBT only, fall back to the method descriptor. The integer setter has a
        // unique (String,int)->void shape, so this remains safe without trusting a name.
        if (!isNbtCompound(type) || names.length == 0) return null;
        Class<?> expectedReturn = nbtExpectedReturn(names[0]);
        if (expectedReturn == null) return null;

        Method resolved = null;
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method candidate : current.getDeclaredMethods()) {
                if (candidate.getParameterTypes().length != args.length) continue;
                if (!compatible(candidate.getParameterTypes(), args)) continue;
                if (!returnCompatible(expectedReturn, candidate.getReturnType())) continue;
                if (resolved != null && !sameSignature(resolved, candidate)) {
                    // Do not guess when a descriptor is ambiguous (notably boolean(String)).
                    return null;
                }
                resolved = candidate;
            }
        }
        return resolved;
    }

    private static boolean isNbtCompound(Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            String name = current.getName();
            if ("net.minecraft.nbt.NBTTagCompound".equals(name) || name.endsWith(".NBTTagCompound")) {
                return true;
            }
        }
        return false;
    }

    private static Class<?> nbtExpectedReturn(String operation) {
        if (operation == null) return null;
        if (operation.startsWith("set") || operation.startsWith("put") || operation.startsWith("remove")) {
            return void.class;
        }
        if ("getInteger".equals(operation) || "getInt".equals(operation)) return int.class;
        if ("getBoolean".equals(operation)) return boolean.class;
        if ("getString".equals(operation)) return String.class;
        return null;
    }

    private static boolean returnCompatible(Class<?> expected, Class<?> actual) {
        Class<?> expectedBoxed = expected.isPrimitive() ? boxed(expected) : expected;
        Class<?> actualBoxed = actual.isPrimitive() ? boxed(actual) : actual;
        return expectedBoxed.isAssignableFrom(actualBoxed);
    }

    private static boolean sameSignature(Method first, Method second) {
        if (!first.getName().equals(second.getName())) return false;
        Class<?>[] left = first.getParameterTypes();
        Class<?>[] right = second.getParameterTypes();
        if (left.length != right.length) return false;
        for (int i = 0; i < left.length; i++) if (left[i] != right[i]) return false;
        return first.getReturnType() == second.getReturnType();
    }

    private static boolean compatible(Class<?>[] types, Object[] args) {
        for (int i = 0; i < types.length; i++) {
            if (args[i] == null) continue;
            Class<?> type = types[i].isPrimitive() ? boxed(types[i]) : types[i];
            if (!type.isInstance(args[i])) return false;
        }
        return true;
    }

    private static Class<?> boxed(Class<?> type) {
        if (type == int.class) return Integer.class;
        if (type == boolean.class) return Boolean.class;
        if (type == long.class) return Long.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        if (type == char.class) return Character.class;
        return type;
    }

    private static boolean contains(String[] names, String value) {
        for (String name : names) if (name.equals(value)) return true;
        return false;
    }

    private static String join(String[] names) {
        StringBuilder builder = new StringBuilder();
        for (String name : names) {
            if (builder.length() > 0) builder.append('|');
            builder.append(name);
        }
        return builder.toString();
    }

    private static String argumentTypes(Object[] args) {
        StringBuilder builder = new StringBuilder("(");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) builder.append(',');
            builder.append(args[i] == null ? "null" : args[i].getClass().getName());
        }
        return builder.append(')').toString();
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current instanceof InvocationTargetException
                && ((InvocationTargetException) current).getTargetException() != null) {
            current = ((InvocationTargetException) current).getTargetException();
        }
        return current.getClass().getSimpleName() + ": " + String.valueOf(current.getMessage());
    }

    public long getRejectedPairs() { return rejectedPairs.get(); }
    public long getRejectedUpgradePairs() { return rejectedUpgradePairs.get(); }
    public long getUpgradedEggs() { return upgradedEggs.get(); }
    public long getZeroTierEggs() { return zeroTierEggs.get(); }
    public long getConsumedAtMake() { return consumedAtMake.get(); }
    public long getLegacyTagsCleaned() { return legacyTagsCleaned.get(); }
    /** Compatibility getters retained for older status integrations. */
    public long getTaggedAtMake() { return 0L; }
    public long getTaggedAtCollect() { return 0L; }
    public long getRemovedParents() { return removedParents.get(); }
    public long getFailedTransactions() { return failedTransactions.get(); }
    public String getLastAction() { return lastAction; }


    private static final class StrictEggState {
        private final int[] ivs;
        private final EnumNature nature;
        private final int targetV;

        private StrictEggState(int[] ivs, EnumNature nature, int targetV) {
            this.ivs = ivs.clone();
            this.nature = nature;
            this.targetV = targetV;
        }
    }

    private static final class SynthesisPlan {
        private final int firstV;
        private final int secondV;
        private final int baseV;
        private int targetV;
        private final boolean zeroTier;
        private final boolean containsZeroParent;
        private EnumNature lockedNature;
        private String natureSource = "Pixelmon原版/不变石";
        private boolean itemLockApplied;
        private int everstoneParent;
        private final List<PowerLock> powerLocks = new ArrayList<PowerLock>(2);

        // Current/failed lock for legacy message placeholders.
        private int powerParent;
        private StatsType powerStatType;
        private int powerStatIndex = -1;
        private int powerLockedIv = -1;
        private String powerItemName;
        private String powerStatName;
        private String powerResolver = "none";

        private SynthesisPlan(int firstV, int secondV, int baseV, int targetV,
                              boolean zeroTier, boolean containsZeroParent) {
            this.firstV = firstV;
            this.secondV = secondV;
            this.baseV = baseV;
            this.targetV = targetV;
            this.zeroTier = zeroTier;
            this.containsZeroParent = containsZeroParent;
        }

        private void setMessagePowerLock(PowerItem item, int parentIndex, int lockedIv) {
            this.powerParent = parentIndex;
            this.powerStatType = item.statType;
            this.powerStatIndex = item.statIndex;
            this.powerLockedIv = lockedIv;
            this.powerItemName = item.displayName;
            this.powerStatName = IV_NAMES[item.statIndex];
            this.powerResolver = item.resolver;
        }

        private void resolveDuplicatePowerStats(Random random, java.util.logging.Logger logger, String stage) {
            if (powerLocks.size() < 2) {
                if (!powerLocks.isEmpty()) setPrimary(powerLocks.get(0));
                return;
            }
            PowerLock first = powerLocks.get(0);
            PowerLock second = powerLocks.get(1);
            if (first.statIndex != second.statIndex) {
                setPrimary(first);
                return;
            }
            PowerLock selected;
            if (first.lockedIv == second.lockedIv) {
                selected = new PowerLock(0, first.itemId, first.itemName,
                        first.statType, first.statIndex, first.statName, first.lockedIv,
                        first.resolver + "+" + second.resolver);
            } else {
                selected = random.nextBoolean() ? first : second;
                logger.warning("Both parents used the same Power Item stat at " + stage
                        + " with different IV values; selected parent " + selected.parentIndex
                        + " for " + selected.statName + "=" + selected.lockedIv);
            }
            powerLocks.clear();
            powerLocks.add(selected);
            setPrimary(selected);
        }

        private void setPrimary(PowerLock lock) {
            this.powerParent = lock.parentIndex;
            this.powerStatType = lock.statType;
            this.powerStatIndex = lock.statIndex;
            this.powerLockedIv = lock.lockedIv;
            this.powerItemName = lock.itemName;
            this.powerStatName = lock.statName;
            this.powerResolver = lock.resolver;
        }

        private String powerItemsText() {
            if (powerLocks.isEmpty()) return powerItemName == null ? "无" : powerItemName;
            StringBuilder out = new StringBuilder();
            for (PowerLock lock : powerLocks) {
                if (out.length() > 0) out.append(" + ");
                out.append(lock.itemName);
            }
            return out.toString();
        }

        private String powerStatsText() {
            if (powerLocks.isEmpty()) return powerStatName == null ? "无" : powerStatName;
            StringBuilder out = new StringBuilder();
            for (PowerLock lock : powerLocks) {
                if (out.length() > 0) out.append(" + ");
                out.append(lock.statName);
            }
            return out.toString();
        }

        private String powerIvsText() {
            if (powerLocks.isEmpty()) return String.valueOf(powerLockedIv < 0 ? 0 : powerLockedIv);
            StringBuilder out = new StringBuilder();
            for (PowerLock lock : powerLocks) {
                if (out.length() > 0) out.append(" + ");
                out.append(lock.lockedIv);
            }
            return out.toString();
        }
    }

    private static final class PowerLock {
        private final int parentIndex;
        private final String itemId;
        private final String itemName;
        private final StatsType statType;
        private final int statIndex;
        private final String statName;
        private final int lockedIv;
        private final String resolver;

        private PowerLock(int parentIndex, PowerItem item, int lockedIv) {
            this(parentIndex, item.id, item.displayName, item.statType, item.statIndex,
                    IV_NAMES[item.statIndex], lockedIv, item.resolver);
        }

        private PowerLock(int parentIndex, String itemId, String itemName, StatsType statType,
                          int statIndex, String statName, int lockedIv, String resolver) {
            this.parentIndex = parentIndex;
            this.itemId = itemId;
            this.itemName = itemName;
            this.statType = statType;
            this.statIndex = statIndex;
            this.statName = statName;
            this.lockedIv = lockedIv;
            this.resolver = resolver;
        }
    }

    private static final class PowerItem {
        private final String id;
        private final String displayName;
        private final StatsType statType;
        private final int statIndex;
        private final String resolver;

        private PowerItem(String id, String displayName, StatsType statType) {
            this(id, displayName, statType, "legacy-fallback");
        }

        private PowerItem(String id, String displayName, StatsType statType, String resolver) {
            this.id = id;
            this.displayName = displayName;
            this.statType = statType;
            this.statIndex = ivIndex(statType);
            this.resolver = resolver == null ? "unknown" : resolver;
            if (this.statIndex < 0) throw new IllegalArgumentException("Unsupported Power Item stat: " + statType);
        }
    }

    private static final class StoredPokemon {
        private final PokemonStorage storage;
        private final StoragePosition position;
        private final Pokemon pokemon;
        private final boolean wasInRanch;

        private StoredPokemon(PokemonStorage storage, StoragePosition position, Pokemon pokemon, boolean wasInRanch) {
            this.storage = storage;
            this.position = position;
            this.pokemon = pokemon;
            this.wasInRanch = wasInRanch;
        }
    }
}
