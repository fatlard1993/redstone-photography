package justfatlard.redstone_photography.gametest;

import justfatlard.redstone_photography.Developing;
import justfatlard.redstone_photography.Main;
import justfatlard.redstone_photography.Photos;
import justfatlard.redstone_photography.Prints;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.UUID;

/**
 * A picture taken through a real client, end to end: the camera's click goes out as the player's
 * own, Pandorical's client reads back the frame, the server keeps the file, the film develops in
 * a sealed room lit by a redstone lamp (after a torch is found spoiling it), and the print hangs
 * on a wall in daylight. Run it under xvfb-run; the frames land in build/run/clientGameTest/screenshots.
 */
public final class Darkroom implements FabricClientGameTest {
	private static final int WIDTH = 1920;
	private static final int HEIGHT = 1080;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();

			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule advance_weather false");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("difficulty peaceful");
			server.runCommand("time set noon");
			server.runCommand("gamemode survival @a");

			BlockPos origin = server.computeOnServer(s -> connection.getServerPlayer().blockPosition());
			int x = origin.getX(), y = origin.getY(), z = origin.getZ();

			// A subject: a striped wall to take a picture of.
			server.runCommand("fill %d %d %d %d %d %d minecraft:smooth_stone".formatted(x - 8, y - 1, z - 12, x + 8, y - 1, z + 4));
			server.runCommand("fill %d %d %d %d %d %d minecraft:air".formatted(x - 8, y, z - 12, x + 8, y + 8, z + 4));
			for (int i = 0; i < 5; i++) {
				String wool = new String[] {"red", "orange", "yellow", "lime", "light_blue"}[i];
				server.runCommand("fill %d %d %d %d %d %d minecraft:%s_wool".formatted(x - 5 + i * 2, y, z - 10, x - 4 + i * 2, y + 4, z - 10, wool));
			}

			// Shoot: camera in hand, film in the pack.
			hold(server, connection, new ItemStack(Main.CAMERA));
			server.runOnServer(s -> connection.getServerPlayer().getInventory().setItem(9, new ItemStack(Main.FILM, 2)));
			look(server, x + 0.5, y, z + 0.5, x + 0.5, y + 2, z - 10);
			context.waitTicks(20);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(60);

			UUID photo = server.computeOnServer(s -> {
				ServerPlayer player = connection.getServerPlayer();
				if (count(player, Main.FILM) != 1) throw new AssertionError("the shot did not use one film: " + count(player, Main.FILM) + " left");
				ItemStack exposed = find(player, Main.EXPOSED_FILM);
				if (exposed.isEmpty()) throw new AssertionError("no exposed film after the shot");
				return justfatlard.redstone_photography.Film.read(exposed).photo();
			});
			File file = server.computeOnServer(s -> Photos.file(s, photo).toFile());
			BufferedImage kept = read(file);
			if (kept.getWidth() != 512 || kept.getHeight() != 512) throw new AssertionError("kept picture is " + kept.getWidth() + "x" + kept.getHeight());

			// A darkroom: a sealed stone box, a redstone lamp in its roof kept on by a block of
			// redstone above, a water cauldron in the middle. And a torch, which spoils it.
			int dy = 30;
			BlockPos cauldron = new BlockPos(x, y + dy + 1, z);
			server.runCommand("fill %d %d %d %d %d %d minecraft:stone hollow".formatted(x - 3, y + dy, z - 3, x + 3, y + dy + 5, z + 3));
			server.runCommand("setblock %d %d %d minecraft:redstone_lamp".formatted(x, y + dy + 5, z));
			server.runCommand("setblock %d %d %d minecraft:redstone_block".formatted(x, y + dy + 6, z));
			server.runCommand("setblock %d %d %d minecraft:water_cauldron[level=3]".formatted(cauldron.getX(), cauldron.getY(), cauldron.getZ()));
			server.runCommand("setblock %d %d %d minecraft:torch".formatted(x + 2, y + dy + 1, z + 2));
			context.waitTicks(20);

