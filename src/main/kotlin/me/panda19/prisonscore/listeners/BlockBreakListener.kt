package me.panda19.prisonscore.listeners

import me.panda19.prisonscore.PrisonsCore
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent

class BlockBreakListener(private val plugin: PrisonsCore) : Listener {

    @EventHandler(ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        val block = event.block

        // quick guard
        if (!isMineable(block.type)) return

        // profile must be loaded (get returns cached profile)
        val profile = plugin.playerDataManager.get(player.uniqueId) ?: return

        // read base values from blockrewards config
        val baseXp = plugin.blockRewardsConfig.getXp(block.type)
        val baseMoney = plugin.blockRewardsConfig.getMoney(block.type)

        // use ProgressionConfig's formula helpers (these call FormulaEvaluator internally)
        val xpGained = plugin.progressionConfig.getBlockXp(baseXp, profile.prestige)
        val moneyGained = plugin.progressionConfig.getBlockMoney(baseMoney, profile.prestige)

        // apply rewards via managers
        plugin.progressionManager.addXp(player, xpGained)
        plugin.economyManager.deposit(player.uniqueId, moneyGained)

        // optional debug message (toggle with config debug.xp)
        if (plugin.config.getBoolean("debug.xp", false)) {
            player.sendMessage("§7⛏ §b+${"%.1f".format(xpGained)} XP §7| §a+$${"%.2f".format(moneyGained)}")
        }
    }

    private fun isMineable(material: Material): Boolean {
        // customize for your server/mine regions; keep this simple for now
        return material.isBlock && material != Material.AIR && material != Material.BEDROCK
    }
}