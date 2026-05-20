package com.altarsmp.diamondsword;

import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.*;

public class DiamondLightningPlugin extends JavaPlugin implements Listener {

    private static final String SWORD_KEY = "altar_lightning_sword";

    private final Map<UUID, Long> abilityLastUsed = new HashMap<>();
    private final Map<UUID, Boolean> abilityActive = new HashMap<>();
    private final Map<UUID, Long> abilityActivatedAt = new HashMap<>();

    private static final int COOLDOWN_SECONDS = 18;
    private static final int FAIL_COOLDOWN_SECONDS = 48;
    private static final int WINDOW_SECONDS = 8;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("DiamondLightningPlugin enabled! - AltarSMP");
        if (getCommand("lightningSword") != null) getCommand("lightningSword").setExecutor((sender, cmd, label, args) -> {
            if (sender instanceof Player player) {
                giveLightningSword(player);
                player.sendMessage(ChatColor.GOLD + "⚡ The Last Storm given!");
            }
            return true;
        });
    }

    @Override
    public void onDisable() {
        getLogger().info("DiamondLightningPlugin disabled.");
    }

    public void giveLightningSword(Player player) {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = sword.getItemMeta();

        meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "⚡ The Last Storm");

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Forged in the storms of AltarSMP");
        lore.add("");
        lore.add(ChatColor.YELLOW + "Passive: " + ChatColor.WHITE + "Looting IV");
        lore.add(ChatColor.YELLOW + "Ability: " + ChatColor.WHITE + "Lightning Strike");
        lore.add(ChatColor.DARK_GRAY + "Sneak → activate, then Hit → ⚡ Strike!");
        lore.add(ChatColor.DARK_GRAY + "Cooldown: " + COOLDOWN_SECONDS + "s  |  Unbreakable");
        lore.add("");
        lore.add(ChatColor.DARK_AQUA + "[AltarSMP]");
        meta.setLore(lore);

        meta.setUnbreakable(true);

        meta.addEnchant(Enchantment.DAMAGE_ALL, 5, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);
        meta.addEnchant(Enchantment.SWEEPING_EDGE, 3, true);
        meta.addEnchant(Enchantment.LOOT_BONUS_MOBS, 4, true);

        meta.getPersistentDataContainer().set(
            new org.bukkit.NamespacedKey(this, SWORD_KEY),
            org.bukkit.persistence.PersistentDataType.BYTE,
            (byte) 1
        );

        sword.setItemMeta(meta);
        player.getInventory().addItem(sword);
    }

    private boolean isLightningSword(ItemStack item) {
        if (item == null || item.getType() != Material.DIAMOND_SWORD) return false;
        if (!item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(
            new org.bukkit.NamespacedKey(this, SWORD_KEY),
            org.bukkit.persistence.PersistentDataType.BYTE
        );
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!isLightningSword(player.getInventory().getItemInMainHand())) return;
        if (!player.isSneaking()) return;
    }

    @EventHandler
    public void onSneak(org.bukkit.event.player.PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        Player player = event.getPlayer();
        if (!isLightningSword(player.getInventory().getItemInMainHand())) return;

        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (abilityLastUsed.containsKey(id)) {
            long elapsed = (now - abilityLastUsed.get(id)) / 1000;
            int requiredCooldown = COOLDOWN_SECONDS;

            if (abilityActive.getOrDefault(id, false)) return;

            if (elapsed < requiredCooldown) {
                long remaining = requiredCooldown - elapsed;
                sendAltarCooldownMessage(player, remaining, requiredCooldown);
                return;
            }
        }

        if (abilityActive.getOrDefault(id, false)) return;

        abilityActive.put(id, true);
        abilityActivatedAt.put(id, now);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.8f);
        player.sendMessage(ChatColor.YELLOW + "" + ChatColor.BOLD + "⚡ ABILITY ACTIVATED" +
                ChatColor.GRAY + " - Hit a player within " + WINDOW_SECONDS + "s!");

        player.sendTitle("", ChatColor.YELLOW + "⚡ Hit a player!", 5, 40, 10);

        spawnActivationCircle(player);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!abilityActive.getOrDefault(id, false)) return;

                abilityActive.put(id, false);
                abilityActivatedAt.remove(id);
                long penaltyStart = System.currentTimeMillis() - ((long)(COOLDOWN_SECONDS - FAIL_COOLDOWN_SECONDS) * 1000);
                abilityLastUsed.put(id, penaltyStart);

                player.sendMessage(ChatColor.RED + "⚡ Ability window expired! " +
                        ChatColor.GRAY + FAIL_COOLDOWN_SECONDS + "s cooldown applied.");
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.5f);
                sendAltarCooldownMessage(player, FAIL_COOLDOWN_SECONDS, FAIL_COOLDOWN_SECONDS);
            }
        }.runTaskLater(this, WINDOW_SECONDS * 20L);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof Player target)) return;
        if (!isLightningSword(player.getInventory().getItemInMainHand())) return;

        UUID id = player.getUniqueId();
        if (!abilityActive.getOrDefault(id, false)) return;

        abilityActive.put(id, false);
        abilityActivatedAt.remove(id);
        abilityLastUsed.put(id, System.currentTimeMillis());

        Location targetLoc = target.getLocation();
        target.getWorld().strikeLightning(targetLoc);

        player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.9f);
        player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.8f, 1.0f);

        spawnImpactParticles(targetLoc);

        player.sendMessage(
            ChatColor.YELLOW + "" + ChatColor.BOLD + "⚡ LIGHTNING STRIKE!" +
            ChatColor.GRAY + " Next use in " + COOLDOWN_SECONDS + "s"
        );
        target.sendMessage(ChatColor.RED + "⚡ You were struck by a Lightning Sword!");

        sendAltarAbilityUsed(player, target);
        startCooldownDisplay(player, COOLDOWN_SECONDS);
    }

    private void sendAltarAbilityUsed(Player user, Player target) {
        String msg = ChatColor.DARK_GRAY + "[" +
                ChatColor.GOLD + "AltarSMP" +
                ChatColor.DARK_GRAY + "] " +
                ChatColor.YELLOW + user.getName() +
                ChatColor.GRAY + " activated " +
                ChatColor.AQUA + "⚡ The Last Storm" +
                ChatColor.GRAY + " on " +
                ChatColor.RED + target.getName() +
                ChatColor.DARK_GRAY + "!";
        Bukkit.broadcastMessage(msg);
    }

    private void sendAltarCooldownMessage(Player player, long remaining, int total) {
        int bars = 20;
        int filled = (int) Math.round((double)(total - remaining) / total * bars);
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < bars; i++) {
            if (i < filled) bar.append(ChatColor.GREEN).append("█");
            else bar.append(ChatColor.DARK_GRAY).append("█");
        }
        String actionBar = ChatColor.GOLD + "⚡ The Last Storm " + ChatColor.DARK_GRAY + "| " +
                bar + ChatColor.DARK_GRAY + " | " +
                ChatColor.YELLOW + remaining + "s";
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(actionBar));

        player.sendMessage(
            ChatColor.DARK_GRAY + "[" + ChatColor.GOLD + "AltarSMP" + ChatColor.DARK_GRAY + "] " +
            ChatColor.RED + "⚡ Cooldown: " + ChatColor.YELLOW + remaining + "s remaining"
        );
    }

    private void startCooldownDisplay(Player player, int totalSeconds) {
        new BukkitRunnable() {
            int secondsLeft = totalSeconds;

            @Override
            public void run() {
                if (!player.isOnline()) { cancel(); return; }
                if (secondsLeft <= 0) {
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        new TextComponent(ChatColor.GREEN + "⚡ The Last Storm " + ChatColor.DARK_GRAY + "| " +
                            ChatColor.GREEN + "READY!"));
                    cancel();
                    return;
                }

                int bars = 20;
                int filled = (int) Math.round((double)(totalSeconds - secondsLeft) / totalSeconds * bars);
                StringBuilder bar = new StringBuilder();
                for (int i = 0; i < bars; i++) {
                    if (i < filled) bar.append(ChatColor.GREEN).append("█");
                    else bar.append(ChatColor.DARK_GRAY).append("█");
                }

                ChatColor color = secondsLeft > 10 ? ChatColor.RED :
                                  secondsLeft > 5  ? ChatColor.YELLOW : ChatColor.GREEN;

                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent(ChatColor.GOLD + "⚡ The Last Storm Cooldown " +
                        ChatColor.DARK_GRAY + "| " + bar +
                        ChatColor.DARK_GRAY + " | " + color + secondsLeft + "s"));
                secondsLeft--;
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    private void spawnActivationCircle(Player player) {
        Location center = player.getLocation().add(0, 0.1, 0);
        double radius = 4.0;
        int points = 64;

        new BukkitRunnable() {
            int tick = 0;
            final int maxTicks = 60;

            @Override
            public void run() {
                if (tick >= maxTicks) { cancel(); return; }

                for (int i = 0; i < points; i++) {
                    double angle = (2 * Math.PI / points) * i;
                    double x = center.getX() + radius * Math.cos(angle);
                    double z = center.getZ() + radius * Math.sin(angle);
                    Location loc = new Location(center.getWorld(), x, center.getY(), z);

                    center.getWorld().spawnParticle(
                        Particle.REDSTONE,
                        loc, 1,
                        0, 0, 0, 0,
                        new Particle.DustOptions(Color.WHITE, 1.0f)
                    );

                    if (i % 4 == 0) {
                        center.getWorld().spawnParticle(
                            Particle.REDSTONE,
                            loc, 1,
                            0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(255, 255, 150), 1.2f)
                        );
                    }
                }
                tick += 2;
            }
        }.runTaskTimer(this, 0L, 2L);
    }

    private void spawnImpactParticles(Location loc) {
        World world = loc.getWorld();
        for (int i = 0; i < 50; i++) {
            double offsetX = (Math.random() - 0.5) * 2;
            double offsetY = Math.random() * 3;
            double offsetZ = (Math.random() - 0.5) * 2;
            Location pLoc = loc.clone().add(offsetX, offsetY, offsetZ);
            world.spawnParticle(Particle.REDSTONE, pLoc, 1, 0, 0, 0, 0,
                new Particle.DustOptions(Color.WHITE, 1.5f));
            if (i % 3 == 0) {
                world.spawnParticle(Particle.REDSTONE, pLoc, 1, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.YELLOW, 1.2f));
            }
        }
        world.spawnParticle(Particle.ELECTRIC_SPARK, loc.clone().add(0, 1, 0), 30, 1, 1, 1, 0.5);
    }
}
