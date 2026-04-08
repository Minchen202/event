package org.event.event;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public final class Event extends JavaPlugin implements org.bukkit.event.Listener {
    private final UUID authorizedUser = UUID.fromString("fdafb296-2e7b-49fd-b1b7-8c53578c77cf");
    private final UUID authorizedUser2 = UUID.fromString("d1982e1a-9c2f-48b0-b232-072b2f86b2ec");
    private String currentEventName = null;
    private Location eventLocation = null;
    public static final AtomicInteger execDepth = new AtomicInteger(0);
    private final Map<UUID, Location> playerReturnLocations = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("event").setExecutor(this::onCommand);
        getCommand("event").setTabCompleter(this::onTabComplete);
    }

    @Override
    public void onDisable() {
        endEvent();
    }

    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("create", "join", "end").stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }


        Player player = (Player) sender;

        if (args.length >= 1) {
            String sub = args[0].toLowerCase();
            switch (sub) {
                case "create":
                    handleCreate(player, args);
                    break;
                case "join":
                    handleJoin(player);
                    break;
                case "end":
                    handleEnd(player);
                    break;
                case "leave":
                    handleLeave(player);
                    break;
                default:
                    player.sendMessage("§cUsage: /event <create|join|end>");
                    break;
            }
        } else {
            player.sendMessage("§cUsage: /event <create|join|end>");
        }
        return true;
    }

    private void handleCreate(Player player, String[] args) {
        if (!player.isOp()) {
            player.sendMessage("You do not have permission to use this command.");
            return;
        }
        if (currentEventName != null) {
            player.sendMessage("§cAn event is already running: " + currentEventName);
            return;
        }

        currentEventName = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "Unnamed Event";
        eventLocation = player.getLocation();

        Bukkit.broadcastMessage("§6§l[Event] §fA new event has started: §b" + currentEventName);
        Bukkit.broadcastMessage("§6§l[Event] §fType §a/event join §fto participate!");
    }

    private void handleJoin(Player player) {
        if (currentEventName == null) {
            player.sendMessage("§cThere is no active event to join.");
            return;
        }

        if (playerReturnLocations.containsKey(player.getUniqueId())) {
            player.sendMessage("§cYou have already joined the event!");
            return;
        }

        playerReturnLocations.put(player.getUniqueId(), player.getLocation());
        player.teleport(eventLocation);
        player.sendMessage("§aYou have joined the event: " + currentEventName);
        player.sendMessage("§7You can leave with /event leave.");
    }

    private void handleEnd(Player player) {
        if (currentEventName == null) {
            player.sendMessage("§cThere is no active event to end.");
            return;
        }

        if (!player.hasPermission("event.admin")) {
        }

        endEvent();
    }

    private void endEvent() {
        if (currentEventName == null) return;

        Bukkit.broadcastMessage("§6§l[Event] §fThe event §b" + currentEventName + " §fhas ended!");
        
        for (Map.Entry<UUID, Location> entry : playerReturnLocations.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null && p.isOnline()) {
                p.teleport(entry.getValue());
                p.sendMessage("§aThe event has ended. You've been teleported back to your original position.");
            }
        }

        currentEventName = null;
        eventLocation = null;
        playerReturnLocations.clear();
    }
    private void handleLeave(Player player) {
        if (currentEventName == null) return;

        if (!playerReturnLocations.containsKey(player.getUniqueId())) {
            player.sendMessage("§cYou are not currently in the event.");
            return;
        }

        player.teleport(playerReturnLocations.get(player.getUniqueId()));
        playerReturnLocations.remove(player.getUniqueId());
        player.sendMessage("§aYou have left the event: " + currentEventName);

        if (playerReturnLocations.isEmpty()) {
            endEvent();
        }
    }
}
