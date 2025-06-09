## Project Details
This is a Velocity Proxy plugin for managing a Minecraft whitelist using MySQL/MariaDB as the backend storage.

This is NOT a ban manager, it is a whitelist plugin ONLY. 
The flow for this plugin is:
- Player attempts to connect with sever details - player is kicked and told they arent whitelisted and to check Discord. This acts as the first layer of proection by tying access to the network to at a minimum, knowing what to google search to find us. 
- (outside scope) Player joins Discord, completes Onboarding flow as appropriate.
- (outside scope) player is instructed by some means to begin the whitelist process.
- in a specific channel or channels nominated in config.yml, player enters /mc <minecraft username> to begin the process.
- The plugin checks the database for the username, if it exists, it returns a message saying they are already whitelisted. 
- If the username does not exist, it begins the "purgatory" process, which is a temporary whitelist that allows the player to join the server, but not interact with it.
- The player is then instructed to complete the onboarding process, which includes verifying their Discord in game. 
- If the player hasnt completed the process within a timeframe nominated in config.yml, they are removed from the purgatory whitelist and kicked from the server. 
- There is a limit on the number of retries a player can have in purgatory, after which they are added to a silent blacklist, which prevents them from joining the server at all.
- If the player completes the onboarding process, they are added to the main whitelist and can interact with the server as normal. 
- After the mc command is run, the player is given a code in the format XXX-XXX
- The player is instructed to run /verify <code> in game, which will verify the association between their Discord account and Minecraft account, and complete the onboarding process.



 ## Code Cleanup
- If asked to clean up a file, do not remove inline comments unless explicity told to do so.

## Function Headers
- Always add a detailed header on any functions, including:
    - The name of the function
    - The purpose of the function
    - All parameters and notes on their use
    - The return value, if applicable and notes on it's use

## Error Handling
- Always handle potential errors gracefully.
- Use the dedicated Exception handler for the plugin to manage exceptions.
- Use try-catch blocks where appropriate otherwise - this should be minimally if at all though. 
- Log errors with sufficient detail to aid in debugging.
- Avoid exposing sensitive information in error messages.

## Naming Conventions
- Use camelCase for variable and function names.
- Use PascalCase for class names.
- Use camelCase for class properties and methods.
- Use UPPER_CASE for constants.