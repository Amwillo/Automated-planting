# Automated Planting

A Fabric mod for **Minecraft 26.1.2** that automatically plants the crop held in your
main hand onto nearby farmland, switched on and off with a key.

## What it does

While switched on, the server looks around you every few ticks and plants the block
you are holding in your **main hand** onto any empty farmland in range, consuming one
item per planting just like a manual right-click.

- **Toggle key:** `K` (rebindable in Options → Controls → Automated Planting).
- **Settings key:** `N` opens the settings screen (also rebindable).
- **Feedback:** the action bar shows `Auto-planting: ON` / `OFF`.
- **Runs on the server.** The client only reports key presses and settings, so it behaves
  the same in single-player and on a server. The on/off state is not saved across a rejoin.
- **Only plantable items count.** The held stack is checked before anything happens: a
  stack of dirt is ignored rather than being reported as a failed planting.
- **Which hand the seeds come from is a setting** (`seedSource`): main hand only, main
  hand with the offhand as a fallback, or offhand only.
- **Vanilla decides placement.** Planting goes through the vanilla placement rules, so a
  block that cannot be planted (or cannot survive there) is simply skipped.

### Opening the settings screen

Either from Mod Menu (the mod appears there when Mod Menu is installed) or with the `N`
key **while in a world**. The key is not polled by Minecraft while a screen such as the
title screen is open, so it does nothing there — that is how the game handles all key
binds.

### Speed

Two settings control it:

| Setting | Effect |
|---|---|
| `intervalTicks` | Ticks between attempts. `1` means an attempt on every server tick (20 per second). |
| `plantsPerCycle` | How many blocks one attempt may plant. |

The **Fastest** button in the screen sets `intervalTicks = 1` and `plantsPerCycle = 64`,
which fills a field essentially instantly. A gentler setting is `intervalTicks: 1` with
`plantsPerCycle: 4`, which keeps up with walking without emptying your stack in one tick.

### Settings screen

Press `N` in game. It edits a local copy and sends it to the server when you press Done;
the server validates and stores it, then replies with what it actually kept, so the screen
always shows the truth. It is also reachable from Mod Menu if that mod is installed.

### Command

Operators can use `/autoplant` when a key press is inconvenient, or from the console:

| Command | Effect |
|---|---|
| `/autoplant on` / `off` | Force the state on or off |
| `/autoplant toggle` | Flip the state |
| `/autoplant status` | Report state plus the active settings |
| `/autoplant info` | Report the loaded config (works from the console) |
| `/autoplant reload` | Re-read the config file |
| `/autoplant test` | Diagnostic: run one planting attempt at the command position |

`/autoplant test` spawns a throwaway actor holding seeds and reports how many crops are
now nearby. It exists so planting can be checked on a server with nobody connected.

## Configuration

`config/automated_planting.json`, relative to the game directory (in development that is
`run/config/`). It is created with defaults on first launch and re-read by
`/autoplant reload`.

```json
{
  "radius": 4,
  "shape": "SPHERE",
  "intervalTicks": 10,
  "plantsPerCycle": 1,
  "seedSource": "BOTH_HANDS",
  "debugLog": false
}
```

| Field | Meaning |
|---|---|
| `radius` | Reach in blocks, along whichever distance measure `shape` uses. Clamped to 0–16 so a typo cannot stall the server. |
| `shape` | The **solid** the scan covers: `SPHERE`, `CUBE`, or `OCTAHEDRON`. |
| `intervalTicks` | Ticks between planting attempts. `1` = every tick. 20 ticks = 1 second. |
| `plantsPerCycle` | How many blocks one attempt may plant, clamped to 1–64. |
| `seedSource` | `MAIN_HAND`, `BOTH_HANDS` (main first, then offhand), or `OFF_HAND`. |
| `debugLog` | Log what each planting attempt is doing, including why one was skipped. |

### Shapes are three-dimensional

`radius` is a real 3D reach, not a horizontal one:

| `shape` | Rule | With `radius: 4` |
|---|---|---|
| `SPHERE` | `dx² + dy² + dz² ≤ radius²` | 9 wide, and ±4 vertically |
| `CUBE` | `max(|dx|,|dy|,|dz|) ≤ radius` | 9×9×9 |
| `OCTAHEDRON` | `|dx| + |dy| + |dz| ≤ radius` | diamond that tapers vertically |

