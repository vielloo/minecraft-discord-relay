package com.jellypudding.discordRelay;

import com.jellypudding.discordRelay.discord.DiscordBotListener;
import com.jellypudding.discordRelay.listeners.ChatListener;
import com.jellypudding.discordRelay.listeners.PlayerListener;
import com.jellypudding.discordRelay.utils.ChromaTagUtil;
import com.jellypudding.discordRelay.utils.WordFilterUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

public class DiscordRelay extends JavaPlugin {

    private JDA jda;
    private String discordChannelId;
    private boolean isConfigured = false;
    private long startTime;
    private ChromaTagUtil chromaTagUtil;
    private WordFilterUtil wordFilterUtil;

    public boolean isPluginConfigured() {
        return isConfigured;
    }

    public String getDiscordChannelId() {
        return discordChannelId;
    }

    public ChromaTagUtil getChromaTagUtil() {
        return chromaTagUtil;
    }

    public long getStartTime() {
        return startTime;
    }

    public WordFilterUtil getWordFilterUtil() {
        return wordFilterUtil;
    }

    private void loadConfig() {
        reloadConfig();
        FileConfiguration config = getConfig();
        String token = config.getString("discord-bot-token");
        discordChannelId = config.getString("discord-channel-id");

        isConfigured = token != null && !token.equals("YOUR_BOT_TOKEN_HERE") &&
                discordChannelId != null && !discordChannelId.equals("YOUR_CHANNEL_ID_HERE");
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();
        startTime = System.currentTimeMillis();

        chromaTagUtil = new ChromaTagUtil(getLogger());

        boolean filterEnabled = getConfig().getBoolean("word-filter.enabled", true);
        List<String> filterWords = getConfig().getStringList("word-filter.words");
        wordFilterUtil = new WordFilterUtil(filterEnabled, filterWords);

        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> chromaTagUtil.cleanupCache(), 24000L, 24000L);

