package traincraft.worldgen;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

/** DEV51B R30i: purely filesystem READ-ONLY backup approval gate. */
public final class Dev51BAsBuiltBackupGateR30i {
    private Dev51BAsBuiltBackupGateR30i(){}
    public static final String WORLD="DEV51B R27 Movement Copy";
    public static final String WITNESS="e6a172d6337927d7ee6f28a8789eed05f18fefe6f0492f3a842d661751b9666d";
    public static final String STAMP="DEV51B-R30I-BACKUP-V1";
    public static final String MARKER="R27-THIS-IS-A-DISPOSABLE-COPY.txt";
    public static final String MARKER_VALUE="R27 DISPOSABLE COPY CONFIRMED";
    public static final String APPROVAL="_DEV51B_R30I/R30I-BACKUP-ATTESTATION.txt";
    public record Result(String status,boolean valid,String backup){}
    public static String sha(Path file) throws Exception {
        MessageDigest sha=MessageDigest.getInstance("SHA-256");
        try(InputStream in=Files.newInputStream(file)){
            byte[] bytes=new byte[32768];int read;
            while((read=in.read(bytes))>=0) if(read>0)sha.update(bytes,0,read);
        }
        return HexFormat.of().formatHex(sha.digest());
    }
    public static Result verify(Path projectRoot,Path worldRoot,String sourceWitness){
        try {
            Path project=projectRoot.toRealPath(),world=worldRoot.toRealPath();
            if(!WORLD.equals(world.getFileName().toString())
                ||!world.equals(project.resolve("run/saves").resolve(WORLD).toRealPath()))
                return bad("HOLD_WRONG_WORLD");
            if(!WITNESS.equals(sourceWitness))return bad("HOLD_SOURCE_FINGERPRINT_MISMATCH");
            Path marker=world.resolve(MARKER);
            if(!Files.isRegularFile(marker,LinkOption.NOFOLLOW_LINKS)
                ||!MARKER_VALUE.equals(Files.readString(marker,StandardCharsets.UTF_8).trim()))
                return bad("HOLD_DISPOSABLE_MARKER");
            Path approval=project.resolve(APPROVAL);
            if(!Files.isRegularFile(approval,LinkOption.NOFOLLOW_LINKS))return bad("HOLD_BACKUP_ATTESTATION_MISSING");
            var age=Duration.between(Files.getLastModifiedTime(approval).toInstant(),Instant.now());
            if(age.isNegative()||age.toHours()>=12)return bad("HOLD_BACKUP_ATTESTATION_STALE");
            List<String> lines=Files.readAllLines(approval,StandardCharsets.UTF_8);
            if(lines.size()!=6||!STAMP.equals(lines.get(0))
                ||!("world="+WORLD).equals(lines.get(1))
                ||!("witness="+WITNESS).equals(lines.get(2)))return bad("HOLD_BACKUP_ATTESTATION_MALFORMED");
            String path=lines.get(3).substring(lines.get(3).startsWith("backup=")?7:0);
            if(!lines.get(3).startsWith("backup=_DEV51B_R30I/world-backups/"))
                return bad("HOLD_BACKUP_PATH_INVALID");
            Path relative=Path.of(path);
            if(relative.isAbsolute()||relative.toString().contains("..")||!relative.equals(relative.normalize()))
                return bad("HOLD_BACKUP_PATH_INVALID");
            Path backup=project.resolve(relative).toRealPath();
            if(!backup.startsWith(project.resolve("_DEV51B_R30I/world-backups").toRealPath())
                ||!backup.getFileName().toString().startsWith("R30I-BACKUP-"))
                return bad("HOLD_BACKUP_PATH_INVALID");
            String manifestLine=lines.get(4),levelLine=lines.get(5);
            if(!manifestLine.matches("manifestSha256=[0-9a-f]{64}")
                ||!levelLine.matches("levelDatSha256=[0-9a-f]{64}"))
                return bad("HOLD_BACKUP_ATTESTATION_MALFORMED");
            Path manifest=backup.resolve("R30I-SHA256-MANIFEST.txt");
            if(!Files.isRegularFile(manifest,LinkOption.NOFOLLOW_LINKS)
                ||!sha(manifest).equals(manifestLine.substring(15)))
                return bad("HOLD_BACKUP_MANIFEST_HASH");
            if(!Files.isRegularFile(backup.resolve("level.dat"),LinkOption.NOFOLLOW_LINKS)
                ||!sha(backup.resolve("level.dat")).equals(levelLine.substring(15)))
                return bad("HOLD_BACKUP_LEVEL_HASH");
            String manifestText=Files.readString(manifest,StandardCharsets.UTF_8);
            if(!manifestText.contains("\tlevel.dat\n")||!manifestText.contains("\t"+MARKER+"\n"))
                return bad("HOLD_BACKUP_FILES_MISSING");
            int checked=0;
            Set<Path> expected=new HashSet<>();
            for(String row:manifestText.split("\n")){
                if(row.isBlank())continue;
                String[] parts=row.split("\t",2);
                if(parts.length!=2||!parts[0].matches("[0-9a-f]{64}"))return bad("HOLD_BACKUP_MANIFEST_INVALID");
                Path rel=Path.of(parts[1]);
                if(rel.isAbsolute()||!rel.normalize().equals(rel)||rel.startsWith(".."))
                    return bad("HOLD_BACKUP_MANIFEST_INVALID");
                Path file=backup.resolve(rel);
                if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||!sha(file).equals(parts[0]))
                    return bad("HOLD_BACKUP_FILE_CHANGED");
                if(!expected.add(file.toRealPath()))return bad("HOLD_BACKUP_MANIFEST_DUPLICATE");
                checked++;
            }
            try(Stream<Path> found=Files.walk(backup)){
                long actual=found.filter(Files::isRegularFile).filter(p->!p.equals(manifest)).count();
                if(checked<3||actual!=checked)return bad("HOLD_BACKUP_MANIFEST_COVERAGE");
            }
            return new Result("BACKUP_VERIFIED",true,relative.toString());
        }catch(Exception e){return bad("HOLD_BACKUP_VERIFICATION_FAILED");}
    }
    private static Result bad(String code){return new Result(code,false,"");}
}
