package com.pixelreel.blocks;

import com.mojang.serialization.MapCodec;
import com.pixelreel.ClientBridge;
import com.pixelreel.blockentities.DisplayBlockEntity;
import com.pixelreel.blockentities.ScreenPanelBlockEntity;
import com.pixelreel.registry.ModBlockEntities;
import com.pixelreel.registry.ModBlocks;
import com.pixelreel.server.ScreenControllerLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** this adds collision to the display i think this is needed imagine getting blown up my a creeper and the movie turns off lame */
public class DisplayBlock extends BaseEntityBlock {
	public static final EnumProperty<Direction> FACING = EnumProperty.create(
		"facing",
		Direction.class,
		Direction.Plane.HORIZONTAL
	);
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

	private final DisplayType type;

	public DisplayBlock(Properties properties, DisplayType type) {
		super(properties);
		this.type = type;
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, Boolean.FALSE));
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		DisplayType displayType = this.type;
		return simpleCodec(properties -> new DisplayBlock(properties, displayType));
	}

	public DisplayType type() {
		return this.type;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, POWERED);
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new DisplayBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> beType) {
		if (level.isClientSide()) {
			return null;
		}
		return createTickerHelper(beType, ModBlockEntities.DISPLAY, (tickLevel, pos, tickState, blockEntity) -> blockEntity.serverTick());
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction facing = context.getHorizontalDirection().getOpposite();
		BlockPos controllerPos = context.getClickedPos();
		Level level = context.getLevel();
		for (ScreenShapes.Cell cell : ScreenShapes.cells(this.type)) {
			BlockPos cellPos = ScreenShapes.cellPos(controllerPos, this.type, facing, cell);
			if (cellPos.equals(controllerPos)) {
				continue;
			}
			if (cell.depthCell() > 0) {
				continue;
			}
			BlockState existing = level.getBlockState(cellPos);
			if (!existing.canBeReplaced(context)) {
				return null;
			}
		}
		return this.defaultBlockState().setValue(FACING, facing).setValue(POWERED, Boolean.FALSE);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
		super.setPlacedBy(level, pos, state, by, itemStack);
		if (!level.isClientSide()) {
			placePanels(level, pos, this.type, state.getValue(FACING), false);
		}
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!state.is(newState.getBlock())) {
			removePanels(level, pos, this.type, state.getValue(FACING));
		}
		super.onRemove(state, level, pos, newState, movedByPiston);
	}

	public static int placePanels(Level level, BlockPos controllerPos, DisplayType type, Direction facing, boolean onlyReplaceable) {
		int placed = 0;
		for (ScreenShapes.Cell cell : ScreenShapes.cells(type)) {
			BlockPos cellPos = ScreenShapes.cellPos(controllerPos, type, facing, cell);
			if (cellPos.equals(controllerPos) || cell.depthCell() > 0) {
				continue;
			}
			BlockState existing = level.getBlockState(cellPos);
			if (existing.getBlock() instanceof ScreenPanelBlock) {
				if (level.getBlockEntity(cellPos) instanceof ScreenPanelBlockEntity panel && !panel.isBoundTo(controllerPos)) {
					panel.bindTo(controllerPos);
					placed++;
				}
			} else if (existing.isAir() || (!onlyReplaceable && existing.canBeReplaced())) {
				level.setBlock(cellPos, ModBlocks.SCREEN_PANEL.defaultBlockState(), Block.UPDATE_ALL);
				if (level.getBlockEntity(cellPos) instanceof ScreenPanelBlockEntity panel) {
					panel.bindTo(controllerPos);
				}
				placed++;
			}
		}
		return placed;
	}

	public static void removePanels(Level level, BlockPos controllerPos, DisplayType type, Direction facing) {
		for (ScreenShapes.Cell cell : ScreenShapes.cells(type)) {
			BlockPos cellPos = ScreenShapes.cellPos(controllerPos, type, facing, cell);
			if (cellPos.equals(controllerPos)) {
				continue;
			}
			if (level.getBlockState(cellPos).getBlock() instanceof ScreenPanelBlock
				&& level.getBlockEntity(cellPos) instanceof ScreenPanelBlockEntity panel
				&& panel.isBoundTo(controllerPos)) {
				level.removeBlock(cellPos, false);
			}
		}
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return controllerShape(state);
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return controllerShape(state);
	}

	@Override
	protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
		return controllerShape(state);
	}

	private VoxelShape controllerShape(BlockState state) {
		Direction facing = state.getValue(FACING);
		VoxelShape screen = ScreenShapes.cellShapeForRow(this.type, facing, this.type.controllerColumn(), 0, 0);
		if (!screen.isEmpty()) {
			return screen;
		}
		return ScreenShapes.controllerStandShape(this.type, facing);
	}

	@Override
	protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
		return Shapes.empty();
	}

	@Override
	protected boolean useShapeForLightOcclusion(BlockState state) {
		return true;
	}

	@Override
	protected boolean isCollisionShapeFullBlock(BlockState state, BlockGetter level, BlockPos pos) {
		return false;
	}

	@Override
	protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
		return true;
	}

	@Override
	protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
		return 1.0F;
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		return interactWithDisplay(level, pos, player);
	}

	public static InteractionResult interactWithDisplay(Level level, BlockPos controllerPos, Player player) {
		if (!(level.getBlockEntity(controllerPos) instanceof DisplayBlockEntity display)) {
			return InteractionResult.PASS;
		}
		if (player.isShiftKeyDown()) {
			if (!level.isClientSide()) {
				ScreenControllerLogic.setPowered(display, !display.isPowered());
			}
			return InteractionResult.SUCCESS;
		}
		if (level.isClientSide()) {
			ClientBridge.openChannelMenu(controllerPos);
		}
		return InteractionResult.SUCCESS;
	}

	public static @Nullable DisplayBlockEntity displayAt(@Nullable BlockGetter level, @Nullable BlockPos pos) {
		if (level == null || pos == null) {
			return null;
		}
		return level.getBlockEntity(pos) instanceof DisplayBlockEntity display ? display : null;
	}

	public static @Nullable DisplayBlockEntity controllerAt(@Nullable BlockGetter level, @Nullable BlockPos pos) {
		if (level == null || pos == null) {
			return null;
		}
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (blockEntity instanceof DisplayBlockEntity display) {
			return display;
		}
		if (blockEntity instanceof ScreenPanelBlockEntity panel) {
			return displayAt(level, panel.controllerPos());
		}
		return null;
	}
}
