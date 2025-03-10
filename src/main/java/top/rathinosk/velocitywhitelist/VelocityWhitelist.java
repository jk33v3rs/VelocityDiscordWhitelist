package top.rathinosk.velocitywhitelist;

import com.google.inject.Inject;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.ResultedEvent.ComponentResult;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bstats.velocity.Metrics;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.*;
import java.util.Properties;
import java.util.UUID;

/**
 * The main class for the VelocityWhitelist plugin.
 * This class handles plugin initialization, command registration,
 * configuration loading, and whitelist management.
 */
@Plugin(
        id = BuildConstants.ID,
        name = BuildConstants.NAME,
        version = BuildConstants.VERSION,
        url = BuildConstants.URL,
        description = BuildConstants.DESCRIPTION,
        authors = BuildConstants.AUTHORS
)
public class VelocityWhitelist {
    public static Connection connection;
    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private final Path configFile;
    private Properties config;
    private final Metrics.Factory metricsFactory;

    /**
     * Constructor for the VelocityWhitelist plugin.
     *
     * @param server         The ProxyServer instance.
     * @param logger         The Logger instance.
     * @param dataDirectory  The plugin's data directory.
     * @param metricsFactory The Metrics.Factory instance.
     */
    @Inject
    public VelocityWhitelist(
            ProxyServer server,
            Logger logger,
            @DataDirectory Path dataDirectory,
            Metrics.Factory metricsFactory
    ) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.configFile = dataDirectory.resolve("config.properties");
        this.metricsFactory = metricsFactory;
        new org.mariadb.jdbc.Driver();
    }

    /**
     * Closes the database connection.
     * This method is synchronized to prevent concurrent access issues.
     */
    public static synchronized void closeConnection() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new RuntimeException("Error while closing connection", e);
        }
    }

    /**
     * Event listener for the ProxyInitializeEvent.
     * This method is called when the proxy server is initialized.
     * It registers commands, loads the configuration, and creates the database table.
     *
     * @param event The ProxyInitializeEvent.
     */
    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        try {
            int pluginId = 25057;
            Metrics metrics = metricsFactory.make(this, pluginId);

            CommandManager commandManager = server.getCommandManager();
            CommandMeta commandMeta = commandManager.metaBuilder("mywl")
                    .plugin(this)
                    .build();
            BrigadierCommand commandToRegister = this.createBrigadierCommand();
            commandManager.register(commandMeta, commandToRegister);

            this.saveDefaultConfig();
            this.config = loadConfig();

            if (Boolean.parseBoolean(config.getProperty("enabled"))) {
                server.getScheduler().buildTask(this, this::createDatabaseTable).schedule();
            }

            /*** SAMPLE LOGO 
             *     __   __ __      __  _    
             *     \ \ / / \ \    / / | |     Velocity Whitelist
             *      \ V /   \ \/\/ /  | |__   v 1.0.0
             *       \_/     \_/\_/   |____|  Built on 2021-09-30
             *                              
             */

            logger.info(" __   __ __      __  _    ");
            logger.info(" \\\\ \\ / / \\\\ \\    / / | |     {} ", BuildConstants.NAME);
            logger.info("  \\ V /   \\\\ \\/\\/ /  | |__   v {} ", BuildConstants.VERSION);
            logger.info("   \\_/     \\_/\\_/   |____|  Built on {} ", BuildConstants.BUILD_DATE);
            logger.info(" ");
            logger.info("{} {} loaded successfully!", BuildConstants.NAME, BuildConstants.VERSION);
        } catch (Exception e) {
            logger.error("Error during plugin initialization", e);
        }
    }

    /**
     * Creates the database table if it doesn't exist.
     * This method opens a connection to the database, executes the SQL query,
     * and then closes the connection.
     */
    private void createDatabaseTable() {
        openConnection();

        try (PreparedStatement sql = connection.prepareStatement(
                "CREATE TABLE IF NOT EXISTS `" + config.getProperty("table") + "` (`UUID` varchar(100), `user` varchar(100)) ;")) {
            sql.execute();
        } catch (SQLException e) {
            logger.error("Error while creating database table", e);
        } finally {
            closeConnection();
        }
    }

    /**
     * Saves the default configuration file if it doesn't exist.
     * This method creates the data directory if it doesn't exist,
     * and then writes the default configuration content to the config file.
     */
    public void saveDefaultConfig() {
        if (Files.notExists(dataDirectory)) {
            try {
                Files.createDirectories(dataDirectory);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        if (Files.notExists(configFile)) {
            String defaultConfigContent = """
                    # Whitelist Status
                    enabled: false

                    # MySQL settings
                    host: localhost
                    user: username
                    password: strongpassword
                    database: velocity
                    port: 3306
                    table: g_whitelist
                                        
                    # Kick message
                    message: Sorry, you are not in the whitelist.
                    """;
            try {
                Files.write(configFile, defaultConfigContent.getBytes(), StandardOpenOption.CREATE);
            } catch (IOException e) {
                throw new RuntimeException("Error while saving default configuration", e);
            }
        }

    }

    /**
     * Loads the configuration from the config file.
     * This method reads the config file and loads the properties into a Properties object.
     *
     * @return The Properties object containing the configuration.
     */
    private Properties loadConfig() {
        Properties properties = new Properties();

        if (Files.exists(configFile)) {
            try (InputStream input = Files.newInputStream(configFile, StandardOpenOption.READ)) {
                InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8);
                properties.load(reader);
            } catch (IOException e) {
                throw new RuntimeException("Error while loading configuration", e);
            }
        }

        return properties;
    }

    /**
     * Saves the configuration to the config file.
     * This method writes the properties to the config file.
     * @param properties The Properties object containing the configuration to save.
     */
    private void saveConfig(Properties properties) {
       try (OutputStream output = Files.newOutputStream(configFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
           OutputStreamWriter writer = new OutputStreamWriter(output,StandardCharsets.UTF_8);
           properties.store(writer, "Updated Configuration");
       } catch (IOException e) {
           throw new RuntimeException("Error while saving configuration", e);
       }
   }

    /**
     * Opens a connection to the MySQL database.
     * This method uses the configuration properties to establish a connection.
     * This method is synchronized to prevent concurrent access issues.
     */
    public synchronized void openConnection() {
        try {
            connection = DriverManager.getConnection("jdbc:mysql://" + config.getProperty("host") + ":" + config.getProperty("port") + "/" + config.getProperty("database") + "?useSSL=false", config.getProperty("user"), config.getProperty("password"));
        } catch (SQLException e) {
            throw new RuntimeException("Error while opening connection", e);
        }
    }

    /**
     * Adds a player to the whitelist.
     * This method opens a connection to the database, executes the SQL query,
     * and then closes the connection.
     *
     * @param source The CommandSource who executed the command.
     * @param player The name of the player to add to the whitelist.
     */
    public void addWhitelist(CommandSource source, String player) {
        this.openConnection();

        try {
            PreparedStatement sql = connection.prepareStatement("SELECT * FROM `" + config.getProperty("table") + "` WHERE `user`=?;");
            sql.setString(1, player);
            ResultSet rs = sql.executeQuery();
            if (!rs.next()) {
                PreparedStatement sql1 = connection.prepareStatement("INSERT INTO `" + config.getProperty("table") + "` (`user`) VALUES (?);");
                sql1.setString(1, player);
                sql1.execute();
                sql1.close();
            }

            rs.close();
            sql.close();
            source.sendMessage(Component.text(player + " is now whitelisted.", NamedTextColor.GREEN));
        } catch (SQLException e) {
            throw new RuntimeException("Error while add whitelist", e);
        } finally {
            closeConnection();
        }

    }

    /**
     * Deletes a player from the whitelist.
     * This method opens a connection to the database, executes the SQL query,
     * and then closes the connection.
     *
     * @param source The CommandSource who executed the command.
     * @param player The name of the player to delete from the whitelist.
     */
    public void delWhitelist(CommandSource source, String player) {
        this.openConnection();

        try {
            PreparedStatement sql = connection.prepareStatement("DELETE FROM `" + config.getProperty("table") + "` WHERE `user`=?;");
            sql.setString(1, player);
            sql.execute();
            sql.close();
            source.sendMessage(Component.text(player + " is no longer whitelisted.", NamedTextColor.AQUA));
        } catch (SQLException e) {
            throw new RuntimeException("Error while add whitelist", e);
        } finally {
            closeConnection();
        }

    }

    /**
     * Creates a BrigadierCommand for the whitelist command.
     * This method defines the command structure and its execution logic.
     *
     * @return The BrigadierCommand instance.
     */
    public BrigadierCommand createBrigadierCommand() {
        LiteralCommandNode<CommandSource> helloNode = BrigadierCommand.literalArgumentBuilder("mywl")
                .requires(source -> source.hasPermission("mysqlwhitelist"))
                .executes(context -> {
                    CommandSource source = context.getSource();
                    sendUsageMessage(source, "all");
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierCommand.requiredArgumentBuilder("argument", StringArgumentType.greedyString())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("add").suggest("del");
//                                    .suggest("on").suggest("off");
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    CommandSource source = context.getSource();
                                    String[] arguments = context.getArgument("argument", String.class).split(" ");

                                    // Check if the command has only one argument
                                    if (arguments.length == 1) {
                                        
                                        // Handle 'add' command with missing player name
                                        if (arguments[0].equals("add")) {
                                            sendUsageMessage(source, "add");
                                        
                                        // Handle 'del' command with missing player name
                                        } else if (arguments[0].equals("del")) {
                                            sendUsageMessage(source, "del");
                                        
                                        // Handle 'on' command to enable the whitelist
                                        } else if (arguments[0].equals("on")) {
                                            config.setProperty("enabled", String.valueOf(true));
                                            saveConfig(config);
                                            source.sendMessage(Component.text("Whitelist enabled", NamedTextColor.GREEN));
                                        
                                        // Handle 'off' command to disable the whitelist
                                        } else if (arguments[0].equals("off")) {
                                            config.setProperty("enabled", String.valueOf(false));
                                            saveConfig(config);
                                            source.sendMessage(Component.text("Whitelist disabled", NamedTextColor.AQUA));
                                        
                                        // Handle unknown command
                                        } else {
                                            sendUsageMessage(source, "all");
                                        }

                                      // Check if the command has two arguments
                                    } else if (arguments.length == 2) {
                                        
                                        // Handle 'add' command with player name
                                        if (arguments[0].equals("add")) {
                                            addWhitelist(source, arguments[1]);
                                        
                                        // Handle 'del' command with player name
                                        } else if (arguments[0].equals("del")) {
                                            delWhitelist(source, arguments[1]);
                                        
                                        // Handle unknown command
                                        } else {
                                            sendUsageMessage(source, "all");
                                        }

                                    // Handle incorrect number of arguments
                                    } else {
                                        sendUsageMessage(source, "all");
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })
                )
                .build();

        return new BrigadierCommand(helloNode);
    }

    /**
     * Sends a usage message to the command source.
     * This method constructs a message based on the specified subcommand.
     *
     * @param source      The CommandSource to send the message to.
     * @param subcommand  The subcommand for which to display usage.
     */
    public void sendUsageMessage(CommandSource source, String subcommand) {
        String usage = switch (subcommand) {
            case "all" -> "/mywl add/del/on/off <player>";
//            case "all" -> "/mywl add/del <player>";
            case "add" -> "/mywl add <player>";
            case "del" -> "/mywl del <player>";
            default -> "";
        };

        if (!usage.isEmpty()) {
            Component message = Component.text("Usage:", NamedTextColor.RED)
                    .append(Component.text(" " + usage, NamedTextColor.WHITE));
            source.sendMessage(message);
        }
    }

    /**
     * Checks if a player is whitelisted.
     * This method queries the database to determine if the player is in the whitelist.
     *
     * @param player The Player to check.
     * @return True if the player is whitelisted, false otherwise.
     */
    public boolean isWhitelisted(Player player) {
        try {
            openConnection();

            UUID uuid = player.getUniqueId();
            String tableName = config.getProperty("table");

            // Check if the player is already in the whitelist
            String selectQuery = "SELECT * FROM `" + tableName + "` WHERE `UUID`=?";
            try (PreparedStatement selectStatement = connection.prepareStatement(selectQuery)) {
                selectStatement.setString(1, uuid.toString());
                ResultSet rs = selectStatement.executeQuery();

                if (rs.next()) {
                    // Player is already in the whitelist, update username if necessary
                    String updateQuery = "UPDATE `" + tableName + "` SET `user`=? WHERE `UUID`=?";
                    try (PreparedStatement updateStatement = connection.prepareStatement(updateQuery)) {
                        updateStatement.setString(1, player.getUsername());
                        updateStatement.setString(2, uuid.toString());
                        updateStatement.executeUpdate();
                    }
                    return true;
                }
            }

            // Check if there is an entry for the player without UUID
            String selectNullQuery = "SELECT * FROM `" + tableName + "` WHERE `user`=? AND `UUID` IS NULL";
            try (PreparedStatement selectNullStatement = connection.prepareStatement(selectNullQuery)) {
                selectNullStatement.setString(1, player.getUsername());
                ResultSet rs2 = selectNullStatement.executeQuery();

                if (!rs2.next()) {
                    // Player is not in the whitelist
                    return false;
                }

                // Update the entry with the player's UUID
                String updateUuidQuery = "UPDATE `" + tableName + "` SET `UUID`=? WHERE `user`=?";
                try (PreparedStatement updateUuidStatement = connection.prepareStatement(updateUuidQuery)) {
                    updateUuidStatement.setString(1, uuid.toString());
                    updateUuidStatement.setString(2, player.getUsername());
                    updateUuidStatement.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error while checking whitelist", e);
        } finally {
            closeConnection();
        }

        return true;
    }

    /**
     * Event listener for the LoginEvent.
     * This method is called when a player attempts to log in.
     * It checks if the player is whitelisted and denies the connection if they are not.
     *
     * @param event The LoginEvent.
     */
    @Subscribe
    public void onPlayerLogin(LoginEvent event) {
        // Validate that the player object is not null
        Player player = event.getPlayer();
        if (player == null) {
            logger.error("LoginEvent triggered with a null player.");
            return;
        }

        // Check if the whitelist is enabled and if the player is not whitelisted
        if (Boolean.parseBoolean(config.getProperty("enabled")) && !this.isWhitelisted(player)) {
            Component kickMessage = Component.text(config.getProperty("message"));
            event.setResult(ComponentResult.denied(kickMessage));
        }
    }
}
