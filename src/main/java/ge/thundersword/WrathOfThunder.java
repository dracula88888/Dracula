package ge.thundersword;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.ChatColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class WrathOfThunder extends JavaPlugin implements Listener {

    private final Map<UUID, Integer> hitCounter = new HashMap<>();
    private final Map<UUID, UUID> pendingKills  = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Wrath of Thunder enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Wrath of Thunder disabled.");
    }

    // ── every 4th hit → lightning ──
    @EventHandler
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        if (!isWrathOfThunder(player.getInventory().getItemInMainHand())) return;

        int hits = hitCounter.getOrDefault(player.getUniqueId(), 0) + 1;

        if (hits >= 4) {
            hitCounter.put(player.getUniqueId(), 0);
            Location loc = target.getLocation();
            pendingKills.put(target.getUniqueId(), player.getUniqueId());
            target.getWorld().strikeLightning(loc);
            spawnHitEffect(loc);
        } else {
            hitCounter.put(player.getUniqueId(), hits);
        }
    }

    // ── wielder immune to their own lightning ──
    @EventHandler(priority = EventPriority.HIGH)
    public void onLightningDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof LightningStrike)) return;
        if (!(event.getEntity() instanceof Player victim)) return;
        if (isWrathOfThunder(victim.getInventory().getItemInMainHand())) {
            event.setCancelled(true);
        }
    }

    // ── player kill → custom death message + kill animation ──
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player dead = event.getEntity();
        UUID killerUUID = pendingKills.remove(dead.getUniqueId());
        if (killerUUID == null) return;

        Player killer = Bukkit.getPlayer(killerUUID);
        if (killer == null) return;
        if (!isWrathOfThunder(killer.getInventory().getItemInMainHand())) return;

        event.setDeathMessage(
            ChatColor.GRAY + dead.getName()
            + " was slain by "
            + ChatColor.WHITE + killer.getName()
            + ChatColor.GRAY + " using "
            + ChatColor.WHITE + "" + ChatColor.BOLD + "[Wrath of Thunder]"
        );

        playKillAnimation(dead.getLocation(), killer);
    }

    private void playKillAnimation(Location loc, Player killer) {
        Particle.DustOptions whiteDust = new Particle.DustOptions(Color.WHITE, 2.0f);

        loc.getWorld().spawnParticle(Particle.FLASH,     loc, 1,  0,   0,   0,   0);
        loc.getWorld().spawnParticle(Particle.WHITE_ASH, loc, 80, 0.5, 1.0, 0.5, 0.2);
        loc.getWorld().spawnParticle(Particle.REDSTONE,  loc, 60, 0.6, 1.0, 0.6, whiteDust);
        loc.getWorld().spawnParticle(Particle.CRIT,      loc, 30, 0.4, 0.8, 0.4, 0.2);

        loc.getWorld().playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 0.6f);
        loc.getWorld().playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT,  1.0f, 1.2f);

        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (tick >= 10) { cancel(); return; }
                double angle = tick * 72 * Math.PI / 180;
                Location ring = loc.clone().add(
                    Math.cos(angle) * 0.8,
                    tick * 0.15,
                    Math.sin(angle) * 0.8
                );
                loc.getWorld().spawnParticle(Particle.REDSTONE, ring, 5, 0.1, 0.1, 0.1, whiteDust);
                loc.getWorld().spawnParticle(Particle.CRIT,     ring, 4, 0.2, 0.2, 0.2, 0.1);
                tick++;
            }
        }.runTaskTimer(this, 0L, 2L);

        killer.sendTitle(
            ChatColor.WHITE + "" + ChatColor.BOLD + killer.getName(),
            ChatColor.WHITE + "⚡ Wrath of Thunder ⚡",
            5, 50, 15
        );
    }

    private void spawnHitEffect(Location loc) {
        Particle.DustOptions whiteDust = new Particle.DustOptions(Color.WHITE, 1.5f);
        loc.getWorld().spawnParticle(Particle.REDSTONE,  loc, 30, 0.3, 0.6, 0.3, whiteDust);
        loc.getWorld().spawnParticle(Particle.CRIT,      loc, 15, 0.3, 0.5, 0.3, 0.2);
    }

    private boolean isWrathOfThunder(ItemStack item) {
        if (item == null || item.getType() != Material.DIAMOND_SWORD) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return false;
        return ChatColor.stripColor(meta.getDisplayName()).equals("Wrath of Thunder");
    }

    public static ItemStack createWrathOfThunder() {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = sword.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.WHITE + "" + ChatColor.BOLD + "Wrath of Thunder");
            meta.setLore(Arrays.asList(
                ChatColor.DARK_GRAY + "⚔ " + ChatColor.GRAY + "Legendary Sword",
                ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━━",
                ChatColor.GRAY + "Passive: " + ChatColor.WHITE + "" + ChatColor.BOLD + "Thunder",
                ChatColor.GRAY + "Every " + ChatColor.YELLOW + "4th hit" + ChatColor.GRAY + " calls lightning",
                ChatColor.GRAY + "upon your enemies.",
                ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━━",
                ChatColor.DARK_PURPLE + "✦ " + ChatColor.LIGHT_PURPLE + "The sky bows to its wielder."
            ));
            meta.addEnchant(
                Registry.ENCHANTMENT.get(NamespacedKey.minecraft("unbreaking")),
                10, true
            );
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            sword.setItemMeta(meta);
        }
        return sword;
    }
}
