package com.pixelreel.blockentities;

import com.pixelreel.blocks.DisplayBlock;
import com.pixelreel.blocks.ScreenShapes;
import com.pixelreel.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class ScreenPanelBlockEntity extends BlockEntity {
	private static final String KEY_CONTROLLER = "Controller";

	private @Nullable BlockPos controllerPos;
	private @Nullable VoxelShape cachedShape;

	public ScreenPanelBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.SCREEN_PANEL, pos, state);
	}

	public @Nullable BlockPos controllerPos() {
		return this.controllerPos;
	}

	public boolean isBoundTo(BlockPos pos) {
		return pos.equals(this.controllerPos);
	}

	public void bindTo(BlockPos pos) {
		this.controllerPos = pos.immutable();
		this.cachedShape = null;
		this.setChanged();
		if (this.level != null && !this.level.isClientSide()) {
			BlockState state = this.getBlockState();
			this.level.sendBlockUpdated(this.worldPosition, state, state, 3);
		}
	}

	public @Nullable VoxelShape shape() {
		VoxelShape cached = this.cachedShape;
		if (cached != null) {
			return cached;
		}
		DisplayBlockEntity controller = DisplayBlock.displayAt(this.level, this.controllerPos);
		if (controller == null) {
			return null;
		}
		DisplayBlock block = controller.displayBlock();
		if (block == null) {
			return null;
		}
		Direction facing = controller.facing();
		ScreenShapes.Cell cell = ScreenShapes.cellOf(controller.getBlockPos(), block.type(), facing, this.worldPosition);
		if (cell == null) {
			return null;
		}
		VoxelShape shape = ScreenShapes.cellShapeForRow(block.type(), facing, cell.column(), cell.row(), cell.depthCell());
		this.cachedShape = shape;
		return shape;
	}

	@Override
	protected void saveAdditional(CompoundTag output) {
		super.saveAdditional(output);
		if (this.controllerPos != null) {
			output.putLong(KEY_CONTROLLER, this.controllerPos.asLong());
		}
	}

	@Override
	public void load(CompoundTag input) {
		super.load(input);
		long packed = input.contains(KEY_CONTROLLER) ? input.getLong(KEY_CONTROLLER) : Long.MIN_VALUE;
		this.controllerPos = packed == Long.MIN_VALUE ? null : BlockPos.of(packed);
		this.cachedShape = null;
	}

	@Override
	public CompoundTag getUpdateTag() {
		CompoundTag tag = new CompoundTag();
		this.saveAdditional(tag);
		return tag;
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}
}
