package traincraft.block.track;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import traincraft.registry.TCBlocks;

public final class TrackMaterialBlockEntity extends BlockEntity {
    // TC_R2F3H_BE_SYNC_TRACE
    private static boolean isClassicStraightState(BlockState state) {
        net.minecraft.resources.ResourceLocation id =
                net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (id == null || !"traincraft".equals(id.getNamespace())) {
            return false;
        }
        String p = id.getPath();
        return p.equals("track_medium_straight_segment")
                || p.equals("track_long_straight_segment")
                || p.equals("track_very_long_straight_segment");
    }

    private String tcSide() {
        return level == null ? "NO_LEVEL" : (level.isClientSide ? "CLIENT" : "SERVER");
    }
    // TC_R2F3G_MODELDATA_REFRESH
    // TC_R2F3E_SEGMENT_MODELDATA
    public static final net.minecraftforge.client.model.data.ModelProperty<TrackMaterial> MODEL_MATERIAL =
            new net.minecraftforge.client.model.data.ModelProperty<>();
    private TrackMaterial material = TrackMaterial.NORMAL;

    public TrackMaterialBlockEntity(BlockPos pos, BlockState state) {
        super(TCBlocks.TRACK_MATERIAL_DATA.get(), pos, state);
    }

    public TrackMaterial getMaterial() {
        return material;
    }

    public void setMaterial(TrackMaterial material) {
        TrackMaterial next = material == null ? TrackMaterial.NORMAL : material;
        if (this.material == next) {
            return;
        }
        this.material = next;
        if (next != TrackMaterial.NORMAL || isClassicStraightState(getBlockState())) {
            traincraft.Traincraft.LOGGER.info(
                    "[TC_R2F3H] SET side={} pos={} block={} old={} new={}",
                    tcSide(), worldPosition,
                    net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(getBlockState().getBlock()),
                    next, next);
        }
        setChanged();

        // TC_R2F3G_MODELDATA_REFRESH
        // Material participates in baked-model selection through Forge ModelData.
        // Explicitly invalidate cached ModelData whenever the value changes.
        requestModelDataUpdate();

        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("TrackMaterial", material.id());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        material = TrackMaterial.fromId(tag.getString("TrackMaterial"));
        if (material != TrackMaterial.NORMAL || isClassicStraightState(getBlockState())) {
            traincraft.Traincraft.LOGGER.info(
                    "[TC_R2F3H] LOAD side={} pos={} block={} tagMaterial={} material={}",
                    tcSide(), worldPosition,
                    net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(getBlockState().getBlock()),
                    tag.getString("TrackMaterial"), material);
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        load(tag);
        if (material != TrackMaterial.NORMAL || isClassicStraightState(getBlockState())) {
            traincraft.Traincraft.LOGGER.info(
                    "[TC_R2F3H] HANDLE_TAG side={} pos={} block={} material={}",
                    tcSide(), worldPosition,
                    net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(getBlockState().getBlock()), material);
        }

        // TC_R2F3G_MODELDATA_REFRESH
        // Client-side NBT synchronization can change material without calling
        // setMaterial(), so refresh Forge's cached model data here as well.
        requestModelDataUpdate();

        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, 3);
        }
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraftforge.client.model.data.ModelData getModelData() {
        if (material != TrackMaterial.NORMAL || isClassicStraightState(getBlockState())) {
            traincraft.Traincraft.LOGGER.info(
                    "[TC_R2F3H] MODEL_DATA side={} pos={} block={} material={}",
                    tcSide(), worldPosition,
                    net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(getBlockState().getBlock()), material);
        }

        return net.minecraftforge.client.model.data.ModelData.builder()
                .with(MODEL_MATERIAL, material)
                .build();
    }
}
