package justfatlard.redstone_photography;

import com.mojang.math.Transformation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * Film developing in a water cauldron. The film floats on the water as an item display, which is
 * what keeps it across a restart, holding the game time it went in. Every second the room is
 * checked again: let light in before it is done and the film is fogged and lost. Done, it comes
 * out as a photograph and the cauldron is a level lower.
 */
public final class Developing {
	private Developing() {}

	public static final String TRAY_TAG = "redstone-photography.developing";
	public static final int DEVELOP_TICKS = 200;
	private static final int TELL_WITHIN = 16;

	public static boolean isTray(Entity entity) {
		return entity instanceof Display.ItemDisplay && entity.entityTags().contains(TRAY_TAG);
	}

	static InteractionResult use(ServerLevel level, BlockPos pos, Player player, InteractionHand hand) {
		BlockState state = level.getBlockState(pos);
		if (!state.is(Blocks.WATER_CAULDRON)) return InteractionResult.PASS;
		ItemStack held = player.getItemInHand(hand);
		Display.ItemDisplay tray = tray(level, pos);

		if (held.is(Main.EXPOSED_FILM)) {
			Film.Exposure exposure = Film.read(held);
			if (exposure == null) return InteractionResult.PASS;
			if (tray != null) {
				tell(player, Component.translatableWithFallback("redstone-photography.develop.busy",
					"A film is already developing in this cauldron."));
				return InteractionResult.FAIL;
			}
			Component problem = Darkroom.problem(level, pos);
			if (problem != null) {
				tell(player, problem);
				return InteractionResult.FAIL;
			}
			start(level, pos, state, exposure);
			held.consume(1, player);
			level.playSound(null, pos, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.3F, 1.4F);
			tell(player, Component.translatableWithFallback("redstone-photography.develop.started",
				"Developing: keep other light out for %s seconds.", DEVELOP_TICKS / 20));
			return InteractionResult.SUCCESS_SERVER;
		}

		if (tray != null && held.isEmpty() && hand == InteractionHand.MAIN_HAND) {
			Film.Exposure exposure = exposureOn(tray);
			tray.discard();
			if (exposure != null) player.getInventory().placeItemBackInInventory(Film.exposed(exposure), Prediction.SERVER_ONLY);
			return InteractionResult.SUCCESS_SERVER;
		}
		return InteractionResult.PASS;
	}

	private static void start(ServerLevel level, BlockPos pos, BlockState cauldron, Film.Exposure exposure) {
		ItemStack film = Film.exposed(exposure);
		CustomData.update(DataComponents.CUSTOM_DATA, film, root -> {
			var tag = root.getCompoundOrEmpty(Main.MOD_ID);
			tag.putLong("since", level.getGameTime());
			root.put(Main.MOD_ID, tag);
		});

		Display.ItemDisplay tray = EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
		if (tray == null) return;
		tray.setPos(Vec3.atBottomCenterOf(pos).add(0, surface(cauldron) + 0.01, 0));
		tray.setItemStack(film);
		tray.setItemTransform(ItemDisplayContext.FIXED);
		tray.setTransformation(new Transformation(null, new Quaternionf().rotateX((float) (Math.PI / 2)),
			new Vector3f(0.5F, 0.5F, 0.5F), null));
		tray.addTag(TRAY_TAG);
		level.addFreshEntity(tray);
	}

	/** How high the water stands in a cauldron, in blocks from its floor. */
	private static double surface(BlockState cauldron) {
		return (6.0 + 3.0 * cauldron.getValue(LayeredCauldronBlock.LEVEL)) / 16.0;
	}

	static void check(ServerLevel level, Display.ItemDisplay tray) {
		if (tray.isRemoved()) return;
		BlockPos pos = tray.blockPosition();
		if (!level.isLoaded(pos)) return;
		Film.Exposure exposure = exposureOn(tray);
		BlockState state = level.getBlockState(pos);
		if (exposure == null) {
			tray.discard();
			return;
		}
		if (!state.is(Blocks.WATER_CAULDRON)) {
			tray.discard();
			pop(level, pos, Film.exposed(exposure));
			return;
		}

		Component problem = Darkroom.lightLeak(level, pos);
		if (problem != null) {
			tray.discard();
			level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 1.2F);
			tellNear(level, pos, Component.translatableWithFallback("redstone-photography.develop.fogged",
				"The film is fogged and lost. %s", problem));
			return;
		}

		long since = tray.getItemStack().getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
			.getCompoundOrEmpty(Main.MOD_ID).getLongOr("since", level.getGameTime());
		if (level.getGameTime() - since < DEVELOP_TICKS) {
			level.sendParticles(ParticleTypes.BUBBLE_POP, tray.getX(), tray.getY() + 0.05, tray.getZ(), 2, 0.15, 0, 0.15, 0);
			return;
		}

		tray.discard();
		int[] maps = Photos.print(level, exposure.photo());
		if (maps == null) {
			tellNear(level, pos, Component.translatableWithFallback("redstone-photography.develop.blank",
				"The film came out blank: the picture it held is gone from the server."));
			return;
		}
		LayeredCauldronBlock.lowerFillLevel(state, level, pos);
		level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1.0F, 1.0F);
		pop(level, pos, Prints.photograph(new Prints.Print(exposure.photo(), exposure.by(), maps)));
	}

	private static Film.Exposure exposureOn(Display.ItemDisplay tray) {
		return Film.read(tray.getItemStack());
	}

	private static Display.ItemDisplay tray(ServerLevel level, BlockPos pos) {
		List<Display.ItemDisplay> found = level.getEntitiesOfClass(Display.ItemDisplay.class, new AABB(pos), Developing::isTray);
		return found.isEmpty() ? null : found.get(0);
	}

	private static void pop(ServerLevel level, BlockPos pos, ItemStack stack) {
		ItemEntity item = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, stack, 0, 0.2, 0);
		item.setDefaultPickUpDelay();
		level.addFreshEntity(item);
	}

	private static void tell(Player player, Component message) {
		player.sendOverlayMessage(message);
	}

	private static void tellNear(ServerLevel level, BlockPos pos, Component message) {
		for (ServerPlayer player : level.players()) {
			if (player.blockPosition().closerThan(pos, TELL_WITHIN)) player.sendOverlayMessage(message);
		}
	}
}
