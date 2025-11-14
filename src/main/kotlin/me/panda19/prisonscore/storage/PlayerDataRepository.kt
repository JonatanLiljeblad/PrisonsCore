package me.panda19.prisonscore.storage

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import me.panda19.prisonscore.models.PlayerProfile
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import javax.sql.DataSource
import java.util.logging.Logger

interface PlayerDataRepository {
    @Throws(IOException::class)
    fun load(uuid: UUID): PlayerProfile?

    @Throws(IOException::class)
    fun save(profile: PlayerProfile)

    @Throws(IOException::class)
    fun delete(uuid: UUID)
}

/**
 * File-based JSON repository.
 * Each player gets their own JSON file under dataDir/players/<uuid>.json.
 * Designed to be used from a background thread (file I/O).
 */
class JsonPlayerDataRepository(
    private val dataDir: Path,
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create(),
    private val logger: Logger? = null
) : PlayerDataRepository {

    private val playersDir = dataDir.resolve("players")

    init {
        if (!Files.exists(playersDir)) {
            Files.createDirectories(playersDir)
        }
    }

    private fun pathFor(uuid: UUID): Path = playersDir.resolve("$uuid.json")

    override fun load(uuid: UUID): PlayerProfile? {
        val path = pathFor(uuid)
        if (!Files.exists(path)) return null
        return try {
            val json = Files.readString(path)
            val type = object : TypeToken<PlayerProfile>() {}.type
            gson.fromJson<PlayerProfile>(json, type)
        } catch (ex: IOException) {
            logger?.severe("❌ Failed to load profile for $uuid: ${ex.message}")
            null
        }
    }

    override fun save(profile: PlayerProfile) {
        val path = pathFor(profile.uuid)
        val json = gson.toJson(profile)

        try {
            val tmp = path.resolveSibling("${path.fileName}.tmp")
            Files.writeString(tmp, json)
            Files.move(
                tmp,
                path,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (ex: IOException) {
            logger?.severe("❌ Failed to save profile for ${profile.name}: ${ex.message}")
        }
    }

    override fun delete(uuid: UUID) {
        val path = pathFor(uuid)
        if (Files.exists(path)) {
            try {
                Files.delete(path)
            } catch (ex: IOException) {
                logger?.severe("⚠️ Failed to delete profile $uuid: ${ex.message}")
            }
        }
    }
}

/**
 * SQL repository stub (use when migrating to MySQL/Postgres). Implement prepared statements with HikariCP.
 */
class SQLPlayerDataRepository(private val dataSource: DataSource) : PlayerDataRepository {
    override fun load(uuid: UUID): PlayerProfile? {
        throw UnsupportedOperationException("SQL repo not implemented yet")
    }

    override fun save(profile: PlayerProfile) {
        throw UnsupportedOperationException("SQL repo not implemented yet")
    }

    override fun delete(uuid: UUID) {
        throw UnsupportedOperationException("SQL repo not implemented yet")
    }

    companion object {
        /*
        Example schema (Postgres / MySQL):

        CREATE TABLE players (
            uuid CHAR(36) PRIMARY KEY,
            name VARCHAR(36) NOT NULL,
            level INTEGER NOT NULL DEFAULT 1,
            xp BIGINT NOT NULL DEFAULT 0,
            prestige INTEGER NOT NULL DEFAULT 0,
            blocks_mined BIGINT NOT NULL DEFAULT 0,
            last_seen BIGINT NOT NULL DEFAULT 0,
            raw_json TEXT NULL,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
        );

        Use UPSERT (ON CONFLICT / ON DUPLICATE KEY UPDATE) for save.
         */
    }
}