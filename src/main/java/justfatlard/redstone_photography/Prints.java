package justfatlard.redstone_photography;

import justfatlard.redstone_photography.mixin.ItemFrameAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Photographs, as items and on walls. A hung photograph is MC Paint's painting: one invisible,
 * fixed item frame per block, each holding the map that block was printed onto, all sharing a
 * group id. Fixed frames cannot be turned, emptied, pushed or knocked off, so a photograph comes
 * down only when a player hits it or the wall behind it goes.
 */
public final class Prints {
	private Prints() {}

	public static final String PRINT_TAG = "redstone-photography.photograph";

	/** @param maps in reading order: the top row left to right, then the next */
	public record Print(UUID photo, String by, int[] maps) {}

	// --- As an item ---

	public static ItemStack photograph(Print print) {
		ItemStack stack = new ItemStack(Main.PHOTOGRAPH);
		Film.write(stack, toTag(print));
		stack.set(DataComponents.ITEM_NAME, Component.translatableWithFallback("redstone-photography.photograph.named",
			"Photograph by %s", print.by()));
		stack.set(DataComponents.LORE, new ItemLore(List.of(Film.grey(Component.translatableWithFallback(
			"redstone-photography.photograph.size", "%s × %s blocks", Photos.TILES_ACROSS, Photos.TILES_ACROSS)))));
		return stack;
	}

	public static Print read(ItemStack stack) {
		if (!stack.is(Main.PHOTOGRAPH)) return null;
		return fromTag(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompoundOrEmpty(Main.MOD_ID));
	}

	private static Print fromTag(CompoundTag tag) {
		UUID photo = Film.uuid(tag.getStringOr("photo", ""));
		int[] maps = tag.getIntArray("maps").orElse(new int[0]);
		if (photo == null || maps.length != Photos.TILES_ACROSS * Photos.TILES_ACROSS) return null;
		return new Print(photo, tag.getStringOr("by", ""), maps);
	}

	private static CompoundTag toTag(Print print) {
		CompoundTag tag = new CompoundTag();
		tag.putString("photo", print.photo().toString());
		tag.putString("by", print.by());
		tag.putIntArray("maps", print.maps());
		return tag;
	}

	// --- Hanging ---

	static InteractionResult hang(UseOnContext context, Print print) {
		if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
		Player player = context.getPlayer();
		Direction facing = context.getClickedFace();
		if (!facing.getAxis().isHorizontal()) {
			tell(player, Component.translatableWithFallback("redstone-photography.hang.walls_only", "Photographs hang on walls."));
			return InteractionResult.FAIL;
		}

		List<BlockPos> tiles = tilePositions(context.getClickedPos().relative(facing), facing);
		for (BlockPos tile : tiles) {
			if (player != null && !player.mayUseItemAt(tile, facing, context.getItemInHand())) return InteractionResult.FAIL;
			if (!fits(level, tile, facing)) {
				tell(player, Component.translatableWithFallback("redstone-photography.hang.no_room",
					"Won't fit: a photograph needs a clear, flat wall %s × %s blocks.", Photos.TILES_ACROSS, Photos.TILES_ACROSS));
				return InteractionResult.FAIL;
			}
		}

		String group = UUID.randomUUID().toString();
		for (int i = 0; i < tiles.size(); i++) {
			ItemFrame frame = new ItemFrame(level, tiles.get(i), facing);
			frame.setInvisible(true);
			frame.setSilent(true);
			((ItemFrameAccessor) frame).setPrintFixed(true);
			frame.setItem(tileStack(print, group, i), false);
			frame.addTag(PRINT_TAG);
			level.addFreshEntity(frame);
		}
		level.playSound(null, tiles.get(0), SoundEvents.PAINTING_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
		context.getItemInHand().consume(1, player);
		return InteractionResult.SUCCESS_SERVER;
	}

	/** Centred on the clicked block, the odd block going right and up, the way vanilla hangs a painting. */
	private static List<BlockPos> tilePositions(BlockPos clicked, Direction facing) {
		Direction right = facing.getCounterClockWise();
		int across = Photos.TILES_ACROSS;
		int left = (across - 1) / 2;
		int below = (across - 1) / 2;
		List<BlockPos> tiles = new ArrayList<>();
		for (int row = 0; row < across; row++) {
			for (int column = 0; column < across; column++) {
				tiles.add(clicked.relative(right, column - left).above(across - 1 - row - below));
			}
		}
		return tiles;
	}

	private static boolean fits(ServerLevel level, BlockPos tile, Direction facing) {
		if (!level.getBlockState(tile.relative(facing.getOpposite())).isSolid()) return false;
		if (!level.getBlockState(tile).getCollisionShape(level, tile).isEmpty()) return false;
		return level.getEntitiesOfClass(HangingEntity.class, new AABB(tile)).isEmpty();
	}

	private static ItemStack tileStack(Print print, String group, int tile) {
		ItemStack map = new ItemStack(Items.FILLED_MAP);
		map.set(DataComponents.MAP_ID, new MapId(print.maps()[tile]));
		CompoundTag tag = toTag(print);
		tag.putString("group", group);
		Film.write(map, tag);
		return map;
	}

	private static CompoundTag tileData(ItemFrame frame) {
		return frame.getItem().getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompoundOrEmpty(Main.MOD_ID);
	}

	// --- Taking down ---

	public static boolean isPrint(Entity entity) {
		return entity instanceof ItemFrame && entity.entityTags().contains(PRINT_TAG);
	}

	/** Takes down the whole photograph and gives it back where it was hit; nothing to a creative player. */
	static void takeDown(ServerLevel level, ItemFrame hit, Player by) {
		CompoundTag data = tileData(hit);
		String group = data.getStringOr("group", "");
		List<ItemFrame> parts = group.isEmpty() ? List.of(hit) : level.getEntitiesOfClass(ItemFrame.class,
			hit.getBoundingBox().inflate(Photos.TILES_ACROSS),
			frame -> isPrint(frame) && group.equals(tileData(frame).getStringOr("group", "")));
		Print print = fromTag(data);
		if (print != null && (by == null || !by.hasInfiniteMaterials())) hit.spawnAtLocation(level, photograph(print));
		level.playSound(null, hit.getPos(), SoundEvents.PAINTING_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
		for (ItemFrame part : parts) part.discard();
	}

	/** A photograph whose wall has gone comes down. */
	static void check(ServerLevel level, ItemFrame frame) {
		if (frame.isRemoved() || !isPrint(frame)) return;
		BlockPos wall = frame.getPos().relative(frame.getDirection().getOpposite());
		if (level.isLoaded(wall) && !level.getBlockState(wall).isSolid()) takeDown(level, frame, null);
	}

	private static void tell(Player player, Component message) {
		if (player != null) player.sendOverlayMessage(message);
	}
}
