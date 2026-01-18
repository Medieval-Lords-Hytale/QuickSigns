package me.ascheladd.hytale.quicksigns.listener;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import me.ascheladd.hytale.quicksigns.QuickSigns;
import me.ascheladd.hytale.quicksigns.storage.SignHologramStorage;
import me.ascheladd.hytale.quicksigns.ui.SignTextInputPage;
import me.ascheladd.hytale.quicksigns.util.SignUtil;

/**
 * Handles sign placement to show text input UI.
 */
public class SignPlaceListener extends EntityEventSystem<EntityStore, PlaceBlockEvent> {
    
    private final SignHologramStorage signHologramStorage;
    
    /**
     * Creates a new sign place listener.
     * @param signHologramStorage The sign hologram storage instance
     */
    public SignPlaceListener(SignHologramStorage signHologramStorage) {
        super(PlaceBlockEvent.class);
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
        ItemStack itemStack = ev.getItemInHand();
        Item item = itemStack != null ? itemStack.getItem() : null;
        
        // Check if placing an editable sign
        if (!SignUtil.isEditableSign(item)) {
            QuickSigns.debug("Not an editable sign");
            return;
        }
        
        // Get the entity reference from the archetype chunk
        Ref<EntityStore> entityRef = archetypeChunk.getReferenceTo(index);
        
        // Get the player component from entity ref
        Player player = store.getComponent(entityRef, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(entityRef, PlayerRef.getComponentType());
        
        if (player == null || playerRef == null) {
            QuickSigns.debug("Player or PlayerRef is null");
            return;
        }
        
        var targetBlock = ev.getTargetBlock();
        int signX = targetBlock.x;
        int signY = targetBlock.y;
        int signZ = targetBlock.z;
        
        String worldId = store.getExternalData().getWorld().getName();
        
        QuickSigns.debug("Editable sign placed at " + worldId + ":" + signX + ":" + signY + ":" + signZ);
        
        // Open sign text input page
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
}
