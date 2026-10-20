package traincraft.worldgen;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * DEV51B R30g: IN-MEMORY ONLY persistence-format preflight.
 * No SavedData or Minecraft classes are referenced; no filesystem/world writes.
 * This is a proposed SOURCE-ONLY, nine-rail record, never a constructed route.
 */
public final class Dev51BAsBuiltPersistenceR30g {
    private Dev51BAsBuiltPersistenceR30g() {}
    public static final String SCHEMA="TC_DEV51B_AS_BUILT_SOURCE_V1_DRAFT";
    public static final String DIMENSION="minecraft:overworld";
    public static final String STATUS="OBSERVED_SOURCE_ONLY_UNREGISTERED";
    public static final int VERSION=1, EXISTING_RAILS=9, MAGIC=0x54435247;
    private static final int MAX_BYTES=4096;

    public record Draft(String schema,int version,String dimension,int x,int y,int z,
                        int minX,int maxX,int rails,String railShape,String status,
                        String sourceFingerprint,boolean networkRegistered,
                        boolean mayPlaceRails,boolean mayDrive,boolean mayDispatch) {
        public Draft {
            if(!SCHEMA.equals(schema)||version!=VERSION||!DIMENSION.equals(dimension)
                    ||x!=61||y!=-60||z!=-3||minX!=53||maxX!=61||rails!=EXISTING_RAILS
                    ||!"EAST_WEST".equals(railShape)||!STATUS.equals(status)
                    ||sourceFingerprint==null||!sourceFingerprint.matches("[0-9a-f]{64}")
                    ||networkRegistered||mayPlaceRails||mayDrive||mayDispatch)
                throw new IllegalArgumentException("Invalid R30g source-only draft");
        }
    }
    public record Result(String verdict,boolean formatRoundTrip,
            boolean sourceWitnessMatches,boolean rollbackUnchanged,
            boolean storageImplemented,boolean savedDataWritten,
            boolean mayPersist,boolean mayBuild,boolean mayDrive,boolean mayDispatch,
            String formatSha256,int encodedBytes) {}

