package me.panda19.prisonscore.managers

import me.panda19.prisonscore.PrisonsCore
import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import java.text.DecimalFormat
import java.util.UUID

class EconomyManager(private val plugin: PrisonsCore) {

    private var economy: Economy? = null
    private val df = DecimalFormat("#,##0.##")

    fun setupEconomy(): Boolean {
        // Prevent re-hooking if already initialized
        if (economy != null) return true

        val rsp = plugin.server.servicesManager.getRegistration(Economy::class.java)
        economy = rsp?.provider

        return if (economy == null) {
            plugin.logger.warning("⚠️ Vault economy not found. Economy features will be disabled.")
            false
        } else {
            plugin.logger.info("✅ Vault economy hooked: ${economy!!.name}")
            true
        }
    }

    fun deposit(uuid: UUID, amount: Double) {
        if (amount <= 0.0) return
        val eco = economy ?: return
        val offline = Bukkit.getOfflinePlayer(uuid)
        if (!offline.hasPlayedBefore() && !offline.isOnline) return

        try {
            eco.depositPlayer(offline, amount)
            plugin.playerDataManager.get(uuid)?.let { profile ->
                synchronized(profile) {
                    profile.balance = getBalance(uuid)
                }
            }
        } catch (ex: Exception) {
            plugin.logger.warning("⚠️ Failed to deposit ${df.format(amount)} to ${offline.name ?: uuid}: ${ex.message}")
        }
    }

    fun withdraw(uuid: UUID, amount: Double) {
        if (amount <= 0.0) return
        val eco = economy ?: return
        val offline = Bukkit.getOfflinePlayer(uuid)
        if (!offline.hasPlayedBefore() && !offline.isOnline) return

        try {
            eco.withdrawPlayer(offline, amount)
            plugin.playerDataManager.get(uuid)?.let { profile ->
                synchronized(profile) {
                    profile.balance = getBalance(uuid)
                }
            }
        } catch (ex: Exception) {
            plugin.logger.warning("⚠️ Failed to withdraw ${df.format(amount)} from ${offline.name ?: uuid}: ${ex.message}")
        }
    }

    fun getBalance(uuid: UUID): Double {
        val eco = economy ?: return 0.0
        val offline = Bukkit.getOfflinePlayer(uuid)
        return try {
            eco.getBalance(offline)
        } catch (ex: Exception) {
            plugin.logger.warning("⚠️ Failed to fetch balance for ${offline.name ?: uuid}: ${ex.message}")
            0.0
        }
    }

    fun resetBalance(uuid: UUID) {
        val eco = economy ?: return
        val offline = Bukkit.getOfflinePlayer(uuid)

        try {
            val bal = eco.getBalance(offline)
            if (bal > 0.0) {
                eco.withdrawPlayer(offline, bal)
                plugin.logger.fine("💰 Reset balance for ${offline.name ?: uuid} (${df.format(bal)} removed)")
            }

            plugin.playerDataManager.get(uuid)?.let { profile ->
                synchronized(profile) {
                    profile.balance = 0.0
                }
            }
        } catch (ex: Exception) {
            plugin.logger.warning("⚠️ Failed to reset balance for ${offline.name ?: uuid}: ${ex.message}")
        }
    }
}