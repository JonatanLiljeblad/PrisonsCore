package me.panda19.prisonscore.listeners

import me.panda19.prisonscore.PrisonsCore
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent

class PlayerQuitListener(private val plugin: PrisonsCore) : Listener {

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val player = event.player
        plugin.logger.info("💾 Saving and unloading profile for ${player.name}...")
        plugin.playerDataManager.removeAndSave(player.uniqueId)
    }
}