        if (isConfigured) {
            initialisePlugin(false);
            DiscordRelayAPI.initialize(this);
        } else {
            getLogger().warning("The Discord bot is not yet configured. Please check your DiscordRelay/config.yml file and then use the /discordrelay reload command.");
        }
    }

    private void initialisePlugin(boolean isReload) {
        connectToDiscord(isReload);
        if (isConfigured) {
            registerListeners();
            Objects.requireNonNull(getCommand("discordrelay")).setTabCompleter(this);
        }
    }

    private void registerListeners() {
        HandlerList.unregisterAll((JavaPlugin) this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
    }

    private void connectToDiscord(boolean isReload) {
        try {
            if (jda != null) {
                jda.shutdown();
                jda = null;
            }
            jda = JDABuilder.createDefault(getConfig().getString("discord-bot-token"))
                    .enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MEMBERS)
                    .addEventListeners(new DiscordBotListener(this))
                    .build();
            jda.awaitReady();

            jda.updateCommands().addCommands(
                    Commands.slash("list", "Get a list of online players"),
                    Commands.slash("uptime", "Get the server's uptime"),
                    Commands.slash("tps", "Get the server's TPS (ticks per second)"),
                    Commands.slash("firstseen", "Check when a player first joined the server")
                            .addOption(OptionType.STRING, "name", "Player name", true),
                    Commands.slash("lastseen", "Check when a player was last seen on the server")
                            .addOption(OptionType.STRING, "name", "Player name", true),
                    Commands.slash("timeplayed", "Check how long a player has played on the server")
                            .addOption(OptionType.STRING, "name", "Player name", true),
                    Commands.slash("chatter", "Check how many chat messages a player has sent")
                            .addOption(OptionType.STRING, "name", "Player name", true),
                    Commands.slash("kills", "Check how many kills a player has")
                            .addOption(OptionType.STRING, "name", "Player name", true),
                    Commands.slash("deaths", "Check how many times a player has died")
                            .addOption(OptionType.STRING, "name", "Player name", true),
                    Commands.slash("reputation", "Check a player's reputation")
                            .addOption(OptionType.STRING, "player", "Player name", true)
            ).queue();

            getLogger().info("Discord bot connected successfully!");

            if (!isReload) {
                sendToDiscord("**Server is starting up!**");
                new Metrics(this, 27558);
            }

        } catch (Exception e) {
            getLogger().severe("Failed to connect to Discord. Please check your bot token and try again.");
            isConfigured = false;
        }
    }

    @Override
    public void onDisable() {
        if (jda != null) {
            try {
                sendToDiscord("**Server is shutting down!**");

                jda.shutdownNow();
                try {
                    if (!jda.awaitShutdown(Duration.ofSeconds(2))) {
                        getLogger().warning("JDA did not shut down in time. Forcing shutdown.");
                        jda.shutdown();
                    }
                } catch (InterruptedException e) {
                    getLogger().warning("Interrupted while shutting down JDA. Forcing shutdown.");
                    jda.shutdown();
                    Thread.currentThread().interrupt();
                }
                getLogger().info("Discord bot disconnected successfully.");
            } catch (Exception e) {
                getLogger().warning("Error during JDA shutdown: " + e.getMessage());
            }
        }
        DiscordRelayAPI.shutdown();
        HandlerList.unregisterAll((JavaPlugin) this);
    }

    public void sendPlayerMessageToDiscord(String playerName, String message) {
        if (jda != null) {
            TextChannel channel = jda.getTextChannelById(discordChannelId);
            if (channel != null) {
                String filtered = escapeMarkdown(wordFilterUtil.filterMessage(message));
                String avatarUrl = String.format("https://mc-heads.net/avatar/%s", playerName);
                EmbedBuilder embed = new EmbedBuilder()
                        .setAuthor(playerName, null, avatarUrl)
                        .setDescription(filtered)
                        .setColor(Color.YELLOW);
                channel.sendMessageEmbeds(embed.build()).queue();
            } else {
                getLogger().warning("Discord channel not found!");
            }
        }
    }

    public void sendMeMessageToDiscord(String playerName, String emoteText) {
        if (jda != null) {
            TextChannel channel = jda.getTextChannelById(discordChannelId);
            if (channel != null) {
                String filtered = escapeMarkdown(wordFilterUtil.filterMessage(emoteText));
                String avatarUrl = String.format("https://mc-heads.net/avatar/%s", playerName);
                EmbedBuilder embed = new EmbedBuilder()
                        .setAuthor(playerName, null, avatarUrl)
                        .setDescription("_" + escapeMarkdown(playerName) + " " + filtered + "_")
                        .setColor(new Color(128, 0, 128));
                channel.sendMessageEmbeds(embed.build()).queue();
            } else {
                getLogger().warning("Discord channel not found!");
            }
        }
    }

    private static String escapeMarkdown(String text) {
        if (text == null) return null;
        return text.replace("\\", "\\\\")
                   .replace("*", "\\*")
                   .replace("_", "\\_")
                   .replace("~", "\\~")
                   .replace("`", "\\`")
                   .replace("|", "\\|");
    }

    public void sendPlayerEventToDiscord(String playerName, String action, Color color) {
        if (!isConfigured) return;
        if (jda != null) {
            TextChannel channel = jda.getTextChannelById(discordChannelId);
            if (channel != null) {
                String avatarUrl = String.format("https://mc-heads.net/avatar/%s", playerName);
                EmbedBuilder embed = new EmbedBuilder()
                        .setAuthor(playerName + " " + action, null, avatarUrl)
                        .setColor(color);
                channel.sendMessageEmbeds(embed.build()).queue();
            } else {
                getLogger().warning("Discord channel not found!");
            }
        }
    }

    public void sendDeathMessageToDiscord(String playerName, String deathMessage) {
        if (!isConfigured) return;
        if (jda != null) {
            TextChannel channel = jda.getTextChannelById(discordChannelId);
            if (channel != null) {
                String filtered = wordFilterUtil.filterMessage(deathMessage);
                String avatarUrl = String.format("https://mc-heads.net/avatar/%s", playerName);
                EmbedBuilder embed = new EmbedBuilder()
                        .setAuthor(filtered, null, avatarUrl)
                        .setColor(Color.GRAY);
                channel.sendMessageEmbeds(embed.build()).queue();
            } else {
                getLogger().warning("Discord channel not found!");
            }
        }
    }

    public void sendToDiscord(String message) {
        if (!isConfigured) return;
        if (jda != null) {
            TextChannel channel = jda.getTextChannelById(discordChannelId);
            if (channel != null) {
                channel.sendMessage(message).complete();
            } else {
                getLogger().warning("Discord channel not found!");
            }
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, Command command, @NotNull String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("discordrelay")) return false;

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (sender.hasPermission("discordrelay.reload")) {
                reloadPlugin();
                sender.sendMessage("DiscordRelay plugin reloaded.");
            } else {
                sender.sendMessage("You don't have permission to reload DiscordRelay.");
            }
            return true;
        }

        if (args.length > 2 && args[0].equalsIgnoreCase("send")) {
            if (sender.hasPermission("discordrelay.send")) {
                String colourName = args[1].toLowerCase();
                String fullMessage = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));

                Color colour = parseColour(colourName);
                if (colour == null) {
                    sender.sendMessage("Invalid colour! Available colours: red, green, blue, yellow, orange, purple, pink, grey, white, black");
                    return true;
                }

                String title = "Server Message";
                String message = fullMessage;
                if (fullMessage.contains(":")) {
                    String[] parts = fullMessage.split(":", 2);
                    if (parts.length == 2 && !parts[0].trim().isEmpty() && !parts[1].trim().isEmpty()) {
                        title = parts[0].trim();
                        message = parts[1].trim();
                    }
                }

                relayFormattedMessage(title, message, colour);
                sender.sendMessage("Message sent to Discord with " + colourName + " colour!");
            } else {
                sender.sendMessage("You don't have permission to send messages to Discord.");
            }
            return true;
        }

        sender.sendMessage("Usage: /discordrelay reload | /discordrelay send <colour> <message>");
        return true;
    }

    private void reloadPlugin() {
        loadConfig();
        chromaTagUtil.refresh();

        boolean filterEnabled = getConfig().getBoolean("word-filter.enabled", true);
        List<String> filterWords = getConfig().getStringList("word-filter.words");
        wordFilterUtil = new WordFilterUtil(filterEnabled, filterWords);

        if (isConfigured) {
            initialisePlugin(true);
            if (jda != null) {
                getLogger().info("DiscordRelay plugin reloaded successfully.");
            } else {
                getLogger().warning("Failed to connect to Discord after reload. Please check your bot token and try again.");
            }
        } else {
            if (jda != null) {
                jda.shutdown();
                jda = null;
            }
            HandlerList.unregisterAll((JavaPlugin) this);
            getLogger().warning("Failed to reload: Discord bot is not configured properly. Please check your config.yml file.");
        }
    }

    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("discordrelay")) return null;
        if (args.length == 1) {
            return List.of("reload", "send");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("send")) {
            return List.of("red", "green", "blue", "yellow", "orange", "purple", "pink", "grey", "white", "black");
        }
        return null;
    }

    private Color parseColour(String colourName) {
        return switch (colourName.toLowerCase()) {
            case "red"          -> Color.RED;
            case "green"        -> Color.GREEN;
            case "blue"         -> Color.BLUE;
            case "yellow"       -> Color.YELLOW;
            case "orange"       -> Color.ORANGE;
            case "purple"       -> new Color(128, 0, 128);
            case "pink"         -> Color.PINK;
            case "grey", "gray" -> Color.GRAY;
            case "white"        -> Color.WHITE;
            case "black"        -> Color.BLACK;
            default             -> null;
        };
    }

    public void relayPlayerJoin(String playerName) {
        if (!isConfigured) return;
        sendPlayerEventToDiscord(playerName, "joined the game", Color.GREEN);
    }

    public void relayPlayerLeave(String playerName) {
        if (!isConfigured) return;
        sendPlayerEventToDiscord(playerName, "left the game", Color.RED);
    }

    public void relayPlayerMessage(String playerName, String message) {
        if (!isConfigured) return;
        sendPlayerMessageToDiscord(playerName, message);
    }

    public void relayPlayerDeath(String playerName, String deathMessage) {
        if (!isConfigured) return;
        sendDeathMessageToDiscord(playerName, deathMessage);
    }

    public void relayCustomMessage(String message) {
        if (!isConfigured) return;
        sendToDiscord(message);
    }

    public void relayFormattedMessage(String title, String description, Color colour) {
        if (!isConfigured) return;
        if (jda != null) {
            TextChannel channel = jda.getTextChannelById(discordChannelId);
            if (channel != null) {
                EmbedBuilder embed = new EmbedBuilder();
                if (title != null && !title.trim().isEmpty()) embed.setTitle(title);
                if (description != null && !description.trim().isEmpty()) embed.setDescription(description);
                if (colour != null) embed.setColor(colour);
                channel.sendMessageEmbeds(embed.build()).queue();
            } else {
                getLogger().warning("Discord channel not found!");
            }
        }
    }
}
