# MSpell Compatibility Suite

**MSpell Compatibility Suite** is a compatibility mod for Minecraft 1.21.1 (NeoForge) that improves interactions between Flowing Fluids and several other mods.

The project focuses on connecting existing mechanics rather than replacing the original mods.

## Features

### Flowing Fluids + Sable

- Transfers fluids between the Minecraft world and Sable sublevels.
- Allows fluids to enter and leave dynamic structures through compatible openings.
- Integrates fluid transport with Sable's physical simulation.
- Accounts for fluid mass in dynamic structures.

### Flowing Fluids + Simple Clouds

- Uses local precipitation data for compatible fluid refill mechanics.
- Allows local rain to generate Flowing Fluids surface water layers.
- Provides configurable surface water generation settings.

### Flowing Fluids + Legendary Survival Overhaul

- Makes drinking from compatible finite water sources consume water.
- Supports configurable water consumption per drink.
- Applies finite-water behavior when filling compatible canteens.

### Flowing Fluids + Burnt Basic

- Allows Flowing Fluids water to count toward adjacent-water checks used for extinguishing.
- Supports local Simple Clouds precipitation checks for extinguishing.
- Configures the minimum required water amount and the number of water layers consumed per extinguish operation.

### Flowing Fluids + Modular Backpacks

- Supports fractional fluid amounts when transferring fluids between compatible backpack tanks and buckets.
- Handles tank slot interactions.
- Prevents duplicate processing by the native tank tick handler.

### Tree Physics + Sable damage

- Routes affected tree logs from explosions through Tree Physics' own `FloodFillUtil.blockBroken` API.
- Detects tree-log removal caused by world block destruction, including impact-driven removal, and asks Tree Physics to split valid trees into Sable physics sub-levels.
- Leaves tree validation, tags, rooted-tree rules, impulses, and falling behavior to Tree Physics.
- The integration activates only when Tree Physics is installed.

## Requirements

- Minecraft **1.21.1**
- **NeoForge 21.1.x**
- Java 21

All gameplay integration mods are optional. Flowing Fluids, Simple Clouds, Legendary Survival Overhaul, Burnt Basic, Modular Backpacks, Sable, Sable Beyond, Tree Physics, Sable: Destructive, Create, and Create Aeronautics are detected at runtime; each integration activates only when its required mods are present and its config option is enabled.

### Supported integrations

| Mod | Version used for development |
|---|---|
| Flowing Fluids | 1.0.8 |
| Simple Clouds | 0.7.3+1.21.1 |
| Legendary Survival Overhaul | 2.4.7.2 |
| Modular Backpacks | 0.2.12 |
| Burnt Basic | 1.10.5.3 |
| Sable | 2.0.6 |
| Sable Beyond | 0.5.0 |
| Tree Physics | 2.4 |
| Sable: Destructive | Optional runtime integration; exact test version not recorded in this source archive |
| Create | 6.0.10 |
| Create Aeronautics | 1.3.2 |

The versions above describe the development environment. Compatibility with other versions is not guaranteed.

Cloth Config 15.0.140 is an optional client-side dependency for the configuration screen.

## Configuration

The mod provides configuration options for supported integrations, including:

- Enabling or disabling individual compatibility features, including Tree Physics falling from explosions and block destruction.
- Adjusting surface water generation.
- Configuring finite-water consumption for drinking and canteen filling.
- Setting the minimum water amount and water-layer consumption for extinguishing.
- Enabling or disabling Sable fluid transport.
- Enabling or disabling Tree Physics felling from explosions and world-block damage independently.
- Adjusting fluid transport intervals, processing limits, and transfer amounts.
- Enabling diagnostic logging when troubleshooting issues.

Configuration options depend on the installed mods and are available through the generated configuration files. The client configuration screen requires Cloth Config.

## Installation

1. Install Minecraft 1.21.1 with NeoForge.
2. Download the MSpell Compatibility Suite JAR.
3. Place the JAR in your Minecraft `mods` folder.
4. Install any supported mods whose integrations you want to use. No gameplay integration mod is mandatory.

## License

MSpell Compatibility Suite is licensed under the MIT License. See [`LICENSE`](LICENSE) for details.

This project is inspired by and partially adapts compatibility logic from [Sable & Flowing Fluids Compat](https://www.curseforge.com/minecraft/mc-mods/sable-flowing-fluids-compat) by Gideon Strom (FluffyCommando).

See [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) for attribution and the applicable third-party license notice.

MSpell Compatibility Suite is an independent project and is not affiliated with or endorsed by the authors of the supported mods.

## Development

The project uses Gradle and Java 21.

To build the mod locally:

```bash
./gradlew clean build
```

On Windows:

```powershell
.\gradlew.bat clean build
```

See [`DEPENDENCIES.md`](DEPENDENCIES.md) for development dependency versions and project links.
## Performance settings

This build reduces repeated background work while preserving feature logic:

- Full Sable/Flowing Fluids transport pass defaults to every 10 ticks (`scan_interval_ticks`).
- Local boundary-water intake defaults to every 5 ticks (`boundary_input_interval_ticks`).
- Water-mass scan defaults to every 20 ticks (`sable_fluid_mass.scan_interval_ticks`); hull-bound refresh defaults to every 200 ticks.
- Runtime reflection lookups are cached in the mass and impact handlers.
- Eager diagnostic messages are only constructed when `debug_logging=true`; detailed diagnostics remain disabled by default.
- Impact displacement stays enabled by default to preserve water-interaction behavior. Its scan interval (`impact_displacement.scan_interval_ticks`) defaults to 1 tick for per-tick responsiveness and can be increased in `config/mspellcompat-sable-fluid.toml` to reduce scanning frequency. Cleanup cadence, history retention, and per-structure world-scan budget are configurable in the same section.

Existing TOML files retain their explicitly saved values. For a world already in use, edit `saves/<world>/serverconfig/mspellcompat-sable-fluids-server.toml`: in `[sable_flowing_fluids_block_transport]`, set `scan_interval_ticks = 10` and `boundary_input_interval_ticks = 5`; in `[sable_fluid_mass]`, set `scan_interval_ticks = 20` and `hull_refresh_ticks = 200`. Impact settings are in `[impact_displacement]` in `config/mspellcompat-sable-fluid.toml`: `enabled` defaults to `true`; `scan_interval_ticks` controls scan frequency; `cleanup_interval_ticks` and `history_retention_ticks` control stored-history cleanup; and `max_world_scan_cells` limits the largest world-space scan per structure. Tree Physics temporary-guard cleanup cadence is configured under `[tree_physics_damage]` in `config/mspellcompat-server.toml`. NeoForge retains values already saved in existing TOML files; if an older MSpellCompat config contains `enabled = false`, set it to `true` once to restore the requested default behavior.

