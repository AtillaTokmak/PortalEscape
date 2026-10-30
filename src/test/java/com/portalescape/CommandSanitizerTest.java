package com.portalescape;

import com.portalescape.util.CommandSanitizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CommandSanitizerTest {

    @Test
    @DisplayName("Null and blank commands must fall back to /rtp")
    void testNullAndBlankFallback() {
        assertEquals("/rtp", CommandSanitizer.sanitizeCommand(null));
        assertEquals("/rtp", CommandSanitizer.sanitizeCommand(""));
        assertEquals("/rtp", CommandSanitizer.sanitizeCommand("    "));
    }

    @Test
    @DisplayName("Newline and carriage return injection characters must be stripped")
    void testStripNewlinesAndCarriageReturns() {
        String dirty = "/rtp\r\n/op malicious\r\n";
        String clean = CommandSanitizer.sanitizeCommand(dirty);
        assertFalse(clean.contains("\r"), "Should not contain CR");
        assertFalse(clean.contains("\n"), "Should not contain LF");
        assertEquals("/rtp/op malicious", clean);
    }

    @Test
    @DisplayName("Null bytes, tabs, and control characters must be stripped")
    void testStripNullBytesAndTabs() {
        String dirty = "/rtp\0\t--debug\u0007";
        String clean = CommandSanitizer.sanitizeCommand(dirty);
        assertFalse(clean.contains("\0"));
        assertFalse(clean.contains("\t"));
        assertEquals("/rtp--debug", clean);
    }

    @Test
    @DisplayName("Color code section sign (§) must be stripped")
    void testStripSectionSign() {
        String dirty = "§c/rtp§4§ksecret";
        String clean = CommandSanitizer.sanitizeCommand(dirty);
        assertFalse(clean.contains("§"));
        assertEquals("/rtpsecret", clean);
    }

    @Test
    @DisplayName("Commands missing leading slash must have slash prepended")
    void testPrependSlash() {
        assertEquals("/spawn", CommandSanitizer.sanitizeCommand("spawn"));
        assertEquals("/rtp", CommandSanitizer.sanitizeCommand("rtp"));
    }

    @Test
    @DisplayName("Commands exceeding 256 characters must be capped at 256")
    void testMaxLengthClamping() {
        String longCommand = "/warp " + "A".repeat(300);
        String clean = CommandSanitizer.sanitizeCommand(longCommand);
        assertEquals(CommandSanitizer.MAX_COMMAND_LENGTH, clean.length());
        assertTrue(clean.startsWith("/warp AAAAA"));
    }

    @Test
    @DisplayName("Extract raw command returns command without leading slash")
    void testExtractRawCommand() {
        assertEquals("rtp", CommandSanitizer.extractRawCommand("/rtp"));
        assertEquals("spawn lobby", CommandSanitizer.extractRawCommand("/spawn lobby"));
        assertEquals("spawn lobby", CommandSanitizer.extractRawCommand("spawn lobby"));
    }

    @Test
    @DisplayName("Slash-only input falls back to default /rtp")
    void testSlashOnlyFallback() {
        assertEquals("/rtp", CommandSanitizer.sanitizeCommand("/"));
        assertEquals("/rtp", CommandSanitizer.sanitizeCommand("/   "));
    }

    @Test
    @DisplayName("Recursive /pe and /portalescape commands fall back to /rtp")
    void testRecursiveCommandFallback() {
        assertEquals("/rtp", CommandSanitizer.sanitizeCommand("/pe"));
        assertEquals("/rtp", CommandSanitizer.sanitizeCommand("/pe timeout 10"));
        assertEquals("/rtp", CommandSanitizer.sanitizeCommand("pe status"));
        assertEquals("/rtp", CommandSanitizer.sanitizeCommand("/portalescape"));
        assertEquals("/rtp", CommandSanitizer.sanitizeCommand("/portalescape autocommand false"));
        assertEquals("/rtp", CommandSanitizer.sanitizeCommand("/PE"));
        assertEquals("/rtp", CommandSanitizer.sanitizeCommand("/PORTALESCAPE"));

        // Commands starting with pe but not recursive commands should be allowed
        assertEquals("/pet", CommandSanitizer.sanitizeCommand("/pet"));
        assertEquals("/permission check", CommandSanitizer.sanitizeCommand("/permission check"));

        assertTrue(CommandSanitizer.isRecursiveCommand("/pe"));
        assertTrue(CommandSanitizer.isRecursiveCommand("/pe timeout 5"));
        assertTrue(CommandSanitizer.isRecursiveCommand("/portalescape"));
        assertFalse(CommandSanitizer.isRecursiveCommand("/spawn"));
        assertFalse(CommandSanitizer.isRecursiveCommand("/pet"));
        assertFalse(CommandSanitizer.isRecursiveCommand(null));
    }
}
