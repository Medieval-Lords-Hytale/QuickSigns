package me.ascheladd.hytale.neolocks.ui;

import java.util.UUID;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import me.ascheladd.hytale.neolocks.model.LockedChest;
import me.ascheladd.hytale.neolocks.storage.ChestLockStorage;
import me.ascheladd.hytale.neolocks.storage.SignHologramStorage;
import me.ascheladd.hytale.neolocks.util.HologramUtil;

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
    private final SignHologramStorage signHologramStorage;
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
        SignHologramStorage signHologramStorage,
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
        this.signHologramStorage = signHologramStorage;
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
        this(playerRef, storage, null, playerId, playerName, worldId, chestX, chestY, chestZ, 
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
        events.addEventBinding(CustomUIEventBindingType.Activating, "#LockButton", 
            EventData.of("ButtonAction", "lock"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#PlaceSignButton", 
            EventData.of("ButtonAction", "placesign"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CancelButton", 
            EventData.of("ButtonAction", "cancel"), false);
    }
    
    @Override
    public void handleDataEvent(
        @Nonnull Ref<EntityStore> playerEntity,
        @Nonnull Store<EntityStore> store,
        @Nonnull Data data
    ) {
        String action = data.getAction();
        
        if ("lock".equals(action)) {
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
            
            // Create a text hologram in front of the sign
            var world = store.getExternalData().getWorld();
            
            // Get player position for offset calculation
            var transformComponent = store.getComponent(playerEntity, TransformComponent.getComponentType());
            if (transformComponent == null) return;
            var playerPos = transformComponent.getPosition();
            
            world.execute(() -> {
                String hologramText = playerName + "'s chest";
                HologramUtil.HologramResult result = HologramUtil.createHologram(
                    world,
                    signX,
                    signY,
                    signZ,
                    playerPos.x,
                    playerPos.z,
                    hologramText
                );
                
                if (result != null) {
                    me.ascheladd.hytale.neolocks.listener.BlockBreakListener.registerHologram(
                        result.networkId, result.entityRef
                    );
                    
                    // update the chest(s) with the hologram network ID
                    for (ChestPosition pos : chestPositions) {
                        LockedChest chest = storage.getLockedChest(worldId, pos.x, pos.y, pos.z);
                        if (chest != null) {
                            chest.setHologramNetworkId(result.networkId);
                            storage.lockChest(chest); // Re-save with the hologram ID
                        }
                    }
                }
            });
            
            close();
        } else if ("placesign".equals(action)) {
            // Open sign text input page
            var player = store.getComponent(playerEntity, Player.getComponentType());
            var playerRefComponent = store.getComponent(playerEntity, PlayerRef.getComponentType());
            if (player != null && playerRefComponent != null) {
                SignTextInputPage signTextPage = new SignTextInputPage(
                    playerRefComponent,
                    playerId,
                    playerName,
                    worldId,
                    signX,
                    signY,
                    signZ,
                    signHologramStorage
                );
                player.getPageManager().openCustomPage(playerEntity, store, signTextPage);
            }
            // Don't close here - opening new page will handle it
        } else if ("cancel".equals(action)) {
            close();
        }
    }
    
    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> playerEntity, @Nonnull Store<EntityStore> store) {
        // Cleanup when page closes
    }
}
