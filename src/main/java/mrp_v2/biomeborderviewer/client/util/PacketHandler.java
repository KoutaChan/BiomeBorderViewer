package mrp_v2.biomeborderviewer.client.util;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import mrp_v2.biomeborderviewer.client.util.event.PacketReceiveEvent;
import net.minecraft.network.protocol.Packet;
import net.minecraftforge.common.MinecraftForge;

public class PacketHandler extends ChannelDuplexHandler {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof Packet) {
            MinecraftForge.EVENT_BUS.post(new PacketReceiveEvent((Packet<?>) msg));
        }
        super.channelRead(ctx, msg);
    }
}