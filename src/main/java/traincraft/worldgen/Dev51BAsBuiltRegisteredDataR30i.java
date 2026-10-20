package traincraft.worldgen;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;

/**
 * R30i - one-time, SOURCE-ONLY SavedData record; NEVER a route authorization.
 * Separate from the frozen R30h read-only adapter and the legacy station/network data.
 * A record may only be created from a current, exact live R30f witness, and only
 * after a full OFFLINE test-world backup has been verified. No rail/vehicle writes.
 */
public final class Dev51BAsBuiltRegisteredDataR30i extends SavedData {
    public static final String KEY="tc_dev51b_asbuilt_source_v1_registered";
    private static final String MARKER=Dev51BAsBuiltStorageEnvelopeR30h.MARKER;
    private Dev51BAsBuiltStorageEnvelopeR30h.Envelope payload;
    private final boolean presentOnDisk,invalid;
    private boolean registeredThisSession;

    private Dev51BAsBuiltRegisteredDataR30i(
            Dev51BAsBuiltStorageEnvelopeR30h.Envelope bytes,boolean existing,boolean malformed){
        payload=bytes;presentOnDisk=existing;invalid=malformed;
    }
    public static Dev51BAsBuiltRegisteredDataR30i empty(){return new Dev51BAsBuiltRegisteredDataR30i(null,false,false);}
    public static Dev51BAsBuiltRegisteredDataR30i load(CompoundTag nbt){
        if(nbt==null||!nbt.getAllKeys().equals(java.util.Set.of("Marker","Version","Wire"))
                ||!nbt.contains("Marker",Tag.TAG_STRING)
                ||!nbt.contains("Version",Tag.TAG_INT)
                ||!nbt.contains("Wire",Tag.TAG_BYTE_ARRAY))
            return new Dev51BAsBuiltRegisteredDataR30i(null,true,true);
        return new Dev51BAsBuiltRegisteredDataR30i(
                new Dev51BAsBuiltStorageEnvelopeR30h.Envelope(
                        nbt.getString("Marker"),nbt.getInt("Version"),nbt.getByteArray("Wire")),true,false);
    }
    @Override public CompoundTag save(CompoundTag tag){
        if(payload==null || invalid || (!presentOnDisk&&!registeredThisSession))
            throw new IllegalStateException("R30i refuses accidental SavedData serialization");
        tag.putString("Marker",payload.marker());
        tag.putInt("Version",payload.version());
        tag.putByteArray("Wire",payload.bytes());
        return tag;
    }
    public record Result(String status,boolean backupValid,boolean recordPresent,
                         boolean diskLoaded,boolean dirty,boolean writeStaged) {}
    /** Only method allowed to call setDirty(); never used by legacy networks. */
    public static Result execute(ServerLevel level,Dev51BAsBuiltPersistenceR30g.Draft live,boolean commit){
        if(level==null||live==null || !Dev51BAsBuiltBackupGateR30i.WITNESS.equals(live.sourceFingerprint()))
            return hold("HOLD_LIVE_SOURCE_WITNESS",false,false,false);
        final Dev51BAsBuiltBackupGateR30i.Result backup;
        try {
            java.nio.file.Path world=level.getServer().getWorldPath(LevelResource.ROOT).toRealPath();
            java.nio.file.Path project=world.getParent().getParent().getParent();
            backup=Dev51BAsBuiltBackupGateR30i.verify(project,world,live.sourceFingerprint());
        }catch(Exception e){return hold("HOLD_WORLD_BACKUP_PATH",false,false,false);}
        Dev51BAsBuiltRegisteredDataR30i record=level.getDataStorage().computeIfAbsent(
                Dev51BAsBuiltRegisteredDataR30i::load,Dev51BAsBuiltRegisteredDataR30i::empty,KEY);
        if(record.invalid)return hold("HOLD_EXISTING_RECORD_INVALID_NBT",backup.valid(),true,record.presentOnDisk);
        if(record.payload!=null){
            var verdict=Dev51BAsBuiltStorageEnvelopeR30h.inspect(record.payload,live);
            if(!verdict.valid()||!verdict.matches())
                return hold("HOLD_EXISTING_RECORD_INVALID_OR_STALE_"+verdict.code(),backup.valid(),true,record.presentOnDisk);
            if(commit)return hold("HOLD_DUPLICATE_REGISTRATION_REFUSED",backup.valid(),true,record.presentOnDisk);
            // Once a record is SAVED, inspection must remain available even if
            // the original one-time backup attestation has aged out.
            if(record.presentOnDisk)return new Result("SOURCE_RECORD_RELOADED_VALID_NO_ROUTE_AUTHORITY",backup.valid(),true,true,record.isDirty(),false);
            return new Result("SOURCE_RECORD_STAGED_SAVE_AND_RESTART",backup.valid(),true,false,record.isDirty(),false);
        }
        if(!backup.valid())return hold(backup.status(),false,false,false);
        if(record.isDirty())return hold("HOLD_UNEXPECTED_DIRTY_EMPTY_STORAGE",true,false,false);
        if(!commit)return new Result("READY_SOURCE_ONLY_CONFIRM_REQUIRED",true,false,false,false,false);
        record.payload=Dev51BAsBuiltStorageEnvelopeR30h.stageInMemory(live);
        record.registeredThisSession=true;
        record.setDirty();
        return new Result("SOURCE_RECORD_STAGED_SAVE_AND_RESTART",true,true,false,true,true);
    }
    private static Result hold(String code,boolean backup,boolean present,boolean disk){
        return new Result(code,backup,present,disk,false,false);
    }
}
