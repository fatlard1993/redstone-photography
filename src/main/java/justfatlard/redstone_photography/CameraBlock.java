package justfatlard.redstone_photography;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * A camera set down, its lens facing the way its owner looked. A redstone signal coming on takes
 * a picture onto its film; film is loaded by using it on the camera, and the exposures are taken
 * out with an empty hand.
 */
public class CameraBlock extends Block implements EntityBlock {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
	public static final BooleanProperty TRIGGERED = BlockStateProperties.TRIGGERED;

	// The body and the lens barrel, as the model draws them facing north
	private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateAll(Shapes.or(
		Block.box(2, 1, 5, 14, 11, 13),
		Block.box(5, 3, 2, 11, 9, 5)));

	public CameraBlock(Properties properties) {
		super(properties);
		registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH).setValue(TRIGGERED, false));
	}

	/** Sets a camera down where a sneaking player uses one against a block. */
	static InteractionResult place(UseOnContext use) {
		BlockPlaceContext context = new BlockPlaceContext(use);
		Level level = context.getLevel();
		Player player = context.getPlayer();
		BlockPos pos = context.getClickedPos();
		ItemStack camera = context.getItemInHand();
		if (!context.canPlace()) return InteractionResult.FAIL;
		BlockState state = Main.CAMERA_BLOCK.defaultBlockState()
			.setValue(FACING, context.getNearestLookingDirection())
			// Placed against a signal already on, it waits for the next
			.setValue(TRIGGERED, level.hasNeighborSignal(pos));
		if (!level.isUnobstructed(state, pos, player == null ? CollisionContext.empty() : CollisionContext.placementContext(player))) {
			return InteractionResult.FAIL;
		}
		if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
		if (player != null && !player.mayUseItemAt(pos, context.getClickedFace(), camera)) return InteractionResult.FAIL;

		server.setBlock(pos, state, Block.UPDATE_ALL_IMMEDIATE);
		if (server.getBlockEntity(pos) instanceof CameraBlockEntity placed) {
			placed.setUp(Roll.read(camera), player == null ? "" : player.getName().getString());
		}
		server.playSound(null, pos, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.9F);
		server.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(player, state));
		camera.consume(1, player);
		return InteractionResult.SUCCESS_SERVER;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES.get(state.getValue(FACING));
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
		boolean powered = level.hasNeighborSignal(pos);
		if (powered == state.getValue(TRIGGERED)) return;
		level.setBlock(pos, state.setValue(TRIGGERED, powered), Block.UPDATE_CLIENTS);
		if (powered && level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof CameraBlockEntity camera) {
			camera.shoot(server);
		}
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (!stack.is(Main.FILM)) return InteractionResult.TRY_WITH_EMPTY_HAND;
		if (!(level.getBlockEntity(pos) instanceof CameraBlockEntity camera)) return InteractionResult.PASS;
		if (level.isClientSide()) return InteractionResult.SUCCESS;
		return camera.load(player, stack) ? InteractionResult.SUCCESS_SERVER : InteractionResult.FAIL;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level.getBlockEntity(pos) instanceof CameraBlockEntity camera)) return InteractionResult.PASS;
		if (level.isClientSide()) return InteractionResult.SUCCESS;
		camera.takeOut(player);
		return InteractionResult.SUCCESS_SERVER;
	}

	@Override
	public BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	public BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, TRIGGERED);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new CameraBlockEntity(pos, state);
	}
}
