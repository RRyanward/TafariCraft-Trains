package traincraft.network.packet;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import traincraft.lighting.LocomotiveLightMode;

/** Client request; server verifies the operator before persisting or synchronizing. */
public final class LocomotiveLightModePacket {
    private final int id;
    private final int mode;
    public LocomotiveLightModePacket(int id, int mode) { this.id=id; this.mode=mode; }
    public static void encode(LocomotiveLightModePacket p,FriendlyByteBuf b) {
        b.writeVarInt(p.id); b.writeByte(p.mode);
    }
    public static LocomotiveLightModePacket decode(FriendlyByteBuf b) {
        return new LocomotiveLightModePacket(b.readVarInt(),b.readByte());
    }
    public static void handle(LocomotiveLightModePacket p,Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx=supplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender=ctx.getSender();
            if(sender==null || p.mode<0 || p.mode>2)return;
            Entity e=sender.level().getEntity(p.id);
            LocomotiveLightMode.change(sender,e,p.mode);
        });
        ctx.setPacketHandled(true);
    }
}
