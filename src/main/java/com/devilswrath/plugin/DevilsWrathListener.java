package com.devilswrath.plugin;

import org.bukkit.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DevilsWrathListener implements Listener {

    private final DevilsWrathPlugin plugin;

    private final Map<UUID, Integer> activeAbilities  = new HashMap<>();
    private final Map<UUID, Integer> cooldownTasks    = new HashMap<>();
    private final Map<UUID, Integer> actionBarTasks   = new HashMap<>();

    public static final Map<UUID, Boolean>  abilityActive     = new HashMap<>();
    public static final Map<UUID, Integer>  cooldownRemaining = new HashMap<>();

    private static final long ABILITY_DURATION_TICKS = 160L; // 8 s
    private static final long COOLDOWN_TICKS         = 160L; // 8 s
    private static final int  COOLDOWN_SECONDS       = 8;

    // Radius for the ring effect
    private static final double RING_RADIUS = 8.0;

    public DevilsWrathListener(DevilsWrathPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Sneak → activate ability ──────────────────────────────────────────
    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (!event.isSneaking()) return;

        ItemStack held = player.getInventory().getItemInMainHand();
        if (!DevilsWrathSword.isDevilsWrath(held)) return;

        UUID uuid = player.getUniqueId();

        if (abilityActive.getOrDefault(uuid, false)) return;

        if (cooldownTasks.containsKey(uuid)) {
            int rem = cooldownRemaining.getOrDefault(uuid, 0);
            player.sendMessage(ChatColor.DARK_GRAY + "Devils Wrath is recovering... " + rem + "s remaining.");
            return;
        }

        activateAbility(player);
    }

    // ── Hit entity → apply effects + hit particles ───────────────────────
    @EventHandler
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        Player attacker = (Player) event.getDamager();

        ItemStack held = attacker.getInventory().getItemInMainHand();
        if (!DevilsWrathSword.isDevilsWrath(held)) return;

        if (!(event.getEntity() instanceof LivingEntity)) return;
        LivingEntity victim = (LivingEntity) event.getEntity();

        // Only apply effects + indicator when ability is active
        if (abilityActive.getOrDefault(attacker.getUniqueId(), false)) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 80, 0, false, false));
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,  80, 0, false, false));

            // Visual indicator at the hit entity so attacker knows the effect landed
            spawnHitEffectIndicator(victim.getLocation().add(0, 1, 0));
        }
    }

    // ── Cleanup on logout ─────────────────────────────────────────────────
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        cancelTask(activeAbilities.remove(uuid));
        cancelTask(cooldownTasks.remove(uuid));
        cancelTask(actionBarTasks.remove(uuid));

        abilityActive.remove(uuid);
        cooldownRemaining.remove(uuid);
    }

    // ── Internal helpers ──────────────────────────────────────────────────

    private void cancelTask(Integer taskId) {
        if (taskId != null) Bukkit.getScheduler().cancelTask(taskId);
    }

    private void activateAbility(Player player) {
        UUID uuid = player.getUniqueId();
        abilityActive.put(uuid, true);

        player.sendMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + "⚔ Devils Wrath awakened! ⚔");
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.4f, 0.5f);

        // Spawn the 8-block-radius black ring (appears, stays ~1 s, fades)
        spawnAbilityRing(player.getLocation());

        // Schedule ability end
        int taskId = new BukkitRunnable() {
            @Override public void run() {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) deactivateAbility(p);
                else { abilityActive.remove(uuid); activeAbilities.remove(uuid); }
            }
        }.runTaskLater(plugin, ABILITY_DURATION_TICKS).getTaskId();

        activeAbilities.put(uuid, taskId);
    }

    private void deactivateAbility(Player player) {
        UUID uuid = player.getUniqueId();
        abilityActive.put(uuid, false);
        activeAbilities.remove(uuid);

        player.sendMessage(ChatColor.GRAY + "Devils Wrath fades... recovering for " + COOLDOWN_SECONDS + " seconds.");
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.3f, 1.5f);

        cooldownRemaining.put(uuid, COOLDOWN_SECONDS);

        // Action bar countdown
        int actionBarTaskId = new BukkitRunnable() {
            int secondsLeft = COOLDOWN_SECONDS;

            @Override public void run() {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null || !p.isOnline()) { cancel(); actionBarTasks.remove(uuid); return; }

                ItemStack held = p.getInventory().getItemInMainHand();

                if (secondsLeft <= 0) {
                    if (DevilsWrathSword.isDevilsWrath(held))
                        p.sendActionBar(ChatColor.GREEN + "⚔ Devils Wrath Ready! ⚔");
                    cancel(); actionBarTasks.remove(uuid); cooldownRemaining.remove(uuid);
                    return;
                }

                if (DevilsWrathSword.isDevilsWrath(held)) {
                    int totalSeg = 10;
                    int filled  = (int) Math.ceil((double) secondsLeft / COOLDOWN_SECONDS * totalSeg);
                    StringBuilder bar = new StringBuilder(ChatColor.DARK_RED + "⚔ ");
                    for (int i = 0; i < totalSeg; i++)
                        bar.append(i < filled ? ChatColor.DARK_RED + "█" : ChatColor.DARK_GRAY + "░");
                    bar.append(ChatColor.DARK_RED + " ").append(secondsLeft).append("s");
                    p.sendActionBar(bar.toString());
                }

                cooldownRemaining.put(uuid, secondsLeft);
                secondsLeft--;
            }
        }.runTaskTimer(plugin, 0L, 20L).getTaskId();

        actionBarTasks.put(uuid, actionBarTaskId);

        // Cooldown gate
        int cooldownTaskId = new BukkitRunnable() {
            @Override public void run() {
                cooldownTasks.remove(uuid);
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    p.sendMessage(ChatColor.DARK_RED + "⚔ Devils Wrath is ready again. ⚔");
                    p.playSound(p.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.4f, 0.8f);
                }
            }
        }.runTaskLater(plugin, COOLDOWN_TICKS).getTaskId();

        cooldownTasks.put(uuid, cooldownTaskId);
    }

    // ── Ring effect: 8-block radius black circle, appears then fades ──────
    // Runs for ~20 ticks (1 s). Particles: SQUID_INK on the ground ring.
    private void spawnAbilityRing(Location center) {
        new BukkitRunnable() {
            int ticks = 0;

            @Override public void run() {
                if (ticks >= 20) { cancel(); return; }

                World world = center.getWorld();
                int points = 64; // smooth circle

                // Fade: full for first 10 ticks, then reduce count to 0
                // Use count=1 always; control visibility via randomness
                double visibility = ticks < 10 ? 1.0 : 1.0 - ((ticks - 10) / 10.0);

                for (int i = 0; i < points; i++) {
                    if (Math.random() > visibility) continue; // fade out
                    double angle = (2 * Math.PI / points) * i;
                    double x = Math.cos(angle) * RING_RADIUS;
                    double z = Math.sin(angle) * RING_RADIUS;
                    Location pos = center.clone().add(x, 0.05, z);
                    world.spawnParticle(Particle.SQUID_INK, pos, 1,
                            0.0, 0.0, 0.0, 0.0);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L); // every tick for precision
    }

    // ── Hit effect indicator: tiny burst so attacker knows effect applied ──
    private void spawnHitEffectIndicator(Location loc) {
        World world = loc.getWorld();
        // Small SQUID_INK burst at the hit point — visible in the world, not on screen
        world.spawnParticle(Particle.SQUID_INK, loc, 6, 0.15, 0.15, 0.15, 0.02);
    }
}
