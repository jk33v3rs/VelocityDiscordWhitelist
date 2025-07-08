# Scripts Directory

This directory contains utility scripts and data files for the VelocityDiscordWhitelist plugin.

## Files

### Python Scripts
- **`extract_achievements.py`** - Extracts achievement data from BlazeAndCaves advancement pack
- **`parse_achievements.py`** - Parses and processes achievement data for plugin integration

### Data Files
- **`blazeandcaves-achievements.json`** - Comprehensive achievement database (1200+ achievements)
  - Contains metadata, difficulty multipliers, category information
  - Used by XP system for rank progression calculations
  - Source: BlazeAndCaves Advancements Pack 1.21

## Usage

These scripts are development utilities for:
1. **Achievement Integration**: Processing BlazeAndCaves pack data
2. **XP Calculation**: Generating base XP values for achievements
3. **Data Validation**: Ensuring achievement data consistency

## Requirements

- Python 3.7+
- JSON processing capabilities
- Optional: requests library for downloading achievement data

## Note

These files are primarily for development and maintenance. The plugin runtime uses the processed achievement data embedded in the JAR file.
