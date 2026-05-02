# MSpell Compatibility Suite

Compatibility patch for Minecraft 1.21.1 and NeoForge 21.1.262. Requires Java 21.

## Integrations

- Flowing Fluids with Simple Clouds local precipitation.
- Finite Flowing Fluids water consumption for Legendary Survival Overhaul drinking and canteen filling.
- Flowing Fluids water checks and local-rain checks for Burnt Basic.
- Water transport between the world and Sable sublevels, including water mass integration.

Flowing Fluids is the only required runtime mod. All other integration mods are optional and are detected before their mixins or API paths are used. Modular Backpacks is included as an optional development dependency; its compatibility integration is not implemented yet.

## Build

Requirements: Java 21 and an internet connection to the official Gradle, NeoForge, SpongePowered and Modrinth Maven repositories.

Run from the project root:

```shell
./gradlew clean build
```

On Windows:

```powershell
gradlew.bat clean build
```

Gradle downloads pinned development JARs from Modrinth Maven. They are used only for compilation and development runs; they are not bundled in the MSpellCompat output JAR. The Simple Clouds API used at compile time is extracted from the Simple Clouds JAR and is not added to the output mod.

See [`DEPENDENCIES.md`](DEPENDENCIES.md) for the pinned artifacts and official project pages.

## Attribution

This project is inspired by and partially adapts logic from [Sable & Flowing Fluids Compat 1.0.2](https://www.curseforge.com/minecraft/mc-mods/sable-flowing-fluids-compat), by Gideon Strom (FluffyCommando). See [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) for the source link and MIT attribution. This project is independent and is not affiliated with or endorsed by the original author.

## License

MSpell Compatibility Suite is licensed under the MIT License. See [`LICENSE`](LICENSE) and [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).


The built JAR includes the project license and third-party notices under META-INF/licenses/.

Burnt Basic 1.10.5.3 for Minecraft 1.21.1 NeoForge is resolved from Modrinth Maven using maven.modrinth:aclfPzta:oWQQludc.
