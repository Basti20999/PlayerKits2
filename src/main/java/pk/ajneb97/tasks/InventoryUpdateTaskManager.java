package pk.ajneb97.tasks;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import pk.ajneb97.PlayerKits2;
import pk.ajneb97.managers.*;
import pk.ajneb97.model.inventory.InventoryPlayer;
import pk.ajneb97.utils.FoliaScheduler;
import pk.ajneb97.utils.InventoryUtils;
import pk.ajneb97.utils.ItemUtils;

import java.util.List;

public class InventoryUpdateTaskManager {

    private PlayerKits2 plugin;
    public InventoryUpdateTaskManager(PlayerKits2 plugin){
        this.plugin = plugin;
    }

    public void start(){
        FoliaScheduler.runTimer(plugin, this::tick, 1L, 20L);
    }

    private void tick(){
        InventoryManager inventoryManager = plugin.getInventoryManager();
        List<InventoryPlayer> players = inventoryManager.getPlayers();
        for(InventoryPlayer inventoryPlayer : players){
            Player player = inventoryPlayer.getPlayer();
            if(player == null || !player.isOnline()){
                continue;
            }
            FoliaScheduler.runForEntity(plugin, player, () -> updateFor(inventoryPlayer), null);
        }
    }

    private void updateFor(InventoryPlayer inventoryPlayer){
        Player player = inventoryPlayer.getPlayer();
        if(player == null || !player.isOnline()){
            return;
        }
        InventoryManager inventoryManager = plugin.getInventoryManager();
        KitsManager kitsManager = plugin.getKitsManager();
        PlayerDataManager playerDataManager = plugin.getPlayerDataManager();
        KitItemManager kitItemManager = plugin.getKitItemManager();

        Inventory inv = InventoryUtils.getTopInventory(player);
        if(inv == null){
            return;
        }
        ItemStack[] contents = inv.getContents();
        for(int i=0;i<contents.length;i++){
            ItemStack item = contents[i];
            if(item == null || item.getType().equals(Material.AIR)){
                continue;
            }

            String kitName = ItemUtils.getTagStringItem(plugin,item,"playerkits_kit");
            if(kitName != null){
                inventoryManager.setKit(kitName,player,inv,i,kitsManager,
                        playerDataManager,kitItemManager,item);
            }
        }
    }
}
