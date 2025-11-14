package me.panda19.prisonscore.listeners

import me.panda19.prisonscore.PrisonsCore
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

class PlayerJoinListener(private val plugin: PrisonsCore) : Listener {

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        plugin.playerDataManager.getOrLoad(player) { profile ->
            plugin.logger.info("✅ Loaded profile for ${profile.name} (${profile.uuid})")
            player.sendMessage("§aWelcome back, §f${profile.name}§a!")
            player.sendMessage("§7Level: §b${profile.level} §7| XP: §b${"%.1f".format(profile.xp)}")
        }
    }
}