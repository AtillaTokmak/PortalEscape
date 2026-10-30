package com.portalescape.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.portalescape.util.CommandSanitizer;
import com.portalescape.util.SafeLogger;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;

/**
 * Thread-safe, validated JSON configuration for PortalEscape.
 * Enforces schema boundary limits, fallback defaults on malformed JSON,
 * and atomic disk writes.
 */
public class ModConfig {
    public static final double MIN_STUCK_TIMEOUT = 1.0;
    public static final double MAX_STUCK_TIMEOUT = 60.0;
    public static final double DEFAULT_STUCK_TIMEOUT = 5.0;

    public static final double MIN_MOVEMENT_THRESHOLD = 0.001;
    public static final double MAX_MOVEMENT_THRESHOLD = 5.0;
    public static final double DEFAULT_MOVEMENT_THRESHOLD = 0.05;

    public static final double MIN_COOLDOWN = 1.0;
    public static final double MAX_COOLDOWN = 300.0;
    public static final double DEFAULT_COOLDOWN = 10.0;

    public static final long MIN_COORD_CONFIRMATION_WINDOW_MS = 500L;
    public static final long MAX_COORD_CONFIRMATION_WINDOW_MS = 5000L;
    public static final long DEFAULT_COORD_CONFIRMATION_WINDOW_MS = 2000L;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ModConfig instance;

    // Config Properties
    private boolean enabled = true;
    private double stuckTimeoutSeconds = DEFAULT_STUCK_TIMEOUT;
    private double movementThreshold = DEFAULT_MOVEMENT_THRESHOLD;
    private String recoveryCommand = CommandSanitizer.DEFAULT_COMMAND;
    private double cooldownSeconds = DEFAULT_COOLDOWN;
    private boolean debugLogging = false;
    private boolean autoCommandEnabled = true;
    private boolean keyAutoPopulateCommand = true;
    private long coordConfirmationWindowMs = DEFAULT_COORD_CONFIRMATION_WINDOW_MS;

    private final transient Object lock = new Object();
    private transient Path configPath;

    public ModConfig() {
        // default constructor
    }

    public static synchronized ModConfig getInstance() {
        if (instance == null) {
            Path defaultPath = getDefaultConfigPath();
            instance = loadOrCreate(defaultPath);
        }
        return instance;
    }

    public static synchronized void setInstance(ModConfig config) {
        instance = config;
    }

    public static Path getDefaultConfigPath() {
        try {
            return FabricLoader.getInstance().getConfigDir().resolve("portalescape.json");
        } catch (Throwable t) {
            // Fallback for tests outside fabric environment
            return Path.of("config", "portalescape.json");
        }
    }

