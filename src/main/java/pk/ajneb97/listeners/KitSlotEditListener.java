package pk.ajneb97.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import pk.ajneb97.PlayerKits2;
import pk.ajneb97.managers.KitSlotEditManager;
import pk.ajneb97.model.inventory.InventoryPlayer;

public class KitSlotEditListener implements Listener {

    private final PlayerKits2 plugin;

    public KitSlotEditListener(PlayerKits2 plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        KitSlotEditManager manager = plugin.getKitSlotEditManager();
        InventoryPlayer inventoryPlayer = manager.getInventoryPlayer(player);
        if (inventoryPlayer != null) {
            manager.removeInventoryPlayer(player);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        KitSlotEditManager manager = plugin.getKitSlotEditManager();
        InventoryPlayer inventoryPlayer = manager.getInventoryPlayer(player);
        if (inventoryPlayer == null) return;

        ClickType clickType = event.getClick();
        manager.clickInventory(inventoryPlayer, event.getCurrentItem(), event.getSlot(), clickType, event);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Player player = (Player) event.getWhoClicked();
        KitSlotEditManager manager = plugin.getKitSlotEditManager();
        InventoryPlayer inventoryPlayer = manager.getInventoryPlayer(player);
        if (inventoryPlayer == null) return;

        manager.handleDrag(event);
    }
}
