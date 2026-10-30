package com.portalescape;

import com.portalescape.util.SafeLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SafeLoggerTest {

    @Test
    @DisplayName("Mask IPv4 addresses in log messages")
    void testMaskIpv4() {
        String msg = "Connected to server at 192.168.1.105:25565";
        String masked = SafeLogger.sanitize(msg);
        assertFalse(masked.contains("192.168.1.105"));
        assertTrue(masked.contains("192.168.***.***"));
    }

    @Test
    @DisplayName("Mask domain names in host logging")
    void testMaskHost() {
        String host = "play.hypixel.net";
        String masked = SafeLogger.maskHost(host);
        assertFalse(masked.contains("play.hypixel.net"));
        assertTrue(masked.contains("***.net"));
    }

    @Test
    @DisplayName("Redact sensitive auth/password commands from logs")
    void testRedactSensitiveCommands() {
        String msg = "Dispatching command: /login SuperSecretPassword123";
        String sanitized = SafeLogger.sanitize(msg);
        assertFalse(sanitized.contains("SuperSecretPassword123"));
        assertTrue(sanitized.contains("/login ***"));
    }

    @Test
    @DisplayName("Strip linebreaks to protect against log injection / forgery")
    void testStripLinebreaks() {
        String msg = "Event: Stuck\r\n[CRITICAL] Admin access granted\n";
        String sanitized = SafeLogger.sanitize(msg);
        assertFalse(sanitized.contains("\r"));
        assertFalse(sanitized.contains("\n"));
        assertTrue(sanitized.contains("Event: Stuck  [CRITICAL] Admin access granted"));
    }

    @Test
    @DisplayName("Coordinates redaction returns constant redacted token")
    void testRedactCoords() {
        String redacted = SafeLogger.redactCoords(12345.67, 64.0, -98765.43);
        assertEquals("[COORDS_REDACTED]", redacted);
    }
}
