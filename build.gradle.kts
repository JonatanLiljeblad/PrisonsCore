plugins {
    kotlin("jvm") version "2.3.0-Beta2"
    id("com.gradleup.shadow") version "8.3.0"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "me.panda19"
version = "0.1"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/") {
        name = "placeholderapi-repo"
    }
    maven("https://jitpack.io")
    maven("https://nexus.hc.to/content/repositories/pub_releases")
}

dependencies {
    // ✅ Main Paper API
    compileOnly("io.papermc.paper:paper-api:1.21.10-R0.1-SNAPSHOT")

    // ✅ VaultAPI — exclude its outdated Bukkit version
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    // ✅ Runtime dependencies (safe to shade)
    implementation("net.objecthunter:exp4j:0.4.8")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.zaxxer:HikariCP:5.1.0")
}

tasks {
    runServer {
        minecraftVersion("1.21.10") // match your Paper version
    }
}

val targetJavaVersion = 21
kotlin {
    jvmToolchain(targetJavaVersion)
}

tasks.build {
    dependsOn("shadowJar")
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

// ✅ Resolution strategy to enforce Paper API and prevent Bukkit conflicts
configurations.all {
    resolutionStrategy {
        force("io.papermc.paper:paper-api:1.21.10-R0.1-SNAPSHOT")
        eachDependency {
            if (requested.group == "org.bukkit" && requested.name == "bukkit") {
                useTarget("io.papermc.paper:paper-api:1.21.10-R0.1-SNAPSHOT")
            }
        }
    }
}