package com.devilswrath.plugin;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

public class DevilsWrathSword {

    public static final String SWORD_NAME = ChatColor.DARK_RED + "" + ChatColor.BOLD + "Devils Wrath";

    public static ItemStack create() {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = sword.getItemMeta();

        meta.setDisplayName(SWORD_NAME);
        meta.setUnbreakable(true);
        meta.setLore(Arrays.asList(
                ChatColor.DARK_PURPLE + "Passive: " + ChatColor.GRAY + "Looting IV",
                ChatColor.RED + "Ability: " + ChatColor.GRAY + "Sneak to awaken demonic power.",
                ChatColor.DARK_RED + "Cooldown: " + ChatColor.GRAY + "8 seconds.",
                ChatColor.DARK_GRAY + "  Blindness 4s | Slowness I 4s"
        ));

        sword.setItemMeta(meta);

        sword.addUnsafeEnchantment(Enchantment.SHARPNESS, 5);
        sword.addUnsafeEnchantment(Enchantment.MENDING, 1);
        sword.addUnsafeEnchantment(Enchantment.SWEEPING_EDGE, 3);
        sword.addUnsafeEnchantment(Enchantment.LOOTING, 4);

        return sword;
    }

    public static boolean isDevilsWrath(ItemStack item) {
        if (item == null) return false;
        if (item.getType() != Material.DIAMOND_SWORD) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return false;
        return meta.getDisplayName().equals(SWORD_NAME);
    }
}
