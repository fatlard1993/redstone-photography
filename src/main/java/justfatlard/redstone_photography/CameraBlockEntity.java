package justfatlard.redstone_photography;

import justfatlard.pandorical.api.Capture;
import justfatlard.pandorical.api.PandoricalApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A placed camera's film, and who set it down, whose name its pictures carry.
 *
 * <p>The server cannot draw the world, so a picture is drawn by the client of the nearest player
 * who has the camera's chunk loaded, off screen, from the lens. With no such player the shutter
 * clicks dry and no film is spent.
 */
public class CameraBlockEntity extends BlockEntity {
	/** From the block's centre to just past the lens, so the camera's own model is not in the picture. */
	private static final double LENS = 0.55;

	private Roll roll = Roll.EMPTY;
	private String owner = "";
	private boolean shooting;

	public CameraBlockEntity(BlockPos pos, BlockState state) {
		super(Main.CAMERA_BLOCK_ENTITY, pos, state);
	}

	public Roll roll() {
		return roll;
	}

	void setUp(Roll roll, String owner) {
		this.roll = roll;
		this.owner = owner;
		setChanged();
	}

	// --- Film in and out ---

	boolean load(Player player, ItemStack film) {
		int loading = Math.min(film.getCount(), roll.room());
		if (loading == 0) {
			tell(player, Component.translatableWithFallback("redstone-photography.roll.full",
				"The camera is full: take the exposed film out first."));
			return false;
		}
		roll = roll.load(loading);
		film.consume(loading, player);
		setChanged();
		level.playSound(null, worldPosition, SoundEvents.BUNDLE_INSERT, SoundSource.BLOCKS, 0.8F, 1.2F);
		tell(player, roll.describe());
		return true;
	}

	void takeOut(Player player) {
		if (roll.exposed().isEmpty()) {
			tell(player, roll.describe());
			return;
		}
		for (Film.Exposure exposure : roll.exposed()) {
			player.getInventory().placeItemBackInInventory(Film.exposed(exposure), Prediction.SERVER_ONLY);
		}
		roll = new Roll(roll.blank(), List.of());
		setChanged();
		level.playSound(null, worldPosition, SoundEvents.BUNDLE_REMOVE_ONE, SoundSource.BLOCKS, 0.8F, 1.2F);
		tell(player, roll.describe());
	}

	// --- Taking a picture ---

	void shoot(ServerLevel level) {
		if (shooting) return;
		ServerPlayer photographer = roll.blank() == 0 ? null : photographer(level);
		if (photographer == null) {
			level.playSound(null, worldPosition, SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 0.5F, 1.6F);
			return;
		}
		shooting = true;
		Direction facing = getBlockState().getValue(CameraBlock.FACING);
		Vec3 eye = Vec3.atCenterOf(worldPosition).add(Vec3.atLowerCornerOf(facing.getUnitVec3i()).scale(LENS));
		float yRot = facing.getAxis().isHorizontal() ? facing.toYRot() : 0.0F;
		float xRot = facing == Direction.UP ? -90.0F : facing == Direction.DOWN ? 90.0F : 0.0F;
		level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6F, 1.8F);
		String by = owner.isEmpty() ? photographer.getName().getString() : owner;
		PandoricalApi.capture().request(photographer, CameraItem.SIDE, eye, yRot, xRot)
			.thenAccept(capture -> developed(level, by, capture));
	}

	/** The nearest player whose client has this camera's chunk and can draw from a viewpoint. */
	private ServerPlayer photographer(ServerLevel level) {
		Vec3 here = Vec3.atCenterOf(worldPosition);
		return level.getChunkSource().chunkMap.getPlayers(ChunkPos.containing(worldPosition), false).stream()
			.filter(player -> PandoricalApi.capture().canCaptureFrom(player))
			.min(Comparator.comparingDouble(player -> player.distanceToSqr(here)))
			.orElse(null);
	}

	private void developed(ServerLevel level, String by, Optional<Capture> capture) {
		shooting = false;
		// Broken while the picture was on its way: the film went with the camera, unspent
		if (isRemoved()) return;
		if (capture.isEmpty() || roll.blank() == 0) {
			level.playSound(null, worldPosition, SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 0.5F, 1.6F);
			return;
		}
		UUID photo = UUID.randomUUID();
		if (!Photos.save(level.getServer(), photo, capture.get().png())) return;
		roll = roll.shoot(new Film.Exposure(photo, by));
		setChanged();
	}

	// --- Kept ---

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.store("roll", CompoundTag.CODEC, roll.toTag());
		output.putString("owner", owner);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		roll = input.read("roll", CompoundTag.CODEC).map(Roll::fromTag).orElse(Roll.EMPTY);
		owner = input.getStringOr("owner", "");
	}

	/** Broken, blown up or replaced, the camera comes back as an item with its film in it. */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level == null) return;
		ItemStack camera = new ItemStack(Main.CAMERA);
		roll.write(camera);
		Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), camera);
	}

	private static void tell(Player player, Component message) {
		player.sendOverlayMessage(message);
	}
}
