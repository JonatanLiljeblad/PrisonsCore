package me.panda19.prisonscore.config

import me.panda19.prisonscore.PrisonsCore
import me.panda19.prisonscore.utils.ChatUtils
import me.panda19.prisonscore.utils.FormulaEvaluator
import net.kyori.adventure.text.Component
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.logging.Level

class ProgressionConfig(private val plugin: PrisonsCore) {

    private lateinit var config: FileConfiguration
    private val file = File(plugin.dataFolder, "progression.yml")

    companion object {
        // bump when you change structure / defaults
        private const val CURRENT_CONFIG_VERSION = 2
    }

    // ======================================================
    // Base Configuration Values
    // ======================================================

    var baseXp: Double = 100.0
        private set
    var xpMultiplier: Double = 1.15
        private set
    var requiredLevelForPrestige: Int = 100
        private set
    var resetBalanceOnPrestige: Boolean = true
        private set
    var resetXpOnPrestige: Boolean = true
        private set
    var prestigeLevelReset: Int = 1
        private set
    var prestigeRewardMultiplier: Double = 1.25
        private set
    var maxPrestige: Int = 999
        private set

    private var configVersion: Int = 1

    // ======================================================
    // Runtime Storage
    // ======================================================
    private val formulas = mutableMapOf<String, String>()
    private val messages = mutableMapOf<String, Component>()

    // Simple effects configuration (optional)
    data class Effects(
        val levelUpSound: String? = null,
        val prestigeSound: String? = null
    )

    var effects: Effects = Effects()

    // ======================================================
    // Lifecycle
    // ======================================================
    fun load() {
        if (!file.exists()) plugin.saveResource("progression.yml", false)
        config = YamlConfiguration.loadConfiguration(file)
        reloadValues()
    }

    fun reload() {
        config = YamlConfiguration.loadConfiguration(file)
        reloadValues()
        plugin.logger.info("✅ Reloaded progression.yml successfully.")
    }

    // ======================================================
    // Value Reloading
    // ======================================================
    private fun reloadValues() {
        configVersion = config.getInt("config-version", 1)

        // handle simple migration note
        if (configVersion < CURRENT_CONFIG_VERSION) {
            plugin.logger.log(Level.INFO, "⚙ Detected old progression.yml (v$configVersion). Upgrading to v$CURRENT_CONFIG_VERSION.")
            // (Optional) you can add automated migration logic here.
            // For now, we just log the change — consider backing up & copying defaults in future.
        }

        baseXp = config.getDouble("levels.base-xp", 100.0)
        xpMultiplier = config.getDouble("levels.xp-multiplier", 1.15)

        requiredLevelForPrestige = config.getInt("prestige.required-level", 100)
        resetBalanceOnPrestige = config.getBoolean("prestige.reset-balance", true)
        resetXpOnPrestige = config.getBoolean("prestige.reset-xp", true)
        prestigeLevelReset = config.getInt("prestige.level-reset", 1)
        prestigeRewardMultiplier = config.getDouble("prestige.reward-multiplier", 1.25)
        maxPrestige = config.getInt("prestige.max-prestige", 999)

        loadFormulas()
        loadMessages()
        loadEffects()
    }

    // ======================================================
    // Section Loading
    // ======================================================
    private fun loadFormulas() {
        formulas.clear()
        val section = config.getConfigurationSection("formulas") ?: return
        for (key in section.getKeys(false)) {
            formulas[key] = section.getString(key, "") ?: ""
        }

        // ensure new formula key exists as fallback
        if (!formulas.containsKey("prestige-reward")) {
            // default prestige reward formula (uses 'base' and 'prestige')
            formulas["prestige-reward"] = "base * (1 + (prestige * 0.25))"
        }
    }

    private fun loadMessages() {
        messages.clear()
        val section = config.getConfigurationSection("messages") ?: return
        for (key in section.getKeys(true)) {
            val fullPath = "messages.$key"
            val msg = config.getString(fullPath) ?: continue
            messages[key] = parse(msg)
        }
    }

    private fun loadEffects() {
        // Optional sound/effect section
        val levelUpSound = config.getString("effects.level-up.sound", null)
        val prestigeSound = config.getString("effects.prestige.sound", null)
        effects = Effects(levelUpSound, prestigeSound)
    }

    // ======================================================
    // Message Parsing & Retrieval
    // ======================================================
    private fun parse(input: String?): Component {
        if (input == null) return Component.empty()
        val trimmed = input.trim()
        return if (trimmed.contains('<') && trimmed.contains('>'))
            ChatUtils.mm(trimmed)
        else
            ChatUtils.color(trimmed)
    }

    fun message(key: String, placeholders: Map<String, String> = emptyMap()): Component {
        // Fallback: try both "prestige.title" and "title"
        val base = messages[key]
            ?: messages[key.substringAfterLast(".")]
            ?: return Component.text(key)

        var plain = ChatUtils.toLegacy(base)
        placeholders.forEach { (p, v) -> plain = plain.replace("{$p}", v) }
        return parse(plain)
    }

    // ======================================================
    // Formula Helpers
    // ======================================================

    /**
     * baseVars now includes xp and rewardMultiplier so formulas can reference them.
     */
    private fun baseVars(level: Int = 1, prestige: Int = 0, xp: Double = 0.0, rewardMultiplier: Double = 1.0): MutableMap<String, Double> =
        mutableMapOf(
            "base" to baseXp,
            "multiplier" to xpMultiplier,
            "level" to level.toDouble(),
            "prestige" to prestige.toDouble(),
            "blockXp" to 0.0,
            "baseMoney" to 0.0,
            "xp" to xp,
            "rewardMultiplier" to rewardMultiplier
        )

    private fun evalDebug(name: String, formula: String, vars: Map<String, Double>): Double {
        val result = FormulaEvaluator.eval(formula, vars)
        if (plugin.config.getBoolean("debug.formulas", false)) {
            plugin.logger.log(
                Level.INFO,
                "[Formula Debug] $name = $formula => $result vars=$vars"
            )
        }
        return result
    }

    // ======================================================
    // Public Formula Accessors
    // ======================================================

    fun getXpForLevel(level: Int): Double {
        val formula = formulas["level-xp"] ?: "base * (multiplier ^ (level - 1))"
        val vars = baseVars(level)
        return evalDebug("level-xp", formula, vars)
    }

    fun getBlockXp(blockXp: Double, prestige: Int): Double {
        val formula = formulas["block-xp"] ?: "blockXp * (1 + (prestige * 0.05))"
        val vars = baseVars(prestige = prestige)
        vars["blockXp"] = blockXp
        return evalDebug("block-xp", formula, vars)
    }

    fun getBlockMoney(baseMoney: Double, prestige: Int): Double {
        val formula = formulas["money"] ?: "baseMoney * (1 + (prestige * 0.1))"
        val vars = baseVars(prestige = prestige)
        vars["baseMoney"] = baseMoney
        return evalDebug("money", formula, vars)
    }

    /**
     * New getter for prestige reward formula (separate from block money).
     * Expects formulas["prestige-reward"] to exist (we ensure a default above).
     */
    fun getPrestigeReward(base: Double, prestige: Int): Double {
        val formula = formulas["prestige-reward"] ?: "base * (1 + (prestige * 0.25))"
        val vars = baseVars(prestige = prestige, rewardMultiplier = base)
        vars["base"] = base
        return evalDebug("prestige-reward", formula, vars)
    }

    fun getDeathXpLoss(level: Int): Double {
        val formula = formulas["death-xp-loss"] ?: "level * 2"
        val vars = baseVars(level)
        return evalDebug("death-xp-loss", formula, vars)
    }
}