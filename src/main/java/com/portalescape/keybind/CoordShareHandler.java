package com.portalescape.keybind;

import com.portalescape.config.ModConfig;
import com.portalescape.util.SafeLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import java.util.function.LongSupplier;

/**
 * Handles coordinate sharing with double-tap confirmation, hardware debouncing,
 * and anti-spam broadcast rate limiting.
 */
public class CoordShareHandler {
    public static final long DEBOUNCE_NANOS = 250_000_000L; // 250ms hardware debounce
    public static final long BROADCAST_COOLDOWN_NANOS = 3_000_000_000L; // 3.0s anti-spam

    public enum ShareResult {
        AWAITING_CONFIRMATION,
        DEBOUNCED,
        CONFIRMED,
        COOLDOWN_BLOCKED
    }

    private static CoordShareHandler instance;

    private final LongSupplier nanoClock;
    private long lastPressNano = 0L;
    private long lastBroadcastNano = 0L;

    public CoordShareHandler() {
        this(System::nanoTime);
    }

    public CoordShareHandler(LongSupplier nanoClock) {
        this.nanoClock = nanoClock;
    }

    public static synchronized CoordShareHandler getInstance() {
        if (instance == null) {
            instance = new CoordShareHandler();
        }
        return instance;
    }

    public static synchronized void setInstance(CoordShareHandler handler) {
        instance = handler;
    }

    /**
     * Evaluates a key press event against monotonic timestamps.
     */
    public synchronized ShareResult onKeyTrigger(long now) {
        // 1. Check anti-spam broadcast cooldown
        if (lastBroadcastNano != 0L && (now - lastBroadcastNano) < BROADCAST_COOLDOWN_NANOS) {
            return ShareResult.COOLDOWN_BLOCKED;
        }

        // 2. First press
        if (lastPressNano == 0L) {
            lastPressNano = now;
            return ShareResult.AWAITING_CONFIRMATION;
        }

        // 3. Second press
        long elapsed = now - lastPressNano;

        // Hardware switch bounce guard
        if (elapsed < DEBOUNCE_NANOS) {
            return ShareResult.DEBOUNCED;
        }

        long windowNanos = ModConfig.getInstance().getCoordConfirmationWindowMs() * 1_000_000L;
        if (elapsed <= windowNanos) {
            // Confirmed within window
            lastPressNano = 0L;
            lastBroadcastNano = now;
            return ShareResult.CONFIRMED;
        } else {
            // Confirmation expired; treat as a fresh first press
            lastPressNano = now;
            return ShareResult.AWAITING_CONFIRMATION;
        }
    }

    /**
     * Handles key press from client input tick.
     */
    public void handleKeyPress(Minecraft client) {
        if (client == null || client.player == null) {
            return;
        }

        long now = nanoClock.getAsLong();
        ShareResult result = onKeyTrigger(now);

        switch (result) {
            case COOLDOWN_BLOCKED -> client.player.sendSystemMessage(
                    Component.literal("§c[PortalEscape]§r Lütfen kordinat paylaşmak için bekleyin.")
            );
            case AWAITING_CONFIRMATION -> client.player.sendSystemMessage(
                    Component.literal("§6[PortalEscape]§r Paylaşmak istediğinize emin misiniz? Onaylamak için tekrar basın.")
            );
            case DEBOUNCED -> SafeLogger.debug(
                    ModConfig.getInstance().isDebugLogging(),
                    "Coordinate share key press debounced (< 250ms)"
            );
            case CONFIRMED -> broadcastCoordinates(client.player);
        }
    }

    private void broadcastCoordinates(LocalPlayer player) {
        if (player == null || player.connection == null) {
            return;
        }

        int x = player.getBlockX();
        int y = player.getBlockY();
        int z = player.getBlockZ();
        String dimension = "unknown";
        if (player.level() != null && player.level().dimension() != null) {
            dimension = player.level().dimension().identifier().toString();
        }

        String chatMessage = String.format("[PortalEscape] Kordinat: X: %d, Y: %d, Z: %d (%s)", x, y, z, dimension);
        player.connection.sendChat(chatMessage);
        SafeLogger.info("Publicly shared coordinates in chat: [COORDS_REDACTED] ({})", dimension);
    }

    /**
     * Resets pending confirmation state (called on disconnect, dimension change, etc.).
     */
    public synchronized void reset() {
        lastPressNano = 0L;
    }

    /**
     * Full reset including broadcast cooldown timer.
     */
    public synchronized void resetAll() {
        lastPressNano = 0L;
        lastBroadcastNano = 0L;
    }

    public synchronized long getLastPressNano() {
        return lastPressNano;
    }

    public synchronized long getLastBroadcastNano() {
        return lastBroadcastNano;
    }
}
