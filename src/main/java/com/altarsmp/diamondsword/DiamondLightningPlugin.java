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

    // abilityActive: true = player sneaked, waiting for a hit
    private final Map<UUID, Boolean> abilityActive = new HashMap<>();
    // cooldownStart: when the 18s cooldown started (ms) — only set after a successful hit
    private final Map<UUID, Long> cooldownStart = new HashMap<>();
    private final Set<UUID> lightningKillPending = new HashSet<>();
    private final Map<UUID, BossBar> activeBossBars = new HashMap<>();
    private final Map<UUID, BukkitTask> glintTasks = new HashMap<>();
    // Track the 8s expiry task so we can cancel it if player hits in time
    private final Map<UUID, BukkitTask> windowTasks = new HashMap<>();

    private static final int COOLDOWN_SECONDS = 18;
    private static final int WINDOW_SECONDS = 8;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("DiamondLightningPlugin enabled! - AltarSMP");
        if (getCommand("lightningSword") != null)
            getCommand("lightningSword").setExecutor((sender, cmd, label, args) -> {
                if (sender instanceof Player player) {
                    giveLightningSword(player);
                    player.sendMessage(ChatColor.WHITE + "⚡ The Last Storm given!");
                }
                return true;
            });
    }

    @Override
    public void onDisable() {
        activeBossBars.values().forEach(BossBar::removeAll);
        activeBossBars.clear();
        glintTasks.values().forEach(t -> { if (!t.isCancelled()) t.cancel(); });
        glintTasks.clear();
        windowTasks.values().forEach(t -> { if (!t.isCancelled()) t.cancel(); });
        windowTasks.clear();
        getLogger().info("DiamondLightningPlugin disabled.");
    }

    // ── Give Sword ───────────────────────────────────────────────────────────

    public void giveLightningSword(Player player) {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = sword.getItemMeta();
        meta.setDisplayName(ChatColor.WHITE + "" + ChatColor.BOLD + "⚡ The Last Storm");

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Forged in the storms of AltarSMP");
        lore.add("");
        lore.add(ChatColor.YELLOW + "Passive: " + ChatColor.WHITE + "Looting IV");
        lore.add(ChatColor.YELLOW + "Passive: " + ChatColor.WHITE + "Unbreakable");
        lore.add(ChatColor.YELLOW + "Ability: " + ChatColor.WHITE + "Lightning Strike");
        lore.add(ChatColor.DARK_GRAY + "Sneak → activate, then Hit → ⚡ Strike!");
        lore.add(ChatColor.DARK_GRAY + "Cooldown: " + COOLDOWN_SECONDS + "s");
        lore.add("");
        lore.add(ChatColor.DARK_AQUA + "[AltarSMP]");
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
        if (item == null || item.getType() != Material.DIAMOND_SWORD) return false;
        if (!item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(
            new org.bukkit.NamespacedKey(this, SWORD_KEY),
            org.bukkit.persistence.PersistentDataType.BYTE);
    }

    // ── Glint (action bar shimmer — only when sword in hand & ready) ─────────

    private void startIdleGlint(Player player) {
        UUID id = player.getUniqueId();
        if (glintTasks.containsKey(id)) return;
        BukkitTask task = new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!player.isOnline()) { cancel(); glintTasks.remove(id); return; }
                if (!isLightningSword(player.getInventory().getItemInMainHand())) {
                    cancel(); glintTasks.remove(id);
                    clearActionBar(player);
                    return;
                }
                if (abilityActive.getOrDefault(id, false) || cooldownStart.containsKey(id)) {
                    cancel(); glintTasks.remove(id); return;
                }
                t++;
                String[] frames = {
                    ChatColor.GRAY  + "⚡ The Last Storm",
                    ChatColor.WHITE + "⚡ The Last Storm",
                    ChatColor.WHITE + "" + ChatColor.BOLD + "⚡ The Last Storm",
                    ChatColor.WHITE + "⚡ The Last Storm"
                };
                player.spigot().sendMessage(
                    net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    net.md_5.bungee.api.chat.TextComponent.fromLegacyText(frames[t % frames.length]));
            }
        }.runTaskTimer(this, 0L, 10L);
        glintTasks.put(id, task);
    }

    private void stopIdleGlint(Player player) {
        UUID id = player.getUniqueId();
        BukkitTask t = glintTasks.remove(id);
        if (t != null && !t.isCancelled()) t.cancel();
    }

    private void clearActionBar(Player player) {
        player.spigot().sendMessage(
            net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
            net.md_5.bungee.api.chat.TextComponent.fromLegacyText(""));
    }

    // ── Item held change ──────────────────────────────────────────────────────

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            boolean holdingSword = isLightningSword(player.getInventory().getItemInMainHand());
            boolean inCooldown = cooldownStart.containsKey(id);
            boolean windowActive = abilityActive.getOrDefault(id, false);
            if (holdingSword && !inCooldown && !windowActive) {
                startIdleGlint(player);
            } else if (!holdingSword) {
                stopIdleGlint(player);
                clearActionBar(player);
            }
        }, 1L);
    }

    // ── BossBar helpers ──────────────────────────────────────────────────────

    private BossBar getOrCreateBar(Player player) {
        return activeBossBars.computeIfAbsent(player.getUniqueId(), k -> {
            BossBar bar = Bukkit.createBossBar("", BarColor.WHITE, BarStyle.SOLID);
            bar.addPlayer(player);
            return bar;
        });
    }

    private void showBar(Player player, String text, BarColor color, double progress) {
        BossBar bar = getOrCreateBar(player);
        bar.setTitle(text);
        bar.setColor(color);
        bar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
        bar.setVisible(true);
    }

    private void hideBar(Player player) {
        BossBar bar = activeBossBars.get(player.getUniqueId());
        if (bar != null) bar.setVisible(false);
    }

    // ── Sneak → Activate ability ─────────────────────────────────────────────

    @EventHandler
    public void onSneak(org.bukkit.event.player.PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        Player player = event.getPlayer();
        if (!isLightningSword(player.getInventory().getItemInMainHand())) return;

        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();

        // Already in window — ignore
        if (abilityActive.getOrDefault(id, false)) return;

        // In cooldown — show remaining and block
        if (cooldownStart.containsKey(id)) {
            long elapsed = (now - cooldownStart.get(id)) / 1000;
            if (elapsed < COOLDOWN_SECONDS) {
                long remaining = COOLDOWN_SECONDS - elapsed;
                showBar(player,
                    ChatColor.WHITE + "⚡ The Last Storm  " + ChatColor.GRAY + remaining + "s",
                    BarColor.WHITE,
                    (double) elapsed / COOLDOWN_SECONDS);
                return;
            } else {
                cooldownStart.remove(id);
            }
        }

        // ── Activate ──
        abilityActive.put(id, true);
        stopIdleGlint(player);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.8f);

        // Show "READY TO STRIKE" in action bar — no boss bar during window
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!player.isOnline() || !abilityActive.getOrDefault(id, false)) {
                    cancel(); return;
                }
                t++;
                String[] frames = {
                    ChatColor.YELLOW + "⚡ " + ChatColor.WHITE + "Strike NOW!",
                    ChatColor.WHITE  + "⚡ " + ChatColor.YELLOW + "Strike NOW!",
                    ChatColor.YELLOW + "" + ChatColor.BOLD + "⚡ Strike NOW!",
                    ChatColor.WHITE  + "⚡ " + ChatColor.YELLOW + "Strike NOW!"
                };
                player.spigot().sendMessage(
                    net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    net.md_5.bungee.api.chat.TextComponent.fromLegacyText(frames[t % frames.length]));
            }
        }.runTaskTimer(this, 0L, 8L);

        // After 8s — if no hit, cancel ability (NO cooldown started — free try)
        BukkitTask expiry = new BukkitRunnable() {
            @Override public void run() {
                if (!abilityActive.getOrDefault(id, false)) return; // already used
                abilityActive.put(id, false);
                windowTasks.remove(id);
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.5f);
                clearActionBar(player);
                // Restart glint if sword still held
                if (isLightningSword(player.getInventory().getItemInMainHand())) {
                    startIdleGlint(player);
                }
            }
        }.runTaskLater(this, WINDOW_SECONDS * 20L);
        windowTasks.put(id, expiry);
    }

    // ── Hit → Lightning strike ───────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!isLightningSword(player.getInventory().getItemInMainHand())) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        if (event.getEntity().getEntityId() == player.getEntityId()) return;

        UUID id = player.getUniqueId();
        if (!abilityActive.getOrDefault(id, false)) return;

        // Consume ability
        abilityActive.put(id, false);

        // Cancel the 8s expiry task
        BukkitTask expiry = windowTasks.remove(id);
        if (expiry != null && !expiry.isCancelled()) expiry.cancel();

        clearActionBar(player);

        Location targetLoc = target.getLocation();
        target.getWorld().strikeLightningEffect(targetLoc);
        player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.9f);
        player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.8f, 1.0f);
        Bukkit.getScheduler().runTaskLater(this, () -> target.setVelocity(new org.bukkit.util.Vector(0, 0, 0)), 1L);

        if (target instanceof Player targetPlayer) {
            lightningKillPending.add(targetPlayer.getUniqueId());
        }

        spawnImpactParticles(targetLoc);

        // Start 18s cooldown only after a successful hit
        startCooldownBar(player);
    }

    // ── 18s Cooldown bar (0→1 fills, plain WHITE) ────────────────────────────

    private void startCooldownBar(Player player) {
        UUID id = player.getUniqueId();
        cooldownStart.put(id, System.currentTimeMillis());

        new BukkitRunnable() {
            int secondsLeft = COOLDOWN_SECONDS;
            @Override public void run() {
                if (!player.isOnline()) { cancel(); return; }
                if (secondsLeft <= 0) {
                    cooldownStart.remove(id);
                    showBar(player,
                        ChatColor.WHITE + "⚡ " + ChatColor.BOLD + "The Last Storm  " + ChatColor.RESET + ChatColor.GREEN + "READY",
                        BarColor.GREEN,
                        1.0);
                    cancel();
                    if (isLightningSword(player.getInventory().getItemInMainHand())) {
                        startIdleGlint(player);
                    }
                    return;
                }
                int elapsed = COOLDOWN_SECONDS - secondsLeft;
                double progress = (double) elapsed / COOLDOWN_SECONDS;
                showBar(player,
                    ChatColor.WHITE + "⚡ The Last Storm  " + ChatColor.GRAY + secondsLeft + "s",
                    BarColor.WHITE,
                    progress);
                secondsLeft--;
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    // ── Death message ────────────────────────────────────────────────────────

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player dead = event.getEntity();
        if (!lightningKillPending.remove(dead.getUniqueId())) return;
        String deathMsg = event.getDeathMessage();
        if (deathMsg != null)
            event.setDeathMessage(ChatColor.WHITE + "" + ChatColor.BOLD + "⚡ " + deathMsg);

        Location loc = dead.getLocation();
        World world = dead.getWorld();
        for (int i = 0; i < 80; i++) {
            double ox = (Math.random() - 0.5) * 1.5;
            double oy = Math.random() * 2.5;
            double oz = (Math.random() - 0.5) * 1.5;
            world.spawnParticle(Particle.REDSTONE, loc.clone().add(ox, oy, oz), 1, 0, 0, 0, 0,
                new Particle.DustOptions(Color.WHITE, 2.0f));
        }
        world.spawnParticle(Particle.FLASH, loc.clone().add(0, 1, 0), 1, 0, 0, 0, 0);
    }

    // ── Impact Particles ─────────────────────────────────────────────────────

    private void spawnImpactParticles(Location loc) {
        World world = loc.getWorld();
        for (int i = 0; i < 80; i++) {
            double ox = (Math.random() - 0.5) * 2;
            double oy = Math.random() * 3;
            double oz = (Math.random() - 0.5) * 2;
            Color c = (i % 2 == 0) ? Color.WHITE : Color.YELLOW;
            world.spawnParticle(Particle.REDSTONE, loc.clone().add(ox, oy, oz), 1, 0, 0, 0, 0,
                new Particle.DustOptions(c, 1.5f));
        }
        world.spawnParticle(Particle.FLASH, loc.clone().add(0, 1, 0), 1, 0, 0, 0, 0);
    }
}
