package me.ascheladd.hytale.neolocks.listener;

import java.util.Map;
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
import me.ascheladd.hytale.neolocks.util.ChestUtil;
import me.ascheladd.hytale.neolocks.util.ChestUtil.ChestPosition;

/**
 * Handles block breaking events for signs (unlocking) and chests (protection).
 */
public class BlockBreakListener extends EntityEventSystem<EntityStore, BreakBlockEvent> {
    
    private static final String PERMISSION_BYPASS = "neolocks.bypass";
    
    private final ChestLockStorage storage;
    
    /**
     * In-memory map tracking hologram entity references by network ID.
     * This allows us to properly delete holograms when signs are broken.
     * Pattern inspired by TaleLib's HologramManager.
     */
    private static final Map<Long, Ref<EntityStore>> hologramRefs = new ConcurrentHashMap<>();
    
    /**
     * Registers a hologram entity reference for later cleanup.
     * Call this after spawning a hologram to enable deletion.
     */
    public static void registerHologram(long networkId, Ref<EntityStore> entityRef) {
        hologramRefs.put(networkId, entityRef);
        System.out.println("Registered hologram with NetworkId: " + networkId + " (total tracked: " + hologramRefs.size() + ")");
    }
    
    public BlockBreakListener(ChestLockStorage storage) {
        super(BreakBlockEvent.class);
        this.storage = storage;
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
                        System.out.println("Unlocking chest - attempting to delete hologram with NetworkId: " + existingLock.getHologramNetworkId());
                        deleteHologram(entityRef, existingLock.getHologramNetworkId());
                    } else {
                        System.out.println("Unlocking chest - no hologram network ID stored");
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
     * Deletes a hologram entity by its network ID.
     */
    private void deleteHologram(Ref<EntityStore> playerEntityRef, long networkId) {
        // Retrieve the hologram entity reference from our tracking map
        Ref<EntityStore> hologramRef = hologramRefs.remove(networkId);
        
        if (hologramRef == null) {
            System.out.println("No hologram reference found for NetworkId: " + networkId);
            return;
        }
        
        // Check if entity reference is still valid
        if (!hologramRef.isValid()) {
            System.out.println("Hologram reference is no longer valid for NetworkId: " + networkId);
            return;
        }
        
        try {
            var store = playerEntityRef.getStore();
            var world = store.getExternalData().getWorld();
            
            // Execute on world thread for thread safety
            world.execute(() -> {
                // Double-check validity on world thread
                if (!hologramRef.isValid()) {
                    return;
                }
                
                var entityStore = world.getEntityStore();
                
                try {
                    // Remove entity using TaleLib's pattern:
                    // store.removeEntity(ref, holder, reason)
                    Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
                    entityStore.getStore().removeEntity(hologramRef, holder, RemoveReason.REMOVE);
                    System.out.println("Successfully deleted hologram with NetworkId: " + networkId);
                } catch (Exception e) {
                    System.err.println("Failed to remove hologram entity: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        } catch (Exception e) {
            System.err.println("Failed to schedule hologram deletion: " + e.getMessage());
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
