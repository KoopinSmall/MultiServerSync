package uz.koopin.mss.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import uz.koopin.mss.PaperSettings;
import uz.koopin.mss.PaperSyncPlugin;
import uz.koopin.mss.messages.CommandMessage;

import java.util.Arrays;

public class SyncCommand implements CommandExecutor {

    private final PaperSyncPlugin plugin;

    public SyncCommand(PaperSyncPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("mss.command.sync")) {
            sender.sendMessage(ChatColor.RED + "Нет прав.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Использование: /sync [-g] <target> <command>");
            sender.sendMessage(ChatColor.GRAY + "  <target> — имя сервера, или '*' / 'all' для всех серверов");
            sender.sendMessage(ChatColor.GRAY + "  -g <group> — выполнить на всех серверах группы");
            return true;
        }

        boolean isGroup = false;
        String target;
        String commandLine;
        int cmdStartIndex;

        if (args[0].equalsIgnoreCase("-g")) {
            if (args.length < 3) {
                sender.sendMessage(ChatColor.RED + "Укажите группу и команду!");
                return true;
            }
            isGroup = true;
            target = args[1];
            cmdStartIndex = 2;
        } else {
            target = args[0];
            cmdStartIndex = 1;
        }

        commandLine = String.join(" ", Arrays.copyOfRange(args, cmdStartIndex, args.length));

        CommandMessage message = new CommandMessage(
                PaperSettings.PROJECT_NAME,
                PaperSyncPlugin.SERVER_NAME,
                target,
                isGroup,
                commandLine
        );

        plugin.getServerManager().getMessageBroker().publishMessage(message);
        sender.sendMessage(ChatColor.GREEN + "Команда отправлена!");

        return true;
    }
}