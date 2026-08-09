package com.pixelmonmod.pixelmon.battles.controller;

import com.pixelmonmod.pixelmon.battles.controller.participants.BattleParticipant;
import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;
import com.pixelmonmod.pixelmon.battles.controller.participants.TeraBattleFix;

/**
 * Activates a previously accepted Terastallization request at the beginning
 * of the action phase, before per-turn stat modifiers and move-speed sorting.
 *
 * V5 kept activation in PixelmonWrapper#useAttack as a late safety net. That
 * hook is intentionally retained; after this pre-order activation the pending
 * request has already been consumed, so the later call becomes a no-op.
 */
public final class TeraTurnOrderFix {
    private TeraTurnOrderFix() {
    }

    public static void activateBeforeStats(BattleControllerBase bc) {
        if (bc == null || bc.simulateMode || bc.turn != 0 || bc.participants == null) {
            return;
        }

        for (BattleParticipant participant : bc.participants) {
            if (participant == null || participant.controlledPokemon == null) {
                continue;
            }
            for (PixelmonWrapper wrapper : participant.controlledPokemon) {
                if (wrapper == null || !wrapper.isAlive() || wrapper.attack == null) {
                    continue;
                }
                TeraBattleFix.activateReady(wrapper);
            }
        }
    }
}
