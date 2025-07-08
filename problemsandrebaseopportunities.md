# Problems and Rebase Opportunities

## 1. Core Plugin Structure & Startup

- **Startup Complexity**: The main plugin class (`VelocityDiscordWhitelist.java`) handles a large number of responsibilities: configuration loading, dependency injection, async startup, error escalation, and status reporting. This makes it harder to test and maintain. Consider extracting startup orchestration into a dedicated `StartupManager` or similar class.
- **Async Initialization**: While async startup is robust, error propagation between async tasks (Discord, SQL, etc.) could be further unified. A central `StartupStatus` object could track all subsystem readiness and failures.
- **Configuration Validation**: Config validation is present but scattered. A single `ConfigValidator` utility could centralize all config checks (Discord, SQL, XP, etc.), improving maintainability and error reporting.
- **Status Reporting**: Startup status is reported via logs, but not surfaced to in-game admins or Discord. Consider adding a `/vwl status` command and Discord DM/alert for failed startup.
- **Dependency Injection**: Guice is used, but some dependencies are still manually constructed. Full DI would improve testability.

## 2. Modules & Integrations

- **RewardsHandler & XPManager**: These modules contain business logic for player rewards and XP, but are tightly coupled to the main plugin and database handler. Refactor to use interfaces and dependency injection, allowing for easier testing and future expansion (e.g., alternate reward systems).
- **Integrations (LuckPerms, TNE, Vault)**: Integration classes are present but lack clear boundaries and error handling. Each integration should have a clear interface, robust error reporting, and be optional (fail gracefully if not present).
- **Duplication**: Some logic (e.g., player lookup, config access) is duplicated across modules. Centralize common operations in utility or service classes.
- **Separation of Concerns**: Modules sometimes mix concerns (e.g., DiscordBotHandler manages both Discord logic and some config validation). Refactor to ensure each class has a single responsibility.
- **Technical Debt**: Inline comments indicate workarounds and TODOs, especially around async event handling and integration points. Track these in a dedicated section or GitHub issues for better visibility.
- **Testing**: There is little evidence of automated tests. Consider introducing unit tests for config validation, startup, and integration logic, using mocks for external systems.
