package traincraft.worldgen;

import java.util.Set;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * DEV51B R30h - GUARDED Minecraft SavedData READ adapter. No registration.
 * computeIfAbsent caches an EMPTY CLEAN object in RAM if the key is absent.
 * It does not setDirty, create a disk record, touch legacy station storage,
 * or authorize world operations. save() intentionally fails CLOSED.
 */
public final class Dev51BAsBuiltSavedDataR30h extends SavedData {
    public static final String KEY="tc_dev51b_asbuilt_source_v1_unregistered";
    private static final Set<String> KEYS=Set.of("Marker","Version","Wire");
    private final Dev51BAsBuiltStorageEnvelopeR30h.Envelope envelope;
    private final boolean present;
    private final boolean malformed;

    private Dev51BAsBuiltSavedDataR30h(
            Dev51BAsBuiltStorageEnvelopeR30h.Envelope value,boolean exists,boolean invalid) {
        envelope=value;present=exists;malformed=invalid;
    }
    public static Dev51BAsBuiltSavedDataR30h empty(){
        return new Dev51BAsBuiltSavedDataR30h(null,false,false);
    }
    public static Dev51BAsBuiltSavedDataR30h load(CompoundTag source){
        if(source==null||!source.getAllKeys().equals(KEYS)
                ||!source.contains("Marker",Tag.TAG_STRING)
                ||!source.contains("Version",Tag.TAG_INT)
                ||!source.contains("Wire",Tag.TAG_BYTE_ARRAY))
            return new Dev51BAsBuiltSavedDataR30h(null,true,true);
        return new Dev51BAsBuiltSavedDataR30h(
                new Dev51BAsBuiltStorageEnvelopeR30h.Envelope(
                    source.getString("Marker"),source.getInt("Version"),source.getByteArray("Wire")),
                true,false);
    }
    /** Isolated NBT round-trip fixture in RAM. Not called by the save system. */
    public static CompoundTag simulatedNbt(Dev51BAsBuiltStorageEnvelopeR30h.Envelope value){
        CompoundTag tag=new CompoundTag();
        tag.putString("Marker",value.marker());
        tag.putInt("Version",value.version());
        tag.putByteArray("Wire",value.bytes());
        return tag;
    }
    @Override public CompoundTag save(CompoundTag unused){
        throw new IllegalStateException("R30h is READ ONLY - SavedData disk saving disabled");
    }
    public record Survey(String verdict,boolean nbtRoundTrip,boolean liveSourceMatches,
                         boolean recordPresent,boolean recordValid,boolean storageDirty) {}
    public static Survey inspect(ServerLevel level,Dev51BAsBuiltPersistenceR30g.Draft expected) {
        // Before loading the persistent name, verify an independent native NBT
        // round-trip in-memory so the native schema has been exercised.
        var staged=Dev51BAsBuiltStorageEnvelopeR30h.stageInMemory(expected);
        var nativeLoaded=load(simulatedNbt(staged));
        var simulated=Dev51BAsBuiltStorageEnvelopeR30h.inspect(nativeLoaded.envelope,expected);
        boolean roundTrip=!nativeLoaded.malformed&&simulated.valid()&&simulated.matches();
        // The ONLY backing-storage operation in this class is a read through
        // Minecraft's built-in loader, or an empty CLEAN in-memory instance.
        // Crucially, no setDirty() or persistent save is ever invoked.
        Dev51BAsBuiltSavedDataR30h stored=level.getDataStorage().computeIfAbsent(
                Dev51BAsBuiltSavedDataR30h::load,Dev51BAsBuiltSavedDataR30h::empty,KEY);
        if(stored.isDirty())
            return new Survey("HOLD_SAVEDDATA_DIRTY_UNEXPECTED",roundTrip,false,stored.present,false,true);
        if(stored.malformed)
            return new Survey("HOLD_SAVEDDATA_INVALID_NBT",roundTrip,false,true,false,false);
        var probe=Dev51BAsBuiltStorageEnvelopeR30h.inspect(stored.envelope,expected);
        return new Survey(probe.code(),roundTrip,probe.matches(),probe.present(),probe.valid(),false);
    }
}
