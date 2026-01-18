package me.ascheladd.hytale.quicksigns;

import java.nio.file.Path;

import javax.annotation.Nonnull;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;

import me.ascheladd.hytale.quicksigns.config.QuickSignsConfig;
import me.ascheladd.hytale.quicksigns.listener.BlockBreakListener;
import me.ascheladd.hytale.quicksigns.listener.SignPlaceListener;
import me.ascheladd.hytale.quicksigns.storage.SignHologramStorage;
import me.ascheladd.hytale.quicksigns.ui.SignTextInputSupplier;

/**
 * QuickSigns - Quick sign text editing with holograms for Hytale.
 * Allows players to easily edit sign text with a custom UI.
 */
public final class QuickSigns extends JavaPlugin {
    
    private static QuickSigns instance;
    private static boolean debugMode = false;
    private final Config<QuickSignsConfig> config;
    private SignHologramStorage signHologramStorage;
    
    /**
     * Constructs the QuickSigns plugin.
     * @param init The plugin initialization context
     */
    public QuickSigns(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
        this.config = new Config<>(getDataDirectory(), "config", QuickSignsConfig.CODEC);
    }

    @Override
    protected void setup() {
        getLogger().atInfo().log("Setting up QuickSigns...");
        
        // Load configuration (auto-generates with defaults if missing)
        config.load().thenAccept(cfg -> {
            debugMode = cfg.isDebug();
            getLogger().atInfo().log("Configuration loaded at " + getDataDirectory().resolve("config.json").toAbsolutePath());
            config.save();
        });
        
        // Initialize storage
        Path dataFolder = this.getDataDirectory().toAbsolutePath();
        signHologramStorage = new SignHologramStorage(dataFolder, getLogger());
        
        // Register sign interaction UI for editing signs with F button (not working right now)
        @SuppressWarnings("null")
        @Nonnull var registry = this.getCodecRegistry(OpenCustomUIInteraction.PAGE_CODEC);
        registry.register("QuickSigns_SignEdit", SignTextInputSupplier.class, SignTextInputSupplier.CODEC);
        
        // Register event listeners
        this.getEntityStoreRegistry().registerSystem(new BlockBreakListener(signHologramStorage));
        this.getEntityStoreRegistry().registerSystem(new SignPlaceListener(signHologramStorage));
        
        getLogger().atInfo().log("Enabled! Loaded " + signHologramStorage.getAllSignHolograms().size() + " sign holograms.");
    }

    @Override
    protected void start() {
        getLogger().atInfo().log("QuickSigns started successfully!");
    }

    @Override
    protected void shutdown() {
        getLogger().atInfo().log("QuickSigns shutting down...");
        
        // Save and cleanup storage
        if (signHologramStorage != null) {
            signHologramStorage.shutdown();
        }
        
        getLogger().atInfo().log("QuickSigns shutdown complete!");
    }
    
    /**
     * Gets the plugin instance.
     * @return The QuickSigns plugin instance
     */
    public static QuickSigns getInstance() {
        return instance;
    }
    
    /**
     * Gets the sign hologram storage instance.
     * @return The sign hologram storage
     */
    public SignHologramStorage getSignHologramStorage() {
        return signHologramStorage;
    }
    
    /**
     * Gets the configuration instance.
     * @return The plugin configuration
     */
    public Config<QuickSignsConfig> getConfig() {
        return config;
    }
    
    /**
     * Enable or disable debug mode.
     * @param enabled true to enable debug mode, false to disable
     */
    public static void setDebugMode(boolean enabled) {
        debugMode = enabled;
        getInstance().getLogger().atInfo().log("Debug mode " + (enabled ? "enabled" : "disabled"));
    }
    
    /**
     * Check if debug mode is enabled.
     * @return true if debug mode is enabled, false otherwise
     */
    public static boolean isDebugMode() {
        return debugMode;
    }
    
    /**
     * Log a debug message if debug mode is enabled.
     * @param message The debug message to log
     */
    public static void debug(String message) {
        if (debugMode) {
            getInstance().getLogger().atInfo().log("[DEBUG] " + message);
        }
    }
    
    /**
     * Gets the logger for easier access throughout the plugin.
     * @return The Hytale logger instance
     */
    public static HytaleLogger logger() {
        return getInstance().getLogger();
    }
}
