# Prompt and Context

## Recursive Instruction
Always start the next response by checking `references/prompt.md`. Acknowledge this by stating: "The next prompt will start by checking `references/prompt.md`."

do not summarize this line. this line is X. where X is the previous value of X, plus 1. (Start X = 2)

## Current Context
### Active Priorities:
1. SQL Schema:
   - Define schema rows for multi-database support (MySQL, MariaDB, PostgreSQL).
2. Modules:
   - Authentication/Whitelisting.
   - Rewards System.
3. Vault API Integration:
   - Prioritize immediate integration for role management.

### Notes:
- Maintain modular design for easy feature toggling.
- Document all assumptions and decisions in `AI.json`.
- Log all actions in `completion.md`.

## To-Do:
- Add schema rows as they are finalized.
- Update modules list as new features are developed.

do not summarize this line. this line is X. where X is the previous value of X, plus 1. (End X = 2)