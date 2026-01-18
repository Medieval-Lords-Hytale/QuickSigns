package me.ascheladd.hytale.quicksigns.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bson.BsonDocument;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.logger.HytaleLogger;

/**
 * Manages persistent storage of sign hologram mappings using JSON.
 * Stores the relationship between sign positions and their hoHytale's Codec system.
 * Stores the relationship between sign positions and their hologram entity UUIDs.
 * 
 * Uses BuilderCodec with MapCodec for type-safe JSON serialization.
 * 
 * Implements async saving with 15-minute autosave intervals to improve performance.
 * Data is saved asynchronously to prevent blocking the main thread.
 */
public class SignHologramStorage {
    private static final long AUTOSAVE_INTERVAL_MINUTES = 15;
    
    private final Path storageFile;
    private final HytaleLogger logger;
    private final ScheduledExecutorService saveExecutor;
    private final AtomicBoolean dirty;
    
    // Map from sign location key "worldId:x:y:z" to list of hologram UUIDs
    private final Map<String, List<UUID>> signHolograms;
    
    /**
     * Creates a new sign hologram storage.
     * @param dataFolder The data folder for storage files
     * @param logger The logger instance
     */
    public SignHologramStorage(Path dataFolder, HytaleLogger logger) {
        this.storageFile = dataFolder.resolve("sign_holograms.json");
        this.logger = logger;
        this.signHolograms = new ConcurrentHashMap<>();
        this.dirty = new AtomicBoolean(false);
        this.saveExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "SignHolograms-AutoSave");
            thread.setDaemon(true);
            return thread;
        });
        
        // Create data folder if it doesn't exist
        try {
            Files.createDirectories(dataFolder);
        } catch (IOException e) {
            logger.atSevere().log("Failed to create data folder: " + e.getMessage());
        }
        
        load();
        
        // Start autosave task (every 15 minutes)
        saveExecutor.scheduleAtFixedRate(() -> {
            if (dirty.get()) {
                saveAsync();
            }
        }, AUTOSAVE_INTERVAL_MINUTES, AUTOSAVE_INTERVAL_MINUTES, TimeUnit.MINUTES);
        
        logger.atInfo().log("Autosave enabled with " + AUTOSAVE_INTERVAL_MINUTES + " minute interval");
    }
    
    /**
     * Registers a sign hologram mapping.
     * @param worldId The world ID
     * @param x The x coordinate
     * @param y The y coordinate
     * @param z The z coordinate
     * @param entityUuid The hologram entity UUID
     */
    public void registerSignHologram(String worldId, int x, int y, int z, UUID entityUuid) {
        String locationKey = makeLocationKey(worldId, x, y, z);
        signHolograms.computeIfAbsent(locationKey, k -> new ArrayList<>()).add(entityUuid);
        markDirty();
        logger.atInfo().log("Registered sign hologram at " + locationKey + " with UUID: " + entityUuid);
    }
    
    /**
     * Gets all hologram UUIDs for a sign location.
     * @param worldId The world ID
     * @param x The x coordinate
     * @param y The y coordinate
     * @param z The z coordinate
     * @return List of hologram UUIDs, or null if none exist
     */
    public List<UUID> getSignHolograms(String worldId, int x, int y, int z) {
        String locationKey = makeLocationKey(worldId, x, y, z);
        return signHolograms.get(locationKey);
    }
    
    /**
     * Removes all hologram mappings for a sign location.
     * Returns the UUIDs that were removed.
     * @param worldId The world ID
     * @param x The x coordinate
     * @param y The y coordinate
     * @param z The z coordinate
     * @return List of removed hologram UUIDs, or null if none existed
     */
    public List<UUID> removeSignHolograms(String worldId, int x, int y, int z) {
        String locationKey = makeLocationKey(worldId, x, y, z);
        List<UUID> removed = signHolograms.remove(locationKey);
        if (removed != null && !removed.isEmpty()) {
            markDirty();
            logger.atInfo().log("Removed " + removed.size() + " sign holograms at " + locationKey);
        }
        return removed;
    }
    
    /**
     * Gets all sign hologram mappings.
     * @return A copy of all sign hologram mappings
     */
    public Map<String, List<UUID>> getAllSignHolograms() {
        return new ConcurrentHashMap<>(signHolograms);
    }
    
    /**
     * Checks if there is a tracked sign at the specified location.
     * @param worldId The world ID
     * @param x The x coordinate
     * @param y The y coordinate
     * @param z The z coordinate
     * @return true if a sign exists at this location, false otherwise
     */
    public boolean hasSignAt(String worldId, int x, int y, int z) {
        String key = makeLocationKey(worldId, x, y, z);
        return signHolograms.containsKey(key);
    }
    
    /**
     * Creates a location key for sign position.
     */
    private String makeLocationKey(String worldId, int x, int y, int z) {
        return worldId + ":" + x + ":" + y + ":" + z;
    }
    
    /**
     * Loads sign hologram mappings from JSON storage file.
     */
    private void load() {
        if (!Files.exists(storageFile)) {
            logger.atInfo().log("No existing sign holograms file found, starting fresh");
            return;
        }
        
        try {
            String json = Files.readString(storageFile);
            BsonDocument document = BsonDocument.parse(json);
            
            if (document == null) {
                logger.atWarning().log("Failed to parse sign holograms JSON: document is null");
                return;
            }
            
            SignHologramData data = SignHologramData.CODEC.decode(document, new ExtraInfo());
            signHolograms.putAll(data.getSignHolograms());
            
            logger.atInfo().log("Loaded " + signHolograms.size() + " sign hologram mappings from storage");
            
        } catch (IOException e) {
            logger.atSevere().log("Failed to load sign holograms: " + e.getMessage());
        } catch (Exception e) {
            logger.atSevere().log("Failed to parse sign holograms JSON: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Marks data as dirty (needing save).
     */
    private void markDirty() {
        dirty.set(true);
    }
    
    /**
     * Saves data asynchronously if dirty.
     * Safe to call from any thread.
     */
    public void saveAsync() {
        if (!dirty.get()) {
            return; // No changes to save
        }
        
        saveExecutor.execute(() -> {
            save();
        });
    }
    
    /**
     * Saves data synchronously (blocks until complete).
     * Should be called on server shutdown to ensure all data is saved.
     */
    public void saveSync() {
        if (dirty.get()) {
            save();
        }
    }
    
    /**
     * Saves sign hologram mappings to JSON storage file using Codec.
     * Should only be called internally or by saveSync/saveAsync.
     */
    private void save() {
        if (!dirty.compareAndSet(true, false)) {
            return; // Already saved or no changes
        }
        
        try {
            SignHologramData data = new SignHologramData(new ConcurrentHashMap<>(signHolograms));
            BsonDocument document = SignHologramData.CODEC.encode(data, new ExtraInfo());
            
            String json = document.toJson();
            Files.writeString(storageFile, json);
            
            logger.atInfo().log("Saved " + signHolograms.size() + " sign hologram mappings");
            
        } catch (IOException e) {
            logger.atSevere().log("Failed to save sign holograms: " + e.getMessage());
            dirty.set(true); // Mark dirty again so we retry next time
        } catch (Exception e) {
            logger.atSevere().log("Failed to encode sign holograms to JSON: " + e.getMessage());
            e.printStackTrace();
            dirty.set(true); // Mark dirty again so we retry next time
        }
    }
    
    /**
     * Shuts down the autosave executor and performs final save.
     * Must be called on plugin shutdown to ensure data is saved and threads are cleaned up.
     */
    public void shutdown() {
        logger.atInfo().log("Shutting down SignHologramStorage...");
        
        // Stop accepting new tasks
        saveExecutor.shutdown();
        
        try {
            // Wait for any pending saves to complete (max 5 seconds)
            if (!saveExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                saveExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            saveExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // Final synchronous save
        saveSync();
        logger.atInfo().log("SignHologramStorage shutdown complete");
    }
}
