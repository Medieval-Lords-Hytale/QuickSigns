package me.ascheladd.hytale.neolocks.ui;

import java.util.UUID;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import me.ascheladd.hytale.neolocks.storage.SignHologramStorage;
import me.ascheladd.hytale.neolocks.util.HologramUtil;

/**
 * UI page for entering sign text when placing a sign.
 * Allows up to 4 lines of text, 16 characters each.
 */
public class SignTextInputPage extends CustomUIPage {
    
    private final String playerName;
    private final String worldId;
    private final int signX;
    private final double signY;
    private final int signZ;
    private final SignHologramStorage signHologramStorage;
    
    /**
     * Create sign text input page.
     * 
     * @param playerRef Player reference
     * @param playerId Player UUID
     * @param playerName Player name
     * @param worldId World ID
     * @param signX Sign X coordinate
     * @param signY Sign Y coordinate
     * @param signZ Sign Z coordinate
     * @param signHologramStorage Sign hologram storage for persistence
     */
    public SignTextInputPage(
        @Nonnull PlayerRef playerRef,
        UUID playerId,
        String playerName,
        String worldId,
        int signX,
        double signY,
        int signZ,
        SignHologramStorage signHologramStorage
    ) {
        super(playerRef, CustomPageLifetime.CanDismiss);
        this.playerName = playerName;
        this.worldId = worldId;
        this.signX = signX;
        this.signY = signY;
        this.signZ = signZ;
        this.signHologramStorage = signHologramStorage;
        me.ascheladd.hytale.neolocks.NeoLocks.debug("SignTextInputPage created for sign at " + worldId + ":" + signX + ":" + signY + ":" + signZ);
    }
    
    @Override
    public void build(
        @Nonnull Ref<EntityStore> playerEntity,
        @Nonnull UICommandBuilder ui,
        @Nonnull UIEventBuilder events,
        @Nonnull Store<EntityStore> store
    ) {
        // Load the UI file
        ui.append("SignTextInput.ui");
        
        // Register button click events with captured text field values
        // Using @ prefix tells Hytale to capture the element's current value
        EventData confirmData = new EventData()
            .append("ButtonAction", "confirm")
            .append("@Line1", "#Line1Input.Value")
            .append("@Line2", "#Line2Input.Value")
            .append("@Line3", "#Line3Input.Value");
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ConfirmButton", confirmData, false);
        
        EventData cancelData = new EventData().append("ButtonAction", "cancel");
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CancelButton", cancelData, false);
    }
    
