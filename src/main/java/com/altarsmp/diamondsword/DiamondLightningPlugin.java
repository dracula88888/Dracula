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

import java.util.*;

public class DiamondLightningPlugin extends JavaPlugin implements Listener {

    // Key to identify our custom sword
    private static final String SWORD_KEY = "altar_lightning_sword";

    // Per-player state
    private final Map<UUID, Long> abilityLastUsed = new HashMap<>();
    private final Map<UUID, Boolean> abilityActive = new HashMap<>();     // waiting for hit
    private final Map<UUID, Long> abilityActivatedAt = new HashMap<>();   // when ability was activated

    private static final int COOLDOWN_SECONDS = 18;     // cooldown after successful use
    private static final int FAIL_COOLDOWN_SECONDS = 48; // cooldown if 8s window missed
    private static final int WINDOW_SECONDS = 8;         // time window to hit someone

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

    // ─── Give the custom sword ────────────────────────────────────────────────

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

        // True unbreakable - never loses durability
        meta.setUnbreakable(true);

        // Enchantments
        meta.addEnchant(Enchantment.DAMAGE_ALL, 5, true);          // Sharpness V
        meta.addEnchant(Enchantment.MENDING, 1, true);             // Mending
        meta.addEnchant(Enchantment.SWEEPING_EDGE, 3, true);       // Sweeping Edge III
        meta.addEnchant(Enchantment.LOOT_BONUS_MOBS, 4, true);    // Looting IV

        // Mark our custom sword
        meta.getPersistentDataContainer().set(
            new org.bukkit.NamespacedKey(this, SWORD_KEY),
            org.bukkit.persistence.PersistentDataType.BYTE,
            (byte) 1
        );

