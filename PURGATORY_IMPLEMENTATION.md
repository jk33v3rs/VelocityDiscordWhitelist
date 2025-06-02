# Purgatory System Implementation

## Overview
This document describes the implementation of the missing purgatory system features in VelocityDiscordWhitelist, specifically the adventure mode lock and transfer packet blocking functionality that was configured in the YAML files but not implemented in the code.

## Features Implemented

### 1. Transfer Packet Blocking
**Configuration:** `general.purgatory.transfer_packet_blocking: true`
**Implementation:** Enhanced the existing `onServerPreConnect` event handler

**How it works:**
- When a player in purgatory attempts to connect to a server, the system checks the configuration
- If transfer packet blocking is enabled, players are restricted to their designated purgatory server
- Any attempts to connect to other servers are denied with a notification message
- Players receive the purgatory restriction message explaining they need to complete verification

**Code Location:** `VelocityDiscordWhitelist.java` - `onServerPreConnect()` method

### 2. Adventure Mode Lock
**Configuration:** `general.purgatory.adventure_mode_lock: true`
**Implementation:** New `onServerConnected` event handler

**How it works:**
- When a player in purgatory successfully connects to a server, the system checks the configuration
- If adventure mode lock is enabled, a gamemode command is sent to the backend server
- The command `gamemode adventure <username>` is executed with a 1-second delay to ensure proper connection
- This prevents purgatory players from interacting with the world until verification is complete

**Code Location:** `VelocityDiscordWhitelist.java` - `onServerConnected()` method

### 3. Purgatory Restriction Removal
**Implementation:** Automatic cleanup when verification is completed

**How it works:**
- When a player successfully completes verification, their purgatory session is automatically removed
- If adventure mode lock was enabled, the player's gamemode is restored to survival
- A success message is sent to notify the player of their new access level
- All purgatory restrictions are lifted, allowing normal server transfers

**Code Location:** 
- `VelocityDiscordWhitelist.java` - `removePurgatoryRestrictions()` method
- `EnhancedPurgatoryManager.java` - `completeVerification()` method with callback

## Configuration Structure

The purgatory settings are accessed from the YAML configuration as follows:

```yaml
general:
  purgatory:
    enabled: true
    server: "hub"
    adventure_mode_lock: true        # NEW: Forces adventure mode for purgatory players
    transfer_packet_blocking: true   # NEW: Blocks server transfers for purgatory players
    # ... other existing settings
```

## Technical Implementation Details

### Configuration Access
Added a helper method `getPurgatoryConfig()` that safely extracts purgatory settings from the main configuration map.

### Callback System
Implemented a callback system between `EnhancedPurgatoryManager` and the main plugin to handle restriction removal:
- `setPurgatoryRemovalCallback()` registers the callback
- `removePurgatoryRestrictions()` handles gamemode restoration and messaging

### Message Support
Added `getVerificationSuccessMessage()` to `MessageLoader` for consistent messaging when verification completes.

### Event Handlers
1. **ServerPreConnectEvent**: Enhanced to check transfer packet blocking configuration
2. **ServerConnectedEvent**: New handler for adventure mode enforcement
3. **Verification Completion**: Integrated with existing verification flow

## Error Handling
- All gamemode commands are executed asynchronously to prevent blocking
- Failed gamemode changes are logged as warnings but don't prevent verification completion
- Configuration errors fall back to secure defaults (restrictions enabled)
- Missing configuration values use sensible defaults

## Backward Compatibility
- All changes are backward compatible with existing configurations
- Default values ensure the system works even if new settings are missing
- Existing purgatory functionality remains unchanged

## Testing Recommendations

To test the implementation:

1. **Transfer Packet Blocking:**
   - Put a player in purgatory
   - Try to connect them to different servers
   - Verify they can only access their designated purgatory server

2. **Adventure Mode Lock:**
   - Connect a purgatory player to a server
   - Verify their gamemode is set to adventure
   - Complete verification and check gamemode is restored to survival

3. **Configuration Toggle:**
   - Test with both features enabled and disabled
   - Verify behavior changes according to configuration

## Future Enhancements

Potential improvements:
- Add configurable gamemode for non-purgatory players
- Support for custom commands beyond gamemode changes
- Per-server purgatory restrictions
- Time-based automatic restriction removal
