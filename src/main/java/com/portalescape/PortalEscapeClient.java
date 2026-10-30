package com.portalescape;

import com.portalescape.command.PortalEscapeCommands;
import com.portalescape.config.ModConfig;
import com.portalescape.keybind.KeybindHandler;
import com.portalescape.recovery.RecoveryDispatcher;
import com.portalescape.tracker.PortalTracker;
import com.portalescape.tracker.StuckDetector;
import com.portalescape.util.SafeLogger;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

/**
 * Main client-side entrypoint for PortalEscape.
 */
public class PortalEscapeClient implements ClientModInitializer {
    public static final String MOD_ID = "portalescape";
    public static final String MOD_NAME = "PortalEscape";

    @Override
    public void onInitializeClient() {
        SafeLogger.info("Initializing {} client mod...", MOD_NAME);

        // 1. Initialize configuration
        ModConfig config = ModConfig.getInstance();
        SafeLogger.info("PortalEscape enabled: {}, Stuck Timeout: {}s, Cooldown: {}s",
                config.isEnabled(), config.getStuckTimeoutSeconds(), config.getCooldownSeconds());

        // 2. Initialize singletons
        RecoveryDispatcher.getInstance();
        StuckDetector.getInstance();

        // 3. Register client commands
        ClientCommandRegistrationCallback.EVENT.register(PortalEscapeCommands::register);

        // 4. Register keybindings
        KeybindHandler.register();

        // 5. Register event listeners
        PortalTracker.register();

        SafeLogger.info("{} initialization complete.", MOD_NAME);
    }
}
