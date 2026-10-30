package com.portalescape.recovery;

import com.portalescape.config.ModConfig;
import com.portalescape.util.CommandSanitizer;
import com.portalescape.util.SafeLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Single-fire recovery command dispatcher.
 * Enforces hard debounce and rate-limiting using a monotonic clock,
 * sanitizes commands prior to transmission, and dispatches on the client thread.
 */
public class RecoveryDispatcher {
    private static RecoveryDispatcher instance;

    private final LongSupplier nanoClock;
    private final AtomicLong lastDispatchNano = new AtomicLong(0L);

    public RecoveryDispatcher() {
        this(System::nanoTime);
    }

    public RecoveryDispatcher(LongSupplier nanoClock) {
        this.nanoClock = nanoClock;
    }

    public static synchronized RecoveryDispatcher getInstance() {
        if (instance == null) {
            instance = new RecoveryDispatcher();
        }
        return instance;
    }

    public static synchronized void setInstance(RecoveryDispatcher dispatcher) {
        instance = dispatcher;
    }

    /**
     * Attempts to trigger the recovery command.
     * Returns true if the command was successfully dispatched, or false if blocked by cooldown or null client state.
     */
    public boolean dispatchRecovery(Minecraft client) {
        if (client == null || client.player == null) {
            SafeLogger.warn("Attempted to dispatch recovery command with null client or player context.");
            return false;
        }

        ModConfig config = ModConfig.getInstance();
        if (!config.isEnabled()) {
            SafeLogger.debug(config.isDebugLogging(), "Recovery dispatch skipped: PortalEscape is disabled in configuration.");
            return false;
        }

        long now = nanoClock.getAsLong();
        long cooldownNanos = (long) (config.getCooldownSeconds() * 1_000_000_000L);
        long last = lastDispatchNano.get();

        if (last != 0 && (now - last) < cooldownNanos) {
            long remainingSec = Math.max(1, (cooldownNanos - (now - last)) / 1_000_000_000L);
            SafeLogger.debug(config.isDebugLogging(), "Recovery dispatch blocked by active cooldown ({}s remaining).", remainingSec);
            return false;
        }

        // Compare and set monotonic timestamp to guard against multi-threaded / double invocation
        if (!lastDispatchNano.compareAndSet(last, now)) {
            return false;
        }

        String rawCommand = CommandSanitizer.extractRawCommand(config.getRecoveryCommand());
        SafeLogger.info("Emergency portal recovery triggered! Dispatching command: /{}", rawCommand);

        // Schedule execution on client main thread
        client.execute(() -> {
            try {
                if (client.player != null && client.player.connection != null) {
                    client.player.connection.sendCommand(rawCommand);
                    client.player.sendSystemMessage(
                            Component.literal("§6[PortalEscape]§r Dispatched emergency recovery command: §e/" + rawCommand + "§r")
                    );
                }
            } catch (Throwable t) {
                SafeLogger.error("Failed to execute recovery command /" + rawCommand, t);
            }
        });

        return true;
    }

    /**
     * Resets cooldown timer (e.g., when player manually disconnects or respawns).
     */
    public void resetCooldown() {
        lastDispatchNano.set(0L);
    }

    public long getLastDispatchNano() {
        return lastDispatchNano.get();
    }
}
