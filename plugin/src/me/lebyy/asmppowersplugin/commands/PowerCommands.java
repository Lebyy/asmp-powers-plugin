package me.lebyy.asmppowersplugin.commands;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.Objects;

public class PowerCommands implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if(!(sender instanceof Player)) {
            return true;
        }

        Player player = (Player) sender;

        if (cmd.getName().equalsIgnoreCase("axolotl")) {
            int amount = 1;
            if (args.length > 0) {
                try {
                    amount = Integer.parseInt(args[0]);
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Invalid number");
                    return true;
                }
            }

            Location location = player.getLocation();
            for (int x = -10; x < 10; x++) {
                for (int y = -10; y < 10; y++) {
                    for (int z = -10; z < 10; z++) {
                        if (Objects.requireNonNull(location.getWorld()).getBlockAt(location.clone().add(x, y, z)).getType() == Material.WATER) {
                            for (int i = 0; i < amount; i++) {
                                location.getWorld().spawnEntity(location.clone().add(x, y, z), EntityType.AXOLOTL);
                            }
                            player.sendMessage(ChatColor.GREEN + "Spawned " + amount + " axolotl(s)");
                            return true;
                        }
                    }
                }
            }

            player.sendMessage(ChatColor.RED + "No water found within 10 blocks");
            return true;
        }

        return true;
    }
}
