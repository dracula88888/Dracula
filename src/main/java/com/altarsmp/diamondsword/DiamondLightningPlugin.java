package com.altarsmp.diamondsword;

import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class DiamondLightningPlugin extends JavaPlugin implements Listener {

    private static final String SWORD_KEY = "altar_lightning_sword";

    private final Map<UUID, Boolean>    abilityActive  = new HashMap<>();
    private final Map<UUID, Long>       cooldownStart  = new HashMap<>();
    private final Set<UUID>             lightningKillPending = new HashSet<>();
    private final Map<UUID, BossBar>    bossBars       = new HashMap<>();
    private final Map<UUID, BukkitTask> windowTasks    = new HashMap<>();
    private final Map<UUID, BukkitTask> glintTasks     = new HashMap<>();

    private static final int COOLDOWN_SECONDS = 18;
    private static final int WINDOW_SECONDS   = 8;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("DiamondLightningPlugin enabled! - AltarSMP");
        if (getCommand("lightningSword") != null)
            getCommand("lightningSword").setExecutor((sender, cmd, label, args) -> {
                if (sender instanceof Player p) giveLightningSword(p);
                return true;
            });
    }

    @Override
    public void onDisable() {
        bossBars.values().forEach(b -> { b.removeAll(); b.setVisible(false); });
        bossBars.clear();
        glintTasks.values().forEach(t  -> { if (!t.isCancelled()) t.cancel(); });
        windowTasks.values().forEach(t -> { if (!t.isCancelled()) t.cancel(); });
        glintTasks.clear(); windowTasks.clear();
    }

    // ── Give Sword ───────────────────────────────────────────────────────────

    private void giveLightningSword(Player player) {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = sword.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + "⚡ The Last Storm");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Forged in the storms of AltarSMP");
        lore.add("");
        lore.add(ChatColor.YELLOW + "Passive: " + ChatColor.WHITE + "Looting IV · Unbreakable");
        lore.add(ChatColor.YELLOW + "Ability: " + ChatColor.WHITE + "Lightning Strike");
        lore.add(ChatColor.DARK_GRAY + "Sneak → activate  |  Hit → ⚡ Strike");
        lore.add(ChatColor.DARK_GRAY + "Window: " + WINDOW_SECONDS + "s  ·  Cooldown: " + COOLDOWN_SECONDS + "s");
        lore.add(""); lore.add(ChatColor.DARK_AQUA + "[AltarSMP]");
        meta.setLore(lore);
        meta.setUnbreakable(true);
        meta.addEnchant(Enchantment.DAMAGE_ALL, 5, true);
        meta.addEnchant(Enchantment.SWEEPING_EDGE, 3, true);
        meta.addEnchant(Enchantment.LOOT_BONUS_MOBS, 4, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);
        meta.getPersistentDataContainer().set(
            new org.bukkit.NamespacedKey(this, SWORD_KEY),
            org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        sword.setItemMeta(meta);
        player.getInventory().addItem(sword);
    }

    private boolean isLightningSword(ItemStack item) {
        if (item == null || item.getType() != Material.DIAMOND_SWORD || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(
            new org.bukkit.NamespacedKey(this, SWORD_KEY),
            org.bukkit.persistence.PersistentDataType.BYTE);
    }

    // ── BossBar ──────────────────────────────────────────────────────────────

    private BossBar getBar(Player player) {
        return bossBars.computeIfAbsent(player.getUniqueId(), k -> {
            BossBar b = Bukkit.createBossBar("", BarColor.YELLOW, BarStyle.SEGMENTED_20);
            b.addPlayer(player);
            b.setVisible(false);
            return b;
        });
    }

    private void showBar(Player player, String title, BarColor color, double progress) {
        BossBar b = getBar(player);
        b.setTitle(title);
        b.setColor(color);
        b.setProgress(Math.max(0.0, Math.min(1.0, progress)));
        b.setVisible(true);
    }

    private void hideBar(Player player) {
        BossBar b = bossBars.get(player.getUniqueId());
        if (b != null) b.setVisible(false);
    }

    // ── Action bar ───────────────────────────────────────────────────────────

    private void sendActionBar(Player player, String msg) {
        player.spigot().sendMessage(
            net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
            net.md_5.bungee.api.chat.TextComponent.fromLegacyText(msg));
    }

    private void clearActionBar(Player player) { sendActionBar(player, ""); }

    // ── Glint: one-shot shimmer on sword switch, then GONE ───────────────────
    // Only fires once when switching to sword. No looping. No repeated text.

    private void startIdleGlint(Player player) {
        UUID id = player.getUniqueId();
        if (glintTasks.containsKey(id)) return;
        // 4 frames × 8 ticks = ~1.6s total, then clears itself
        String[] frames = {
            ChatColor.GOLD   + "⚡ The Last Storm",
            ChatColor.YELLOW + "" + ChatColor.BOLD + "⚡ The Last Storm",
            ChatColor.YELLOW + "⚡ The Last Storm",
            ChatColor.GOLD   + "⚡ The Last Storm",
            ""  // final frame = clear
        };
        BukkitTask task = new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!player.isOnline()
                        || !isLightningSword(player.getInventory().getItemInMainHand())
                        || abilityActive.getOrDefault(id, false)
                        || cooldownStart.containsKey(id)) {
                    cancel(); glintTasks.remove(id); clearActionBar(player); return;
                }
                sendActionBar(player, frames[t]);
                t++;
                if (t >= frames.length) { cancel(); glintTasks.remove(id); }
            }
        }.runTaskTimer(this, 0L, 8L);
        glintTasks.put(id, task);
    }

    private void stopIdleGlint(Player player) {
        UUID id = player.getUniqueId();
        BukkitTask t = glintTasks.remove(id);
        if (t != null && !t.isCancelled()) t.cancel();
        clearActionBar(player);
    }

    // ── Item held change ──────────────────────────────────────────────────────

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            boolean holdingSword = isLightningSword(player.getInventory().getItemInMainHand());
            if (holdingSword && !abilityActive.getOrDefault(id, false) && !cooldownStart.containsKey(id)) {
                startIdleGlint(player);
            } else if (!holdingSword) {
                stopIdleGlint(player);
            }
        }, 1L);
    }

    // ── Sneak → Activate ─────────────────────────────────────────────────────

    @EventHandler
    public void onSneak(org.bukkit.event.player.PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        Player player = event.getPlayer();
        if (!isLightningSword(player.getInventory().getItemInMainHand())) return;

        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (abilityActive.getOrDefault(id, false)) return;

        // Block if in cooldown
        if (cooldownStart.containsKey(id)) {
            long elapsed = (now - cooldownStart.get(id)) / 1000;
            if (elapsed < COOLDOWN_SECONDS) return;
            cooldownStart.remove(id);
        }

        // Activate
        abilityActive.put(id, true);
        stopIdleGlint(player);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.8f);

        // 8s window bar: YELLOW, drains 1→0
        new BukkitRunnable() {
            int secondsLeft = WINDOW_SECONDS;
            @Override public void run() {
                if (!player.isOnline()) { cancel(); return; }
                if (!abilityActive.getOrDefault(id, false)) { hideBar(player); cancel(); return; }
                double progress = (double) secondsLeft / WINDOW_SECONDS;
                BarColor color = secondsLeft <= 2 ? BarColor.RED : BarColor.YELLOW;
                showBar(player,
                    ChatColor.YELLOW + "⚡ " + ChatColor.WHITE + "Strike!  " + ChatColor.GRAY + secondsLeft + "s",
                    color, progress);
                secondsLeft--;
            }
        }.runTaskTimer(this, 0L, 20L);

        // 8s expiry — whether hit or not, cooldown always starts after
        BukkitTask expiry = new BukkitRunnable() {
            @Override public void run() {
                if (!abilityActive.getOrDefault(id, false)) return; // hit already handled it
                abilityActive.put(id, false);
                windowTasks.remove(id);
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.5f);
                clearActionBar(player);
                startCooldownBar(player);
            }
        }.runTaskLater(this, WINDOW_SECONDS * 20L);
        windowTasks.put(id, expiry);
    }

    // ── Hit → Lightning ──────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!isLightningSword(player.getInventory().getItemInMainHand())) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        if (event.getEntity().getEntityId() == player.getEntityId()) return;

        UUID id = player.getUniqueId();
        if (!abilityActive.getOrDefault(id, false)) return;

        abilityActive.put(id, false);

        BukkitTask expiry = windowTasks.remove(id);
        if (expiry != null && !expiry.isCancelled()) expiry.cancel();

        hideBar(player);
        clearActionBar(player);

        Location targetLoc = target.getLocation();
        target.getWorld().strikeLightningEffect(targetLoc);
        player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.9f);
        player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.8f, 1.0f);
        Bukkit.getScheduler().runTaskLater(this, () ->
            target.setVelocity(new org.bukkit.util.Vector(0, 0, 0)), 1L);

        if (target instanceof Player tp) lightningKillPending.add(tp.getUniqueId());

        spawnImpactParticles(targetLoc);
        startCooldownBar(player);
    }

    // ── 18s Cooldown bar — YELLOW only, no green ─────────────────────────────

    private void startCooldownBar(Player player) {
        UUID id = player.getUniqueId();
        cooldownStart.put(id, System.currentTimeMillis());

        new BukkitRunnable() {
            int secondsLeft = COOLDOWN_SECONDS;
            @Override public void run() {
                if (!player.isOnline()) { cancel(); return; }
                if (secondsLeft < 0) { cancel(); return; }

                if (secondsLeft == 0) {
                    cooldownStart.remove(id);
                    // READY — still yellow, brighter title
                    showBar(player,
                        ChatColor.YELLOW + "" + ChatColor.BOLD + "⚡ The Last Storm  " + ChatColor.GOLD + "READY",
                        BarColor.YELLOW, 1.0);
                    cancel();
                    return;
                }

                int elapsed = COOLDOWN_SECONDS - secondsLeft;
                double progress = (double) elapsed / COOLDOWN_SECONDS;
                showBar(player,
                    ChatColor.YELLOW + "⚡ " + ChatColor.WHITE + "Cooldown  " + ChatColor.GRAY + secondsLeft + "s",
                    BarColor.YELLOW, progress);
                secondsLeft--;
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    // ── Death message + kill animation ───────────────────────────────────────

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player dead = event.getEntity();
        if (!lightningKillPending.remove(dead.getUniqueId())) return;

        String msg = event.getDeathMessage();
        if (msg != null)
            event.setDeathMessage(ChatColor.YELLOW + "" + ChatColor.BOLD + "⚡ " + ChatColor.RESET + msg);

        Location loc = dead.getLocation();
        World world = dead.getWorld();

        for (int i = 0; i < 100; i++) {
            double ox = (Math.random() - 0.5) * 2.0;
            double oy = Math.random() * 3.0;
            double oz = (Math.random() - 0.5) * 2.0;
            Color c = (i % 3 == 0) ? Color.fromRGB(255, 215, 0)
                    : (i % 3 == 1) ? Color.fromRGB(255, 255, 0)
                    :                Color.fromRGB(255, 165, 0);
            world.spawnParticle(Particle.REDSTONE, loc.clone().add(ox, oy, oz), 1, 0, 0, 0, 0,
                new Particle.DustOptions(c, 2.0f));
        }
        world.spawnParticle(Particle.FLASH, loc.clone().add(0, 1, 0), 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.ELECTRIC_SPARK, loc.clone().add(0, 1, 0), 40, 0.5, 1.0, 0.5, 0.1);
    }

    // ── Impact Particles ─────────────────────────────────────────────────────

    private void spawnImpactParticles(Location loc) {
        World world = loc.getWorld();
        for (int i = 0; i < 80; i++) {
            double ox = (Math.random() - 0.5) * 2;
            double oy = Math.random() * 3;
            double oz = (Math.random() - 0.5) * 2;
            Color c = (i % 3 == 0) ? Color.fromRGB(255, 215, 0)
                    : (i % 3 == 1) ? Color.fromRGB(255, 255, 0)
                    :                Color.fromRGB(255, 165, 0);
            world.spawnParticle(Particle.REDSTONE, loc.clone().add(ox, oy, oz), 1, 0, 0, 0, 0,
                new Particle.DustOptions(c, 1.5f));
        }
        world.spawnParticle(Particle.FLASH, loc.clone().add(0, 1, 0), 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.ELECTRIC_SPARK, loc.clone().add(0, 1, 0), 30, 0.5, 1.0, 0.5, 0.1);
    }
}