			String spoiled = server.computeOnServer(s -> {
				var problem = justfatlard.redstone_photography.Darkroom.problem(s.overworld(), cauldron);
				return problem == null ? null : problem.getString();
			});
			if (spoiled == null || !spoiled.contains("Torch")) throw new AssertionError("a torch in the room went unnoticed: " + spoiled);
			server.runCommand("setblock %d %d %d minecraft:air".formatted(x + 2, y + dy + 1, z + 2));
			context.waitTicks(20);
			String clear = server.computeOnServer(s -> {
				var problem = justfatlard.redstone_photography.Darkroom.problem(s.overworld(), cauldron);
				return problem == null ? null : problem.getString();
			});
			if (clear != null) throw new AssertionError("the redstone-lit room is not a darkroom: " + clear);

			// A light under the floor, sealed off by the solid top of a slab, is no leak.
			server.runCommand("setblock %d %d %d minecraft:smooth_stone_slab[type=top]".formatted(x + 2, y + dy, z + 2));
			server.runCommand("setblock %d %d %d minecraft:glowstone".formatted(x + 2, y + dy - 1, z + 2));
			context.waitTicks(20);
			String underSlab = server.computeOnServer(s -> {
				var problem = justfatlard.redstone_photography.Darkroom.problem(s.overworld(), cauldron);
				return problem == null ? null : problem.getString();
			});
			if (underSlab != null) throw new AssertionError("a light under a slab floor spoiled the room: " + underSlab);
			server.runCommand("setblock %d %d %d minecraft:air".formatted(x + 2, y + dy, z + 2));
			context.waitTicks(20);
			String throughHole = server.computeOnServer(s -> {
				var problem = justfatlard.redstone_photography.Darkroom.problem(s.overworld(), cauldron);
				return problem == null ? null : problem.getString();
			});
			if (throughHole == null || !throughHole.contains("Glowstone")) throw new AssertionError("a light under a hole in the floor went unnoticed: " + throughHole);
			server.runCommand("setblock %d %d %d minecraft:stone".formatted(x + 2, y + dy, z + 2));
			server.runCommand("setblock %d %d %d minecraft:air".formatted(x + 2, y + dy - 1, z + 2));
			context.waitTicks(20);

			// Develop: the exposed film into the cauldron, by hand.
			server.runOnServer(s -> {
				ServerPlayer player = connection.getServerPlayer();
				ItemStack exposed = find(player, Main.EXPOSED_FILM).copy();
				clearOf(player, Main.EXPOSED_FILM);
				player.getInventory().setItem(0, exposed);
			});
			look(server, x + 0.5, y + dy + 1, z + 2.5, x + 0.5, y + dy + 1.8, z + 0.5);
			context.waitTicks(20);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(10);
			boolean developing = server.computeOnServer(s -> !trays(s.overworld(), cauldron).isEmpty());
			if (!developing) throw new AssertionError("the film did not go into the cauldron");

