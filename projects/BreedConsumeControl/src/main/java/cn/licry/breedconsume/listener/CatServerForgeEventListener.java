package cn.licry.breedconsume.listener;

import catserver.api.bukkit.event.ForgeEvent;
import cn.licry.breedconsume.BreedConsumePlugin;
import cn.licry.breedconsume.service.BreedConsumeService;
import com.pixelmonmod.pixelmon.api.events.BreedEvent;
import com.pixelmonmod.pixelmon.api.events.EggHatchEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Uses the CatServer API path proven by the supplied working Pixelmon plugin:
 * Bukkit Listener -> catserver.api.bukkit.event.ForgeEvent -> underlying Forge event.
 */
public final class CatServerForgeEventListener implements Listener {
    private final BreedConsumePlugin plugin;
    private final BreedConsumeService service;
    private final AtomicLong totalForgeEvents = new AtomicLong();
    private final AtomicLong otherForgeEvents = new AtomicLong();
    private final AtomicLong addEvents = new AtomicLong();
    private final AtomicLong makeEvents = new AtomicLong();
    private final AtomicLong collectEvents = new AtomicLong();
    private final AtomicLong hatchPreEvents = new AtomicLong();
    private final AtomicLong hatchPostEvents = new AtomicLong();
    private final AtomicLong callbackErrors = new AtomicLong();
    private volatile String lastForgeEvent = "never";

    public CatServerForgeEventListener(BreedConsumePlugin plugin, BreedConsumeService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onForgeEvent(ForgeEvent wrapper) {
        totalForgeEvents.incrementAndGet();
        Event raw = wrapper == null ? null : wrapper.getForgeEvent();
        if (raw == null) {
            otherForgeEvents.incrementAndGet();
            lastForgeEvent = "null";
            return;
        }
        lastForgeEvent = raw.getClass().getName();

        try {
            if (raw instanceof BreedEvent.AddPokemon) {
                addEvents.incrementAndGet();
                BreedEvent.AddPokemon event = (BreedEvent.AddPokemon) raw;
                if (!event.isCanceled()) service.handleAddPokemon(event);
                return;
            }
            if (raw instanceof BreedEvent.MakeEgg) {
                makeEvents.incrementAndGet();
                BreedEvent.MakeEgg event = (BreedEvent.MakeEgg) raw;
                if (!event.isCanceled()) service.handleMakeEgg(event);
                return;
            }
            if (raw instanceof BreedEvent.CollectEgg) {
                collectEvents.incrementAndGet();
                BreedEvent.CollectEgg event = (BreedEvent.CollectEgg) raw;
                if (!event.isCanceled()) service.handleCollectEgg(event);
                return;
            }
            if (raw instanceof EggHatchEvent.Post) {
                hatchPostEvents.incrementAndGet();
                service.handleHatchPost((EggHatchEvent.Post) raw);
                return;
            }
            if (raw instanceof EggHatchEvent.Pre) {
                hatchPreEvents.incrementAndGet();
                service.handleHatchPre((EggHatchEvent.Pre) raw);
                return;
            }
            otherForgeEvents.incrementAndGet();
        } catch (Throwable error) {
            callbackErrors.incrementAndGet();
            plugin.getLogger().severe("ForgeEvent callback failed for " + raw.getClass().getName()
                    + ": " + error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage()));
            // Fail closed only for AddPokemon and CollectEgg. MakeEgg must finish natively.
            if (raw instanceof BreedEvent.AddPokemon) {
                safeDeny((BreedEvent.AddPokemon) raw);
            } else if (raw instanceof BreedEvent.CollectEgg) {
                safeCancel(raw);
            }
            // Never cancel MakeEgg because of an internal plugin error. Pixelmon
            // must be allowed to finish assigning the ranch egg first.
        }
    }

    private static void safeDeny(BreedEvent.AddPokemon event) {
        try { event.setResult(Event.Result.DENY); } catch (Throwable ignored) { }
        safeCancel(event);
    }

    private static void safeCancel(Event event) {
        try { if (event.isCancelable()) event.setCanceled(true); } catch (Throwable ignored) { }
    }

    public long getTotalForgeEvents() { return totalForgeEvents.get(); }
    public long getOtherForgeEvents() { return otherForgeEvents.get(); }
    public long getAddEvents() { return addEvents.get(); }
    public long getMakeEvents() { return makeEvents.get(); }
    public long getCollectEvents() { return collectEvents.get(); }
    public long getHatchPreEvents() { return hatchPreEvents.get(); }
    public long getHatchPostEvents() { return hatchPostEvents.get(); }
    public long getCallbackErrors() { return callbackErrors.get(); }
    public String getLastForgeEvent() { return lastForgeEvent; }
}
