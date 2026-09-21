This file describes which features have priority in the upcoming 1.12.2 port/rewrite.

As of right now it is more a rewrite than a port.
A lot of code was removed, commented out or straight away removed, so it even compiles in a 1.12.2 Forge environment.

The following shows a simple path on which features are gonna appear first. Nobody is bound to this, so it may not represent the real progress.

- [x] Compilable for 1.12.2 - latest Minecraft Forge: 
    - For now the tracks and the books (recipe and admin) are removed, also sound and achievements are gonna changed.
- [x] New registry for Items, Blocks and more
- [x] Begin to develop a new API
    - Implement the API with a simple train
    - Model file is loaded from .json (JTMT-Format) on class load (Cooperation with Fexcraft)
- [ ] Implement all the items from 1.7.10
    - [x] Composite Armor
    - [x] Driver Armor
    - [x] General TC Armor
    - [x] Ticketman Armor
    - [x] Fluid Canister: - should be able to hold every fluid, just like a bucket
    - [ ] Traincraft tool: combination of the wrench, stake and skin changer
    - [ ] All the items used for crafting rolling stock
- [ ] Implement all the blocks from 1.7.10
    - [ ] Assembly Table I
    - [ ] Assembly Table II
    - [ ] Assembly Table III
    - [ ] Bridge Pillar
    - [x] Copper Ore
    - [ ] Diesel Generator
    - [ ] Distillery
        - [x] Block
        - [x] TileEntity
        - [x] Texture
        - [x] Gui and Container (shift clicking is missing)
        - [ ] Logic (partly done)
    - [ ] Lantern
        - should work without a tile entity. Colors are bound to the default minecraft colors
    - [x] Oil Sand
    - [ ] Open Hearth Furnace
    - [x] Petrol Ore
    - [ ] Stopper
    - [ ] Switch Stand
    - [x] Train Workbench
    - [ ] Water Wheel
    - [x] Wind Mill
        - A custom Capability was added to address the wind speed
    - Including recipe handling over .json (To support the vanilla based system)
- [ ] Implement driving physics for TrackAPI compatible rails (**Help is needed**)
- [ ] Implement linked rolling stock
    - Already implemented, but not used
