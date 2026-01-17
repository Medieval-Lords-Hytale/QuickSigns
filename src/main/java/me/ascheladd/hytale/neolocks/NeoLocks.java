package me.ascheladd.hytale.neolocks;

import java.nio.file.Path;

import javax.annotation.Nonnull;

import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;

import me.ascheladd.hytale.neolocks.config.NeoLocksConfig;
import me.ascheladd.hytale.neolocks.listener.BlockBreakListener;
import me.ascheladd.hytale.neolocks.listener.ChestOpenListener;
import me.ascheladd.hytale.neolocks.listener.SignPlaceListener;
import me.ascheladd.hytale.neolocks.storage.ChestLockStorage;
import me.ascheladd.hytale.neolocks.storage.SignHologramStorage;
import me.ascheladd.hytale.neolocks.ui.SignTextInputSupplier;

/**
 * NeoLocks - A chest locking plugin for Hytale.
 * Allows players to lock chests so that only they can open them.
 */
public final class NeoLocks extends JavaPlugin {
    
    private static NeoLocks instance;
    private static boolean debugMode = false;
    private final Config<NeoLocksConfig> config;
    private ChestLockStorage storage;
    private SignHologramStorage signHologramStorage;
    
    public NeoLocks(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
        this.config = new Config<>(getDataDirectory(), "config.json", NeoLocksConfig.CODEC);
    }

    @Override
    protected void setup() {
        getLogger().atInfo().log("Setting up...");
        
        // Load configuration
        config.load().thenAccept(cfg -> {
            debugMode = cfg.isDebug();
            getLogger().atInfo().log("Configuration loaded. Debug mode: " + debugMode);
        });
        
        // Initialize storage
        Path dataFolder = this.getDataDirectory().toAbsolutePath();
        storage = new ChestLockStorage(dataFolder, getLogger());
        signHologramStorage = new SignHologramStorage(dataFolder, getLogger());
        
        // Register sign interaction UI for editing signs with F button
        @SuppressWarnings("null")
        var registry = this.getCodecRegistry(OpenCustomUIInteraction.PAGE_CODEC);
        registry.register("NeoLocks_SignEdit", SignTextInputSupplier.class, SignTextInputSupplier.CODEC);
        
        // Initialize and register chest listener
        this.getEntityStoreRegistry().registerSystem(new ChestOpenListener(storage));
        
        // Initialize and register sign placement listener
        this.getEntityStoreRegistry().registerSystem(new SignPlaceListener(storage, signHologramStorage));
        
        // Initialize and register block break listener
        this.getEntityStoreRegistry().registerSystem(new BlockBreakListener(storage, signHologramStorage));
        
        getLogger().atInfo().log("Enabled! Loaded " + storage.getLockedChestCount() + " locked chests.");
        
        // Log warning about sign hologram persistence
        int signHologramCount = signHologramStorage.getAllSignHolograms().size();
        if (signHologramCount > 0) {
            getLogger().atWarning().log("Found " + signHologramCount + " sign hologram mappings from previous session.");
            getLogger().atWarning().log("Note: Sign holograms from before server restart cannot be automatically deleted.");
            getLogger().atWarning().log("They will remain visible until manually removed or the world is reset.");
        }
    }

    @Override
    protected void start() {
        getLogger().atInfo().log("Started successfully!");
    }

    @Override
    protected void shutdown() {
        getLogger().atInfo().log("Shutting down...");
    }
    
    /**
     * Gets the plugin instance.
     */
    public static NeoLocks getInstance() {
        return instance;
    }
    
    /**
     * Gets the chest lock storage instance.
     */
    public ChestLockStorage getStorage() {
        return storage;
    }
    
    /**
     * Gets the sign hologram storage instance.
     */
    public SignHologramStorage getSignHologramStorage() {
        return signHologramStorage;
    }
    
    /**
     * Gets the configuration instance.
     */
    public Config<NeoLocksConfig> getConfig() {
        return config;
    }
    
    /**
     * Enable or disable debug mode.
     */
    public static void setDebugMode(boolean enabled) {
        debugMode = enabled;
        getInstance().getLogger().atInfo().log("Debug mode " + (enabled ? "enabled" : "disabled"));
    }
    
    /**
     * Check if debug mode is enabled.
     */
    public static boolean isDebugMode() {
        return debugMode;
    }
    
    /**
     * Log a debug message if debug mode is enabled.
     */
    public static void debug(String message) {
        if (debugMode) {
            getInstance().getLogger().atInfo().log("[DEBUG] " + message);
        }
    }
}