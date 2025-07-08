# VelocityDiscordWhitelist - Core Whitelist System Documentation

## Overview
VelocityDiscordWhitelist is a lean, modern whitelist plugin for Velocity proxy that integrates with Discord for player verification. The system provides a simple, secure flow where players must join Discord and run a command to gain temporary access, then are permanently whitelisted on their first successful server join.

## Core Architecture

### 1. Main Components

#### **VelocityDiscordWhitelist.java** (Main Plugin Class)
- Manages plugin lifecycle and component initialization
- Handles player login events and whitelist checking
- Coordinates between Discord and Minecraft systems
- Provides centralized configuration and logging

#### **DiscordHandler.java** (Discord Connection Manager)
- Manages JDA (Java Discord API) connection
- Handles bot status and connectivity
- Initializes and coordinates Discord components
- Provides clean shutdown procedures

#### **DiscordListener.java** (Discord Command Handler)
- Listens for Discord slash commands (`/mc`, `/whitelist`)
- Creates purgatory sessions for players
- Manages admin whitelist commands
- Handles Discord-side verification flow

#### **PurgatoryManager.java** (Temporary Session Manager)
- Manages temporary whitelist sessions (10-minute default)
- Tracks active verification attempts
- Automatically expires old sessions
- Converts purgatory sessions to permanent whitelist on join

#### **SQLHandler.java** (Database Interface)
- Handles all database operations
- Manages whitelist entries and player data
- Provides async operations for performance
- Includes connection pooling with HikariCP

### 2. Simplified Whitelist Flow

```
1. Player attempts to join → DENIED (not whitelisted)
   ↓
2. Player joins Discord server
   ↓
3. Player runs `/mc <username>` in designated channel
   ↓
4. System creates 10-minute purgatory session
   ↓
5. Player joins Minecraft server within 10 minutes
   ↓
6. System detects purgatory session → IMMEDIATE PERMANENT WHITELIST
   ↓
7. Player gains full server access
```

### 3. Key Features

#### **Immediate Verification**
- No verification codes needed
- No multi-step verification process
- Discord command → temporary access → permanent whitelist on join
- Failed sessions auto-expire

#### **Admin Commands**
Discord slash commands for administrators:
- `/whitelist add <username>` - Add player to whitelist
- `/whitelist remove <username>` - Remove player from whitelist  
- `/whitelist check <username>` - Check whitelist status

#### **Security Features**
- Channel-restricted commands (configurable)
- Admin permission checking
- Session timeout protection
- Database connection monitoring
- Comprehensive error handling and logging

#### **Database Design**
- Optimized table structure for whitelist operations
- Support for Discord user linking
- Verification state tracking
- Automatic cleanup of expired sessions

## Configuration

### Discord Settings
```yaml
discord:
  enabled: true
  bot_token: "your_bot_token"
  guild_id: "your_guild_id"
  verification_channel_id: "channel_id" # Optional - restrict commands to specific channel
```

### Purgatory Settings
```yaml
purgatory:
  session_timeout_minutes: 10
  cleanup_interval_minutes: 5
```

### Database Settings
```yaml
database:
  host: "localhost"
  port: 3306
  database: "minecraft"
  username: "root"
  password: "password"
  table: "whitelist"
```

## Database Schema

### Whitelist Table
```sql
CREATE TABLE IF NOT EXISTS whitelist (
    id INT AUTO_INCREMENT PRIMARY KEY,
    UUID VARCHAR(36) UNIQUE,
    user VARCHAR(16) NOT NULL,
    discord_id BIGINT,
    discord_name VARCHAR(100),
    verification_state ENUM('PENDING', 'VERIFIED') DEFAULT 'VERIFIED',
    verified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    added_by VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_uuid (UUID),
    INDEX idx_username (user),
    INDEX idx_discord_id (discord_id)
);
```

## API Usage

### For Plugin Developers

#### Check if Player is Whitelisted
```java
// Async check by Player object
CompletableFuture<Boolean> isWhitelisted = sqlHandler.isPlayerWhitelisted(player);

// Sync check by username
boolean isWhitelisted = sqlHandler.isPlayerWhitelistedByUsername(username);
```

#### Create Purgatory Session
```java
ValidationResult result = purgatoryManager.createDiscordSession(username, discordUserId, discordUsername);
if (result.isSuccess()) {
    // Session created successfully
}
```

#### Check Active Sessions
```java
boolean hasSession = purgatoryManager.isInPurgatory(username);
int activeCount = purgatoryManager.getActiveSessionCount();
```

### For Discord Bot Integration

#### Required Permissions
- Send Messages
- Use Slash Commands
- Read Message History (for command context)

#### Command Registration
Commands are automatically registered to the specified guild on bot ready.

## Error Handling

### Centralized Exception Management
All components use `ExceptionHandler.java` for consistent error handling:
- Database connection issues
- Discord API errors  
- Configuration problems
- Player verification failures

### Graceful Degradation
- Plugin continues operating if Discord is unavailable
- Fallback to basic whitelist checking
- Comprehensive logging for debugging
- Automatic retry mechanisms for recoverable errors

## Performance Considerations

### Database Optimization
- Connection pooling with HikariCP
- Async operations for all database calls
- Prepared statements to prevent SQL injection
- Indexed columns for fast lookups

### Memory Management
- ConcurrentHashMap for thread-safe session storage
- Automatic cleanup of expired sessions
- Minimal object creation in hot paths

### Network Efficiency
- Guild-specific slash commands (not global)
- Ephemeral responses for user privacy
- Minimal Discord API calls

## Testing and Validation

### Integration Points
1. **Discord Connection**: Bot comes online and registers commands
2. **Database**: Tables created and connections established  
3. **Player Login**: Whitelist checking and purgatory session handling
4. **Session Management**: Creation, expiration, and cleanup

### Common Issues and Solutions

#### Bot Not Responding
- Check bot token validity
- Verify guild ID configuration
- Ensure bot has required permissions
- Check network connectivity

#### Database Connection Issues  
- Validate database credentials
- Check network connectivity to database
- Verify database exists and is accessible
- Review connection pool settings

#### Players Not Getting Whitelisted
- Check purgatory session creation in logs
- Verify session hasn't expired (10-minute window)
- Ensure player joins with exact username used in Discord
- Check database whitelist table for entries

## Future Extensions

The architecture supports easy extension for:
- Global chat integration (HuskChat-style)
- Advanced role synchronization
- Multiple verification methods
- Custom verification workflows
- Integration with other Velocity plugins

## Dependencies

### Required
- Velocity API 3.1.0+
- JDA (Java Discord API) 5.0.0+
- HikariCP for connection pooling
- SLF4J for logging

### Optional
- LuckPerms (for role integration)
- Vault (for economy features)

---

*This documentation covers the core whitelist functionality. Additional features like chat integration and advanced role management are documented separately.*
