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

**Status: Late-stage development / release preparation — active regression and world-generation testing.**

The Minecraft 1.20.1 / Forge / Java 17 port has substantial playable locomotive, rolling-stock, track, crafting, and railway infrastructure systems. Targeted checks have passed for many components, but not every locomotive, world seed, mod combination, or automated railway scenario has been certified.

**Latest source sync: October 20, 2026.** GitHub `main` now includes additional DEV51B station work and DEV53–DEV55 improvements to passenger/driver seating, locomotive running gear, lighting, fluid-transfer rail presentation, and operational sounds. This describes **source development**, not a published release or a guarantee that every feature has passed full-world runtime testing.

**Current DEV56–DEV57 focus:** improving village/station discovery and expanding safely generated railway structure variety while preserving existing worlds. The proposed newer village-trigger approach and additional structure/worldgen work remain under investigation; they are **not** described here as completed production behavior.

No approved public release JAR has been published yet. See the [Changelog](CHANGELOG.md) for earlier milestone detail.

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

### 🚉 Railway Stations, Villagers & Services (active development)
- Village-linked station pilot with ticket offices, railway platforms, lantern lighting, and railway infrastructure
- **Surface station variety (DEV51B R37):** regular, abandoned, overgrown, and under-construction variants, selected deterministically from the world seed and station location; within eligible *surface-station* selections, the current variant roll is 60% / 15% / 15% / 10%, respectively
- **Underground stations (DEV51B R38):** a player-operated subway design, attempted at 10% of eligible newly placed station sites when enabled
- **Circular underground hubs (DEV51B R39):** a 25-block-diameter station hall with two side rooms, attempted at 5% of eligible new station sites when enabled; selected sites must pass excavation and chunk-safety checks
- Underground placement attempts can fall back to a safer eligible station design when their read-only site preflight rejects the location; these percentages are **selection rolls, not guaranteed spawn rates** across all villages
- Biome/style-aware station architecture, station survey and route diagnostics, clearance checks, and guarded player-operated station testing
- Six railway professions: **Station Chief, Train Conductor, Track Worker, Railway Engineer, Freight Yard Clerk, and Fuel Depot Operator**
- Corresponding workstations, villager trades, and Minecraft-style spawn eggs in the vanilla Spawn Eggs tab
- Experimental route planning, read-only audits, guarded service/dispatch studies, and controlled station-operation test suites

**Worldgen boundaries:** New station placement is eligibility-, configuration-, and terrain-dependent. Existing saves are not automatically retrofitted by the new station variants. Abandoned/overgrown stations do not receive normal station staff. Subway and circular-hub structures are player-operated designs, **not** automatically connected inter-village railways. Automated networks, schedules, and unsupervised train dispatch remain unfinished and under safety review.

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

### 🚂 Animation, Headlights & Visual Fixes
- DEV54 fleet running-gear work on wheel rotation, connecting rods, piston linkages, and static mesh masking, with targeted review commands and accepted locomotive-specific fixes
- DEV55 lighting work on headlamp direction, beam/ground-light placement, rear-lamp review, and in-world lighting diagnostics
- Passenger and driver seating calibration improvements, including wooden passenger tram driver-control safeguards and seat-alignment tools
- Fluid-loading and unloading rail direction markings, plus event-driven tanker pumping and mob-transport sound cues
- Earlier medium parallel-curve rail-side mesh repair and medium-diagonal placement-ghost seam fixes

**Visual-test boundaries:** Some locomotives and lighting setups are accepted in targeted tests, while other rod/mesh combinations and fleet-wide ground illumination still need review. In particular, the Berkshire 1225 ground light remains a known issue; no full-fleet lighting or rod-animation certification is claimed.

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
| Project Status | **Late-stage development / release preparation; active worldgen and visual validation** |

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
- **Documentation / In-game Guide:** In active development

---

<div align="center">

### 🚂 TafariCraft - Trains

**Build • Explore • Connect**

**Keep the rails alive.**

</div>
