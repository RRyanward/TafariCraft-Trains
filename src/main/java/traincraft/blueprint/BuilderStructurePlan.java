package traincraft.blueprint;

// TC_DEV34_R1B2_STRUCTURE_PLAN_NORMAL_STATE

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import traincraft.item.TrackBlueprintItem;

import java.util.Optional;

/**
 * Dev34 side-car structure plan stored inside the existing TafariTrackBlueprint
 * compound. The frozen TrackBlueprintItem exact track solver is intentionally
 * not modified.
 *
 * r1b2 stores normal BlockState properties in addition to the block registry
 * id, while still reading r1a entries that only contain "Block".
 */
public final class BuilderStructurePlan {
    public static final String ROOT = "TafariTrackBlueprint";
    public static final String BUILD_BLOCKS = "BuildBlocks";
    public static final String PROPERTIES = "Properties";

    public static final int MAX_BUILD_BLOCKS = 4096;

    public static final double CLEARANCE_RADIUS_XZ = 3.25D;
    public static final double CLEARANCE_BELOW = 1.00D;
    public static final double CLEARANCE_ABOVE = 4.25D;

    // TC_DEV34_R1B5_FULL_BLOCK_CLEARANCE
    //
    // A Build Mode entry represents a complete Minecraft block volume, not a
    // dimensionless point at that block's center. r1b4 correctly reconstructed
    // the train path, but the final distance query still tested only
    // BlockPos + (0.5,0.5,0.5). That leaves a narrow boundary hole wherever the
    // block center is just outside the clearance envelope while part of the
    // 1x1x1 cube still intersects it (most visible at open track connectors
    // and at the top of the vertical clearance envelope).
    //
    // Inflate point-to-path clearance by the farthest horizontal point in a
    // unit block and by half its vertical height. This is intentionally
    // conservative by at most ~0.207 blocks on an axis and guarantees that no
    // part of the planned cube intrudes into the accepted train envelope.
    private static final double BUILD_BLOCK_HALF_DIAGONAL_XZ =
            Math.sqrt(0.5D);
    private static final double BUILD_BLOCK_HALF_HEIGHT =
            0.5D;

    private BuilderStructurePlan() {}

    public record GhostHit(
            BlockPos pos,
            Direction face,
            Vec3 location,
            double distanceSq) {}

    private static CompoundTag readData(ItemStack blueprint) {
        CompoundTag root = blueprint.getTag();

        if (root == null
                || !root.contains(ROOT, Tag.TAG_COMPOUND)) {
            return null;
        }

        return root.getCompound(ROOT);
    }

    private static CompoundTag writeData(ItemStack blueprint) {
        CompoundTag root = blueprint.getOrCreateTag();

        if (!root.contains(ROOT, Tag.TAG_COMPOUND)) {
            root.put(ROOT, new CompoundTag());
        }

        CompoundTag data = root.getCompound(ROOT);

        if (!data.contains(BUILD_BLOCKS, Tag.TAG_LIST)) {
            data.put(BUILD_BLOCKS, new ListTag());
        }

        root.put(ROOT, data);
        return data;
    }

    public static boolean hasTrackOrigin(ItemStack blueprint) {
        CompoundTag data = readData(blueprint);

        return data != null
                && data.contains("Origin", Tag.TAG_LONG);
    }

    public static BlockPos trackOrigin(ItemStack blueprint) {
        return hasTrackOrigin(blueprint)
                ? TrackBlueprintItem.origin(blueprint)
                : BlockPos.ZERO;
    }

    public static int count(ItemStack blueprint) {
        CompoundTag data = readData(blueprint);

        return data == null
                ? 0
                : data.getList(
                        BUILD_BLOCKS,
                        Tag.TAG_COMPOUND)
                        .size();
    }

    public static CompoundTag entry(
            ItemStack blueprint,
            int index) {
        CompoundTag data = readData(blueprint);

        if (data == null) {
            return new CompoundTag();
        }

        ListTag list =
                data.getList(
                        BUILD_BLOCKS,
                        Tag.TAG_COMPOUND);

        return index >= 0
                && index < list.size()
                ? list.getCompound(index)
                : new CompoundTag();
    }

    public static BlockPos relativePos(CompoundTag entry) {
        return new BlockPos(
                entry.getInt("X"),
                entry.getInt("Y"),
                entry.getInt("Z"));
    }

    public static BlockPos worldPos(
            ItemStack blueprint,
            CompoundTag entry) {
        return trackOrigin(blueprint)
                .offset(relativePos(entry));
    }

    public static String blockId(CompoundTag entry) {
        return entry.getString("Block");
    }

