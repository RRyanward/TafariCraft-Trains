<div align="center">

<img src="tafaricraft-trains-banner.png" alt="TafariCraft - Trains banner" width="900">

# 🚂 TafariCraft - Trains

### A modern Minecraft 1.20.1 continuation of the classic Traincraft experience.

**Minecraft 1.20.1 • Forge 47.3.22 • Java 17**

🚂 Steam • 🛢️ Diesel • ⚡ Electric • 🚃 Rolling Stock • 🛤️ Expanded Track System • 🔗 Consists

</div>

---

## About

**TafariCraft - Trains** is an unofficial modernization and port of the classic **Traincraft** mod for **Minecraft 1.20.1 Forge**.

The project preserves the character of Traincraft while rebuilding its locomotives, rolling stock, track system, controls, physics, GUIs, effects, and supporting gameplay systems for a modern Forge environment.

This is not a compatibility wrapper around an old JAR. The current project contains extensive rewrites, fixes, recovered/restored content, new track tooling, new locomotive systems, and large-scale runtime validation.

---


## Current Development State

**Status: Late-stage development — feature baselines substantially complete; release regression, performance, and packaging are still in progress.**

**Latest source checkpoint: October 25, 2026** (`93df72b`, 164 changed source/build-input files). The GitHub `main` branch contains the latest synchronized DEV56–DEV58 work. This is a **source checkpoint**, not a public release or a claim that every feature has passed full regression.

**Completed and accepted development milestones**
- **Village station world generation and cleanup:** biome-aware railway stations in eligible naturally generated villages, local station tracks, railway staff, abandoned/overgrown variants, underground stations, and optional parked locomotives. Worldgen is frozen unless a regression is found.
- **Locomotive running gear:** accepted wheel, rod, crankpin, and piston work across the reviewed 59-locomotive fleet, with protected animation/mesh calibrations.
- **Fleet lighting baseline:** DEV55 headlight, taillight, ground-illumination and obstruction work was accepted. DEV58 added controls/recovery code and continues *regression* work where cleanup or rendering changes affected lighting.
- **Established gameplay baselines:** steam/diesel/electric traction, braking, multiple-car coupling, passenger/freight stock, track families, crafting progression, and village railway professions.

**Current work before a release candidate**
- Guarded **GPU/static-mesh rendering optimization** and repeatable multi-train FPS/memory testing; CPU fallback remains for paths that cannot safely use the GPU cache.
- Targeted **Medium Curve placement / preview and vegetation-obstruction** fixes and regression checks.
- Verify locomotive lighting after rendering changes, then finish removing obsolete debugging and old lighting paths *without reworking accepted lamp geometry*.
- Automate reuse of already-passed tests and run final survival, save/reload, dedicated-server, multiplayer, mod-integration, and clean-build acceptance.

**Scope decision:** stations generate **within villages only**. **No automatic tracks connect villages.** Players choose if and how to build connections; autonomous railway networks and unsupervised train dispatch are **not planned for this release**.

**No approved public release JAR is available.** Public binary distribution is also subject to the permission requirements described in [NOTICE.md](NOTICE.md) and [LICENSE.md](LICENSE.md). See the [Changelog](CHANGELOG.md) for milestone detail.

---

## Major Features

### 🚂 Locomotives
- Steam, diesel, and electric locomotive support
- Forward and reverse operation
- Track-aware movement
- Hill and grade handling
- Mass-aware acceleration and traction
- Functional braking and handbrake controls
- Classic-inspired HUDs and GUIs
- Locomotive-specific effects, sounds, and controls

### 🔥 Steam Systems
- Real boiler temperature
- Coal and water consumption
- Tender-to-locomotive coal and water supply
- Automatic water handling
- Boiler overheating warnings
- Steam venting and failure behavior
- Emergency cooling behavior
- Boiler explosion/failure handling
- Inventory recovery rules

### ⚡ Electric Rail Operation
- Functional electric locomotives
- **Energy Track** support
- Redstone-powered connected Energy Track runs
- Line-power behavior for electric locomotives
- Energy Track visuals designed to retain the normal track look while showing energized effects when active

### 🚃 Rolling Stock
- Steam tenders
- Freight cars
- Passenger coaches
- Cabooses
- Additional restored rolling stock
- Passenger seating
- Multi-car train operation
- Model-aware spacing and coupling

