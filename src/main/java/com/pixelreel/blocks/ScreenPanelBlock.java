package com.pixelreel.blocks;

import com.pixelreel.blockentities.DisplayBlockEntity;
import com.pixelreel.blockentities.ScreenPanelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** added invisible collision over the active screen */
public class ScreenPanelBlock extends BaseEntityBlock {
	private static final VoxelShape FALLBACK = net.minecraft.world.level.block.Block.box(0.0, 0.0, 7.0, 16.0, 16.0, 9.0);

	public ScreenPanelBlock(Properties properties) {
		super(properties);
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ScreenPanelBlockEntity(pos, state);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		return null;
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shapeAt(level, pos);
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shapeAt(level, pos);
	}

	@Override
	public VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
		return shapeAt(level, pos);
	}

	@Override
	public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
		return Shapes.empty();
	}

	@Override
	public boolean useShapeForLightOcclusion(BlockState state) {
		return true;
	}

	@Override
	public boolean isCollisionShapeFullBlock(BlockState state, BlockGetter level, BlockPos pos) {
		return false;
	}

	@Override
	public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
		return true;
	}

	@Override
	public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
		return 1.0F;
	}

	private static VoxelShape shapeAt(BlockGetter level, BlockPos pos) {
		if (level.getBlockEntity(pos) instanceof ScreenPanelBlockEntity panel) {
			VoxelShape shape = panel.shape();
			if (shape != null) {
				return shape;
			}
		}
		return FALLBACK;
	}

	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
		if (level.getBlockEntity(pos) instanceof ScreenPanelBlockEntity panel) {
			DisplayBlockEntity controller = DisplayBlock.displayAt(level, panel.controllerPos());
			if (controller != null) {
				return DisplayBlock.interactWithDisplay(level, controller.getBlockPos(), player);
			}
		}
		return InteractionResult.PASS;
	}
}
