package traincraft.worldgen;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;
/** Read-only approval verifier. Backup creation is a separate OFFLINE Java 17 command. */
public final class Dev51BWestTrialBackupR30n {
 private Dev51BWestTrialBackupR30n(){}
 public static final String WORLD="DEV51B R27 Movement Copy";
 public static final String ATTEST="_DEV51B_R30N/R30N-WORLD-BACKUP-APPROVAL.txt";
 public static final String MARKER="R27-THIS-IS-A-DISPOSABLE-COPY.txt";
 public static final String MARKER_TEXT="R27 DISPOSABLE COPY CONFIRMED";
 public static final String KEY="data/tc_dev51b_asbuilt_source_v1_registered.dat";
 public static final String STAMP="TC-DEV51B-R30N-PRE-MOVEMENT-BACKUP-V1";
 public static String sha(Path p)throws Exception{
  MessageDigest d=MessageDigest.getInstance("SHA-256");
  try(InputStream in=Files.newInputStream(p)){
   byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)d.update(b,0,n);
  }
  return HexFormat.of().formatHex(d.digest());
 }
 public static boolean verify(Path project,Path world) {
  try{
   project=project.toRealPath();world=world.toRealPath();
   Path expected=project.resolve("run/saves").resolve(WORLD).toRealPath();
   if(!world.equals(expected)||!WORLD.equals(world.getFileName().toString()))return false;
   if(!MARKER_TEXT.equals(Files.readString(world.resolve(MARKER),StandardCharsets.UTF_8).trim()))return false;
   Path stamp=project.resolve(ATTEST);
   if(!Files.isRegularFile(stamp,LinkOption.NOFOLLOW_LINKS))return false;
   Duration age=Duration.between(Files.getLastModifiedTime(stamp).toInstant(),Instant.now());
   if(age.isNegative()||age.toHours()>=12)return false;
   var lines=Files.readAllLines(stamp,StandardCharsets.UTF_8);
   if(lines.size()!=5||!STAMP.equals(lines.get(0))||!("world="+WORLD).equals(lines.get(1))
       ||!lines.get(2).startsWith("backup=_DEV51B_R30N/world-backups/")
       ||!lines.get(3).matches("manifestSha256=[0-9a-f]{64}")
       ||!lines.get(4).matches("recordSha256=[0-9a-f]{64}"))return false;
   Path relative=Path.of(lines.get(2).substring(7));
   if(relative.isAbsolute()||!relative.normalize().equals(relative)||relative.startsWith(".."))return false;
   Path backup=project.resolve(relative).toRealPath();
   if(!backup.startsWith(project.resolve("_DEV51B_R30N/world-backups").toRealPath()))return false;
   Path manifest=backup.resolve("R30N-SHA256-MANIFEST.txt");
   if(!sha(manifest).equals(lines.get(3).substring(15))
       ||!sha(backup.resolve(KEY)).equals(lines.get(4).substring(13)))return false;
   var hashes=Files.readAllLines(manifest,StandardCharsets.UTF_8);
   Set<Path> checked=new HashSet<>();
   for(String line:hashes){
     String[] pair=line.split("\\t",2);
     if(pair.length!=2||!pair[0].matches("[0-9a-f]{64}"))return false;
     Path rel=Path.of(pair[1]);
     if(rel.isAbsolute()||!rel.equals(rel.normalize())||rel.startsWith(".."))return false;
     Path f=backup.resolve(rel);
     if(!Files.isRegularFile(f,LinkOption.NOFOLLOW_LINKS)||!sha(f).equals(pair[0])||!checked.add(f.toRealPath()))return false;
   }
   if(hashes.size()<4||!Files.isRegularFile(backup.resolve("level.dat"))
      ||!Files.isRegularFile(backup.resolve(MARKER))||!checked.contains(backup.resolve(KEY).toRealPath()))return false;
   try(Stream<Path> walk=Files.walk(backup)){
     long actual=walk.filter(Files::isRegularFile).filter(f->!f.equals(manifest)).count();
     return actual==checked.size();
   }
  }catch(Exception bad){return false;}
 }
}
