package me.lebyy.asmppowersplugin;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Random;
import java.util.UUID;

public class ASMPPowersPlugin extends JavaPlugin implements Listener {
    private static final HashMap<UUID, Long > cooldown = new HashMap < UUID, Long > ();
    private static boolean whimsyAndLumiPowerActivated = false;
    private static Vector lastBarrierBlockLocation;
    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getConsoleSender().sendMessage(ChatColor.GREEN + "[Artist SMP Powers Plugin]: The plugin was enabled and is ready to rock n roll!");
    }

    @Override
    public void onDisable() {
        getServer().getConsoleSender().sendMessage(ChatColor.RED + "[Artist SMP Powers Plugin]: The plugin was disabled and won't be rock n roll anymore :c");
    }

    // Check For CoolDown + Send Warn
    private boolean checkForCooldown(Player player) {
        if ((cooldown.containsKey(player.getUniqueId()) && cooldown.get(player.getUniqueId()) > System.currentTimeMillis())) {
            long remainingTime = cooldown.get(player.getUniqueId()) - System.currentTimeMillis();
            player.sendMessage(ChatColor.RED + "You cannot use your power " + player.getName() + ", you need to wait for another " + remainingTime / 1000 + " seconds");
            return true;
        }
        return false;
    }

    // Add player to CoolDown
    private boolean addCooldown(Player player, Integer time) {
        cooldown.put(player.getUniqueId(), System.currentTimeMillis() + time);
        return true;
    }

    // Random Teleportation Function
    private void randomTeleport(Player player) {
        Random random = new Random();
        int x = player.getLocation().getBlockX();
        int y = player.getLocation().getBlockY();
        int z = player.getLocation().getBlockZ();
        int newX = x + random.nextInt(20 - (-20));
        int newZ = z + random.nextInt(20 - (-20));
        int newY = player.getWorld().getHighestBlockYAt(newX, newZ) + 1;
        if (newY > 0) {
            if (newY <= y + 20 || newY >= y - 20) {
                Location newLoc = new Location(player.getWorld(), newX, newY, newZ);
                player.teleport(newLoc);
            } else {
                randomTeleport(player);
            }
        } else {
            randomTeleport(player);
        }
    }

    // Trigerred once a player uses Shift + (Item Swap HotKey, default is F)
    @EventHandler
    public void onAbilityUse(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();

        if (player.isSneaking()) {
            // Randomized teleportation within 20 blocks.
            if (player.hasPermission("powers.frogs")) {
                Boolean playerOnCooldown = checkForCooldown(player);
                if(!playerOnCooldown) {
                    randomTeleport(player);
                    addCooldown(player, 30000);
                }
            }
            if (player.hasPermission("powers.whimsy")) {
                Boolean playerOnCooldown = checkForCooldown(player);
                if(!playerOnCooldown) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 3000, 0));
                    whimsyAndLumiPowerActivated = true;
                    addCooldown(player, 300000);
                }
            }
            if (player.hasPermission("powers.ice")) {
                Boolean playerOnCooldown = checkForCooldown(player);
                if(!playerOnCooldown) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 3000, 0));
                    addCooldown(player, 300000);
                }
            }
        }
    }

    // Trigerred once an Entity receives damage.
    @EventHandler
    public void onDamage(EntityDamageEvent event){
        if(event.getEntity() instanceof Player){
            Player p = (Player) event.getEntity();

            if(whimsyAndLumiPowerActivated) {
                if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                    event.setCancelled(true);
                }
                if (p.getLastDamageCause().getCause() == EntityDamageEvent.DamageCause.FALL) {
                    event.setCancelled(true);
                }
            }
        }
    }

    // Trigerred once player respawn's.
    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("powers.ice")) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0));
        }
    }

    // Trigerred once player joins the server.
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("powers.ice")) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0));
        }
    }

    // Trigerred once a player eats something.
    @EventHandler
    public void onConsumeItem(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        ItemStack food = event.getItem();
        if (player.hasPermission("powers.ice")) {
            if (food.getType() == Material.MILK_BUCKET) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0));
            }
        }
    }
}
