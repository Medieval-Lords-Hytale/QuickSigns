package me.ascheladd.hytale.neolocks;

import java.nio.file.Path;

import javax.annotation.Nonnull;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import me.ascheladd.hytale.neolocks.listener.BlockBreakListener;
import me.ascheladd.hytale.neolocks.listener.ChestOpenListener;
import me.ascheladd.hytale.neolocks.listener.SignPlaceListener;
import me.ascheladd.hytale.neolocks.storage.ChestLockStorage;

/**
 * NeoLocks - A chest locking plugin for Hytale.
 * Allows players to lock chests so that only they can open them.
 */
public final class NeoLocks extends JavaPlugin {
    
    private ChestLockStorage storage;
    
    public NeoLocks(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        getLogger().atInfo().log("Setting up...");
        
        // Initialize storage
        Path dataFolder = this.getDataDirectory().toAbsolutePath();
        storage = new ChestLockStorage(dataFolder, getLogger());
        
        // Initialize and register chest listener
        this.getEntityStoreRegistry().registerSystem(new ChestOpenListener(storage));
        
        // Initialize and register sign placement listener
        this.getEntityStoreRegistry().registerSystem(new SignPlaceListener(storage));
        
        // Initialize and register block break listener
        this.getEntityStoreRegistry().registerSystem(new BlockBreakListener(storage));
        
        getLogger().atInfo().log("Enabled! Loaded " + storage.getLockedChestCount() + " locked chests.");
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
     * Gets the chest lock storage instance.
     */
    public ChestLockStorage getStorage() {
        return storage;
    }
}