# Changelog

All notable public changes to **TafariCraft - Trains** are documented here.

The project is currently in final development / release-candidate validation and has not yet reached its first public release.

## Unreleased

### October 13, 2026 — Development source synchronization (DEV37–DEV52)

The GitHub source tree was synchronized with the working Minecraft 1.20.1 project. Items below reflect added code/content and previously reported targeted development checks; they are **not** a claim that every feature has passed complete release or multiplayer regression testing.

**Railway villages and operations**
- Added village railway station generation/pilot logic, enclosed ticket office upgrades, station lighting, station-network survey/preview/placement/audit tools, and terrain/clearance checks.
- Added six railway villager professions with workstations, trades, uniforms, and spawn eggs: Station Chief, Train Conductor, Track Worker, Railway Engineer, Freight Yard Clerk, and Fuel Depot Operator.
- Added managed railway staff recovery options, abandoned-station variants, and railway service activate/status/staff controls.
- Developed route and junction testing tools, station-access corrections, and experimental network/service systems; larger autonomous railway networks remain in progress.

**Tracks and signaling**
- Added 15 specialty track handlers: six stock-type detectors; coupler; animal/mob boarding; disembarking; locomotive speed control; locomotive holding; station stop; locomotive horn; crossing approach.
- Added a guarded 15-track automatic handler test, crossing approach signaling logic, train buffers, and crossing warning effects.
- Rebuilt medium parallel-curve rail-side meshes to remove orange/black artifacts and corrected medium-diagonal ghost seams.

**Rolling stock, visuals, and logistics**
- Added dedicated water, lava, and universal tanker cars with 64,000 mB fluid capacity, persisted fluid contents, and fluid-dependent loaded mass.
- Added cargo/fluid load and unload tracks, interface blocks, rail-power bridges, and Forge capability-based integration components.
- Improved animated wheels, rods, pistons, and gear/linkage visibility for restored locomotives.
- Continued train lighting with headlamp aiming, day/night behavior, ground wash, and selected-locomotive beam occlusion testing.
- Removed the Zeppelin items, models, and related registrations from the current active mod content.

**Progression and player experience**
- Restored Tier I/II/III Assembly Tables and Train Workbench functionality and expanded crafting/assembly progression.
- Added workshop machinery, railway workstations, and JEI integration code for assembly recipes.
- Added the in-game TafariCraft Guide and one-time first-join guide distribution.
- Refined creative inventory organization, including railway villager eggs in Minecraft's Spawn Eggs tab.

**Targeted validations previously reported**
- Five-car FC12 forward/reverse consist traversals without flipping.
- Medium-diagonal placement preview seam fix and medium parallel-curve rail-side artifact fix.
- Selected steam locomotive wheel/rod animation checks and selected train lighting/day-night improvements.
- Railway workstations' flicker fixes and restored village staff/trades in the isolated recovery world.
- Assembly/crafting DEV52B progression and coverage checks reported as passed.

**Still in progress:** comprehensive 15-specialty-track physical runtime certification; general automatic railway networks, persistent autonomous timetables, and dispatch safety; full-fleet lighting obstruction; compatibility and release regression testing.


### Added
- Minecraft 1.20.1 / Forge 47.3.22 / Java 17 foundation
- Functional steam locomotive systems
- Diesel locomotive support
- Electric locomotive support
- Energy Track line-power system
- Steam tenders and tender supply behavior
- Freight, passenger, caboose, and additional restored rolling stock
- Multi-car coupling and consist operation
- Passenger seating
- Classic-inspired locomotive HUDs and GUIs
- Boiler temperature, overheating, cooling, venting, and failure behavior
- Coal and water consumption and automatic supply
- Expanded restored track families
- 29x / 32x track families
- 45-degree curves and switches
- Diagonal tracks and crossings
- Straight and curved slope families
- Left/right track-family handling
- Direction-aware and connector-aware placement
- White real-track placement previews
- Tracks Builder
- Blueprint route planning and laying
- Blueprint direction/hand preservation
- Builder material handling
- Runtime and headless track validation tooling
- Chunk-loader support used by railway systems
- Restored legacy models, textures, and rolling-stock content

### Changed
- Reworked legacy Traincraft systems for modern Forge/Minecraft APIs
- Reworked locomotive traction and mass behavior
- Reworked coupling for stable forward and reverse consists
- Reworked train handling over curves, hills, slopes, and transitions
- Reworked rolling-stock positioning and renderer calibration
- Reworked track placement and snapping without retuning accepted ride/path math
- Reworked Energy Track power recognition and visual behavior
- Reworked locomotive/tender inventory, fuel, and water behavior
- Restored classic-style train interfaces while retaining modern synchronization
- Consolidated left/right track families where appropriate

### Validated During Development
- Five-car FC12 consist testing: Small Steam → Tender → Freight → Passenger Coach → Caboose
- Forward and reverse consist movement
- Track curve and slope traversal
- Dedicated/headless validation for major track and Builder systems
- Protected track geometry/path behavior during final cleanup

### In Progress
- Final client/runtime regression testing
- Tracks Builder / Blueprint full-route validation
- Final visual and model cleanup
- Documentation and in-game guide updates
- Modpack interoperability work for cargo, fluids, power, vegetation, and entity interaction
- Public release packaging and release-page preparation
