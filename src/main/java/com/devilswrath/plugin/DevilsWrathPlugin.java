package com.devilswrath.plugin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.ChatColor;

public class DevilsWrathPlugin extends JavaPlugin {

    private static DevilsWrathPlugin instance;

    @Override
    public void onEnable() {
        instance = this;

        // Register listeners
        getServer().getPluginManager().registerEvents(new DevilsWrathListener(this), this);

        // Start particle task for all sword holders
        new DevilsWrathParticleTask(this).runTaskTimer(this, 0L, 2L);

        getLogger().info("Devils Wrath plugin enabled! The demon awakens...");
    }

    @Override
    public void onDisable() {
        getLogger().info("Devils Wrath plugin disabled.");
    }

    public static DevilsWrathPlugin getInstance() {
        return instance;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("devilswrath")) return false;

        // Determine target player
        Player target;
        if (args.length >= 1) {
            target = getServer().getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player '" + args[0] + "' not found or not online.");
                return true;
            }
        } else if (sender instanceof Player) {
            target = (Player) sender;
        } else {
            sender.sendMessage(ChatColor.RED + "Console must specify a player: /devilswrath <player>");
            return true;
        }

        target.getInventory().addItem(DevilsWrathSword.create());
        target.sendMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + "⚔ You have received the Devils Wrath! ⚔");
        if (!target.equals(sender)) {
            sender.sendMessage(ChatColor.DARK_RED + "Gave Devils Wrath sword to " + target.getName() + ".");
        }
        return true;
    }
}
