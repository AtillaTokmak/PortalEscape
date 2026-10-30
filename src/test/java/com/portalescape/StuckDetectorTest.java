package com.portalescape;

import com.portalescape.config.ModConfig;
import com.portalescape.tracker.StuckDetector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class StuckDetectorTest {

    private AtomicLong fakeClock;
    private AtomicInteger recoveryCounter;
    private StuckDetector detector;
    private ModConfig config;

    @BeforeEach
    void setUp() {
        fakeClock = new AtomicLong(1_000_000_000L); // 1 second epoch
        recoveryCounter = new AtomicInteger(0);
        detector = new StuckDetector(fakeClock::get, client -> recoveryCounter.incrementAndGet());

        config = new ModConfig();
        config.setEnabled(true);
        config.setStuckTimeoutSeconds(5.0);
        config.setMovementThreshold(0.05);
        config.setCooldownSeconds(10.0);
        ModConfig.setInstance(config);
    }

    @Test
    @DisplayName("Entering portal transitions state from IDLE to MONITORING")
    void testIdleToMonitoring() {
        assertEquals(StuckDetector.State.IDLE, detector.getState());

        // Player enters portal at (100, 64, 200)
        detector.tick(true, 100.0, 64.0, 200.0, null);
        assertEquals(StuckDetector.State.MONITORING, detector.getState());
    }

    @Test
    @DisplayName("Leaving portal before timeout cleanly returns state to IDLE")
    void testCleanExitBeforeTimeout() {
        // Enters portal
        detector.tick(true, 100.0, 64.0, 200.0, null);
        assertEquals(StuckDetector.State.MONITORING, detector.getState());

        // Leaves portal after 500ms
        fakeClock.addAndGet(500_000_000L);
        detector.tick(false, 100.0, 64.0, 201.0, null);
        assertEquals(StuckDetector.State.IDLE, detector.getState());
        assertEquals(0, recoveryCounter.get(), "Recovery should not have fired");
    }

    @Test
    @DisplayName("Active player movement in portal resets stuck timer and keeps state in MONITORING")
    void testPlayerMovementResetsStuckTimer() {
        detector.tick(true, 100.0, 64.0, 200.0, null);
        assertEquals(StuckDetector.State.MONITORING, detector.getState());

        // Move 0.2 blocks every 400ms for 6 seconds (total 6.0s elapsed, but never immobile)
        for (int i = 1; i <= 15; i++) {
            fakeClock.addAndGet(400_000_000L);
            detector.tick(true, 100.0 + (i * 0.2), 64.0, 200.0, null);
            assertEquals(StuckDetector.State.MONITORING, detector.getState(),
                    "Should remain in MONITORING due to active movement at tick " + i);
        }

        assertEquals(0, recoveryCounter.get(), "Recovery must not trigger while moving");
    }

    @Test
    @DisplayName("Immobile player transitions to SUSPECTED (1s) and TRIGGERED (5s) with single-fire recovery")
    void testImmobilityTriggersRecoveryOnce() {
        detector.tick(true, 100.0, 64.0, 200.0, null);
        assertEquals(StuckDetector.State.MONITORING, detector.getState());

        // Advance 1.2s without movement -> SUSPECTED
        fakeClock.addAndGet(1_200_000_000L);
        detector.tick(true, 100.0, 64.0, 200.0, null);
        assertEquals(StuckDetector.State.SUSPECTED, detector.getState());
        assertEquals(0, recoveryCounter.get());

        // Advance remaining 3.9s (total 5.1s immobile >= 5.0s timeout) -> TRIGGERED & COOLDOWN
        fakeClock.addAndGet(3_900_000_000L);
        detector.tick(true, 100.0, 64.0, 200.0, null);

        // State immediately transitions to COOLDOWN after firing
        assertEquals(StuckDetector.State.COOLDOWN, detector.getState());
        assertEquals(1, recoveryCounter.get(), "Recovery must have fired exactly once");
    }

    @Test
    @DisplayName("Cooldown prevents duplicate dispatches and packet spamming")
    void testCooldownPreventsDuplicateDispatches() {
        // Trigger recovery
        detector.tick(true, 100.0, 64.0, 200.0, null);
        fakeClock.addAndGet(5_100_000_000L);
        detector.tick(true, 100.0, 64.0, 200.0, null);

        assertEquals(StuckDetector.State.COOLDOWN, detector.getState());
        assertEquals(1, recoveryCounter.get());

        // Simulate 5 seconds ticking during cooldown while still in portal
        for (int i = 0; i < 10; i++) {
            fakeClock.addAndGet(500_000_000L);
            detector.tick(true, 100.0, 64.0, 200.0, null);
            assertEquals(StuckDetector.State.COOLDOWN, detector.getState());
            assertEquals(1, recoveryCounter.get(), "Recovery must NOT fire during cooldown");
        }

        // Advance past cooldown (10s total cooldown)
        fakeClock.addAndGet(6_000_000_000L);
        detector.tick(true, 100.0, 64.0, 200.0, null);

        // Player still in portal -> re-enters MONITORING
        assertEquals(StuckDetector.State.MONITORING, detector.getState());
    }

    @Test
    @DisplayName("Reset clears state back to IDLE immediately on disconnect or dimension load")
    void testReset() {
        detector.tick(true, 100.0, 64.0, 200.0, null);
        fakeClock.addAndGet(2_000_000_000L);
        detector.tick(true, 100.0, 64.0, 200.0, null);
        assertEquals(StuckDetector.State.SUSPECTED, detector.getState());

        detector.reset();
        assertEquals(StuckDetector.State.IDLE, detector.getState());
    }

    @Test
    @DisplayName("Auto recovery is suppressed when autoCommandEnabled is false")
    void testAutoCommandDisabledSuppressesDispatch() {
        config.setAutoCommandEnabled(false);

        detector.tick(true, 100.0, 64.0, 200.0, null);
        fakeClock.addAndGet(5_100_000_000L);
        detector.tick(true, 100.0, 64.0, 200.0, null);

        // State advances to COOLDOWN, but action was NOT invoked
        assertEquals(StuckDetector.State.COOLDOWN, detector.getState());
        assertEquals(0, recoveryCounter.get(), "Recovery action must NOT be dispatched when autoCommandEnabled is false");
    }
}
