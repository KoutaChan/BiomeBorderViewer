package mrp_v2.biomeborderviewer.client.util.event;

import net.minecraft.network.protocol.Packet;
import net.minecraftforge.eventbus.api.Event;

public class PacketReceiveEvent extends Event {
    public Packet<?> packet;

    public PacketReceiveEvent(Packet<?> packet) {
        this.packet = packet;
    }
}