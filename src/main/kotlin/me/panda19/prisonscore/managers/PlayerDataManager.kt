package me.panda19.prisonscore.managers

import me.panda19.prisonscore.PrisonsCore
import me.panda19.prisonscore.models.PlayerProfile
import me.panda19.prisonscore.storage.PlayerDataRepository
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.io.IOException
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * PlayerDataManager
 * - Manages in-memory PlayerProfile cache.
 * - Handles async load/save operations using a dedicated I/O thread.
 * - Supports periodic autosave and graceful shutdown saving.
 */
class PlayerDataManager(
    private val plugin: PrisonsCore,
    private val repo: PlayerDataRepository,
    dataFolder: Path = plugin.dataFolder.toPath()
) {

    private val cache = ConcurrentHashMap<UUID, PlayerProfile>()
    private val ioExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "PrisonsCore-DataIO").apply { isDaemon = true }
    }

    private val autosaveSeconds: Long =
        plugin.config.getLong("storage.json.autosave-seconds", 300L)

    init {
        // Schedule periodic autosave if enabled
        if (autosaveSeconds > 0) {
            plugin.server.scheduler.runTaskTimerAsynchronously(
                plugin,
                Runnable { saveAllAsync() },
                autosaveSeconds * 20L,
                autosaveSeconds * 20L
            )
            plugin.logger.info("⏱️ Autosave scheduled every $autosaveSeconds seconds.")
        }
    }

    /**
     * Load or retrieve an existing player profile.
     * Calls the provided callback on the main thread after loading.
     */
    fun getOrLoad(player: Player, callback: (PlayerProfile) -> Unit) {
        val uuid = player.uniqueId
        val cached = cache[uuid]

        // --- Already cached → call callback + update XP on main thread ---
        if (cached != null) {
            cached.touch(player.name)
            Bukkit.getScheduler().runTask(plugin, Runnable {
                callback(cached)
                plugin.progressionManager.updatePlayerExpBar(player, cached)
            })
            return
        }

        // --- Load from disk async ---
        ioExecutor.submit {
            try {
                val loaded = repo.load(uuid)
                val profile = loaded?.apply { touch(player.name) }
                    ?: PlayerProfile(uuid, player.name)

                cache[uuid] = profile

                // MUST call callback on main thread
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    callback(profile)
                    plugin.progressionManager.updatePlayerExpBar(player, profile)
                })

            } catch (e: IOException) {
                plugin.logger.severe("❌ Failed to load profile for $uuid: ${e.message}")

                val fallback = PlayerProfile(uuid, player.name)
                cache[uuid] = fallback

                Bukkit.getScheduler().runTask(plugin, Runnable {
                    callback(fallback)
                    plugin.progressionManager.updatePlayerExpBar(player, fallback)
                })
            }
        }
    }

    fun getOrCreate(uuid: UUID, name: String): PlayerProfile {
        return cache.computeIfAbsent(uuid) { PlayerProfile(uuid, name) }
    }

    fun get(uuid: UUID): PlayerProfile? = cache[uuid]

    fun saveAsync(uuid: UUID) {
        val profile = cache[uuid] ?: return
        ioExecutor.submit {
            try {
                repo.save(profile)
            } catch (e: IOException) {
                plugin.logger.severe("❌ Failed to save profile $uuid: ${e.message}")
            }
        }
    }

    fun saveAllAsync() {
        val snapshot = ArrayList(cache.values)
        if (snapshot.isEmpty()) return
        ioExecutor.submit {
            snapshot.forEach { profile ->
                try {
                    repo.save(profile)
                } catch (e: IOException) {
                    plugin.logger.severe("❌ Failed to save profile ${profile.uuid}: ${e.message}")
                }
            }
            plugin.logger.info("💾 Autosaved ${snapshot.size} player profiles.")
        }
    }

    /**
     * Synchronous saveAll used during shutdown. Blocks until complete.
     */
    fun saveAllSync(timeoutSeconds: Long = 10) {
        val snapshot = ArrayList(cache.values)
        if (snapshot.isEmpty()) return
        try {
            val futures = snapshot.map { profile ->
                ioExecutor.submit {
                    try {
                        repo.save(profile)
                    } catch (e: IOException) {
                        plugin.logger.severe("❌ Failed to save profile ${profile.uuid} on shutdown: ${e.message}")
                    }
                }
            }
            ioExecutor.shutdown()
            if (!ioExecutor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                plugin.logger.warning("⚠️ DataIO did not finish within $timeoutSeconds seconds; forcing final save.")
                snapshot.forEach {
                    try {
                        repo.save(it)
                    } catch (e: IOException) {
                        plugin.logger.severe("❌ Direct-save failed for profile ${it.uuid}: ${e.message}")
                    }
                }
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            plugin.logger.severe("⚠️ Interrupted while saving during shutdown: ${e.message}")
        }
    }

    fun removeAndSave(uuid: UUID) {
        val profile = cache.remove(uuid) ?: return
        ioExecutor.submit {
            try {
                repo.save(profile)
            } catch (e: IOException) {
                plugin.logger.severe("❌ Failed to save profile $uuid on quit: ${e.message}")
            }
        }
    }

    fun shutdown() {
        saveAllSync()
        //ioExecutor.shutdownNow()
    }
}