# Traincraft 1.20.1 Port Status

Target: Minecraft 1.20.1 / Forge 47.3.22 / Java 17

## Completed

### Phase 1 — Preserve original source
- Original 1.12.2 Java code is retained in `src/legacy/java`.
- Original resources and models are retained for reference/porting.

### Phase 2 — Modern Forge foundation
- ForgeGradle 6 / Gradle 8.1.1 project.
- Java 17 toolchain target.
- Modern `mods.toml` and 1.20.1 resource pack metadata.
- Modern `@Mod` entry point.
- Confirmed by user: `gradlew build` succeeds on Forge 47.3.22 / Java 17.

### Phase 3 — Active block/item registry foundation
- Added 1.20.1 `DeferredRegister` registries.
- Activated 17 Traincraft block IDs.
- Activated the core Traincraft crafting/component items.
- Added block items for all active blocks.
- Added a modern Traincraft creative tab.
- Converted active blockstates to modern vanilla JSON format.
- Added simple block models and item models for active blocks.
- Converted English `.lang` entries into `en_us.json` for modern translations.

Important: machine blocks are placeholders at this stage. Their original block entities, GUIs, recipes, energy/fluid logic and special behavior are intentionally not active yet.

## Next phases

### Phase 4 — Resource/data migration
- Move and update recipes from legacy `assets/.../recipes` to `data/traincraft/recipes`.
- Replace ore-dictionary ingredients with Forge tags.
- Add mining/tool tags, loot tables, block/item tags and worldgen data.
- Validate all item models and textures in a 1.20.1 client run.

### Phase 5 — Machine block entities
- Distillery
- Assembly tables
- Train workbench
- Hearth furnace
- Diesel generator
- Battery
- Water wheel / wind mill

### Phase 6+ — Train systems
- Rolling-stock entity base.
- Locomotive and wagon registration.
- Track integration.
- Networking/capabilities.
- Menus/screens.
- Rendering/models/animations.
- Multiplayer synchronization and final compatibility pass.


## Step 3.1 - resource compatibility fixes

- Migrated block/item atlas references from legacy `textures/blocks` and `textures/items` to the 1.20.1 singular atlas paths (`textures/block`, `textures/item`).
- Removed the obsolete `forge:default-item` transform from the shared item model.
- Namespaced `sounds.json` entries with `traincraft:` so Traincraft OGG files resolve from the mod namespace.
- Bumped the port build version to `4.4.1_022-port.3.1.20.1`.


## Phase 4 checkpoint — first rolling stock

- Added a modern `EntityType` registry for rolling stock.
- Ported the **Small Steam Locomotive** as the first functional entity.
- Added a placement item using the original `train_steam_small` icon.
- Uses vanilla minecart rail physics temporarily to validate spawning, saving,
  multiplayer tracking, riding, collisions and rail movement before TrackAPI is restored.
- Converted the original 19-box JTMT small-steam model into a 1.20.1 `ModelPart` layer.
- Added a dedicated 1.20.1 entity renderer using the original red locomotive skin.
- Preserved the original 45 km/h top-speed target as 0.625 blocks/tick.

### Phase 4 test

1. `./gradlew clean build`
2. `./gradlew runClient`
3. Open the Traincraft creative tab and take **Small Steam Locomotive**.
4. Place vanilla rails, then right-click a rail with the locomotive item.
5. Verify the locomotive renders, rides the rail, can be entered, survives save/reload,
   and drops its Traincraft item when destroyed.

Next: skin switching/synchronization, steam state, inventory/water/fuel, and TrackAPI movement.

### Step 6.9.2 - Hill brake hold and facing stability
- Space/service brake suppresses vanilla slope acceleration while held so braking on an incline cannot immediately turn into downhill rollback.
- C handbrake now clears X/Y/Z motion, fixing uphill slope creep while parked.
- Rail-facing selection now resolves ambiguous high-speed axis transitions from actual motion while remembering forward-vs-reverse travel, preventing 180-degree visual flips on downhill curves.


## Step 6.9.3 - Feedwater automation and curve stability
- Steam locomotive storage slots now retain empty buckets returned by the feedwater system.
- Water buckets queued in locomotive storage auto-feed into the 5000 mB water tank while capacity is available.
- Each consumed 1000 mB water bucket cools a boiler above the 20 C resting temperature by 15 C.
- Sharp vanilla curves use a 21.6 km/h movement cap while on the bend; straight-track maximum remains 45 km/h.

## Step 6.9.4 - Demand feedwater and controllable boiler heat
- GUI storage water buckets no longer auto-feed continuously just because the tank has room.
- Queued buckets begin feeding only when water reaches 2000 mB (40%) or lower; normal refill stops once the tank rises above that threshold.
- If a tick begins at 200 C or hotter, queued water buckets are emergency-injected in the same tick until the tank is full or no buckets remain.
- Each accepted 1000 mB bucket now removes 20 C of boiler heat.
- A watered/fired boiler now stabilizes around 165 C during normal water levels instead of climbing forever.
- Low water raises the working equilibrium toward 195 C; a completely dry fired boiler still runs away into overheat/critical failure.
- Safety-valve cooling was strengthened so a recovered wet boiler can be brought back under control.


## Step 6.9.5 - Recovery drops, reserve coal and classic boiler gauges
- Breaking/removing the small steam locomotive now spills all internal fuel, water-container and storage-slot items before the normal locomotive item drops.
- Boiler rupture preserves the internal inventory and scatters it after the explosion, preventing the blast from immediately deleting the recovery drops.
- The existing reserve-fuel feeder is retained explicitly: when the dedicated firebox slot empties it pulls the next coal/charcoal/Forge furnace-fuel stack from the 3x3 locomotive storage area.
- Replaced temporary R-GUI temperature/steam text with two compact vertical instrument gauges.
- Temperature gauge is yellow/orange in the stable range, transitions toward red as the boiler approaches 200 C, and flashes red during the 220 C+ critical failure countdown.
- Steam gauge is yellow/orange while charging and switches to cyan at 100%.
- The classic external HUD now uses the same live temperature and steam color rules.

