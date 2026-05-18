package com.devilswrath.plugin.storage;

import com.devilswrath.plugin.DevilsWrathPlugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.UUID;

/**
 * Persists the one-time claim of Devil's Wrath to config.yml.
 * Once claimed the owner UUID and name are locked forever.
 */
public class OwnerStorage {

    private final DevilsWrathPlugin plugin;
    private final String            sectionKey;

    private static final String KEY_CLAIMED    = "claimed";
    private static final String KEY_OWNER_UUID = "owner-uuid";
    private static final String KEY_OWNER_NAME = "owner-name";

    public OwnerStorage(DevilsWrathPlugin plugin, String sectionKey) {
        this.plugin     = plugin;
        this.sectionKey = sectionKey;
    }

    private String key(String suffix) {
        return sectionKey + "." + suffix;
    }

    public boolean isClaimed() {
        return plugin.getConfig().getBoolean(key(KEY_CLAIMED), false);
    }

    public void setClaimed(UUID ownerUuid, String ownerName) {
        FileConfiguration cfg = plugin.getConfig();
        cfg.set(key(KEY_CLAIMED),    true);
        cfg.set(key(KEY_OWNER_UUID), ownerUuid.toString());
        cfg.set(key(KEY_OWNER_NAME), ownerName);
        plugin.saveConfig();
    }

    public UUID getOwnerUuid() {
        String raw = plugin.getConfig().getString(key(KEY_OWNER_UUID));
        if (raw == null) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public String getOwnerName() {
        return plugin.getConfig().getString(key(KEY_OWNER_NAME), "Unknown");
    }
}
