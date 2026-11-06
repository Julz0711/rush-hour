package io.github.julz0711.rushhour;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Set;

/** A bound stack that displays the round's authoritative skip balance. */
final class SkipTokens {
    private static final Set<ClickType> REARRANGE = Set.of(ClickType.LEFT, ClickType.NUMBER_KEY, ClickType.SWAP_OFFHAND);
    private final NamespacedKey marker;

    SkipTokens(RushHourPlugin plugin) {
        marker = new NamespacedKey(plugin, "skip_token");
    }

    boolean isToken(ItemStack item) {
        return item != null && item.getType() == Material.BARRIER && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(marker, PersistentDataType.BYTE);
    }

    void sync(Player player, int count, boolean bingo, boolean shared) {
        var inventory = player.getInventory();
        ItemStack cursor = player.getItemOnCursor();
        boolean found = isToken(cursor);
        if (found && count == 0) player.setItemOnCursor(null);
        if (found && count > 0 && cursor.getAmount() != count) player.setItemOnCursor(cursor.asQuantity(count));

        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (!isToken(item)) continue;
            if (found || count == 0) {
                inventory.setItem(slot, null);
            } else if (item.getAmount() != count) {
                inventory.setItem(slot, item.asQuantity(count));
            }
            found = true;
        }
        if (found || count == 0) return;

        ItemStack item = new ItemStack(Material.BARRIER, count);
        item.editMeta(meta -> {
            meta.displayName(Component.text("Skip Token", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text(bingo ? "Right-click to choose a Bingo square." : "Right-click to reroll your target.", NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text(shared ? "This balance is shared with your team." : "Each item is one skip.", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));
            meta.getPersistentDataContainer().set(marker, PersistentDataType.BYTE, (byte) 1);
        });
        ItemStack lastHotbarSlot = inventory.getItem(8);
        if (lastHotbarSlot == null || lastHotbarSlot.isEmpty()) inventory.setItem(8, item);
        else inventory.addItem(item);
    }

    boolean blocksTransfer(Player player, InventoryClickEvent event) {
        int hotbarSlot = event.getHotbarButton();
        boolean touchesToken = isToken(event.getCurrentItem()) || isToken(event.getCursor())
                || hotbarSlot >= 0 && isToken(player.getInventory().getItem(hotbarSlot))
                || event.getClick() == ClickType.SWAP_OFFHAND && isToken(player.getInventory().getItemInOffHand());
        return touchesToken && (event.getClickedInventory() != event.getView().getBottomInventory()
                || !REARRANGE.contains(event.getClick()));
    }
}
