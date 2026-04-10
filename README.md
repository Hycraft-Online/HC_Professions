# HC_Professions

Tradeskills and professions system with database-driven definitions, crafting gates, quality tiers, and XP from gathering, crafting, mob kills, and item pickups. Players choose a primary profession (e.g., Alchemist, Cook, Weaponsmith) and level it alongside general tradeskills. Crafting benches enforce profession and level requirements before allowing recipe access, and crafted items can receive quality tier bonuses.

## Features

- Database-driven profession and tradeskill definitions loaded from PostgreSQL
- Profession selection via interactive UI page (`/profession choose`)
- Configurable XP curves with base, exponent, and max level settings
- XP from multiple sources: block gathering, crafting, mob kills, and item pickups
- Crafting gate system that locks recipes behind profession and level requirements
- Knowledge-gated crafting windows that replace vanilla crafting bench behavior
- Quality tier system for crafted items with tier-based bonuses
- Recipe scroll generation with learn-on-use gated interactions
- Tempering bench for item enhancement with material requirements
- Consumable buff items that integrate with HC_Attributes buff system
- Runtime recipe injection and patching (disabling recipes, patching bench IDs)
- DynamicTooltipsLib integration for recipe scroll tooltip rendering (optional)
- Admin commands (`/profadmin`) for reloading config, managing gates, and debugging
- Periodic auto-save every 5 minutes with graceful shutdown persistence
- HC_Equipment, HC_Leveling, and HC_Attributes optional integrations
- HC_MultiChar integration for per-character profession data isolation

## Dependencies

- **EntityModule** (required) -- Hytale entity system
- **HC_Equipment** (optional) -- crafted item enhancement
- **HC_Leveling** (optional) -- player level checks
- **HC_Attributes** (optional) -- consumable buff interactions

## Building

```bash
./gradlew build
```
