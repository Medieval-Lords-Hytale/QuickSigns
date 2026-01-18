package me.ascheladd.hytale.quicksigns.listener;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import me.ascheladd.hytale.quicksigns.QuickSigns;
import me.ascheladd.hytale.quicksigns.storage.SignHologramStorage;
import me.ascheladd.hytale.quicksigns.util.SignUtil;

/**
 * Handles block breaking events for signs - deletes associated holograms.
 */
public class BlockBreakListener extends EntityEventSystem<EntityStore, BreakBlockEvent> {
    
    private final SignHologramStorage signHologramStorage;
    
    /**
     * Creates a new block break listener.
     * @param signHologramStorage The sign hologram storage instance
     */
    public BlockBreakListener(SignHologramStorage signHologramStorage) {
        super(BreakBlockEvent.class);
        this.signHologramStorage = signHologramStorage;
    }
    
    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }
    
    @Override
    public void handle(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull BreakBlockEvent ev
    ) {
        BlockType blockType = ev.getBlockType();
        var targetBlock = ev.getTargetBlock();
        int blockX = targetBlock.x;
        int blockY = targetBlock.y;
        int blockZ = targetBlock.z;
        
        String worldId = store.getExternalData().getWorld().getName();
        var item = blockType != null ? blockType.getItem() : null;

        Ref<EntityStore> entityRef = archetypeChunk.getReferenceTo(index);
        
        // 1. Check if the broken block itself is a sign
        if (SignUtil.isEditableSign(item)) {
            deleteSignHolograms(worldId, blockX, blockY, blockZ, entityRef);
        }
        
        // 2. Check for tracked signs adjacent/above this block that might break due to physics
        checkNearbyTrackedSigns(worldId, blockX, blockY, blockZ, entityRef, store);
    }
    
    /**
     * Checks nearby positions for tracked signs that might have been affected by this break.
     * Wall-mounted signs can be attached to blocks, so we check all 4 cardinal directions.
     * Checks are delayed to allow physics to process before verifying sign existence.
     */
    private void checkNearbyTrackedSigns(String worldId, int x, int y, int z, Ref<EntityStore> entityRef, Store<EntityStore> store) {
        // Check cardinal directions where wall-mounted signs might be affected
        int[][] checkPositions = {
            {x + 1, y, z},     // East
            {x - 1, y, z},     // West
            {x, y, z + 1},     // South
            {x, y, z - 1}      // North
        };
        
        var world = store.getExternalData().getWorld();
        
        for (int[] pos : checkPositions) {
            // Check if we have a tracked sign at this position
            if (signHologramStorage.hasSignAt(worldId, pos[0], pos[1], pos[2])) {
                // Schedule a delayed check to allow physics to process
                int checkX = pos[0];
                int checkY = pos[1];
                int checkZ = pos[2];
                
                world.execute(() -> {
                    // Verify the block still exists and is still a sign after physics has processed
                    try {
                        var blockType = world.getBlockType(checkX, checkY, checkZ);
                        var blockItem = blockType != null ? blockType.getItem() : null;
                        
                        // If it's no longer a sign, clean up the holograms
                        if (!SignUtil.isEditableSign(blockItem)) {
                            QuickSigns.debug("Detected missing sign at " + checkX + "," + checkY + "," + checkZ + " (was affected by block break)");
                            deleteSignHolograms(worldId, checkX, checkY, checkZ, entityRef);
                        } else {
                            QuickSigns.debug("Sign at " + checkX + "," + checkY + "," + checkZ + " still exists, not cleaning up");
                        }
                    } catch (Exception e) {
                        // If we can't check the block, assume it's gone and clean up
                        QuickSigns.debug("Could not verify sign at " + checkX + "," + checkY + "," + checkZ + ", cleaning up");
                        deleteSignHolograms(worldId, checkX, checkY, checkZ, entityRef);
                    }
                });
            }
        }
    }
    
    /**
     * Deletes all sign text holograms at the specified location.
     */
    private void deleteSignHolograms(String worldId, int x, int y, int z, Ref<EntityStore> playerEntityRef) {
        List<UUID> uuids = signHologramStorage.removeSignHolograms(worldId, x, y, z);
        
        if (uuids == null || uuids.isEmpty()) {
            QuickSigns.debug("No sign holograms found in storage for this location");
            return;
        }
        
        QuickSigns.debug("Deleting " + uuids.size() + " sign text holograms at " + worldId + ":" + x + ":" + y + ":" + z);
        for (UUID uuid : uuids) {
            QuickSigns.debug("Attempting to delete hologram UUID: " + uuid);
            deleteHologramByUuid(playerEntityRef, uuid);
        }
    }
    
    /**
     * Deletes a hologram entity by its UUID
     */
    private void deleteHologramByUuid(Ref<EntityStore> playerEntityRef, UUID entityUuid) {
        QuickSigns.debug("deleteHologramByUuid called for UUID: " + entityUuid);
        
        try {
            var store = playerEntityRef.getStore();
            var world = store.getExternalData().getWorld();
            
            QuickSigns.debug("Scheduling hologram deletion on world thread");
            
            // Execute on world thread for thread safety
            world.execute(() -> {
                var entityStore = world.getEntityStore();
                
                // Note: getRefFromUUID is a method on EntityStore, not Store<EntityStore>
                Ref<EntityStore> hologramRef = entityStore.getRefFromUUID(java.util.Objects.requireNonNull(entityUuid));
                
                if (hologramRef == null) {
                    QuickSigns.logger().atWarning().log("Could not find entity with UUID: " + entityUuid);
                    QuickSigns.logger().atWarning().log("Entity may have already been removed or UUID is invalid");
                    return;
                }
                
                // Validate reference before deletion
                if (!hologramRef.isValid()) {
                    QuickSigns.debug("Hologram reference is not valid for UUID: " + entityUuid);
                    QuickSigns.debug("Entity may have already been removed");
                    return;
                }
                
                try {
                    Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
                    entityStore.getStore().removeEntity(hologramRef, holder, RemoveReason.REMOVE);
                    QuickSigns.debug("✓ Successfully deleted hologram with UUID: " + entityUuid);
                } catch (Exception e) {
                    QuickSigns.logger().atSevere().log("Failed to remove hologram entity: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        } catch (Exception e) {
            QuickSigns.logger().atSevere().log("Failed to schedule hologram deletion: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
