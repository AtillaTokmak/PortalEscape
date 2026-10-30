package com.portalescape.util;

/**
 * Sanitizes chat and command inputs to prevent packet injection,
 * newline-based chat exploits, color code spoofing, and overflow errors.
 */
public final class CommandSanitizer {
    public static final String DEFAULT_COMMAND = "/rtp";
    public static final int MAX_COMMAND_LENGTH = 256;

    private CommandSanitizer() {}

    /**
     * Sanitizes a command string:
     * - Strips Minecraft color/format code pairs (§ followed by alphanumeric)
     * - Strips CR, LF, null bytes, tabs, and any remaining section signs (§)
     * - Strips control characters
     * - Trims whitespace
     * - Ensures leading slash
     * - Caps at 256 characters
     * - Falls back to DEFAULT_COMMAND if invalid or empty
     */
    public static String sanitizeCommand(String raw) {
        if (raw == null) {
            return DEFAULT_COMMAND;
        }

        // 1. Strip Minecraft formatting pairs (§ followed by code character)
        String preCleaned = raw.replaceAll("(?i)§[0-9a-fk-or]", "");

        // 2. Strip dangerous characters: \r, \n, \0, \t, §
        StringBuilder clean = new StringBuilder(preCleaned.length());
        for (int i = 0; i < preCleaned.length(); i++) {
            char c = preCleaned.charAt(i);
            if (c == '\r' || c == '\n' || c == '\0' || c == '\t' || c == '§') {
                continue;
            }
            if (Character.isISOControl(c)) {
                continue;
            }
            clean.append(c);
        }

        String trimmed = clean.toString().trim();
        if (trimmed.isEmpty()) {
            return DEFAULT_COMMAND;
        }

        // 3. Ensure leading slash
        if (!trimmed.startsWith("/")) {
            trimmed = "/" + trimmed;
        }

        // 4. Clamp to max length
        if (trimmed.length() > MAX_COMMAND_LENGTH) {
            trimmed = trimmed.substring(0, MAX_COMMAND_LENGTH);
        }

        // If after substring it is just "/", fallback
        if (trimmed.equals("/")) {
            return DEFAULT_COMMAND;
        }

        // 5. Prevent recursive commands targeting PortalEscape client commands
        if (isRecursiveCommand(trimmed)) {
            return DEFAULT_COMMAND;
        }

        return trimmed;
    }

    /**
     * Checks if a command would recursively call PortalEscape client commands (/pe or /portalescape).
     */
    public static boolean isRecursiveCommand(String command) {
        if (command == null) {
            return false;
        }
        String trimmed = command.trim();
        if (!trimmed.startsWith("/")) {
            trimmed = "/" + trimmed;
        }
        String lower = trimmed.toLowerCase(java.util.Locale.ROOT);
        return lower.equals("/pe") || lower.startsWith("/pe ") || lower.startsWith("/pe:")
                || lower.equals("/portalescape") || lower.startsWith("/portalescape ") || lower.startsWith("/portalescape:");
    }

    /**
     * Returns the raw command without the leading slash for use with
     * {@code ClientPacketListener.sendCommand(String)}.
     */
    public static String extractRawCommand(String command) {
        String sanitized = sanitizeCommand(command);
        if (sanitized.startsWith("/")) {
            return sanitized.substring(1);
        }
        return sanitized;
    }
}
