# Automated Planting

A Fabric mod for **Minecraft 26.1.2** that plants the crop in your hand onto nearby
farmland automatically, while you walk. Switch it on and off with a key.

---

## What it does

While switched on, the server looks around you every few ticks and plants the plantable
item you are holding onto any empty farmland in range — consuming one item per planting,
exactly like a manual right-click.

- **Toggle key:** `K` — rebindable in Options → Controls → Automated Planting.
- **Settings key:** `N` — opens the settings screen. Also reachable from Mod Menu.
- **Feedback:** the action bar shows `Auto-planting: ON` / `OFF`.
- **Vanilla decides placement.** Planting is driven through the vanilla block placement
  code, so a block that cannot be planted, or cannot survive at that spot, is skipped
  rather than forced into the world.
- **Only plantable items are used.** A stack of dirt is ignored and reported as such,
  instead of making the mod scan the area for nothing.
- **The on/off state is not saved.** It resets when you rejoin, so a world can never be
  left with planting silently running.

### Which hand the seeds come from

The `seedSource` setting decides this:

| Value | Behaviour |
|---|---|
| `MAIN_HAND` | Only the main hand is used. The offhand is never touched. |
| `BOTH_HANDS` | **Default.** The main hand is used when it holds something plantable; only then does the offhand act as a fallback. |
| `OFF_HAND` | Only the offhand is used. |

`BOTH_HANDS` will not spend offhand items while your main hand can do the job, so the
common “seeds in the offhand as a spare” setup behaves the way you would expect.

### Do I need it on the server?

**Yes.** The server is the side that plants. A client-only install does nothing at all.

| Installed on | Result |
|---|---|
| Server only | Planting works for players who have it client-side. Players without it are unaffected, and the mod costs them nothing. |
| Client only | **Nothing happens.** The key press is discarded before it leaves your machine — no message, no planting. |
| Both | Works normally. This is the usual setup. |

Why a client-only install is completely silent: the client checks whether the server
accepts the mod's packet before sending it, and the “Auto-planting: ON/OFF” message is
sent by the *server*, not the client. The settings screen still opens, but nothing can be
saved, because the server owns the configuration.

---

## Opening the settings screen

Two ways:

1. **Mod Menu** — the mod appears in the mod list when Mod Menu is installed.
2. **The `N` key, while you are in a world.**

> **Note:** `N` does nothing on the title screen. Minecraft does not poll key bindings
> while any screen is open, so all key binds (not just this one) are inactive there.
> Use Mod Menu, or press `N` after entering a world.

The screen edits a local copy and sends it to the server when you press **Done**. The
server validates and stores it, then replies with what it actually kept, so the screen
always ends up showing the truth even if a value was clamped. **Fastest** is a one-press
preset that plants every tick and fills the whole area at once.

---

## Speed

Two settings control it:

| Setting | Effect |
|---|---|
| `intervalTicks` | Ticks between attempts. `1` = an attempt on every server tick (20 per second). |
| `plantsPerCycle` | How many blocks one attempt may plant. |

The **Fastest** preset sets `intervalTicks = 1` and `plantsPerCycle = 64`, which fills a
field almost instantly. A gentler setting is `intervalTicks: 1` with `plantsPerCycle: 4`,
which keeps up with walking without emptying your stack in a single tick.

---

## Commands

Operators can use `/autoplant`, which is also usable from the server console:

| Command | Effect |
|---|---|
| `/autoplant on` / `off` | Force the state on or off |
| `/autoplant toggle` | Flip the state |
| `/autoplant status` | Report state plus the active settings |
| `/autoplant info` | Report the loaded config (works from the console, with no player) |
| `/autoplant reload` | Re-read the config file |
| `/autoplant test` | Diagnostic: run one planting attempt at the command position |

`/autoplant test` spawns a throwaway actor holding seeds and reports how many crops are
now nearby. It exists so planting can be checked on a server with nobody connected.

---

## Configuration

