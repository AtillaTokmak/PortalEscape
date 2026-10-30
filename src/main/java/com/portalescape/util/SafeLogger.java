package com.portalescape.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

/**
 * Diagnostic logger that enforces privacy redaction:
 * - Redacts exact coordinates from log messages
 * - Masks server IPs and hostnames
 * - Redacts sensitive command parameters (e.g., auth tokens/passwords)
 */
public final class SafeLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger("PortalEscape");

    private static final Pattern IPV4_PATTERN = Pattern.compile("\\b(\\d{1,3}\\.\\d{1,3})\\.\\d{1,3}\\.\\d{1,3}\\b");
    private static final Pattern HOST_PATTERN = Pattern.compile("(?i)\\b([a-z0-9-]{1,2})[a-z0-9.-]+\\.([a-z]{2,})\\b");
    private static final Pattern SENSITIVE_CMD_PATTERN = Pattern.compile("(?i)(/(?:login|register|changepassword|auth|msg|w|tell)\\s+)\\S+");

    private SafeLogger() {}

    public static Logger getRawLogger() {
        return LOGGER;
    }

    public static void info(String message, Object... args) {
        LOGGER.info(sanitize(String.format(message.replace("{}", "%s"), args)));
    }

    public static void warn(String message, Object... args) {
        LOGGER.warn(sanitize(String.format(message.replace("{}", "%s"), args)));
    }

    public static void error(String message, Throwable t) {
        LOGGER.error(sanitize(message), t);
    }

    public static void debug(boolean isDebugEnabled, String message, Object... args) {
        if (isDebugEnabled) {
            LOGGER.info("[DEBUG] " + sanitize(String.format(message.replace("{}", "%s"), args)));
        }
    }

    /**
     * Masks an IP address or hostname so public logs do not leak player server connections.
     */
    public static String maskHost(String host) {
        if (host == null || host.isBlank()) {
            return "[UNKNOWN_HOST]";
        }
        String masked = IPV4_PATTERN.matcher(host).replaceAll("$1.***.***");
        return HOST_PATTERN.matcher(masked).replaceAll("$1***.$2");
    }

    /**
     * Redacts exact coordinates, replacing them with a safe placeholder or delta description.
     */
    public static String redactCoords(double x, double y, double z) {
        return "[COORDS_REDACTED]";
    }

    /**
     * Sanitizes general text strings to prevent log injection or leakage.
     */
    public static String sanitize(String input) {
        if (input == null) return "null";
        String out = IPV4_PATTERN.matcher(input).replaceAll("$1.***.***");
        out = SENSITIVE_CMD_PATTERN.matcher(out).replaceAll("$1***");
        // Remove raw linebreaks to prevent log forging
        return out.replace('\r', ' ').replace('\n', ' ');
    }
}
