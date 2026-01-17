package me.ascheladd.hytale.neolocks.ui;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import me.ascheladd.hytale.neolocks.model.LockedChest;

/**
 * UI page shown when a player tries to open a locked chest they don't own.
 * Displays information about the chest being locked and who owns it.
 */
public class LockedChestPage extends InteractiveCustomUIPage<LockedChestPage.Data> {
    
    // Data class for handling button clicks
    public static class Data {
        @Nonnull
        public static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
            .append(new KeyedCodec<>("CloseAction", Codec.STRING), 
                (data, value) -> data.action = value, 
                data -> data.action)
            .add()
            .build();
        
        private String action;
        
        public String getAction() {
            return action;
        }
    }
    
    private final LockedChest lockedChest;
    
    public LockedChestPage(@Nonnull PlayerRef playerRef, LockedChest lockedChest) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, Data.CODEC);
        this.lockedChest = lockedChest;
    }
    
    @Override
    public void build(
        @Nonnull Ref<EntityStore> playerEntity,
        @Nonnull UICommandBuilder ui,
        @Nonnull UIEventBuilder events,
        @Nonnull Store<EntityStore> store
    ) {
        // Load the UI file
        ui.append("LockedChest.ui");
        
        // Set owner name
        String ownerName = lockedChest.getOwnerName();
        ui.set("#OwnerName.Text", ownerName != null ? ownerName : "Unknown");
        
        // Set location
        String location = "Location: " + 
            lockedChest.getX() + ", " + 
            lockedChest.getY() + ", " + 
            lockedChest.getZ();
        ui.set("#Location.Text", location);
        
        // Register button click event
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton", 
            EventData.of("CloseAction", "close"), false);
    }
    
    @Override
    public void handleDataEvent(
        @Nonnull Ref<EntityStore> playerEntity,
        @Nonnull Store<EntityStore> store,
        @Nonnull Data data
    ) {
        // Close the page
        close();
    }
    
    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> playerEntity, @Nonnull Store<EntityStore> store) {
        // Cleanup when page closes
    }
}
