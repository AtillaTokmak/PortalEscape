package com.portalescape.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import com.portalescape.config.ModConfig;
import com.portalescape.util.SafeLogger;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.resources.Identifier;

/**
 * Manages PortalEscape keybinding registration and input handling.
 * Allows opening chat (with or without recovery command) and sharing coordinates even inside portals.
 */
public class KeybindHandler {
    public static final String KEY_CATEGORY = "key.categories.portalescape";
    public static final String KEY_OPEN_CHAT = "key.portalescape.open_chat";
    public static final String KEY_OPEN_PLAIN_CHAT = "key.portalescape.open_plain_chat";
    public static final String KEY_SHARE_COORDS = "key.portalescape.share_coords";

    private static KeyMapping openChatKey;
    private static KeyMapping openPlainChatKey;
    private static KeyMapping shareCoordsKey;

    public static void register() {
        try {
            KeyMapping.Category category = KeyMapping.Category.register(
                    Identifier.fromNamespaceAndPath("portalescape", "portalescape")
            );

            // 1. Emergency recovery chat key (Default: K)
            openChatKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                    KEY_OPEN_CHAT,
                    InputConstants.KEY_K,
                    category
            ));

            // 2. Normal plain chat key in portal (Default: H)
            openPlainChatKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                    KEY_OPEN_PLAIN_CHAT,
                    InputConstants.KEY_H,
                    category
            ));

            // 3. Share coordinates key (Default: P)
            shareCoordsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                    KEY_SHARE_COORDS,
                    InputConstants.KEY_P,
                    category
            ));

            SafeLogger.info("PortalEscape keybindings registered successfully: OpenChat (K), PlainChat (H), ShareCoords (P)");
        } catch (Throwable t) {
            SafeLogger.error("Failed to register PortalEscape keybindings", t);
        }
    }

    /**
     * Handles client tick input checks.
     */
    public static void handleInput(Minecraft client) {
        if (client == null) {
            return;
        }

        // Process emergency command chat key
        if (openChatKey != null) {
            while (openChatKey.consumeClick()) {
                if (client.gui != null && client.gui.screen() == null) {
                    ModConfig config = ModConfig.getInstance();
                    String initialText = config.isKeyAutoPopulateCommand() ? config.getRecoveryCommand() : "";
                    SafeLogger.debug(config.isDebugLogging(), "Emergency chat key pressed. Opening ChatScreen with initial text: '{}'", initialText);
                    client.gui.setScreen(new ChatScreen(initialText, false));
                }
            }
        }

        // Process plain chat key (opens empty chat without any prefilled command)
        if (openPlainChatKey != null) {
            while (openPlainChatKey.consumeClick()) {
                if (client.gui != null && client.gui.screen() == null) {
                    SafeLogger.debug(ModConfig.getInstance().isDebugLogging(), "Plain chat key pressed. Opening blank ChatScreen");
                    client.gui.setScreen(new ChatScreen("", false));
                }
            }
        }

        // Process coordinate share key
        if (shareCoordsKey != null) {
            while (shareCoordsKey.consumeClick()) {
                CoordShareHandler.getInstance().handleKeyPress(client);
            }
        }
    }

    public static KeyMapping getOpenChatKey() {
        return openChatKey;
    }

    public static KeyMapping getOpenPlainChatKey() {
        return openPlainChatKey;
    }

    public static KeyMapping getShareCoordsKey() {
        return shareCoordsKey;
    }
}
