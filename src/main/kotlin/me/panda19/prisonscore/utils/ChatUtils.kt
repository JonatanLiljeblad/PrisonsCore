package me.panda19.prisonscore.utils

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.entity.Player
import java.util.logging.Logger

object ChatUtils {

    private val miniMessage = MiniMessage.miniMessage()
    private val legacyAmpersand = LegacyComponentSerializer.legacyAmpersand()

    fun mm(message: String): Component =
        miniMessage.deserialize(message)

    fun color(message: String?): Component =
        legacyAmpersand.deserialize(message ?: "")

    fun toLegacy(component: Component): String =
        legacyAmpersand.serialize(component)

    /**
     * Plays a sound safely by its Minecraft name (e.g. "entity.player.levelup").
     * Works on modern Paper builds (1.21+).
     */
    fun playSound(player: Player, soundName: String?, volume: Float = 1.0f, pitch: Float = 1.0f) {
        if (soundName.isNullOrBlank()) return

        try {
            // Convert to a NamespacedKey and lookup the sound in the registry
            val key = NamespacedKey.minecraft(soundName.lowercase())
            val sound = Registry.SOUNDS.get(key)

            if (sound != null) {
                player.playSound(player.location, sound, volume, pitch)
            } else {
                player.sendMessage(mm("<red>⚠ Unknown sound: <gray>$soundName"))
                //plugin.logger.warning("Unknown sound name in config: $soundName")
            }
        } catch (e: Exception) {
            player.sendMessage(mm("<red>⚠ Invalid sound name: <gray>$soundName"))
        }
    }
}