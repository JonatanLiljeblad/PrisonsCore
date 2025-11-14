package me.panda19.prisonscore.models

import java.util.UUID

data class PlayerProfile(
    val uuid: UUID,
    var name: String,
    var level: Int = 1,
    var xp: Double = 0.0,
    var prestige: Int = 0,
    var balance: Double = 0.0,
    var rewardMultiplier: Double = 1.0,
    var lastSeen: Long = System.currentTimeMillis(),
    var version: Int = 1
) {
    fun touch(currentName: String) {
        if (this.name != currentName) {
            this.name = currentName
        }
        this.lastSeen = System.currentTimeMillis()
    }

    fun resetForPrestige() {
        level = 1
        xp = 0.0
        balance = 0.0
    }

    override fun toString(): String {
        return "PlayerProfile(name=$name, level=$level, xp=$xp, prestige=$prestige, balance=$balance, multiplier=$rewardMultiplier)"
    }
}