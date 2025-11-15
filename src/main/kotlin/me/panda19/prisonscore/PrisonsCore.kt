package me.panda19.prisonscore

import me.panda19.prisonscore.commands.PrisonsCommand
import me.panda19.prisonscore.config.BlockRewardsConfig
import me.panda19.prisonscore.config.ProgressionConfig
import me.panda19.prisonscore.events.PlayerExperienceListener
import me.panda19.prisonscore.listeners.BlockBreakListener
import me.panda19.prisonscore.listeners.PlayerJoinListener
import me.panda19.prisonscore.listeners.PlayerQuitListener
import me.panda19.prisonscore.managers.EconomyManager
import me.panda19.prisonscore.managers.MineManager
import me.panda19.prisonscore.managers.PlayerDataManager
import me.panda19.prisonscore.managers.ProgressionManager
import me.panda19.prisonscore.storage.JsonPlayerDataRepository
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin

class PrisonsCore : JavaPlugin() {

    lateinit var economyManager: EconomyManager
        private set
    lateinit var playerDataManager: PlayerDataManager
        private set
    lateinit var mineManager: MineManager
        private set
    lateinit var progressionConfig: ProgressionConfig
        private set
    lateinit var progressionManager: ProgressionManager
        private set
    lateinit var blockRewardsConfig: BlockRewardsConfig
        private set

    override fun onEnable() {
        val startTime = System.currentTimeMillis()
        instance = this
        saveDefaultConfig()

        // === Register Configs ===
        progressionConfig = ProgressionConfig(this)
        blockRewardsConfig = BlockRewardsConfig(this)
        blockRewardsConfig.load()
        progressionConfig.load()


        // === Initialize Managers ===
        val repo = JsonPlayerDataRepository(dataFolder.toPath())
        playerDataManager = PlayerDataManager(this, repo)
        economyManager = EconomyManager(this)
        mineManager = MineManager(this)
        progressionManager = ProgressionManager(this, progressionConfig)

        // === Setup Vault Economy ===
        if (!economyManager.setupEconomy()) {
            logger.warning("⚠️ Vault economy not found. Economy features will be disabled.")
        }

        // === Register Listeners ===
        val pm = server.pluginManager
        pm.registerEvents(PlayerJoinListener(this), this)
        pm.registerEvents(PlayerQuitListener(this), this)
        pm.registerEvents(BlockBreakListener(this), this)
        pm.registerEvents(PlayerExperienceListener(this), this)

        // === Register Commands ===
        getCommand("prisons")?.apply {
            val cmd = PrisonsCommand(this@PrisonsCore)
            setExecutor(cmd)
            tabCompleter = cmd
        }

        // === Log Startup Info ===
        val startupTime = System.currentTimeMillis() - startTime
        logger.info("✅ PrisonsCore enabled successfully in ${startupTime}ms.")
        logger.info("Running on Paper ${Bukkit.getVersion()}")
    }

    override fun onDisable() {
        logger.info("📦 PrisonsCore is shutting down...")

        try {
            // 1. Cancel any Bukkit schedulers related to this plugin
            server.scheduler.cancelTasks(this)

            // 2. Save all player data synchronously before shutdown
            playerDataManager.saveAllSync()
            logger.info("💾 All player data saved successfully before shutdown.")
        } catch (e: Exception) {
            logger.severe("⚠️ Failed to fully save player data on shutdown: ${e.message}")
            e.printStackTrace()
        }

        // 3. Shut down any executor threads you might have explicitly started
        try {
            playerDataManager.shutdown()
        } catch (ignored: Exception) {}

        logger.info("🧹 PrisonsCore disabled cleanly.")
    }

    /**
     * Reloads all configurations for /prisons reload
     */
    fun reloadConfigs() {
        reloadConfig()
        progressionConfig.load()
        blockRewardsConfig.reload()
        logger.info("🔄 All configs reloaded successfully.")
    }

    companion object {
        lateinit var instance: PrisonsCore
            private set

        const val PREFIX = "§b[PrisonsCore]§r "
    }
}