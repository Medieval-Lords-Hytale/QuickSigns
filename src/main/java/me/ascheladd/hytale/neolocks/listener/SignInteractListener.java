package me.ascheladd.hytale.neolocks.listener;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import me.ascheladd.hytale.neolocks.storage.SignHologramStorage;
import me.ascheladd.hytale.neolocks.ui.SignTextInputPage;

/**
 * Handles sign interaction events.
 * When a player presses F (interact) on a sign that has holograms attached, allows them to edit the text.
 * 
 * Note: Interaction hints (the prompt text shown when looking at the block) should be added
 * through block metadata or client-side components, which requires further API exploration.
 */
public class SignInteractListener extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {
    
    private final SignHologramStorage signHologramStorage;
    
    public SignInteractListener(SignHologramStorage signHologramStorage) {
        super(UseBlockEvent.Pre.class);
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
        @Nonnull UseBlockEvent.Pre ev
    ) {
        // Get block type and position
        BlockType blockType = ev.getBlockType();
        var targetBlock = ev.getTargetBlock();
        
        // Check if it's a sign
        if (!isSign(blockType)) {
            return;
        }
        
        // Check if it's an Interact action (F key)
        if (ev.getInteractionType() != InteractionType.Use) {
            return;
        }
        
        Ref<EntityStore> entityRef = ev.getContext().getEntity();
        
        int x = targetBlock.x;
        int y = targetBlock.y;
        int z = targetBlock.z;
        
        String worldId = entityRef.getStore().getExternalData().getWorld().getName();
        
        // Get the player components
        Player player = entityRef.getStore().getComponent(entityRef, Player.getComponentType());
        PlayerRef playerRef = entityRef.getStore().getComponent(entityRef, PlayerRef.getComponentType());
        
        if (player == null || playerRef == null) {
            return;
        }
        
        // Check if this sign has holograms attached
        if (!hasHolograms(worldId, x, y, z)) {
            return; // No holograms, let vanilla sign behavior happen
        }
        
        // Cancel the default sign interaction
        ev.setCancelled(true);
        
        // Send a helpful message with color formatting
        player.sendMessage(Message.raw("§a✎ Opening sign editor... §7(Press F to edit signs)"));
        
        // Open the sign text input page to edit the text
        SignTextInputPage signTextPage = new SignTextInputPage(
            playerRef,
            playerRef.getUuid(),
            playerRef.getUsername(),
            worldId,
            x,
            y,
            z,
            signHologramStorage
        );
        
        player.getPageManager().openCustomPage(
            entityRef,
            store,
            signTextPage
        );
    }
    
    /**
     * Checks if a sign at the given location has holograms attached.
     */
    private boolean hasHolograms(String worldId, int x, int y, int z) {
        var holograms = signHologramStorage.getSignHolograms(worldId, x, y, z);
        return holograms != null && !holograms.isEmpty();
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
