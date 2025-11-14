package me.panda19.prisonscore.commands

import me.panda19.prisonscore.PrisonsCore
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class PrisonsCommand(private val plugin: PrisonsCore) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sendHelp(sender)
            return true
        }

        when (args[0].lowercase()) {
            "reload" -> handleReload(sender)
            else -> sendHelp(sender)
        }

        return true
    }

    private fun handleReload(sender: CommandSender) {
        if (sender is Player && !sender.hasPermission("prisons.admin")) {
            sender.sendMessage("${PrisonsCore.PREFIX}§cYou don't have permission to use this command.")
            return
        }

        val start = System.currentTimeMillis()
        plugin.reloadConfigs()
        val took = System.currentTimeMillis() - start

        sender.sendMessage("${PrisonsCore.PREFIX}§aReloaded all configs in ${took}ms.")
    }

    private fun sendHelp(sender: CommandSender) {
        sender.sendMessage("§7--------- §bPrisonsCore Commands §7---------")
        sender.sendMessage("§b/prisons reload §7- Reloads all configs")
    }

    // === Tab Completer ===
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): MutableList<String>? {
        if (args.size == 1) {
            val options = mutableListOf<String>()
            if (sender.hasPermission("prisons.admin")) {
                options.add("reload")
            }
            return options.filter { it.startsWith(args[0], ignoreCase = true) }.toMutableList()
        }
        return mutableListOf()
    }
}