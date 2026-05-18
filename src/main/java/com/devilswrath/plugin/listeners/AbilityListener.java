package com.devilswrath.plugin.listeners;

import com.devilswrath.plugin.DevilsWrathPlugin;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.NamespacedKey;

import java.util.*;

public class AbilityListener implements Listener {

    // ── Constants ─────────────────────────────────────────────────────────────

    /** 48-second cooldown in milliseconds */
    private static final long COOLDOWN_MS = 48_000L;

    /** Blindness duration: 4 seconds = 80 ticks */
    private static final int BLINDNESS_TICKS = 80;

    /** Shield disable: 5 seconds = 100 ticks */
    private static final int SHIELD_DISABLE_TICKS = 100;

    /** Boss-bar refresh every second */
    private static final long BOSSBAR_TICK_INTERVAL = 20L;

    // ── State ─────────────────────────────────────────────────────────────────

    /** playerUUID → cooldown expiry timestamp (ms) */
    private final Map<UUID, Long>       cooldownExpiry = new HashMap<>();
    /** playerUUID → active boss bar */
    private final Map<UUID, BossBar>    playerBossBars = new HashMap<>();
    /** playerUUID → boss-bar tick task */
    private final Map<UUID, BukkitTask> playerTasks    = new HashMap<>();

    // PDC key for persisting the cooldown across restarts
    private final NamespacedKey COOLDOWN_PDC_KEY;

    private final DevilsWrathPlugin plugin;

    public AbilityListener(DevilsWrathPlugin plugin) {
        this.plugin           = plugin;
        this.COOLDOWN_PDC_KEY = new NamespacedKey(plugin, "devilswrath_cooldown");
    }

    // ── Main hit handler ──────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {

        // 1. Attacker must be a player
        if (!(event.getDamager() instanceof Player attacker)) return;

        // 2. Must be holding Devil's Wrath in main hand
        ItemStack held = attacker.getInventory().getItemInMainHand();
        if (!plugin.getDevilsWrathItem().isDevilsWrath(held)) return;

        // 3. Must be sneaking
        if (!attacker.isSneaking()) return;

        // 4. Target must be a living entity
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        // ── Shield-break (fires on ANY sneaking hit, no cooldown needed) ──────
        if (target instanceof Player targetPlayer) {
            tryBreakShield(targetPlayer);
        }

        // 5. Crit check — only the blindness ability requires a crit
        if (!isCriticalHit(attacker)) return;

        // 6. Cooldown check
        UUID uuid      = attacker.getUniqueId();
        long now       = System.currentTimeMillis();
        long remaining = cooldownExpiry.getOrDefault(uuid, 0L) - now;

        if (remaining > 0) {
            long seconds = (remaining + 999) / 1000;
            attacker.sendActionBar(
                Component.text("\uD83D\uDD25 Veil of Wrath \u2014 " + seconds + "s remaining")
                    .color(NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false)
            );
            return;
        }

        // 7. Activate — apply blindness
        target.addPotionEffect(new PotionEffect(
            PotionEffectType.BLINDNESS,
            BLINDNESS_TICKS,
            0,      // amplifier: Blindness I
            false,  // not ambient
            true,   // show particles
            true    // show icon
        ));

        // 8. Start cooldown & boss bar
        long expiry = now + COOLDOWN_MS;
        cooldownExpiry.put(uuid, expiry);
        // Persist so a relog re-shows the cooldown
        attacker.getPersistentDataContainer().set(COOLDOWN_PDC_KEY, PersistentDataType.LONG, expiry);
        startBossBar(attacker);

        // 9. Effects
        playActivationEffects(attacker, target);