    @Override
    public void handleDataEvent(
        @Nonnull Ref<EntityStore> playerEntity,
        @Nonnull Store<EntityStore> store,
        String rawData
    ) {
        // Parse the raw JSON event data
        // Format: {"ButtonAction":"confirm","@Line1":"text1","@Line2":"text2"...}
        if (rawData == null || !rawData.contains("ButtonAction")) {
            return;
        }
        
        // Simple JSON parsing (avoiding external dependencies)
        String action = extractJsonValue(rawData, "ButtonAction");
        
        if ("confirm".equals(action)) {
            // Extract text from all 3 lines
            String line1 = extractJsonValue(rawData, "@Line1");
            String line2 = extractJsonValue(rawData, "@Line2");
            String line3 = extractJsonValue(rawData, "@Line3");
            
            // Create multi-line hologram with stacked entities
            
            String[] lines = new String[3];
            lines[0] = line1 != null ? line1.trim() : "";
            lines[1] = line2 != null ? line2.trim() : "";
            lines[2] = line3 != null ? line3.trim() : "";
            
            // Cut off lines longer than 16 characters
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].length() > 16) {
                    lines[i] = lines[i].substring(0, 16);
                }
            }
            
            // Determine how many lines to create based on which lines have content
            // If line 3 is empty, only create entities for lines 1 and 2
            // If line 3 has content, create all 3 entities (even if line 2 is empty)
            java.util.List<String> displayLines = new java.util.ArrayList<>();
            if (!lines[2].isEmpty()) {
                // Line 3 has content, create all 3 entities
                displayLines.add(lines[0]);
                displayLines.add(lines[1]);
                displayLines.add(lines[2]);
            } else if (!lines[1].isEmpty() || !lines[0].isEmpty()) {
                // Line 3 is empty, only create entities for lines 1 and 2
                displayLines.add(lines[0]);
                displayLines.add(lines[1]);
            }
            
            // Default to player name if all fields empty
            if (displayLines.isEmpty()) {
                displayLines.add(playerName + "'s sign");
            }
                
            // Get player position for offset calculation
            var transformComponent = store.getComponent(playerEntity, TransformComponent.getComponentType());
            if (transformComponent == null) return;
            var playerPos = transformComponent.getPosition();
            
            var world = store.getExternalData().getWorld();
            
            final java.util.List<String> finalLines = displayLines;
            final double playerX = playerPos.getX();
            final double playerZ = playerPos.getZ();
            
            world.execute(() -> {
                // Delete existing holograms at this sign location if any
                deleteExistingHolograms(world, worldId, signX, (int) signY, signZ);
                
                // Create multi-line hologram with stacked entities
                final double lineSpacing = 0.25;
                
                for (int i = 0; i < finalLines.size(); i++) {
                    String lineText = finalLines.get(i);
                    
                    // Calculate Y offset: center the text stack on the sign
                    // More lines means lower starting position so all lines fit on sign
                    // Formula: y + ((size - 1 - index) - (size - 1) / 2) * spacing
                    double yOffset = ((finalLines.size() - 1 - i) - (finalLines.size() - 1) / 2.0) * lineSpacing;
                    
                    HologramUtil.HologramResult result = HologramUtil.createHologram(
                        world,
                        signX,
                        signY + yOffset,
                        signZ,
                        playerX,
                        playerZ,
                        lineText
                    );
                    
                    // Register each hologram for deletion when sign is broken
                    if (result != null) {
                        me.ascheladd.hytale.neolocks.listener.BlockBreakListener.registerSignHologram(
                            worldId, signX, (int) signY, signZ, result.networkId, result.entityRef
                        );
                        
                        // Persist the sign-hologram mapping using UUID (TaleLib approach)
                        if (signHologramStorage != null && result.entityUuid != null) {
                            signHologramStorage.registerSignHologram(worldId, signX, (int) signY, signZ, result.entityUuid);
                            me.ascheladd.hytale.neolocks.NeoLocks.debug("Persisted sign hologram mapping: " + worldId + ":" + signX + ":" + signY + ":" + signZ + " -> UUID:" + result.entityUuid);
                        } else {
                            if (signHologramStorage == null) {
                                me.ascheladd.hytale.neolocks.NeoLocks.getInstance().getLogger().atWarning().log("SignHologramStorage is null, hologram mapping will not persist!");
                            }
                            if (result.entityUuid == null) {
                                me.ascheladd.hytale.neolocks.NeoLocks.getInstance().getLogger().atWarning().log("Entity UUID is null, hologram mapping will not persist!");
                            }
                        }
                        
                        me.ascheladd.hytale.neolocks.NeoLocks.debug("Created sign hologram line " + (i+1) + ": " + lineText);
                    } else {
                        me.ascheladd.hytale.neolocks.NeoLocks.getInstance().getLogger().atSevere().log("Failed to create hologram for line " + (i+1));
                    }
                }
            });
            
            close();
        } else if ("cancel".equals(action)) {
            // Just close the page without creating hologram
            close();
        }
    }
    
    /**
     * Simple JSON value extractor to avoid external dependencies.
     * Extracts value for a given key from JSON string.
     */
    private String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\":\"";
        int startIndex = json.indexOf(searchKey);
        if (startIndex == -1) {
            return null;
        }
        startIndex += searchKey.length();
        int endIndex = json.indexOf("\"", startIndex);
        if (endIndex == -1) {
            return null;
        }
        return json.substring(startIndex, endIndex);
    }
    
    /**
     * Deletes existing holograms at the sign location before creating new ones.
     */
    private void deleteExistingHolograms(
        com.hypixel.hytale.server.core.universe.world.World world,
        String worldId, 
        int x, 
        int y, 
        int z
    ) {
        java.util.List<java.util.UUID> existingUuids = signHologramStorage.removeSignHolograms(worldId, x, y, z);
        
        if (existingUuids == null || existingUuids.isEmpty()) {
            me.ascheladd.hytale.neolocks.NeoLocks.debug("No existing holograms to delete at " + worldId + ":" + x + ":" + y + ":" + z);
            return;
        }
        
        me.ascheladd.hytale.neolocks.NeoLocks.debug("Deleting " + existingUuids.size() + " existing sign holograms before creating new ones");
        
        var entityStore = world.getEntityStore();
        
        for (java.util.UUID uuid : existingUuids) {
            try {
                Ref<EntityStore> hologramRef = entityStore.getRefFromUUID(java.util.Objects.requireNonNull(uuid));
                
                if (hologramRef == null || !hologramRef.isValid()) {
                    me.ascheladd.hytale.neolocks.NeoLocks.debug("Hologram UUID " + uuid + " not found or invalid, skipping");
                    continue;
                }
                
                // Remove entity
                com.hypixel.hytale.component.Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
                entityStore.getStore().removeEntity(hologramRef, holder, com.hypixel.hytale.component.RemoveReason.REMOVE);
                me.ascheladd.hytale.neolocks.NeoLocks.debug("Deleted existing hologram UUID: " + uuid);
            } catch (Exception e) {
                me.ascheladd.hytale.neolocks.NeoLocks.getInstance().getLogger().atSevere().log("Failed to delete hologram UUID " + uuid + ": " + e.getMessage());
            }
        }
    }
    
    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> playerEntity, @Nonnull Store<EntityStore> store) {
        // Cleanup when page closes
    }
}
