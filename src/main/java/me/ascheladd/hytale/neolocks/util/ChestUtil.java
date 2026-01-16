package me.ascheladd.hytale.neolocks.util;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Utility class for chest-related operations.
 */
public class ChestUtil {
    
    /**
     * Checks if a BlockType is a chest by checking its state data.
     * A chest is identified by having a StateData with id='container'.
     * 
     * @param blockType The block type to check
     * @return true if the block is a chest/container, false otherwise
     */
    public static boolean isChest(BlockType blockType) {        
        // Primary Method: Check if the block has container state data
        var state = blockType.getState();
        if (state != null) {
            System.out.println(state);
            String stateId = state.getId();
            System.out.println("State id" + stateId);
            if (stateId != null && stateId.contains("container")) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Gets all block positions for a chest (handles double chests).
     * For a single chest, returns an array with one position.
     * For a double chest, returns an array with both positions.
     * 
     * Double chests are only formed when:
     * - Two chests are adjacent (horizontally)
     * - Both chests have the same yaw rotation (facing the same direction)
     * 
     * @param entityRef The entity reference from the event context
     * @param x The x coordinate of the chest block
     * @param y The y coordinate of the chest block
     * @param z The z coordinate of the chest block
     * @return Array of chest positions (size 1 for single chest, size 2 for double chest, empty if not a chest)
     */
    public static ChestPosition[] getChestPositions(Ref<EntityStore> entityRef, int x, int y, int z) {
        try {
            if (entityRef == null || !entityRef.isValid()) {
                System.out.println("Entity ref not valid");
                return new ChestPosition[0];
            }
            
            // Get the world from the entity store
            var world = entityRef.getStore().getExternalData().getWorld();
            
            // Calculate chunk coordinates and index
            int chunkX = ChunkUtil.chunkCoordinate(x);
            int chunkZ = ChunkUtil.chunkCoordinate(z);
            long chunkIndex = ChunkUtil.indexChunk(chunkX, chunkZ);
            
            // Get the chunk (should be loaded already since player is interacting with it)
            var chunk = world.getChunkIfLoaded(chunkIndex);
            if (chunk == null) {
                System.out.println("Chunk not loaded");
                return new ChestPosition[0];
            }
            
            // Get the current block type to verify it's a chest
            var blockType = chunk.getBlockType(x, y, z);
            if (!isChest(blockType)) {
                System.out.println("Block is not a chest (ChestPosition) " + blockType + " at " + x + "," + y + "," + z);
                return new ChestPosition[0];
            }
            
            // Get the rotation of the current chest via BlockState
            var currentState = chunk.getState(x, y, z);
            if (currentState == null) {
                System.out.println("Current state is null");
                return new ChestPosition[] { new ChestPosition(x, y, z) };
            }
            
            int currentRotationIndex = currentState.getRotationIndex();
            var currentRotation = RotationTuple.get(currentRotationIndex);
            if (currentRotation == null) {
                System.out.println("Current rotation is null");
                return new ChestPosition[] { new ChestPosition(x, y, z) };
            }
            
            // Check all 4 adjacent horizontal blocks for another chest
            ChestPosition[] adjacentPositions = {
                new ChestPosition(x + 1, y, z), // East
                new ChestPosition(x - 1, y, z), // West
                new ChestPosition(x, y, z + 1), // South
                new ChestPosition(x, y, z - 1)  // North
            };
            
            for (ChestPosition adjPos : adjacentPositions) {
                try {
                    // Calculate chunk for adjacent position (might be different chunk)
                    int adjChunkX = ChunkUtil.chunkCoordinate(adjPos.x);
                    int adjChunkZ = ChunkUtil.chunkCoordinate(adjPos.z);
                    
                    var adjChunk = chunk; // Default to same chunk
                    
                    // If adjacent block is in a different chunk, try to load it
                    if (adjChunkX != chunkX || adjChunkZ != chunkZ) {
                        long adjChunkIndex = ChunkUtil.indexChunk(adjChunkX, adjChunkZ);
                        adjChunk = world.getChunkIfLoaded(adjChunkIndex);
                        if (adjChunk == null) {
                            continue; // Skip if chunk not loaded
                        }
                    }
                    
                    // Get block type at adjacent position
                    var adjBlockType = adjChunk.getBlockType(adjPos.x, adjPos.y, adjPos.z);
                    if (!isChest(adjBlockType)) {
                        continue; // Not a chest, skip
                    }
                    
                    // Get rotation of the adjacent chest via BlockState
                    var adjState = adjChunk.getState(adjPos.x, adjPos.y, adjPos.z);
                    if (adjState == null) {
                        continue; // No state info, skip
                    }
                    
                    int adjRotationIndex = adjState.getRotationIndex();
                    var adjRotation = RotationTuple.get(adjRotationIndex);
                    if (adjRotation == null) {
                        continue; // No rotation info, skip
                    }
                    
                    // Check if both chests have the same yaw rotation (facing same direction)
                    // This is required for them to form a double chest
                    if (currentRotation.yaw() == adjRotation.yaw()) {
                        // Found a connected double chest!
                        return new ChestPosition[] { 
                            new ChestPosition(x, y, z), 
                            adjPos 
                        };
                    }
                } catch (Exception e) {
                    // Continue checking other positions
                }
            }
            
            // No connected chest found, it's a single chest
            return new ChestPosition[] { new ChestPosition(x, y, z) };
            
        } catch (Exception e) {
            // Fallback to empty array on error
            return new ChestPosition[0];
        }
    }
    
    /**
     * Helper class to store chest block positions.
     */
    public static class ChestPosition {
        public final int x;
        public final int y;
        public final int z;
        
        public ChestPosition(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
        
        @Override
        public String toString() {
            return "ChestPosition{x=" + x + ", y=" + y + ", z=" + z + "}";
        }
    }
}
