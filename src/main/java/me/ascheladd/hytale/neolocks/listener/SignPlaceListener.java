package me.ascheladd.hytale.neolocks.listener;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import me.ascheladd.hytale.neolocks.storage.ChestLockStorage;
import me.ascheladd.hytale.neolocks.storage.SignHologramStorage;
import me.ascheladd.hytale.neolocks.ui.LockConfirmationPage;
import me.ascheladd.hytale.neolocks.ui.SignTextInputPage;
import me.ascheladd.hytale.neolocks.util.ChestUtil;
import me.ascheladd.hytale.neolocks.util.ChestUtil.ChestPosition;

/**
 * Handles sign placement to show text input UI.
 * If the sign is adjacent to an unlocked chest, offers the option to lock it first.
 */
public class SignPlaceListener extends EntityEventSystem<EntityStore, PlaceBlockEvent> {
    
    private static final String PERMISSION_LOCK = "neolocks.lock";
    
    private final ChestLockStorage storage;
    private final SignHologramStorage signHologramStorage;
    
    public SignPlaceListener(ChestLockStorage storage, SignHologramStorage signHologramStorage) {
        super(PlaceBlockEvent.class);
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
        @Nonnull PlaceBlockEvent ev
    ) {
        // Check if placing a sign
        if (!isSign(ev.getItemInHand())) {
            me.ascheladd.hytale.neolocks.NeoLocks.debug("Not a sign");
            return;
        }
        
        // Get the entity reference from the archetype chunk
        Ref<EntityStore> entityRef = archetypeChunk.getReferenceTo(index);
        
        // Get the player component from entity ref
        Player player = store.getComponent(entityRef, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(entityRef, PlayerRef.getComponentType());
        if (player == null) {
            me.ascheladd.hytale.neolocks.NeoLocks.debug("No permissions");
            return;
        }
        
        // Check if player has permission to lock chests
        if (!player.hasPermission(PERMISSION_LOCK)) {
            player.sendMessage(Message.raw("You don't have permission to lock chests.").color("#FF0000"));
            return;
        }
        var targetBlock = ev.getTargetBlock();
        int signX = targetBlock.x;
        int signY = targetBlock.y;
        int signZ = targetBlock.z;
        
        String worldId = store.getExternalData().getWorld().getName();
        
        // The targetBlock is where the sign will be placed
        // Check adjacent blocks to find if there's a chest
        ChestPosition[] chestPositions = findAdjacentChest(entityRef, signX, signY, signZ);
        
        // Check if any part of the chest is already locked
        if (chestPositions.length > 0) {
            boolean alreadyLocked = false;
            for (ChestPosition pos : chestPositions) {
                if (storage.isLocked(worldId, pos.x, pos.y, pos.z)) {
                    alreadyLocked = true;
                    break;
                }
            }
            
            // If adjacent to unlocked chest, show lock confirmation page with 3 options
            if (!alreadyLocked) {
                // Convert to LockConfirmationPage.ChestPosition[]
                LockConfirmationPage.ChestPosition[] uiChestPositions = 
                    new LockConfirmationPage.ChestPosition[chestPositions.length];
                for (int i = 0; i < chestPositions.length; i++) {
                    uiChestPositions[i] = new LockConfirmationPage.ChestPosition(
                        chestPositions[i].x,
                        chestPositions[i].y,
                        chestPositions[i].z
                    );
                }
                
                LockConfirmationPage lockPage = new LockConfirmationPage(
                    playerRef,
                    storage,
                    signHologramStorage,
                    playerRef.getUuid(),
                    playerRef.getUsername(),
                    worldId,
                    chestPositions[0].x,
                    chestPositions[0].y,
                    chestPositions[0].z,
                    signX,
                    signY,
                    signZ,
                    uiChestPositions
                );
                
                player.getPageManager().openCustomPage(
                    entityRef,
                    store,
                    lockPage
                );
                return;
            }
        }
        
        // No adjacent unlocked chest, open sign text input directly
        SignTextInputPage signTextPage = new SignTextInputPage(
            playerRef,
            playerRef.getUuid(),
            playerRef.getUsername(),
            worldId,
            signX,
            signY,
            signZ,
            signHologramStorage
        );
        
        player.getPageManager().openCustomPage(
            entityRef,
            store,
            signTextPage
        );
    }
    
    /**
     * Finds a chest adjacent to the sign placement position.
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
     * Checks if an ItemStack will place a sign block.
     */
    private boolean isSign(ItemStack itemStack) {
        if (itemStack == null) {
            return false;
        }
        
        String blockKey = itemStack.getBlockKey();
        if (blockKey == null) {
            return false;
        }
        return blockKey.equals("Sign") || blockKey.contains("Sign");
    }
}
