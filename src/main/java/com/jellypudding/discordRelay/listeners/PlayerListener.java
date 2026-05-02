package com.jellypudding.discordRelay.listeners;

import com.jellypudding.discordRelay.DiscordRelay;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.awt.Color;
import java.util.Objects;

public class PlayerListener implements Listener {

    private final DiscordRelay plugin;

    public PlayerListener(DiscordRelay plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.sendPlayerEventToDiscord(event.getPlayer().getName(), "joined the game", Color.GREEN);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.sendPlayerEventToDiscord(event.getPlayer().getName(), "left the game", Color.RED);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        String playerName = event.getEntity().getName();
        String deathMessage = event.deathMessage() != null
                ? PlainTextComponentSerializer.plainText().serialize(Objects.requireNonNull(event.deathMessage()))
                : playerName + " died";
        plugin.sendDeathMessageToDiscord(playerName, deathMessage);
    }
}
