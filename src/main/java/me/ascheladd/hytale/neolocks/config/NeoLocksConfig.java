package me.ascheladd.hytale.neolocks.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * Configuration settings for NeoLocks plugin.
 * Uses Hytale's built-in Config system with BuilderCodec.
 */
public class NeoLocksConfig {
    
    public static final BuilderCodec<NeoLocksConfig> CODEC = BuilderCodec.builder(NeoLocksConfig.class, NeoLocksConfig::new)
        .append(new KeyedCodec<>("debug", Codec.BOOLEAN),
            (config, value) -> config.debug = value,
            config -> config.debug)
        .add()
        .build();
    
    private boolean debug = false;
    
    public NeoLocksConfig() {
    }
    
    /**
     * Get debug mode setting.
     */
    public boolean isDebug() {
        return debug;
    }
    
    /**
     * Set debug mode setting.
     */
    public void setDebug(boolean debug) {
        this.debug = debug;
    }
}
