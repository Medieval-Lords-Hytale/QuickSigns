package me.ascheladd.hytale.neolocks.ui;

import java.util.UUID;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import me.ascheladd.hytale.neolocks.model.LockedChest;
import me.ascheladd.hytale.neolocks.storage.ChestLockStorage;

/**
 * UI page shown when a player attempts to place a sign on a chest.
 * Allows the player to confirm or cancel locking the chest.
 * Handles both single and double chests.
 */
public class LockConfirmationPage extends InteractiveCustomUIPage<LockConfirmationPage.Data> {
    
    // Data class for handling button clicks
    public static class Data {
        @Nonnull
        public static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
            .append(new KeyedCodec<>("ButtonAction", Codec.STRING), 
                (data, value) -> data.action = value, 
                data -> data.action)
            .add()
            .build();
        
        private String action; // "confirm" or "cancel"
        
        public String getAction() {
            return action;
        }
    }
    
    // Inner class to hold chest position data
    public static class ChestPosition {
        public final int x;
        public final int y;
        public final int z;
        
        public ChestPosition(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
    
    private final ChestLockStorage storage;
    private final UUID playerId;
    private final String playerName;
    private final String worldId;
    private final int chestX;
    private final int chestY;
    private final int chestZ;
    private final int signX;
    private final int signY;
    private final int signZ;
    private final ChestPosition[] chestPositions;
    
    public LockConfirmationPage(
        @Nonnull PlayerRef playerRef,
        ChestLockStorage storage,
        UUID playerId,
        String playerName,
        String worldId,
        int chestX,
        int chestY,
        int chestZ,
        int signX,
        int signY,
        int signZ,
        ChestPosition[] chestPositions
    ) {
        super(playerRef, CustomPageLifetime.CanDismiss, Data.CODEC);
        this.storage = storage;
        this.playerId = playerId;
        this.playerName = playerName;
        this.worldId = worldId;
        this.chestX = chestX;
        this.chestY = chestY;
        this.chestZ = chestZ;
        this.signX = signX;
        this.signY = signY;
        this.signZ = signZ;
        this.chestPositions = chestPositions;
    }
    
    /**
     * Constructor for single chest (backward compatibility).
     */
    public LockConfirmationPage(
        @Nonnull PlayerRef playerRef,
        ChestLockStorage storage,
        UUID playerId,
        String playerName,
        String worldId,
        int chestX,
        int chestY,
        int chestZ
    ) {
        this(playerRef, storage, playerId, playerName, worldId, chestX, chestY, chestZ, 
            chestX, chestY, chestZ,
            new ChestPosition[] { new ChestPosition(chestX, chestY, chestZ) });
    }
    
    @Override
    public void build(
        @Nonnull Ref<EntityStore> playerEntity,
        @Nonnull UICommandBuilder ui,
        @Nonnull UIEventBuilder events,
        @Nonnull Store<EntityStore> store
    ) {
        // Load the UI file
        ui.append("LockConfirmation.ui");
        
        // Set the description text
        String chestType = chestPositions.length > 1 ? "double chest" : "chest";
        String description = "Do you want to lock this " + chestType + "?\n" +
            "Location: " + chestX + ", " + chestY + ", " + chestZ + "\n" +
            (chestPositions.length > 1 ? "(" + chestPositions.length + " blocks)\n" : "") +
            "\nOnly you will be able to open it.";
        
        ui.set("#Description.Text", description);
        
        // Register button click events
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ConfirmButton", 
            EventData.of("ButtonAction", "confirm"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CancelButton", 
            EventData.of("ButtonAction", "cancel"), false);
    }
    
    @Override
    public void handleDataEvent(
        @Nonnull Ref<EntityStore> playerEntity,
        @Nonnull Store<EntityStore> store,
        @Nonnull Data data
    ) {
        if ("confirm".equals(data.getAction())) {
            // Lock all parts of the chest first (handles double chests)
            for (ChestPosition pos : chestPositions) {
                LockedChest chest = new LockedChest(
                    playerId,
                    playerName,
                    worldId,
                    pos.x,
                    pos.y,
                    pos.z
                );
                storage.lockChest(chest);
            }
            
            // Create a text hologram in front of the sign (towards the chest)
            var world = store.getExternalData().getWorld();
            
            world.execute(() -> {
                // Create entity holder
                Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
                
                // Create projectile component (invisible entity shell)
                ProjectileComponent projectileComponent = new ProjectileComponent("Projectile");
                holder.putComponent(ProjectileComponent.getComponentType(), projectileComponent);
                
                // Calculate direction from sign to chest, then reverse for front of sign
                int deltaX = chestX - signX;
                int deltaZ = chestZ - signZ;
                
                // Normalize and apply offset (0.3 blocks AWAY from chest, in front of sign)
                // Negate the offset so it goes opposite direction (towards player, not chest)
                double offsetX = deltaX != 0 ? -Math.signum(deltaX) * 0.3 : 0;
                double offsetZ = deltaZ != 0 ? -Math.signum(deltaZ) * 0.3 : 0;
                
                // Position hologram in front of sign (opposite side from chest)
                Transform hologramTransform = new Transform(
                    signX + 0.5 + offsetX,
                    signY,  // Already centered at sign position
                    signZ + 0.5 + offsetZ,
                    0.0f, 0.0f, 0.0f  // Rotation (yaw, pitch, roll) - Note: Nameplate always faces player
                );
                holder.putComponent(TransformComponent.getComponentType(), 
                    new TransformComponent(hologramTransform.getPosition(), hologramTransform.getRotation()));
                
                // Ensure UUID component
                holder.ensureComponent(UUIDComponent.getComponentType());
                
                // Initialize projectile
                if (projectileComponent.getProjectile() == null) {
                    projectileComponent.initialize();
                    if (projectileComponent.getProjectile() == null) {
                        return;
                    }
                }
                
                // Add network ID
                long networkId = world.getEntityStore().getStore().getExternalData().takeNextNetworkId();
                holder.addComponent(NetworkId.getComponentType(), new NetworkId((int) networkId));
                
                // Add nameplate with text
                String hologramText = playerName + "'s chest";
                holder.addComponent(Nameplate.getComponentType(), new Nameplate(hologramText));
                
                // Spawn the entity and capture reference
                Ref<EntityStore> hologramRef = world.getEntityStore().getStore().addEntity(holder, AddReason.SPAWN);
                
                // Register the hologram for later deletion (TaleLib pattern)
                if (hologramRef != null && hologramRef.isValid()) {
                    me.ascheladd.hytale.neolocks.listener.BlockBreakListener.registerHologram(networkId, hologramRef);
                }
                
                // NOW update the chest(s) with the hologram network ID (inside world thread)
                for (ChestPosition pos : chestPositions) {
                    LockedChest chest = storage.getLockedChest(worldId, pos.x, pos.y, pos.z);
                    if (chest != null) {
                        chest.setHologramNetworkId(networkId);
                        storage.lockChest(chest); // Re-save with the hologram ID
                    }
                }
            });
        }
        
        // Close the page (for both confirm and cancel)
        close();
    }
    
    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> playerEntity, @Nonnull Store<EntityStore> store) {
        // Cleanup when page closes
    }
}
