package me.lebyy.asmppowersplugin;

import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

public class ASMPPowersPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getServer().getConsoleSender().sendMessage(ChatColor.GREEN + "[Artist SMP Powers Plugin]: The plugin was enabled and is ready to rock n roll!");
    }

    @Override
    public void onDisable() {
        getServer().getConsoleSender().sendMessage(ChatColor.RED + "[Artist SMP Powers Plugin]: The plugin was disabled and won't be rock n roll anymore :c");
    }

}
