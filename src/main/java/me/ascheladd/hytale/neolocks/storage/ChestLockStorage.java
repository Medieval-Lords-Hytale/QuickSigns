package me.ascheladd.hytale.neolocks.storage;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.hypixel.hytale.logger.HytaleLogger;

import me.ascheladd.hytale.neolocks.model.LockedChest;
import me.ascheladd.hytale.neolocks.model.LockedChest.BlockPosition;

/**
 * Manages persistent storage of locked chests in a flatfile format.
 * Uses a position-based index for fast lookups.
 */
public class ChestLockStorage {
    private final Path storageFile;
    private final HytaleLogger logger;
    
    // Map from individual block positions to the locked chest
    private final Map<String, LockedChest> positionIndex;
    
    public ChestLockStorage(Path dataFolder, HytaleLogger logger) {
        this.storageFile = dataFolder.resolve("locked_chests.txt");
        this.logger = logger;
        this.positionIndex = new ConcurrentHashMap<>();
        
        // Create data folder if it doesn't exist
        try {
            Files.createDirectories(dataFolder);
        } catch (IOException e) {
            logger.atSevere().log("Failed to create data folder: " + e.getMessage());
        }
        
        load();
    }
    
    /**
     * Locks a chest at the specified location(s).
     * @param chest The chest to lock, containing all its block positions
     */
    public void lockChest(LockedChest chest) {
        // Index each position that makes up this chest
        for (BlockPosition pos : chest.getPositions()) {
            String key = makePositionKey(chest.getWorldId(), pos.x(), pos.y(), pos.z());
            positionIndex.put(key, chest);
        }
        save();
        logger.atInfo().log("Locked chest at " + chest.getLocationKey() + " for " + chest.getOwnerName());
    }
    
    /**
     * Unlocks a chest at the specified location.
     * Removes all positions belonging to the chest.
     */
    public void unlockChest(String worldId, int x, int y, int z) {
        LockedChest chest = getLockedChest(worldId, x, y, z);
        if (chest == null) {
            return;
        }
        
        // Remove all positions of this chest from the index
        for (BlockPosition pos : chest.getPositions()) {
            String key = makePositionKey(worldId, pos.x(), pos.y(), pos.z());
            positionIndex.remove(key);
        }
        
        save();
        logger.atInfo().log("Unlocked chest at " + chest.getLocationKey());
    }
    
    /**
     * Checks if a chest is locked at the specified position.
     */
    public boolean isLocked(String worldId, int x, int y, int z) {
        String key = makePositionKey(worldId, x, y, z);
        return positionIndex.containsKey(key);
    }
    
    /**
     * Gets the locked chest at the specified position.
     */
    public LockedChest getLockedChest(String worldId, int x, int y, int z) {
        String key = makePositionKey(worldId, x, y, z);
        return positionIndex.get(key);
    }
    
    /**
     * Gets all locked chests owned by a specific player.
     */
    public List<LockedChest> getChestsByOwner(UUID ownerId) {
        Set<LockedChest> uniqueChests = new HashSet<>();
        for (LockedChest chest : positionIndex.values()) {
            if (chest.isOwnedBy(ownerId)) {
                uniqueChests.add(chest);
            }
        }
        return new ArrayList<>(uniqueChests);
    }
    
    /**
     * Creates a position key for indexing.
     */
    private String makePositionKey(String worldId, int x, int y, int z) {
        return worldId + ":" + x + "," + y + "," + z;
    }
    