    public static ModConfig loadOrCreate(Path path) {
        ModConfig config = new ModConfig();
        config.configPath = path;

        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
                if (loaded != null) {
                    config.copyFrom(loaded);
                    config.validateAndClamp();
                    SafeLogger.info("PortalEscape configuration loaded successfully from {}", path.getFileName());
                    return config;
                }
            } catch (JsonSyntaxException | IOException e) {
                SafeLogger.warn("Malformed or unreadable config file at {}. Creating backup and restoring defaults.", path);
                createCorruptedBackup(path);
            }
        }

        // Fresh creation or recovery from corrupted config
        config.validateAndClamp();
        config.save();
        return config;
    }

    private static void createCorruptedBackup(Path original) {
        try {
            Path backup = original.resolveSibling(original.getFileName() + ".corrupted." + Instant.now().toEpochMilli());
            Files.copy(original, backup, StandardCopyOption.REPLACE_EXISTING);
            SafeLogger.warn("Backed up corrupted config to {}", backup.getFileName());
        } catch (IOException ignored) {}
    }

    public void copyFrom(ModConfig other) {
        synchronized (this.lock) {
            this.enabled = other.enabled;
            this.stuckTimeoutSeconds = other.stuckTimeoutSeconds;
            this.movementThreshold = other.movementThreshold;
            this.recoveryCommand = other.recoveryCommand;
            this.cooldownSeconds = other.cooldownSeconds;
            this.debugLogging = other.debugLogging;
            this.autoCommandEnabled = other.autoCommandEnabled;
            this.keyAutoPopulateCommand = other.keyAutoPopulateCommand;
            this.coordConfirmationWindowMs = other.coordConfirmationWindowMs;
        }
    }

    public void validateAndClamp() {
        synchronized (this.lock) {
            // Validate stuck timeout
            if (Double.isNaN(stuckTimeoutSeconds) || Double.isInfinite(stuckTimeoutSeconds)) {
                stuckTimeoutSeconds = DEFAULT_STUCK_TIMEOUT;
            } else if (stuckTimeoutSeconds < MIN_STUCK_TIMEOUT) {
                stuckTimeoutSeconds = MIN_STUCK_TIMEOUT;
            } else if (stuckTimeoutSeconds > MAX_STUCK_TIMEOUT) {
                stuckTimeoutSeconds = MAX_STUCK_TIMEOUT;
            }

            // Validate movement threshold
            if (Double.isNaN(movementThreshold) || Double.isInfinite(movementThreshold)) {
                movementThreshold = DEFAULT_MOVEMENT_THRESHOLD;
            } else if (movementThreshold < MIN_MOVEMENT_THRESHOLD) {
                movementThreshold = MIN_MOVEMENT_THRESHOLD;
            } else if (movementThreshold > MAX_MOVEMENT_THRESHOLD) {
                movementThreshold = MAX_MOVEMENT_THRESHOLD;
            }

            // Validate cooldown
            if (Double.isNaN(cooldownSeconds) || Double.isInfinite(cooldownSeconds)) {
                cooldownSeconds = DEFAULT_COOLDOWN;
            } else if (cooldownSeconds < MIN_COOLDOWN) {
                cooldownSeconds = MIN_COOLDOWN;
            } else if (cooldownSeconds > MAX_COOLDOWN) {
                cooldownSeconds = MAX_COOLDOWN;
            }

            // Validate coord confirmation window
            if (coordConfirmationWindowMs < MIN_COORD_CONFIRMATION_WINDOW_MS) {
                coordConfirmationWindowMs = MIN_COORD_CONFIRMATION_WINDOW_MS;
            } else if (coordConfirmationWindowMs > MAX_COORD_CONFIRMATION_WINDOW_MS) {
                coordConfirmationWindowMs = MAX_COORD_CONFIRMATION_WINDOW_MS;
            }

            // Sanitize recovery command and block recursive calls
            this.recoveryCommand = CommandSanitizer.sanitizeCommand(this.recoveryCommand);
            if (CommandSanitizer.isRecursiveCommand(this.recoveryCommand)) {
                this.recoveryCommand = CommandSanitizer.DEFAULT_COMMAND;
            }
        }
    }

    public void save() {
        synchronized (this.lock) {
            if (configPath == null) {
                return;
            }
            try {
                if (configPath.getParent() != null) {
                    Files.createDirectories(configPath.getParent());
                }
                Path tempPath = configPath.resolveSibling(configPath.getFileName() + ".tmp");
                try (Writer writer = Files.newBufferedWriter(tempPath)) {
                    GSON.toJson(this, writer);
                }
                Files.move(tempPath, configPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                SafeLogger.error("Failed to write configuration file atomically to " + configPath, e);
            }
        }
    }

    public boolean isEnabled() {
        synchronized (this.lock) {
            return enabled;
        }
    }

    public void setEnabled(boolean enabled) {
        synchronized (this.lock) {
            this.enabled = enabled;
        }
    }

    public double getStuckTimeoutSeconds() {
        synchronized (this.lock) {
            return stuckTimeoutSeconds;
        }
    }

    public void setStuckTimeoutSeconds(double seconds) {
        synchronized (this.lock) {
            this.stuckTimeoutSeconds = seconds;
            validateAndClamp();
        }
    }

    public double getMovementThreshold() {
        synchronized (this.lock) {
            return movementThreshold;
        }
    }

    public void setMovementThreshold(double threshold) {
        synchronized (this.lock) {
            this.movementThreshold = threshold;
            validateAndClamp();
        }
    }

    public String getRecoveryCommand() {
        synchronized (this.lock) {
            return recoveryCommand;
        }
    }

    public void setRecoveryCommand(String command) {
        synchronized (this.lock) {
            if (CommandSanitizer.isRecursiveCommand(command)) {
                this.recoveryCommand = CommandSanitizer.DEFAULT_COMMAND;
            } else {
                this.recoveryCommand = command;
            }
            validateAndClamp();
        }
    }

    public double getCooldownSeconds() {
        synchronized (this.lock) {
            return cooldownSeconds;
        }
    }

    public void setCooldownSeconds(double seconds) {
        synchronized (this.lock) {
            this.cooldownSeconds = seconds;
            validateAndClamp();
        }
    }

    public boolean isDebugLogging() {
        synchronized (this.lock) {
            return debugLogging;
        }
    }

    public void setDebugLogging(boolean debugLogging) {
        synchronized (this.lock) {
            this.debugLogging = debugLogging;
        }
    }

    public boolean isAutoCommandEnabled() {
        synchronized (this.lock) {
            return autoCommandEnabled;
        }
    }

    public void setAutoCommandEnabled(boolean autoCommandEnabled) {
        synchronized (this.lock) {
            this.autoCommandEnabled = autoCommandEnabled;
        }
    }

    public boolean isKeyAutoPopulateCommand() {
        synchronized (this.lock) {
            return keyAutoPopulateCommand;
        }
    }

    public void setKeyAutoPopulateCommand(boolean keyAutoPopulateCommand) {
        synchronized (this.lock) {
            this.keyAutoPopulateCommand = keyAutoPopulateCommand;
        }
    }

    public long getCoordConfirmationWindowMs() {
        synchronized (this.lock) {
            return coordConfirmationWindowMs;
        }
    }

    public void setCoordConfirmationWindowMs(long coordConfirmationWindowMs) {
        synchronized (this.lock) {
            this.coordConfirmationWindowMs = coordConfirmationWindowMs;
            validateAndClamp();
        }
    }

    public Path getConfigPath() {
        return configPath;
    }

    public void setConfigPath(Path configPath) {
        this.configPath = configPath;
    }
}
