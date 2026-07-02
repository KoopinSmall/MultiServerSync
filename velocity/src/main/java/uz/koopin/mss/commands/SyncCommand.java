package uz.koopin.mss.commands;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import uz.koopin.mss.VelocitySettings;
import uz.koopin.mss.VelocitySyncPlugin;
import uz.koopin.mss.messages.CommandMessage;
import uz.koopin.mss.storage.ServerGroup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class SyncCommand implements SimpleCommand {

    private final VelocitySyncPlugin plugin;

    public SyncCommand(VelocitySyncPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        String[] args = invocation.arguments();

        if (args.length < 2) {
            invocation.source().sendMessage(Component.text("Использование: /vsync [-g] <target> <command>").color(NamedTextColor.RED));
            invocation.source().sendMessage(Component.text("  <target> — имя сервера, или '*' / 'all' для всех серверов").color(NamedTextColor.GRAY));
            invocation.source().sendMessage(Component.text("  -g <group> — выполнить на всех серверах группы").color(NamedTextColor.GRAY));
            return;
        }

        boolean isGroup = false;
        String target;
        String commandLine;

        int cmdStartIndex;

        if (args[0].equalsIgnoreCase("-g")) {
            if (args.length < 3) {
                invocation.source().sendMessage(Component.text("Укажите группу и команду!").color(NamedTextColor.RED));
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
                VelocitySettings.PROJECT_NAME,
                VelocitySettings.PROXY_NAME,
                target,
                isGroup,
                commandLine
        );

        plugin.getServerManager().getMessageBroker().publishMessage(message);
        invocation.source().sendMessage(Component.text("Команда отправлена!").color(NamedTextColor.GREEN));
    }

    @Override
    public List<String> suggest(final Invocation invocation) {
        String[] args = invocation.arguments();

        if (args.length <= 1) {
            String prefix = args.length == 1 ? args[0].toLowerCase(Locale.ROOT) : "";
            List<String> options = new ArrayList<>();
            options.add("-g");
            options.add(CommandMessage.BROADCAST_TARGET);
            options.add("all");
            options.add(VelocitySettings.PROXY_NAME);
            for (RegisteredServer server : plugin.getServer().getAllServers()) {
                options.add(server.getServerInfo().getName());
            }
            return filterByPrefix(options, prefix);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("-g")) {
            List<String> groups = new ArrayList<>();
            for (ServerGroup group : VelocitySettings.getServerGroups()) {
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

    @Override
    public boolean hasPermission(final Invocation invocation) {
        return invocation.source().hasPermission("mss.command.sync");
    }
}