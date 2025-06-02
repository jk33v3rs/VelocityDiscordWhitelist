# VelocityDiscordWhitelist v1.0.0

A powerful Velocity Proxy plugin for managing Minecraft whitelists with Discord integration, advanced player verification, and comprehensive reward systems.

**Author:** jk33v3rs based on the plugin VelocityWhitelist by Rathinosk  
**License:** MIT License  
**Dependencies:** Requires [Spicord](https://github.com/Spicord/Spicord) for Discord functionality  
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
- **[Spicord 5.7.0+](https://github.com/Spicord/Spicord)** (provides JDA Discord library)
- **Discord bot token** (for Discord integration)

### Quick Setup
1. **Install Spicord** first - [Download here](https://github.com/Spicord/Spicord/releases)
2. **Download** VelocityDiscordWhitelist from [Releases](https://github.com/jk33v3rs/VelocityDiscordWhitelist/releases)
3. **Place both JARs** in your Velocity `plugins` directory
4. **Start** the server once to generate configuration files
5. **Configure** your database and Discord bot (see Configuration section)
6. **Restart** the server

### Key Features
- **Multi-database Support**: MySQL/MariaDB, PostgreSQL, SQLite with HikariCP connection pooling
- **Discord Integration**: Player self-whitelisting via Discord bot commands
- **Purgatory System**: Secure verification process restricting new players to hub server
- **Rewards System**: Economy and permission rewards via Vault and LuckPerms integration
- **Performance Optimized**: Async operations, connection pooling, graceful error handling
- **Comprehensive Logging**: Debug mode with detailed logging for troubleshooting
5. **Restart** your Velocity proxy

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

# The compiled JAR will be in build/libs/VelocityDiscordWhitelist-1.0.0-all.jar
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
- **Production JAR**: `build/libs/VelocityDiscordWhitelist-1.0.0-all.jar` (~20MB with JDA)
- **Dev JAR**: `build/libs/VelocityDiscordWhitelist-1.0.0.jar` (without dependencies)

---

## 📚 API Documentation

### Maven Dependency
```xml
<dependency>
    <groupId>top.jk33v3rs</groupId>
    <artifactId>VelocityDiscordWhitelist</artifactId>
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>
```

### Gradle Dependency
```gradle
dependencies {
    compileOnly 'top.jk33v3rs:VelocityDiscordWhitelist:1.0.0'
}
```

### Core API Classes

#### VelocityDiscordWhitelist (Main Plugin)
```java
@Plugin(id = "velocitydiscordwhitelist", name = "VelocityDiscordWhitelist", version = "1.0.0")
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
- **JDA 5.0.0-beta.21** - Discord integration (~15MB)
- **HikariCP 5.1.0** - Database connection pooling (~130KB)
- **MariaDB Java Client 3.3.3** - Database driver (~600KB)
- **SnakeYAML 2.2** - Configuration parsing (~300KB)

### Size Reduction Options
The large JAR size (~20MB) is primarily due to JDA (Java Discord API). Consider:

1. **Runtime dependency loading** (similar to Spicord's approach)
2. **Discord webhook integration** instead of full bot API
3. **Modular builds** with optional Discord features

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
```xml
<dependency>
    <groupId>top.jk33v3rs</groupId>
    <artifactId>VelocityDiscordWhitelist</artifactId>
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>
```

#### Gradle Dependency
```gradle
dependencies {
    compileOnly 'top.jk33v3rs:VelocityDiscordWhitelist:1.0.0'
}
```

#### API Examples

**Check if player is whitelisted:**
```java
VelocityDiscordWhitelist plugin = // get plugin instance
SQLHandler sqlHandler = plugin.getSqlHandler();

CompletableFuture<Boolean> isWhitelisted = sqlHandler.isPlayerWhitelisted(player);
isWhitelisted.thenAccept(whitelisted -> {
    if (whitelisted) {
        // Player is whitelisted
    }
});
```

**Add player to whitelist:**
```java
CompletableFuture<Boolean> success = sqlHandler.addPlayerToWhitelist(
    playerUuid, 
    playerName, 
    discordUserId, 
    discordUsername
);
```

**Get player rank information:**
```java
RewardsHandler rewardsHandler = plugin.getRewardsHandler();
CompletableFuture<Optional<PlayerRank>> rankInfo = rewardsHandler.getPlayerRank(playerUuid);
```

**Integration with LuckPerms:**
```java
LuckPermsIntegration luckPerms = plugin.getLuckPermsIntegration();
if (luckPerms.isAvailable()) {
    CompletableFuture<Boolean> success = luckPerms.addPlayerToGroup(playerUuid, "member");
}
```

### Building from Source

#### Prerequisites
- Java 17 JDK
- Git

#### Compile Instructions
```bash
# Clone the repository
git clone https://github.com/jk33v3rs/VelocityDiscordWhitelist.git
cd VelocityDiscordWhitelist

# Build the plugin
./gradlew shadowJar

# The compiled JAR will be in build/libs/
```

#### Development Setup
```bash
# Clean and build
./gradlew clean build

# Run tests
./gradlew test

# Generate development JAR
./gradlew shadowJar
```

### Plugin Architecture

#### Core Components
- **VelocityDiscordWhitelist**: Main plugin class and initialization
- **SQLHandler**: Database operations with connection pooling
- **DiscordBotHandler**: Discord bot integration and command handling  
- **EnhancedPurgatoryManager**: Verification session management
- **RewardsHandler**: Rank progression and reward distribution
- **BrigadierCommandHandler**: Minecraft command registration

#### Integration Modules
- **LuckPermsIntegration**: Permission group management
- **VaultIntegration**: Economy integration for rewards
- **TNEIntegration**: The New Economy support

#### Database Schema
The plugin automatically creates the following tables:
- `whitelist`: Core whitelist data with player UUIDs and Discord associations
- `rank_definitions`: Rank system configuration and requirements
- `player_ranks`: Individual player rank progression data
- `rank_history`: Historical rank progression tracking

### Troubleshooting

#### Common Issues
1. **Plugin fails to start**: Check Java version (requires 17+) and Velocity version (requires 3.4.0+)
2. **Database connection errors**: Verify database credentials and ensure the database exists
3. **Discord bot not responding**: Check bot token and permissions
4. **Commands not working**: Ensure the plugin loaded successfully (check console logs)

#### Debug Mode
Enable debug mode in `config.yaml`:
```yaml
debug: true
```
This provides detailed logging for troubleshooting issues.

#### Getting Help
- **GitHub Issues**: [Report bugs and request features](https://github.com/jk33v3rs/VelocityDiscordWhitelist/issues)
- **Pull Requests**: Contributions are welcome!
- **Discord**: Check with your server administrators for support

### Contributing

We welcome contributions! Please:
1. Fork the repository
2. Create a feature branch
3. Make your changes with proper testing
4. Submit a pull request with a clear description

### License

This project is licensed under the MIT License. Portions of code are based on VelocityWhitelist by Rathinosk, used under MIT license.

### Acknowledgments

- **Rathinosk** for the original VelocityWhitelist plugin
- **Velocity** team for the excellent proxy platform
- **JDA** library for Discord integration
- **HikariCP** for database connection pooling
  - SQLite 3.34+
- For Discord integration:
  - Discord Bot Token
  - Discord Server with appropriate permissions
- For Vault integration (optional):
  - Vault plugin on backend servers
  - Economy plugin (EssentialsX, CMI, etc.)
  - Permission plugin
- For LuckPerms integration (optional):
  - LuckPerms plugin on Velocity proxy

## Documentation
- **[INTEGRATIONS.md](INTEGRATIONS.md)** - Detailed guide for Vault, LuckPerms, and Discord integrations
- **[config-example.yaml](config-example.yaml)** - Comprehensive configuration example with all features
- **[references/](references/)** - Architectural documentation and development guidelines
  - Discord Bot Token
  - Server with appropriate permissions
- (Optional) Vault-compatible economy plugin

## Configuration
### Core Settings (`config.yaml`)
```yaml
enabled: true           # Enable/disable the whitelist
debug: false           # Enable debug logging

database:
  type: mariadb        # mysql, mariadb, postgresql, sqlite
  retry_count: 3       # Connection retry attempts
  retry_delay: 5000    # Delay between retries (ms)
  
  mysql:               # MariaDB/MySQL settings
    host: localhost
    port: 3306
    database: velocity
    user: username
    password: password
    useSSL: false
    table: whitelist
    maxPoolSize: 10    # Connection pool size
    
discord:
  token: 'your-token'  # Discord bot token
  server: 'server-id'  # Discord server ID
  approvedChannelIds: 'channel-ids'  # Comma-separated
  init_timeout: 30     # Bot initialization timeout

validation:
  mojangVerification: true
  geyserVerification: true
  geyserPrefix: '.'    # Bedrock username prefix
  timeout: 10          # Minutes (corrected to 10)
  codeFormat: '***-***' # 6-digit hexadecimal format (XXX-XXX)
```

### Purgatory Configuration
```yaml
purgatory:
  enabled: true
  server: 'hub'        # Server for unverified players (corrected to hub)
  timeout: 10          # Minutes until session expires (corrected to 10)
  kickMessage: 'Please verify using /verify'
  adventure_mode_lock: true    # Lock players in adventure mode
  transfer_packet_blocking: true # Block transfer packets
```

### Rewards Configuration
```yaml
rewards:
  enabled: true
  economy:
    enabled: true
    amount: 1000       # Initial reward amount
  permissions:
    enabled: true
    defaultGroup: 'verified'
  ranks:
    enabled: true
    defaultRank: 'Member'
```

## Player Usage
- To join the server, your Minecraft username must be whitelisted.
- **NEW Verification Process**: Use `/mc <your_username>` in Discord to start the process.
- The Discord bot will provide you with a 6-digit verification code (format: XXX-XXX).
- Join the Minecraft server and use `/verify` in-game to complete verification.
- If you are not whitelisted, you will see the configured kick message.

### Player Commands
- **Discord**: `/mc <minecraft_username>` — Start the verification process. Use this command in Discord to get your verification code.
- **In-Game**: `/verify` — Complete the verification process. Use this command in-game and enter your code when prompted.

**Note**: The old flow where `/mc` was used in-game is now deprecated. Always start with Discord first.

## Server Admin Usage

### Basic Commands
- `/vwl add <player>`: Add a player to the whitelist.
- `/vwl del <player>`: Remove a player from the whitelist.
- `/vwl list <search>`: List whitelisted players matching a search string (min 2 characters).

### Admin Commands
- `/vwl enable`: Enable the whitelist.
- `/vwl disable`: Disable the whitelist.
- `/vwl reload`: Reload the configuration from disk.
- `/vwl debug <on/off>`: Enable or disable debug logging.

### Permissions
- `velocitywhitelist`: Required for basic whitelist commands.
- `velocitywhitelist.admin`: Required for admin commands.

## Methods, Reasoning, and Developer API
- The plugin uses a modular design for easy feature toggling and future expansion.
- Whitelist status is checked on player login; non-whitelisted players are denied access.
- Discord integration allows players to link their Minecraft and Discord accounts for verification.
- Vault integration (if enabled) can reward players with currency or permissions upon successful whitelisting (**WIP**).
- Temporary access and validation sessions are managed for players in the process of verification.
- The plugin exposes a modular command and handler system for extending functionality.
- Database operations are abstracted via the SQLHandler class.
- Discord and Vault integrations are handled by their respective modules.
- All major functions, methods, and operational reasoning are documented in the `references` folder (see `references/completion.md` and `references/instructions.md`).
- The `references` folder also contains tools and prompts for those using AI coding assistants, to help immediately catch up on project context and standards—generated by my LLM for yours.

## License
This project is licensed under the GNU General Public License v3.0. See the LICENSE file for details.

## Disclaimer
This codebase is a heavily modified fork, with significant changes and class renaming. It is functional as described in the instructions. AI tools were used extensively for troubleshooting and feature development. The codebase may contain duplicated logic, excessive logging, and does not always follow best practices. Please see the full disclaimer in previous README versions for more context. Use, modify, and contribute with care and consideration.