# VelocityDiscordWhitelist v1.0.11

A powerful Velocity Proxy plugin for managing Minecraft whitelists with Discord integration, advanced player verification, and comprehensive reward systems.

**Author:** jk33v3rs based on the plugin VelocityWhitelist by Rathinosk  
**License:** MIT License  
**Dependencies:** None - All dependencies are bundled within the plugin  
**Disclaimer:** AI tools were used in the IDE during development with supervised access to code. Please report issues via GitHub. Pull requests welcome.

## 🎯 For Players

### Getting Whitelisted

1. **Join our Discord server** (server admins will provide the link)
2. **Use the `/mc <your_minecraft_username>` command** in Discord
3. **You'll receive a 6-digit verification code** (valid for 5 minutes)
4. **Join the Minecraft server** and use `/verify <code>` to complete verification
5. **You're now whitelisted!** Enjoy the server

### Player Commands

- `/verify <code>` - Complete Discord verification with your 6-digit code
- `/rank` - View your current rank progress and statistics  
- `/xpchart` - View XP progression charts and requirements

### Features for Players

- **Quick verification process** - Get whitelisted in under 2 minutes
- **Rank progression system** - Advance through ranks with playtime and achievements
- **Discord integration** - Manage your whitelist status directly from Discord
- **Bedrock support** - Geyser/Floodgate compatible for Bedrock players

---

## 🛠️ For Server Administrators

### Installation Requirements

- **Java 17 or higher**
- **Velocity 3.4.0 or higher**
- **Database**: MariaDB 10.5+, MySQL 8.0+, PostgreSQL 12+, or SQLite
- **Discord bot token** (for Discord integration)

### Quick Setup

