package justfatlard.redstone_photography;

import justfatlard.pandorical.api.Capture;
import justfatlard.pandorical.api.PandoricalApi;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

/** Takes a picture of what its holder sees, onto a film from their inventory. */
public class CameraItem extends Item {
	/** Twice the print's 256, so the kept file is worth looking at on its own. */
	public static final int SIDE = 512;
	private static final int COOLDOWN_TICKS = 20;

	public CameraItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(player instanceof ServerPlayer shooter)) return InteractionResult.SUCCESS;
		ItemStack camera = player.getItemInHand(hand);
		if (!PandoricalApi.capture().canCapture(shooter)) {
			tell(shooter, Component.translatableWithFallback("redstone-photography.camera.needs_pandorical",
				"Taking pictures needs Pandorical 15.15 or later on your client."));
			return InteractionResult.FAIL;
		}
		if (!player.hasInfiniteMaterials() && film(player).isEmpty()) {
			tell(shooter, Component.translatableWithFallback("redstone-photography.camera.no_film",
				"No film: craft some from paper and an ink sac."));
			return InteractionResult.FAIL;
		}

		player.getCooldowns().addCooldown(camera, COOLDOWN_TICKS);
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.LEVER_CLICK, SoundSource.PLAYERS, 0.6F, 1.8F);
		PandoricalApi.capture().request(shooter, SIDE).thenAccept(capture -> developed(shooter, capture));
		return InteractionResult.SUCCESS_SERVER;
	}

	private static void developed(ServerPlayer shooter, Optional<Capture> capture) {
		if (shooter.isRemoved()) return;
		if (capture.isEmpty()) {
			tell(shooter, Component.translatableWithFallback("redstone-photography.camera.jammed",
				"The shutter jammed: close any screen and try again."));
			return;
		}
		ItemStack film = film(shooter);
		if (!shooter.hasInfiniteMaterials() && film.isEmpty()) {
			tell(shooter, Component.translatableWithFallback("redstone-photography.camera.no_film",
				"No film: craft some from paper and an ink sac."));
			return;
		}
		UUID photo = UUID.randomUUID();
		if (!Photos.save(shooter.level().getServer(), photo, capture.get().png())) {
			tell(shooter, Component.translatableWithFallback("redstone-photography.camera.not_saved",
				"The picture could not be kept: the server could not save it."));
			return;
		}
		film.consume(1, shooter);
		ItemStack exposed = Film.exposed(new Film.Exposure(photo, shooter.getName().getString()));
		shooter.getInventory().placeItemBackInInventory(exposed, Prediction.SERVER_ONLY);
	}

	/** The film the camera takes from: the off hand first, then the inventory in order. */
	private static ItemStack film(Player player) {
		if (player.getOffhandItem().is(Main.FILM)) return player.getOffhandItem();
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.is(Main.FILM)) return stack;
		}
		return ItemStack.EMPTY;
	}

	private static void tell(ServerPlayer player, Component message) {
		player.sendOverlayMessage(message);
	}
}
