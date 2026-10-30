package com.portalescape.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.portalescape.config.ModConfig;
import com.portalescape.util.CommandSanitizer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;

/**
 * Registers client-side commands for runtime configuration of PortalEscape:
 * - /pe or /portalescape
 * - /pe status
 * - /pe timeout <1.0 - 60.0>
 * - /pe command <greedy_string>
 * - /pe autocommand <true|false>
 * - /pe keycommand <true|false>
 */
public class PortalEscapeCommands {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext buildContext) {
        dispatcher.register(buildRootCommand("pe"));
        dispatcher.register(buildRootCommand("portalescape"));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> buildRootCommand(String rootLiteral) {
        return ClientCommands.literal(rootLiteral)
                .executes(context -> {
                    showStatus(context.getSource());
                    return 1;
                })
                .then(ClientCommands.literal("status")
                        .executes(context -> {
                            showStatus(context.getSource());
                            return 1;
                        })
                )
                .then(ClientCommands.literal("timeout")
                        .then(ClientCommands.argument("seconds", DoubleArgumentType.doubleArg(ModConfig.MIN_STUCK_TIMEOUT, ModConfig.MAX_STUCK_TIMEOUT))
                                .executes(context -> {
                                    double seconds = DoubleArgumentType.getDouble(context, "seconds");
                                    ModConfig config = ModConfig.getInstance();
                                    config.setStuckTimeoutSeconds(seconds);
                                    config.save();
                                    context.getSource().sendFeedback(
                                            Component.literal("§6[PortalEscape]§r Stuck timeout set to: §e" + config.getStuckTimeoutSeconds() + "s§r.")
                                    );
                                    return 1;
                                })
                        )
                )
                .then(ClientCommands.literal("command")
                        .then(ClientCommands.argument("cmd", StringArgumentType.greedyString())
                                .executes(context -> {
                                    String rawCmd = StringArgumentType.getString(context, "cmd");
                                    String sanitized = CommandSanitizer.sanitizeCommand(rawCmd);
                                    ModConfig config = ModConfig.getInstance();
                                    config.setRecoveryCommand(sanitized);
                                    config.save();
                                    context.getSource().sendFeedback(
                                            Component.literal("§6[PortalEscape]§r Recovery command set to: §e" + config.getRecoveryCommand() + "§r.")
                                    );
                                    return 1;
                                })
                        )
                )
                .then(ClientCommands.literal("autocommand")
                        .then(ClientCommands.argument("enabled", BoolArgumentType.bool())
                                .executes(context -> {
                                    boolean enabled = BoolArgumentType.getBool(context, "enabled");
                                    ModConfig config = ModConfig.getInstance();
                                    config.setAutoCommandEnabled(enabled);
                                    config.save();
                                    context.getSource().sendFeedback(
                                            Component.literal("§6[PortalEscape]§r Auto recovery command set to: §e" + enabled + "§r.")
                                    );
                                    return 1;
                                })
                        )
                )
                .then(ClientCommands.literal("keycommand")
                        .then(ClientCommands.argument("enabled", BoolArgumentType.bool())
                                .executes(context -> {
                                    boolean enabled = BoolArgumentType.getBool(context, "enabled");
                                    ModConfig config = ModConfig.getInstance();
                                    config.setKeyAutoPopulateCommand(enabled);
                                    config.save();
                                    context.getSource().sendFeedback(
                                            Component.literal("§6[PortalEscape]§r Keybind auto-populate set to: §e" + enabled + "§r.")
                                    );
                                    return 1;
                                })
                        )
                );
    }

    private static void showStatus(FabricClientCommandSource source) {
        ModConfig config = ModConfig.getInstance();
        source.sendFeedback(Component.literal("§6=== PortalEscape Status ==="));
        source.sendFeedback(Component.literal("§7- Mod Enabled: §f" + config.isEnabled()));
        source.sendFeedback(Component.literal("§7- Stuck Timeout: §f" + config.getStuckTimeoutSeconds() + "s §8(1.0 - 60.0s)"));
        source.sendFeedback(Component.literal("§7- Recovery Command: §e" + config.getRecoveryCommand()));
        source.sendFeedback(Component.literal("§7- Auto Command Enabled: §f" + config.isAutoCommandEnabled()));
        source.sendFeedback(Component.literal("§7- Keybind Auto-Populate: §f" + config.isKeyAutoPopulateCommand()));
        source.sendFeedback(Component.literal("§7- Cooldown: §f" + config.getCooldownSeconds() + "s §8(1.0 - 300.0s)"));
        source.sendFeedback(Component.literal("§7- Movement Threshold: §f" + config.getMovementThreshold() + " blocks"));
        source.sendFeedback(Component.literal("§7- Coord Confirmation Window: §f" + config.getCoordConfirmationWindowMs() + "ms"));
        source.sendFeedback(Component.literal("§7- Debug Logging: §f" + config.isDebugLogging()));
        source.sendFeedback(Component.literal("§6Available Subcommands:"));
        source.sendFeedback(Component.literal("§e /pe timeout <1.0-60.0>§7 - Set stuck timeout seconds"));
        source.sendFeedback(Component.literal("§e /pe command <command>§7 - Set emergency recovery command"));
        source.sendFeedback(Component.literal("§e /pe autocommand <true|false>§7 - Toggle auto recovery command"));
        source.sendFeedback(Component.literal("§e /pe keycommand <true|false>§7 - Toggle auto-populating command on keypress"));
        source.sendFeedback(Component.literal("§e /pe status§7 - Display current configuration"));
    }
}
