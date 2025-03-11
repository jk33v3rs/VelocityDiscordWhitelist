# Velocity Whitelist

This is a Velocity plugin designed to manage a Minecraft whitelist using MySQL/MariaDB as the backend storage. Unable to find a plugin that met my exact requirements, I decided to adapt this one to suit my needs.

The project is based on [MySQLWhitelistVelocity by moesnow](https://github.com/moesnow/MySQLWhitelistVelocity) version 1.2.0.

## Installation

1. Download the latest release from the [Releases](https://github.com/rathinosk/VelocityWhitelist/releases) page.
2. Place the JAR file in the `plugins` directory of your Velocity proxy.
3. Start or Restart the proxy.

## Configuration

The plugin uses a configuration file (`config.properties`) located in the `plugins/velocitywhitelist` directory.

### Default Configuration

```properties
# Whitelist Status
enabled: false

# Enable Debug Messages
debug: false

# MySQL settings
host: localhost
user: username
password: strongpassword
database: velocity
port: 3306
table: g_whitelist
                    
# Kick message
message: Sorry, you are not in the whitelist.
```

### Configuration Options

- `enabled`: Set to `true` to enable the whitelist.
- `host`, `user`, `password`, `database`, `port`, `table`: MySQL database connection details.
- `message`: Kick message displayed to players not in the whitelist.

## Commands

# Basic Commands
- `/vwl add <player>`: Add a player to the whitelist.
- `/vwl del <player>`: Remove a player from the whitelist. (with DB autocomplete)
- `/vwl list <search>`: Lists players matching search criteria. (min 2 characters)

# Admin Commands
- `/vwl enable`: Enable the whitelist.
- `/vwl disable`: Disable the whitelist.
- `/vwl reload`: Reload the config.properties.
- `/vwl debug <on/off>`: Turn debug messages on or off.

## Permissions

- `velocitywhitelist`: Required to use the basic whitelist commands.
- `velocitywhitelist.admin`: Required to use whitelist admin commands.

## Usage

1. Configure the MySQL connection details in the `config.properties` file.
2. Start the proxy.
3. Use the `/vwl` command to manage the whitelist.

## Issues and Contributions

[![forthebadge](https://forthebadge.com/images/badges/works-on-my-machine.svg)](https://forthebadge.com)

If you encounter any issues or have suggestions for improvement, please open an issue or submit a pull request on the [GitHub repository](https://github.com/rathinosk/VelocityWhitelist).

## License

This plugin is licensed under the GPL-3.0 License - see the [LICENSE](LICENSE) file for details.

## bStats

[![metrics](https://bstats.org/signatures/velocity/VelocityWhitelist.svg)](https://bstats.org/plugin/velocity/VelocityWhitelist/25057)