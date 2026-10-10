package justfatlard.redstone_photography;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

/**
 * The pictures themselves: kept on the server as PNG files in the world's
 * {@code redstone-photography} folder, named by the photo's id, and printed onto maps when their
 * film is developed.
 */
public final class Photos {
	private Photos() {}

	private static final Logger LOGGER = LoggerFactory.getLogger(Main.MOD_ID);

	public static final int TILES_ACROSS = 2;
	private static final int MAP_SIZE = 128;
	public static final int PRINT_SIDE = TILES_ACROSS * MAP_SIZE;

	private static final int[] PACKED = mapColors();
	private static final int[] PACKED_RGB = new int[PACKED.length];

	static {
		for (int i = 0; i < PACKED.length; i++) PACKED_RGB[i] = MapColor.getColorFromPackedId(PACKED[i]) & 0xFFFFFF;
	}

	public static Path file(MinecraftServer server, UUID photo) {
		return server.getWorldPath(LevelResource.ROOT).resolve(Main.MOD_ID).resolve(photo + ".png");
	}

	static boolean save(MinecraftServer server, UUID photo, byte[] png) {
		Path file = file(server, photo);
		try {
			Files.createDirectories(file.getParent());
			Files.write(file, png);
			return true;
		} catch (IOException e) {
			LOGGER.warn("Could not save photo {}", file, e);
			return false;
		}
	}

	/**
	 * Print a photo onto fresh locked maps, in reading order: the picture scaled to the print and
	 * dithered into map colours. Null if the file is gone or unreadable.
	 */
	static int[] print(ServerLevel level, UUID photo) {
		BufferedImage image;
		try {
			image = ImageIO.read(file(level.getServer(), photo).toFile());
		} catch (IOException e) {
			LOGGER.warn("Could not read photo {}", photo, e);
			return null;
		}
		if (image == null) return null;
		byte[] colors = dither(scale(image, PRINT_SIDE));

		int[] maps = new int[TILES_ACROSS * TILES_ACROSS];
		for (int row = 0; row < TILES_ACROSS; row++) {
			for (int column = 0; column < TILES_ACROSS; column++) {
				MapId id = level.getFreeMapId();
				MapItemSavedData data = MapItemSavedData.createForClient((byte) 0, true, level.dimension());
				for (int y = 0; y < MAP_SIZE; y++) {
					for (int x = 0; x < MAP_SIZE; x++) {
						data.setColor(x, y, colors[column * MAP_SIZE + x + (row * MAP_SIZE + y) * PRINT_SIDE]);
					}
				}
				level.setMapData(id, data);
				maps[column + row * TILES_ACROSS] = id.id();
			}
		}
		return maps;
	}

	/** Each pixel the average of the pixels it covers. */
	static int[] scale(BufferedImage image, int side) {
		int width = image.getWidth(), height = image.getHeight();
		int[] source = image.getRGB(0, 0, width, height, null, 0, width);
		int[] scaled = new int[side * side];
		for (int y = 0; y < side; y++) {
			int top = y * height / side, bottom = Math.max(top + 1, (y + 1) * height / side);
			for (int x = 0; x < side; x++) {
				int left = x * width / side, right = Math.max(left + 1, (x + 1) * width / side);
				long r = 0, g = 0, b = 0;
				for (int sy = top; sy < bottom; sy++) {
					for (int sx = left; sx < right; sx++) {
						int argb = source[sx + sy * width];
						r += (argb >> 16) & 0xFF;
						g += (argb >> 8) & 0xFF;
						b += argb & 0xFF;
					}
				}
				int n = (bottom - top) * (right - left);
				scaled[x + y * side] = (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
			}
		}
		return scaled;
	}

	/** Floyd-Steinberg onto the colours a map can show, as packed map colours. */
	static byte[] dither(int[] rgb) {
		int side = (int) Math.sqrt(rgb.length);
		float[] r = new float[rgb.length], g = new float[rgb.length], b = new float[rgb.length];
		for (int i = 0; i < rgb.length; i++) {
			r[i] = (rgb[i] >> 16) & 0xFF;
			g[i] = (rgb[i] >> 8) & 0xFF;
			b[i] = rgb[i] & 0xFF;
		}
		byte[] out = new byte[rgb.length];
		for (int y = 0; y < side; y++) {
			for (int x = 0; x < side; x++) {
				int i = x + y * side;
				int best = nearest(r[i], g[i], b[i]);
				out[i] = (byte) PACKED[best];
				int chosen = PACKED_RGB[best];
				float er = r[i] - ((chosen >> 16) & 0xFF), eg = g[i] - ((chosen >> 8) & 0xFF), eb = b[i] - (chosen & 0xFF);
				spread(r, g, b, side, x + 1, y, er, eg, eb, 7 / 16F);
				spread(r, g, b, side, x - 1, y + 1, er, eg, eb, 3 / 16F);
				spread(r, g, b, side, x, y + 1, er, eg, eb, 5 / 16F);
				spread(r, g, b, side, x + 1, y + 1, er, eg, eb, 1 / 16F);
			}
		}
		return out;
	}

	private static void spread(float[] r, float[] g, float[] b, int side, int x, int y, float er, float eg, float eb, float share) {
		if (x < 0 || x >= side || y >= side) return;
		int i = x + y * side;
		r[i] += er * share;
		g[i] += eg * share;
		b[i] += eb * share;
	}

	/** By the "redmean" weighting, which tracks the eye better than plain distance at the same cost. */
	private static int nearest(float r, float g, float b) {
		int best = 0;
		float bestDistance = Float.MAX_VALUE;
		for (int i = 0; i < PACKED_RGB.length; i++) {
			int c = PACKED_RGB[i];
			float cr = (c >> 16) & 0xFF;
			float mean = (r + cr) / 2;
			float dr = r - cr, dg = g - ((c >> 8) & 0xFF), db = b - (c & 0xFF);
			float distance = (2 + mean / 256) * dr * dr + 4 * dg * dg + (2 + (255 - mean) / 256) * db * db;
			if (distance < bestDistance) {
				bestDistance = distance;
				best = i;
			}
		}
		return best;
	}

	/** Every packed colour a map can show: each base colour but the transparent one, at each brightness. */
	private static int[] mapColors() {
		int[] packed = new int[256];
		int count = 0;
		for (int id = 1; id < 64; id++) {
			MapColor color = MapColor.byId(id);
			if (color == null || color == MapColor.NONE) continue;
			for (MapColor.Brightness brightness : MapColor.Brightness.values()) {
				packed[count++] = color.getPackedId(brightness) & 0xFF;
			}
		}
		return Arrays.copyOf(packed, count);
	}
}
