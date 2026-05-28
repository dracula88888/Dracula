package ge.thundersword;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
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

public class WrathOfThunder extends JavaPlugin implements Listener, CommandExecutor {

    private final Map<UUID, Integer> hitCounter  = new HashMap<>();
    private final Map<UUID, UUID>    pendingKills = new HashMap<>();

    // ── owner UUID (loaded from config; set on first /wrath use) ──
    private UUID ownerUUID = null;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        String stored = getConfig().getString("owner-uuid", "");
        if (!stored.isEmpty()) {
            try { ownerUUID = UUID.fromString(stored); } catch (Exception ignored) {}
        }
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("wrath").setExecutor(this);
        getLogger().info("Wrath of Thunder enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Wrath of Thunder disabled.");
    }

    // ── helper: is this player the registered owner? ──
    private boolean isOwner(Player player) {
        return ownerUUID != null && player.getUniqueId().equals(ownerUUID);
    }

    // ── /wrath command ──
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        // First ever use: register the caller as the permanent owner
        if (ownerUUID == null) {
            ownerUUID = player.getUniqueId();
            getConfig().set("owner-uuid", ownerUUID.toString());
            saveConfig();
            player.sendMessage(ChatColor.GOLD + "⚡ You are now the eternal owner of the Wrath of Thunder!");
        }

        if (!isOwner(player)) {
            player.sendMessage(ChatColor.RED + "✗ This sword belongs to another. You cannot wield it.");
            return true;
        }

        player.getInventory().addItem(createWrathOfThunder());
        player.sendMessage(ChatColor.WHITE + "" + ChatColor.BOLD + "⚡ You received the Wrath of Thunder!");
        return true;
    }

    // ── hit handler: only owner can deal damage with the sword ──
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!isWrathOfThunder(held)) return;

        // Non-owner cannot use the sword at all
        if (!isOwner(player)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "✗ This sword refuses to obey you.");
            return;
        }

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

    // ── owner immune to their own lightning ──
    @EventHandler(priority = EventPriority.HIGH)
    public void onLightningDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof LightningStrike)) return;
        if (!(event.getEntity() instanceof Player victim)) return;
        if (isOwner(victim) && isWrathOfThunder(victim.getInventory().getItemInMainHand())) {
            event.setCancelled(true);
        }
    }

    // ── prevent non-owner from picking up the sword ──
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isWrathOfThunder(event.getItem().getItemStack())) return;
        if (!isOwner(player)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "✗ The Wrath of Thunder cannot be claimed by you.");
        }
    }

    // ── sword is fireproof / lava-proof when dropped ──
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Item item)) return;
        if (!isWrathOfThunder(item.getItemStack())) return;
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.LAVA
                || cause == EntityDamageEvent.DamageCause.FIRE
                || cause == EntityDamageEvent.DamageCause.FIRE_TICK) {
            event.setCancelled(true);
        }
    }

    // ── block anvil rename / repair by non-owner ──
    @EventHandler
    public void onAnvilPrepare(PrepareAnvilEvent event) {
        ItemStack first  = event.getInventory().getFirstItem();
        ItemStack second = event.getInventory().getSecondItem();
        if (!isWrathOfThunder(first) && !isWrathOfThunder(second)) return;
        // Block ALL anvil operations on this sword
        event.setResult(null);
        // Notify the viewer if they are a player
        if (event.getView().getPlayer() instanceof Player viewer) {
            viewer.sendMessage(ChatColor.RED + "✗ The Wrath of Thunder cannot be altered.");
        }
    }

    // ── block /rename, /name, /i name, or any rename-type command on the sword ──
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!isWrathOfThunder(held)) return;

        String msg = event.getMessage().toLowerCase();
        // Common rename commands from Essentials, CMI, EssentialsX, etc.
        if (msg.startsWith("/rename")
                || msg.startsWith("/name")
                || msg.startsWith("/itemname")
                || msg.startsWith("/i name")
                || msg.startsWith("/edititem")) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "✗ The Wrath of Thunder cannot be renamed.");
        }
    }

    // ── block placing the sword into any inventory slot that could rename it ──
    // (extra safety: stops drag-into-anvil by non-owner entirely)
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getInventory().getType() != InventoryType.ANVIL) return;
        if (isOwner(player)) return; // owner can still interact with their own anvil

        ItemStack cursor  = event.getCursor();
        ItemStack current = event.getCurrentItem();

        if (isWrathOfThunder(cursor) || isWrathOfThunder(current)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "✗ You cannot touch the Wrath of Thunder.");
        }
    }


    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player dead = event.getEntity();

        // Owner keeps the sword — strip it from the drop list
        if (isOwner(dead)) {
            event.getDrops().removeIf(this::isWrathOfThunder);
        }

        // Custom kill message when killed via the sword
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

    // ── on respawn: restore the sword to the owner ──
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!isOwner(player)) return;

        // Give the sword back one tick after respawn (inventory is ready by then)
        Bukkit.getScheduler().runTaskLater(this, () -> {
            // Don't duplicate if somehow already present
            boolean hasSword = Arrays.stream(player.getInventory().getContents())
                    .anyMatch(this::isWrathOfThunder);
            if (!hasSword) {
                player.getInventory().addItem(createWrathOfThunder());
                player.sendMessage(ChatColor.WHITE + "⚡ The Wrath of Thunder has returned to you.");
            }
        }, 1L);
    }

    // ── kill animation ──
    private void playKillAnimation(Location loc, Player killer) {
        Particle.DustOptions whiteDust = new Particle.DustOptions(Color.WHITE, 2.0f);

        loc.getWorld().spawnParticle(Particle.FLASH,     loc, 1,  0,   0,   0,   0);
        loc.getWorld().spawnParticle(Particle.WHITE_ASH, loc, 80, 0.5, 1.0, 0.5, 0.2);
        loc.getWorld().spawnParticle(Particle.DUST,  loc, 60, 0.6, 1.0, 0.6, whiteDust);
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
                loc.getWorld().spawnParticle(Particle.DUST, ring, 5, 0.1, 0.1, 0.1, whiteDust);
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
        loc.getWorld().spawnParticle(Particle.DUST,  loc, 30, 0.3, 0.6, 0.3, whiteDust);
        loc.getWorld().spawnParticle(Particle.CRIT,      loc, 15, 0.3, 0.5, 0.3, 0.2);
    }

    // ── checks ──
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
                ChatColor.GRAY + "Passive: " + ChatColor.WHITE + "" + ChatColor.BOLD + "Unbreakable",
                ChatColor.GRAY + "Passive: " + ChatColor.WHITE + "" + ChatColor.BOLD + "Thunder",
                ChatColor.GRAY + "Every " + ChatColor.YELLOW + "4th hit" + ChatColor.GRAY + " calls lightning",
                ChatColor.GRAY + "upon your enemies.",
                ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━━",
                ChatColor.DARK_PURPLE + "✦ " + ChatColor.LIGHT_PURPLE + "The sky bows to its wielder."
            ));
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
            sword.setItemMeta(meta);
        }
        return sword;
    }
}
