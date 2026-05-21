package com.altarsmp.diamondsword;

import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
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
    private final Set<UUID> lightningKillPending = new HashSet<>();

    private static final int COOLDOWN_SECONDS = 18;
    private static final int FAIL_COOLDOWN_SECONDS = 18;
    private static final int WINDOW_SECONDS = 8;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("DiamondLightningPlugin enabled! - AltarSMP");
        if (getCommand("lightningSword") != null) getCommand("lightningSword").setExecutor((sender, cmd, label, args) -> {
            if (sender instanceof Player player) {
                giveLightningSword(player);
                player.sendMessage(ChatColor.WHITE + "⚡ The Last Storm given!");
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

        meta.setDisplayName(ChatColor.WHITE + "" + ChatColor.BOLD + "⚡ The Last Storm");

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Forged in the storms of AltarSMP");
        lore.add("");
        lore.add(ChatColor.YELLOW + "Passive: " + ChatColor.WHITE + "Looting IV");
        lore.add(ChatColor.YELLOW + "Ability: " + ChatColor.WHITE + "Lightning Strike");
        lore.add(ChatColor.DARK_GRAY + "Sneak → activate, then Hit → ⚡ Strike!");
        lore.add(ChatColor.DARK_GRAY + "Cooldown: " + COOLDOWN_SECONDS + "s");
        lore.add("");
        lore.add(ChatColor.DARK_AQUA + "[AltarSMP]");
        meta.setLore(lore);

        meta.setUnbreakable(true);

        // Sharp V, Sweeping III, Looting IV, Mending — UNBREAKING ამოღებულია, setUnbreakable(true) საკმარისია
        meta.addEnchant(Enchantment.DAMAGE_ALL, 5, true);
        meta.addEnchant(Enchantment.SWEEPING_EDGE, 3, true);
        meta.addEnchant(Enchantment.LOOT_BONUS_MOBS, 4, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);

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
    public void onSneak(org.bukkit.event.player.PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        Player player = event.getPlayer();
        if (!isLightningSword(player.getInventory().getItemInMainHand())) return;

        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (abilityLastUsed.containsKey(id)) {
            long elapsed = (now - abilityLastUsed.get(id)) / 1000;
            if (abilityActive.getOrDefault(id, false)) return;
            if (elapsed < COOLDOWN_SECONDS) {
                long remaining = COOLDOWN_SECONDS - elapsed;
                sendCooldownBar(player, remaining, COOLDOWN_SECONDS);
                return;
            }
        }

        if (abilityActive.getOrDefault(id, false)) return;

        abilityActive.put(id, true);
        abilityActivatedAt.put(id, now);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.8f);

        spawnActivationCircle(player);
        startWindowCountdown(player, id);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!abilityActive.getOrDefault(id, false)) return;
                abilityActive.put(id, false);
                abilityActivatedAt.remove(id);
                long penaltyStart = System.currentTimeMillis() - ((long)(COOLDOWN_SECONDS - FAIL_COOLDOWN_SECONDS) * 1000);
                abilityLastUsed.put(id, penaltyStart);
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.5f);
                startCooldownDisplay(player, FAIL_COOLDOWN_SECONDS);
            }
        }.runTaskLater(this, WINDOW_SECONDS * 20L);
    }

    // ზემოთ bar — ABOVE_ACTIONBAR (title პოზიცია, AltarSMP სტილი)
    private void sendAboveBar(Player player, String msg) {
        player.sendTitle(msg, "", 0, 25, 5);
    }

    private void startWindowCountdown(Player player, UUID id) {
        new BukkitRunnable() {
            int secondsLeft = WINDOW_SECONDS;

            @Override
            public void run() {
                if (!player.isOnline()) { cancel(); return; }
                if (!abilityActive.getOrDefault(id, false)) { cancel(); return; }
                if (secondsLeft <= 0) { cancel(); return; }

                int bars = 16;
                int filled = (int) Math.round((double) secondsLeft / WINDOW_SECONDS * bars);
                StringBuilder bar = new StringBuilder();
                for (int i = 0; i < bars; i++) {
                    bar.append(i < filled ? "█" : "░");
                }

                String msg = ChatColor.WHITE + "⚡ Lightning Strike  " +
                    ChatColor.DARK_GRAY + "| " +
                    ChatColor.WHITE + bar +
                    ChatColor.DARK_GRAY + " |  " +
                    ChatColor.WHITE + secondsLeft + "s";

                sendAboveBar(player, msg);
                secondsLeft--;
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!isLightningSword(player.getInventory().getItemInMainHand())) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        if (event.getEntity().getEntityId() == player.getEntityId()) return;

        UUID id = player.getUniqueId();
        if (!abilityActive.getOrDefault(id, false)) return;

        abilityActive.put(id, false);
        abilityActivatedAt.remove(id);
        abilityLastUsed.put(id, System.currentTimeMillis());

        Location targetLoc = target.getLocation();
        target.getWorld().strikeLightningEffect(targetLoc);

        player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.9f);
        player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.8f, 1.0f);

        Bukkit.getScheduler().runTaskLater(this, () -> target.setVelocity(new org.bukkit.util.Vector(0, 0, 0)), 1L);

        if (target instanceof Player targetPlayer) {
            lightningKillPending.add(targetPlayer.getUniqueId());
        }

        spawnImpactParticles(targetLoc);
        startCooldownDisplay(player, COOLDOWN_SECONDS);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player dead = event.getEntity();
        if (!lightningKillPending.remove(dead.getUniqueId())) return;

        String deathMsg = event.getDeathMessage();
        if (deathMsg != null) {
            event.setDeathMessage(ChatColor.WHITE + "" + ChatColor.BOLD + "⚡ " + deathMsg);
        }

        Location loc = dead.getLocation();
        World world = dead.getWorld();

        // მხოლოდ თეთრი particles
        for (int i = 0; i < 80; i++) {
            double ox = (Math.random() - 0.5) * 1.5;
            double oy = Math.random() * 2.5;
            double oz = (Math.random() - 0.5) * 1.5;
            world.spawnParticle(Particle.REDSTONE,
                loc.clone().add(ox, oy, oz), 1, 0, 0, 0, 0,
                new Particle.DustOptions(Color.WHITE, 2.0f));
        }
        world.spawnParticle(Particle.FLASH, loc.clone().add(0, 1, 0), 1, 0, 0, 0, 0);
    }

    private void sendCooldownBar(Player player, long remaining, int total) {
        int bars = 16;
        int filled = (int) Math.round((double)(total - remaining) / total * bars);
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < bars; i++) {
            bar.append(i < filled ? "█" : "░");
        }
        String msg = ChatColor.WHITE + "⚡ The Last Storm  " +
            ChatColor.DARK_GRAY + "| " +
            ChatColor.WHITE + bar +
            ChatColor.DARK_GRAY + " |  " +
            ChatColor.WHITE + remaining + "s";
        sendAboveBar(player, msg);
    }

    private void startCooldownDisplay(Player player, int totalSeconds) {
        new BukkitRunnable() {
            int secondsLeft = totalSeconds;

            @Override
            public void run() {
                if (!player.isOnline()) { cancel(); return; }
                if (secondsLeft <= 0) {
                    String ready = ChatColor.WHITE + "⚡ The Last Storm  " +
                        ChatColor.DARK_GRAY + "| " +
                        ChatColor.WHITE + "████████████████" +
                        ChatColor.DARK_GRAY + " |  " +
                        ChatColor.WHITE + "READY";
                    sendAboveBar(player, ready);
                    cancel();
                    return;
                }

                int bars = 16;
                int filled = (int) Math.round((double)(totalSeconds - secondsLeft) / totalSeconds * bars);
                StringBuilder bar = new StringBuilder();
                for (int i = 0; i < bars; i++) {
                    bar.append(i < filled ? "█" : "░");
                }

                String msg = ChatColor.WHITE + "⚡ The Last Storm  " +
                    ChatColor.DARK_GRAY + "| " +
                    ChatColor.WHITE + bar +
                    ChatColor.DARK_GRAY + " |  " +
                    ChatColor.WHITE + secondsLeft + "s";

                sendAboveBar(player, msg);
                secondsLeft--;
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    private void spawnActivationCircle(Player player) {
        Location center = player.getLocation().add(0, 0.5, 0);
        double radius = 4.0; // 8x8 = diameter 8, radius 4
        int points = 80;
        int layers = 3;

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

                    for (int layer = 0; layer < layers; layer++) {
                        double y = center.getY() + (layer * 0.12);
                        Location loc = new Location(center.getWorld(), x, y, z);
                        // მხოლოდ თეთრი
                        center.getWorld().spawnParticle(
                            Particle.REDSTONE, loc, 1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.WHITE, 1.3f)
                        );
                    }
                }
                tick += 2;
            }
        }.runTaskTimer(this, 0L, 2L);
    }

    private void spawnImpactParticles(Location loc) {
        World world = loc.getWorld();
        // მხოლოდ თეთრი particles
        for (int i = 0; i < 80; i++) {
            double ox = (Math.random() - 0.5) * 2;
            double oy = Math.random() * 3;
            double oz = (Math.random() - 0.5) * 2;
            world.spawnParticle(Particle.REDSTONE, loc.clone().add(ox, oy, oz), 1, 0, 0, 0, 0,
                new Particle.DustOptions(Color.WHITE, 1.5f));
        }
        world.spawnParticle(Particle.FLASH, loc.clone().add(0, 1, 0), 1, 0, 0, 0, 0);
    }
}