- [ ] Implementing GUIs for trains
    - Steam locomotive gui is partly implemented
    - Diesel missing
    - Electric missing
    - Cargo missing (should be done generic, so there aren't a bulk of gui files)
    - Passenger missing (Is it needed?)
- [ ] Implement train logic
    - Steam locomotive logic is partly done
- [ ] Adding more rolling stock from different types, for testing physic and driving
    - 1/2 Steam
    - 0/2 Diesel
    - 0/2 Electric
    - 0/2 Cargo cart
    - 0/2 Passenger cart
- [ ] Adding chunk loading option for trains. Carts should adopt this from their pulling train.
- [ ] First Alpha version can be released

- [ ] Implement Advancements and Sounds
    - The 1.7.10 Achievements should be used as a base
- [ ] Implement new TAPI-Compatible tracks
    - We could use the old track models, but I think more vanilla looking models are nicer
- [ ] Implement the recipe book. We don't need it, but it just looks nice.
- [ ] Second Alpha version release, with most of the bugs found in alpha one fixed.

- [ ] Implement all the rolling stock from 1.6.4, since the added 1.7.10 models and skins are removed, due to the artists not wanting them in Traincraft anymore.
    - Most of them need new names with a general naming scheme
    - Their implementation should be done through the use of the .json based model and TC API, to give examples for other addons
    - [ ] Work Train
    - [ ] Tender
    - [ ] Freight Cart
    - [ ] Steam Locomotive
    - [ ] Small Steam Train
    - [ ] Tracks Builder
    - [ ] Caboose
    - [ ] Grain Hopper
    - [ ] Lava Tank Cart -> General fluid tank cart
    - [ ] Log Transport
    - [ ] Passenger Car
    - [ ] BR E69
    - [ ] Shunter (UK)
    - [ ] VL10 (SU)
    - [ ] Tram
    - [ ] ChME3 (SU)
    - [ ] Flat car
    - [ ] Freight Wagon
    - [ ] Open Wagon
    - [ ] Tank Wagon (SU)
    - [ ] Jukebox -> no mp3 implementation, only minecraft sounds from discs
    - [ ] Stock Car
    - [ ] Work Caboose
    - [ ] Passenger Car
    - [ ] Tank Wagon
    - [ ] Passenger Car
    - [ ] GP-7 (US)
    - [ ] CD 742
    - [ ] Flatcart (SU)
    - [ ] Flatcart (US)
    - [ ] Box Cart (US)
    - [ ] Hopper Wagon (US)
    - [ ] Tank Wagon (US)
    - [ ] Platform Cart Wood (US)
    - [ ] Freight Car (US)
    - [ ] Small Freight Car
    - [ ] Cherepanov (SU)
    - [ ] Minecart
    - [ ] Cart Hauler
    - [ ] Tender
    - [ ] Wood Transport
    - [ ] Caboose
    - [ ] Passenger Car
    - [ ] Freight Car closed
    - [ ] Freight Car
    - [ ] Mogul (US)
    - [ ] Forney (US)
    - [ ] BR01 (DB)
    - [ ] Mail Wagon (DB)
    - [ ] Freight Wagon
    - [ ] Pass. Cart - I Class (DB)
    - [ ] Pass. Cart - II Class (DB)
    - [ ] BR80 (DB)
    - [ ] BR01's Tender (DB)
    - [ ] Rail Transport (DB)
    - [ ] Flat Car (DB)
    - [ ] Log Transport (DB)
    - [ ] Freight Gondola (DB)
    - [ ] Tank Wagon (DB)
    - [ ] SD70 Union Pacific (US)
    - [ ] USSR 0-5-0
    - [ ] USSR 0-5-0 Tender
    - [ ] Freight Car
    - [ ] Wood Transport
    - [ ] Wood Transport
    - [ ] Freight Trailer
    - [ ] Freight Wellcar
    - [ ] Kof (DB)
    - [ ] V60 (DB)
    - [ ] High Speed Car
    - [ ] N.Y. Subway
    - [ ] High Speed Locomotive
    - [ ] C62 Class [JNR]
    - [ ] Tender [JNR]
    - [ ] Shay (US)
    - [ ] Adler
    - [ ] Adler Tender
    - [ ] Adler Passenger Car
    - [ ] Logging Caboose
    - [ ] N.Y. Subway (passenger cart)
    - [ ] SD40 Santa Fe (U.S.)
- [ ] Fixing most of the alpha 2 bugs
- [ ] First Beta release
### Completed: Step 6.9.2
- Service brake holds against slope gravity while pressed.
- Parking brake removes residual vertical slope motion.
- High-speed downhill/curve facing ambiguity is resolved without flipping the locomotive nose.


### Completed in Step 6.9.3
- [x] Auto-feed water buckets from steam-locomotive GUI storage slots.
- [x] Preserve returned empty buckets in locomotive storage.
- [x] Fresh feedwater actively cools an overheated boiler.
- [x] Add sharp-curve speed protection without lowering straight-track top speed.

### Completed in Step 6.9.4
- [x] Hold reserve water buckets until locomotive tank reaches a low-water threshold.
- [x] Emergency-dump queued water buckets during boiler overheat.
- [x] Make normal wet-boiler temperature self-stabilizing instead of inevitably overheating.
- [x] Preserve dry-fire runaway and critical boiler failure as the dangerous case.


### Completed in Step 6.9.5
- [x] Spill locomotive inventory when the engine is broken/removed from the rails.
- [x] Preserve and drop internal inventory after boiler rupture.
- [x] Auto-pull reserve coal/furnace fuel from the 3x3 storage slots when the firebox fuel slot empties.
- [x] Replace steam/temperature GUI text with compact classic-style vertical gauges.
- [x] Yellow/orange normal boiler indication -> red hot/overheat -> flashing red critical warning.
- [x] Yellow/orange steam indication -> cyan at 100% steam.

### Completed in Step 6.9.6
- [x] Make the small steam locomotive reliably removable by Survival players.
- [x] Preserve locomotive-item and internal-inventory drops when removed normally.


### Completed in Step 6.9.7
- Greyish-white steam exhaust plume.
- Ten-segment boiler temperature gauge.
- Staged yellow -> orange -> red temperature colors with immediate flashing red above 200 C.

### Completed in Step 6.9.8
- [x] Match the boiler-temperature gauge visual style to the steam gauge.
- [x] Keep 50% / 100% staged boiler colors and flashing overheat warning.
- [x] Make low-water reserve-bucket feeding reliable without routing through the dedicated water slot.
- [x] Emergency-quench with all queued storage water buckets, including when the tank is already full.

### Completed in Step 6.9.9
- [x] Give the classic HUD speed gauge staged yellow/orange/red speed bands.
- [x] Keep the speed readout text white and reserve flashing red for actual boiler danger.

### Step 7.0 - Basic coupling and steam tender
- [x] Register a classic steam tender entity/item and legacy renderer.
- [x] Restore the two-click connector interaction for locomotive + tender.
- [x] Persist the connection by UUID and support manual uncoupling.
- [x] Add first-pass straight/curve/slope coupling physics and parking-brake hold.
- [ ] Step 7.1: give the tender storage/fluid capacity and automatically supply coal/water to the coupled steam locomotive.
- [ ] Generalize to front/rear couplers and longer rolling-stock consists.


### Step 7.0.1 - Tender orientation / coupling stabilization
- Tender render orientation corrected.
- First-move coupling snap reduced with coupler slack and rail-aligned force.
- Curve/slope coupling now stays rail-directed instead of applying lateral world-space pull.

### Step 7.0.2 - Close-coupled hill / curve follow
- [x] Tighten locomotive/tender coupling distance.
- [x] Use 3-D spacing on slopes.
- [x] Match true locomotive track speed on inclines.
- [x] Give the follower a controlled catch-up allowance so curve/hill gaps close again.


### Step 7.0.3 - Downhill consist speed lock

Keep a coupled steam tender physically close through fast downhill, uphill, and curve transitions. The locomotive now yields speed when its tender is lagging, while the tender receives stronger catch-up authority. Normal rail transitions no longer auto-break the connection.


## Step 7.0.4 - Close curve coupling + smooth visual turning
- Tightens locomotive/tender spacing earlier on curves and during straight/curve transitions.
- The locomotive governor now reacts sooner when either vehicle is on a sharp vanilla curve.
- Tender post-move catch-up starts sooner so the visible coupler gap does not open as much.
- Adds render-only yaw smoothing for both the locomotive and tender. Rail physics still use exact rail directions, while the long models visually turn through curves instead of snapping sideways and popping straight again.
