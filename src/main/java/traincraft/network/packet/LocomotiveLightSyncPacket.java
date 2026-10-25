package traincraft.network.packet;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import traincraft.lighting.LocomotiveLightMode;

/** Server -> client state for renderers and player GUI. */
public final class LocomotiveLightSyncPacket {
    private final UUID uuid;
    private final int mode;
    public LocomotiveLightSyncPacket(UUID uuid,int mode){this.uuid=uuid;this.mode=mode;}
    public static void encode(LocomotiveLightSyncPacket p,FriendlyByteBuf b){
        b.writeUUID(p.uuid);b.writeByte(p.mode);
    }
    public static LocomotiveLightSyncPacket decode(FriendlyByteBuf b){
        return new LocomotiveLightSyncPacket(b.readUUID(),b.readByte());
    }
    public static void handle(LocomotiveLightSyncPacket p,Supplier<NetworkEvent.Context> supplier){
        NetworkEvent.Context ctx=supplier.get();
        ctx.enqueueWork(() -> LocomotiveLightMode.syncClient(p.uuid,p.mode));
        ctx.setPacketHandled(true);
    }
}
