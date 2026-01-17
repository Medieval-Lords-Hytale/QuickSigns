package me.ascheladd.hytale.neolocks.listener;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import me.ascheladd.hytale.neolocks.model.LockedChest;
import me.ascheladd.hytale.neolocks.storage.ChestLockStorage;
import me.ascheladd.hytale.neolocks.storage.SignHologramStorage;
import me.ascheladd.hytale.neolocks.util.ChestUtil;
import me.ascheladd.hytale.neolocks.util.ChestUtil.ChestPosition;

/**
 * Handles block breaking events for signs (unlocking) and chests (protection).
 */
public class BlockBreakListener extends EntityEventSystem<EntityStore, BreakBlockEvent> {
    
    private static final String PERMISSION_BYPASS = "neolocks.bypass";
    
    private final ChestLockStorage storage;
    private final SignHologramStorage signHologramStorage;
    
    /**
     * In-memory map tracking hologram entity references by network ID.
     * This allows us to properly delete holograms when signs are broken.
     */
    private static final Map<Long, Ref<EntityStore>> hologramRefs = new ConcurrentHashMap<>();
    
    /**
     * Registers a hologram entity reference for later cleanup.
     * Call this after spawning a hologram to enable deletion.
     */
    public static void registerHologram(long networkId, Ref<EntityStore> entityRef) {
        hologramRefs.put(networkId, entityRef);
        me.ascheladd.hytale.neolocks.NeoLocks.debug("Registered chest hologram with NetworkId: " + networkId + " (total tracked: " + hologramRefs.size() + ")");
    }
    
    /**
     * Registers a sign text hologram by location.
     * Call this after spawning sign text holograms to enable deletion when sign is broken.
     * This method is called from SignTextInputPage.
     */
    public static void registerSignHologram(String worldId, int x, int y, int z, long networkId, Ref<EntityStore> entityRef) {
        hologramRefs.put(networkId, entityRef);
        me.ascheladd.hytale.neolocks.NeoLocks.debug("Registered sign text hologram at " + worldId + ":" + x + ":" + y + ":" + z + " with NetworkId: " + networkId + " (ref valid: " + entityRef.isValid() + ")");
        // Note: Persistence is handled by SignTextInputPage calling signHologramStorage.registerSignHologram
    }
    
