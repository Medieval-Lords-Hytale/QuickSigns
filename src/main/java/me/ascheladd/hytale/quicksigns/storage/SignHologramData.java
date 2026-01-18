package me.ascheladd.hytale.quicksigns.storage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;

/**
 * Data class for sign hologram storage using Hytale's Codec system.
 * Maps sign locations (worldId:x:y:z) to lists of hologram UUIDs.
 */
public class SignHologramData {
    
    private Map<String, List<UUID>> signHolograms;
    
    /**
     * Codec for serializing/deserializing sign hologram data.
     * Uses MapCodec with ArrayCodec to handle {@code Map<String, List<UUID>>} storage.
     */
    public static final BuilderCodec<SignHologramData> CODEC = BuilderCodec.builder(
            SignHologramData.class, SignHologramData::new
    )
    .append(
            new KeyedCodec<>(
                    "Signs",
                    new MapCodec<>(Codec.STRING_ARRAY, HashMap::new, false),
                    true
            ),
            // decode: Map<String, String[]> → Map<String, List<UUID>>
            (data, map) -> {
                if (map != null) {
                    map.forEach((key, uuidStrings) -> {
                        List<UUID> uuids = new ArrayList<>();
                        for (String uuidStr : uuidStrings) {
                            try {
                                uuids.add(UUID.fromString(uuidStr));
                            } catch (IllegalArgumentException e) {
                                // Skip invalid UUIDs
                            }
                        }
                        data.signHolograms.put(key, uuids);
                    });
                }
            },
            // encode: Map<String, List<UUID>> → Map<String, String[]>
            data -> {
                Map<String, String[]> out = new HashMap<>();
                data.signHolograms.forEach((key, uuids) -> {
                    String[] uuidStrings = new String[uuids.size()];
                    for (int i = 0; i < uuids.size(); i++) {
                        uuidStrings[i] = uuids.get(i).toString();
                    }
                    out.put(key, uuidStrings);
                });
                return out;
            }
    )
    .add()
    .build();
    
    /**
     * Creates a new SignHologramData with an empty map.
     */
    public SignHologramData() {
        this.signHolograms = new ConcurrentHashMap<>();
    }
    
    /**
     * Creates a new SignHologramData with existing data.
     * @param signHolograms The existing sign hologram mappings
     */
    public SignHologramData(Map<String, List<UUID>> signHolograms) {
        this.signHolograms = signHolograms;
    }
    
    /**
     * Gets the sign hologram mappings.
     * @return The map of sign locations to hologram UUIDs
     */
    public Map<String, List<UUID>> getSignHolograms() {
        return signHolograms;
    }
    
    /**
     * Sets the sign hologram mappings.
     * @param signHolograms The new sign hologram mappings
     */
    public void setSignHolograms(Map<String, List<UUID>> signHolograms) {
        this.signHolograms = signHolograms;
    }
}
