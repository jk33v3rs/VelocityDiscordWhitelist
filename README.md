# VelocityDiscordWhitelist

A professional-grade Velocity proxy plugin for Discord-integrated whitelist management.

[![Version](https://img.shields.io/badge/version-1.0.11-blue.svg)](https://github.com/jk33v3rs/VelocityDiscordWhitelist/releases)
[![Java](https://img.shields.io/badge/java-17+-orange.svg)](https://openjdk.java.net/)
[![Velocity](https://img.shields.io/badge/velocity-3.4.0+-green.svg)](https://velocitypowered.com/)
[![License](https://img.shields.io/badge/license-GPL--3.0-red.svg)](docs/project/LICENSE)

## 🚀 Quick Start

1. **Download**: Get the latest JAR from [releases](https://github.com/jk33v3rs/VelocityDiscordWhitelist/releases)
2. **Install**: Place `VelocityDiscordWhitelist-1.0.11-all.jar` in your Velocity plugins folder
3. **Configure**: Edit `config.yaml` with your database and Discord settings
4. **Start**: Restart your Velocity proxy

## ✨ Key Features

- **🔗 Discord Integration**: Seamless whitelist verification through Discord commands
- **⏳ Purgatory System**: Temporary access for verification process
- **📈 XP & Ranking**: 175-rank progression system with BlazeAndCaves integration
- **🗄️ Database Backend**: MySQL/MariaDB with HikariCP connection pooling
- **🛠️ Admin Tools**: Comprehensive command system for management
- **🔄 Parallel Processing**: Optimized startup with concurrent initialization

## 📚 Documentation

- **📖 Setup Guide**: [`docs/project/README.md`](docs/project/README.md)
- **⚙️ Configuration**: [`docs/project/config-example.yaml`](docs/project/config-example.yaml)

## 🔨 Building

```bash
# Clone repository
git clone https://github.com/jk33v3rs/VelocityDiscordWhitelist.git
cd VelocityDiscordWhitelist

# Build plugin
./gradlew clean shadowJar

# Output: build/libs/VelocityDiscordWhitelist-1.0.11-all.jar
```

## 🎯 Requirements

- **Java**: 17 or higher
- **Velocity**: 3.4.0 or higher
- **Database**: MySQL 8.0+ / MariaDB 10.5+ (optional: SQLite for testing)
- **Discord**: Bot application with required permissions

## 📄 License

GNU General Public License v3.0 - See [`docs/project/LICENSE`](docs/project/LICENSE)

## 👥 Contributing

Contributions welcome! Please read our [setup guide](docs/project/README.md) for development instructions.

---

**Author**: jk33v3rs  
**Based on**: VelocityWhitelist by Rathinosk  
**Discord**: [Join our community](https://discord.gg/your-server)  
