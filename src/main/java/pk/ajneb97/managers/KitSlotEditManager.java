package pk.ajneb97.managers;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import pk.ajneb97.PlayerKits2;
import pk.ajneb97.model.Kit;
import pk.ajneb97.model.PlayerData;
import pk.ajneb97.model.inventory.InventoryPlayer;
import pk.ajneb97.model.item.KitItem;
import pk.ajneb97.utils.InventoryItem;
import pk.ajneb97.utils.InventoryUtils;
import pk.ajneb97.utils.ItemUtils;
import pk.ajneb97.utils.OtherUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class KitSlotEditManager {

    private static final int SLOT_CANCEL = 45;
    private static final int SLOT_INFO = 47;
    private static final int SLOT_RESET = 49;
    private static final int SLOT_SAVE = 53;
    private static final String TAG_IDX = "playerkits_slot_edit_idx";
    // Items go in slots 0-44; bottom row is 45-53
    private static final int MAX_ITEM_SLOT = 44;

    private final PlayerKits2 plugin;
    private final List<InventoryPlayer> players;

    public KitSlotEditManager(PlayerKits2 plugin) {
        this.plugin = plugin;
        this.players = new CopyOnWriteArrayList<>();
    }

    public InventoryPlayer getInventoryPlayer(Player player) {
        for (InventoryPlayer ip : players) {
            if (ip.getPlayer().equals(player)) return ip;
        }
        return null;
    }

    public List<InventoryPlayer> getPlayers() {
        return players;
    }

    public void removeInventoryPlayer(Player player) {
        players.removeIf(p -> p.getPlayer().equals(player));
    }

    public void openInventory(InventoryPlayer inventoryPlayer) {
        inventoryPlayer.setInventoryName("slot_edit");
        String kitName = inventoryPlayer.getKitName();
        Kit kit = plugin.getKitsManager().getKitByName(kitName);

        Inventory inv = Bukkit.createInventory(null, 54,
                MessagesManager.getLegacyColoredMessage("&6Edit Kit Slots: &e" + kitName));

        // Bottom row decorations
        for (int i = 46; i <= 52; i++) {
            if (i == SLOT_INFO || i == SLOT_RESET) continue;
            if (OtherUtils.isLegacy()) {
                new InventoryItem(inv, i, Material.valueOf("STAINED_GLASS_PANE")).dataValue((short) 15).name(" ").ready();
            } else {
                new InventoryItem(inv, i, Material.BLACK_STAINED_GLASS_PANE).name(" ").ready();
            }
        }

        new InventoryItem(inv, SLOT_CANCEL, Material.ARROW).name("&eCancel").ready();

        List<String> lore = new ArrayList<>();
        lore.add("&7Drag items to your preferred inventory slots.");
        lore.add("&7Slot 1-9 (row 1) = Hotbar, rows 2-4 = Inventory.");
        lore.add("&7You cannot add or remove kit items.");
        new InventoryItem(inv, SLOT_INFO, Material.COMPASS).name("&6&lInfo").lore(lore).ready();

        lore = new ArrayList<>();
        lore.add("&7Reset to the default slot arrangement.");
        new InventoryItem(inv, SLOT_RESET, Material.BARRIER).name("&c&lReset to Default").lore(lore).ready();

        lore = new ArrayList<>();
        lore.add("&7Saves your custom slot arrangement.");
        lore.add("&7Applied every time you claim this kit.");
        new InventoryItem(inv, SLOT_SAVE, Material.EMERALD_BLOCK).name("&6&lSave Slots").lore(lore).ready();

        // Load player's saved slot arrangement
        PlayerData playerData = plugin.getPlayerDataManager().getPlayer(inventoryPlayer.getPlayer(), false);
        List<Integer> customSlots = null;
        if (playerData != null) {
            customSlots = playerData.getKitCustomSlots(kitName);
            ArrayList<KitItem> kitItems = kit.getItems();
            if (customSlots != null && customSlots.size() != kitItems.size()) {
                customSlots = null; // stale data
            }
        }

        // Place kit items into the GUI
        KitItemManager kitItemManager = plugin.getKitItemManager();
        ArrayList<KitItem> items = kit.getItems();
        boolean[] usedSlots = new boolean[MAX_ITEM_SLOT + 1];

        for (int i = 0; i < items.size(); i++) {
            KitItem kitItem = items.get(i);
            ItemStack item = kitItemManager.createItemFromKitItem(kitItem, null, kit);
            item = ItemUtils.setTagStringItem(plugin, item, TAG_IDX, String.valueOf(i));

            int slot = -1;
            if (customSlots != null) {
                int saved = customSlots.get(i);
                if (saved >= 0 && saved <= MAX_ITEM_SLOT && !usedSlots[saved]) {
                    slot = saved;
                }
            }
            if (slot == -1) {
                // Auto-assign first free slot
                for (int s = 0; s <= MAX_ITEM_SLOT; s++) {
                    if (!usedSlots[s] && inv.getItem(s) == null) {
                        slot = s;
                        break;
                    }
                }
            }
            if (slot == -1) break; // No space left (more than 45 items)

            usedSlots[slot] = true;
            inv.setItem(slot, item);
        }

        inventoryPlayer.getPlayer().openInventory(inv);
        players.add(inventoryPlayer);
    }

    public void saveSlots(InventoryPlayer inventoryPlayer) {
        Player player = inventoryPlayer.getPlayer();
        Inventory inv = InventoryUtils.getTopInventory(player);
        if (inv == null) return;

        String kitName = inventoryPlayer.getKitName();
        Kit kit = plugin.getKitsManager().getKitByName(kitName);
        ArrayList<KitItem> kitItems = kit.getItems();

        List<Integer> customSlots = new ArrayList<>();
        for (int i = 0; i < kitItems.size(); i++) {
            customSlots.add(-1);
        }

        ItemStack[] contents = inv.getContents();
        for (int slot = 0; slot <= MAX_ITEM_SLOT; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType().equals(Material.AIR)) continue;
            String idxStr = ItemUtils.getTagStringItem(plugin, item, TAG_IDX);
            if (idxStr != null) {
                try {
                    int idx = Integer.parseInt(idxStr);
                    if (idx >= 0 && idx < customSlots.size()) {
                        customSlots.set(idx, slot);
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        PlayerData playerData = plugin.getPlayerDataManager().getPlayer(player, true);
        playerData.setKitCustomSlots(kitName, customSlots);

        if (plugin.getMySQLConnection() != null) {
            plugin.getMySQLConnection().updateKitSlots(playerData, playerData.getKit(kitName));
        }

        player.sendMessage(MessagesManager.getLegacyColoredMessage(
                PlayerKits2.prefix + plugin.getConfigsManager().getMessagesConfigManager()
                        .getConfig().getString("kitSlotsaved").replace("%kit%", kitName)));
    }

    public void resetSlots(InventoryPlayer inventoryPlayer) {
        Player player = inventoryPlayer.getPlayer();
        String kitName = inventoryPlayer.getKitName();

        PlayerData playerData = plugin.getPlayerDataManager().getPlayer(player, false);
        if (playerData != null) {
            playerData.setKitCustomSlots(kitName, null);
            if (plugin.getMySQLConnection() != null) {
                plugin.getMySQLConnection().updateKitSlots(playerData, playerData.getKit(kitName));
            }
        }

        player.sendMessage(MessagesManager.getLegacyColoredMessage(
                PlayerKits2.prefix + plugin.getConfigsManager().getMessagesConfigManager()
                        .getConfig().getString("kitSlotReset").replace("%kit%", kitName)));

        removeInventoryPlayer(player);
        openInventory(inventoryPlayer);
    }

    public void clickInventory(InventoryPlayer inventoryPlayer, ItemStack item, int slot,
                               ClickType clickType, InventoryClickEvent event) {
        Player player = inventoryPlayer.getPlayer();
        Inventory topInv = InventoryUtils.getTopInventory(player);

        if (event.getClickedInventory() == null) {
            event.setCancelled(true);
            return;
        }

        if (event.getClickedInventory().equals(topInv)) {
            if (slot > MAX_ITEM_SLOT) {
                // Bottom row – only buttons, cancel all movement
                event.setCancelled(true);
                if (slot == SLOT_CANCEL) {
                    removeInventoryPlayer(player);
                    player.closeInventory();
                } else if (slot == SLOT_RESET) {
                    resetSlots(inventoryPlayer);
                } else if (slot == SLOT_SAVE) {
                    saveSlots(inventoryPlayer);
                }
            } else if (clickType.isShiftClick()) {
                // Shift-click would move item to player's inventory – disallow
                event.setCancelled(true);
            }
            // Normal clicks in slots 0-44: allow free rearrangement (don't cancel)
        } else {
            // Player's own inventory – prevent adding items to / taking items from GUI
            event.setCancelled(true);
        }
    }

    public void handleDrag(InventoryDragEvent event) {
        // Cancel if any slot touches the bottom row or the player's inventory
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot > MAX_ITEM_SLOT) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