The file is `config/automated_planting.json`, relative to the game directory. It is
created with defaults on first launch, and re-read by `/autoplant reload`.

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
| `radius` | Reach in blocks, along whichever distance measure `shape` uses. Clamped to 0–16, so a typo cannot stall the server. |
| `shape` | The **solid** the scan covers: `SPHERE`, `CUBE`, or `OCTAHEDRON`. |
| `intervalTicks` | Ticks between planting attempts. `1` = every tick. 20 ticks = 1 second. |
| `plantsPerCycle` | How many blocks one attempt may plant, clamped to 1–64. |
| `seedSource` | `MAIN_HAND`, `BOTH_HANDS`, or `OFF_HAND`. |
| `debugLog` | Log what each planting attempt is doing, including why one was skipped. |

### The shapes are three-dimensional

`radius` is a real 3D reach, not a horizontal one:

| `shape` | Rule | With `radius: 4` |
|---|---|---|
| `SPHERE` | `dx² + dy² + dz² ≤ radius²` | 9 wide, and ±4 vertically |
| `CUBE` | `max(\|dx\|, \|dy\|, \|dz\|) ≤ radius` | 9×9×9 |
| `OCTAHEDRON` | `\|dx\| + \|dy\| + \|dz\| ≤ radius` | a diamond that tapers vertically |

The scan is anchored on the surface you are standing on, not your exact feet position, so
standing on a block beside the field still works. If the field is further below you than
`radius` reaches, the mod also looks straight down your column for farmland before giving
up, so standing on a platform above a field still plants it.

An older config file that says `CIRCLE` / `SQUARE` / `DIAMOND` is migrated automatically
to `SPHERE` / `CUBE` / `OCTAHEDRON`; the retired `includeVertical` field is ignored.

---

## Requirements

- **JDK 25** — Minecraft 26.1.2 requires Java 25 (`javaVersion.majorVersion = 25`).
  Verified with Oracle JDK 25.0.4.1.
- No separate Gradle installation; use the bundled wrapper.

## Building and running

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

Game data lives in the project-local `run/` directory, so your real
`%APPDATA%\.minecraft` installation is never touched. `run/` is not committed.

## Troubleshooting

Turn on `debugLog` in the config, then read the game log. Every planting attempt says what
it found, and these are the lines worth knowing:

| Log line | Meaning |
|---|---|
| `no plantable item on hand (seedSource=…)` | Nothing plantable in whichever hand(s) the setting allows. |
| `held item is not a plantable block (…)` | The item is a block, but not a plantable one. |
| `scan anchor=… (feet=…) shape=… radius=… item=…` | Where the search was centred, and with what. |
| `nothing planted; 0 candidate(s) in the scan window` | Nothing plantable was found in range — the radius is probably too small, or the ground is not farmland. |
| `found farmland … below the anchor` | The search fell back to looking straight down your column. |
| `… refused by vanilla placement` | A spot was found, but the game's own rules rejected the placement. |
| `ran out of items after N planting(s)` | The stack was used up mid-scan. |
| `planted N/M block(s), K candidate(s) seen` | It worked: `N` of a possible `M` blocks were planted, from `K` candidates. |

If planting does not happen at all *and* commands also fail, check that the mod is
installed on the **server** side, not just the client.

---

## Technical notes

The parts that are easy to get wrong, and why the code looks the way it does.

### No mappings are configured — this is intentional

Minecraft 26.1 is the first release shipped **unobfuscated**, and Fabric stopped
maintaining third-party mappings from that version onward. Minecraft 26.1.2 therefore
publishes no `client_mappings` / `server_mappings`, and there is no Yarn mapping for it;
mods are written directly against the real class names (for example
`net.minecraft.resources.Identifier`).

