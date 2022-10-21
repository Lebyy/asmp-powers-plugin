package me.lebyy.asmppowersplugin;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import dev.geco.gsit.api.event.PrePlayerPlayerSitEvent;
import me.lebyy.asmppowersplugin.ArmorListener.ArmorEquipEvent;
import me.lebyy.asmppowersplugin.ArmorListener.ArmorListener;
import me.lebyy.asmppowersplugin.ArmorListener.DispenserArmorListener;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

class Cooldown {
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public boolean isOnCooldown(UUID uuid) {
        return cooldowns.containsKey(uuid) && cooldowns.get(uuid) > System.currentTimeMillis();
    }

    public void setCooldown(UUID uuid, int cooldown) {
        cooldowns.put(uuid, System.currentTimeMillis() + cooldown);
    }

    public long getCooldown(UUID uuid) {
        return cooldowns.get(uuid) - System.currentTimeMillis();
    }

    public void removeCooldown(UUID uuid) {
        cooldowns.remove(uuid);
    }

    public void clearCooldowns() {
        cooldowns.clear();
    }

    public void warnCooldown(Player player, String ability) {
        player.sendMessage(ChatColor.RED + "You are on cooldown for " + ability + " for " + getCooldown(player.getUniqueId()) / 1000 + " seconds.");
    }
}

public class ASMPPowersPlugin extends JavaPlugin implements Listener {
    private static final HashMap < UUID, Long > cooldown = new HashMap<>();
    private static final HashMap < UUID, Long > dataStore = new HashMap<>();

    private static final Cooldown swiftnessGraceCooldown = new Cooldown();
    private static final Cooldown luckyShroudCooldown = new Cooldown();
    private static final Cooldown travellersBlessingCooldown = new Cooldown();

    private static boolean crossedPowerActivated = false;
    private static int numberOfTimesNagaliePowerUsed = 0;

    @Override
    public void onEnable() {
        ProtocolManager pm;
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new ArmorListener(getConfig().getStringList("blocked")), this);
        try {
            Class.forName("org.bukkit.event.block.BlockDispenseArmorEvent");
            getServer().getPluginManager().registerEvents(new DispenserArmorListener(), this);
        } catch (Exception ignored) {}
        getServer().getConsoleSender().sendMessage(ChatColor.GREEN + "[Artist SMP Powers Plugin]: The plugin was enabled and is ready to rock n roll!");

        // Initialize ProtocolLib
        pm = ProtocolLibrary.getProtocolManager();
        pm.addPacketListener(new PacketAdapter(this, ListenerPriority.NORMAL, PacketType.Play.Client.STEER_VEHICLE) {

            //
            // Steer Vehicle packet gets called when the player is riding a vehicle and
            // presses WASD or some other keys like spacebar.
            //
            @Override
            public void onPacketReceiving(PacketEvent event) {
                if (event.getPacketType() == PacketType.Play.Client.STEER_VEHICLE) {
                    Player player = event.getPlayer();

                    if (player.hasPermission("allow.ride")) {
                        // Grab the necessary objects
                        PacketContainer pc = event.getPacket();
                        Entity vehicle = player.getVehicle();

                        // First float in packet is the Left/Right value (A/D)
                        float side = pc.getFloat().read(0);

                        // Second float in packet is the Forward/Backward value (W/S keys)
                        float forw = pc.getFloat().read(1);

                        // First byte bitmask (retrieved through boolean) is whether the player is
                        // jumping (pressing spacebar)
                        boolean jump = pc.getBooleans().read(0);

                        if (jump) {
                            assert vehicle != null;
                            if (vehicle.isOnGround()) {
                                // Initial jump velocity is 0.5, which allows them to jump over 1 block but not
                                // 1.5 or 2 (close to default, can be modified)
                                vehicle.setVelocity(vehicle.getVelocity().add(new Vector(0.0, 0.5, 0.0)));
                            }
                        }

                        // Now, calculate the new velocity using the function below, and apply to the
                        // vehicle entity
                        assert vehicle != null;
                        Vector vel = ASMPPowersPlugin.getVelocityVector(vehicle.getVelocity(), player, side, forw);
                        vehicle.setVelocity(vel);
                        // Update entity head rotation
                        vehicle.setRotation(player.getEyeLocation().getYaw(), player.getEyeLocation().getPitch());
                    }
                }
            }
        });
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

    // Check for player data
    private boolean checkForData(Player player) {
        return cooldown.containsKey(player.getUniqueId()) && cooldown.get(player.getUniqueId()) > System.currentTimeMillis();
    }

