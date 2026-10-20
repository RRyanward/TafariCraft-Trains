package traincraft.worldgen;

import java.util.Arrays;
import java.util.Objects;

/**
 * DEV51B R30h strictly non-authoritative data envelope, independent of Minecraft.
 * Only nine OBSERVED existing source rails can be represented. An envelope is
 * NEVER a permit to persist, build, move a train or dispatch it.
 */
public final class Dev51BAsBuiltStorageEnvelopeR30h {
    private Dev51BAsBuiltStorageEnvelopeR30h() {}
    public static final String MARKER = "TC_DEV51B_AS_BUILT_R30H_NBT_SOURCE_ONLY";
    public static final int FORMAT_VERSION=1;

    public record Envelope(String marker,int version,byte[] bytes) {
        public Envelope {
            if(bytes!=null) bytes=bytes.clone();
        }
        @Override public byte[] bytes(){return bytes==null?null:bytes.clone();}
    }
    public record Verdict(String code,boolean present,boolean valid,boolean matches,
                          boolean mayPersist,boolean mayRegister,boolean mayBuild,
                          boolean mayDrive,boolean mayDispatch) {}

    public static Envelope stageInMemory(Dev51BAsBuiltPersistenceR30g.Draft draft) {
        Objects.requireNonNull(draft);
        return new Envelope(MARKER,FORMAT_VERSION,Dev51BAsBuiltPersistenceR30g.encode(draft));
    }
    public static Verdict inspect(Envelope storage,Dev51BAsBuiltPersistenceR30g.Draft expected) {
        Objects.requireNonNull(expected);
        if(storage==null) return reject("HOLD_SAVEDDATA_RECORD_ABSENT",false,false,false);
        if(!MARKER.equals(storage.marker())||storage.version()!=FORMAT_VERSION)
            return reject("HOLD_SAVEDDATA_UNKNOWN_SCHEMA",true,false,false);
        byte[] candidate=storage.bytes();
        if(candidate==null||candidate.length==0||candidate.length>4096)
            return reject("HOLD_SAVEDDATA_BAD_LENGTH",true,false,false);
        final Dev51BAsBuiltPersistenceR30g.Draft decoded;
        try {decoded=Dev51BAsBuiltPersistenceR30g.decode(candidate);}
        catch(RuntimeException ex){return reject("HOLD_SAVEDDATA_INVALID_RECORD",true,false,false);}
        if(!Arrays.equals(candidate,Dev51BAsBuiltPersistenceR30g.encode(decoded)))
            return reject("HOLD_SAVEDDATA_NOT_CANONICAL",true,false,false);
        if(!decoded.equals(expected))
            return reject("HOLD_SAVEDDATA_SOURCE_MISMATCH",true,true,false);
        return reject("HOLD_SAVEDDATA_VALID_SOURCE_ONLY_NO_AUTHORITY",true,true,true);
    }
    private static Verdict reject(String verdict,boolean exists,boolean valid,boolean matches) {
        return new Verdict(verdict,exists,valid,matches,false,false,false,false,false);
    }
}
