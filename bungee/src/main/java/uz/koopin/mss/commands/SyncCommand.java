package uz.koopin.mss.commands;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.TabExecutor;
import uz.koopin.mss.BungeeSettings;
import uz.koopin.mss.BungeeSyncPlugin;
import uz.koopin.mss.messages.CommandMessage;
import uz.koopin.mss.storage.ServerGroup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class SyncCommand extends Command implements TabExecutor {

    private final BungeeSyncPlugin plugin;

    public SyncCommand(BungeeSyncPlugin plugin) {
        super("bsync", "mss.command.sync");
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(new ComponentBuilder("Использование: /bsync [-g] <target> <command>").color(ChatColor.RED).create());
            sender.sendMessage(new ComponentBuilder("  <target> — имя сервера, или '*' / 'all' для всех серверов").color(ChatColor.GRAY).create());
            sender.sendMessage(new ComponentBuilder("  -g <group> — выполнить на всех серверах группы").color(ChatColor.GRAY).create());
            return;
        }

        boolean isGroup = false;
        String target;
        String commandLine;

        int cmdStartIndex;

        if (args[0].equalsIgnoreCase("-g")) {
            if (args.length < 3) {
                sender.sendMessage(new ComponentBuilder("Укажите группу и команду!").color(ChatColor.RED).create());
                return;
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
                BungeeSettings.PROJECT_NAME,
                BungeeSettings.PROXY_NAME,
                target,
                isGroup,
                commandLine
        );

        plugin.getServerManager().getMessageBroker().publishMessage(message);
        sender.sendMessage(new ComponentBuilder("Команда отправлена!").color(ChatColor.GREEN).create());
    }

    @Override
    public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            String prefix = args.length == 1 ? args[0].toLowerCase(Locale.ROOT) : "";
            List<String> options = new ArrayList<>();
            options.add("-g");
            options.add(CommandMessage.BROADCAST_TARGET);
            options.add("all");
            options.add(BungeeSettings.PROXY_NAME);
            for (ServerInfo server : plugin.getProxy().getServers().values()) {
                options.add(server.getName());
            }
            return filterByPrefix(options, prefix);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("-g")) {
            List<String> groups = new ArrayList<>();
            for (ServerGroup group : BungeeSettings.getServerGroups()) {
                groups.add(group.name());
            }
            return filterByPrefix(groups, args[1].toLowerCase(Locale.ROOT));
        }

        return List.of();
    }

    private List<String> filterByPrefix(List<String> options, String prefix) {
        if (prefix.isEmpty()) {
            return options;
        }
        List<String> filtered = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                filtered.add(option);
            }
        }
        return filtered;
    }
}
