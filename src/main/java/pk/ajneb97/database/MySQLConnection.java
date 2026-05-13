package pk.ajneb97.database;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import pk.ajneb97.PlayerKits2;
import pk.ajneb97.managers.MessagesManager;
import pk.ajneb97.model.PlayerData;
import pk.ajneb97.model.PlayerDataKit;
import pk.ajneb97.model.internal.GenericCallback;
import pk.ajneb97.model.internal.SimpleCallback;
import pk.ajneb97.utils.FoliaScheduler;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class MySQLConnection {

    private PlayerKits2 plugin;
    private HikariConnection connection;

    public MySQLConnection(PlayerKits2 plugin){
        this.plugin = plugin;
    }

    public void setupMySql(){
        FileConfiguration config = plugin.getConfigsManager().getMainConfigManager().getConfig();
        try {
            connection = new HikariConnection(config);
            connection.getHikari().getConnection();
            createTables();
            Bukkit.getConsoleSender().sendMessage(MessagesManager.getLegacyColoredMessage(plugin.prefix+" &aSuccessfully connected to the Database."));
        }catch(Exception e) {
            Bukkit.getConsoleSender().sendMessage(MessagesManager.getLegacyColoredMessage(plugin.prefix+" &cError while connecting to the Database."));
        }
    }

    public Connection getConnection() {
        try {
            return connection.getHikari().getConnection();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void createTables() {
        try(Connection connection = getConnection()){
            PreparedStatement statement1 = connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS playerkits_players" +
                    " (UUID varchar(200) NOT NULL, " +
                    " PLAYER_NAME varchar(50), " +
                    " PRIMARY KEY ( UUID ))"
            );
            statement1.executeUpdate();
            PreparedStatement statement2 = connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS playerkits_players_kits" +
                    " (ID int NOT NULL AUTO_INCREMENT, " +
                    " UUID varchar(200) NOT NULL, " +
                    " NAME varchar(100), " +
                    " COOLDOWN BIGINT, " +
                    " ONE_TIME BOOLEAN, " +
                    " BOUGHT BOOLEAN, " +
                    " SLOT_ORDER TEXT, " +
                    " PRIMARY KEY ( ID ), " +
                    " FOREIGN KEY (UUID) REFERENCES playerkits_players(UUID))");
            statement2.executeUpdate();
            try {
                PreparedStatement alterStatement = connection.prepareStatement(
                        "ALTER TABLE playerkits_players_kits ADD COLUMN SLOT_ORDER TEXT");
                alterStatement.executeUpdate();
            } catch(SQLException ignored) {
                // Column already exists, ignore
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void getPlayer(String uuid, GenericCallback<PlayerData> callback){
        FoliaScheduler.runAsync(plugin, () -> {
            PlayerData player = null;
            try(Connection connection = getConnection()){
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT playerkits_players.UUID, playerkits_players.PLAYER_NAME, " +
                                "playerkits_players_kits.NAME, " +
                                "playerkits_players_kits.COOLDOWN, " +
                                "playerkits_players_kits.ONE_TIME, " +
                                "playerkits_players_kits.BOUGHT, " +
                                "playerkits_players_kits.SLOT_ORDER " +
                                "FROM playerkits_players LEFT JOIN playerkits_players_kits " +
                                "ON playerkits_players.UUID = playerkits_players_kits.UUID " +
                                "WHERE playerkits_players.UUID = ?");

                statement.setString(1, uuid);
                ResultSet result = statement.executeQuery();

                while(result.next()){
                    UUID playerUuid = UUID.fromString(result.getString("UUID"));
                    String playerName = result.getString("PLAYER_NAME");
                    String kitName = result.getString("NAME");
                    long cooldown = result.getLong("COOLDOWN");
                    boolean oneTime = result.getBoolean("ONE_TIME");
                    boolean bought = result.getBoolean("BOUGHT");
                    if(player == null){
                        player = new PlayerData(playerUuid,playerName);
                    }
                    if(kitName != null){
                        String slotOrderStr = null;
                        try { slotOrderStr = result.getString("SLOT_ORDER"); } catch(SQLException ignored) {}
                        List<Integer> customSlots = null;
                        if(slotOrderStr != null && !slotOrderStr.isEmpty()){
                            customSlots = new ArrayList<>();
                            for(String s : slotOrderStr.split(",")){
                                try { customSlots.add(Integer.parseInt(s.trim())); } catch(NumberFormatException ignored) {}
                            }
                        }
                        PlayerDataKit playerDataKit = new PlayerDataKit(kitName);
                        playerDataKit.setCooldown(cooldown);
                        playerDataKit.setOneTime(oneTime);
                        playerDataKit.setBought(bought);
                        playerDataKit.setCustomSlots(customSlots);
                        player.addKit(playerDataKit);
                    }
                }

                PlayerData finalPlayer = player;
                FoliaScheduler.run(plugin, () -> callback.onDone(finalPlayer));
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    public void createPlayer(PlayerData player, SimpleCallback callback){
        FoliaScheduler.runAsync(plugin, () -> {
            try(Connection connection = getConnection()){
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO playerkits_players " +
                                "(UUID, PLAYER_NAME) VALUE (?,?)");

                statement.setString(1, player.getUuid().toString());
                statement.setString(2, player.getName());
                statement.executeUpdate();

                FoliaScheduler.run(plugin, callback::onDone);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    public void updatePlayerName(PlayerData player){
        FoliaScheduler.runAsync(plugin, () -> {
            try(Connection connection = getConnection()){
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE playerkits_players SET " +
                                "PLAYER_NAME=? WHERE UUID=?");

                statement.setString(1, player.getName());
                statement.setString(2, player.getUuid().toString());
                statement.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    public void updateKit(PlayerData player,PlayerDataKit kit,boolean mustCreate){
        FoliaScheduler.runAsync(plugin, () -> {
            try(Connection connection = getConnection()){
                PreparedStatement statement;
                String slotOrder = serializeSlots(kit.getCustomSlots());
                if(mustCreate){
                    statement = connection.prepareStatement(
                            "INSERT INTO playerkits_players_kits " +
                                    "(UUID, NAME, COOLDOWN, ONE_TIME, BOUGHT, SLOT_ORDER) VALUE (?,?,?,?,?,?)");

                    statement.setString(1, player.getUuid().toString());
                    statement.setString(2, kit.getName());
                    statement.setLong(3, kit.getCooldown());
                    statement.setBoolean(4, kit.isOneTime());
                    statement.setBoolean(5, kit.isBought());
                    statement.setString(6, slotOrder);
                }else{
                    statement = connection.prepareStatement(
                            "UPDATE playerkits_players_kits SET " +
                                    "COOLDOWN=?, ONE_TIME=?, BOUGHT=?, SLOT_ORDER=? WHERE UUID=? AND NAME=?");

                    statement.setLong(1, kit.getCooldown());
                    statement.setBoolean(2, kit.isOneTime());
                    statement.setBoolean(3, kit.isBought());
                    statement.setString(4, slotOrder);
                    statement.setString(5, player.getUuid().toString());
                    statement.setString(6, kit.getName());
                }
                statement.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    public void updateKitSlots(PlayerData player, PlayerDataKit kit){
        if(kit == null) return;
        FoliaScheduler.runAsync(plugin, () -> {
            try(Connection connection = getConnection()){
                String slotOrder = serializeSlots(kit.getCustomSlots());
                PreparedStatement check = connection.prepareStatement(
                        "SELECT ID FROM playerkits_players_kits WHERE UUID=? AND NAME=?");
                check.setString(1, player.getUuid().toString());
                check.setString(2, kit.getName());
                ResultSet rs = check.executeQuery();
                if(rs.next()){
                    PreparedStatement statement = connection.prepareStatement(
                            "UPDATE playerkits_players_kits SET SLOT_ORDER=? WHERE UUID=? AND NAME=?");
                    statement.setString(1, slotOrder);
                    statement.setString(2, player.getUuid().toString());
                    statement.setString(3, kit.getName());
                    statement.executeUpdate();
                } else {
                    PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO playerkits_players_kits " +
                                    "(UUID, NAME, COOLDOWN, ONE_TIME, BOUGHT, SLOT_ORDER) VALUE (?,?,?,?,?,?)");
                    statement.setString(1, player.getUuid().toString());
                    statement.setString(2, kit.getName());
                    statement.setLong(3, kit.getCooldown());
                    statement.setBoolean(4, kit.isOneTime());
                    statement.setBoolean(5, kit.isBought());
                    statement.setString(6, slotOrder);
                    statement.executeUpdate();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    private String serializeSlots(List<Integer> slots){
        if(slots == null || slots.isEmpty()) return null;
        return slots.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    public void resetKit(String uuid,String kitName,boolean all){
        FoliaScheduler.runAsync(plugin, () -> {
            try(Connection connection = getConnection()){
                PreparedStatement statement;
                if(all){
                    statement = connection.prepareStatement(
                            "DELETE FROM playerkits_players_kits " +
                                    "WHERE NAME=?");
                    statement.setString(1, kitName);
                }else{
                    statement = connection.prepareStatement(
                            "DELETE FROM playerkits_players_kits " +
                                    "WHERE UUID=? AND NAME=?");

                    statement.setString(1, uuid);
                    statement.setString(2, kitName);
                }
                statement.executeUpdate();

            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }
}