    private static byte[] shaBytes(byte[] b) {
        try{return MessageDigest.getInstance("SHA-256").digest(b);}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private static String hex(byte[] b){return HexFormat.of().formatHex(shaBytes(b));}
    private static byte[] core(Draft d) {
        try {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            DataOutputStream out=new DataOutputStream(bytes);
            out.writeInt(MAGIC);out.writeInt(d.version());out.writeUTF(d.schema());
            out.writeUTF(d.dimension());out.writeInt(d.x());out.writeInt(d.y());out.writeInt(d.z());
            out.writeInt(d.minX());out.writeInt(d.maxX());out.writeInt(d.rails());
            out.writeUTF(d.railShape());out.writeUTF(d.status());out.writeUTF(d.sourceFingerprint());
            out.writeBoolean(d.networkRegistered());out.writeBoolean(d.mayPlaceRails());
            out.writeBoolean(d.mayDrive());out.writeBoolean(d.mayDispatch());
            out.flush();return bytes.toByteArray();
        }catch(IOException e){throw new IllegalArgumentException("Codec write error",e);}
    }
    /** Deterministic self-checksummed proposed wire encoding; NEVER writes a file. */
    public static byte[] encode(Draft d) {
        Objects.requireNonNull(d);
        byte[] payload=core(d);
        if(payload.length+32>MAX_BYTES)throw new IllegalArgumentException("Draft too large");
        byte[] result=Arrays.copyOf(payload,payload.length+32);
        System.arraycopy(shaBytes(payload),0,result,payload.length,32);
        return result;
    }
    /** Requires exact version/schema, no unknown fields, and an intact hash. */
    public static Draft decode(byte[] bytes) {
        if(bytes==null||bytes.length<36||bytes.length>MAX_BYTES)throw new IllegalArgumentException("Invalid wire length");
        byte[] payload=Arrays.copyOf(bytes,bytes.length-32);
        if(!MessageDigest.isEqual(shaBytes(payload),Arrays.copyOfRange(bytes,bytes.length-32,bytes.length)))
            throw new IllegalArgumentException("Checksum mismatch");
        try {
            DataInputStream in=new DataInputStream(new ByteArrayInputStream(payload));
            if(in.readInt()!=MAGIC)throw new IllegalArgumentException("Magic mismatch");
            int version=in.readInt();
            String schema=in.readUTF(),dim=in.readUTF();
            int x=in.readInt(),y=in.readInt(),z=in.readInt();
            int lo=in.readInt(),hi=in.readInt(),rails=in.readInt();
            String shape=in.readUTF(),status=in.readUTF(),fingerprint=in.readUTF();
            boolean reg=in.readBoolean(),place=in.readBoolean(),drive=in.readBoolean(),dispatch=in.readBoolean();
            if(in.available()!=0)throw new IllegalArgumentException("Unknown trailing fields");
            return new Draft(schema,version,dim,x,y,z,lo,hi,rails,shape,status,fingerprint,
                    reg,place,drive,dispatch);
        } catch(IOException ex){throw new IllegalArgumentException("Malformed proposed record",ex);}
    }
    public static Draft draftFor(Dev51BAsBuiltRecoveryR30f.Report report) {
        if(report==null||!"HOLD_AS_BUILT_RECORD_NOT_PERSISTED_REVIEW_ONLY".equals(report.verdict())
                ||report.firstHold()!=-1||report.existingRail()!=9||report.proposedAir()!=16
                ||report.sourceStationRails()!=1||report.additionalStationRails()!=0
                ||report.legacyNetworkRecorded()||report.legacyWideRecorded()
                ||report.hasLegacyOutbound()||report.secondStationRecorded()
                ||report.recordExistsInNewSchema()||!report.sourceStationRecorded()
                ||report.isR30TestStop()||report.mayPersistRecord()||report.mayBuildRails()
                ||report.mayMoveTrain()||report.mayDispatch())
            throw new IllegalArgumentException("R30f witness is not eligible for schema preflight");
        return new Draft(SCHEMA,VERSION,DIMENSION,61,-60,-3,53,61,EXISTING_RAILS,
                "EAST_WEST",STATUS,report.sourceFingerprint(),false,false,false,false);
    }
    public static Result preflight(Dev51BAsBuiltRecoveryR30f.Report report) {
        if(report==null)throw new IllegalArgumentException("Missing R30f report");
        if(!"HOLD_AS_BUILT_RECORD_NOT_PERSISTED_REVIEW_ONLY".equals(report.verdict()))
            return new Result("HOLD_SOURCE_WITNESS_"+report.verdict(),false,false,true,false,false,
                    false,false,false,false,"NONE",0);
        Draft draft=draftFor(report);
        byte[] encoded=encode(draft);
        Draft reloaded=decode(encoded.clone());
        boolean match=reloaded.sourceFingerprint().equals(report.sourceFingerprint());
        boolean roundTrip=reloaded.equals(draft)&&Arrays.equals(encoded,encode(reloaded));
        // Exercise both hypothetical in-memory transaction states: initially
        // absent and previously present. This NEVER touches actual storage.
        String key="source:61,-60,-3";
        Map<String,byte[]> scratch=new HashMap<>();
        scratch.put(key,encoded.clone()); // candidate staged in memory
        boolean verifiedStage=decode(scratch.get(key)).equals(draft);
        scratch.remove(key); // abort initial creation
        boolean absentRestored=!scratch.containsKey(key);
        byte[] prior="PREEXISTING_RECORD_UNCHANGED".getBytes(StandardCharsets.UTF_8);
        scratch.put(key,prior.clone());
        byte[] priorSnapshot=scratch.get(key).clone();
        scratch.put(key,encoded.clone()); // hypothetical replacement
        scratch.put(key,priorSnapshot); // abort replacement
        boolean priorRestored=Arrays.equals(prior,scratch.get(key));
        boolean rollback=verifiedStage&&absentRestored&&priorRestored;
        String verdict=roundTrip&&match&&rollback?
                "HOLD_STORAGE_NOT_IMPLEMENTED_FORMAT_PREFLIGHT_PASS":
                "HOLD_FORMAT_ROUNDTRIP_OR_ROLLBACK_FAILED";
        return new Result(verdict,roundTrip,match,rollback,false,false,
                false,false,false,false,hex(encoded),encoded.length);
    }
}
