package com.devilswrath.plugin.items;

import com.devilswrath.plugin.DevilsWrathPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class DevilsWrathItem {

    public static final String ITEM_KEY = "devils_wrath";

    private final NamespacedKey itemKey;

    public DevilsWrathItem() {
        this.itemKey = new NamespacedKey(DevilsWrathPlugin.getInstance(), ITEM_KEY);
    }

    public NamespacedKey getItemKey() { return itemKey; }

    // ── Create ────────────────────────────────────────────────────────────────

    public ItemStack create() {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);

        sword.editMeta(ItemMeta.class, meta -> {

            // Display name
            meta.displayName(
                Component.text("Devil's Wrath")
                    .color(NamedTextColor.DARK_RED)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false)
            );

            // Lore
            meta.lore(List.of(
                Component.empty(),
                Component.text("Forged in the depths of the Underworld,")
                    .color(NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("tempered by the screams of the damned.")
                    .color(NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("\uD83D\uDD25 Ability: ")
                    .color(NamedTextColor.RED)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(
                        Component.text("Veil of Wrath")
                            .color(NamedTextColor.DARK_RED)
                            .decoration(TextDecoration.BOLD, true)
                            .decoration(TextDecoration.ITALIC, false)
                    ),
                Component.text("Sneak \u2192 land a critical hit to activate.")
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("Blinds the target for ")
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("4 seconds").color(NamedTextColor.WHITE)
                        .decoration(TextDecoration.ITALIC, false)),
                Component.text("Breaks shields on any sneaking hit.")
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("Cooldown: ")
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(
                        Component.text("48s")
                            .color(NamedTextColor.GOLD)
                            .decoration(TextDecoration.ITALIC, false)
                    ),
                Component.empty(),
                Component.text("Soulbound \u2014 it belongs to one alone.")
                    .color(NamedTextColor.DARK_RED)
                    .decoration(TextDecoration.ITALIC, true),
                Component.empty(),
                Component.text("\"...even shadows fear its edge...\"")
                    .color(NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, true)
            ));

            // Enchantments
            meta.addEnchant(Enchantment.SHARPNESS,     5, true);
            meta.addEnchant(Enchantment.MENDING,        1, true);
            meta.addEnchant(Enchantment.SWEEPING_EDGE,  3, true);
            meta.addEnchant(Enchantment.LOOTING,        3, true);

            // Unbreakable
            meta.setUnbreakable(true);

            // Clean tooltip
            meta.addItemFlags(
                ItemFlag.HIDE_ENCHANTS,
                ItemFlag.HIDE_UNBREAKABLE,
                ItemFlag.HIDE_ATTRIBUTES
            );

            // PDC identity tag
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        });

        return sword;
    }

    // ── Identity check ────────────────────────────────────────────────────────

    public boolean isDevilsWrath(ItemStack item) {
        if (item == null || item.getType() != Material.DIAMOND_SWORD) return false;
        var meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }
}
