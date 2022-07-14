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
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class ASMPPowersPlugin extends JavaPlugin implements Listener {
    private static final HashMap < UUID, Long > cooldown = new HashMap < UUID, Long > ();
    private static final HashMap < UUID, Long > dataStore = new HashMap < UUID, Long > ();
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

                    if(player.hasPermission("allow.ride")) {
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

                        if (jump && vehicle.isOnGround()) {
                            // Initial jump velocity is 0.5, which allows them to jump over 1 block but not
                            // 1.5 or 2 (close to default, can be modified)
                            vehicle.setVelocity(vehicle.getVelocity().add(new Vector(0.0, 0.5, 0.0)));
                        }

                        // Now, calculate the new velocity using the function below, and apply to the
                        // vehicle entity
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
        if ((cooldown.containsKey(player.getUniqueId()) && cooldown.get(player.getUniqueId()) > System.currentTimeMillis())) {
            return true;
        }
        return false;
    }

    // Add player to CoolDown
    private boolean addCooldown(Player player, Integer time) {
        cooldown.put(player.getUniqueId(), System.currentTimeMillis() + time);
        return true;
    }

    // Add data to player data
    private boolean addData(Player player, Integer time) {
        dataStore.put(player.getUniqueId(), System.currentTimeMillis() + time);
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
                if (!playerOnCooldown) {
                    randomTeleport(player);
                    addCooldown(player, 30000);
                }
            }
            if (player.hasPermission("powers.whimsy")) {
                Boolean playerOnCooldown = checkForCooldown(player);
                if (!playerOnCooldown) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 600, 4, true, false));
                    addData(player, 35000);
                    addCooldown(player, 180000);
                }
            }
            if (player.hasPermission("powers.lumi")) {
                Boolean playerOnCooldown = checkForCooldown(player);
                if (!playerOnCooldown) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 600, 4, true, false));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 3600, 0, true, false));
                    addData(player, 35000);
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
                Boolean playerOnCooldown = checkForCooldown(player);
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
                Boolean playerOnCooldown = checkForCooldown(player);
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
        }
    }

    // Trigerred once an Entity receives damage.
    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player)) return;

        Player player = (Player) event.getEntity();

        if (player.hasPermission("powers.whimsy")) {
            Boolean checkForActivation = checkForData(player);
            if (checkForActivation) {
                if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                    event.setCancelled(true);
                }
                if (player.getLastDamageCause().getCause() == EntityDamageEvent.DamageCause.FALL) {
                    event.setCancelled(true);
                }
            }
        };
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
    }

    // Trigerred once a player eats something.
    @EventHandler
    public void onConsumeItem(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        ItemStack food = event.getItem();
        if (player.hasPermission("powers.ice")) {
            if (food.getType() == Material.MILK_BUCKET) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, true, false));
            }
        }
        if (player.hasPermission("powers.milo")) {
            if (food.getType() == Material.MILK_BUCKET) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, true, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 1, true, false));
            }
        }
        if (player.hasPermission("powers.lake")) {
            if (food.getType() == Material.MILK_BUCKET) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, Integer.MAX_VALUE, 9, true, false));
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
                if (item.getItemMeta().getDisplayName().equalsIgnoreCase("amethyst heart")) {
                    player.damage(Integer.MAX_VALUE);
                }
            }
        }
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
                        if (itemInHand.getItemMeta().getDisplayName().equalsIgnoreCase("Sparklia")) {
                            if (numberOfTimesNagaliePowerUsed > 3) {
                                Boolean playerOnCooldown = checkForCooldown(player);
                                if (!playerOnCooldown) {
                                    Block oldBlock = event.getClickedBlock();
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

        if (player.hasPermission("magic.swiftness_grace_buff")) {
            if (player.isSneaking()) {
                if (action == Action.LEFT_CLICK_BLOCK || action == Action.LEFT_CLICK_AIR) {
                    Float playerPitch = player.getEyeLocation().getPitch();
                    if (playerPitch <= -90) {
                        player.setVelocity(player.getVelocity().setY(0.5));
                    }
                    Block block = player.getLocation().getBlock().getRelative(BlockFace.DOWN);
                    if (block.getType() == Material.LAVA_CAULDRON) {
                        Boolean playerOnCooldown = checkForCooldown(player);
                        if (!playerOnCooldown) {
                            if (player.getInventory().contains(Material.AMETHYST_SHARD, 64)) {
                                player.getInventory().removeItem(new ItemStack(Material.AMETHYST_SHARD, 64));
                            } else if (player.getInventory().contains(Material.AMETHYST_BLOCK, 16)) {
                                player.getInventory().removeItem(new ItemStack(Material.AMETHYST_BLOCK, 16));
                            }
                            List<Entity> playersEntity = player.getNearbyEntities(5, 5, 5);
                            Entity playerEntity = playersEntity.get(0);
                            if (playerEntity instanceof Player player2) {
                                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 7200, 2, true, false));
                                player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 7200, 2, true, false));
                                player.addPotionEffect(new PotionEffect(PotionEffectType.CONDUIT_POWER, 7200, 2, true, false));
                                player2.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 3600, 2, true, false));
                            } else {
                                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 3600, 2, true, false));
                            }
                            addCooldown(player, 360000);
                        }

                    }
                }
            }
        }

        if (player.hasPermission("magic.swiftness_grace_debuff")) {
            if (player.isSneaking()) {
                if (action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR) {
                    Float playerPitch = player.getEyeLocation().getPitch();
                    if (playerPitch <= -90) {
                        player.setVelocity(player.getVelocity().setY(0.5));
                    }
                    Block block = player.getLocation().getBlock().getRelative(BlockFace.DOWN);
                    if (block.getType() == Material.LAVA_CAULDRON) {
                        Boolean playerOnCooldown = checkForCooldown(player);
                        if (!playerOnCooldown) {
                            if (player.getInventory().contains(Material.AMETHYST_SHARD, 64)) {
                                player.getInventory().removeItem(new ItemStack(Material.AMETHYST_SHARD, 64));
                            } else if (player.getInventory().contains(Material.AMETHYST_BLOCK, 16)) {
                                player.getInventory().removeItem(new ItemStack(Material.AMETHYST_BLOCK, 16));
                            }
                            List<Entity> playersEntity = player.getNearbyEntities(5, 5, 5);
                            Entity playerEntity = playersEntity.get(0);
                            if (playerEntity instanceof Player player2) {
                                player2.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 7200, 2, true, false));
                                player2.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 7200, 2, true, false));
                                player2.addPotionEffect(new PotionEffect(PotionEffectType.CONDUIT_POWER, 7200, 2, true, false));
                                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 3600, 2, true, false));
                            } else {
                                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 3600, 2, true, false));
                            }
                            addCooldown(player, 360000);
                        }

                    }
                }
            }
        }

        if(player.hasPermission("magic.lucky_shroud_buff")) {
            if (player.isSneaking()) {
                if (action == Action.LEFT_CLICK_BLOCK || action == Action.LEFT_CLICK_AIR) {
                    Integer angleOfPlayer = (Math.round(player.getEyeLocation().getYaw()) + 270) % 360;
                    if (angleOfPlayer <= 247 || angleOfPlayer <= 292 || angleOfPlayer <= 337) {
                        Block block = player.getLocation().getBlock().getRelative(BlockFace.DOWN);
                        if (block.getType() == Material.LAVA_CAULDRON) {
                            Boolean playerOnCooldown = checkForCooldown(player);
                            if (!playerOnCooldown) {
                                if (player.getInventory().contains(Material.AMETHYST_SHARD, 64)) {
                                    player.getInventory().removeItem(new ItemStack(Material.AMETHYST_SHARD, 64));
                                } else if (player.getInventory().contains(Material.AMETHYST_BLOCK, 16)) {
                                    player.getInventory().removeItem(new ItemStack(Material.AMETHYST_BLOCK, 16));
                                }
                                List<Entity> playersEntity = player.getNearbyEntities(5, 5, 5);
                                Entity playerEntity = playersEntity.get(0);
                                if (playerEntity instanceof Player player2) {
                                    player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 7200, 2, true, false));
                                    player.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 7200, 2, true, false));
                                    player2.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 3600, 0, true, false));
                                } else {
                                    player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 3600, 0, true, false));
                                }
                                addCooldown(player, 360000);
                            }
                        }
                    }
                }
            }
        }

        if(player.hasPermission("magic.lucky_shroud_debuff")) {
            if (player.isSneaking()) {
                if (action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR) {
                    Integer angleOfPlayer = (Math.round(player.getEyeLocation().getYaw()) + 270) % 360;
                    if (angleOfPlayer <= 247 || angleOfPlayer <= 292 || angleOfPlayer <= 337) {
                        Block block = player.getLocation().getBlock().getRelative(BlockFace.DOWN);
                        if (block.getType() == Material.LAVA_CAULDRON) {
                            Boolean playerOnCooldown = checkForCooldown(player);
                            if (!playerOnCooldown) {
                                if (player.getInventory().contains(Material.AMETHYST_SHARD, 64)) {
                                    player.getInventory().removeItem(new ItemStack(Material.AMETHYST_SHARD, 64));
                                } else if (player.getInventory().contains(Material.AMETHYST_BLOCK, 16)) {
                                    player.getInventory().removeItem(new ItemStack(Material.AMETHYST_BLOCK, 16));
                                }
                                List<Entity> playersEntity = player.getNearbyEntities(5, 5, 5);
                                Entity playerEntity = playersEntity.get(0);
                                if (playerEntity instanceof Player player2) {
                                    player2.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 7200, 2, true, false));
                                    player2.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 7200, 2, true, false));
                                    player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 3600, 0, true, false));
                                } else {
                                    player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 3600, 0, true, false));
                                }
                                addCooldown(player, 360000);
                            }
                        }
                    }
                }
            }
        }

        if(player.hasPermission("magic.travellers_blessing_buff")) {
            if (player.isSneaking()) {
                if (action == Action.LEFT_CLICK_BLOCK || action == Action.LEFT_CLICK_AIR) {
                    Integer angleOfPlayer = (Math.round(player.getEyeLocation().getYaw()) + 270) % 360;
                    if (angleOfPlayer <= 67 || angleOfPlayer <= 112 || angleOfPlayer <= 157) {
                        Block block = player.getLocation().getBlock().getRelative(BlockFace.DOWN);
                        if (block.getType() == Material.LAVA_CAULDRON) {
                            Boolean playerOnCooldown = checkForCooldown(player);
                            if (!playerOnCooldown) {
                                if (player.getInventory().contains(Material.AMETHYST_SHARD, 32)) {
                                    player.getInventory().removeItem(new ItemStack(Material.AMETHYST_SHARD, 32));
                                } else if (player.getInventory().contains(Material.AMETHYST_BLOCK, 8)) {
                                    player.getInventory().removeItem(new ItemStack(Material.AMETHYST_BLOCK, 8));
                                }
                                List<Entity> playersEntity = player.getNearbyEntities(5, 5, 5);
                                Entity playerEntity = playersEntity.get(0);
                                if (playerEntity instanceof Player player2) {
                                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 3600, 0, true, false));
                                    player.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, 3600, 0, true, false));
                                    player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 3600, 0, true, false));
                                    player2.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING, 2400, 0, true, false));
                                } else {
                                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING, 2400, 0, true, false));
                                }
                                addCooldown(player, 360000);
                            }
                        }
                    }
                }
            }
        }

        if(player.hasPermission("magic.travellers_blessing_debuff")) {
            if (player.isSneaking()) {
                if (action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR) {
                    Integer angleOfPlayer = (Math.round(player.getEyeLocation().getYaw()) + 270) % 360;
                    if (angleOfPlayer <= 67 || angleOfPlayer <= 112 || angleOfPlayer <= 157) {
                        Block block = player.getLocation().getBlock().getRelative(BlockFace.DOWN);
                        if (block.getType() == Material.LAVA_CAULDRON) {
                            Boolean playerOnCooldown = checkForCooldown(player);
                            if (!playerOnCooldown) {
                                if (player.getInventory().contains(Material.AMETHYST_SHARD, 32)) {
                                    player.getInventory().removeItem(new ItemStack(Material.AMETHYST_SHARD, 32));
                                } else if (player.getInventory().contains(Material.AMETHYST_BLOCK, 8)) {
                                    player.getInventory().removeItem(new ItemStack(Material.AMETHYST_BLOCK, 8));
                                }
                                List<Entity> playersEntity = player.getNearbyEntities(5, 5, 5);
                                Entity playerEntity = playersEntity.get(0);
                                if (playerEntity instanceof Player player2) {
                                    player2.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 3600, 0, true, false));
                                    player2.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, 3600, 0, true, false));
                                    player2.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 3600, 0, true, false));
                                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING, 2400, 0, true, false));
                                } else {
                                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING, 2400, 0, true, false));
                                }
                                addCooldown(player, 360000);
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

        if(player.hasPermission("allow.ride")) {
            if(entity.getType() == EntityType.SPIDER) {
                if(player.hasPermission(("allow.ride.spider"))) {
                    entity.addPassenger(player);
                }
            } else if(entity.getType() == EntityType.CAVE_SPIDER) {
                if(player.hasPermission(("allow.ride.cave_spider"))) {
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

        if(!player.hasPermission("powers.sit")) event.setCancelled(true);
        if(!target.hasPermission("powers.sittable")) event.setCancelled(true);
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
}