        sword.setItemMeta(meta);
        player.getInventory().addItem(sword);
    }

    // ─── Check if held item is our sword ────────────────────────────────────

    private boolean isLightningSword(ItemStack item) {
        if (item == null || item.getType() != Material.DIAMOND_SWORD) return false;
        if (!item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(
            new org.bukkit.NamespacedKey(this, SWORD_KEY),
            org.bukkit.persistence.PersistentDataType.BYTE
        );
    }

    // ─── Sneak to activate ability ───────────────────────────────────────────

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!isLightningSword(player.getInventory().getItemInMainHand())) return;
        if (!player.isSneaking()) return;

        // Only on sneak + right-click or sneak itself (we hook offhand/sneak toggle via PlayerToggleSneakEvent)
    }

    // Use PlayerToggleSneakEvent to activate the ability
    @EventHandler
    public void onSneak(org.bukkit.event.player.PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return; // only when starting sneak
        Player player = event.getPlayer();
        if (!isLightningSword(player.getInventory().getItemInMainHand())) return;

        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();

        // Check cooldown
        if (abilityLastUsed.containsKey(id)) {
            long elapsed = (now - abilityLastUsed.get(id)) / 1000;
            int requiredCooldown = COOLDOWN_SECONDS;

            // Check if they failed last time (48s cooldown)
            if (abilityActive.getOrDefault(id, false)) return; // already active

            if (elapsed < requiredCooldown) {
                long remaining = requiredCooldown - elapsed;
                sendAltarCooldownMessage(player, remaining, requiredCooldown);
                return;
            }
        }

        if (abilityActive.getOrDefault(id, false)) return; // already active

        // Activate ability
        abilityActive.put(id, true);
        abilityActivatedAt.put(id, now);

        // Sound + Visual feedback
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.8f);
        player.sendMessage(ChatColor.YELLOW + "" + ChatColor.BOLD + "⚡ ABILITY ACTIVATED" +
                ChatColor.GRAY + " - Hit a player within " + WINDOW_SECONDS + "s!");

        // Action bar message
        player.sendTitle("", ChatColor.YELLOW + "⚡ Hit a player!", 5, 40, 10);

        // Spawn activation particles (8x8 circle, white + slight yellow)
        spawnActivationCircle(player);

        // Schedule 8-second window expiry
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!abilityActive.getOrDefault(id, false)) return; // already used

                // Window expired - apply 48s cooldown
                abilityActive.put(id, false);
                abilityActivatedAt.remove(id);
                abilityLastUsed.put(id, System.currentTimeMillis() - (COOLDOWN_SECONDS * 1000L));
                // Set so next available is 48s from now
                long penaltyStart = System.currentTimeMillis() - ((long)(COOLDOWN_SECONDS - FAIL_COOLDOWN_SECONDS) * 1000);
                abilityLastUsed.put(id, penaltyStart);

                player.sendMessage(ChatColor.RED + "⚡ Ability window expired! " +
                        ChatColor.GRAY + FAIL_COOLDOWN_SECONDS + "s cooldown applied.");
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.5f);
                sendAltarCooldownMessage(player, FAIL_COOLDOWN_SECONDS, FAIL_COOLDOWN_SECONDS);
            }
        }.runTaskLater(this, WINDOW_SECONDS * 20L);
    }

    // ─── On Hit: trigger lightning ───────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof Player target)) return;
        if (!isLightningSword(player.getInventory().getItemInMainHand())) return;

        UUID id = player.getUniqueId();
        if (!abilityActive.getOrDefault(id, false)) return;
        // No sneaking requirement on hit - just need ability active

        // Trigger lightning!
        abilityActive.put(id, false);
        abilityActivatedAt.remove(id);
        abilityLastUsed.put(id, System.currentTimeMillis());

        Location targetLoc = target.getLocation();
        target.getWorld().strikeLightning(targetLoc);

        // Sound effects
        player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.9f);
        player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.8f, 1.0f);

        // Fancy particles around impact
        spawnImpactParticles(targetLoc);

        // Messages
        player.sendMessage(
            ChatColor.YELLOW + "" + ChatColor.BOLD + "⚡ LIGHTNING STRIKE!" +
            ChatColor.GRAY + " Next use in " + COOLDOWN_SECONDS + "s"
        );
        target.sendMessage(ChatColor.RED + "⚡ You were struck by a Lightning Sword!");

        // AltarSMP style chat notification
        sendAltarAbilityUsed(player, target);

        // Update lore to show cooldown countdown
        startCooldownDisplay(player, COOLDOWN_SECONDS);
    }

    // ─── AltarSMP-style messages ─────────────────────────────────────────────

    private void sendAltarAbilityUsed(Player user, Player target) {
        // Broadcast in AltarSMP chat style
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
        // Action bar cooldown display - AltarSMP style
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
        player.sendActionBar(actionBar);

        // Also chat message
        player.sendMessage(
            ChatColor.DARK_GRAY + "[" + ChatColor.GOLD + "AltarSMP" + ChatColor.DARK_GRAY + "] " +
            ChatColor.RED + "⚡ Cooldown: " + ChatColor.YELLOW + remaining + "s remaining"
        );
    }

    // ─── Cooldown bar display on action bar ─────────────────────────────────

    private void startCooldownDisplay(Player player, int totalSeconds) {
        new BukkitRunnable() {
            int secondsLeft = totalSeconds;

            @Override
            public void run() {
                if (!player.isOnline()) { cancel(); return; }
                if (secondsLeft <= 0) {
                    player.sendActionBar(ChatColor.GREEN + "⚡ The Last Storm " + ChatColor.DARK_GRAY + "| " +
                            ChatColor.GREEN + "READY!");
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

                player.sendActionBar(
                    ChatColor.GOLD + "⚡ The Last Storm Cooldown " +
                    ChatColor.DARK_GRAY + "| " + bar +
                    ChatColor.DARK_GRAY + " | " + color + secondsLeft + "s"
                );
                secondsLeft--;
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    // ─── Particle effects ────────────────────────────────────────────────────

    private void spawnActivationCircle(Player player) {
        Location center = player.getLocation().add(0, 0.1, 0);
        double radius = 4.0; // 8x8 = radius 4
        int points = 64;

        new BukkitRunnable() {
            int tick = 0;
            final int maxTicks = 60; // 3 seconds = 60 ticks

            @Override
            public void run() {
                if (tick >= maxTicks) { cancel(); return; }

                double opacity = 1.0 - (double) tick / maxTicks; // fade out

                for (int i = 0; i < points; i++) {
                    double angle = (2 * Math.PI / points) * i;
                    double x = center.getX() + radius * Math.cos(angle);
                    double z = center.getZ() + radius * Math.sin(angle);
                    Location loc = new Location(center.getWorld(), x, center.getY(), z);

                    // White particles (main circle)
                    center.getWorld().spawnParticle(
                        Particle.DUST,
                        loc, 1,
                        0, 0, 0, 0,
                        new Particle.DustOptions(Color.WHITE, 1.0f)
                    );

                    // Slightly yellow accent (every 4th particle)
                    if (i % 4 == 0) {
                        center.getWorld().spawnParticle(
                            Particle.DUST,
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
        // Big flash of white and yellow on impact
        for (int i = 0; i < 50; i++) {
            double offsetX = (Math.random() - 0.5) * 2;
            double offsetY = Math.random() * 3;
            double offsetZ = (Math.random() - 0.5) * 2;
            Location pLoc = loc.clone().add(offsetX, offsetY, offsetZ);
            world.spawnParticle(Particle.DUST, pLoc, 1, 0, 0, 0, 0,
                new Particle.DustOptions(Color.WHITE, 1.5f));
            if (i % 3 == 0) {
                world.spawnParticle(Particle.DUST, pLoc, 1, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.YELLOW, 1.2f));
            }
        }
        // Electric sparks
        world.spawnParticle(Particle.ELECTRIC_SPARK, loc.clone().add(0, 1, 0), 30, 1, 1, 1, 0.5);
    }
}
