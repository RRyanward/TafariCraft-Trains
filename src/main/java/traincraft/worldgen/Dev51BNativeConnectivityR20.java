/* TafariCraft - Trains / DEV51B R20
 * Pure Java 17, READ-ONLY native-assembly ownership and endpoint handshake audit.
 * This reports observational adjacency, never certifies turnout states or train driving.
 */
package traincraft.worldgen;

import java.util.*;

public final class Dev51BNativeConnectivityR20 {
    private Dev51BNativeConnectivityR20() { }
    public static final int MAX_ASSEMBLIES = 48;
    public static final int MAX_PARTS = 64;
    public enum Facing { NORTH(0,-1),EAST(1,0),SOUTH(0,1),WEST(-1,0);
        public final int dx,dz;
        Facing(int x,int z){dx=x;dz=z;}
        public Facing reverse(){return values()[(ordinal()+2)%4];}
    }
    public record Pos(int x,int y,int z) {
        public Pos move(Facing f){return new Pos(x+f.dx,y,z+f.dz);}
    }
    public record Expected(int part,Pos position) { }
    public record Port(Pos position,Facing out,boolean logicalOffset) { }
    public record Piece(String key,String kind,boolean dynamic,List<Expected> parts,List<Port> ports) {
        public Piece {
            if(key==null||key.isBlank()||key.length()>160||kind==null||kind.isBlank()||
                    parts==null||parts.isEmpty()||parts.size()>MAX_PARTS||ports==null||ports.isEmpty()||ports.size()>16)
                throw new IllegalArgumentException("Unsafe native assembly definition");
            parts=List.copyOf(parts); ports=List.copyOf(ports);
            Set<Integer> ids=new HashSet<>(); Set<Pos> positions=new HashSet<>();
            for(Expected e:parts)if(e==null||e.position()==null||e.part()<0||e.part()>=MAX_PARTS||
                    !ids.add(e.part())||!positions.add(e.position())) throw new IllegalArgumentException("Repeated/invalid native part");
            for(Port p:ports)if(p==null||p.position()==null||p.out()==null)
                throw new IllegalArgumentException("Bad native endpoint");
        }
    }
    public enum State { MATCH,UNLOADED,MISSING,FOREIGN }
    @FunctionalInterface public interface PartReader { State check(String owner,int part,Pos position); }
    public enum NeighborKind { PLAIN,COMPLEX,UNLOADED,EMPTY }
    public record Neighbor(NeighborKind kind,Set<Facing> ports){
        public Neighbor { if(kind==null||ports==null)throw new IllegalArgumentException();ports=Set.copyOf(ports); }
        public static Neighbor plain(Facing... port){return new Neighbor(NeighborKind.PLAIN,Set.of(port));}
        public static Neighbor other(NeighborKind kind){return new Neighbor(kind,Set.of());}
    }
    @FunctionalInterface public interface NeighborReader { Neighbor read(Pos pos); }
    public record Audit(int pieces,int verified,int incomplete,int foreign,int dynamicPieces,
                        int endpointCount,int nativePairs,int plainContacts,int openEnds,
                        int unknownNeighbors,int logicalOffsetPorts,boolean capReached,
                        String verdict,List<String> warnings) { }

    public static Audit audit(Collection<Piece> input,PartReader parts,NeighborReader neighbors,
                              boolean candidatesClipped) {
        if(input==null||parts==null||neighbors==null||input.size()>512)
            throw new IllegalArgumentException("Unsafe R20 audit inputs");
        List<Piece> ordered=new ArrayList<>(input);
        ordered.sort(Comparator.comparing(Piece::key));
        boolean capped=ordered.size()>MAX_ASSEMBLIES||candidatesClipped;
        if(ordered.size()>MAX_ASSEMBLIES)ordered=new ArrayList<>(ordered.subList(0,MAX_ASSEMBLIES));
        Set<String> seen=new HashSet<>();
        Map<String,Boolean> complete=new HashMap<>();
        int verified=0,incomplete=0,foreign=0,dynamic=0,endpointCount=0,offsets=0;
        List<String> issues=new ArrayList<>();
        for(Piece piece:ordered){
            if(!seen.add(piece.key()))throw new IllegalArgumentException("Duplicate native owner");
            boolean ok=true;
            for(Expected e:piece.parts()){
                State actual=parts.check(piece.key(),e.part(),e.position());
                if(actual==null)throw new IllegalArgumentException("Missing part reader state");
                if(actual!=State.MATCH){ok=false;if(actual==State.FOREIGN)foreign++;
                    if(issues.size()<16)issues.add(piece.key()+" part="+e.part()+" state="+actual);}
            }
            complete.put(piece.key(),ok);
            if(ok)verified++;else incomplete++;
            if(piece.dynamic())dynamic++;
            endpointCount+=piece.ports().size();
            for(Port p:piece.ports())if(p.logicalOffset())offsets++;
        }
        // Deduplicate reciprocal port pairs; never treat a path through an
        // incompletely loaded assembly or a dynamic turnout as drive-safe.
        Set<String> joined=new HashSet<>();
        int pairs=0,plain=0,open=0,unknown=0;
        for(Piece piece:ordered)for(Port port:piece.ports()){
            if(!complete.get(piece.key())||port.logicalOffset()){unknown++;continue;}
            Pos next=port.position().move(port.out());
            boolean found=false;
            for(Piece target:ordered){
                if(!complete.get(target.key())||target.key().equals(piece.key()))continue;
                for(Port counterpart:target.ports()){
                    if(counterpart.logicalOffset()||!counterpart.position().equals(next)||
                            counterpart.out()!=port.out().reverse())continue;
                    // Each native-to-native endpoint handshake is counted once.
                    String a=piece.key()+"@"+port.position(),b=target.key()+"@"+counterpart.position();
                    String join=a.compareTo(b)<0?a+"<->"+b:b+"<->"+a;
                    if(joined.add(join))pairs++;
                    found=true;
                }
            }
            if(found)continue;
            Neighbor nearby=neighbors.read(next);
            if(nearby==null||nearby.kind()==NeighborKind.UNLOADED||nearby.kind()==NeighborKind.COMPLEX){unknown++;continue;}
            if(nearby.kind()==NeighborKind.PLAIN && nearby.ports().contains(port.out().reverse()))plain++;
            else open++;
        }
        String verdict=capped||incomplete>0||unknown>0||offsets>0||dynamic>0||foreign>0?
                "PARTIAL_NATIVE_TOPOLOGY_NOT_DRIVE_CERTIFIED":
                "OBSERVED_NATIVE_ENDPOINTS_NOT_DRIVE_CERTIFIED";
        return new Audit(ordered.size(),verified,incomplete,foreign,dynamic,endpointCount,
                pairs,plain,open,unknown,offsets,capped,verdict,List.copyOf(issues));
    }
}