        attacker.sendActionBar(
            Component.text("\uD83D\uDD25 Veil of Wrath activated! Target blinded for 4 seconds.")
                .color(NamedTextColor.DARK_RED)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false)
        );
    }

    // ── Shield-break ──────────────────────────────────────────────────────────

    private void tryBreakShield(Player target) {
        ItemStack offhand = target.getInventory().getItemInOffHand();
        ItemStack mainhand = target.getInventory().getItemInMainHand();

        boolean holdingShield =
            offhand.getType() == Material.SHIELD ||
            mainhand.getType() == Material.SHIELD;

        if (!holdingShield) return;

        // Apply the shield item cooldown (vanilla shield-break mechanic)
        target.setCooldown(Material.SHIELD, SHIELD_DISABLE_TICKS);

        target.playSound(target.getLocation(), Sound.ITEM_SHIELD_BREAK, 1.0f, 1.0f);
        target.sendActionBar(
            Component.text("Your shield has been broken!")
                .color(NamedTextColor.DARK_RED)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false)
        );
    }

    // ── Critical-hit detection ────────────────────────────────────────────────

    /**
     * Mirrors vanilla critical-hit conditions:
     * player is airborne (not on ground), moving downward (negative Y velocity),
     * not sprinting, not blind, not inside a vehicle.
     */
    private boolean isCriticalHit(Player player) {
        return !player.isOnGround()
            && player.getVelocity().getY() < 0
            && !player.isSprinting()
            && !player.isInsideVehicle()
            && player.getActivePotionEffects().stream()
               .noneMatch(e -> e.getType() == PotionEffectType.BLINDNESS);
    }

    // ── Visual & audio feedback ───────────────────────────────────────────────

    private void playActivationEffects(Player attacker, LivingEntity target) {
        var world   = target.getWorld();
        var hitLoc  = target.getLocation().add(0, target.getHeight() * 0.6, 0);

        // Dark-smoke burst on target
        world.spawnParticle(Particle.SQUID_INK,  hitLoc, 35, 0.4, 0.6, 0.4, 0.04);
        world.spawnParticle(Particle.SMOKE,       hitLoc, 20, 0.3, 0.5, 0.3, 0.02);
        world.spawnParticle(Particle.LARGE_SMOKE, hitLoc, 10, 0.4, 0.5, 0.4, 0.01);

        // Red-dust ring
        world.spawnParticle(Particle.DUST, hitLoc, 40, 0.6, 0.4, 0.6, 0,
            new Particle.DustOptions(org.bukkit.Color.fromRGB(180, 0, 0), 2.2f));

        // Sounds
        world.playSound(hitLoc, Sound.ENTITY_WITHER_SHOOT,        0.6f, 0.5f);
        world.playSound(hitLoc, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.0f, 0.8f);
        attacker.playSound(attacker.getLocation(), Sound.ENTITY_EVOKER_PREPARE_WOLOLO, 0.5f, 0.9f);
    }

    // ── Boss-bar cooldown display (pattern from ApolloDrawListener) ───────────

    private BossBar getOrCreateBossBar(UUID uuid) {
        return playerBossBars.computeIfAbsent(uuid, k -> BossBar.bossBar(
            Component.text("\uD83D\uDD25 Veil of Wrath Cooldown")
                .color(NamedTextColor.DARK_RED)
                .decoration(TextDecoration.BOLD, true),
            1.0f,
            BossBar.Color.RED,
            BossBar.Overlay.NOTCHED_10
        ));
    }

    private void startBossBar(Player player) {
        UUID uuid = player.getUniqueId();

        // Cancel any existing task
        BukkitTask old = playerTasks.remove(uuid);
        if (old != null && !old.isCancelled()) old.cancel();

        BossBar bar = getOrCreateBossBar(uuid);
        player.showBossBar(bar);

        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                hideBossBar(player);
                return;
            }

            long now       = System.currentTimeMillis();
            long expiry    = cooldownExpiry.getOrDefault(uuid, 0L);
            long remaining = expiry - now;

            if (remaining <= 0) {
                player.hideBossBar(bar);
                BukkitTask self = playerTasks.remove(uuid);
                if (self != null) self.cancel();

                player.sendActionBar(
                    Component.text("\uD83D\uDD25 Veil of Wrath is ready!")
                        .color(NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false)
                );
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
                return;
            }

            long  seconds  = (remaining + 999) / 1000;
            float progress = Math.min(1.0f, (float) remaining / COOLDOWN_MS);

            bar.name(
                Component.text("\uD83D\uDD25 Veil of Wrath \u2014 " + seconds + "s")
                    .color(NamedTextColor.DARK_RED)
                    .decoration(TextDecoration.BOLD, true)
            );
            bar.progress(progress);
            bar.color(progress > 0.4f ? BossBar.Color.RED : BossBar.Color.WHITE);

        }, 1L, BOSSBAR_TICK_INTERVAL);

        playerTasks.put(uuid, task);
    }

    private void hideBossBar(Player player) {
        UUID uuid = player.getUniqueId();
        cooldownExpiry.remove(uuid);
        BossBar bar = playerBossBars.remove(uuid);
        if (bar != null) player.hideBossBar(bar);
        BukkitTask task = playerTasks.remove(uuid);
        if (task != null && !task.isCancelled()) task.cancel();
    }

    // ── Join / Quit ───────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID   uuid   = player.getUniqueId();

        var storage = plugin.getOwnerStorage();
        if (!storage.isClaimed()) return;
        if (!uuid.equals(storage.getOwnerUuid())) return;

        // Restore cooldown from PDC
        long saved = player.getPersistentDataContainer()
            .getOrDefault(COOLDOWN_PDC_KEY, PersistentDataType.LONG, 0L);

        cooldownExpiry.put(uuid, saved);

        if (saved > System.currentTimeMillis()) {
            startBossBar(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hideBossBar(event.getPlayer());
    }

    // ── Plugin-disable cleanup ────────────────────────────────────────────────

    public void cleanupAll() {
        List<UUID> uuids = new ArrayList<>(playerBossBars.keySet());
        for (UUID uuid : uuids) {
            Player online = plugin.getServer().getPlayer(uuid);
            BossBar bar   = playerBossBars.remove(uuid);
            if (bar != null && online != null) online.hideBossBar(bar);
            BukkitTask task = playerTasks.remove(uuid);
            if (task != null && !task.isCancelled()) task.cancel();
        }
        playerTasks.clear();
        cooldownExpiry.clear();
    }
}
