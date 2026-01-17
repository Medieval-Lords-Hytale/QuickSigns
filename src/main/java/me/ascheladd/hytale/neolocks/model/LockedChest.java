package me.ascheladd.hytale.neolocks.model;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Represents a locked chest with its location and owner information.
 * Stores all block positions that make up the chest (1 for single, 2 for double).
 */
public class LockedChest {
    private final UUID ownerId;
    private final String ownerName;
    private final String worldId;
    private final Set<BlockPosition> positions;
    private Long hologramNetworkId; // Network ID of the hologram entity (nullable)
    
    /**
     * Creates a LockedChest with multiple block positions.
     * @param positions All blocks that make up this chest (1 for single, 2 for double)
     */
    public LockedChest(UUID ownerId, String ownerName, String worldId, Set<BlockPosition> positions) {
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        this.worldId = worldId;
        this.positions = new HashSet<>(positions);
    }
    
    /**
     * Creates a single-block chest.
     */
    public LockedChest(UUID ownerId, String ownerName, String worldId, int x, int y, int z) {
        this(ownerId, ownerName, worldId, Collections.singleton(new BlockPosition(x, y, z)));
    }
    
    public UUID getOwnerId() {
        return ownerId;
    }
    
    public String getOwnerName() {
        return ownerName;
    }
    
    public String getWorldId() {
        return worldId;
    }
    
    public Set<BlockPosition> getPositions() {
        return Collections.unmodifiableSet(positions);
    }
    
    /**
     * Gets the primary position (first in set) for display purposes.
     */
    public BlockPosition getPrimaryPosition() {
        return positions.stream().findFirst().orElseThrow();
    }
    
    public int getX() {
        return getPrimaryPosition().x();
    }
    
    public int getY() {
        return getPrimaryPosition().y();
    }
    
    public int getZ() {
        return getPrimaryPosition().z();
    }
    
    /**
     * Checks if this chest contains the given block position.
     */
    public boolean containsPosition(int x, int y, int z) {
        return positions.contains(new BlockPosition(x, y, z));
    }
    
    /**
     * Creates a unique key for this chest based on all positions.
     * For double chests, includes all positions sorted.
     */
    public String getLocationKey() {
        String positionsStr = positions.stream()
            .sorted()
            .map(p -> p.x() + "," + p.y() + "," + p.z())
            .collect(Collectors.joining(";"));
        return worldId + ":" + positionsStr;
    }
    
    /**
     * Checks if this chest is owned by the given player.
     */
    public boolean isOwnedBy(UUID playerId) {
        return ownerId.equals(playerId);
    }
    
    public Long getHologramNetworkId() {
        return hologramNetworkId;
    }
    
    public void setHologramNetworkId(Long networkId) {
        this.hologramNetworkId = networkId;
    }
    
    public boolean hasHologram() {
        return hologramNetworkId != null;
    }
    
    /**
     * Simple position record for block coordinates.
     */
    public record BlockPosition(int x, int y, int z) implements Comparable<BlockPosition> {
        @Override
        public int compareTo(BlockPosition other) {
            int cmp = Integer.compare(x, other.x);
            if (cmp != 0) return cmp;
            cmp = Integer.compare(y, other.y);
            if (cmp != 0) return cmp;
            return Integer.compare(z, other.z);
        }
    }
    
    @Override
    public String toString() {
        return "LockedChest{owner=" + ownerName + ", location=" + getLocationKey() + "}";
    }
}
