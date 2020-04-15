package com.mrp_v2.biomeborderviewer.visualize;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;

import com.mojang.blaze3d.vertex.IVertexBuilder;
import com.mrp_v2.biomeborderviewer.BiomeBorderViewer;
import com.mrp_v2.biomeborderviewer.config.ConfigOptions;
import com.mrp_v2.biomeborderviewer.config.ConfigOptions.RenderModes;
import com.mrp_v2.biomeborderviewer.util.ChunkBiomeBorderData;
import com.mrp_v2.biomeborderviewer.util.Color;
import com.mrp_v2.biomeborderviewer.util.CornerData;
import com.mrp_v2.biomeborderviewer.util.LineData;
import com.mrp_v2.biomeborderviewer.util.QueuedChunkData;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Matrix4f;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.IWorld;
import net.minecraft.world.gen.Heightmap;
import net.minecraftforge.client.event.InputEvent.KeyInputEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class VisualizeBorders {

	private static boolean showingBorders;

	private static int viewRange;

	private static double playerHeightOffset;
	private static double terrainHeightOffset;
	private static double fixedHeight;

	private static float radius;

	private static Color colorA = new Color();
	private static Color colorB = new Color();

	private static ConfigOptions.RenderModes renderMode;

	private static ConcurrentHashMap<ChunkPos, ChunkBiomeBorderData> calculatedChunks = new ConcurrentHashMap<ChunkPos, ChunkBiomeBorderData>(
			128);

	private static ConcurrentHashMap<ChunkPos, QueuedChunkData> queuedChunks = new ConcurrentHashMap<ChunkPos, QueuedChunkData>(
			32);

	private static ArrayList<ChunkPos> loadedChunks = new ArrayList<ChunkPos>();

	@SubscribeEvent
	public static void chunkLoad(ChunkEvent.Load event) {
		if (event.getWorld() == null || !(event.getWorld() instanceof ClientWorld)) {
			return;
		}
		loadedChunks.add(event.getChunk().getPos());
		if (chunkReadyForCalculations(event.getChunk().getPos())) {
			calculatedChunks.put(event.getChunk().getPos(),
					new ChunkBiomeBorderData(new QueuedChunkData(event.getChunk(), event.getWorld())));
		} else {
			queuedChunks.put(event.getChunk().getPos(),
					new QueuedChunkData(event.getChunk(), event.getWorld().getWorld()));
		}
		for (ChunkPos pos : getNeighborChunks(event.getChunk().getPos())) {
			if (queuedChunks.containsKey(pos)) {
				if (chunkReadyForCalculations(pos)) {
					calculatedChunks.put(pos, new ChunkBiomeBorderData(queuedChunks.get(pos)));
				}
			}
		}
	}

	@SubscribeEvent
	public static void chunkUnload(ChunkEvent.Unload event) {
		if (event.getWorld() == null || !(event.getWorld() instanceof ClientWorld)) {
			return;
		}
		calculatedChunks.remove(event.getChunk().getPos());
		queuedChunks.remove(event.getChunk().getPos());
		loadedChunks.remove(event.getChunk().getPos());
	}

	@SubscribeEvent
	public static void worldUnload(WorldEvent.Unload event) {
		if (event.getWorld() == null || !(event.getWorld() instanceof ClientWorld)) {
			return;
		}
		calculatedChunks.clear();
		queuedChunks.clear();
		loadedChunks.clear();
	}

	@SuppressWarnings("resource")
	@SubscribeEvent
	public static void keyPressed(KeyInputEvent event) {
		if (BiomeBorderViewer.showBorders.isPressed()) {
			showingBorders = !showingBorders;
			LogManager.getLogger().debug("Show Borders hotkey pressed. showingBorders is now " + showingBorders
					+ ", render mode = " + renderMode.toString());
			Minecraft.getInstance().player
					.sendMessage(new StringTextComponent("Showing borders is now " + showingBorders));
		}
	}

	@SubscribeEvent
	public static void renderEvent(RenderWorldLastEvent event) {
		if (showingBorders) {
			@SuppressWarnings("resource")
			PlayerEntity player = Minecraft.getInstance().player;
			ChunkPos playerChunk = new ChunkPos(player.getPosition());
			Vec3d playerEyePos = player.getEyePosition(event.getPartialTicks());
			IVertexBuilder builder = Minecraft.getInstance().getRenderTypeBuffers().getBufferSource()
					.getBuffer(RenderType.getLightning());
			event.getMatrixStack().push();
			event.getMatrixStack().translate(-playerEyePos.x, -playerEyePos.y, -playerEyePos.z);
			Matrix4f matrix = event.getMatrixStack().getLast().getMatrix();
			for (ChunkPos pos : calculatedChunks.keySet()) {
				if (pos.getChessboardDistance(playerChunk) <= viewRange) {
					for (LineData lineData : calculatedChunks.get(pos).getLines()) {
						switch (renderMode) {
						case WALL:
							drawWall(lineData, matrix, builder);
							break;
						default:
							drawLine(lineData, matrix, builder, player.getEntityWorld(), playerEyePos);
							break;
						}
					}
					if (renderMode != RenderModes.WALL) {
						for (CornerData cornerData : calculatedChunks.get(pos).getCorners()) {
							drawCorner(cornerData, matrix, builder, player.getEntityWorld(), playerEyePos);
						}
					}
				}
			}
			event.getMatrixStack().pop();
			Minecraft.getInstance().getRenderTypeBuffers().getBufferSource().finish(RenderType.getLightning());
		}
	}

	private static ChunkPos[] getNeighborChunks(ChunkPos chunk) {
		return new ChunkPos[] { new ChunkPos(chunk.x + 1, chunk.z), new ChunkPos(chunk.x - 1, chunk.z),
				new ChunkPos(chunk.x, chunk.z + 1), new ChunkPos(chunk.x, chunk.z - 1),
				new ChunkPos(chunk.x + 1, chunk.z + 1), new ChunkPos(chunk.x - 1, chunk.z - 1),
				new ChunkPos(chunk.x - 1, chunk.z + 1), new ChunkPos(chunk.x + 1, chunk.z - 1) };
	}

	private static boolean chunkReadyForCalculations(ChunkPos pos) {
		if (!loadedChunks.contains(pos)) {
			return false;
		}
		for (ChunkPos neighbor : getNeighborChunks(pos)) {
			if (!loadedChunks.contains(neighbor)) {
				return false;
			}
		}
		return true;
	}

	private static final float minWallHeight = 0;
	private static final float maxWallHeight = 255;
	private static final float wallOffsetDivisor = 1f / 0b11111111;

	private static void drawWall(LineData lineData, Matrix4f matrix, IVertexBuilder builder) {
		Color color = borderColor(lineData.similarTemperature);
		if (lineData.a.x == lineData.b.x) {
			// -x side
			builder.pos(matrix, (float) lineData.a.x + wallOffsetDivisor, minWallHeight, (float) lineData.a.z)
					.color(color.r, color.g, color.b, color.a).endVertex();
			builder.pos(matrix, (float) lineData.b.x + wallOffsetDivisor, minWallHeight, (float) lineData.b.z)
					.color(color.r, color.g, color.b, color.a).endVertex();
			builder.pos(matrix, (float) lineData.b.x + wallOffsetDivisor, maxWallHeight, (float) lineData.b.z)
					.color(color.r, color.g, color.b, color.a).endVertex();
			builder.pos(matrix, (float) lineData.a.x + wallOffsetDivisor, maxWallHeight, (float) lineData.a.z)
					.color(color.r, color.g, color.b, color.a).endVertex();
			// +x side
			builder.pos(matrix, (float) lineData.a.x - wallOffsetDivisor, maxWallHeight, (float) lineData.a.z)
					.color(color.r, color.g, color.b, color.a).endVertex();
			builder.pos(matrix, (float) lineData.b.x - wallOffsetDivisor, maxWallHeight, (float) lineData.b.z)
					.color(color.r, color.g, color.b, color.a).endVertex();
			builder.pos(matrix, (float) lineData.b.x - wallOffsetDivisor, minWallHeight, (float) lineData.b.z)
					.color(color.r, color.g, color.b, color.a).endVertex();
			builder.pos(matrix, (float) lineData.a.x - wallOffsetDivisor, minWallHeight, (float) lineData.a.z)
					.color(color.r, color.g, color.b, color.a).endVertex();
		} else {
			// -z side
			builder.pos(matrix, (float) lineData.a.x, minWallHeight, (float) lineData.a.z - wallOffsetDivisor)
					.color(color.r, color.g, color.b, color.a).endVertex();
			builder.pos(matrix, (float) lineData.b.x, minWallHeight, (float) lineData.b.z - wallOffsetDivisor)
					.color(color.r, color.g, color.b, color.a).endVertex();
			builder.pos(matrix, (float) lineData.b.x, maxWallHeight, (float) lineData.b.z - wallOffsetDivisor)
					.color(color.r, color.g, color.b, color.a).endVertex();
			builder.pos(matrix, (float) lineData.a.x, maxWallHeight, (float) lineData.a.z - wallOffsetDivisor)
					.color(color.r, color.g, color.b, color.a).endVertex();
			// +z side
			builder.pos(matrix, (float) lineData.a.x, maxWallHeight, (float) lineData.a.z + wallOffsetDivisor)
					.color(color.r, color.g, color.b, color.a).endVertex();
			builder.pos(matrix, (float) lineData.b.x, maxWallHeight, (float) lineData.b.z + wallOffsetDivisor)
					.color(color.r, color.g, color.b, color.a).endVertex();
			builder.pos(matrix, (float) lineData.b.x, minWallHeight, (float) lineData.b.z + wallOffsetDivisor)
					.color(color.r, color.g, color.b, color.a).endVertex();
			builder.pos(matrix, (float) lineData.a.x, minWallHeight, (float) lineData.a.z + wallOffsetDivisor)
					.color(color.r, color.g, color.b, color.a).endVertex();
		}
	}

	private static void drawLine(LineData lineData, Matrix4f matrix, IVertexBuilder builder, IWorld world,
			Vec3d playerPos) {
		float ay = heightForPos(lineData.a.x, lineData.a.z, world, playerPos);
		float by = heightForPos(lineData.b.x, lineData.b.z, world, playerPos);
		Color color = borderColor(lineData.similarTemperature);
		if (lineData.a.x == lineData.b.x) {
			// top
			builder.pos(matrix, (float) lineData.a.x + radius, ay + radius, (float) lineData.a.z - radius)
					.color(color.r, color.g, color.b, color.a).endVertex();
			builder.pos(matrix, (float) lineData.b.x + radius, by + radius, (float) lineData.b.z + radius)
					.color(color.r, color.g, color.b, color.a).endVertex();
			builder.pos(matrix, (float) lineData.b.x - radius, by + radius, (float) lineData.b.z + radius)
					.color(color.r, color.g, color.b, color.a).endVertex();
			builder.pos(matrix, (float) lineData.a.x - radius, ay + radius, (float) lineData.a.z - radius)
					.color(color.r, color.g, color.b, color.a).endVertex();
			// bottom
			builder.pos(matrix, (float) lineData.a.x - radius, ay - radius, (float) lineData.a.z - radius)
					.color(color.r, color.g, color.b, color.a).endVertex();
			builder.pos(matrix, (float) lineData.b.x - radius, by - radius, (float) lineData.b.z + radius)
					.color(color.r, color.g, color.b, color.a).endVertex();
			builder.pos(matrix, (float) lineData.b.x + radius, by - radius, (float) lineData.b.z + radius)
					.color(color.r, color.g, color.b, color.a).endVertex();
			builder.pos(matrix, (float) lineData.a.x + radius, ay - radius, (float) lineData.a.z - radius)
					.color(color.r, color.g, color.b, color.a).endVertex();
			// -x side
			builder.pos(matrix, (float) lineData.a.x - radius, ay + radius, (float) lineData.a.z - radius)
					.color(color.r, color.g, color.b, color.a).endVertex();
			builder.pos(matrix, (float) lineData.b.x - radius, by + radius, (float) lineData.b.z + radius)
					.color(color.r, color.g, color.b, color.a).endVertex();
			builder.pos(matrix, (float) lineData.b.x - radius, by - radius, (float) lineData.b.z + radius)
					.color(color.r, color.g, color.b, color.a).endVertex();
			builder.pos(matrix, (float) lineData.a.x - radius, ay - radius, (float) lineData.a.z - radius)
					.color(color.r, color.g, color.b, color.a).endVertex();
			// +x side
			builder.pos(matrix, (float) lineData.a.x + radius, ay - radius, (float) lineData.a.z - radius)
					.color(color.r, color.g, color.b, color.a).endVertex();
			builder.pos(matrix, (float) lineData.b.x + radius, by - radius, (float) lineData.b.z + radius)
					.color(color.r, color.g, color.b, color.a).endVertex();
			builder.pos(matrix, (float) lineData.b.x + radius, by + radius, (float) lineData.b.z + radius)
					.color(color.r, color.g, color.b, color.a).endVertex();
			builder.pos(matrix, (float) lineData.a.x + radius, ay + radius, (float) lineData.a.z - radius)
					.color(color.r, color.g, color.b, color.a).endVertex();
		} else {
			// top
			builder.pos(matrix, (float) lineData.a.x - radius, ay + radius, (float) lineData.a.z - radius)
					.color(color.r, color.g, color.b, color.a).endVertex();
			builder.pos(matrix, (float) lineData.b.x + radius, by + radius, (float) lineData.b.z - radius)
					.color(color.r, color.g, color.b, color.a).endVertex();
			builder.pos(matrix, (float) lineData.b.x + radius, by + radius, (float) lineData.b.z + radius)
					.color(color.r, color.g, color.b, color.a).endVertex();
			builder.pos(matrix, (float) lineData.a.x - radius, ay + radius, (float) lineData.a.z + radius)
					.color(color.r, color.g, color.b, color.a).endVertex();
			// bottom
			builder.pos(matrix, (float) lineData.a.x - radius, ay - radius, (float) lineData.a.z + radius)
					.color(color.r, color.g, color.b, color.a).endVertex();
			builder.pos(matrix, (float) lineData.b.x + radius, by - radius, (float) lineData.b.z + radius)
					.color(color.r, color.g, color.b, color.a).endVertex();
			builder.pos(matrix, (float) lineData.b.x + radius, by - radius, (float) lineData.b.z - radius)
					.color(color.r, color.g, color.b, color.a).endVertex();
			builder.pos(matrix, (float) lineData.a.x - radius, ay - radius, (float) lineData.a.z - radius)
					.color(color.r, color.g, color.b, color.a).endVertex();
			// -z side
			builder.pos(matrix, (float) lineData.a.x - radius, ay - radius, (float) lineData.a.z - radius)
					.color(color.r, color.g, color.b, color.a).endVertex();
			builder.pos(matrix, (float) lineData.b.x + radius, by - radius, (float) lineData.b.z - radius)
					.color(color.r, color.g, color.b, color.a).endVertex();
			builder.pos(matrix, (float) lineData.b.x + radius, by + radius, (float) lineData.b.z - radius)
					.color(color.r, color.g, color.b, color.a).endVertex();
			builder.pos(matrix, (float) lineData.a.x - radius, ay + radius, (float) lineData.a.z - radius)
					.color(color.r, color.g, color.b, color.a).endVertex();
			// +z side
			builder.pos(matrix, (float) lineData.a.x - radius, ay + radius, (float) lineData.a.z + radius)
					.color(color.r, color.g, color.b, color.a).endVertex();
			builder.pos(matrix, (float) lineData.b.x + radius, by + radius, (float) lineData.b.z + radius)
					.color(color.r, color.g, color.b, color.a).endVertex();
			builder.pos(matrix, (float) lineData.b.x + radius, by - radius, (float) lineData.b.z + radius)
					.color(color.r, color.g, color.b, color.a).endVertex();
			builder.pos(matrix, (float) lineData.a.x - radius, ay - radius, (float) lineData.a.z + radius)
					.color(color.r, color.g, color.b, color.a).endVertex();
		}
	}

	private static void drawCorner(CornerData cornerData, Matrix4f matrix, IVertexBuilder builder, IWorld world,
			Vec3d playerPos) {
		float y = heightForPos(cornerData.x, cornerData.z, world, playerPos);
		Color color = borderColor(cornerData.similarTemperature);
		if (cornerData.showPlusX) {
			// +x side
			builder.pos(matrix, (float) cornerData.x + radius, y - radius, (float) cornerData.z - radius)
					.color(color.r, color.g, color.b, color.a).endVertex();
			builder.pos(matrix, (float) cornerData.x + radius, y + radius, (float) cornerData.z - radius)
					.color(color.r, color.g, color.b, color.a).endVertex();
			builder.pos(matrix, (float) cornerData.x + radius, y + radius, (float) cornerData.z + radius)
					.color(color.r, color.g, color.b, color.a).endVertex();
			builder.pos(matrix, (float) cornerData.x + radius, y - radius, (float) cornerData.z + radius)
					.color(color.r, color.g, color.b, color.a).endVertex();
		}
		if (cornerData.showMinusX) {
			// -x side
			builder.pos(matrix, (float) cornerData.x - radius, y - radius, (float) cornerData.z + radius)
					.color(color.r, color.g, color.b, color.a).endVertex();
			builder.pos(matrix, (float) cornerData.x - radius, y + radius, (float) cornerData.z + radius)
					.color(color.r, color.g, color.b, color.a).endVertex();
			builder.pos(matrix, (float) cornerData.x - radius, y + radius, (float) cornerData.z - radius)
					.color(color.r, color.g, color.b, color.a).endVertex();
			builder.pos(matrix, (float) cornerData.x - radius, y - radius, (float) cornerData.z - radius)
					.color(color.r, color.g, color.b, color.a).endVertex();
		}
		if (cornerData.showPlusZ) {
			// +z side
			builder.pos(matrix, (float) cornerData.x + radius, y - radius, (float) cornerData.z + radius)
					.color(color.r, color.g, color.b, color.a).endVertex();
			builder.pos(matrix, (float) cornerData.x + radius, y + radius, (float) cornerData.z + radius)
					.color(color.r, color.g, color.b, color.a).endVertex();
			builder.pos(matrix, (float) cornerData.x - radius, y + radius, (float) cornerData.z + radius)
					.color(color.r, color.g, color.b, color.a).endVertex();
			builder.pos(matrix, (float) cornerData.x - radius, y - radius, (float) cornerData.z + radius)
					.color(color.r, color.g, color.b, color.a).endVertex();
		}
		if (cornerData.showMinusZ) {
			// -z side
			builder.pos(matrix, (float) cornerData.x - radius, y - radius, (float) cornerData.z - radius)
					.color(color.r, color.g, color.b, color.a).endVertex();
			builder.pos(matrix, (float) cornerData.x - radius, y + radius, (float) cornerData.z - radius)
					.color(color.r, color.g, color.b, color.a).endVertex();
			builder.pos(matrix, (float) cornerData.x + radius, y + radius, (float) cornerData.z - radius)
					.color(color.r, color.g, color.b, color.a).endVertex();
			builder.pos(matrix, (float) cornerData.x + radius, y - radius, (float) cornerData.z - radius)
					.color(color.r, color.g, color.b, color.a).endVertex();
		}
		// top
		builder.pos(matrix, (float) cornerData.x + radius, y + radius, (float) cornerData.z + radius)
				.color(color.r, color.g, color.b, color.a).endVertex();
		builder.pos(matrix, (float) cornerData.x + radius, y + radius, (float) cornerData.z - radius)
				.color(color.r, color.g, color.b, color.a).endVertex();
		builder.pos(matrix, (float) cornerData.x - radius, y + radius, (float) cornerData.z - radius)
				.color(color.r, color.g, color.b, color.a).endVertex();
		builder.pos(matrix, (float) cornerData.x - radius, y + radius, (float) cornerData.z + radius)
				.color(color.r, color.g, color.b, color.a).endVertex();
		// bottom
		builder.pos(matrix, (float) cornerData.x - radius, y - radius, (float) cornerData.z + radius)
				.color(color.r, color.g, color.b, color.a).endVertex();
		builder.pos(matrix, (float) cornerData.x - radius, y - radius, (float) cornerData.z - radius)
				.color(color.r, color.g, color.b, color.a).endVertex();
		builder.pos(matrix, (float) cornerData.x + radius, y - radius, (float) cornerData.z - radius)
				.color(color.r, color.g, color.b, color.a).endVertex();
		builder.pos(matrix, (float) cornerData.x + radius, y - radius, (float) cornerData.z + radius)
				.color(color.r, color.g, color.b, color.a).endVertex();
	}

	private static Color borderColor(boolean isSimilar) {
		if (isSimilar) {
			return colorA;
		} else {
			return colorB;
		}
	}

	private static float heightForPos(double x, double z, IWorld world, Vec3d playerPos) {
		switch (renderMode) {
		case LINE_FIXED_HEIGHT:
			return (float) fixedHeight;
		case LINE_FOLLOW_PLAYER_HEIGHT:
			return playerBasedHeight(playerPos);
		case LINE_FOLLOW_PLAYER_IF_HIGHER_THAN_TERRAIN:
			float playerBasedHeight = playerBasedHeight(playerPos);
			float terrainBasedHeight = terrainBasedHeight(x, z, world);
			if (playerBasedHeight >= terrainBasedHeight)
				return playerBasedHeight;
			else
				return terrainBasedHeight;
		case LINE_MATCH_TERRAIN:
			return terrainBasedHeight(x, z, world);
		default:
			return 64;
		}
	}

	private static float playerBasedHeight(Vec3d playerPos) {
		float height = (float) (playerPos.y + playerHeightOffset);
		return height;
	}

	private static float terrainBasedHeight(double xf, double zf, IWorld world) {
		int x = (int) Math.round(xf);
		int z = (int) Math.round(zf);
		float height = 0;
		for (int tempX = x - 1; tempX <= x; tempX++) {
			for (int tempZ = z - 1; tempZ <= z; tempZ++) {
				int y = world.getHeight(Heightmap.Type.MOTION_BLOCKING, tempX, tempZ);
				if (y > height) {
					height = y;
				}
			}
		}
		return (float) (height + terrainHeightOffset);
	}

	public static void loadConfigSettings() {
		LogManager.getLogger().debug("Loading config settings for border lines.");
		viewRange = ConfigOptions.viewRange.get();
		playerHeightOffset = ConfigOptions.playerHeightOffset.get();
		terrainHeightOffset = ConfigOptions.terrainHeightOffset.get();
		fixedHeight = ConfigOptions.fixedHeight.get();
		renderMode = ConfigOptions.renderMode.get();
		colorA.set(ConfigOptions.getColorA());
		colorB.set(ConfigOptions.getColorB());
		radius = ConfigOptions.lineWidth.get().floatValue() / 2;
	}
}
