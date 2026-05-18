package com.devilswrath.plugin;

import com.devilswrath.plugin.commands.DevilsWrathCommand;
import com.devilswrath.plugin.items.DevilsWrathItem;
import com.devilswrath.plugin.listeners.AbilityListener;
import com.devilswrath.plugin.listeners.ContainerListener;
import com.devilswrath.plugin.storage.OwnerStorage;
import org.bukkit.plugin.java.JavaPlugin;

public class DevilsWrathPlugin extends JavaPlugin {

    private static DevilsWrathPlugin instance;

    private DevilsWrathItem devilsWrathItem;
    private OwnerStorage    ownerStorage;
    private AbilityListener abilityListener;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        devilsWrathItem  = new DevilsWrathItem();
        ownerStorage     = new OwnerStorage(this, "devilswrath");
        abilityListener  = new AbilityListener(this);

        getServer().getPluginManager().registerEvents(abilityListener,              this);
        getServer().getPluginManager().registerEvents(new ContainerListener(this),  this);

        var cmd = getCommand("devilswrath");
        if (cmd != null) {
            var handler = new DevilsWrathCommand(this);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }

        getLogger().info("Devil's Wrath is awake. Darkness stirs.");
    }

    @Override
    public void onDisable() {
        if (abilityListener != null) abilityListener.cleanupAll();
        getLogger().info("Devil's Wrath has gone quiet.");
    }

    // ── Accessors ────────────────────────────────────────────────────────────

    public static DevilsWrathPlugin getInstance() { return instance; }

    public DevilsWrathItem   getDevilsWrathItem() { return devilsWrathItem; }
    public OwnerStorage      getOwnerStorage()    { return ownerStorage;    }
}