## Step 6.9.6 - Reliable Survival locomotive removal
- Survival player attacks now have a dedicated removal counter in addition to vanilla minecart damage.
- Three successful player hits within five seconds remove the locomotive from the rails, avoiding vanilla minecart damage decay making the larger Traincraft engine effectively unbreakable by hand.
- Removal still goes through the normal minecart destruction path, so the locomotive item drops and Step 6.9.5 inventory recovery remains intact.
- Boiler rupture still suppresses the locomotive-item drop, and creative removal behavior remains vanilla.


## Step 6.9.7 - Grey steam and staged boiler gauge
- Changed the visible steam plume from bright-white CLOUD particles to greyish-white POOF particles.
- Reworked boiler gauges in the R screen and classic HUD into ten 10% segments.
- Temperature colors now step at 50% and 100% heat instead of blending continuously.
- Overheat flashing now starts immediately above the 200 C overheat threshold rather than waiting for the 220 C critical-failure threshold.

## Step 6.9.8 - Matching gauges and reliable reserve feedwater
- Boiler temperature now uses the same smooth vertical gauge body/fill/tick style as the steam gauge while retaining staged yellow/orange/red warning colors and flashing red above 200 C.
- Normal storage feedwater is consumed directly from the locomotive 3x3 inventory when the tank reaches 2000 mB or less, removing the dedicated-water-slot staging dependency.
- Overheat emergency cooling now consumes every queued 3x3 water bucket, even when the internal tank is already full; excess feedwater acts as overflow while still applying cooling.
- Automatic storage-bucket use returns the empty bucket to its source storage slot.

## Step 6.9.9 - Staged speed gauge colors
- The classic HUD speed gauge now uses the same staged warning palette as the boiler instruments instead of a fixed light-gray fill.
- 0-49% of 45 km/h (0-22 km/h) is yellow, 50-79% (23-35 km/h) is orange, and 80-100% (36-45 km/h) is red.
- Speed does not flash at top speed because 45 km/h is the locomotive's intended maximum, not an overheat/failure condition.
- Numeric speed text remains white for readability.

## Step 7.0 - Basic locomotive/tender coupling

- Added the first functional 1.20.1 steam tender using the legacy `ModelNormalSteamTender` geometry and `tender2.png` skin.
- Restored the classic two-click Connector workflow for the small steam locomotive and steam tender.
- Connector selection is UUID-backed and server-authoritative; sneak-right-click a coupled vehicle to uncouple it, or sneak-right-click in the air to clear a pending selection.
- Coupling state is saved on both rolling-stock entities and survives world save/reload.
- Added a first-pass physical coupling constraint so the tender follows the locomotive through straight track, vanilla curves and one-block slopes without vanilla minecart collision bounce.
- A parked locomotive holds its attached tender. Powered rails do not independently propel the tender.
- The coupler automatically separates if the pair is forced more than eight blocks apart.
- Step 7.0 deliberately supports one locomotive-to-tender link only. Front/rear multi-car chains and tender fuel/water transfer are planned next.


## Step 7.0.1 - Tender orientation and first-move coupling stability

- Corrected the classic steam tender renderer so the bogies remain below the tender body instead of receiving the small-locomotive-only vertical flip.
- Added coupler slack to prevent a large first-tick snap after a connection is established.
- Coupling forces are now projected along the tender rail axis rather than pulling directly across world X/Z, reducing curve derail/pop-out behavior.
- Preserved vertical rail motion while coupled so slopes do not fight the tender constraint.

## Step 7.0.2 - Close-coupled hill and curve follow

- Tightened locomotive/tender center-spacing slack so the rendered couplers stay visually close instead of allowing a large gap.
- Coupling spacing now measures true 3-D separation, preventing ascending rail height from hiding an oversized coupler gap.
- Tender follow speed now matches the locomotive's full track-speed magnitude rather than only X/Z speed, fixing lost distance while climbing.
- While already moving, the tender uses its live rail-aligned motion as the coupling tangent to avoid stale facing at curve transitions.
- Coupled tenders receive a limited catch-up speed allowance above locomotive cruise speed so distance lost on a curve or hill can be recovered smoothly instead of becoming permanent.


## Step 7.0.3 - Consist speed lock / downhill coupling stability

- Increased the emergency coupling break distance so ordinary slopes, curves, and acceleration cannot sever a valid locomotive/tender pair.
- Tightened the normal coupler spacing and strengthened tender catch-up.
- Added a post-move tender catch-up pass because vanilla minecart rail resolution can consume follower speed after the pre-move coupling calculation.
- Added a locomotive-side consist governor: if the tender begins to lag, the locomotive progressively reduces its own speed until the coupler closes again instead of running away from the tender.
- The governor is inactive at normal close spacing, so solo locomotive speed and normal coupled cruising remain unchanged.


## Step 7.0.4 - Close curve coupling + smooth visual turning
- Tightens locomotive/tender spacing earlier on curves and during straight/curve transitions.
- The locomotive governor now reacts sooner when either vehicle is on a sharp vanilla curve.
- Tender post-move catch-up starts sooner so the visible coupler gap does not open as much.
- Adds render-only yaw smoothing for both the locomotive and tender. Rail physics still use exact rail directions, while the long models visually turn through curves instead of snapping sideways and popping straight again.