1. **Download** VelocityDiscordWhitelist from [Releases](https://github.com/jk33v3rs/VelocityDiscordWhitelist/releases)
2. **Place the JAR** in your Velocity `plugins` directory
3. **Start** the server once to generate configuration files
4. **Configure** your database and Discord bot (see Configuration section)
5. **Restart** the server

### Key Features

- **Multi-database Support**: MySQL/MariaDB, PostgreSQL, SQLite with HikariCP connection pooling
- **Discord Integration**: Player self-whitelisting via Discord bot commands
- **Purgatory System**: Secure verification process restricting new players to hub server
- **Rewards System**: Economy and permission rewards via Vault and LuckPerms integration
- **Performance Optimized**: Async operations, connection pooling, graceful error handling
- **Comprehensive Logging**: Debug mode with detailed logging for troubleshooting

### Essential Configuration

#### Database Setup (`config.yaml`)

```yaml
database:
  type: "mariadb"  # mariadb, mysql, postgresql, sqlite
  host: "localhost"
  port: 3306
  database: "whitelist"
  username: "your_username"
  password: "your_password"
  table: "whitelist"
```

#### Discord Bot Setup (`config.yaml`)

```yaml
discord:
  enabled: true
  token: "your_discord_bot_token"
  guild_id: "your_discord_server_id"
  channel_id: "verification_channel_id"
```

#### Required Discord Bot Permissions

- Send Messages
- Use Slash Commands
- Manage Roles (for rank integration)
- View Channels

### Admin Commands

- `/vwl add <player>` - Add player to whitelist
- `/vwl del <player>` - Remove player from whitelist  
- `/vwl list [search]` - List whitelisted players
- `/vwl enable/disable` - Toggle whitelist
- `/vwl reload` - Reload configuration

### Permissions

- `velocitywhitelist` - Basic whitelist commands
- `velocitywhitelist.admin` - Admin commands

---

## 🔧 Building from Source

### Prerequisites

- **Java 17+ JDK** (OpenJDK or Oracle JDK)
- **Git** for cloning the repository

### Compile Instructions

```bash
# Clone the repository
git clone https://github.com/jk33v3rs/VelocityDiscordWhitelist.git
cd VelocityDiscordWhitelist

# Build the plugin (creates shaded JAR with all dependencies)
./gradlew shadowJar

# The compiled JAR will be in build/libs/VelocityDiscordWhitelist-1.0.11-all.jar
```

### Development Commands

```bash
# Clean previous builds
./gradlew clean

# Compile without creating JAR
./gradlew build

# Run tests
./gradlew test

# Build without daemon (faster for single builds)
./gradlew shadowJar --no-daemon
```

### Build Output

- **Production JAR**: `build/libs/VelocityDiscordWhitelist-1.0.11-all.jar` (~12MB optimized with JDA)
- **Dev JAR**: `build/libs/VelocityDiscordWhitelist-1.0.11.jar` (without dependencies)

---

## 📚 API Documentation

### Maven Dependency

```xml
<dependency>
    <groupId>top.jk33v3rs</groupId>
    <artifactId>VelocityDiscordWhitelist</artifactId>
    <version>1.0.11</version>
    <scope>provided</scope>
</dependency>
```

### Gradle Dependency

```gradle
dependencies {
    compileOnly 'top.jk33v3rs:VelocityDiscordWhitelist:1.0.11'
}
```

### Core API Classes

#### VelocityDiscordWhitelist (Main Plugin)

```java
@Plugin(id = "velocitydiscordwhitelist", name = "VelocityDiscordWhitelist", version = "1.0.11")
public class VelocityDiscordWhitelist {
    // Access plugin components
    public SQLHandler getSqlHandler()
    public DiscordBotHandler getDiscordBotHandler()
    public RewardsHandler getRewardsHandler()
    public EnhancedPurgatoryManager getPurgatoryManager()
    public LuckPermsIntegration getLuckPermsIntegration()
    public VaultIntegration getVaultIntegration()
}
```

#### SQLHandler (Database Operations)

```java
public class SQLHandler {
    // Player whitelist operations
    CompletableFuture<Boolean> isPlayerWhitelisted(UUID playerUuid)
    CompletableFuture<Boolean> addPlayerToWhitelist(UUID uuid, String name, String discordId, String discordName)
    CompletableFuture<Boolean> removePlayerFromWhitelist(UUID playerUuid)
    
    // Player data retrieval
    CompletableFuture<List<PlayerInfo>> getWhitelistedPlayers()
    CompletableFuture<Optional<PlayerInfo>> getPlayerInfo(UUID playerUuid)
}
```

#### RewardsHandler (Rank System)

```java
public class RewardsHandler {
    CompletableFuture<Optional<PlayerRank>> getPlayerRank(UUID playerUuid)
    CompletableFuture<Boolean> updatePlayerRank(UUID playerUuid, int mainRank, int subRank)
    long calculateRequiredPlayTime(int mainRank, int subRank)
    int calculateRequiredAchievements(int mainRank, int subRank)
    Optional<RankDefinition> getNextRank(int currentMainRank, int currentSubRank)
}
```

### API Usage Examples

**Get plugin instance:**

```java
@Subscribe
public void onProxyInitialization(ProxyInitializeEvent event) {
    VelocityDiscordWhitelist plugin = (VelocityDiscordWhitelist) 
        proxyServer.getPluginManager().getPlugin("velocitydiscordwhitelist").get().getInstance().get();
}
```

**Check whitelist status:**

```java
SQLHandler sqlHandler = plugin.getSqlHandler();
CompletableFuture<Boolean> isWhitelisted = sqlHandler.isPlayerWhitelisted(playerUuid);
isWhitelisted.thenAccept(whitelisted -> {
    if (whitelisted) {
        player.sendMessage(Component.text("Welcome back!"));
    } else {
        player.disconnect(Component.text("You're not whitelisted!"));
    }
});
```

**Add player to whitelist:**

```java
CompletableFuture<Boolean> result = sqlHandler.addPlayerToWhitelist(
    playerUuid, 
    playerName, 
    discordUserId, 
    discordUsername
);
result.thenAccept(success -> {
    if (success) {
        logger.info("Player {} added to whitelist", playerName);
    }
});
```

**Get player rank:**

```java
RewardsHandler rewardsHandler = plugin.getRewardsHandler();
CompletableFuture<Optional<PlayerRank>> rankFuture = rewardsHandler.getPlayerRank(playerUuid);
rankFuture.thenAccept(optionalRank -> {
    if (optionalRank.isPresent()) {
        PlayerRank rank = optionalRank.get();
        player.sendMessage(Component.text("Your rank: " + rank.getMainRank() + "." + rank.getSubRank()));
    }
});
```

**LuckPerms integration:**

```java
LuckPermsIntegration luckPerms = plugin.getLuckPermsIntegration();
if (luckPerms.isAvailable()) {
    CompletableFuture<Boolean> result = luckPerms.addPlayerToGroup(playerUuid, "verified");
    result.thenAccept(success -> {
        if (success) {
            logger.info("Added player to verified group");
        }
    });
}
```

### Event System

```java
// Listen for whitelist events (custom events fired by the plugin)
@Subscribe
public void onPlayerWhitelistAdd(PlayerWhitelistAddEvent event) {
    UUID playerUuid = event.getPlayerUuid();
    String playerName = event.getPlayerName();
    // Handle whitelist addition
}
```

---

## 🗄️ Database Schema

### Auto-Generated Tables

**`whitelist` (Primary whitelist data)**

```sql
CREATE TABLE whitelist (
    uuid VARCHAR(36) PRIMARY KEY,
    name VARCHAR(16) NOT NULL,
    discord_id VARCHAR(20),
    discord_name VARCHAR(32),
    date_added TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    added_by VARCHAR(36)
);
```

**`rank_definitions` (Rank configuration)**

```sql
CREATE TABLE rank_definitions (
    id INT PRIMARY KEY AUTO_INCREMENT,
    main_rank INT NOT NULL,
    sub_rank INT NOT NULL,
    rank_name VARCHAR(50),
    required_playtime BIGINT,
    required_achievements INT,
    permissions TEXT,
    rewards TEXT
);
```

**`player_ranks` (Individual player progression)**

```sql
CREATE TABLE player_ranks (
    uuid VARCHAR(36) PRIMARY KEY,
    main_rank INT DEFAULT 1,
    sub_rank INT DEFAULT 0,
    playtime BIGINT DEFAULT 0,
    achievements INT DEFAULT 0,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

## 🐛 Troubleshooting

### Common Issues

**Plugin fails to load:**

- Check Java version: `java -version` (requires 17+)
- Check Velocity version in console (requires 3.4.0+)
- Verify JAR file isn't corrupted

**Database connection errors:**

```yaml
# Verify database settings in config.yaml
database:
  type: "mariadb"
  host: "localhost"  # Ensure database server is accessible
  port: 3306         # Correct port
  database: "whitelist"  # Database exists
  username: "user"   # Valid credentials
  password: "pass"
```

**Discord bot not responding:**

- Verify bot token in `config.yaml`
- Check bot has required permissions in Discord server
- Ensure bot is online and added to the correct server

**Commands not working:**

- Check console for plugin load errors
- Verify player has required permissions
- Enable debug mode for detailed logging

### Debug Mode

```yaml
debug: true
```

Enables detailed logging for troubleshooting database operations, Discord interactions, and command execution.

### Log Locations

- **Velocity logs**: `logs/latest.log`
- **Plugin-specific logs**: Look for `[VelocityDiscordWhitelist]` entries

---

## 📋 Dependencies & Size Optimization

### Major Dependencies

- **JDA 5.0.0-beta.21** - Discord integration (~12MB optimized)
- **HikariCP 5.1.0** - Database connection pooling (~130KB)
- **MariaDB Java Client 3.3.3** - Database driver (~600KB)
- **SnakeYAML 2.2** - Configuration parsing (~300KB)

### JDA Optimization Strategy

The plugin uses an optimized JDA configuration to minimize resource usage:

- **Light Bot Mode**: Uses `JDABuilder.createLight()` instead of full bot features
- **Minimal Gateway Intents**: Only essential message and guild intents enabled
- **Disabled Caches**: Audio, voice, emoji, presence caches disabled
- **Excluded Components**: Audio libraries, voice natives, and unused JDA modules excluded
- **No Member Caching**: Reduces memory footprint for large Discord servers

**Result**: ~40% reduction in JDA footprint while maintaining full whitelist functionality.

---

## 🤝 Contributing

### Development Setup

```bash
git clone https://github.com/jk33v3rs/VelocityDiscordWhitelist.git
cd VelocityDiscordWhitelist
./gradlew build
```

### Code Standards

- Follow existing naming conventions (camelCase)
- Add JavaDoc headers to all public methods
- Include error handling with try-catch blocks
- Write unit tests for new features

### Pull Request Process

1. Fork the repository
2. Create feature branch: `git checkout -b feature/amazing-feature`
3. Make changes with proper testing
4. Update documentation if needed
5. Submit pull request with clear description

---

## 📄 License

This project is licensed under the MIT License. Portions of code are based on VelocityWhitelist by Rathinosk, used under MIT license.

---

## 🙏 Acknowledgments

- **Rathinosk** for the original VelocityWhitelist plugin foundation
- **Velocity** team for the excellent proxy platform and APIs
- **JDA** library maintainers for Discord integration capabilities
- **HikariCP** team for high-performance connection pooling

---

## 📞 Support

- **GitHub Issues**: [Report bugs and request features](https://github.com/jk33v3rs/VelocityDiscordWhitelist/issues)
- **Pull Requests**: Contributions are welcome!
- **Documentation**: Check the `references/` folder for detailed technical documentation
