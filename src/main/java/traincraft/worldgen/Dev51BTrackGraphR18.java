/*
 * TafariCraft – Trains / DEV51B R18
 * Pure-Java, read-only graph observer. No Minecraft/Forge dependency or world writes.
 * Logical reciprocal single-cell connections are NOT native train route certification.
 */
package traincraft.worldgen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Dev51BTrackGraphR18 {
    private Dev51BTrackGraphR18() { }
    public static final int MAX_NODES = 2048;
    public static final int MAX_RADIUS = 96;
    public enum Shape {
        NORTH_SOUTH, EAST_WEST, ASCENDING_EAST, ASCENDING_WEST,
        ASCENDING_NORTH, ASCENDING_SOUTH, SOUTH_EAST, SOUTH_WEST,
        NORTH_WEST, NORTH_EAST
    }
    public enum Kind { EMPTY, PLAIN, COMPLEX, UNLOADED }
    public enum Dir {
        NORTH(0,-1), EAST(1,0), SOUTH(0,1), WEST(-1,0);
        final int dx, dz;
        Dir(int dx, int dz) { this.dx=dx; this.dz=dz; }
        Dir reverse() { return values()[(ordinal()+2)%4]; }
    }
    public record Pos(int x,int y,int z) {
        public Pos step(Dir direction,int rise) {
            return new Pos(x+direction.dx,y+rise,z+direction.dz);
        }
    }
    public record Cell(Kind kind,Shape shape,String blockId,boolean station) {
        public Cell {
            if (kind == Kind.PLAIN && shape == null) throw new IllegalArgumentException("Plain rail needs shape");
            if (kind != Kind.PLAIN && shape != null) throw new IllegalArgumentException("Non-plain has no shape");
            if (blockId == null) blockId = "";
        }
        public static Cell plain(Shape shape, boolean station) {
            return new Cell(Kind.PLAIN,shape,"",station);
        }
        public static Cell other(Kind kind,String id) {
            return new Cell(kind,null,id,false);
        }
    }
    @FunctionalInterface
    public interface Reader { Cell read(Pos position); }

    public record Result(Pos seed,int nodes,long edges,int openPorts,int curves,int slopes,
                         int stationCells,int complexBoundaries,int switchCells,int turnCells,
                         int unloadedBoundaries,int ambiguousPorts,int clippedPorts,
                         boolean capReached,boolean possibleLoop,String verdict,
                         Set<Pos> observedNodes) { }

    public static boolean hasPort(Shape shape,Dir direction) {
        return switch (shape) {
            case NORTH_SOUTH, ASCENDING_NORTH, ASCENDING_SOUTH -> direction==Dir.NORTH || direction==Dir.SOUTH;
            case EAST_WEST, ASCENDING_EAST, ASCENDING_WEST -> direction==Dir.EAST || direction==Dir.WEST;
            case NORTH_EAST -> direction==Dir.NORTH || direction==Dir.EAST;
            case NORTH_WEST -> direction==Dir.NORTH || direction==Dir.WEST;
            case SOUTH_EAST -> direction==Dir.SOUTH || direction==Dir.EAST;
            case SOUTH_WEST -> direction==Dir.SOUTH || direction==Dir.WEST;
        };
    }
    public static int portRise(Shape shape,Dir dir) {
        return (shape==Shape.ASCENDING_NORTH && dir==Dir.NORTH)
                || (shape==Shape.ASCENDING_SOUTH && dir==Dir.SOUTH)
                || (shape==Shape.ASCENDING_EAST && dir==Dir.EAST)
                || (shape==Shape.ASCENDING_WEST && dir==Dir.WEST) ? 1 : 0;
    }
    private static boolean within(Pos p,Pos origin,int radius) {
        return Math.abs((long)p.x()-origin.x())<=radius
                &&Math.abs((long)p.z()-origin.z())<=radius
                &&Math.abs((long)p.y()-origin.y())<=24;
    }
    public static Result scan(Reader reader,Pos start) {return scan(reader,start,MAX_RADIUS,MAX_NODES);}
    public static Result scan(Reader reader,Pos start,int radius,int cap) {
        if (reader==null || start==null || radius<1 || radius>MAX_RADIUS || cap<1 || cap>MAX_NODES)
            throw new IllegalArgumentException("Unsafe scan arguments");
        Cell startCell=reader.read(start);
        if (startCell==null || startCell.kind()!=Kind.PLAIN)
            throw new IllegalArgumentException("Seed is not a verifiable single-cell rail");
        ArrayDeque<Pos> queue=new ArrayDeque<>();
        LinkedHashSet<Pos> visited=new LinkedHashSet<>();
        Map<Pos,Set<Pos>> links=new LinkedHashMap<>();
        Set<Pos> complex=new LinkedHashSet<>();
        Set<Pos> unloaded=new LinkedHashSet<>();
        Set<Pos> ambiguous=new LinkedHashSet<>();
        queue.add(start);
        int clipped=0;
        while (!queue.isEmpty() && visited.size()<cap) {
            Pos at=queue.removeFirst();
            if (!visited.add(at)) continue;
            Cell cell=reader.read(at);
            if (cell==null || cell.kind()!=Kind.PLAIN) continue;
            links.computeIfAbsent(at,k->new LinkedHashSet<>());
            for (Dir direction:Dir.values()) {
                if (!hasPort(cell.shape(),direction)) continue;
                List<Pos> candidates=new ArrayList<>(2);
                for (int dy=-1;dy<=1;dy++) {
                    Pos next=at.step(direction,dy);
                    if (!within(next,start,radius)) { clipped++; continue; }
                    Cell neighbor=reader.read(next);
                    if (neighbor==null) continue;
                    if (neighbor.kind()==Kind.UNLOADED) {unloaded.add(next);continue;}
                    if (neighbor.kind()==Kind.COMPLEX) {complex.add(next);continue;}
                    if (neighbor.kind()!=Kind.PLAIN || !hasPort(neighbor.shape(),direction.reverse())) continue;
                    int hereHeight=at.y()+portRise(cell.shape(),direction);
                    int thereHeight=next.y()+portRise(neighbor.shape(),direction.reverse());
                    if (hereHeight==thereHeight) candidates.add(next);
                }
                if (candidates.size()>1) {ambiguous.add(at);continue;}
                if (candidates.size()==1) {
                    Pos next=candidates.get(0);
                    links.get(at).add(next);
                    links.computeIfAbsent(next,k->new LinkedHashSet<>()).add(at);
                    if (!visited.contains(next)) queue.add(next);
                }
            }
        }
        long degreeSum=0;
        int open=0,curves=0,slopes=0,stations=0;
        for (Pos pos:visited) {
            Cell cell=reader.read(pos);
            if (cell==null || cell.kind()!=Kind.PLAIN) continue;
            int degree=0;
            for (Pos connected:links.getOrDefault(pos,Set.of())) if (visited.contains(connected)) degree++;
            degreeSum+=degree;
            open+=Math.max(0,2-degree);
            Shape shape=cell.shape();
            if (shape==Shape.NORTH_EAST||shape==Shape.NORTH_WEST||shape==Shape.SOUTH_EAST||shape==Shape.SOUTH_WEST) curves++;
            if (shape==Shape.ASCENDING_EAST||shape==Shape.ASCENDING_WEST
                    ||shape==Shape.ASCENDING_NORTH||shape==Shape.ASCENDING_SOUTH) slopes++;
            if (cell.station()) stations++;
        }
        long edges=degreeSum/2;
        int switches=0,turns=0;
        for (Pos pos:complex) {
            String id=reader.read(pos).blockId().toLowerCase(java.util.Locale.ROOT);
            if (id.contains("switch")||id.contains("turnout")) switches++;
            if (id.contains("curve")||id.contains("turn")) turns++;
        }
        boolean capped=!queue.isEmpty();
        boolean loop=visited.size()>=4 && degreeSum==visited.size()*2L
                && edges==visited.size() && !capped && clipped==0
                && unloaded.isEmpty() && ambiguous.isEmpty() && complex.isEmpty();
        String verdict=loop?"CLOSED_SINGLE_CELL_GRAPH_NOT_DRIVE_CERTIFIED":
                capped||clipped>0||!unloaded.isEmpty()?"PARTIAL_LOADED_OR_BOUNDED":
                !complex.isEmpty()||!ambiguous.isEmpty()?"COMPLEX_LINKS_UNRESOLVED":
                "OPEN_SINGLE_CELL_GRAPH";
        return new Result(start,visited.size(),edges,open,curves,slopes,stations,
                complex.size(),switches,turns,unloaded.size(),ambiguous.size(),clipped,
                capped,loop,verdict,Collections.unmodifiableSet(visited));
    }
}
