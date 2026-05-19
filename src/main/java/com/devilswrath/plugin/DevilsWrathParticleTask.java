package com.devilswrath.plugin;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

public class DevilsWrathParticleTask extends BukkitRunnable {

    private final DevilsWrathPlugin plugin;
    private int tick = 0;

    // Y offsets along the blade — very small, only vertical
    private static final double[] BLADE_Y = { 0.0, 0.15, 0.30, 0.45, 0.60 };

    public DevilsWrathParticleTask(DevilsWrathPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        tick++;

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();

            ItemStack mainHand = player.getInventory().getItemInMainHand();
            if (!DevilsWrathSword.isDevilsWrath(mainHand)) continue;

            boolean abilityOn = DevilsWrathListener.abilityActive.getOrDefault(uuid, false);
            spawnSwordParticles(player, abilityOn);
        }
    }

    private void spawnSwordParticles(Player player, boolean abilityActive) {
        World world = player.getWorld();

        // ── Right-hand position: offset to the right of the player ──
        double yaw = Math.toRadians(player.getLocation().getYaw());
        double rx = Math.cos(yaw);   // right vector X
        double rz = Math.sin(yaw);   // right vector Z

        // Hand base: player eye level shifted right and slightly forward
        Location base = player.getEyeLocation().clone()
                .add(rx * 0.5, -0.3, rz * 0.5);

        // Blade rotates up-down using a slow sine wave
        double swingAngle = Math.sin(tick * 0.18) * 0.45; // ±0.45 rad (~25°)

        // Forward vector (facing direction), modulated by swing
        double pitch = Math.toRadians(player.getLocation().getPitch()) + swingAngle;
        double fx = -Math.sin(yaw) * Math.cos(pitch);
        double fy = -Math.sin(pitch);
        double fz =  Math.cos(yaw) * Math.cos(pitch);

        for (double t : BLADE_Y) {
            // Each point along the blade follows the swing direction
            Location pos = base.clone().add(fx * t, fy * t, fz * t);

            if (abilityActive) {
                // Active: slightly more particles but still small & world-only
                // count=1, spread very small so they stay on the blade
                world.spawnParticle(Particle.SQUID_INK, pos, 1,
                        0.02, 0.02, 0.02, 0.0);
                if (tick % 2 == 0) {
                    world.spawnParticle(Particle.SQUID_INK, pos, 1,
                            0.03, 0.03, 0.03, 0.0);
                }
            } else {
                // Passive: 1 tiny particle every 3 ticks per blade point
                if (tick % 3 == 0) {
                    world.spawnParticle(Particle.SQUID_INK, pos, 1,
                            0.02, 0.02, 0.02, 0.0);
                }
            }
        }
    }
}
