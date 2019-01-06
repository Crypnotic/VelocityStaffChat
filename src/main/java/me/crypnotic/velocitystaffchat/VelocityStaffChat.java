package me.crypnotic.velocitystaffchat;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;

import com.google.inject.Inject;
import com.moandjiezana.toml.Toml;
import com.velocitypowered.api.command.Command;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent.ChatResult;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;

import lombok.Getter;
import net.kyori.text.TextComponent;
import net.kyori.text.format.TextColor;
import net.kyori.text.serializer.ComponentSerializers;

@Plugin(id = BuildInfo.PLUGIN_ID, name = BuildInfo.PLUGIN_NAME, version = BuildInfo.PLUGIN_VERSION)
public class VelocityStaffChat implements Command {

    @Getter
    private final ProxyServer proxy;
    @Getter
    private final Logger logger;
    @Getter
    private final Path configPath;

    private Toml toml;
    private String messageFormat;
    private String toggleFormat;
    private Set<UUID> toggledPlayers;

    @Inject
    public VelocityStaffChat(ProxyServer proxy, Logger logger, @DataDirectory Path configPath) {
        this.proxy = proxy;
        this.logger = logger;
        this.configPath = configPath;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        this.toml = loadConfig(configPath);
        if (toml == null) {
            logger.warn("Failed to load config.toml. Shutting down.");
            return;
        }

        this.messageFormat = toml.getString("message-format");
        this.toggleFormat = toml.getString("toggle-format");
        this.toggledPlayers = new HashSet<UUID>();

        proxy.getCommandManager().register(this, "staffchat", "sc");
    }

    private Toml loadConfig(Path path) {
        File folder = path.toFile();
        File file = new File(folder, "config.toml");
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }

        if (!file.exists()) {
            try (InputStream input = getClass().getResourceAsStream("/" + file.getName())) {
                if (input != null) {
                    Files.copy(input, file.toPath());
                } else {
                    file.createNewFile();
                }
            } catch (IOException exception) {
                exception.printStackTrace();
                return null;
            }
        }

        return new Toml().read(file);
    }

    @Override
    public void execute(CommandSource source, String[] args) {
        if (source instanceof Player) {
            Player player = (Player) source;
            if (player.hasPermission("staffchat")) {
                if (args.length == 0) {
                    if (toggledPlayers.contains(player.getUniqueId())) {
                        toggledPlayers.remove(player.getUniqueId());
                        sendToggleMessage(player, false);
                    } else {
                        toggledPlayers.add(player.getUniqueId());
                        sendToggleMessage(player, true);
                    }
                } else {
                    sendStaffMessage(player, player.getCurrentServer().get(), String.join(" ", args));
                }
            } else {
                player.sendMessage(TextComponent.of("Permission denied.").color(TextColor.RED));
            }
        } else {
            source.sendMessage(TextComponent.of("Only players can use this command."));
        }
    }

    @Subscribe
    public void onChat(PlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!toggledPlayers.contains(player.getUniqueId())) {
            return;
        }

        event.setResult(ChatResult.denied());

        sendStaffMessage(player, player.getCurrentServer().get(), event.getMessage());
    }

    private void sendToggleMessage(Player player, boolean state) {
        player.sendMessage(color(toggleFormat.replace("{state}", state ? "enabled" : "disabled")));
    }

    private void sendStaffMessage(Player player, ServerConnection server, String message) {
        proxy.getAllPlayers().stream().filter(target -> target.hasPermission("staffchat")).forEach(target -> {
            target.sendMessage(color(messageFormat.replace("{player}", player.getUsername())
                    .replace("{server}", server != null ? server.getServerInfo().getName() : "N/A")
                    .replace("{message}", message)));
        });
    }

    @SuppressWarnings("deprecation")
    private TextComponent color(String text) {
        return ComponentSerializers.LEGACY.deserialize(text, '&');
    }
}
