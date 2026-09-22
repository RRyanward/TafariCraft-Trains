# TafariCraft - Trains backup checkpoint — Step 9.3g-t7-d2

Current state: **FIXED TEMPORARY TRAVERSAL DIAGNOSTIC INSTALLED / RUNTIME SWEEP FAILED**

Frozen production baseline:
- Step 9.3g-t6-s3c = ALL_RAILS_VISUALS_CONFIRMED_GOOD_FROZEN
- Frozen production JAR SHA256: EA782B3CC2A0680E3699A1D8D40DDEF691A35969C96CA3814971BC7DD0167DF5
- t7 input ZIP SHA256: 1B94DF1FD91C01DC7592F3241EADA5E652545DB857B48869E9B66F5453D7E1D7
- fixed t7-d2 diagnostic source SHA256: BCDCA0804402CD046F5BDECFA0712CCF383F5BB729574A3AA2A494055399F46E

Superseded diagnostic note:
- an earlier t7-d2 diagnostic build was replaced by the fixed installer
- the current installed diagnostic is the BCDCA080... revision above

Runtime report:
- t7-all-rails-traversal-sweep-20260922-025809-814.txt
- ALL_CASES_PASS=false
- RESULT=T7_D2_TRAVERSAL_AND_FOLLOWER_ROUTING_FAIL

The failure is diagnostic evidence only; the frozen production rail/movement/coupling baseline remains hash-gated and unchanged.

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
