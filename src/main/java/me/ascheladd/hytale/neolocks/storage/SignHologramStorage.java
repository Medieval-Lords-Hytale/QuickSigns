package me.ascheladd.hytale.neolocks.storage;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.hypixel.hytale.logger.HytaleLogger;

/**
 * Manages persistent storage of sign hologram mappings.
 * Stores the relationship between sign positions and their hologram entity UUIDs.
 * 
 * Uses UUID-based storage similar to TaleLib's HologramManager to enable
 * hologram deletion after server restarts using EntityStore.getRefFromUUID().
 */
public class SignHologramStorage {
    private final Path storageFile;
    private final HytaleLogger logger;
    
    // Map from sign location key "worldId:x:y:z" to list of hologram UUIDs
    private final Map<String, List<UUID>> signHolograms;
    
    public SignHologramStorage(Path dataFolder, HytaleLogger logger) {
        this.storageFile = dataFolder.resolve("sign_holograms.txt");
        this.logger = logger;
        this.signHolograms = new ConcurrentHashMap<>();
        
        // Create data folder if it doesn't exist
        try {
            Files.createDirectories(dataFolder);
        } catch (IOException e) {
            logger.atSevere().log("Failed to create data folder: " + e.getMessage());
        }
        
        load();
    }
    
    /**
     * Registers a sign hologram mapping.
     */
    public void registerSignHologram(String worldId, int x, int y, int z, UUID entityUuid) {
        String locationKey = makeLocationKey(worldId, x, y, z);
        signHolograms.computeIfAbsent(locationKey, k -> new ArrayList<>()).add(entityUuid);
        save();
        logger.atInfo().log("Registered sign hologram at " + locationKey + " with UUID: " + entityUuid);
    }
    
    /**
     * Gets all hologram UUIDs for a sign location.
     */
    public List<UUID> getSignHolograms(String worldId, int x, int y, int z) {
        String locationKey = makeLocationKey(worldId, x, y, z);
        return signHolograms.get(locationKey);
    }
    
    /**
     * Removes all hologram mappings for a sign location.
     * Returns the UUIDs that were removed.
     */
    public List<UUID> removeSignHolograms(String worldId, int x, int y, int z) {
        String locationKey = makeLocationKey(worldId, x, y, z);
        List<UUID> removed = signHolograms.remove(locationKey);
        if (removed != null && !removed.isEmpty()) {
            save();
            logger.atInfo().log("Removed " + removed.size() + " sign holograms at " + locationKey);
        }
        return removed;
    }
    
    /**
     * Gets all sign hologram mappings.
     */
    public Map<String, List<UUID>> getAllSignHolograms() {
        return new ConcurrentHashMap<>(signHolograms);
    }
    
    /**
     * Creates a location key for sign position.
     */
    private String makeLocationKey(String worldId, int x, int y, int z) {
        return worldId + ":" + x + ":" + y + ":" + z;
    }
    
    /**
     * Loads sign hologram mappings from the storage file.
     */
    private void load() {
        if (!Files.exists(storageFile)) {
            logger.atInfo().log("No existing sign holograms file found, starting fresh");
            return;
        }
        
        int loadedCount = 0;
        
        try (BufferedReader reader = new BufferedReader(new FileReader(storageFile.toFile()))) {
            String line;
            
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                try {
                    parseLine(line);
                    loadedCount++;
                } catch (Exception e) {
                    logger.atWarning().log("Failed to parse sign hologram line: " + line + " - " + e.getMessage());
                }
            }
            
            logger.atInfo().log("Loaded " + loadedCount + " sign hologram mappings from storage");
            
        } catch (IOException e) {
            logger.atSevere().log("Failed to load sign holograms: " + e.getMessage());
        }
    }
    
    /**
     * Saves sign hologram mappings to the storage file.
     */
    private void save() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(storageFile.toFile()))) {
            writer.write("# NeoLocks - Sign Hologram Mappings");
            writer.newLine();
            writer.write("# Format: locationKey|uuid1,uuid2,uuid3");
            writer.newLine();
            writer.write("# locationKey = worldId:x:y:z");
            writer.newLine();
            writer.newLine();
            
            for (Map.Entry<String, List<UUID>> entry : signHolograms.entrySet()) {
                writer.write(formatLine(entry.getKey(), entry.getValue()));
                writer.newLine();
            }
            
        } catch (IOException e) {
            logger.atSevere().log("Failed to save sign holograms: " + e.getMessage());
        }
    }
    
    /**
     * Parses a line from the storage file.
     * Format: locationKey|uuid1,uuid2,uuid3
     */
    private void parseLine(String line) {
        String[] parts = line.split("\\|");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid format, expected 2 parts but got " + parts.length);
        }
        
        String locationKey = parts[0];
        String[] uuidStrs = parts[1].split(",");
        
        List<UUID> uuids = new ArrayList<>();
        for (String uuidStr : uuidStrs) {
            uuids.add(UUID.fromString(uuidStr.trim()));
        }
        
        signHolograms.put(locationKey, uuids);
    }
    
    /**
     * Formats a sign hologram mapping into a line for the storage file.
     * Format: locationKey|uuid1,uuid2,uuid3
     */
    private String formatLine(String locationKey, List<UUID> uuids) {
        StringBuilder ids = new StringBuilder();
        boolean first = true;
        
        for (UUID uuid : uuids) {
            if (!first) ids.append(",");
            ids.append(uuid.toString());
            first = false;
        }
        
        return locationKey + "|" + ids;
    }
}
