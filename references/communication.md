# Communication Guidelines

## Player Communication

### In-Game Messages
1. **General Principles**
   - Keep messages clear and concise
   - Use consistent color coding (see Color Scheme section)
   - Include actionable next steps where appropriate
   - Support multi-language through configuration

2. **Error Messages**
   - Be specific about what went wrong
   - Provide clear steps to resolve the issue
   - Avoid technical jargon in player-facing messages
   - Log detailed debug info separately when debug mode is enabled

3. **Success Messages**
   - Confirm the action that was completed
   - Show relevant state changes
   - Include next steps if part of a multi-step process

### Discord Messages
1. **Bot Responses**
   - Keep the same tone and style as in-game messages
   - Use Discord formatting for better readability
   - Include emoji reactions for quick status indication
   - Support slash commands with clear descriptions

2. **Verification Process**
   - Clear step-by-step instructions
   - Timeout warnings and status updates
   - Success/failure notifications in both Discord and Minecraft

## Color Scheme
- `GREEN`: Success messages
- `AQUA`: Information and status updates
- `RED`: Errors and warnings
- `WHITE`: Command usage and help text

## Staff Communication
1. **Admin Commands**
   - Clear feedback for all actions
   - Debug information when enabled
   - Confirmation for destructive actions
   - List commands show relevant limits and filters

2. **Permission Messages**
   - Clear indication of missing permissions
   - Suggest alternative commands if available
   - Show permission node in debug mode

## Message Templates
1. **Whitelist Management**
```
[Success] {player} is now whitelisted.
[Success] {player} is no longer whitelisted.
[Error] You don't have permission to use this command.
[Info] Whitelisted Players matching '{search}': {players}
```

2. **Discord Integration**
```
[Info] Use /verify {code} in-game to complete verification
[Error] Verification timed out. Please start over with /mc
[Success] Account successfully linked to Discord
```

3. **Debug Mode**
```
[DEBUG] {detailed_technical_message}
[DEBUG] SQL Query: {query}
[DEBUG] Command arguments: {args}
```

## Request Clarification When
1. Player requests seem unclear or ambiguous
2. Command usage is incorrect
3. Permission level is insufficient
4. Database operation fails
5. Discord integration encounters issues

## Response Guidelines
1. **For Player Requests**
   - Ask specific questions about intent
   - Provide available options
   - Give examples of correct usage

2. **For Technical Issues**
   - Request relevant error messages
   - Ask about recent changes
   - Note server software versions
   - Check for plugin conflicts
