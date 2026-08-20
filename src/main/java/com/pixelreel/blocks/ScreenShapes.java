package com.pixelreel.blocks;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** adds the thin collision */
public final class ScreenShapes {
	private static final float HALF_THICKNESS = 1.2F;
	private static final int SUB_BOXES_PER_CELL = 4;

	private ScreenShapes() {
	}

	public record Cell(int column, int row, int depthCell) {
	}

	public static List<Cell> cells(DisplayType type) {
		List<Cell> cells = new ArrayList<>();
		boolean controllerAdded = false;
		int colStart = Math.max(0, (int)Math.floor(type.screenLeft() / 16.0F));
		int colEnd = Math.min(type.widthBlocks() - 1, (int)Math.floor((type.screenRight() - 0.001F) / 16.0F));
		int rowStart = Math.max(0, (int)Math.floor(type.screenBottom() / 16.0F));
		int rowEnd = Math.min(type.heightBlocks() - 1, (int)Math.floor((type.screenTop() - 0.001F) / 16.0F));

		for (int column = colStart; column <= colEnd; column++) {
			float[] range = columnDepthRange(type, column);
			int kMin = Math.floorDiv((int)Math.floor(range[0] - HALF_THICKNESS), 16);
			int kMax = Math.floorDiv((int)Math.floor(range[1] + HALF_THICKNESS), 16);
			kMin = Math.max(-1, kMin);
			kMax = Math.min(1, kMax);
			for (int k = kMin; k <= kMax; k++) {
				for (int row = rowStart; row <= rowEnd; row++) {
					if (!cellIntersectsScreen(type, column, row)) {
						continue;
					}
					cells.add(new Cell(column, row, k));
					if (column == type.controllerColumn() && row == 0 && k == 0) {
						controllerAdded = true;
					}
				}
			}
		}
		if (!controllerAdded) {
			cells.add(new Cell(type.controllerColumn(), 0, 0));
		}
		return cells;
	}

	private static boolean cellIntersectsScreen(DisplayType type, int column, int row) {
		float cellLeft = column * 16.0F;
		float cellRight = cellLeft + 16.0F;
		float cellBottom = row * 16.0F;
		float cellTop = cellBottom + 16.0F;
		return cellRight > type.screenLeft()
			&& cellLeft < type.screenRight()
			&& cellTop > type.screenBottom()
			&& cellBottom < type.screenTop();
	}

	public static BlockPos cellPos(BlockPos controllerPos, DisplayType type, Direction facing, Cell cell) {
		Direction columnAxis = facing.getCounterClockWise();
		Direction back = facing.getOpposite();
		return controllerPos.relative(columnAxis, cell.column() - type.controllerColumn())
			.above(cell.row())
			.relative(back, cell.depthCell());
	}

	public static @Nullable Cell cellOf(BlockPos controllerPos, DisplayType type, Direction facing, BlockPos pos) {
		Direction columnAxis = facing.getCounterClockWise();
		Direction back = facing.getOpposite();
		BlockPos delta = pos.subtract(controllerPos);
		int column = type.controllerColumn()
			+ delta.getX() * columnAxis.getStepX() + delta.getY() * columnAxis.getStepY() + delta.getZ() * columnAxis.getStepZ();
		int row = delta.getY();
		int depthCell = delta.getX() * back.getStepX() + delta.getZ() * back.getStepZ();
		if (column < 0 || column >= type.widthBlocks() || row < 0 || row >= type.heightBlocks()) {
			return null;
		}
		return new Cell(column, row, depthCell);
	}

	public static VoxelShape cellShape(DisplayType type, Direction facing, int column, int depthCell) {
		return cellShapeForRow(type, facing, column, 0, depthCell);
	}

	public static VoxelShape controllerStandShape(DisplayType type, Direction facing) {
		float z = Math.clamp(type.baseZ() - 1.0F, 1.0F, 14.0F);
		VoxelShape north = Block.box(4.0, 0.0, z - 2.0F, 12.0, 8.0, z + 2.0F);
		return rotateFromNorth(north, facing);
	}

	private static VoxelShape northCellShape(DisplayType type, int column, int depthCell) {
		return northCellShape(type, column, 0, depthCell);
	}

	private static VoxelShape northCellShape(DisplayType type, int column, int row, int depthCell) {
		float width = type.displayWidthPx();
		float localBottom = Math.max(0.0F, type.screenBottom() - row * 16.0F);
		float localTop = Math.min(16.0F, type.screenTop() - row * 16.0F);
		if (localTop - localBottom < 0.01F) {
			return Shapes.empty();
		}
		VoxelShape shape = Shapes.empty();
		for (int i = 0; i < SUB_BOXES_PER_CELL; i++) {
			float dLo = column * 16.0F + i * (16.0F / SUB_BOXES_PER_CELL);
			float dHi = dLo + 16.0F / SUB_BOXES_PER_CELL;
			if (dHi <= type.screenLeft() || dLo >= type.screenRight()) {
				continue;
			}
			float[] range = depthRange(type, dLo / width, dHi / width);
			float zMin = range[0] - HALF_THICKNESS - depthCell * 16.0F;
			float zMax = range[1] + HALF_THICKNESS - depthCell * 16.0F;
			zMin = Math.max(0.0F, zMin);
			zMax = Math.min(16.0F, zMax);
			if (zMax - zMin < 0.01F) {
				continue;
			}
			float xMin = 16.0F - (dHi - column * 16.0F);
			float xMax = 16.0F - (dLo - column * 16.0F);
			shape = Shapes.or(shape, Block.box(xMin, localBottom, zMin, xMax, localTop, zMax));
		}
		return shape;
	}

	public static VoxelShape cellShapeForRow(DisplayType type, Direction facing, int column, int row, int depthCell) {
		VoxelShape north = northCellShape(type, column, row, depthCell);
		if (north.isEmpty()) {
			return north;
		}
		return rotateFromNorth(north, facing);
	}

	private static VoxelShape rotateFromNorth(VoxelShape north, Direction facing) {
		if (facing == Direction.NORTH) {
			return north;
		}
		VoxelShape[] buffer = new VoxelShape[]{north, Shapes.empty()};
		int times = switch (facing) {
			case EAST -> 1;
			case SOUTH -> 2;
			case WEST -> 3;
			default -> 0;
		};
		for (int i = 0; i < times; i++) {
			buffer[0].forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
				buffer[1] = Shapes.or(buffer[1], Shapes.box(1.0 - maxZ, minY, minX, 1.0 - minZ, maxY, maxX));
			});
			buffer[0] = buffer[1];
			buffer[1] = Shapes.empty();
		}
		return buffer[0];
	}

	private static float[] columnDepthRange(DisplayType type, int column) {
		float width = type.displayWidthPx();
		return depthRange(type, column * 16.0F / width, (column + 1) * 16.0F / width);
	}

	private static float[] depthRange(DisplayType type, float u0, float u1) {
		float a = type.depthAt(u0);
		float b = type.depthAt(u1);
		float min = Math.min(a, b);
		float max = Math.max(a, b);
		if (u0 < 0.5F && u1 > 0.5F) {
			max = Math.max(max, type.depthAt(0.5F));
		}
		return new float[]{min, max};
	}
}
