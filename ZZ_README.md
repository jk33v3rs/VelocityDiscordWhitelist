# VelocityDiscordWhitelist v1.3.1

A Velocity Proxy plugin for managinga Minecraft whitelist using MySQL/MariaDB/PostgreSQL/SQLite as backend storage. This plugin provides robust whitelist management with Discord integration, purgatory system for new players, and reward mechanisms.

## Features
- **Multi-database Support**
  - MySQL/MariaDB with connection pooling
  - PostgreSQL
  - SQLite (fallback option)
  - Automatic retry and failover
  
- **Discord Integration**
  - Player self-whitelisting via `/mc` and `/verify` commands
  - Customizable verification process
  - Role-based access control
  - Server status monitoring
  
- **Advanced Whitelist Management**
  - Purgatory system for new players
  - Customizable verification timeouts
  - Geyser/Floodgate support for Bedrock players
  - Mojang account verification
  
- **Rewards System**
  - Economy integration via Vault API
  - Advanced permission management via LuckPerms
  - Permission group management
  - Customizable rewards for verification
  - Rank-based progression rewards
  - Discord role synchronization
  
- **Performance & Reliability**
  - Async database operations
  - Connection pooling
  - Graceful error handling
  - Debug logging options

## Installation
1. Download the latest release JAR from the Releases page
2. Place the JAR in your Velocity `plugins` directory
3. Start the server once to generate configuration files
4. Configure the plugin (see Configuration section)
5. Restart your Velocity proxy

## Requirements
- Java 17 or higher
- Velocity 3.4.0 or higher
- One of the supported databases:
  - MariaDB 10.5+
  - MySQL 8.0+
  - PostgreSQL 12+
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