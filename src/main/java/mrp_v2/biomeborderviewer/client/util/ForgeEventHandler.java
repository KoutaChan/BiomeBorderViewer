package mrp_v2.biomeborderviewer.client.util;

import mrp_v2.biomeborderviewer.BiomeBorderViewer;
import mrp_v2.biomeborderviewer.client.renderer.debug.VisualizeBorders;
import mrp_v2.biomeborderviewer.client.util.event.PacketReceiveEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = BiomeBorderViewer.ID)
public class ForgeEventHandler {
    @SubscribeEvent
    public static void loggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
        event.getConnection().channel().pipeline().addBefore("packet_handler", "biome_border_view", new PacketHandler());
    }

    @SubscribeEvent
    public static void packetReceived(PacketReceiveEvent event) {
        if (event.packet instanceof ClientboundChunksBiomesPacket) {
            VisualizeBorders.reSync();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void chunkLoad(ChunkEvent.Load event) {
        VisualizeBorders.chunkLoad(event.getLevel(), event.getChunk().getPos());
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void chunkUnload(ChunkEvent.Unload event) {
        VisualizeBorders.chunkUnload(event.getLevel(), event.getChunk().getPos());
    }

    @SubscribeEvent
    public static void keyPressed(InputEvent.Key event) {
        if (ObjectHolder.SHOW_BORDERS.consumeClick()) {
            VisualizeBorders.bordersKeyPressed();
        }
    }

    @SubscribeEvent
    public static void renderEvent(RenderLevelStageEvent event) {
        VisualizeBorders.renderEvent(event);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void worldUnload(LevelEvent.Unload event) {
        VisualizeBorders.worldUnload(event.getLevel());
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void worldLoad(LevelEvent.Load event) {
        VisualizeBorders.worldLoad(event.getLevel());
    }
}
