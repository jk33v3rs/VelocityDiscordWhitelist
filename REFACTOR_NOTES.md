# VelocityDiscordWhitelist Refactoring Notes

## Analysis of Spicord/DiscordNotify Architecture

### Key Design Patterns from Spicord:

1. **Simple Bot Initialization**:
   - Uses JDABuilder.createLight() with minimal required intents
   - Proper status tracking with BotStatus enum (READY, OFFLINE, STARTING, STOPPING)
   - Clean connection timeout handling
   - Proper shutdown procedures

2. **Configuration Simplicity**:
   - Minimal configuration files (just bot token, guild ID, enabled status)
   - No complex nested configurations
   - Default values handled programmatically
   - Configuration validation at startup

3. **Command Registration**:
   - Guild-specific slash commands (not global)
   - Commands registered on ReadyEvent
   - Clean command handler pattern
   - Proper permission checking

4. **Error Handling**:
   - Comprehensive exception handling
   - Graceful degradation when Discord is unavailable
   - Clear error messages and logging

### Issues in Current Implementation:

1. **Configuration Problems**:
   - config.yaml is 250+ lines with overlapping/unused sections
   - Multiple config files (config.yaml, messages.yaml, ranks.yml) causing confusion
   - No proper config validation
   - Discord bot says "disabled" even when enabled=true

2. **Bot Connection Issues**:
   - Bot doesn't show as online
   - No slash commands appear
   - Poor status management
   - Global command registration instead of guild-specific

3. **File Structure Issues**:
   - Too many separate config files
   - JSON files for simple rank data
   - blazeandcaves.json not needed

## Refactoring Plan:

### Phase 1: Clean Configuration
- [ ] Create simple config.yml with only essential settings
- [ ] Create messages.yml only if it doesn't exist (startup check)
- [ ] Remove ranks.yml, blazeandcaves.json dependencies
- [ ] Simplify Discord configuration to: token, guild_id, enabled

### Phase 2: Fix Discord Bot
- [ ] Implement proper bot status checking
- [ ] Use guild-specific command registration
- [ ] Fix connection timeout issues
- [ ] Add proper error handling

### Phase 3: Simplify Features
- [ ] Remove complex rank system for now
- [ ] Focus on core whitelist functionality
- [ ] Programmatic achievement handling (no JSON files)
- [ ] Console-only admin commands for rank management

### Target Configuration Structure:

```yaml
# config.yml - ONLY essential settings
database:
  host: "localhost"
  port: 3306
  database: "discord_whitelist"
  username: "root" 
  password: "password"

discord:
  enabled: true
  token: "YOUR_BOT_TOKEN"
  guild_id: "YOUR_GUILD_ID"
  verification_channel: "CHANNEL_ID"

settings:
  debug: false
  session_timeout_minutes: 30
```

### Benefits of This Approach:
- Matches Spicord's simplicity and reliability
- Easier to debug and maintain
- Clear separation of concerns
- Follows Discord bot best practices
- Reduces configuration complexity by 90%

## Implementation Status (Dev Branch):

### ✅ COMPLETED - Phase 1: Clean Configuration
- [x] Created SimpleConfigLoader inspired by Spicord's design patterns
- [x] Replaced complex YamlConfigLoader/JsonConfigLoader system with single unified loader
- [x] Removed 250+ line config.yaml dependencies and JSON files
- [x] Implemented config.yml and messages.yml auto-generation system
- [x] Added proper configuration validation at startup
- [x] Simplified Discord configuration to: token, guild_id, verification_channel
- [x] Removed blazeandcaves.json and ranks.yml dependencies

### Key Changes Made:
1. **SimpleConfigLoader.java**: New unified configuration loader
   - Single config.yml file only (no more .yaml vs .yml confusion)
   - Auto-generates messages.yml if missing
   - Built-in validation for Discord settings
   - Clean getter methods with defaults
   - Proper error handling and logging

2. **VelocityDiscordWhitelist.java**: Updated main plugin class
   - Removed JsonConfigLoader and YamlConfigLoader dependencies
   - Simplified loadConfig() method by 70% reduction in complexity
   - Updated reloadConfig() to use SimpleConfigLoader
   - Removed unnecessary saveDefaultConfig() method
   - Eliminated JSON configuration fields (ranksConfig, rewardsConfig)

3. **Configuration Structure**: Now matches Spicord's minimal approach
   ```yaml
   database:
     host: "localhost"
     port: 3306
     database: "discord_whitelist"
     username: "root"
     password: "password"
   
   discord:
     enabled: true
     token: "YOUR_BOT_TOKEN"
     guild_id: "YOUR_GUILD_ID"
     verification_channel: "CHANNEL_ID"
   
   settings:
     debug: false
     session_timeout_minutes: 30
   ```

### Next Steps for Phase 2:
- [ ] Update DiscordBotHandler to use SimpleConfigLoader
- [ ] Implement guild-specific command registration (not global)
- [ ] Add proper bot status checking before operations
- [ ] Fix connection timeout issues
- [ ] Improve error handling in Discord integration
