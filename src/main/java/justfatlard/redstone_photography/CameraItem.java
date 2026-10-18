package justfatlard.redstone_photography;

import justfatlard.pandorical.api.Capture;
import justfatlard.pandorical.api.PandoricalApi;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

/**
 * Takes a picture of what its holder sees, onto the film loaded in it. Film goes in as it goes
 * into a bundle, clicked onto the camera in an inventory, and exposed film comes out one at a time
 * to a right click with nothing held. Used against a block while sneaking, it is set down there.
 */
public class CameraItem extends Item {
	/** Twice the print's 256, so the kept file is worth looking at on its own. */
	public static final int SIDE = 512;
	private static final int COOLDOWN_TICKS = 20;

	public CameraItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		if (player == null || !player.isSecondaryUseActive()) return InteractionResult.PASS;
		return CameraBlock.place(context);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		// Sneaking sets it down, and the client asks for a use as well when it does
		if (player.isSecondaryUseActive()) return InteractionResult.PASS;
		if (!(player instanceof ServerPlayer shooter)) return InteractionResult.SUCCESS;
		ItemStack camera = player.getItemInHand(hand);
		if (!PandoricalApi.capture().canCapture(shooter)) {
			tell(shooter, Component.translatableWithFallback("redstone-photography.camera.needs_pandorical",
				"Taking pictures needs Pandorical 15.15 or later on your client."));
			return InteractionResult.FAIL;
		}
		if (!player.hasInfiniteMaterials() && Roll.read(camera).blank() == 0) {
			tell(shooter, noFilm());
			return InteractionResult.FAIL;
		}

		player.getCooldowns().addCooldown(camera, COOLDOWN_TICKS);
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.LEVER_CLICK, SoundSource.PLAYERS, 0.6F, 1.8F);
		PandoricalApi.capture().request(shooter, SIDE).thenAccept(capture -> developed(shooter, camera, capture));
		return InteractionResult.SUCCESS_SERVER;
	}

	private static void developed(ServerPlayer shooter, ItemStack camera, Optional<Capture> capture) {
		if (shooter.isRemoved()) return;
		if (capture.isEmpty()) {
			tell(shooter, Component.translatableWithFallback("redstone-photography.camera.jammed",
				"The shutter jammed: close any screen and try again."));
			return;
		}
		if (!carries(shooter, camera)) {
			tell(shooter, Component.translatableWithFallback("redstone-photography.camera.put_away",
				"The picture was lost: the camera left your inventory before it was taken."));
			return;
		}
		Roll roll = Roll.read(camera);
		if (!shooter.hasInfiniteMaterials() && roll.blank() == 0) {
			tell(shooter, noFilm());
			return;
		}
		UUID photo = UUID.randomUUID();
		if (!Photos.save(shooter.level().getServer(), photo, capture.get().png())) {
			tell(shooter, Component.translatableWithFallback("redstone-photography.camera.not_saved",
				"The picture could not be kept: the server could not save it."));
			return;
		}
		Film.Exposure exposure = new Film.Exposure(photo, shooter.getName().getString());
		if (roll.blank() > 0 || roll.room() > 0) {
			roll.shoot(exposure).write(camera);
		} else {
			// A creative player's camera, full of exposures
			shooter.getInventory().placeItemBackInInventory(Film.exposed(exposure), Prediction.SERVER_ONLY);
		}
	}

	/** Still this very camera, not one like it: the picture goes onto the film it was taken with. */
	private static boolean carries(Player player, ItemStack camera) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			if (player.getInventory().getItem(slot) == camera) return true;
		}
		return false;
	}

	// --- Loading, as a bundle loads ---

	/** The camera held on the cursor, clicked onto a slot. */
	@Override
	public boolean overrideStackedOnOther(ItemStack camera, Slot slot, ClickAction action, Player player) {
		ItemStack other = slot.getItem();
		if (other.is(Main.FILM)) {
			if (!slot.allowModification(player)) return false;
			load(camera, other, action, player);
			slot.setChanged();
			return true;
		}
		if (other.isEmpty() && action == ClickAction.SECONDARY) {
			Roll roll = Roll.read(camera);
			if (roll.exposed().isEmpty() || !slot.mayPlace(Film.exposed(roll.last()))) return false;
			slot.safeInsert(Film.exposed(roll.last()));
			roll.withoutLast().write(camera);
			sound(player, SoundEvents.BUNDLE_REMOVE_ONE);
			return true;
		}
		return false;
	}

	/** Something on the cursor clicked onto the camera in its slot. */
	@Override
	public boolean overrideOtherStackedOnMe(ItemStack camera, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess carried) {
		if (!slot.allowModification(player)) return false;
		if (other.is(Main.FILM)) {
			load(camera, other, action, player);
			return true;
		}
		if (other.isEmpty() && action == ClickAction.SECONDARY) {
			Roll roll = Roll.read(camera);
			if (roll.exposed().isEmpty()) return false;
			carried.set(Film.exposed(roll.last()));
			roll.withoutLast().write(camera);
			sound(player, SoundEvents.BUNDLE_REMOVE_ONE);
			return true;
		}
		return false;
	}

	/** A left click loads all that fits, a right click one. */
	private static void load(ItemStack camera, ItemStack film, ClickAction action, Player player) {
		Roll roll = Roll.read(camera);
		int loading = Math.min(action == ClickAction.PRIMARY ? film.getCount() : 1, roll.room());
		if (loading == 0) {
			sound(player, SoundEvents.BUNDLE_INSERT_FAIL);
			return;
		}
		roll.load(loading).write(camera);
		film.shrink(loading);
		sound(player, SoundEvents.BUNDLE_INSERT);
	}

	private static void sound(Player player, SoundEvent sound) {
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 0.8F,
			0.8F + player.level().getRandom().nextFloat() * 0.4F);
	}

	private static Component noFilm() {
		return Component.translatableWithFallback("redstone-photography.camera.no_film",
			"No film in the camera: click film onto it in your inventory.");
	}

	private static void tell(ServerPlayer player, Component message) {
		player.sendOverlayMessage(message);
	}
}
