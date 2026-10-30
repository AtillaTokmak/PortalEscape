package com.portalescape;

import com.portalescape.config.ModConfig;
import com.portalescape.keybind.CoordShareHandler;
import com.portalescape.keybind.CoordShareHandler.ShareResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class CoordShareHandlerTest {

    private AtomicLong fakeClock;
    private CoordShareHandler handler;
    private ModConfig config;

    @BeforeEach
    void setUp() {
        fakeClock = new AtomicLong(10_000_000_000L); // 10s epoch
        handler = new CoordShareHandler(fakeClock::get);

        config = new ModConfig();
        config.setCoordConfirmationWindowMs(2000L); // 2.0s window
        ModConfig.setInstance(config);
    }

    @Test
    @DisplayName("Single key press enters AWAITING_CONFIRMATION state")
    void testFirstPressAwaitsConfirmation() {
        long t0 = fakeClock.get();
        ShareResult result = handler.onKeyTrigger(t0);

        assertEquals(ShareResult.AWAITING_CONFIRMATION, result);
        assertEquals(t0, handler.getLastPressNano());
        assertEquals(0L, handler.getLastBroadcastNano());
    }

    @Test
    @DisplayName("Rapid second press (< 250ms) is rejected by hardware debounce guard")
    void testHardwareDebounceGuard() {
        long t0 = fakeClock.get();
        handler.onKeyTrigger(t0);

        // 100ms later (debounce threshold is 250ms)
        long t1 = t0 + 100_000_000L;
        ShareResult debounceResult = handler.onKeyTrigger(t1);

        assertEquals(ShareResult.DEBOUNCED, debounceResult);
        assertEquals(t0, handler.getLastPressNano(), "Pending press timestamp must remain untouched on debounced press");
    }

    @Test
    @DisplayName("Second press within confirmation window (250ms - 2000ms) is CONFIRMED")
    void testSecondPressWithinWindowConfirms() {
        long t0 = fakeClock.get();
        handler.onKeyTrigger(t0);

        // 600ms later (valid confirmation window: 250ms <= 600ms <= 2000ms)
        long t1 = t0 + 600_000_000L;
        ShareResult confirmResult = handler.onKeyTrigger(t1);

        assertEquals(ShareResult.CONFIRMED, confirmResult);
        assertEquals(0L, handler.getLastPressNano(), "Pending press should be cleared after confirmation");
        assertEquals(t1, handler.getLastBroadcastNano(), "Broadcast timestamp should be recorded");
    }

    @Test
    @DisplayName("Second press after confirmation window expires is treated as a new first press")
    void testSecondPressAfterWindowExpires() {
        long t0 = fakeClock.get();
        handler.onKeyTrigger(t0);

        // 2500ms later (window is 2000ms, expired!)
        long t1 = t0 + 2_500_000_000L;
        ShareResult expiredResult = handler.onKeyTrigger(t1);

        assertEquals(ShareResult.AWAITING_CONFIRMATION, expiredResult);
        assertEquals(t1, handler.getLastPressNano(), "New first press timestamp should be set to t1");
    }

    @Test
    @DisplayName("Anti-spam cooldown blocks repeated broadcasts for 3 seconds")
    void testAntiSpamBroadcastCooldown() {
        long t0 = fakeClock.get();
        handler.onKeyTrigger(t0);

        // Confirm broadcast at +500ms
        long tConfirm = t0 + 500_000_000L;
        assertEquals(ShareResult.CONFIRMED, handler.onKeyTrigger(tConfirm));

        // Attempt new share at +1500ms (1.0s after broadcast, cooldown is 3.0s)
        long tSpam = tConfirm + 1_000_000_000L;
        assertEquals(ShareResult.COOLDOWN_BLOCKED, handler.onKeyTrigger(tSpam));

        // Attempt at +3100ms (past 3.0s cooldown)
        long tAllowed = tConfirm + 3_100_000_000L;
        assertEquals(ShareResult.AWAITING_CONFIRMATION, handler.onKeyTrigger(tAllowed));
    }

    @Test
    @DisplayName("Reset clears pending confirmation on disconnect or dimension load")
    void testResetClearsPendingState() {
        long t0 = fakeClock.get();
        handler.onKeyTrigger(t0);
        assertEquals(t0, handler.getLastPressNano());

        // Player switches dimensions or disconnects
        handler.reset();
        assertEquals(0L, handler.getLastPressNano());

        // Next press should be treated as fresh first press, not confirmation
        long t1 = t0 + 500_000_000L;
        assertEquals(ShareResult.AWAITING_CONFIRMATION, handler.onKeyTrigger(t1));
    }

    @Test
    @DisplayName("Custom confirmation window from config is respected")
    void testCustomConfirmationWindowConfig() {
        config.setCoordConfirmationWindowMs(1000L); // 1.0s window

        long t0 = fakeClock.get();
        handler.onKeyTrigger(t0);

        // 1200ms later (> 1000ms window)
        long t1 = t0 + 1_200_000_000L;
        assertEquals(ShareResult.AWAITING_CONFIRMATION, handler.onKeyTrigger(t1),
                "Should expire and treat as fresh first press with 1.0s custom window");
    }
}
