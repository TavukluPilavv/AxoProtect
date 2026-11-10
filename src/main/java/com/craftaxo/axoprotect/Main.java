package com.craftaxo.axoprotect;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.text.Normalizer;
import java.util.*;

public final class Main extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private final Map<UUID, Selection> selections = new HashMap<>();
    private final List<ProtectedRegion> regions = new ArrayList<>();
    private NamespacedKey wandKey;
    private File regionsFile;
    private YamlConfiguration regionsConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        wandKey = new NamespacedKey(this, "selection_wand");
        regionsFile = new File(getDataFolder(), "regions.yml");
        loadRegions();
        PluginCommand command = getCommand("dunya");
        if (command == null) {
            getLogger().severe("plugin.yml icinde dunya komutu bulunamadi!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        command.setExecutor(this);
        command.setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("AxoProtect 1.0.0 aktif. " + regions.size() + " koruma alani yuklendi.");
    }

    @Override
    public void onDisable() {
        saveRegions();
        selections.clear();
    }

    private void loadRegions() {
        regions.clear();
        if (!regionsFile.exists()) {
            try {
                getDataFolder().mkdirs();
                regionsFile.createNewFile();
            } catch (IOException ex) {
                getLogger().severe("regions.yml olusturulamadi: " + ex.getMessage());
            }
        }
        regionsConfig = YamlConfiguration.loadConfiguration(regionsFile);
        for (String id : regionsConfig.getKeys(false)) {
            ConfigurationSection s = regionsConfig.getConfigurationSection(id);
            if (s == null) continue;
            String world = s.getString("world");
            if (world == null) continue;
            regions.add(new ProtectedRegion(
                    id, world,
                    s.getInt("min-x"), s.getInt("min-y"), s.getInt("min-z"),
                    s.getInt("max-x"), s.getInt("max-y"), s.getInt("max-z"),
                    s.isSet("break-allowed") ? s.getBoolean("break-allowed") : null,
                    s.isSet("place-allowed") ? s.getBoolean("place-allowed") : null,
                    s.getLong("created", System.currentTimeMillis())
            ));
        }
        regions.sort(Comparator.comparingLong(r -> r.created));
    }

    private void saveRegions() {
        if (regionsConfig == null) regionsConfig = new YamlConfiguration();
        for (String key : new HashSet<>(regionsConfig.getKeys(false))) regionsConfig.set(key, null);
        for (ProtectedRegion r : regions) {
            String path = r.id;
            regionsConfig.set(path + ".world", r.world);
            regionsConfig.set(path + ".min-x", r.minX);
            regionsConfig.set(path + ".min-y", r.minY);
            regionsConfig.set(path + ".min-z", r.minZ);
            regionsConfig.set(path + ".max-x", r.maxX);
            regionsConfig.set(path + ".max-y", r.maxY);
            regionsConfig.set(path + ".max-z", r.maxZ);
            regionsConfig.set(path + ".break-allowed", r.breakAllowed);
            regionsConfig.set(path + ".place-allowed", r.placeAllowed);
            regionsConfig.set(path + ".created", r.created);
        }
        try {
            regionsConfig.save(regionsFile);
        } catch (IOException ex) {
            getLogger().severe("Koruma alanlari kaydedilemedi: " + ex.getMessage());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(color(msg("wand-only-player")));
            return true;
        }
        if (args.length != 4 || !normalize(args[1]).equals("normal")) {
            player.sendMessage(color(msg("usage")));
            return true;
        }
        return beginSelection(player, args[0], args[2], args[3]);
    }

    private boolean beginSelection(Player player, String worldName, String operation, String state) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            player.sendMessage(color(msg("invalid-world").replace("%world%", worldName)));
            return true;
        }
        String op = normalize(operation);
        String st = normalize(state);
        boolean breaking = op.equals("blokkirma");
        boolean placing = op.equals("blokkoyma");
        if (!breaking && !placing) {
            player.sendMessage(color(msg("usage")));
            return true;
        }
        if (!st.equals("ac") && !st.equals("kapa")) {
            player.sendMessage(color(msg("usage")));
            return true;
        }
        String permission = "axoprotect." + (breaking ? "blokkirma." : "blokkoyma.") + st;
        String turkishPermission = "axoprotect." + (breaking ? "blokkırma." : "blokkoyma.") + (st.equals("ac") ? "aç" : "kapa");
        if (!player.hasPermission("axoprotect.admin") && !player.hasPermission(permission) && !player.hasPermission(turkishPermission)) {
            player.sendMessage(color(msg("no-permission")));
            return true;
        }
        if (!player.hasPermission("axoprotect.wand") && !player.hasPermission("axoprotect.admin")) {
            player.sendMessage(color(msg("no-permission")));
            return true;
        }

        ItemStack wand = createWand();
        player.getInventory().addItem(wand).values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        selections.put(player.getUniqueId(), new Selection(world.getName(), breaking, st.equals("ac")));
        player.sendMessage(color(msg("selection-start")));
        return true;
    }

    private ItemStack createWand() {
        Material material;
        try {
            material = Material.valueOf(getConfig().getString("protection.wand-material", "WOODEN_AXE").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            material = Material.WOODEN_AXE;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color("&6AxoProtect &eSecim Baltasi"));
        meta.setLore(List.of(color("&7Sol tik: 1. nokta"), color("&7Sag tik: 2. nokta")));
        meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isWand(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWandUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !isWand(event.getItem())) return;
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        Selection selection = selections.get(player.getUniqueId());
        if (selection == null) {
            player.sendMessage(color("&cOnce /dunya <dunya> normal <blokkırma|blokkoyma> <aç|kapa> komutunu kullan."));
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!block.getWorld().getName().equalsIgnoreCase(selection.world)) {
            player.sendMessage(color("&cBu secim " + selection.world + " dunyasinda yapilmali."));
            return;
        }
        if (action == Action.LEFT_CLICK_BLOCK) {
            selection.first = new BlockPoint(block.getX(), block.getY(), block.getZ());
            player.sendMessage(color(msg("point-1").replace("%x%", "" + block.getX()).replace("%y%", "" + block.getY()).replace("%z%", "" + block.getZ())));
        } else {
            selection.second = new BlockPoint(block.getX(), block.getY(), block.getZ());
            player.sendMessage(color(msg("point-2").replace("%x%", "" + block.getX()).replace("%y%", "" + block.getY()).replace("%z%", "" + block.getZ())));
        }
        if (selection.first != null && selection.second != null) completeSelection(player, selection);
    }

    private void completeSelection(Player player, Selection s) {
        int minX = Math.min(s.first.x, s.second.x), maxX = Math.max(s.first.x, s.second.x);
        int minY = Math.min(s.first.y, s.second.y), maxY = Math.max(s.first.y, s.second.y);
        int minZ = Math.min(s.first.z, s.second.z), maxZ = Math.max(s.first.z, s.second.z);
        long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        long maxVolume = getConfig().getLong("protection.max-selection-volume", 5000000L);
        if (volume > maxVolume) {
            player.sendMessage(color("&cSecilen alan cok buyuk! En fazla " + maxVolume + " blok olabilir."));
            s.first = null;
            s.second = null;
            return;
        }
        long now = System.currentTimeMillis();
        String id = "region-" + now + "-" + UUID.randomUUID().toString().substring(0, 8);
        Boolean breakAllowed = s.breakRule ? s.allowed : null;
        Boolean placeAllowed = s.breakRule ? null : s.allowed;
        ProtectedRegion region = new ProtectedRegion(id, s.world, minX, minY, minZ, maxX, maxY, maxZ, breakAllowed, placeAllowed, now);
        regions.add(region);
        saveRegions();
        selections.remove(player.getUniqueId());
        removeWands(player);
        String state = s.allowed ? "açık" : "kapalı";
        String message = s.breakRule ? msg("saved-break") : msg("saved-place");
        player.sendMessage(color(message.replace("%world%", s.world).replace("%state%", state)));
    }

    private Boolean regionRuleAt(String world, int x, int y, int z, boolean breaking) {
        for (int i = regions.size() - 1; i >= 0; i--) {
            ProtectedRegion r = regions.get(i);
            if (!r.contains(world, x, y, z)) continue;
            Boolean rule = breaking ? r.breakAllowed : r.placeAllowed;
            if (rule != null) return rule;
        }
        return null;
    }

    private void removeWands(Player player) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            if (isWand(player.getInventory().getItem(i))) player.getInventory().setItem(i, null);
        }
        if (isWand(player.getInventory().getItemInOffHand())) player.getInventory().setItemInOffHand(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (canBypass(player) || isTrapWorld(event.getBlock().getWorld())) return;
        Boolean allowed = regionRuleAt(event.getBlock().getWorld().getName(), event.getBlock().getX(), event.getBlock().getY(), event.getBlock().getZ(), true);
        if (Boolean.FALSE.equals(allowed)) {
            event.setCancelled(true);
            player.sendMessage(color(msg("denied-break")));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (canBypass(player) || isTrapWorld(event.getBlock().getWorld())) return;
        Block b = event.getBlock();
        Boolean allowed = regionRuleAt(b.getWorld().getName(), b.getX(), b.getY(), b.getZ(), false);
        if (Boolean.FALSE.equals(allowed)) {
            event.setCancelled(true);
            player.sendMessage(color(msg("denied-place")));
        }
    }

    private boolean canBypass(Player player) {
        return player.isOp() || player.hasPermission(getConfig().getString("protection.bypass-permission", "axoprotect.bypass"));
    }

    private boolean isTrapWorld(World world) {
        if (!getConfig().getBoolean("integrations.skip-trapplugin-worlds", true)) return false;
        if (Bukkit.getPluginManager().getPlugin("TrapPlugin") == null) return false;
        for (String name : getConfig().getStringList("integrations.trapplugin-worlds")) {
            if (world.getName().equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private String msg(String key) {
        return getConfig().getString("messages." + key, "&c" + key);
    }

    private String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }

    private String normalize(String input) {
        String lower = input.toLowerCase(Locale.ROOT)
                .replace('ı', 'i').replace('İ', 'i').replace('ş', 's')
                .replace('ğ', 'g').replace('ü', 'u').replace('ö', 'o').replace('ç', 'c');
        return lower;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> worlds = new ArrayList<>();
            for (World w : Bukkit.getWorlds()) worlds.add(w.getName());
            return filter(worlds, args[0]);
        }
        if (args.length == 2) return filter(List.of("normal"), args[1]);
        if (args.length == 3) return filter(List.of("blokkırma", "blokkoyma"), args[2]);
        if (args.length == 4) return filter(List.of("aç", "kapa"), args[3]);
        return Collections.emptyList();
    }

    private List<String> filter(List<String> options, String input) {
        String needle = input.toLowerCase(Locale.ROOT);
        return options.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(needle)).toList();
    }

    private static final class Selection {
        final String world;
        final boolean breakRule;
        final boolean allowed;
        BlockPoint first;
        BlockPoint second;
        Selection(String world, boolean breakRule, boolean allowed) {
            this.world = world; this.breakRule = breakRule; this.allowed = allowed;
        }
    }
    private record BlockPoint(int x, int y, int z) {}
    private static final class ProtectedRegion {
        final String id, world;
        final int minX, minY, minZ, maxX, maxY, maxZ;
        final Boolean breakAllowed, placeAllowed;
        final long created;
        ProtectedRegion(String id, String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                        Boolean breakAllowed, Boolean placeAllowed, long created) {
            this.id=id; this.world=world; this.minX=minX; this.minY=minY; this.minZ=minZ;
            this.maxX=maxX; this.maxY=maxY; this.maxZ=maxZ;
            this.breakAllowed=breakAllowed; this.placeAllowed=placeAllowed; this.created=created;
        }
        boolean contains(String worldName, int x, int y, int z) {
            return world.equalsIgnoreCase(worldName) && x>=minX && x<=maxX && y>=minY && y<=maxY && z>=minZ && z<=maxZ;
        }
    }
}