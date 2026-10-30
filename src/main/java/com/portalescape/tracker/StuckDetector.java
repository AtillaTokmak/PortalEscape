package com.portalescape.tracker;

import com.portalescape.config.ModConfig;
import com.portalescape.recovery.RecoveryDispatcher;
import com.portalescape.util.SafeLogger;
import net.minecraft.client.Minecraft;

import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * State machine detecting immobile / trapped state inside portal transitions.
 * States:
 *   IDLE -> MONITORING -> SUSPECTED -> TRIGGERED -> COOLDOWN
 */
public class StuckDetector {

    public enum State {
        IDLE,
        MONITORING,
        SUSPECTED,
        TRIGGERED,
        COOLDOWN
    }

    private static StuckDetector instance;

    private final LongSupplier nanoClock;
    private final Consumer<Minecraft> recoveryAction;

    private State state = State.IDLE;
    private double lastX;
    private double lastY;
    private double lastZ;
    private boolean positionInitialized = false;

    private long stuckStartNano = 0L;
    private long cooldownStartNano = 0L;

    public StuckDetector() {
        this(System::nanoTime, client -> RecoveryDispatcher.getInstance().dispatchRecovery(client));
    }

    public StuckDetector(LongSupplier nanoClock, Consumer<Minecraft> recoveryAction) {
        this.nanoClock = nanoClock;
        this.recoveryAction = recoveryAction;
    }

    public static synchronized StuckDetector getInstance() {
        if (instance == null) {
            instance = new StuckDetector();
        }
        return instance;
    }

    public static synchronized void setInstance(StuckDetector detector) {
        instance = detector;
    }

    /**
     * Ticks the stuck detector state machine.
     *
     * @param inPortal whether the player is currently detected inside a portal block/effect
     * @param currentX player current X coordinate
     * @param currentY player current Y coordinate
     * @param currentZ player current Z coordinate
     * @param client   Minecraft client instance (nullable in unit tests if recoveryAction handles null)
     */
    public synchronized void tick(boolean inPortal, double currentX, double currentY, double currentZ, Minecraft client) {
        ModConfig config = ModConfig.getInstance();
        if (!config.isEnabled()) {
            if (state != State.IDLE) {
                reset();
            }
            return;
        }

        long now = nanoClock.getAsLong();
        double movementThreshold = config.getMovementThreshold();
        double stuckTimeoutSec = config.getStuckTimeoutSeconds();
        double cooldownSec = config.getCooldownSeconds();

        long stuckTimeoutNanos = (long) (stuckTimeoutSec * 1_000_000_000L);
        long cooldownNanos = (long) (cooldownSec * 1_000_000_000L);

        // Check distance moved since last tick
        double displacement = 0.0;
        if (positionInitialized) {
            double dx = currentX - lastX;
            double dy = currentY - lastY;
            double dz = currentZ - lastZ;
            displacement = Math.sqrt(dx * dx + dy * dy + dz * dz);
        } else {
            lastX = currentX;
            lastY = currentY;
            lastZ = currentZ;
            positionInitialized = true;
        }

        boolean playerMoved = displacement > movementThreshold;

        switch (state) {
            case IDLE:
                if (inPortal) {
                    state = State.MONITORING;
                    stuckStartNano = now;
                    lastX = currentX;
                    lastY = currentY;
                    lastZ = currentZ;
                    SafeLogger.debug(config.isDebugLogging(), "Entered portal. State -> MONITORING");
                }
                break;

            case MONITORING:
                if (!inPortal) {
                    state = State.IDLE;
                    SafeLogger.debug(config.isDebugLogging(), "Exited portal cleanly. State -> IDLE");
                    break;
                }
                if (playerMoved) {
                    // Player is actively moving inside or through portal
                    lastX = currentX;
                    lastY = currentY;
                    lastZ = currentZ;
                    stuckStartNano = now;
                } else if ((now - stuckStartNano) >= stuckTimeoutNanos) {
                    triggerRecovery(client, stuckTimeoutSec, cooldownSec, now);
                } else if ((now - stuckStartNano) >= 1_000_000_000L) {
                    // Immobile for at least 1 second
                    state = State.SUSPECTED;
                    SafeLogger.debug(config.isDebugLogging(), "Immobile for 1.0s. State -> SUSPECTED");
                }
                break;

            case SUSPECTED:
                if (!inPortal) {
                    state = State.IDLE;
                    SafeLogger.debug(config.isDebugLogging(), "Exited portal from SUSPECTED. State -> IDLE");
                    break;
                }
                if (playerMoved) {
                    state = State.MONITORING;
                    lastX = currentX;
                    lastY = currentY;
                    lastZ = currentZ;
                    stuckStartNano = now;
                    SafeLogger.debug(config.isDebugLogging(), "Player movement detected in SUSPECTED. State -> MONITORING");
                } else if ((now - stuckStartNano) >= stuckTimeoutNanos) {
                    triggerRecovery(client, stuckTimeoutSec, cooldownSec, now);
                }
                break;

            case TRIGGERED:
                // Defensive: Should transition immediately to COOLDOWN
                state = State.COOLDOWN;
                cooldownStartNano = now;
                break;

            case COOLDOWN:
                if ((now - cooldownStartNano) >= cooldownNanos) {
                    if (inPortal) {
                        // Still in portal after cooldown expires, re-initiate monitoring
                        state = State.MONITORING;
                        stuckStartNano = now;
                        lastX = currentX;
                        lastY = currentY;
                        lastZ = currentZ;
                        SafeLogger.debug(config.isDebugLogging(), "Cooldown elapsed, player still in portal. State -> MONITORING");
                    } else {
                        state = State.IDLE;
                        SafeLogger.debug(config.isDebugLogging(), "Cooldown elapsed. State -> IDLE");
                    }
                }
                break;
        }
    }

    private void triggerRecovery(Minecraft client, double stuckTimeoutSec, double cooldownSec, long now) {
        state = State.TRIGGERED;
        SafeLogger.warn("Player trapped/immobilized in portal for {}s! State -> TRIGGERED", stuckTimeoutSec);
        ModConfig config = ModConfig.getInstance();
        if (config.isAutoCommandEnabled()) {
            try {
                recoveryAction.accept(client);
            } catch (Throwable t) {
                SafeLogger.error("Error executing recovery action in TRIGGERED state", t);
            }
        } else {
            SafeLogger.info("Auto recovery command is disabled in configuration. Suppressing command execution.");
        }
        // Advance to COOLDOWN
        state = State.COOLDOWN;
        cooldownStartNano = now;
        SafeLogger.debug(config.isDebugLogging(), "Recovery cycle concluded. State -> COOLDOWN ({}s)", cooldownSec);
    }

    /**
     * Resets detector back to IDLE state (called on disconnect, death, or dimension load).
     */
    public synchronized void reset() {
        state = State.IDLE;
        positionInitialized = false;
        stuckStartNano = 0L;
        cooldownStartNano = 0L;
        SafeLogger.debug(ModConfig.getInstance().isDebugLogging(), "StuckDetector reset to IDLE.");
    }

    public synchronized State getState() {
        return state;
    }

    public synchronized void setState(State state) {
        this.state = state;
    }

    public synchronized long getStuckStartNano() {
        return stuckStartNano;
    }

    public synchronized long getCooldownStartNano() {
        return cooldownStartNano;
    }
}
