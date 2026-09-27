package com.ultrahome;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public final class UltraHome extends JavaPlugin implements CommandExecutor, Listener {

    private final Map pendingTeleports = new HashMap();
    private final Map initialLocations = new HashMap();

    private static final String[] HOME_SLOTS = {
        "home_1", "home_2", "home_3",
        "home_4", "home_5", "home_6",
        "home_7", "home_8", "home_9"
    };

    private static final int[] HOME_HOURS = {
        0, 0, 0,
        5, 5, 5,
        10, 10, 10
    };

    private static final int[] GUI_SLOTS = {
        11, 12, 13,
        14, 15, 16,
        19, 20, 21
    };

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getCommand("sethome").setExecutor(this);
        getCommand("home").setExecutor(this);
        getCommand("homes").setExecutor(this);
        getCommand("delhome").setExecutor(this);

        getCommand("home").setTabCompleter(new HomeTabCompleter());
        getCommand("delhome").setTabCompleter(new HomeTabCompleter());

        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("UltraHome enabled successfully!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Players only!");
            return true;
        }

        Player player = (Player) sender;
        UUID uuid = player.getUniqueId();
        String cmd = command.getName().toLowerCase();

        if (cmd.equals("sethome")) {
            String homeName = args.length > 0 ? args[0].toLowerCase() : getFirstAvailableUnlockedSlot(player);
            if (homeName == null) {
                homeName = "home_1";
            }
            setPlayerHome(player, homeName);
            return true;
        }

        if (cmd.equals("homes") || cmd.equals("home")) {
            if (args.length == 0) {
                openHomesGUI(player);
                return true;
            }
            String homeName = args[0].toLowerCase();
            teleportToHome(player, homeName);
            return true;
        }

        if (cmd.equals("delhome")) {
            if (args.length == 0) {
                player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "HOMES " + ChatColor.DARK_GRAY + "» " + ChatColor.RED + "Usage: /delhome ");
                return true;
            }
            String homeName = args[0].toLowerCase();
            String path = "homes." + uuid.toString() + "." + homeName;

            if (!getConfig().contains(path)) {
                player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "HOMES " + ChatColor.DARK_GRAY + "» " + ChatColor.RED + "Home '" + homeName + "' does not exist!");
                return true;
            }

            getConfig().set(path, null);
            saveConfig();
            player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "HOMES " + ChatColor.DARK_GRAY + "» " + ChatColor.GREEN + "Deleted home " + ChatColor.YELLOW + homeName + "!");
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_BREAK, 1.0f, 1.0f);
            return true;
        }

        return false;
    }

    private String getFirstAvailableUnlockedSlot(Player player) {
        double playerHours = player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20.0 / 3600.0;
        for (int i = 0; i < HOME_SLOTS.length; i++) {
            if (playerHours >= HOME_HOURS[i]) {
                String path = "homes." + player.getUniqueId().toString() + "." + HOME_SLOTS[i];
                if (!getConfig().contains(path)) {
                    return HOME_SLOTS[i];
                }
            }
        }
        return HOME_SLOTS[0];
    }

    private void setPlayerHome(Player player, String homeName) {
        UUID uuid = player.getUniqueId();
        double playerHours = player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20.0 / 3600.0;

        int slotIndex = -1;
        for (int i = 0; i < HOME_SLOTS.length; i++) {
            if (HOME_SLOTS[i].equalsIgnoreCase(homeName) || ("home_" + (i + 1)).equalsIgnoreCase(homeName)) {
                slotIndex = i;
                homeName = HOME_SLOTS[i];
                break;
            }
        }

        if (slotIndex == -1) {
            slotIndex = 0;
            homeName = HOME_SLOTS[0];
        }

        if (playerHours < HOME_HOURS[slotIndex]) {
            player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "HOMES " + ChatColor.DARK_GRAY + "» " + ChatColor.RED + "You need " + HOME_HOURS[slotIndex] + " hours of playtime to unlock " + homeName + "!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        Location loc = player.getLocation();
        String path = "homes." + uuid.toString() + "." + homeName;

        getConfig().set(path + ".world", loc.getWorld().getName());
        getConfig().set(path + ".x", loc.getX());
        getConfig().set(path + ".y", loc.getY());
        getConfig().set(path + ".z", loc.getZ());
        getConfig().set(path + ".yaw", loc.getYaw());
        getConfig().set(path + ".pitch", loc.getPitch());
        saveConfig();

        player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "HOMES " + ChatColor.DARK_GRAY + "» " + ChatColor.GREEN + "Successfully set home " + ChatColor.YELLOW + homeName + ChatColor.GREEN + "!");
        player.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
    }

    private void openHomesGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, "Homes");
        double playerHours = player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20.0 / 3600.0;
        ConfigurationSection section = getConfig().getConfigurationSection("homes." + player.getUniqueId().toString());

        ItemStack infoItem = new ItemStack(Material.CLOCK);
        ItemMeta infoMeta = infoItem.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName(ChatColor.WHITE + "Playtime: " + String.format("%.1f", playerHours) + " hrs");
            List infoLore = new ArrayList();
            infoLore.add(ChatColor.GRAY + "Unlock more homes as");
            infoLore.add(ChatColor.GRAY + "you play on the server!");
            infoMeta.setLore(infoLore);
            infoItem.setItemMeta(infoMeta);
        }
        inv.setItem(10, infoItem);

        ItemStack showMoreItem = new ItemStack(Material.PAPER);
        ItemMeta showMoreMeta = showMoreItem.getItemMeta();
        if (showMoreMeta != null) {
            showMoreMeta.setDisplayName(ChatColor.WHITE + "Show More");
            showMoreItem.setItemMeta(showMoreMeta);
        }
        inv.setItem(22, showMoreItem);

        for (int i = 0; i < HOME_SLOTS.length; i++) {
            String homeName = HOME_SLOTS[i];
            int requiredHours = HOME_HOURS[i];
            boolean unlocked = playerHours >= requiredHours;
            boolean hasHome = section != null && section.contains(homeName);

            ItemStack item;
            ItemMeta meta;

            if (!unlocked) {
                item = new ItemStack(Material.BARRIER);
                meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ChatColor.RED + "Locked");
                    List lore = new ArrayList();
                    lore.add(ChatColor.GRAY + "Requires " + requiredHours + " hrs playtime");
                    meta.setLore(lore);
                    item.setItemMeta(meta);
                }
            } else if (!hasHome) {
                item = new ItemStack(Material.WHITE_BED);
                meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ChatColor.GRAY + "New Home");
                    List lore = new ArrayList();
                    lore.add(ChatColor.DARK_GRAY + "Click to set " + homeName);
                    meta.setLore(lore);
                    item.setItemMeta(meta);
                }
            } else {
                item = new ItemStack(Material.COMPASS);
                meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ChatColor.GREEN + "Home: " + ChatColor.YELLOW + homeName);
                    List lore = new ArrayList();
                    lore.add(ChatColor.GRAY + "Click to teleport");
                    lore.add(ChatColor.DARK_GRAY + "Right-click to delete");
                    meta.setLore(lore);
                    item.setItemMeta(meta);
                }
            }

            inv.setItem(GUI_SLOTS[i], item);
        }

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (title != null && title.equals("Homes")) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;

            int slot = event.getRawSlot();
            Player player = (Player) event.getWhoClicked();

            int homeIndex = -1;
            for (int i = 0; i < GUI_SLOTS.length; i++) {
                if (GUI_SLOTS[i] == slot) {
                    homeIndex = i;
                    break;
                }
            }

            if (homeIndex == -1) return;

            String homeName = HOME_SLOTS[homeIndex];

            if (clicked.getType() == Material.BARRIER) {
                player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "HOMES " + ChatColor.DARK_GRAY + "» " + ChatColor.RED + "This home slot is locked!");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            } else if (clicked.getType() == Material.WHITE_BED) {
                player.closeInventory();
                setPlayerHome(player, homeName);
                openHomesGUI(player);
            } else if (clicked.getType() == Material.COMPASS) {
                player.closeInventory();
                if (event.isRightClick() || event.isShiftClick()) {
                    String path = "homes." + player.getUniqueId().toString() + "." + homeName;
                    getConfig().set(path, null);
                    saveConfig();
                    player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "HOMES " + ChatColor.DARK_GRAY + "» " + ChatColor.GREEN + "Deleted home " + ChatColor.YELLOW + homeName + "!");
                    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_BREAK, 1.0f, 1.0f);
                } else {
                    teleportToHome(player, homeName);
                }
            }
        }
    }

    private void teleportToHome(Player player, String homeName) {
        UUID uuid = player.getUniqueId();
        
        for (int i = 0; i < HOME_SLOTS.length; i++) {
            if (HOME_SLOTS[i].equalsIgnoreCase(homeName) || ("home_" + (i + 1)).equalsIgnoreCase(homeName)) {
                homeName = HOME_SLOTS[i];
                break;
            }
        }

        String path = "homes." + uuid.toString() + "." + homeName;

        if (!getConfig().contains(path)) {
            player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "HOMES " + ChatColor.DARK_GRAY + "» " + ChatColor.RED + "Home '" + homeName + "' not set yet! Use /sethome " + homeName);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        String worldName = getConfig().getString(path + ".world");
        World world = worldName != null ? Bukkit.getWorld(worldName) : null;
        if (world == null) {
            world = player.getWorld();
        }

        double x = getConfig().getDouble(path + ".x");
        double y = getConfig().getDouble(path + ".y");
        double z = getConfig().getDouble(path + ".z");
        float yaw = (float) getConfig().getDouble(path + ".yaw");
        float pitch = (float) getConfig().getDouble(path + ".pitch");
        Location homeLoc = new Location(world, x, y, z, yaw, pitch);

        player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "HOMES " + ChatColor.DARK_GRAY + "» " + ChatColor.GRAY + "Teleporting to " + ChatColor.YELLOW + homeName + ChatColor.GRAY + " in 5 seconds. Don't move!");
        initialLocations.put(uuid, player.getLocation().clone());

        BukkitTask task = new BukkitRunnable() {
            int countdown = 5;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    pendingTeleports.remove(uuid);
                    initialLocations.remove(uuid);
                    return;
                }

                if (countdown > 0) {
                    player.playSound(player.getLocation(), Sound.BLOCK_LEVER_CLICK, 1.0f, 1.0f);
                    countdown--;
                } else {
                    player.teleport(homeLoc);
                    player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "HOMES " + ChatColor.DARK_GRAY + "» " + ChatColor.GREEN + "Teleported successfully!");
                    player.playSound(homeLoc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
                    pendingTeleports.remove(uuid);
                    initialLocations.remove(uuid);
                    cancel();
                }
            }
        }.runTaskTimer(this, 0L, 20L);

        pendingTeleports.put(uuid, task);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (pendingTeleports.containsKey(uuid)) {
            if (event.hasChangedPosition()) {
                BukkitTask task = (BukkitTask) pendingTeleports.remove(uuid);
                if (task != null) {
                    task.cancel();
                }
                initialLocations.remove(uuid);
                player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "HOMES " + ChatColor.DARK_GRAY + "» " + ChatColor.RED + "Teleportation cancelled because you moved!");
                player.playSound(player.getLocation(), Sound.ENTITY_WANDERING_TRADER_DISAPPEARED, 1.0f, 1.0f);
            }
        }
    }

    public static class HomeTabCompleter implements TabCompleter {
        @Override
        public List onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            if (sender instanceof Player && args.length == 1) {
                Player player = (Player) sender;
                UltraHome plugin = JavaPlugin.getPlugin(UltraHome.class);
                ConfigurationSection section = plugin.getConfig().getConfigurationSection("homes." + player.getUniqueId().toString());
                if (section != null) {
                    List matches = new ArrayList();
                    String search = args[0].toLowerCase();
                    for (String key : section.getKeys(false)) {
                        if (key.toLowerCase().startsWith(search)) {
                            matches.add(key);
                        }
                    }
                    return matches;
                }
            }
            return Collections.emptyList();
        }
    }
}