The scan is anchored on the surface you are standing on, not your exact feet position, so
standing on a block beside the field still works. If the field is further below you than
`radius` reaches, the mod also looks straight down your column for farmland before giving
up, so standing on a platform above a field still plants it.

An older config file that says `CIRCLE` / `SQUARE` / `DIAMOND` is migrated automatically
to `SPHERE` / `CUBE` / `OCTAHEDRON`; the retired `includeVertical` field is ignored.

## Requirements

- **JDK 25** — Minecraft 26.1.2 requires Java 25 (`javaVersion.majorVersion = 25`).
  Verified with Oracle JDK 25.0.4.1.
- No separate Gradle installation is needed; use the bundled wrapper.

## Commands

```powershell
# Build the mod (output: build/libs/automated_planting-1.0.0.jar)
.\gradlew.bat build

# Run a development client
.\gradlew.bat runClient

# Run a development server (accept run/eula.txt first)
.\gradlew.bat runServer

# Generate readable sources for the Minecraft classes
.\gradlew.bat genSources
```

Game data is isolated in the project-local `run/` directory, so your real
`%APPDATA%\.minecraft` installation is never touched.

## Pinned versions

| Component | Version |
|---|---|
| Minecraft | 26.1.2 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.155.3+26.1.2 |
| Fabric Loom | 1.18.3 |
| Gradle | 9.7.1 |
| Java | 25 |

## No mappings are configured — this is intentional

Minecraft 26.1 is the first release shipped **unobfuscated**, and Fabric stopped
maintaining third-party mappings from that version onward. Minecraft 26.1.2
therefore publishes no `client_mappings`/`server_mappings`, there is no Yarn
mapping for it, and mods are written directly against the real class names
(for example `net.minecraft.resources.Identifier`).

Consequently `build.gradle` deliberately declares **no `mappings` dependency**,
and dependencies use plain `implementation` rather than `modImplementation`.
Adding a `mappings` line makes Loom fail with
`Failed to find official mojang mappings for 26.1.2`.
See the [Fabric mappings migration guide](https://docs.fabricmc.net/26.1.2/develop/porting/mappings/).

## About the repository mirrors

`maven.fabricmc.net` is extremely slow (roughly 900 B/s–15 KB/s) from some
networks, which makes dependency resolution time out. To work around this,
`build.gradle` pins the Fabric and Sponge groups to the
`repository.hanbings.io/proxy` mirror using Gradle `exclusiveContent`, so those
artifacts resolve from the mirror and never fall back to the slow host.

The mirror serves the same published artifacts under the same coordinates, but
it is still a third-party mirror. To resolve exclusively from official sources
instead, delete the `exclusiveContent { ... }` block and add:

```groovy
maven {
	name = 'Fabric'
	url = 'https://maven.fabricmc.net/'
	content { includeGroupByRegex 'net\\.fabricmc.*' }
}
```

Expect resolution to be much slower on a network like the one this was set up on.

## Project layout

```
build.gradle                  build configuration
gradle.properties             pinned versions
settings.gradle               repositories + project name
gradlew / gradlew.bat         Gradle wrapper (Gradle 9.7.1)
src/main/java/com/example/automatedplanting/
  AutomatedPlanting.java            entry point: payload, command, tick hook
  config/AutoPlantConfig.java       JSON settings + the shape maths
  network/ToggleAutoPlantPayload.java   the "key was pressed" packet
  server/AutoPlantHandler.java      the planting logic
  server/AutoPlantCommand.java      /autoplant
src/client/java/.../client/
  AutomatedPlantingClient.java      key mapping, runs only on the client
src/main/resources/
  fabric.mod.json             mod metadata
  assets/automated_planting/lang/   translations (en_us, zh_cn)
```

The split between `src/main` and `src/client` matters: the key mapping is a client-only
API, and Loom's `splitEnvironmentSourceSets()` keeps that code out of a dedicated server.

## Why planting is delegated to vanilla

`AutoPlantHandler` builds a `BlockPlaceContext` and calls `BlockItem.place(...)` rather
than calling `setBlock` directly. Vanilla therefore decides whether the seed may go
there — farmland only, correct light, the block's own survival rules, the place sound —
and a seed that cannot be planted is skipped instead of being force-placed.

## License

CC0-1.0. See [LICENSE](LICENSE).
