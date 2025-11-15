package me.panda19.prisonscore.managers

import me.panda19.prisonscore.PrisonsCore
import me.panda19.prisonscore.config.ProgressionConfig
import me.panda19.prisonscore.events.PlayerLevelUpEvent
import me.panda19.prisonscore.events.PlayerPrestigeEvent
import me.panda19.prisonscore.models.PlayerProfile
import me.panda19.prisonscore.utils.ChatUtils
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import java.text.DecimalFormat
import java.util.logging.Level

class ProgressionManager(
    private val plugin: PrisonsCore,
    private val config: ProgressionConfig
) {

    private val df = DecimalFormat("#,##0.##")

    /**
     * Adds XP to a player, then checks for level-up and prestige transitions.
     */
    fun addXp(player: Player, amount: Double) {
        if (amount <= 0) return
        val profile = plugin.playerDataManager.get(player.uniqueId) ?: return

        val oldLevel: Int
        val oldXp: Double

        synchronized(profile) {
            oldLevel = profile.level
            oldXp = profile.xp

            profile.xp += amount
            checkLevelUp(player, profile)

            // Always updatebar after XP changed
            updatePlayerExpBar(player, profile)
        }

        // Fire event if leveled up (fireLevelUpEvent will handle cancellation)
        if (profile.level > oldLevel) {
            fireLevelUpEvent(player, oldLevel, profile.level)
        }

        // Optional debug message
        if (plugin.config.getBoolean("debug.xp", false)) {
            player.sendMessage(
                config.message("xp-gain-debug", mapOf(
                    "amount" to df.format(amount),
                    "oldXp" to df.format(oldXp),
                    "newXp" to df.format(profile.xp),
                    "oldLevel" to oldLevel.toString(),
                    "newLevel" to profile.level.toString()
                ))
            )
        }
    }

    /**
     * Handles level-up chain until the player's XP is below the required threshold.
     * Adds a safety guard to avoid infinite loops on extreme XP values.
     */
    private fun checkLevelUp(player: Player, profile: PlayerProfile) {
        var xpNeeded = config.getXpForLevel(profile.level)
        var safety = 0
        val SAFETY_LIMIT = 1000

        while (profile.xp >= xpNeeded && safety++ < SAFETY_LIMIT) {
            profile.xp -= xpNeeded
            profile.level++
            updatePlayerExpBar(player, profile)

            // Prestige check
            if (shouldPrestige(profile)) {
                tryPrestige(player, profile)
                break
            }

            xpNeeded = config.getXpForLevel(profile.level)
        }

        if (safety >= SAFETY_LIMIT) {
            plugin.logger.log(Level.WARNING, "⚠ Potential XP overflow detected for ${profile.name} — loop hit safety limit ($SAFETY_LIMIT). XP: ${profile.xp}, Level: ${profile.level}")
        }
    }

    /**
     * Determines if the player qualifies for prestige.
     */
    private fun shouldPrestige(profile: PlayerProfile): Boolean {
        return profile.level >= config.requiredLevelForPrestige &&
                profile.prestige < config.maxPrestige
    }

    /**
     * Prestiges the player with formula-based reward scaling.
     */
    private fun tryPrestige(player: Player, profile: PlayerProfile) {
        if (!shouldPrestige(profile)) return

        // Create and fire event first to allow listeners to cancel prestige
        val prestigeEvent = PlayerPrestigeEvent(player, profile.prestige + 1)
        Bukkit.getPluginManager().callEvent(prestigeEvent)
        if (prestigeEvent is Cancellable && prestigeEvent.isCancelled) return

        profile.prestige++

        if (config.resetXpOnPrestige) profile.xp = 0.0
        if (config.prestigeLevelReset >= 0) profile.level = config.prestigeLevelReset

        // Handle economy reset safely
        if (config.resetBalanceOnPrestige) {
            try {
                plugin.economyManager.resetBalance(profile.uuid)
            } catch (e: Exception) {
                plugin.logger.log(Level.WARNING, "⚠️ Failed to reset balance for ${profile.name}: ${e.message}")
            }
        }

        // Formula-driven reward scaling (use the dedicated prestige formula)
        val newMultiplier = config.getPrestigeReward(profile.rewardMultiplier, profile.prestige)
        profile.rewardMultiplier = newMultiplier

        // Fire additional event and send feedback
        firePrestigeEvent(player, profile.prestige)

        // Messages (these respect config parsing)
        player.sendMessage(config.message("prestige.title"))
        player.sendMessage(config.message("prestige.subtitle", mapOf("prestige" to profile.prestige.toString())))
        player.sendMessage(config.message("prestige.reward", mapOf("multiplier" to df.format(profile.rewardMultiplier))))

        // Optional prestige sound (safe and modern)
        config.effects.prestigeSound?.let {
            try {
                ChatUtils.playSound(player, it, 1f, 1f)
            } catch (e: Exception) {
                plugin.logger.log(Level.WARNING, "⚠️ Invalid prestige sound '$it' in progression.yml: ${e.message}")
            }
        }
    }

    /**
     * Re-checks XP on player load, ensuring proper progression.
     */
    fun validateProgress(profile: PlayerProfile) {
        val needed = config.getXpForLevel(profile.level)
        if (profile.xp < needed) return

        Bukkit.getScheduler().runTask(plugin, Runnable {
            val player = Bukkit.getPlayer(profile.uuid) ?: return@Runnable
            val loaded = plugin.playerDataManager.get(profile.uuid) ?: return@Runnable
            checkLevelUp(player, loaded)
        })
    }

    /**
     * Syncs the player's Minecraft XP bar with their custom progression stats.
     * - player.level displays the current custom level
     * - player.exp displays progress to the next level (0.0 to 1.0)
     */
    fun updatePlayerExpBar(player: Player, profile: PlayerProfile) {
        val needed = config.getXpForLevel(profile.level)

        // Prevent division-by-zero or invalid config
        if (needed <= 0) {
            player.level = profile.level
            player.exp = 0f
            return
        }

        val ratio = (profile.xp / needed).coerceIn(0.0, 1.0)

        // Show current level as the green number above the XP bar
        player.level = profile.level

        // Show progress as the experience bar fill amount
        player.exp = ratio.toFloat()
    }

    /**
     * Dispatches a level-up event and sends message.
     * If event is cancellable and cancelled, message/effects are skipped.
     */
    private fun fireLevelUpEvent(player: Player, oldLevel: Int, newLevel: Int) {
        try {
            val event = PlayerLevelUpEvent(player, oldLevel, newLevel)
            Bukkit.getPluginManager().callEvent(event)

            // If the event is cancellable and cancelled, do not send messages/effects
            if (event is Cancellable && event.isCancelled) return

        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "⚠️ Failed to dispatch level-up event: ${e.message}")
        }

        player.sendMessage(config.message("level-up", mapOf("level" to newLevel.toString())))

        // Optional level-up sound
        config.effects.levelUpSound?.let {
            try {
                ChatUtils.playSound(player, it, 1f, 1f)
            } catch (e: Exception) {
                plugin.logger.log(Level.WARNING, "⚠️ Invalid level-up sound '$it' in progression.yml: ${e.message}")
            }
        }
    }

    /**
     * Dispatches a prestige event and logs errors safely.
     */
    private fun firePrestigeEvent(player: Player, newPrestige: Int) {
        try {
            val event = PlayerPrestigeEvent(player, newPrestige)
            Bukkit.getPluginManager().callEvent(event)
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "⚠️ Failed to dispatch prestige event: ${e.message}")
        }
    }
}