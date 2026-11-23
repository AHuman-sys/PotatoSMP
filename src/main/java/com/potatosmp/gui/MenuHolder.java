package com.potatosmp.gui;

import com.potatosmp.PotatoSMPPlugin;
import com.potatosmp.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public abstract class MenuHolder implements InventoryHolder {

    protected final PotatoSMPPlugin plugin;
    protected final Player viewer;
    protected final Inventory inventory;

    protected MenuHolder(PotatoSMPPlugin plugin, Player viewer, int size, String titleMm) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(this, size, TextUtil.mm(titleMm));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public abstract void render();

    public abstract void handleClick(InventoryClickEvent event);

    public void handleClose(InventoryCloseEvent event) {}

    public void open() {
        render();
        viewer.openInventory(inventory);
    }

    protected void fillBackground() {
        ItemStack filler = createIcon(plugin.getConfigManager().getFillerMaterial(), " ", List.of());
        ItemStack accent = createIcon(plugin.getConfigManager().getAccentMaterial(), " ", List.of());
        int size = inventory.getSize();
        for (int i = 0; i < size; i++) {
            int row = i / 9;
            int col = i % 9;
            boolean isCorner = (row == 0 || row == (size / 9) - 1) && (col == 0 || col == 8);
            inventory.setItem(i, isCorner ? accent : filler);
        }
    }

    protected ItemStack createIcon(Material material, String nameMm, List<String> loreMm) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.mm(nameMm));
            if (loreMm != null && !loreMm.isEmpty()) {
                meta.lore(TextUtil.mmList(loreMm));
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_DYE, ItemFlag.HIDE_ARMOR_TRIM);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
