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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class DiamondLightningPlugin extends JavaPlugin implements Listener {

    private static final String SWORD_KEY = "altar_lightning_sword";

    // abilityActive: true = 8s warning window is running, waiting for hit
    private final Map<UUID, Boolean> abilityActive = new HashMap<>();
    // cooldownStart: when the 18s cooldown started (ms)
    private final Map<UUID, Long> cooldownStart = new HashMap<>();
    private final Set<UUID> lightningKillPending = new HashSet<>();
    private final Map<UUID, BossBar> activeBossBars = new HashMap<>();
    private final Map<UUID, BukkitTask> glintTasks = new HashMap<>();

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

        startIdleGlint(player);
    }

    private boolean isLightningSword(ItemStack item) {
        if (item == null || item.getType() != Material.DIAMOND_SWORD) return false;
        if (!item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(
            new org.bukkit.NamespacedKey(this, SWORD_KEY),
            org.bukkit.persistence.PersistentDataType.BYTE);
    }

    // ── Glint (action bar shimmer when idle) ─────────────────────────────────

    private void startIdleGlint(Player player) {
        UUID id = player.getUniqueId();
        if (glintTasks.containsKey(id)) return;
        BukkitTask task = new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!player.isOnline() || !isLightningSword(player.getInventory().getItemInMainHand())) {
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

    // ── BossBar helpers ──────────────────────────────────────────────────────

    private BossBar getOrCreateBar(Player player) {
        return activeBossBars.computeIfAbsent(player.getUniqueId(), k -> {
            BossBar bar = Bukkit.createBossBar("", BarColor.WHITE, BarStyle.SOLID);
            bar.addPlayer(player);
            return bar;
        });
    }

    private void showBar(Player player, String text, double progress) {
        BossBar bar = getOrCreateBar(player);
        bar.setTitle(text);
        bar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
        bar.setVisible(true);
    }

    // ── Sneak → Activate ability ─────────────────────────────────────────────

    @EventHandler
    public void onSneak(org.bukkit.event.player.PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        Player player = event.getPlayer();
        if (!isLightningSword(player.getInventory().getItemInMainHand())) return;

        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();

        // Already in warning window — ignore
        if (abilityActive.getOrDefault(id, false)) return;

        // In cooldown — show remaining and block
        if (cooldownStart.containsKey(id)) {
            long elapsed = (now - cooldownStart.get(id)) / 1000;
            if (elapsed < COOLDOWN_SECONDS) {
                long remaining = COOLDOWN_SECONDS - elapsed;
                showBar(player,
                    ChatColor.WHITE + "⚡ The Last Storm  " + ChatColor.GRAY + remaining + "s",
                    (double) elapsed / COOLDOWN_SECONDS);
                return;
            } else {
                cooldownStart.remove(id); // cooldown done, clear it
            }
        }

        // ── Activate! ──
        abilityActive.put(id, true);
        stopIdleGlint(player);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.8f);
        spawnActivationCircle(player);

        // 8s WARNING countdown bar (1→0)
        new BukkitRunnable() {
            int secondsLeft = WINDOW_SECONDS;
            @Override public void run() {
                if (!player.isOnline()) { cancel(); return; }
                if (!abilityActive.getOrDefault(id, false)) { cancel(); return; }
                if (secondsLeft < 0) { cancel(); return; }

                showBar(player,
                    ChatColor.YELLOW + "⚡ Strike NOW!  " + ChatColor.WHITE + secondsLeft + "s",
                    (double) (WINDOW_SECONDS - secondsLeft) / WINDOW_SECONDS);
                secondsLeft--;
            }
        }.runTaskTimer(this, 0L, 20L);

        // After 8s — if no hit, cancel ability and start 18s cooldown
        new BukkitRunnable() {
            @Override public void run() {
                if (!abilityActive.getOrDefault(id, false)) return; // already used
                abilityActive.put(id, false);
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.5f);
                startCooldownBar(player);
            }
        }.runTaskLater(this, WINDOW_SECONDS * 20L);
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

        Location targetLoc = target.getLocation();
        target.getWorld().strikeLightningEffect(targetLoc);
        player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.9f);
        player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.8f, 1.0f);
        Bukkit.getScheduler().runTaskLater(this, () -> target.setVelocity(new org.bukkit.util.Vector(0, 0, 0)), 1L);

        if (target instanceof Player targetPlayer) {
            lightningKillPending.add(targetPlayer.getUniqueId());
        }

        spawnImpactParticles(targetLoc);

        // Start 18s cooldown
        startCooldownBar(player);
    }

    // ── 18s Cooldown bar (0→1 fills) ─────────────────────────────────────────

    private void startCooldownBar(Player player) {
        UUID id = player.getUniqueId();
        cooldownStart.put(id, System.currentTimeMillis());

        new BukkitRunnable() {
            int secondsLeft = COOLDOWN_SECONDS;
            @Override public void run() {
                if (!player.isOnline()) { cancel(); return; }
                if (secondsLeft <= 0) {
                    cooldownStart.remove(id);
                    showBar(player, ChatColor.WHITE + "⚡ The Last Storm  " + ChatColor.WHITE + "⚡ READY", 1.0);
                    cancel();
                    startIdleGlint(player);
                    return;
                }
                int elapsed = COOLDOWN_SECONDS - secondsLeft;
                // Bar fills 0→1 as time passes
                showBar(player,
                    ChatColor.WHITE + "⚡ The Last Storm  " + ChatColor.GRAY + secondsLeft + "s",
                    (double) elapsed / COOLDOWN_SECONDS);
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

    // ── Particles ────────────────────────────────────────────────────────────

    // 8x8 activation circle — only WHITE and YELLOW
    private void spawnActivationCircle(Player player) {
        Location center = player.getLocation().add(0, 0.5, 0);
        double radius = 4.0;
        int points = 80;
        int layers = 3;

        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (tick >= 60) { cancel(); return; }
                for (int i = 0; i < points; i++) {
                    double angle = (2 * Math.PI / points) * i;
                    double x = center.getX() + radius * Math.cos(angle);
                    double z = center.getZ() + radius * Math.sin(angle);
                    for (int layer = 0; layer < layers; layer++) {
                        Location loc = new Location(center.getWorld(), x, center.getY() + layer * 0.12, z);
                        Color c = (i % 2 == 0) ? Color.WHITE : Color.YELLOW;
                        center.getWorld().spawnParticle(Particle.REDSTONE, loc, 1, 0, 0, 0, 0,
                            new Particle.DustOptions(c, 1.3f));
                    }
                }
                tick += 2;
            }
        }.runTaskTimer(this, 0L, 2L);
    }

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