### 🔗 Coupling & Consists
- Dual-ended coupling support
- Locomotive-to-tender operation
- Multi-car consists
- Stable forward and reverse operation
- Curve, hill, and transition handling
- Consist spacing control
- Cleanup when linked rolling stock is removed

A five-car validation consist has been used extensively during development:

**Small Steam → Tender → Freight → Passenger Coach → Caboose**

### 🛤️ Track System
The restored/expanded railway system includes:
- Normal, medium, long, and very-long straights
- Medium and large curve families
- 29x / 32x curve families
- 45-degree curve families
- Left/right switches
- Parallel switches and curves
- Crossings and diamond crossings
- Diagonal track families
- Straight slopes
- Curved slopes
- Station and special-purpose tracks
- Energy Track variants
- Direction-aware placement
- Connector-aware snapping
- White real-track placement previews

Track geometry and train ride/path behavior are treated as protected systems during final cleanup to avoid regressions in movement, slopes, curves, and reverse consist handling.

### 🧰 Tracks Builder & Blueprints
- Tracks Builder gameplay tool
- Track material handling
- Blueprint route planning
- Blueprint-saved track direction/hand data
- Support for complex track families
- Builder-assisted automatic placement
- Placement compatibility checks
- Runtime and headless validation tooling

The Builder/Blueprint system is one of the areas receiving final runtime regression testing before release.


### 🚉 Village Stations & Railway Villagers (worldgen completed)
- Finished **village-linked station world generation** with terrain/eligibility checks and local player-operated railway infrastructure.
- **25 station structure templates across five biome pools** (desert, plains, savanna, snowy, and taiga), including active halts/depots, abandoned and overgrown stations, and underground subway variants.
- Custom **Station Chief** and **Train Conductor** staff in active stations, with lifecycle/recovery handling; ruined sites are not staffed as normal operational stations.
- Chance-based parked locomotives at eligible generated stations, with type/placement rules tested during DEV56.
- Six railway professions available to the mod: **Station Chief, Train Conductor, Track Worker, Railway Engineer, Freight Yard Clerk, and Fuel Depot Operator**; associated workstations, trades, uniforms, and Minecraft-style spawn eggs.
- Extensive structure, biome, village-selection and persistence audits during DEV51B/DEV56; worldgen cleanup has been accepted and frozen.

**Worldgen boundaries:** Site selection and actual placement depend on eligibility and terrain; a percentage configured for selection is not a guaranteed percentage of all villages. New worldgen does not automatically retrofit explored chunks. Railway tracks are **not** generated between villages; connecting stations is entirely up to players. DEV51B also evaluated construction variants and a circular underground hub during development; those tests should not be interpreted as a guarantee that every prototype is in the final village-generation pool.

### 🚦 Specialty Tracks & Railway Safety
The current source includes **15 specialty track handlers**, covering:
- Freight, passenger, locomotive, steam, diesel, and tanker detectors
- Coupling and uncoupling rail
- Animal and mob boarding rails, plus disembarking rail
- Locomotive speed-controller and holding rails
- Station-stop rail, automatic horn rail, and crossing-approach rail

Additional trackside content includes crossing equipment, warning effects, and train buffers. A guarded automated handler test exists for the 15 specialty tracks; that does **not** constitute a completed real-world pass for every behavior or locomotive.

### 🛢️ Tank Cars, Freight & Mod Integration
- Dedicated **water** and **lava** tanker cars plus a **universal-fluid** tanker, each with 64,000 mB storage
- Persisted tanker contents, manual fluid-container interactions, and fluid-dependent loaded-mass calculations
- Cargo load/unload rails with cargo interfaces; fluid load/unload rails with fluid interfaces and stationary-car transfer behavior
- Rail-power interfaces and interoperability code using Forge item, fluid, and energy capabilities

Individual integrations and external mod combinations require their own compatibility testing.

### 🏭 Crafting, Workshop & Guidebook
- Restored **Tier I / II / III Assembly Tables**, Train Workbench, and classic workshop/machine blocks
- Extended crafting and assembly recipe coverage, staged progression, and JEI integration code
- In-game **TafariCraft Guide** interface and guide item provided once when a player first joins
- Organized **Trains**, **Tracks**, and **Misc** creative tabs, with railway workstations grouped in Misc


