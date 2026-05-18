package com.devilswrath.plugin.commands;

import com.devilswrath.plugin.DevilsWrathPlugin;
import com.devilswrath.plugin.storage.OwnerStorage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class DevilsWrathCommand implements CommandExecutor, TabCompleter {

    private static final String DISPLAY_NAME = "Devil's Wrath";
    private static final String CMD_NAME     = "devilswrath";
    private static final NamedTextColor COLOR = NamedTextColor.DARK_RED;

    private final DevilsWrathPlugin plugin;

    public DevilsWrathCommand(DevilsWrathPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Route subcommands ─────────────────────────────────────────────────────

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (args.length > 0) {
            return switch (args[0].toLowerCase()) {
                case "owner"   -> handleOwner(sender);
                case "restore" -> handleRestore(sender);
                default        -> { sendUsage(sender); yield true; }
            };
        }

        // /devilswrath — claim the weapon
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can claim " + DISPLAY_NAME + ".")
                .color(NamedTextColor.RED));
            return true;
        }

        if (!player.hasPermission("devilswrath.claim")) {
            player.sendMessage(Component.text("You do not have permission to claim " + DISPLAY_NAME + ".")
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
            return true;
        }

        OwnerStorage storage = plugin.getOwnerStorage();

        if (storage.isClaimed()) {
            player.sendMessage(
                Component.text(DISPLAY_NAME + " has already been claimed by ")
                    .color(NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(
                        Component.text(storage.getOwnerName())
                            .color(COLOR)
                            .decoration(TextDecoration.BOLD, true)
                            .decoration(TextDecoration.ITALIC, false)
                    )
                    .append(
                        Component.text(". It can never be claimed again.")
                            .color(NamedTextColor.RED)
                            .decoration(TextDecoration.ITALIC, false)
                    )
            );
            return true;
        }

        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(
                Component.text("Your inventory is full! Clear a slot and try /" + CMD_NAME + " again.")
                    .color(NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false)
            );
            return true;
        }

        // Register owner and hand over the weapon
        storage.setClaimed(player.getUniqueId(), player.getName());
        player.getInventory().addItem(plugin.getDevilsWrathItem().create());

        // Server-wide broadcast
        plugin.getServer().broadcast(
            Component.text("\u2620 ")
                .color(NamedTextColor.DARK_RED)
                .append(
                    Component.text(player.getName())
                        .color(NamedTextColor.RED)
                        .decoration(TextDecoration.BOLD, true)
                )
                .append(Component.text(" has claimed ").color(NamedTextColor.GRAY))
                .append(
                    Component.text(DISPLAY_NAME)
                        .color(COLOR)
                        .decoration(TextDecoration.BOLD, true)
                )
                .append(Component.text(". May darkness follow their enemies.").color(NamedTextColor.GRAY))
        );

        player.sendMessage(
            Component.text(DISPLAY_NAME + " is now yours \u2014 and yours alone.")
                .color(COLOR)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false)
        );
        player.sendMessage(
            Component.text("Sneak and land a critical hit to activate Veil of Wrath.")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
        );

        return true;
    }

    // ── /devilswrath owner ────────────────────────────────────────────────────

    private boolean handleOwner(CommandSender sender) {
        OwnerStorage storage = plugin.getOwnerStorage();

        if (!storage.isClaimed()) {
            sender.sendMessage(
                Component.text(DISPLAY_NAME + " has not yet been claimed by anyone.")
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
            );
            return true;
        }

        sender.sendMessage(
            Component.text(DISPLAY_NAME + " Owner: ")
                .color(COLOR)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false)
                .append(
                    Component.text(storage.getOwnerName())
                        .color(NamedTextColor.RED)
                        .decoration(TextDecoration.BOLD, true)
                        .decoration(TextDecoration.ITALIC, false)
                )
        );
        return true;
    }

    // ── /devilswrath restore  (admin only) ────────────────────────────────────

    private boolean handleRestore(CommandSender sender) {
        if (!sender.hasPermission("devilswrath.admin")) {
            sender.sendMessage(
                Component.text("You do not have permission to use this command.")
                    .color(NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false)
            );
            return true;
        }

        OwnerStorage storage = plugin.getOwnerStorage();

        if (!storage.isClaimed()) {
            sender.sendMessage(
                Component.text(DISPLAY_NAME + " has not been claimed yet. Nothing to restore.")
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
            );
            return true;
        }

        Player owner = plugin.getServer().getPlayer(storage.getOwnerUuid());
        if (owner == null) {
            sender.sendMessage(
                Component.text("The owner (" + storage.getOwnerName() + ") is not currently online.")
                    .color(NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false)
            );
            return true;
        }

        if (owner.getInventory().firstEmpty() == -1) {
            sender.sendMessage(
                Component.text(owner.getName() + "'s inventory is full.")
                    .color(NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false)
            );
            owner.sendMessage(
                Component.text("An admin tried to restore " + DISPLAY_NAME + " but your inventory is full.")
                    .color(NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false)
            );
            return true;
        }

        owner.getInventory().addItem(plugin.getDevilsWrathItem().create());

        sender.sendMessage(
            Component.text(DISPLAY_NAME + " restored and given to " + owner.getName() + ".")
                .color(NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false)
        );
        owner.sendMessage(
            Component.text(DISPLAY_NAME + " has been restored to you by an administrator.")
                .color(COLOR)
                .decoration(TextDecoration.ITALIC, false)
        );
        return true;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(
            Component.text("Usage: /" + CMD_NAME + " | /" + CMD_NAME + " owner | /" + CMD_NAME + " restore")
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false)
        );
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("owner", "restore").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .toList();
        }
        return List.of();
    }
}
