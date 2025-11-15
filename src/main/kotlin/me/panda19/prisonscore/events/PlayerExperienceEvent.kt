package me.panda19.prisonscore.events

import me.panda19.prisonscore.PrisonsCore
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerExpChangeEvent
import org.bukkit.event.player.PlayerLevelChangeEvent

class PlayerExperienceListener(private val plugin: PrisonsCore) : Listener {

    @EventHandler
    fun onExpChange(event: PlayerExpChangeEvent) {
        // Disable all vanilla exp gain (mobs, ores, bottles, etc.)
        event.amount = 0
    }

    @EventHandler
    fun onLevelChange(event: PlayerLevelChangeEvent) {
        // Override vanilla level changes with your custom system
        val player = event.player
        val profile = plugin.playerDataManager.get(player.uniqueId) ?: return

        // Force correct level
        player.level = profile.level
    }
}