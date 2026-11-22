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

## Requirements

- Minecraft **1.21.1**
- **NeoForge 21.1.x**
- Java 21
- [Flowing Fluids](https://modrinth.com/mod/flowing-fluids)

Flowing Fluids is required. Other supported mods are optional and their integrations are activated when the corresponding mods are present.

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
| Create | 6.0.10 |
| Create Aeronautics | 1.3.2 |

The versions above describe the development environment. Compatibility with other versions is not guaranteed.

Cloth Config 15.0.140 is an optional client-side dependency for the configuration screen.

## Configuration

The mod provides configuration options for supported integrations, including:

- Enabling or disabling individual compatibility features.
- Adjusting surface water generation.
- Configuring finite-water consumption for drinking and canteen filling.
- Setting the minimum water amount and water-layer consumption for extinguishing.
- Enabling or disabling Sable fluid transport.
- Adjusting fluid transport intervals, processing limits, and transfer amounts.
- Enabling diagnostic logging when troubleshooting issues.

Configuration options depend on the installed mods and are available through the generated configuration files. The client configuration screen requires Cloth Config.

## Installation

1. Install Minecraft 1.21.1 with NeoForge.
2. Install Flowing Fluids.
3. Download the MSpell Compatibility Suite JAR.
4. Place the JAR in your Minecraft `mods` folder.
5. Install any additional supported mods whose integrations you want to use.

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