So `build.gradle` deliberately declares **no `mappings` dependency**, and dependencies use
plain `implementation` rather than `modImplementation`. Adding a `mappings` line makes
Loom fail with `Failed to find official mojang mappings for 26.1.2`.
See the [Fabric mappings migration guide](https://docs.fabricmc.net/26.1.2/develop/porting/mappings/).

### Client and server code are separated

`src/main` holds everything both sides need; `src/client` holds the key bindings, the
settings screen and the Mod Menu hook. Loom's `splitEnvironmentSourceSets()` keeps that
client-only code off a dedicated server's classpath.

### Planting is delegated to vanilla

`AutoPlantHandler` builds a `BlockPlaceContext` and calls `BlockItem.place(...)` instead of
calling `setBlock` directly. Vanilla therefore decides whether the seed may go there —
farmland only, correct light, the block's own survival rules, the place sound — and a seed
that cannot be planted is skipped instead of being force-placed.

The entity that holds the seeds and the entity used as the placement context are the same
in normal play. They are kept as separate parameters so a test can drive planting with a
spawned mob, which is what `/autoplant test` does.

### The render entry point is easy to get wrong

In 26.x a `Screen` exposes two different methods: `extractRenderState(...)` is the one an
add-on overrides, while `extractRenderStateWithTooltipAndSubtitles(...)` is the `final`
framework entry point that also blurs the background. Calling the latter from inside the
former blurs twice in one frame and crashes with `Can only blur once per frame`. The
settings screen therefore calls `super.extractRenderState(...)` and nothing else.

Also: a `CycleButton` draws its own `label: value` caption, so its row must not have text
drawn over it.

### Mod Menu is optional

Mod Menu is a **compile-time-only** dependency, registered through an optional `modmenu`
entrypoint. Nothing touches it when it is absent, so the mod works fine without it.

### Repository mirrors

`maven.fabricmc.net` is extremely slow (roughly 900 B/s–15 KB/s) from some networks, which
makes dependency resolution time out. `build.gradle` therefore pins the Fabric and Sponge
groups to the `repository.hanbings.io/proxy` mirror using Gradle `exclusiveContent`, so
those artifacts resolve from the mirror and never fall back to the slow host.

The mirror serves the same published artifacts under the same coordinates, but it is still
a third-party mirror. To resolve exclusively from official sources instead, delete the
`exclusiveContent { ... }` block and add:

```groovy
maven {
	name = 'Fabric'
	url = 'https://maven.fabricmc.net/'
	content { includeGroupByRegex 'net\\.fabricmc.*' }
}
```

Expect far slower resolution on a network like the one this was set up on.

---

## Project layout

```
build.gradle                          build configuration
gradle.properties                     pinned versions
settings.gradle                       repositories + project name
gradlew / gradlew.bat                 Gradle wrapper (Gradle 9.7.1)

src/main/java/com/example/automatedplanting/
  AutomatedPlanting.java              entry point: packets, command, tick hook
  config/AutoPlantConfig.java         JSON settings, shape maths, migration
  config/AutoPlantSettings.java       validated snapshot shared with the client
  network/ToggleAutoPlantPayload.java          "the key was pressed"
  network/UpdateAutoPlantSettingsPayload.java  the screen's apply button
  network/SyncAutoPlantSettingsPayload.java    the server's answer
  network/AutoPlantSettingsCodec.java          wire format for the settings
  server/AutoPlantHandler.java        the planting logic
  server/AutoPlantCommand.java        /autoplant

src/client/java/com/example/automatedplanting/client/
  AutomatedPlantingClient.java        key bindings, client packet handling
  AutoPlantConfigScreen.java          the settings screen
  AutoPlantModMenu.java               optional Mod Menu hook

src/main/resources/
  fabric.mod.json                     mod metadata
  assets/automated_planting/          icon and translations (en_us, zh_cn)
```

## Pinned versions

| Component | Version |
|---|---|
| Minecraft | 26.1.2 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.155.3+26.1.2 |
| Fabric Loom | 1.18.3 |
| Gradle | 9.7.1 |
| Java | 25 |

## License

CC0-1.0. See [LICENSE](LICENSE).

---

## AI-assisted development notice

This project — its code, configuration and documentation — was written by an AI coding
assistant (DeepSeek) working under human direction. A human specified the required
behaviour, made the design decisions, and reviewed, tested and revised the result. The
code is therefore AI-assisted rather than human-authored.

It is released under CC0-1.0, which waives copyright and related rights. If you need to
attribute it, crediting **Amwillo** as the publisher is the accurate choice; the
implementation itself was produced by an AI assistant.

