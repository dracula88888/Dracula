package ge.thundersword;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class WrathOfThunder extends JavaPlugin implements Listener {

    // ── hit counter: how many times each player has hit with the sword ──
    private final Map<UUID, Integer> hitCounter = new HashMap<>();
    // ── tracks which entity was thunder-struck, and by whom ──
    private final Map<UUID, UUID> pendingKills = new HashMap<>();

    // ═══════════════════════════════════════════════════════
    //  PLUGIN LIFECYCLE
    // ═══════════════════════════════════════════════════════

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Wrath of Thunder enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Wrath of Thunder disabled.");
    }

    // ═══════════════════════════════════════════════════════
    //  PASSIVE: every 4th hit → lightning
    // ═══════════════════════════════════════════════════════

    @EventHandler
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        if (!(event.getEntity() instanceof LivingEntity)) return;

        Player player = (Player) event.getDamager();
        Entity target  = event.getEntity();

        if (!isWrathOfThunder(player.getInventory().getItemInMainHand())) return;

        int hits = hitCounter.getOrDefault(player.getUniqueId(), 0) + 1;

        if (hits >= 4) {
            hitCounter.put(player.getUniqueId(), 0);
            Location loc = target.getLocation();

            // register before strike so the death event can find it
            pendingKills.put(target.getUniqueId(), player.getUniqueId());

            // real lightning — attacker is immune via onLightningDamage below
            target.getWorld().strikeLightning(loc);

            // small white particle burst on every thunder hit
            spawnHitEffect(loc);

        } else {
            hitCounter.put(player.getUniqueId(), hits);
        }
    }

    // ═══════════════════════════════════════════════════════
    //  SELF-IMMUNITY: sword wielder takes no lightning damage
    // ═══════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGH)
    public void onLightningDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof LightningStrike)) return;
        if (!(event.getEntity() instanceof Player)) return;

        Player victim = (Player) event.getEntity();
        if (isWrathOfThunder(victim.getInventory().getItemInMainHand())) {
            event.setCancelled(true);
        }
    }

    // ═══════════════════════════════════════════════════════
    //  KILL EVENT: player kill → kill animation + custom death msg
    // ═══════════════════════════════════════════════════════

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        UUID killerUUID = pendingKills.remove(dead.getUniqueId());

        // only player kills get the animation
        if (!(dead instanceof Player)) return;
        if (killerUUID == null) return;

        Player killer = Bukkit.getPlayer(killerUUID);
        if (killer == null) return;
        if (!isWrathOfThunder(killer.getInventory().getItemInMainHand())) return;

        Player deadPlayer = (Player) dead;

        // ── replace vanilla death message with custom one ──
        // format:  DeadName was slain by KillerName using [Wrath of Thunder]
        event.setDeathMessage(
            ChatColor.GRAY + deadPlayer.getName()
            + " was slain by "
            + ChatColor.WHITE + killer.getName()
            + ChatColor.GRAY + " using "
            + ChatColor.WHITE + ChatColor.BOLD.toString() + "[Wrath of Thunder]"
        );

        // ── kill animation ──
        playKillAnimation(dead.getLocation(), killer);
    }

    // ═══════════════════════════════════════════════════════
    //  KILL ANIMATION
    // ═══════════════════════════════════════════════════════

    private void playKillAnimation(Location loc, Player killer) {
        Particle.DustOptions whiteDust = new Particle.DustOptions(Color.WHITE, 2.0f);

        // instant white explosion burst
        loc.getWorld().spawnParticle(Particle.FLASH,          loc, 1,  0,   0,   0,   0);
        loc.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, loc, 3,  0.3, 0.3, 0.3, 0);
        loc.getWorld().spawnParticle(Particle.WHITE_ASH,       loc, 80, 0.5, 1.0, 0.5, 0.2);
        loc.getWorld().spawnParticle(Particle.REDSTONE,        loc, 60, 0.6, 1.0, 0.6, whiteDust);
        loc.getWorld().spawnParticle(Particle.SPELL_WITCH,     loc, 40, 0.4, 0.8, 0.4, 0.5);

        // thunder sounds (natural, no extras)
        loc.getWorld().playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 0.6f);
        loc.getWorld().playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT,  1.0f, 1.2f);

        // rising white spark ring
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
                loc.getWorld().spawnParticle(Particle.REDSTONE,   ring, 5, 0.1, 0.1, 0.1, whiteDust);
                loc.getWorld().spawnParticle(Particle.CRIT_MAGIC, ring, 4, 0.2, 0.2, 0.2, 0.1);
                tick++;
            }
        }.runTaskTimer(this, 0L, 2L);

        // title shown only to the killer:
        //   line 1 — killer's own name, bright white bold
        //   line 2 — sword name, white (not grey)
        killer.sendTitle(
            ChatColor.WHITE + "" + ChatColor.BOLD + killer.getName(),
            ChatColor.WHITE + "⚡ Wrath of Thunder ⚡",
            5, 50, 15
        );
    }

    // ═══════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════

    /** Small white burst spawned on every 4th hit (not just kills). */
    private void spawnHitEffect(Location loc) {
        Particle.DustOptions whiteDust = new Particle.DustOptions(Color.WHITE, 1.5f);
        loc.getWorld().spawnParticle(Particle.REDSTONE,    loc, 30, 0.3, 0.6, 0.3, whiteDust);
        loc.getWorld().spawnParticle(Particle.SPELL_WITCH, loc, 20, 0.3, 0.5, 0.3, 0.3);
        loc.getWorld().spawnParticle(Particle.CRIT,        loc, 15, 0.3, 0.5, 0.3, 0.2);
    }

    /** Returns true only if the item is a Diamond Sword named "Wrath of Thunder". */
    private boolean isWrathOfThunder(ItemStack item) {
        if (item == null || item.getType() != Material.DIAMOND_SWORD) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return false;
        return ChatColor.stripColor(meta.getDisplayName()).equals("Wrath of Thunder");
    }

    /** Factory method — use this to give the sword via any command plugin. */
    public static ItemStack createWrathOfThunder() {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = sword.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.WHITE + "" + ChatColor.BOLD + "Wrath of Thunder");
            meta.setLore(Arrays.asList(
                ChatColor.DARK_GRAY + "⚔ "  + ChatColor.GRAY + "Legendary Sword",
                ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━━",
                ChatColor.GRAY + "Passive: " + ChatColor.WHITE + "" + ChatColor.BOLD + "Thunder",
                ChatColor.GRAY + "Every " + ChatColor.YELLOW + "4th hit" + ChatColor.GRAY + " calls lightning",
                ChatColor.GRAY + "upon your enemies.",
                ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━━",
                ChatColor.DARK_PURPLE + "✦ " + ChatColor.LIGHT_PURPLE + "The sky bows to its wielder."
            ));
            meta.addEnchant(Enchantment.DURABILITY, 10, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            sword.setItemMeta(meta);
        }
        return sword;
    }
}
