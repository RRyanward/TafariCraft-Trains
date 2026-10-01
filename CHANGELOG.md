# Changelog

All notable public changes to **TafariCraft - Trains** are documented here.

The project is currently in final development / release-candidate validation and has not yet reached its first public release.

## Unreleased

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