			context.waitTicks(Developing.DEVELOP_TICKS + 40);
			boolean printed = server.computeOnServer(s -> !find(connection.getServerPlayer(), Main.PHOTOGRAPH).isEmpty()
				|| !s.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(cauldron).inflate(4),
					item -> item.getItem().is(Main.PHOTOGRAPH)).isEmpty());
			if (!printed) throw new AssertionError("no photograph came out of the cauldron");

			// Fogged: film going in again, and the roof opened on it before it is done. The first
			// print is picked up first, so that any print lying here afterwards is a new one.
			server.runOnServer(s -> {
				ServerPlayer player = connection.getServerPlayer();
				player.getInventory().setItem(0,
					justfatlard.redstone_photography.Film.exposed(new justfatlard.redstone_photography.Film.Exposure(photo, "test")));
				for (ItemEntity item : s.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(cauldron).inflate(4),
						item -> item.getItem().is(Main.PHOTOGRAPH))) {
					player.getInventory().add(item.getItem().copy());
					item.discard();
				}
			});
			context.waitTicks(5);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(10);
			if (server.computeOnServer(s -> trays(s.overworld(), cauldron).isEmpty())) throw new AssertionError("the second film did not go in");
			server.runCommand("fill %d %d %d %d %d %d minecraft:air".formatted(x - 1, y + dy + 5, z - 1, x + 1, y + dy + 6, z + 1));
			context.waitTicks(60);
			boolean fogged = server.computeOnServer(s -> trays(s.overworld(), cauldron).isEmpty()
				&& s.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(cauldron).inflate(4),
					item -> item.getItem().is(Main.PHOTOGRAPH) || item.getItem().is(Main.EXPOSED_FILM)).isEmpty()
				&& count(connection.getServerPlayer(), Main.EXPOSED_FILM) == 0);
			if (!fogged) throw new AssertionError("daylight on developing film did not fog it");

			// Hang it outside on a wall, in daylight.
			server.runOnServer(s -> {
				ServerPlayer player = connection.getServerPlayer();
				ItemStack print = find(player, Main.PHOTOGRAPH);
				if (print.isEmpty()) {
					var items = s.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(cauldron).inflate(4),
						item -> item.getItem().is(Main.PHOTOGRAPH));
					print = items.get(0).getItem();
					items.get(0).discard();
				}
				ItemStack copy = print.copy();
				clearOf(player, Main.PHOTOGRAPH);
				player.getInventory().setItem(0, copy);
			});
			server.runCommand("fill %d %d %d %d %d %d minecraft:stone_bricks".formatted(x - 3, y, z - 4, x + 3, y + 4, z - 4));
			look(server, x + 0.5, y, z + 0.5, x + 0.5, y + 1.5, z - 3.5);
			context.waitTicks(20);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(20);
			int frames = server.computeOnServer(s -> s.overworld().getEntitiesOfClass(Entity.class,
				new AABB(x - 4, y - 1, z - 5, x + 4, y + 5, z - 2), Prints::isPrint).size());
			if (frames != 4) throw new AssertionError("the photograph hung as " + frames + " frames, not 4");

			context.getInput().pressKey(options -> options.keyToggleGui);
			look(server, x + 0.5, y, z + 2.5, x + 0.5, y + 1.6, z - 3.5);
			context.waitTicks(40);
			context.takeScreenshot(TestScreenshotOptions.of("photograph-on-wall").withSize(WIDTH, HEIGHT).disableCounterPrefix());
		}
	}

	private static List<Entity> trays(ServerLevel level, BlockPos cauldron) {
		return level.getEntitiesOfClass(Entity.class, new AABB(cauldron), Developing::isTray);
	}

	private static void hold(TestServerContext server, TestServerConnection connection, ItemStack stack) {
		server.runOnServer(s -> connection.getServerPlayer().getInventory().setItem(0, stack));
	}

	private static int count(ServerPlayer player, Item item) {
		int n = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.is(item)) n += stack.getCount();
		}
		return n;
	}

	private static ItemStack find(ServerPlayer player, Item item) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.is(item)) return stack;
		}
		return ItemStack.EMPTY;
	}

	private static void clearOf(ServerPlayer player, Item item) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			if (player.getInventory().getItem(slot).is(item)) player.getInventory().setItem(slot, ItemStack.EMPTY);
		}
	}

	private static BufferedImage read(File file) {
		try {
			BufferedImage image = ImageIO.read(file);
			if (image == null) throw new AssertionError(file + " is not a picture");
			return image;
		} catch (java.io.IOException e) {
			throw new AssertionError("could not read " + file, e);
		}
	}

	/** Stand at one place and look at another; y is the feet, the eye 1.62 above. */
	private static void look(TestServerContext server, double x, double y, double z, double atX, double atY, double atZ) {
		double dx = atX - x, dy = atY - (y + 1.62), dz = atZ - z;
		double yaw = -Math.toDegrees(Math.atan2(dx, dz));
		double pitch = -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		server.runCommand("tp @a %.2f %.2f %.2f %.1f %.1f".formatted(x, y, z, yaw, pitch));
	}
}
