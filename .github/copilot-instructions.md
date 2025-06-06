## Project Details
This is a Velocity Proxy plugin for managing a Minecraft whitelist using MySQL/MariaDB as the backend storage.

This is NOT a ban manager, it is a whitelist plugin ONLY.

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
- Use try-catch blocks where appropriate.
- Log errors with sufficient detail to aid in debugging.
- Avoid exposing sensitive information in error messages.

## Naming Conventions
- Use camelCase for variable and function names.
- Use PascalCase for class names.
- Use camelCase for class properties and methods.
- Use UPPER_CASE for constants.