package justfatlard.redstone_photography;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Whether a cauldron stands in a darkroom: no daylight reaching it, and lit, but only by redstone
 * light (the blocks tagged {@code redstone-photography:safelights}).
 *
 * <p>Light is followed back from the cauldron the way it spreads, losing a level a block and more
 * through what dims it, and stopped by what it cannot pass. So a torch on the far side of a wall
 * does not spoil a darkroom, and one round the corner of an open door does.
 */
public final class Darkroom {
	private Darkroom() {}

	public static final TagKey<Block> SAFELIGHTS = TagKey.create(Registries.BLOCK,
		Identifier.fromNamespaceAndPath(Main.MOD_ID, "safelights"));

	private static final int BRIGHTEST = 15;

	/** Null when film can go in here; otherwise what is wrong, to tell the player. */
	public static Component problem(ServerLevel level, BlockPos cauldron) {
		Component leak = lightLeak(level, cauldron);
		if (leak != null) return leak;
		if (level.getBrightness(LightLayer.BLOCK, cauldron.above()) == 0) {
			return Component.translatableWithFallback("redstone-photography.darkroom.unlit",
				"Too dark to work: light the room with a redstone lamp or torch.");
		}
		return null;
	}

	/**
	 * Null when no light but redstone light reaches the cauldron; otherwise what gets in. Darkness
	 * is no harm to film, so this alone is what spoils film already developing.
	 */
	public static Component lightLeak(ServerLevel level, BlockPos cauldron) {
		BlockPos air = cauldron.above();
		if (level.getBrightness(LightLayer.SKY, air) > 0) {
			return Component.translatableWithFallback("redstone-photography.darkroom.daylight",
				"Daylight gets in here: develop film in a closed room.");
		}
		BlockState leak = leak(level, air);
		if (leak != null) {
			return Component.translatableWithFallback("redstone-photography.darkroom.light_leak",
				"%s lights this room: only redstone light is safe for film.", leak.getBlock().getName());
		}
		return null;
	}

	/** A light other than a safelight that reaches {@code from}, or null. */
	private static BlockState leak(ServerLevel level, BlockPos from) {
		// Searched by what light would lose getting here, least first
		LongArrayList[] byLoss = new LongArrayList[BRIGHTEST];
		for (int i = 0; i < BRIGHTEST; i++) byLoss[i] = new LongArrayList();
		Long2IntOpenHashMap loss = new Long2IntOpenHashMap();
		loss.defaultReturnValue(Integer.MAX_VALUE);
		byLoss[0].add(from.asLong());
		loss.put(from.asLong(), 0);

		BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();
		for (int lost = 0; lost < BRIGHTEST; lost++) {
			LongArrayList queue = byLoss[lost];
			for (int i = 0; i < queue.size(); i++) {
				long at = queue.getLong(i);
				if (loss.get(at) != lost) continue;
				for (Direction direction : Direction.values()) {
					next.set(at).move(direction);
					if (!level.isLoaded(next)) continue;
					BlockState state = level.getBlockState(next);
					int reaching = lost + 1;
					if (state.getLightEmission() > reaching && !state.is(SAFELIGHTS)) return state;
					int dims = state.getLightDampening();
					if (dims >= BRIGHTEST) continue;
					int through = lost + Math.max(1, dims);
					if (through >= BRIGHTEST || through >= loss.get(next.asLong())) continue;
					loss.put(next.asLong(), through);
					byLoss[through].add(next.asLong());
				}
			}
		}
		return null;
	}
}
