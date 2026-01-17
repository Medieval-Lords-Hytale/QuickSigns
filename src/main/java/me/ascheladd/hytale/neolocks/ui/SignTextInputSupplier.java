package me.ascheladd.hytale.neolocks.ui;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;

import me.ascheladd.hytale.neolocks.NeoLocks;
import me.ascheladd.hytale.neolocks.storage.SignHologramStorage;

/**
 * Supplier that creates SignTextInputPage when a sign with holograms is interacted with.
 * Used to register the interaction hint system.
 */
public class SignTextInputSupplier implements OpenCustomUIInteraction.CustomPageSupplier {

    public static final BuilderCodec<SignTextInputSupplier> CODEC = 
        BuilderCodec.builder(SignTextInputSupplier.class, SignTextInputSupplier::new).build();

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

        // Get world info from the store
        Store<EntityStore> store = ref.getStore();
        String worldId = store.getExternalData().getWorld().getName();
        
        // Check if this sign has holograms
        SignHologramStorage storage = NeoLocks.getInstance().getSignHologramStorage();
        if (storage.getSignHolograms(worldId, blockPos.x, blockPos.y, blockPos.z) == null 
            || storage.getSignHolograms(worldId, blockPos.x, blockPos.y, blockPos.z).isEmpty()) {
            return null; // No holograms, don't open UI
        }

        // Get player info
        if (playerRef == null) {
            return null; // No player ref
        }
        
        String playerName = playerRef.getUsername();
        java.util.UUID playerUuid = playerRef.getUuid();
        
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
