package com.portalescape.tracker;

import com.portalescape.keybind.KeybindHandler;
import com.portalescape.recovery.RecoveryDispatcher;
import com.portalescape.util.SafeLogger;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Monitors client tick loops, portal collision/transition states,
 * and dimension change events.
 */
public class PortalTracker {

    public static void register() {
        // Register tick event
        ClientTickEvents.END_CLIENT_TICK.register(PortalTracker::onClientTick);

        // Register dimension / level change event
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register(PortalTracker::onLevelChange);

        // Register network events
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            SafeLogger.info("Client disconnected. Resetting portal tracking states.");
            StuckDetector.getInstance().reset();
            RecoveryDispatcher.getInstance().resetCooldown();
            com.portalescape.keybind.CoordShareHandler.getInstance().reset();
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            SafeLogger.info("Client joined server. Initializing portal tracking states.");
            StuckDetector.getInstance().reset();
            com.portalescape.keybind.CoordShareHandler.getInstance().reset();
        });

        SafeLogger.info("PortalTracker event listeners registered successfully.");
    }

    private static void onClientTick(Minecraft client) {
        if (client == null || client.player == null || client.level == null) {
            return;
        }

        // Process emergency keybind
        KeybindHandler.handleInput(client);

        LocalPlayer player = client.player;
        boolean inPortal = isPlayerInPortal(player, client.level);

        StuckDetector.getInstance().tick(
                inPortal,
                player.getX(),
                player.getY(),
                player.getZ(),
                client
        );
    }

    private static void onLevelChange(Minecraft client, ClientLevel level) {
        SafeLogger.info("Dimension change detected. Resetting stuck detector state.");
        StuckDetector.getInstance().reset();
        com.portalescape.keybind.CoordShareHandler.getInstance().reset();
    }

    /**
     * Determines whether the player is currently inside a portal or in an active portal transition.
     */
    public static boolean isPlayerInPortal(LocalPlayer player, ClientLevel level) {
        if (player == null || level == null) {
            return false;
        }

        // 1. Check entity portal process field (set by Minecraft while inside portal block)
        if (player.portalProcess != null) {
            return true;
        }

        // 2. Check blocks intersecting feet or head
        BlockPos feet = player.blockPosition();
        BlockPos head = feet.above();

        if (isPortalBlock(level.getBlockState(feet)) || isPortalBlock(level.getBlockState(head))) {
            return true;
        }

        // 3. Check portal effect intensity (transition overlay)
        return player.portalEffectIntensity > 0.0f;
    }

    private static boolean isPortalBlock(BlockState state) {
        if (state == null) {
            return false;
        }
        return state.is(Blocks.NETHER_PORTAL)
                || state.is(Blocks.END_PORTAL)
                || state.is(Blocks.END_GATEWAY);
    }
}
