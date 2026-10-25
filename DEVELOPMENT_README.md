# TafariCraft - Trains — Development Notes

This repository contains the active **TafariCraft - Trains** Minecraft **1.20.1** Forge project.

It is not the old Traincraft 1.12.2 rewrite branch. Legacy Traincraft repositories and source are retained only as attribution/reference material where appropriate.

## Development Target

- **Minecraft:** 1.20.1
- **Forge:** 47.3.22
- **Java:** 17
- **Gradle Wrapper:** 8.1.1
- **Primary branch:** `main`

## Build

Use Java 17.

Windows PowerShell:

```powershell
.\gradlew.bat clean build
```

Development client:

```powershell
.\gradlew.bat runClient
```

## Current Architecture

The active project includes:

- Steam, diesel, and electric locomotive systems
- Tender fuel/water supply
- Rolling stock and passenger seating
- Multi-car forward/reverse coupling
- Classic-inspired HUDs and GUIs
- Expanded legacy/restored track families
- Energy Track
- Tracks Builder
- Blueprint route planning and laying
- Track placement previews
- Headless/runtime regression commands
- Restored legacy models and resources

## Protected Systems

Several systems have undergone extensive runtime validation and should not be casually retuned while fixing unrelated placement or UI issues.

In particular:

- Train ride/path math
- Accepted track geometry
- Coupling/consist spacing behavior
- Forward/reverse train handling
- Curved-slope behavior
- Blueprint placement math where already accepted
- Existing Energy Track → electric locomotive behavior

When fixing a placement, preview, GUI, or integration issue, prefer changing that subsystem rather than changing accepted movement/path math.

## Current Release Work

**Checkpoint:** October 25, 2026 source sync (`93df72b`). Village station worldgen and cleanup are accepted/frozen; the project remains in **unreleased late-stage development**.

**Agreed first-release scope:**
- Spawn local railway station structures in eligible naturally generated villages, with railway staff and chance-based parked trains.
- Let players create every inter-village railway connection themselves. **Do not generate automatic networks, timetables, or unsupervised train dispatch.**
- Preserve accepted locomotive rods/pistons/wheels and DEV55 lighting calibrations. Reopen only for demonstrated regressions.

**Current release gates:**
- Guarded GPU/static-mesh performance testing, especially FPS and memory with multiple trains on screen.
- Medium Curve placement/preview and vegetation-aware placement edge cases.
- Post-cleanup lighting regression checks and retirement of obsolete diagnostic/lighting code.
- Reuse accepted test evidence, run automated build/headless regressions, and do targeted in-game visual checks.
- Verify final survival gameplay, save/reload, dedicated server, multiplayer and packaging.
- Ensure public binary distribution permissions are satisfied under `NOTICE.md` and `LICENSE.md` before publication.

The source tree contains opt-in GPU render paths, locomotive `AUTO/ON/OFF` headlight modes, updated train instrument settings and villager/station lifecycle code; these are development features, **not** a substitute for release acceptance.

## Interoperability Direction

New compatibility work should prefer standard Forge interfaces first:

- `IItemHandler` for cargo
- `IFluidHandler` for fluids
- `IEnergyStorage` for optional external rail-power input
- Forge/Minecraft tags for vegetation and environment interaction

Avoid hard dependencies on individual technology mods unless a standard capability cannot provide the required behavior.

## Legacy Traincraft

TafariCraft - Trains is derived from and preserves attribution to the original Traincraft project.

See:

- `CREDITS.md`
- `LICENSE.md`
- `NOTICE.md`

Original repositories:

- https://github.com/Traincraft/Traincraft
- https://github.com/EternalBlueFlame/Traincraft-5
- https://github.com/Spitfire4466/Traincraft-5

## Contributing

Before submitting changes:

1. Read `LICENSE.md`, `NOTICE.md`, and `CREDITS.md`.
2. Do not remove original attribution.
3. Keep changes focused.
4. Document build/runtime testing where practical.
5. Avoid replacing validated movement/path behavior to solve unrelated issues.
6. Do not add third-party copyrighted material without appropriate permission.