    public static BlockState blockState(CompoundTag entry) {
        ResourceLocation id =
                ResourceLocation.tryParse(
                        blockId(entry));

        if (id == null) {
            return Blocks.AIR.defaultBlockState();
        }

        Block block =
                ForgeRegistries.BLOCKS
                        .getValue(id);

        if (block == null) {
            return Blocks.AIR.defaultBlockState();
        }

        BlockState state =
                block.defaultBlockState();

        if (!entry.contains(
                PROPERTIES,
                Tag.TAG_COMPOUND)) {
            // Backward compatibility with Dev34 r1a entries.
            return state;
        }

        CompoundTag properties =
                entry.getCompound(PROPERTIES);

        for (Property<?> property :
                state.getProperties()) {
            String name =
                    property.getName();

            if (!properties.contains(
                    name,
                    Tag.TAG_STRING)) {
                continue;
            }

            state =
                    applyProperty(
                            state,
                            property,
                            properties.getString(name));
        }

        return state;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState applyProperty(
            BlockState state,
            Property property,
            String value) {
        Optional parsed =
                property.getValue(value);

        if (parsed.isEmpty()) {
            return state;
        }

        return state.setValue(
                property,
                (Comparable) parsed.get());
    }

    private static CompoundTag writeProperties(
            BlockState state) {
        CompoundTag result =
                new CompoundTag();

        for (Property<?> property :
                state.getProperties()) {
            writeProperty(
                    result,
                    state,
                    property);
        }

        return result;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void writeProperty(
            CompoundTag target,
            BlockState state,
            Property property) {
        Comparable value =
                (Comparable)
                        state.getValue(property);

        target.putString(
                property.getName(),
                property.getName(value));
    }

    public static int findAt(
            ItemStack blueprint,
            BlockPos worldPos) {
        if (!hasTrackOrigin(blueprint)) {
            return -1;
        }

        BlockPos relative =
                worldPos.subtract(
                        trackOrigin(blueprint));

        CompoundTag data = readData(blueprint);

        if (data == null) {
            return -1;
        }

        ListTag list =
                data.getList(
                        BUILD_BLOCKS,
                        Tag.TAG_COMPOUND);

        for (int i = 0;
             i < list.size();
             i++) {
            CompoundTag entry =
                    list.getCompound(i);

            if (entry.getInt("X") == relative.getX()
                    && entry.getInt("Y") == relative.getY()
                    && entry.getInt("Z") == relative.getZ()) {
                return i;
            }
        }

        return -1;
    }

    public static boolean removeAt(
            ItemStack blueprint,
            BlockPos worldPos) {
        int index =
                findAt(
                        blueprint,
                        worldPos);

        if (index < 0) {
            return false;
        }

        CompoundTag data =
                writeData(blueprint);

        ListTag list =
                data.getList(
                        BUILD_BLOCKS,
                        Tag.TAG_COMPOUND);

        list.remove(index);
        data.put(BUILD_BLOCKS, list);

        blueprint.getOrCreateTag()
                .put(ROOT, data);

        return true;
    }

    public static boolean upsert(
            ItemStack blueprint,
            BlockPos worldPos,
            BlockState state) {
        if (state == null
                || state.isAir()
                || !hasTrackOrigin(blueprint)) {
            return false;
        }

        ResourceLocation id =
                ForgeRegistries.BLOCKS
                        .getKey(
                                state.getBlock());

        if (id == null) {
            return false;
        }

        CompoundTag data =
                writeData(blueprint);

        ListTag list =
                data.getList(
                        BUILD_BLOCKS,
                        Tag.TAG_COMPOUND);

        BlockPos relative =
                worldPos.subtract(
                        trackOrigin(blueprint));

        int existing = -1;

        for (int i = 0;
             i < list.size();
             i++) {
            CompoundTag entry =
                    list.getCompound(i);

            if (entry.getInt("X") == relative.getX()
                    && entry.getInt("Y") == relative.getY()
                    && entry.getInt("Z") == relative.getZ()) {
                existing = i;
                break;
            }
        }

        CompoundTag entry =
                new CompoundTag();

        entry.putInt(
                "X",
                relative.getX());
        entry.putInt(
                "Y",
                relative.getY());
        entry.putInt(
                "Z",
                relative.getZ());
        entry.putString(
                "Block",
                id.toString());
        entry.put(
                PROPERTIES,
                writeProperties(state));

        if (existing >= 0) {
            list.set(existing, entry);
        }
        else {
            if (list.size()
                    >= MAX_BUILD_BLOCKS) {
                return false;
            }

            list.add(entry);
        }

        data.put(
                BUILD_BLOCKS,
                list);

        blueprint.getOrCreateTag()
                .put(ROOT, data);

        return true;
    }

    /**
     * Full-cube editor hit target for planned ghosts.
     *
     * This intentionally uses the Blueprint cell rather than the rendered
     * collision shape. It makes building from translucent ghosts predictable:
     * every planned cell is easy to click even when the visual block is a
     * stair, slab, fence, etc.
     */
    public static GhostHit pickGhost(
            Player player,
            ItemStack blueprint,
            Vec3 rayEnd) {
        if (player == null
                || rayEnd == null
                || !hasTrackOrigin(blueprint)) {
            return null;
        }

        Vec3 start =
                player.getEyePosition();

        GhostHit best = null;

        int total =
                count(blueprint);

        for (int i = 0;
             i < total;
             i++) {
            CompoundTag entry =
                    entry(
                            blueprint,
                            i);

            BlockPos pos =
                    worldPos(
                            blueprint,
                            entry);

            BlockState planned =
                    blockState(entry);

            if (planned.isAir()) {
                continue;
            }

            // Once this exact block is actually built, it stops being a ghost
            // target and normal Minecraft world picking takes over.
            if (player.level()
                    .getBlockState(pos)
                    .equals(planned)) {
                continue;
            }

            AABB box =
                    new AABB(pos);

            Optional<Vec3> clipped =
                    box.clip(
                            start,
                            rayEnd);

            if (clipped.isEmpty()) {
                continue;
            }

            Vec3 hit =
                    clipped.get();

            double distanceSq =
                    start.distanceToSqr(hit);

            if (best != null
                    && distanceSq
                    >= best.distanceSq()) {
                continue;
            }

            best =
                    new GhostHit(
                            pos,
                            faceForHit(
                                    pos,
                                    hit),
                            hit,
                            distanceSq);
        }

        return best;
    }

    private static Direction faceForHit(
            BlockPos pos,
            Vec3 hit) {
        double x =
                hit.x - pos.getX();
        double y =
                hit.y - pos.getY();
        double z =
                hit.z - pos.getZ();

        double best =
                Math.abs(x);
        Direction face =
                Direction.WEST;

        double value =
                Math.abs(1.0D - x);
        if (value < best) {
            best = value;
            face = Direction.EAST;
        }

        value = Math.abs(y);
        if (value < best) {
            best = value;
            face = Direction.DOWN;
        }

        value = Math.abs(1.0D - y);
        if (value < best) {
            best = value;
            face = Direction.UP;
        }

        value = Math.abs(z);
        if (value < best) {
            best = value;
            face = Direction.NORTH;
        }

        value = Math.abs(1.0D - z);
        if (value < best) {
            face = Direction.SOUTH;
        }

        return face;
    }

    // TC_DEV34_R1B3_WORLD_TRACK_CLEARANCE
    //
    // BLOCK work must respect both:
    //   A) the route recorded inside this Blueprint, and
    //   B) real rail infrastructure already present in the world.
    //
    // This closes the r1b2 hole where a structure Blueprint could be authored
    // directly through an existing Traincraft/vanilla rail line.
    public static boolean tooCloseToAnyTrack(
            Level level,
            ItemStack blueprint,
            BlockPos worldPos) {
        return tooCloseToTrack(
                        blueprint,
                        worldPos)
                || tooCloseToWorldTrack(
                        level,
                        worldPos);
    }

    // TC_DEV34_R1B4_USE_GEOMETRY_CLEARANCE
    public static boolean tooCloseToWorldTrack(
            Level level,
            BlockPos worldPos) {
        return BuilderTrackClearanceGeometry
                .tooClose(
                        level,
                        worldPos,
                        CLEARANCE_RADIUS_XZ
                                + BUILD_BLOCK_HALF_DIAGONAL_XZ,
                        CLEARANCE_BELOW
                                + BUILD_BLOCK_HALF_HEIGHT,
                        CLEARANCE_ABOVE
                                + BUILD_BLOCK_HALF_HEIGHT);
    }

    private static boolean isWorldTrack(
            BlockState state) {
        if (state == null
                || state.isAir()) {
            return false;
        }

        if (state.getBlock()
                instanceof BaseRailBlock) {
            return true;
        }

        ResourceLocation key =
                ForgeRegistries.BLOCKS
                        .getKey(
                                state.getBlock());

        return key != null
                && "traincraft".equals(
                        key.getNamespace())
                && key.getPath()
                        .startsWith(
                                "track_");
    }
    public static boolean tooCloseToTrack(
            ItemStack blueprint,
            BlockPos worldPos) {
        if (!hasTrackOrigin(blueprint)) {
            return false;
        }

        BlockPos relative =
                worldPos.subtract(
                        trackOrigin(blueprint));

        return tooCloseRelative(
                blueprint,
                relative);
    }

    // TC_DEV34_R1B6_BLUEPRINT_ROUTE_EXACT_GEOMETRY
    public static boolean tooCloseRelative(
            ItemStack blueprint,
            BlockPos relative) {
        CompoundTag data =
                readData(blueprint);

        if (data == null) {
            return false;
        }

        ListTag segments =
                data.getList(
                        "Segments",
                        Tag.TAG_COMPOUND);

        double px =
                relative.getX() + 0.5D;
        double py =
                relative.getY() + 0.5D;
        double pz =
                relative.getZ() + 0.5D;

        for (int i = 0;
             i < segments.size();
             i++) {
            CompoundTag segment =
                    segments.getCompound(i);

            String itemId =
                    segment.getString(
                            "Item");

            if (BuilderTrackClearanceGeometry
                    .supportsPlannedTrack(
                            itemId)) {
                BlockPos root =
                        new BlockPos(
                                segment.getInt("X"),
                                segment.getInt("Y"),
                                segment.getInt("Z"));

                Direction facing =
                        Direction.fromYRot(
                                segment.getFloat(
                                        "Yaw"));

                boolean left =
                        segment.getBoolean(
                                "Left");

                if (BuilderTrackClearanceGeometry
                        .plannedTrackTooClose(
                                itemId,
                                left,
                                root,
                                facing,
                                px,
                                py,
                                pz,
                                CLEARANCE_RADIUS_XZ
                                        + BUILD_BLOCK_HALF_DIAGONAL_XZ,
                                CLEARANCE_BELOW
                                        + BUILD_BLOCK_HALF_HEIGHT,
                                CLEARANCE_ABOVE
                                        + BUILD_BLOCK_HALF_HEIGHT)) {
                    return true;
                }

                // Critical r1b6 behavior: once a stored track family is
                // geometry-aware, do NOT fall through to the old root->exit
                // straight chord. That chord is what blocked the large empty
                // interior visible inside curved Blueprint routes.
                continue;
            }

            // Fallback for simple/legacy tracks that do not have a dedicated
            // geometry spec. This preserves r1b5 behavior for straight pieces.
            double ax =
                    segment.getInt("X") + 0.5D;
            double ay =
                    segment.getInt("Y") + 0.5D;
            double az =
                    segment.getInt("Z") + 0.5D;

            double bx = ax;
            double by = ay;
            double bz = az;

            if (segment.contains("RouteExitX", Tag.TAG_INT)
                    && segment.contains("RouteExitY", Tag.TAG_INT)
                    && segment.contains("RouteExitZ", Tag.TAG_INT)) {
                bx =
                        segment.getInt("RouteExitX")
                                + 0.5D;
                by =
                        segment.getInt("RouteExitY")
                                + 0.5D;
                bz =
                        segment.getInt("RouteExitZ")
                                + 0.5D;
            }
            else if (i + 1
                    < segments.size()) {
                CompoundTag next =
                        segments.getCompound(i + 1);

                bx =
                        next.getInt("X") + 0.5D;
                by =
                        next.getInt("Y") + 0.5D;
                bz =
                        next.getInt("Z") + 0.5D;
            }

            double minY =
                    Math.min(ay, by)
                            - CLEARANCE_BELOW
                            - BUILD_BLOCK_HALF_HEIGHT;

            double maxY =
                    Math.max(ay, by)
                            + CLEARANCE_ABOVE
                            + BUILD_BLOCK_HALF_HEIGHT;

            if (py < minY
                    || py > maxY) {
                continue;
            }

            if (distancePointToSegmentXZ(
                    px,
                    pz,
                    ax,
                    az,
                    bx,
                    bz)
                    <= CLEARANCE_RADIUS_XZ
                            + BUILD_BLOCK_HALF_DIAGONAL_XZ) {
                return true;
            }
        }

        return false;
    }

    private static double distancePointToSegmentXZ(
            double px,
            double pz,
            double ax,
            double az,
            double bx,
            double bz) {
        double dx =
                bx - ax;
        double dz =
                bz - az;
        double lengthSq =
                dx * dx + dz * dz;

        if (lengthSq < 1.0E-9D) {
            double ex =
                    px - ax;
            double ez =
                    pz - az;

            return Math.sqrt(
                    ex * ex + ez * ez);
        }

        double t =
                ((px - ax) * dx
                        + (pz - az) * dz)
                        / lengthSq;

        t =
                Math.max(
                        0.0D,
                        Math.min(
                                1.0D,
                                t));

        double cx =
                ax + t * dx;
        double cz =
                az + t * dz;

        double ex =
                px - cx;
        double ez =
                pz - cz;

        return Math.sqrt(
                ex * ex + ez * ez);
    }
}
