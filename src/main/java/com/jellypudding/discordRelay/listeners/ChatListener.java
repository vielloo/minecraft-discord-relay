package com.jellypudding.discordRelay.listeners;

import com.jellypudding.discordRelay.DiscordRelay;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public class ChatListener implements Listener {

    private final DiscordRelay plugin;

    public ChatListener(DiscordRelay plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncChatEvent event) {
        if (!plugin.isPluginConfigured()) return;
        if (event.isCancelled()) return;

        if (event.isAsynchronous()) {
            String playerName = event.getPlayer().getName();
            String message = PlainTextComponentSerializer.plainText().serialize(event.message());
            plugin.sendPlayerMessageToDiscord(playerName, message);
        } else {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                String playerName = event.getPlayer().getName();
                String message = PlainTextComponentSerializer.plainText().serialize(event.message());
                plugin.sendPlayerMessageToDiscord(playerName, message);
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerMeCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.isPluginConfigured()) return;
        if (event.isCancelled()) return;

        String raw = event.getMessage();
        if (!raw.toLowerCase().startsWith("/me ")) return;

        String emoteText = raw.substring(4).trim();
        if (emoteText.isEmpty()) return;

        String playerName = event.getPlayer().getName();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () ->
                plugin.sendMeMessageToDiscord(playerName, emoteText));
    }
}
