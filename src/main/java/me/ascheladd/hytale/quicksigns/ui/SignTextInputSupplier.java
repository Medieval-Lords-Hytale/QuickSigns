package me.ascheladd.hytale.quicksigns.ui;

import java.util.UUID;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;

import me.ascheladd.hytale.quicksigns.QuickSigns;
import me.ascheladd.hytale.quicksigns.storage.SignHologramStorage;
import me.ascheladd.hytale.quicksigns.util.SignUtil;

/**
 * Supplier that creates SignTextInputPage when a sign with holograms is interacted with.
 * Used to register the interaction hint system.
 */
public class SignTextInputSupplier implements OpenCustomUIInteraction.CustomPageSupplier {

    /**
     * Codec for serializing/deserializing SignTextInputSupplier.
     */
    public static final BuilderCodec<SignTextInputSupplier> CODEC = 
        BuilderCodec.builder(SignTextInputSupplier.class, SignTextInputSupplier::new).build();
    
    /**
     * Creates a new SignTextInputSupplier.
     */
    public SignTextInputSupplier() {
    }

    @Override
    public CustomUIPage tryCreate(
        Ref<EntityStore> ref, 
        ComponentAccessor<EntityStore> componentAccessor, 
        PlayerRef playerRef, 
        InteractionContext context
    ) {
        // Get the block being interacted with
        Vector3i blockPos = TargetUtil.getTargetBlock(ref, 5, ref.getStore());
        if (blockPos == null) {
            return null;
        }
        
        // Check if the block is an editable sign (Also it's not lumberjack or construction sign)
        Store<EntityStore> store = ref.getStore();
        BlockType blockType = store.getExternalData().getWorld().getBlockType(blockPos.x, blockPos.y, blockPos.z);
        var item = blockType != null ? blockType.getItem() : null;
        if (!SignUtil.isEditableSign(item)) {
            return null;
        }

        // Get world info from the store
        String worldId = store.getExternalData().getWorld().getName();
        
        // Check if this sign has holograms (for editing existing text)
        // If no holograms exist, we'll still show the UI to create new ones
        SignHologramStorage storage = QuickSigns.getInstance().getSignHologramStorage();

        // Get player info
        if (playerRef == null) {
            return null; // No player ref
        }
        
        String playerName = playerRef.getUsername();
        UUID playerUuid = playerRef.getUuid();
        
        // Create and return the sign editor page
        return new SignTextInputPage(
            playerRef,
            playerUuid,
            playerName,
            worldId,
            blockPos.x,
            blockPos.y,
            blockPos.z,
            storage
        );
    }
}