    /**
     * Loads locked chests from the storage file.
     */
    private void load() {
        if (!Files.exists(storageFile)) {
            logger.atInfo().log("No existing locked chests file found, starting fresh");
            return;
        }
        
        Set<LockedChest> loadedChests = new HashSet<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(storageFile.toFile()))) {
            String line;
            
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                try {
                    LockedChest chest = parseLine(line);
                    loadedChests.add(chest);
                } catch (Exception e) {
                    logger.atWarning().log("Failed to parse locked chest line: " + line + " - " + e.getMessage());
                }
            }
            
            // Build index from loaded chests
            for (LockedChest chest : loadedChests) {
                for (BlockPosition pos : chest.getPositions()) {
                    String key = makePositionKey(chest.getWorldId(), pos.x(), pos.y(), pos.z());
                    positionIndex.put(key, chest);
                }
            }
            
            logger.atInfo().log("Loaded " + loadedChests.size() + " locked chests from storage");
            
        } catch (IOException e) {
            logger.atSevere().log("Failed to load locked chests: " + e.getMessage());
        }
    }
    
    /**
     * Saves locked chests to the storage file.
     */
    private void save() {
        // Get unique chests (position index may have duplicates)
        Set<LockedChest> uniqueChests = new HashSet<>(positionIndex.values());
        
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(storageFile.toFile()))) {
            writer.write("# NeoLocks - Locked Chests Storage");
            writer.newLine();
            writer.write("# Format: ownerId|ownerName|worldId|hologramNetworkId|positions");
            writer.newLine();
            writer.write("# positions = x,y,z;x,y,z (semicolon-separated for multi-block chests)");
            writer.newLine();
            writer.write("# hologramNetworkId = network ID of hologram entity (or empty if none)");
            writer.newLine();
            writer.newLine();
            
            for (LockedChest chest : uniqueChests) {
                writer.write(formatLine(chest));
                writer.newLine();
            }
            
        } catch (IOException e) {
            logger.atSevere().log("Failed to save locked chests: " + e.getMessage());
        }
    }
    
    /**
     * Parses a line from the storage file into a LockedChest.
     * Format: ownerId|ownerName|worldId|hologramNetworkId|x,y,z;x,y,z
     * Legacy format (v1): ownerId|ownerName|worldId|x,y,z;x,y,z
     */
    private LockedChest parseLine(String line) {
        String[] parts = line.split("\\|");
        if (parts.length != 4 && parts.length != 5) {
            throw new IllegalArgumentException("Invalid format, expected 4 or 5 parts but got " + parts.length);
        }
        
        UUID ownerId = UUID.fromString(parts[0]);
        String ownerName = parts[1];
        String worldId = parts[2];
        
        // Check if this is the new format with hologram ID
        Long hologramNetworkId = null;
        String positionsData;
        
        if (parts.length == 5) {
            // New format: has hologram ID
            if (!parts[3].isEmpty() && !parts[3].equals("null")) {
                hologramNetworkId = Long.parseLong(parts[3]);
            }
            positionsData = parts[4];
        } else {
            // Legacy format: no hologram ID
            positionsData = parts[3];
        }
        
        // Parse positions (semicolon-separated)
        String[] positionsStr = positionsData.split(";");
        Set<BlockPosition> positions = new HashSet<>();
        
        for (String posStr : positionsStr) {
            String[] coords = posStr.split(",");
            if (coords.length != 3) {
                throw new IllegalArgumentException("Invalid position format: " + posStr);
            }
            int x = Integer.parseInt(coords[0]);
            int y = Integer.parseInt(coords[1]);
            int z = Integer.parseInt(coords[2]);
            positions.add(new BlockPosition(x, y, z));
        }
        
        LockedChest chest = new LockedChest(ownerId, ownerName, worldId, positions);
        chest.setHologramNetworkId(hologramNetworkId);
        return chest;
    }
    
    /**
     * Formats a LockedChest into a line for the storage file.
     * Format: ownerId|ownerName|worldId|hologramNetworkId|x,y,z;x,y,z
     */
    private String formatLine(LockedChest chest) {
        StringBuilder positions = new StringBuilder();
        boolean first = true;
        
        // Sort positions for consistent output
        List<BlockPosition> sortedPositions = chest.getPositions().stream()
            .sorted()
            .toList();
        
        for (BlockPosition pos : sortedPositions) {
            if (!first) positions.append(";");
            positions.append(pos.x()).append(",").append(pos.y()).append(",").append(pos.z());
            first = false;
        }
        
        String hologramId = chest.getHologramNetworkId() != null ? 
            chest.getHologramNetworkId().toString() : "";
        
        return chest.getOwnerId() + "|" +
               chest.getOwnerName() + "|" +
               chest.getWorldId() + "|" +
               hologramId + "|" +
               positions;
    }
    
    /**
     * Gets the total number of locked chests.
     */
    public int getLockedChestCount() {
        // Count unique chests (index may have multiple entries per chest)
        return new HashSet<>(positionIndex.values()).size();
    }
}
