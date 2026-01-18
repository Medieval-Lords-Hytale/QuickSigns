package me.ascheladd.hytale.quicksigns.util;

import java.util.Objects;
import java.util.UUID;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import me.ascheladd.hytale.quicksigns.QuickSigns;

/**
 * Utility class for creating holograms (floating text).
 */
public class HologramUtil {
    
    /**
     * Private constructor to prevent instantiation.
     */
    private HologramUtil() {
        throw new UnsupportedOperationException("Utility class");
    }
    
    /**
     * Creates a sign text hologram positioned in front of the sign.
     * Calculates offset based on the player's cardinal direction from the sign.
     * This method should be called from within world.execute().
     * 
     * @param world The world to create the hologram in
     * @param signX Sign X coordinate
     * @param signY Sign Y coordinate
     * @param signZ Sign Z coordinate
     * @param playerX Player X coordinate
     * @param playerZ Player Z coordinate
     * @param text The text to display
     * @return The hologram entity reference, or null if creation failed
     */
    public static HologramResult createHologram(World world, double signX, double signY, double signZ, double playerX, double playerZ, String text) {
        // Calculate player's direction from sign
        double deltaX = playerX - signX;
        double deltaZ = playerZ - signZ;
        
        // Determine cardinal direction (biggest difference)
        double absX = Math.abs(deltaX);
        double absZ = Math.abs(deltaZ);
        
        double offsetX = 0;
        double offsetZ = 0;
        
        if (absX > absZ) {
            // East/West direction is dominant
            offsetX = deltaX > 0 ? 0.2 : -0.2;
        } else {
            // North/South direction is dominant
            offsetZ = deltaZ > 0 ? 0.2 : -0.2;
        }
        
        return createHologram(world, ((int) signX) + 0.5 + offsetX, signY, ((int) signZ) + 0.5 + offsetZ, text);
    }
    
    /**
     * Creates a hologram at the specified position with the given text.
     * This method should be called from within world.execute().
     * 
     * @param world The world to create the hologram in
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @param text The text to display
     * @return The hologram entity reference, or null if creation failed
     */
    public static HologramResult createHologram(World world, double x, double y, double z, String text) {
        // Create entity holder
        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
        
        // Create projectile component (invisible entity shell)
        ProjectileComponent projectileComponent = new ProjectileComponent("Projectile");
        holder.putComponent(ProjectileComponent.getComponentType(), projectileComponent);
        
        // Position hologram
        Transform hologramTransform = new Transform(x, y, z, 0.0f, 0.0f, 0.0f);
        QuickSigns.debug("Creating hologram at position: " + hologramTransform.getPosition());
        holder.putComponent(TransformComponent.getComponentType(), 
            new TransformComponent(hologramTransform.getPosition(), hologramTransform.getRotation()));
        
        // Initialize projectile
        if (projectileComponent.getProjectile() == null) {
            projectileComponent.initialize();
            if (projectileComponent.getProjectile() == null) {
                return null;
            }
        }
        
        // Add network ID
        long networkId = world.getEntityStore().getStore().getExternalData().takeNextNetworkId();
        holder.addComponent(NetworkId.getComponentType(), new NetworkId((int) networkId));
        
        // Add nameplate with text
        holder.addComponent(Nameplate.getComponentType(), new Nameplate(Objects.requireNonNull(text)));
        
        // Create a specific UUID
        UUID entityUuid = UUID.randomUUID();
        holder.addComponent(UUIDComponent.getComponentType(), new UUIDComponent(Objects.requireNonNull(entityUuid)));
        
        // Spawn the entity
        Ref<EntityStore> hologramRef = world.getEntityStore().getStore().addEntity(holder, AddReason.SPAWN);
        
        if (hologramRef != null && hologramRef.isValid()) {
            return new HologramResult(networkId, hologramRef, entityUuid);
        }
        
        return null;
    }
    
    /**
     * Result class containing hologram creation information.
     */
    public static class HologramResult {
        /**
         * The network ID of the hologram entity.
         */
        public final long networkId;
        
        /**
         * The entity reference for the hologram.
         */
        public final Ref<EntityStore> entityRef;
        
        /**
         * The UUID of the hologram entity.
         */
        public final UUID entityUuid;
        
        /**
         * Creates a new hologram result.
         * @param networkId The network ID
         * @param entityRef The entity reference
         * @param entityUuid The entity UUID
         */
        public HologramResult(long networkId, Ref<EntityStore> entityRef, UUID entityUuid) {
            this.networkId = networkId;
            this.entityRef = entityRef;
            this.entityUuid = entityUuid;
        }
    }
}
