# TafariCraft - Trains backup checkpoint — Step 9.3g-t7-d2

Current state: **TEMPORARY_TRAVERSAL_DIAGNOSTIC_READY / pending runtime report**

Frozen production baseline:
- Step 9.3g-t6-s3c = ALL_RAILS_VISUALS_CONFIRMED_GOOD_FROZEN
- Frozen JAR SHA256: EA782B3CC2A0680E3699A1D8D40DDEF691A35969C96CA3814971BC7DD0167DF5
- t7 input ZIP SHA256: 1B94DF1FD91C01DC7592F3241EADA5E652545DB857B48869E9B66F5453D7E1D7
- t7-d2 diagnostic source SHA256: 5B2F340503E1C7CA17A35DCD69D61821ED0987E45BBC20057D7DB853F5274768

t7-d2 adds one temporary diagnostic source:
- src/main/java/traincraft/debug/TrackT7TraversalSweepCommand.java

Hash-gated and unchanged:
- production rail topology/path source
- SmallSteamLocomotive
- SteamTender
- coupling source
- Freight / Passenger / Caboose
- accepted rail visual assets

This branch is a safety-backup checkpoint. Main remains untouched.