    public BlockBreakListener(ChestLockStorage storage, SignHologramStorage signHologramStorage) {
        super(BreakBlockEvent.class);
        this.storage = storage;
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
        Ref<EntityStore> entityRef = archetypeChunk.getReferenceTo(index);
        Player player = store.getComponent(entityRef, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(entityRef, PlayerRef.getComponentType());
        
        if (player == null || playerRef == null) {
            return;
        }
        
        BlockType blockType = ev.getBlockType();
        var targetBlock = ev.getTargetBlock();
        int blockX = targetBlock.x;
        int blockY = targetBlock.y;
        int blockZ = targetBlock.z;
        
        String worldId = store.getExternalData().getWorld().getName();
        
        // Check if breaking a sign
        if (isSign(blockType)) {
            handleSignBreak(player, playerRef, worldId, blockX, blockY, blockZ, entityRef, ev);
        }
        // Check if breaking a chest
        else if (ChestUtil.isChest(blockType)) {
            handleChestBreak(player, playerRef, worldId, blockX, blockY, blockZ, ev);
        }
    }
    
    /**
     * Handle breaking a sign - check if it unlocks a chest.
     */
    private void handleSignBreak(Player player, PlayerRef playerRef, String worldId, 
                                  int signX, int signY, int signZ, Ref<EntityStore> entityRef,
                                  BreakBlockEvent ev) {
        // Delete any sign text holograms at this location
        deleteSignHolograms(worldId, signX, signY, signZ, entityRef);
        
        // Find adjacent chest
        ChestPosition[] chestPositions = findAdjacentChest(entityRef, signX, signY, signZ);
        
        if (chestPositions.length == 0) {
            return; // No chest adjacent, just a regular sign
        }
        
        // Check if the chest is locked
        for (ChestPosition pos : chestPositions) {
            if (storage.isLocked(worldId, pos.x, pos.y, pos.z)) {
                LockedChest existingLock = storage.getLockedChest(worldId, pos.x, pos.y, pos.z);
                
                // Check if player owns the chest
                if (existingLock.isOwnedBy(playerRef.getUuid())) {
                    // Delete hologram if it exists
                    if (existingLock.hasHologram()) {
                        me.ascheladd.hytale.neolocks.NeoLocks.debug("Unlocking chest - attempting to delete hologram with NetworkId: " + existingLock.getHologramNetworkId());
                        deleteHologram(entityRef, existingLock.getHologramNetworkId());
                    } else {
                        me.ascheladd.hytale.neolocks.NeoLocks.debug("Unlocking chest - no hologram network ID stored");
                    }
                    
                    // Unlock all parts of the chest
                    for (ChestPosition unlockPos : chestPositions) {
                        storage.unlockChest(worldId, unlockPos.x, unlockPos.y, unlockPos.z);
                    }
                    // Send unlock confirmation message
                    player.sendMessage(Message.raw("Chest unlocked!").color("#00FF00"));
                    return;
                } else {
                    // Not the owner, prevent breaking the sign
                    player.sendMessage(Message.raw("You cannot break this sign - it locks someone else's chest.").color("#FF0000"));
                    ev.setCancelled(true);
                    return;
                }
            }
        }
    }
    
    /**
     * Handle breaking a chest - prevent if locked.
     */
    private void handleChestBreak(Player player, PlayerRef playerRef, String worldId, 
                                   int chestX, int chestY, int chestZ, BreakBlockEvent ev) {
        // Check if this chest is locked
        if (storage.isLocked(worldId, chestX, chestY, chestZ)) {
            LockedChest lockedChest = storage.getLockedChest(worldId, chestX, chestY, chestZ);
            
            // Allow owner to break their own chest
            if (lockedChest.isOwnedBy(playerRef.getUuid())) {
                return; // Owner can break it
            }
            
            // Check bypass permission
            if (player.hasPermission(PERMISSION_BYPASS)) {
                return; // Bypass permission allows breaking
            }
            
            // Prevent breaking
            player.sendMessage(Message.raw("This chest is locked! Break the sign to unlock it first.").color("#FF0000"));
            ev.setCancelled(true);
        }
    }
    
    /**
     * Deletes all sign text holograms at the specified location.
     */
    private void deleteSignHolograms(String worldId, int x, int y, int z, Ref<EntityStore> playerEntityRef) {
        List<UUID> uuids = signHologramStorage.removeSignHolograms(worldId, x, y, z);
        
        if (uuids == null || uuids.isEmpty()) {
            me.ascheladd.hytale.neolocks.NeoLocks.debug("No sign holograms found in storage for this location");
            return;
        }
        
        me.ascheladd.hytale.neolocks.NeoLocks.debug("Deleting " + uuids.size() + " sign text holograms at " + worldId + ":" + x + ":" + y + ":" + z);
        for (java.util.UUID uuid : uuids) {
            me.ascheladd.hytale.neolocks.NeoLocks.debug("Attempting to delete hologram UUID: " + uuid);
            deleteHologramByUuid(playerEntityRef, uuid);
        }
    }
    
    /**
     * Deletes a hologram entity by its UUID using TaleLib's approach.
     * This works even after server restart because UUIDs are persisted.
     */
    private void deleteHologramByUuid(Ref<EntityStore> playerEntityRef, java.util.UUID entityUuid) {
        me.ascheladd.hytale.neolocks.NeoLocks.debug("deleteHologramByUuid called for UUID: " + entityUuid);
        
        try {
            var store = playerEntityRef.getStore();
            var world = store.getExternalData().getWorld();
            
            me.ascheladd.hytale.neolocks.NeoLocks.debug("Scheduling hologram deletion on world thread");
            
            // Execute on world thread for thread safety
            world.execute(() -> {
                var entityStore = world.getEntityStore();
                
                // TaleLib approach: Get entity reference from UUID
                // Note: getRefFromUUID is a method on EntityStore, not Store<EntityStore>
                Ref<EntityStore> hologramRef = entityStore.getRefFromUUID(java.util.Objects.requireNonNull(entityUuid));
                
                if (hologramRef == null) {
                    me.ascheladd.hytale.neolocks.NeoLocks.getInstance().getLogger().atWarning().log("Could not find entity with UUID: " + entityUuid);
                    me.ascheladd.hytale.neolocks.NeoLocks.getInstance().getLogger().atWarning().log("Entity may have already been removed or UUID is invalid");
                    return;
                }
                
                // Validate reference before deletion
                if (!hologramRef.isValid()) {
                    me.ascheladd.hytale.neolocks.NeoLocks.debug("Hologram reference is not valid for UUID: " + entityUuid);
                    me.ascheladd.hytale.neolocks.NeoLocks.debug("Entity may have already been removed");
                    return;
                }
                
                try {
                    // Remove entity using TaleLib's pattern:
                    // store.removeEntity(ref, holder, reason)
                    Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
                    entityStore.getStore().removeEntity(hologramRef, holder, RemoveReason.REMOVE);
                    me.ascheladd.hytale.neolocks.NeoLocks.debug("✓ Successfully deleted hologram with UUID: " + entityUuid);
                } catch (Exception e) {
                    me.ascheladd.hytale.neolocks.NeoLocks.getInstance().getLogger().atSevere().log("Failed to remove hologram entity: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        } catch (Exception e) {
            me.ascheladd.hytale.neolocks.NeoLocks.getInstance().getLogger().atSevere().log("Failed to schedule hologram deletion: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Deletes a hologram entity by its network ID (legacy method for cached references).
     */
    private void deleteHologram(Ref<EntityStore> playerEntityRef, long networkId) {
        me.ascheladd.hytale.neolocks.NeoLocks.debug("deleteHologram called for NetworkId: " + networkId);
        me.ascheladd.hytale.neolocks.NeoLocks.debug("hologramRefs map size: " + hologramRefs.size());
        
        // Retrieve the hologram entity reference from our tracking map
        Ref<EntityStore> hologramRef = hologramRefs.remove(networkId);
        
        if (hologramRef == null) {
            me.ascheladd.hytale.neolocks.NeoLocks.debug("No hologram reference found in memory for NetworkId: " + networkId);
            me.ascheladd.hytale.neolocks.NeoLocks.debug("Attempting to find entity by UUID (TaleLib approach)...");
            
            // TaleLib approach: Try to find the entity by looking it up from stored UUID
            // However, we only have networkId, not UUID. The issue is that sign holograms
            // don't have their UUIDs persisted in SignHologramStorage
            me.ascheladd.hytale.neolocks.NeoLocks.getInstance().getLogger().atWarning().log("Cannot delete hologram - no UUID stored for NetworkId: " + networkId);
            me.ascheladd.hytale.neolocks.NeoLocks.getInstance().getLogger().atWarning().log("NOTE: Sign holograms created before server restart cannot be deleted");
            me.ascheladd.hytale.neolocks.NeoLocks.getInstance().getLogger().atWarning().log("SOLUTION: Need to persist entity UUIDs in addition to network IDs");
            return;
        }
        
        me.ascheladd.hytale.neolocks.NeoLocks.debug("Found hologram reference in memory, checking validity...");
        
        // Check if entity reference is still valid
        if (!hologramRef.isValid()) {
            me.ascheladd.hytale.neolocks.NeoLocks.debug("Hologram reference is no longer valid for NetworkId: " + networkId);
            me.ascheladd.hytale.neolocks.NeoLocks.debug("Entity may have already been removed");
            return;
        }
        
        me.ascheladd.hytale.neolocks.NeoLocks.debug("Hologram reference is valid, proceeding with deletion");
        
        try {
            var store = playerEntityRef.getStore();
            var world = store.getExternalData().getWorld();
            
            me.ascheladd.hytale.neolocks.NeoLocks.debug("Scheduling hologram deletion on world thread for NetworkId: " + networkId);
            
            // Execute on world thread for thread safety
            world.execute(() -> {
                // Double-check validity on world thread
                if (!hologramRef.isValid()) {
                    me.ascheladd.hytale.neolocks.NeoLocks.debug("Hologram became invalid before deletion for NetworkId: " + networkId);
                    return;
                }
                
                var entityStore = world.getEntityStore();
                
                try {
                    // Remove entity using TaleLib's pattern:
                    // store.removeEntity(ref, holder, reason)
                    Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
                    entityStore.getStore().removeEntity(hologramRef, holder, RemoveReason.REMOVE);
                    me.ascheladd.hytale.neolocks.NeoLocks.debug("✓ Successfully deleted hologram with NetworkId: " + networkId);
                } catch (Exception e) {
                    me.ascheladd.hytale.neolocks.NeoLocks.getInstance().getLogger().atSevere().log("Failed to remove hologram entity: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        } catch (Exception e) {
            me.ascheladd.hytale.neolocks.NeoLocks.getInstance().getLogger().atSevere().log("Failed to schedule hologram deletion: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**     * Finds a chest adjacent to the sign position.
     * Checks all 6 cardinal directions (up, down, north, south, east, west).
     * 
     * @param entityRef The entity reference
     * @param signX Sign X position
     * @param signY Sign Y position
     * @param signZ Sign Z position
     * @return Array of chest positions if found, empty array otherwise
     */
    private ChestPosition[] findAdjacentChest(Ref<EntityStore> entityRef, int signX, int signY, int signZ) {
        // Check all 6 cardinal directions
        int[][] directions = {
            {0, 1, 0},   // Up
            {0, -1, 0},  // Down
            {1, 0, 0},   // East (+X)
            {-1, 0, 0},  // West (-X)
            {0, 0, 1},   // South (+Z)
            {0, 0, -1}   // North (-Z)
        };
        
        for (int[] dir : directions) {
            int chestX = signX + dir[0];
            int chestY = signY + dir[1];
            int chestZ = signZ + dir[2];
            
            ChestPosition[] positions = ChestUtil.getChestPositions(entityRef, chestX, chestY, chestZ);
            if (positions.length > 0) {
                return positions; // Found a chest
            }
        }
        
        return new ChestPosition[0]; // No chest found
    }
    
    /**
     * Checks if a BlockType is a sign.
     */
    private boolean isSign(BlockType blockType) {
        if (blockType == null) {
            return false;
        }
        
        String blockId = blockType.getId();
        if (blockId == null) {
            return false;
        }
        
        return blockId.equals("Sign") || blockId.contains("Sign");
    }
}