    // Add player to CoolDown
    private void addCooldown(Player player, Integer time) {
        cooldown.put(player.getUniqueId(), System.currentTimeMillis() + time);
    }

    // Add data to player data
    private void addData(Player player) {
        dataStore.put(player.getUniqueId(), System.currentTimeMillis() + 35000);
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
                boolean playerOnCooldown = checkForCooldown(player);
                if (!playerOnCooldown) {
                    randomTeleport(player);
                    addCooldown(player, 30000);
                }
            }
            if (player.hasPermission("powers.whimsy")) {
                boolean playerOnCooldown = checkForCooldown(player);
                if (!playerOnCooldown) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 600, 4, true, false));
                    addData(player);
                    addCooldown(player, 180000);
                }
            }
            if (player.hasPermission("powers.lumi")) {
                boolean playerOnCooldown = checkForCooldown(player);
                if (!playerOnCooldown) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 600, 4, true, false));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 3600, 0, true, false));
                    addData(player);
                    addCooldown(player, 240000);
                }
            }
            if (player.hasPermission("powers.ice")) {
                PotionEffect effect = event.getPlayer().getPotionEffect(PotionEffectType.SLOW_FALLING);
                if (effect != null) {
                    player.removePotionEffect(PotionEffectType.SLOW_FALLING);
                } else {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, Integer.MAX_VALUE, 0, true, false));
                }
            }
            if (player.hasPermission("powers.crossed")) {
                crossedPowerActivated = true;
                playerWallClimbing(player);
            }
            if (player.hasPermission("powers.oddity")) {
                PotionEffect effect = event.getPlayer().getPotionEffect(PotionEffectType.INVISIBILITY);
                if (effect != null) {
                    player.removePotionEffect(PotionEffectType.INVISIBILITY);
                } else {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, true, false));
                }
            }
            if (player.hasPermission("powers.float")) {
                boolean playerOnCooldown = checkForCooldown(player);
                if (!playerOnCooldown) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 20, 9, true, false));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 200, 9, true, false));
                    addCooldown(player, 20000);
                }
            }
            if (player.hasPermission("powers.nagalie")) {
                PotionEffect effect = event.getPlayer().getPotionEffect(PotionEffectType.SLOW_FALLING);
                if (effect != null) {
                    player.removePotionEffect(PotionEffectType.SLOW_FALLING);
                } else {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, Integer.MAX_VALUE, 0, true, false));
                }
            }
            if (player.hasPermission("powers.coppice")) {
                boolean playerOnCooldown = checkForCooldown(player);
                if (!playerOnCooldown) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 600, 1, true, false));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 700, 0, true, false));
                    addCooldown(player, 180000);
                }
            }
            if (player.hasPermission("powers.wilbie")) {
                PotionEffect effect = event.getPlayer().getPotionEffect(PotionEffectType.JUMP);
                if (effect != null) {
                    player.removePotionEffect(PotionEffectType.JUMP);
                } else {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, Integer.MAX_VALUE, 0, true, false));
                }
            }
            if (player.hasPermission("powers.aithne")) {
                if (player.getInventory().getItemInMainHand().getType() == Material.AMETHYST_BLOCK) {
                    if (player.getInventory().getItemInMainHand().getAmount() == 64) {
                        player.getInventory().setItemInMainHand(new ItemStack(Material.BUNDLE, 1));
                    }
                }
            }
        }
    }

    // Trigerred once an Entity receives damage.
    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player)) return;

        Player player = (Player) event.getEntity();

        if (player.hasPermission("powers.whimsy")) {
            boolean checkForActivation = checkForData(player);
            if (checkForActivation) {
                if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                    event.setCancelled(true);
                }
                if (Objects.requireNonNull(player.getLastDamageCause()).getCause() == EntityDamageEvent.DamageCause.FALL) {
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
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, true, false));
        }
        if (player.hasPermission("powers.milo")) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, true, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 1, true, false));
        }
        if (player.hasPermission("powers.lake")) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, Integer.MAX_VALUE, 9, true, false));
        }
        if (player.hasPermission("powers.aithne")) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 2, true, false));
        }
    }

    // Trigerred once player joins the server.
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("powers.ice")) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, true, false));
        }
        if (player.hasPermission("powers.milo")) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, true, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 1, true, false));
        }
        if (player.hasPermission("powers.lake")) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, Integer.MAX_VALUE, 9, true, false));
        }
        if (player.hasPermission("powers.aithne")) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 2, true, false));
            checkAithneHealth(player);
        }
    }

    // Trigerred once a player eats something.
    @EventHandler
    public void onConsumeItem(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        ItemStack food = event.getItem();
        if (food.getType() == Material.MILK_BUCKET) {
            if (player.hasPermission("powers.ice")) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, true, false));
            }
            if (player.hasPermission("powers.milo")) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, true, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 1, true, false));
            }
            if (player.hasPermission("powers.lake")) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, Integer.MAX_VALUE, 9, true, false));
            }
            if (player.hasPermission("powers.aithne")) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 2, true, false));
            }
        }
    }

    // Trigerred once an entity targets a player.
    @EventHandler
    public void onEntityTargetPlayer(EntityTargetLivingEntityEvent event) {
        Entity targetEntity = event.getTarget();
        if (!(targetEntity instanceof Player)) return;

        Player player = (Player) event.getTarget();

        Entity entity = event.getEntity();
        if (player.hasPermission("powers.crossed")) {
            if (entity.getType() == EntityType.SPIDER) {
                event.setCancelled(true);
            }
        }
    }

    // Trigerred once an player wears armor.
    @EventHandler
    public void onPlayerEquipArmor(ArmorEquipEvent event) {
        Player player = event.getPlayer();

        if (player.hasPermission("powers.milo")) {
            event.setCancelled(true);
        }
    }

    // Trigerred once an player armor breaks.
    @EventHandler
    public void onPlayerArmorBreak(PlayerItemBreakEvent event) {
        Player player = event.getPlayer();

        if (player.hasPermission("powers.lake")) {
            ItemStack item = event.getBrokenItem();

            if (item.getType() == Material.CHAINMAIL_CHESTPLATE) {
                if (Objects.requireNonNull(item.getItemMeta()).getDisplayName().equalsIgnoreCase("amethyst heart")) {
                    player.damage(Integer.MAX_VALUE);
                }
            }
        }
    }

    // Triggered once an entity attacks an entity. (player)
    @EventHandler
    public void onEntityAttackEntity(EntityDamageByEntityEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player)) return;

        Player player = (Player) event.getEntity();

        Entity damager = event.getDamager();
        if(!(damager instanceof Player)) return;

        Player attacker = (Player) event.getDamager();

        if (attacker.hasPermission("powers.aithne")) {
            attacker.damage(6);
            player.setHealth(player.getHealth() + 2);

            attacker.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 600, 1, true, false));
            attacker.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING, 600, 1, true, false));
        }
    }

    // Add a check for aithne player if they are under 3 hearts, give them weakness and then remove it when they are above 3 hearts.
    // Use a bukkit runnable to check every 5 seconds.

    private void checkAithneHealth(Player player) {
        new BukkitRunnable() {

            @Override
            public void run() {
                if (player.getHealth() <= 6) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, Integer.MAX_VALUE, 0, true, false));
                } else {
                    player.removePotionEffect(PotionEffectType.WEAKNESS);
                }
            }
        }.runTaskTimer(this, 0, 100);
    }


    // A function which aids in a player climbing a wall.
    private void playerWallClimbing(Player player) {
        new BukkitRunnable() {

            @Override
            public void run() {
                Location location = player.getLocation();
                Block block = location.getBlock();
                if (player.hasPermission("powers.crossed")) {
                    if (crossedPowerActivated) {
                        if (player.isOnline()) {
                            if (player.isSneaking() && !player.isGliding()) {
                                if (nextToWall(player) && !block.isLiquid()) {
                                    player.setVelocity(player.getVelocity()
                                            .setY(0.175));
                                }
                            } else {
                                cancel();
                            }
                        } else {
                            cancel();
                        }
                    } else {
                        cancel();
                    }
                } else {
                    cancel();
                }
            }
        }.runTaskTimerAsynchronously(this, 0L, 1L);
    }

    /**
     * Next to wall boolean.
     *
     * @param player the player
     *
     * @return the boolean
     */
    private boolean nextToWall(Player player) {
        World world = player.getWorld();
        double locX = player.getLocation().getX();
        double locY = player.getLocation().getY();
        double locZ = player.getLocation().getZ();
        Location xp = new Location(world, locX + 0.30175, locY, locZ);
        Location xn = new Location(world, locX - 0.30175, locY, locZ);
        Location zp = new Location(world, locX, locY, locZ + 0.30175);
        Location zn = new Location(world, locX, locY, locZ - 0.30175);

        if (xp.getBlock().getType().isSolid()) {
            return true;
        }
        if (xn.getBlock().getType().isSolid()) {
            return true;
        }
        if (zp.getBlock().getType().isSolid()) {
            return true;
        }
        return zn.getBlock().getType().isSolid();
    }

    // Trigerred once an player interacts's with something.
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        Action action = event.getAction();

        if (player.hasPermission(("powers.nagalie"))) {
            if (action == Action.RIGHT_CLICK_BLOCK) {
                if (itemInHand.getType() == Material.SHEARS) {
                    if (itemInHand.hasItemMeta()) {
                        if (Objects.requireNonNull(itemInHand.getItemMeta()).getDisplayName().equalsIgnoreCase("Sparklia")) {
                            if (numberOfTimesNagaliePowerUsed > 3) {
                                boolean playerOnCooldown = checkForCooldown(player);
                                if (!playerOnCooldown) {
                                    Block oldBlock = event.getClickedBlock();
                                    assert oldBlock != null;
                                    Block newBlock = oldBlock.getLocation().add(0, 1, 0).getBlock();
                                    if (newBlock.getType() == Material.AIR) {
                                        newBlock.setType(oldBlock.getType());
                                        oldBlock.setType(Material.AIR);
                                        addCooldown(player, 30000);
                                        numberOfTimesNagaliePowerUsed = 1;
                                    }
                                }
                            } else {
                                Block oldBlock = event.getClickedBlock();
                                assert oldBlock != null;
                                Block newBlock = oldBlock.getLocation().add(0, 1, 0).getBlock();
                                if (newBlock.getType() == Material.AIR) {
                                    newBlock.setType(oldBlock.getType());
                                    oldBlock.setType(Material.AIR);
                                    addCooldown(player, 30000);
                                    numberOfTimesNagaliePowerUsed++;
                                }
                            }
                        }
                    }
                }
            }
        }

        if (player.hasPermission("magic.aithnes_arrival")) {
            if (player.isSneaking()) {
                String directionOfPlayer = getCardinalDirection(player);
                if (Objects.equals(directionOfPlayer, "N") || Objects.equals(directionOfPlayer, "NE") || Objects.equals(directionOfPlayer, "NW")) {
                    if (action == Action.LEFT_CLICK_BLOCK || action == Action.LEFT_CLICK_AIR) {
                        Block block = player.getLocation().getBlock().getRelative(BlockFace.DOWN);
                        if (block.getType() == Material.LAVA_CAULDRON) {
                            if (player.getInventory().contains(Material.AMETHYST_BLOCK, 1)) {
                                player.getInventory().removeItem(new ItemStack(Material.AMETHYST_BLOCK, 1));
                            } else {
                                player.sendMessage(ChatColor.RED + "You do not have enough amethyst blocks to use this power.");
                            }
                            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                                if (onlinePlayer.hasPermission("powers.aithne")) {
                                    if (onlinePlayer != player) {
                                        onlinePlayer.teleport(player.getLocation().add(3, 0, 0));
                                        player.spawnParticle(Particle.VILLAGER_HAPPY, player.getLocation(), 10, 0.5, 1.5, 0.5, 1);
                                        onlinePlayer.spawnParticle(Particle.WAX_OFF, onlinePlayer.getLocation(), 10, 0.5, 1.5, 0.5, 1);
                                        return;
                                    }
                                }
                            }
                            player.sendMessage(ChatColor.RED + "No players found with the permission 'powers.aithne'");
                        }
                    }
                }
            }
        }

        if (player.hasPermission("magic.swiftness_grace")) {
            if (player.isSneaking()) {
                String directionOfPlayer = getCardinalDirection(player);
                if (Objects.equals(directionOfPlayer, "S") || Objects.equals(directionOfPlayer, "SE") || Objects.equals(directionOfPlayer, "SW")) {
                    if (action == Action.LEFT_CLICK_BLOCK || action == Action.LEFT_CLICK_AIR) {
                        Block block = player.getLocation().getBlock().getRelative(BlockFace.DOWN);
                        if (block.getType() == Material.LAVA_CAULDRON) {
                            boolean playerOnCooldown = swiftnessGraceCooldown.isOnCooldown(player.getUniqueId());
                            if (!playerOnCooldown) {
                                if (player.getInventory().contains(Material.AMETHYST_BLOCK, 32)) {
                                    player.getInventory().removeItem(new ItemStack(Material.AMETHYST_BLOCK, 32));
                                } else {
                                    player.sendMessage(ChatColor.RED + "You do not have enough amethyst blocks to use this power.");
                                }
                                List < Entity > playersEntity = player.getNearbyEntities(5, 5, 5);
                                playersEntity.remove(player);
                                Entity playerEntity = playersEntity.get(0);
                                if (playerEntity instanceof Player player2) {
                                    if(swiftnessGraceCooldown.isOnCooldown(player2.getUniqueId())) {
                                        player.sendMessage(ChatColor.RED + "This player is on cooldown for " + swiftnessGraceCooldown.getCooldown(player2.getUniqueId()) / 1000 + " seconds.");
                                        swiftnessGraceCooldown.warnCooldown(player2, "Swiftness Grace Spell [Player]");
                                        return;
                                    }
                                    player2.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 7200, 2, true, false));
                                    player2.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 7200, 2, true, false));
                                    player2.addPotionEffect(new PotionEffect(PotionEffectType.CONDUIT_POWER, 7200, 2, true, false));
                                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 3600, 2, true, false));
                                    swiftnessGraceCooldown.setCooldown(player2.getUniqueId(), 360000);
                                } else {
                                    player.sendMessage(ChatColor.RED + "You must be near a player to use this spell.");
                                    return;
                                }
                                swiftnessGraceCooldown.setCooldown(player.getUniqueId(), 180000);
                            } else {
                                swiftnessGraceCooldown.warnCooldown(player, "Swiftness Grace Spell [Caster]");
                                return;
                            }
                        }
                    }
                }
            }
        }

        if (player.hasPermission("magic.lucky_shroud")) {
            if (player.isSneaking()) {
                String directionOfPlayer = getCardinalDirection(player);
                if (Objects.equals(directionOfPlayer, "E")) {
                    if (action == Action.LEFT_CLICK_BLOCK || action == Action.LEFT_CLICK_AIR) {
                        Block block = player.getLocation().getBlock().getRelative(BlockFace.DOWN);
                        if (block.getType() == Material.LAVA_CAULDRON) {
                            boolean playerOnCooldown = luckyShroudCooldown.isOnCooldown(player.getUniqueId());
                            if (!playerOnCooldown) {
                                if (player.getInventory().contains(Material.AMETHYST_BLOCK, 24)) {
                                    player.getInventory().removeItem(new ItemStack(Material.AMETHYST_BLOCK, 24));
                                } else {
                                    player.sendMessage(ChatColor.RED + "You do not have enough amethyst blocks to use this power.");
                                }
                                List < Entity > playersEntity = player.getNearbyEntities(5, 5, 5);
                                playersEntity.remove(player);
                                Entity playerEntity = playersEntity.get(0);
                                if (playerEntity instanceof Player player2) {
                                    if(luckyShroudCooldown.isOnCooldown(player2.getUniqueId())) {
                                        player.sendMessage(ChatColor.RED + "This player is on cooldown for " + luckyShroudCooldown.getCooldown(player2.getUniqueId()) / 1000 + " seconds.");
                                        luckyShroudCooldown.warnCooldown(player2, "Lucky Shroud Spell [Player]");
                                        return;
                                    }
                                    player2.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 7200, 2, true, false));
                                    player2.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 7200, 2, true, false));
                                    player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 3600, 0, true, false));
                                    luckyShroudCooldown.setCooldown(player2.getUniqueId(), 360000);
                                } else {
                                    player.sendMessage(ChatColor.RED + "You must be near a player to use this spell.");
                                    return;
                                }
                                luckyShroudCooldown.setCooldown(player.getUniqueId(), 180000);
                            } else {
                                luckyShroudCooldown.warnCooldown(player, "Lucky Shroud Spell [Caster]");
                                return;
                            }
                        }
                    }
                }
            }
        }

        if (player.hasPermission("magic.travellers_blessing")) {
            if (player.isSneaking()) {
                String directionOfPlayer = getCardinalDirection(player);
                if (Objects.equals(directionOfPlayer, "W")) {
                    if (action == Action.LEFT_CLICK_BLOCK || action == Action.LEFT_CLICK_AIR) {
                        Block block = player.getLocation().getBlock().getRelative(BlockFace.DOWN);
                        if (block.getType() == Material.LAVA_CAULDRON) {
                            boolean playerOnCooldown = travellersBlessingCooldown.isOnCooldown(player.getUniqueId());
                            if (!playerOnCooldown) {
                                if (player.getInventory().contains(Material.AMETHYST_BLOCK, 16)) {
                                    player.getInventory().removeItem(new ItemStack(Material.AMETHYST_BLOCK, 16));
                                } else {
                                    player.sendMessage(ChatColor.RED + "You do not have enough amethyst blocks to cast this spell.");
                                }
                                List < Entity > playersEntity = player.getNearbyEntities(5, 5, 5);
                                playersEntity.remove(player);
                                Entity playerEntity = playersEntity.get(0);
                                if (playerEntity instanceof Player player2) {
                                    if(travellersBlessingCooldown.isOnCooldown(player2.getUniqueId())) {
                                        player.sendMessage(ChatColor.RED + "This player is on cooldown for " + travellersBlessingCooldown.getCooldown(player2.getUniqueId()) / 1000 + " seconds.");
                                        travellersBlessingCooldown.warnCooldown(player2, "Traveller's Blessing Spell [Player]");
                                        return;
                                    }
                                    player2.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 3600, 0, true, false));
                                    player2.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, 3600, 0, true, false));
                                    player2.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 3600, 0, true, false));
                                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING, 2400, 0, true, false));
                                    travellersBlessingCooldown.setCooldown(player2.getUniqueId(), 360000);
                                } else {
                                    player.sendMessage(ChatColor.RED + "No nearby players found.");
                                    return;
                                }
                                addCooldown(player, 360000);
                            } else {
                                luckyShroudCooldown.warnCooldown(player, "Traveller's Blessing Spell [Caster]");
                            }
                        }
                    }
                }
            }
        }
    }

    // Trigerred when a player right clicks on an entity
    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();

        if (player.hasPermission("allow.ride")) {
            if (entity.getType() == EntityType.SPIDER) {
                if (player.hasPermission(("allow.ride.spider"))) {
                    entity.addPassenger(player);
                }
            } else if (entity.getType() == EntityType.CAVE_SPIDER) {
                if (player.hasPermission(("allow.ride.cave_spider"))) {
                    entity.addPassenger(player);
                }
            }
        }
    }

    // Gets called before a Player starts sitting on another Player
    @EventHandler
    public void onPrePlayerPlayerSit(PrePlayerPlayerSitEvent event) {
        Player player = event.getPlayer();
        Player target = event.getTarget();

        if (!player.hasPermission("powers.sit")) event.setCancelled(true);
        if (!target.hasPermission("powers.sittable")) event.setCancelled(true);
    }

    private static Vector getVelocityVector(Vector vector, Player player, float side, float forw) {
        // First, kill horizontal velocity that the entity might already have
        vector.setX(0.0);
        vector.setZ(0.0);

        //
        // Many tests were run to get the math seen below.
        //

        // Create a new vector representing the direction of WASD
        Vector mot = new Vector(forw * -1.0, 0, side);

        if (mot.length() > 0.0) {
            // Turn to face the direction the player is facing
            mot.rotateAroundY(Math.toRadians(player.getLocation().getYaw() * -1.0F + 90.0F));
            // Now bring it back to a reasonable speed (0.2, reasonable default speed, can
            // be configured)
            mot.normalize().multiply(0.25F);
        }

        // Now, take this new horizontal direction velocity, and add it to what we
        // already have (which will only be vertical velocity at this point.)
        // We need to preserve vertical velocity so we handle gravity properly.
        return mot.add(vector);
    }

    public static String getCardinalDirection(Player player) {
        double rotation = (player.getLocation().getYaw() - 90.0F) % 360.0F;
        if (rotation < 0.0D) {
            rotation += 360.0D;
        }
        if ((0.0D <= rotation) && (rotation < 22.5D)) {
            return "N";
        }
        if ((22.5D <= rotation) && (rotation < 67.5D)) {
            return "NE";
        }
        if ((67.5D <= rotation) && (rotation < 112.5D)) {
            return "E";
        }
        if ((112.5D <= rotation) && (rotation < 157.5D)) {
            return "SE";
        }
        if ((157.5D <= rotation) && (rotation < 202.5D)) {
            return "S";
        }
        if ((202.5D <= rotation) && (rotation < 247.5D)) {
            return "SW";
        }
        if ((247.5D <= rotation) && (rotation < 292.5D)) {
            return "W";
        }
        if ((292.5D <= rotation) && (rotation < 337.5D)) {
            return "NW";
        }
        if ((337.5D <= rotation) && (rotation < 360.0D)) {
            return "N";
        }
        return null;
    }
}