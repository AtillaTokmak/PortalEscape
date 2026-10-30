package com.portalescape;

import com.portalescape.config.ModConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class ModConfigTest {

    @Test
    @DisplayName("Default configuration values adhere to specification")
    void testDefaultValues() {
        ModConfig config = new ModConfig();
        config.validateAndClamp();

        assertTrue(config.isEnabled());
        assertEquals(5.0, config.getStuckTimeoutSeconds(), 0.001);
        assertEquals(0.05, config.getMovementThreshold(), 0.001);
        assertEquals("/rtp", config.getRecoveryCommand());
        assertEquals(10.0, config.getCooldownSeconds(), 0.001);
        assertFalse(config.isDebugLogging());
        assertTrue(config.isAutoCommandEnabled());
        assertTrue(config.isKeyAutoPopulateCommand());
        assertEquals(2000L, config.getCoordConfirmationWindowMs());
    }

    @Test
    @DisplayName("Boundary clamping on coordConfirmationWindowMs")
    void testCoordConfirmationWindowClamping() {
        ModConfig config = new ModConfig();

        config.setCoordConfirmationWindowMs(100L);
        assertEquals(ModConfig.MIN_COORD_CONFIRMATION_WINDOW_MS, config.getCoordConfirmationWindowMs());

        config.setCoordConfirmationWindowMs(-500L);
        assertEquals(ModConfig.MIN_COORD_CONFIRMATION_WINDOW_MS, config.getCoordConfirmationWindowMs());

        config.setCoordConfirmationWindowMs(10000L);
        assertEquals(ModConfig.MAX_COORD_CONFIRMATION_WINDOW_MS, config.getCoordConfirmationWindowMs());

        config.setCoordConfirmationWindowMs(3500L);
        assertEquals(3500L, config.getCoordConfirmationWindowMs());
    }

    @Test
    @DisplayName("Boundary clamping on stuckTimeoutSeconds")
    void testStuckTimeoutClamping() {
        ModConfig config = new ModConfig();

        config.setStuckTimeoutSeconds(-10.0);
        assertEquals(ModConfig.MIN_STUCK_TIMEOUT, config.getStuckTimeoutSeconds());

        config.setStuckTimeoutSeconds(999.0);
        assertEquals(ModConfig.MAX_STUCK_TIMEOUT, config.getStuckTimeoutSeconds());

        config.setStuckTimeoutSeconds(Double.NaN);
        assertEquals(ModConfig.DEFAULT_STUCK_TIMEOUT, config.getStuckTimeoutSeconds());

        config.setStuckTimeoutSeconds(Double.POSITIVE_INFINITY);
        assertEquals(ModConfig.DEFAULT_STUCK_TIMEOUT, config.getStuckTimeoutSeconds());
    }

    @Test
    @DisplayName("Boundary clamping on movementThreshold")
    void testMovementThresholdClamping() {
        ModConfig config = new ModConfig();

        config.setMovementThreshold(-0.5);
        assertEquals(ModConfig.MIN_MOVEMENT_THRESHOLD, config.getMovementThreshold());

        config.setMovementThreshold(100.0);
        assertEquals(ModConfig.MAX_MOVEMENT_THRESHOLD, config.getMovementThreshold());

        config.setMovementThreshold(Double.NaN);
        assertEquals(ModConfig.DEFAULT_MOVEMENT_THRESHOLD, config.getMovementThreshold());
    }

    @Test
    @DisplayName("Boundary clamping on cooldownSeconds")
    void testCooldownClamping() {
        ModConfig config = new ModConfig();

        config.setCooldownSeconds(0.1);
        assertEquals(ModConfig.MIN_COOLDOWN, config.getCooldownSeconds());

        config.setCooldownSeconds(5000.0);
        assertEquals(ModConfig.MAX_COOLDOWN, config.getCooldownSeconds());

        config.setCooldownSeconds(Double.NaN);
        assertEquals(ModConfig.DEFAULT_COOLDOWN, config.getCooldownSeconds());
    }

    @Test
    @DisplayName("Recovery command is sanitized on set")
    void testRecoveryCommandSanitizedOnSet() {
        ModConfig config = new ModConfig();

        config.setRecoveryCommand("spawn\r\n");
        assertEquals("/spawn", config.getRecoveryCommand());

        config.setRecoveryCommand("§4§c/home");
        assertEquals("/home", config.getRecoveryCommand());

        config.setRecoveryCommand("");
        assertEquals("/rtp", config.getRecoveryCommand());

        // Recursive command tests
        config.setRecoveryCommand("/pe");
        assertEquals("/rtp", config.getRecoveryCommand());

        config.setRecoveryCommand("/pe timeout 10");
        assertEquals("/rtp", config.getRecoveryCommand());

        config.setRecoveryCommand("/portalescape");
        assertEquals("/rtp", config.getRecoveryCommand());

        config.setRecoveryCommand("/portalescape status");
        assertEquals("/rtp", config.getRecoveryCommand());
    }

    @Test
    @DisplayName("Config persistence: Save and reload cleanly")
    void testSaveAndReload(@TempDir Path tempDir) {
        Path configFile = tempDir.resolve("test_config.json");
        ModConfig config = ModConfig.loadOrCreate(configFile);

        config.setEnabled(false);
        config.setStuckTimeoutSeconds(8.5);
        config.setMovementThreshold(0.12);
        config.setRecoveryCommand("/lobby");
        config.setCooldownSeconds(25.0);
        config.setDebugLogging(true);
        config.setAutoCommandEnabled(false);
        config.setKeyAutoPopulateCommand(false);
        config.setCoordConfirmationWindowMs(3500L);
        config.save();

        assertTrue(Files.exists(configFile), "Config file should be saved");

        // Reload fresh instance
        ModConfig reloaded = ModConfig.loadOrCreate(configFile);
        assertFalse(reloaded.isEnabled());
        assertEquals(8.5, reloaded.getStuckTimeoutSeconds(), 0.001);
        assertEquals(0.12, reloaded.getMovementThreshold(), 0.001);
        assertEquals("/lobby", reloaded.getRecoveryCommand());
        assertEquals(25.0, reloaded.getCooldownSeconds(), 0.001);
        assertTrue(reloaded.isDebugLogging());
        assertFalse(reloaded.isAutoCommandEnabled());
        assertFalse(reloaded.isKeyAutoPopulateCommand());
        assertEquals(3500L, reloaded.getCoordConfirmationWindowMs());
    }

    @Test
    @DisplayName("Corrupted JSON fallback restores defaults and backs up corrupted file")
    void testCorruptedJsonFallback(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("portalescape.json");
        Files.writeString(configFile, "{ \"stuckTimeoutSeconds\": [MALFORMED_JSON_SYNTAX] }");

        ModConfig recovered = ModConfig.loadOrCreate(configFile);

        // Should restore defaults
        assertEquals(ModConfig.DEFAULT_STUCK_TIMEOUT, recovered.getStuckTimeoutSeconds());
        assertEquals("/rtp", recovered.getRecoveryCommand());

        // Should have created backup file with .corrupted in the directory
        boolean hasBackup = Files.list(tempDir).anyMatch(p -> p.getFileName().toString().contains(".corrupted."));
        assertTrue(hasBackup, "Should have created a .corrupted backup file");
    }
}