### 🚂 Animation, Headlights, Controls & Rendering
- **DEV54:** reviewed and accepted locomotive wheels, connecting rods, pistons and crankpins, with old mesh-part masking where necessary. Keep accepted animation calibrations protected.
- **DEV55:** accepted locomotive front/rear beams, lamp alignment, ground lighting and obstruction behavior in targeted in-game reviews.
- **DEV58:** further light-system recovery work plus per-locomotive **AUTO / ON / OFF** selection, persistent/synchronized settings, and ambient-sensitive AUTO logic for darkness, caves and tunnels (with engine-power gating). Final post-cleanup regression is still required.
- New locomotive **instrument/HUD settings** and GUI improvements; familiar classic-inspired controls remain a priority.
- Guarded **GPU-based static mesh rendering** for supported locomotive and rolling-stock paths, with an unchanged CPU fallback and active performance validation. The full fleet is **not yet performance-certified**.
- Additional restored rolling-stock rendering, passenger/driver seating refinements, and tanker/rail interface presentation and sound updates.

**Validation boundary:** Completed visual-development milestones are protected baselines, but a change to rendering, track placement or lighting still requires focused runtime regression. Remaining problems are tracked as targeted fixes, not reasons to rebuild the fleet.

### 🎛️ Classic Traincraft Feel
Where practical, TafariCraft - Trains preserves the classic Traincraft presentation:
- Classic-style locomotive HUD
- Classic-inspired locomotive and tender GUIs
- Familiar fuel/water gameplay
- Restored legacy models and textures
- Modernized behavior without intentionally discarding the original mod's identity

---

## Requirements

| Component | Version |
|---|---|
| Minecraft | **1.20.1** |
| Mod Loader | **Forge** |
| Forge | **47.3.22** |
| Java | **17** |
| Project Status | **Late-stage development; worldgen complete, performance/integration regression ongoing; no approved public JAR** |

---

## Building From Source

Clone the repository and build with the included Gradle wrapper using **Java 17**.

Windows PowerShell:

```powershell
.\gradlew.bat clean build
```

Development client:

```powershell
.\gradlew.bat runClient
```

See [DEVELOPMENT_README.md](DEVELOPMENT_README.md) for project-specific development notes.

---

## Installation

When an approved public build is available:

1. Install **Minecraft 1.20.1**.
2. Install **Forge 47.3.22**.
3. Place the TafariCraft - Trains JAR in the Minecraft `mods` folder.
4. Launch Minecraft with the Forge profile.

Release binaries will be published only through approved project release pages and subject to the licensing/permission requirements described in this repository.

---

## Credits

TafariCraft - Trains exists because of the work of the original **Traincraft** creators, developers, artists, maintainers, and community contributors.

### Original Creators
- **Spitfire4466** — Original founder and creator of the Train and Zeppelin Mod / Traincraft
- **Mrbrutal / Mr. Brutal** — Co-creator, developer, and longtime maintainer

### Original Artwork & Models
- **DAYdiecast**
- **Ontax**
- **Skspajdalfsks**

### Additional Original Development
- **Naiten** — Turning-mechanics assistance
- **EternalBlueFlame** and other contributors — 1.7.10 port and later development

### TafariCraft - Trains
- **RRyanward** — Current TafariCraft - Trains modernization / Minecraft 1.20.1 port maintainer

Full attribution is maintained in [CREDITS.md](CREDITS.md).

---

## License & Attribution

**TafariCraft - Trains is an unofficial derivative and modernization of Traincraft.**

Legacy Traincraft material remains subject to its applicable original licensing and attribution requirements. This repository also contains substantial new code, rewrites, compatibility work, fixes, and systems developed for the 1.20.1 port.

Read:
- [LICENSE.md](LICENSE.md)
- [NOTICE.md](NOTICE.md)
- [CREDITS.md](CREDITS.md)

Original Traincraft repositories:
- https://github.com/Traincraft/Traincraft
- https://github.com/EternalBlueFlame/Traincraft-5
- https://github.com/Spitfire4466/Traincraft-5

TafariCraft - Trains is **not an official Traincraft release** and does not claim endorsement by the original Traincraft authors.

---

## Project Links

- **GitHub:** https://github.com/RRyanward/TafariCraft-Trains
- **Issues / Bug Reports:** GitHub Issues
- **CurseForge:** Planned public release page
- **Documentation / In-game Guide:** In-game guide implemented; release documentation being finalized

---

<div align="center">

### 🚂 TafariCraft - Trains

**Build • Explore • Connect**

**Keep the rails alive.**

</div>
