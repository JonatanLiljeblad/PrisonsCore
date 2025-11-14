package me.panda19.prisonscore.config

import me.panda19.prisonscore.PrisonsCore
import org.bukkit.Material
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

class BlockRewardsConfig(private val plugin: PrisonsCore) {

    private lateinit var config: FileConfiguration
    private val file = File(plugin.dataFolder, "blockrewards.yml")

    var defaultXp = 5.0
        private set
    var defaultMoney = 0.5
        private set

    fun load() {
        if (!file.exists()) {
            plugin.saveResource("blockrewards.yml", false)
        }
        config = YamlConfiguration.loadConfiguration(file)

        defaultXp = config.getDouble("default.xp", 5.0)
        defaultMoney = config.getDouble("default.money", 0.5)
    }

    fun reload() {
        load()
    }

    fun getXp(material: Material): Double {
        return config.getDouble("rewards.${material.name}.xp", defaultXp)
    }

    fun getMoney(material: Material): Double {
        return config.getDouble("rewards.${material.name}.money", defaultMoney)
    }
}