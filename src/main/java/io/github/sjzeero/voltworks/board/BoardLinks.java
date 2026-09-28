/*
 * Copyright 2025 patryk3211
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Modified by sjzeero: ported from Create: Power Grid to CEE: Voltworks.
 */
package io.github.sjzeero.voltworks.board;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import io.github.sjzeero.voltworks.circuits.components.ViaComponent;
import io.github.sjzeero.voltworks.circuits.components.properties.Orientation;
import io.github.sjzeero.voltworks.circuits.schematic.PlacedComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static io.github.sjzeero.voltworks.board.BoardBlock.HORIZONTAL_FACING;
import static io.github.sjzeero.voltworks.board.BoardBlock.ROTATION;
import static io.github.sjzeero.voltworks.circuits.components.ViaComponent.EDGE_CONNECTIONS;
import static io.github.sjzeero.voltworks.circuits.components.ViaComponent.VERTICAL_PASSTHROUGH;

/**
 * Edge vias and vertical headers between neighbouring boards, same matching rules as Power Grid's
 * CircuitBoardBlockEntity#edgeConnect. Power Grid joins the two via nodes with a 0.002 ohm wire in its
 * global network; here each linked via becomes an extra board pin and the two pins are bridged in EE.
 */
public final class BoardLinks {
    public static final double LINK_RESISTANCE = 0.002;

    public record Link(PlacedComponent mine, BoardDevice other, PlacedComponent theirs) {
    }

    private BoardLinks() {
    }

    /** Vias that can ever take part in a link. */
    public static boolean canLink(PlacedComponent placed) {
        if(!(placed.component instanceof ViaComponent))
            return false;
        if(placed.get(EDGE_CONNECTIONS) && (placed.x == 0 || placed.y == 0 || placed.x == 15 || placed.y == 15))
            return true;
        return placed.get(VERTICAL_PASSTHROUGH);
    }

    public static List<Link> find(BoardDevice self, ServerLevel level) {
        var result = new ArrayList<Link>();
        var baked = self.baked();
        if(baked == null || !self.hasLinkVias())
            return result;
        var pos = self.pos();
        var state = level.getBlockState(pos);
        if(!(state.getBlock() instanceof BoardBlock))
            return result;
        var devices = DevicesSavedData.load(level);
        for(var orientation : Orientation.values()) {
            var dir = BoardBlock.getDirection(state, orientation);
            var other = neighbour(level, devices, pos.relative(dir));
            if(other == null)
                continue;
            var neighborState = level.getBlockState(other.pos());
            if(state.getValue(ROTATION) == 1) {
                if(neighborState.getValue(ROTATION) == 1 && state.getValue(HORIZONTAL_FACING) == neighborState.getValue(HORIZONTAL_FACING))
                    processNeighbor(self, state, other, neighborState, orientation, result);
            } else if(state.getValue(ROTATION).equals(neighborState.getValue(ROTATION))) {
                processNeighbor(self, state, other, neighborState, orientation, result);
            }
        }
        if(state.getValue(ROTATION) == 1) {
            var dir = state.getValue(HORIZONTAL_FACING);
            var other = neighbour(level, devices, pos.relative(dir));
            if(other != null) {
                var neighborState = level.getBlockState(other.pos());
                if(neighborState.getValue(ROTATION) == 1 && neighborState.getValue(HORIZONTAL_FACING) == dir.getOpposite())
                    processBehind(self, state, other, neighborState, dir, result);
            }
        } else {
            var dir = state.getValue(ROTATION) == 0 ? Direction.DOWN : Direction.UP;
            var other = neighbour(level, devices, pos.relative(dir));
            if(other != null)
                processBehind(self, state, other, level.getBlockState(other.pos()), dir, result);
        }
        return result;
    }

    private static @Nullable BoardDevice neighbour(ServerLevel level, DevicesSavedData devices, BlockPos pos) {
        if(!level.isLoaded(pos) || !(level.getBlockState(pos).getBlock() instanceof BoardBlock))
            return null;
        var device = devices.getDevice(pos, BoardDevice.class);
        if(device == null || device.baked() == null)
            return null;
        return device;
    }

    private static @Nullable PlacedComponent getViaAt(BoardDevice board, BlockState boardState, Direction sideIn, int edgePosition, Orientation edgeOut) {
        var orientation = BoardBlock.getOrientation(boardState, sideIn);
        if(orientation == null)
            return null;
        if(((edgeOut.isX() && orientation.isX()) || (edgeOut.isY() && orientation.isY())) == (edgeOut.positive() == orientation.positive()))
            edgePosition = 15 - edgePosition;
        for(var placed : board.baked().components) {
            if(!(placed.component instanceof ViaComponent) || !placed.get(EDGE_CONNECTIONS))
                continue;
            var match = switch(orientation) {
                case UP -> placed.x == edgePosition && placed.y == 0;
                case DOWN -> placed.x == edgePosition && placed.y == 15;
                case LEFT -> placed.x == 0 && placed.y == edgePosition;
                case RIGHT -> placed.x == 15 && placed.y == edgePosition;
            };
            if(match)
                return placed;
        }
        return null;
    }

    private static @Nullable PlacedComponent getHeaderAt(BoardDevice board, int x, int y) {
        for(var placed : board.baked().components) {
            if(!(placed.component instanceof ViaComponent) || !placed.get(VERTICAL_PASSTHROUGH))
                continue;
            if(placed.x == x && placed.y == y)
                return placed;
        }
        return null;
    }

    private static void processViaOrientation(BlockState state, BoardDevice other, BlockState otherState, Orientation expectedOrientation,
                                              PlacedComponent placed, Orientation edge, int position, List<Link> result) {
        if(edge != expectedOrientation)
            return;
        var dir = BoardBlock.getDirection(state, edge);
        var theirs = getViaAt(other, otherState, dir.getOpposite(), position, edge);
        if(theirs != null)
            result.add(new Link(placed, other, theirs));
    }

    private static void processNeighbor(BoardDevice self, BlockState state, BoardDevice other, BlockState otherState, Orientation expected, List<Link> result) {
        for(var placed : self.baked().components) {
            if(!(placed.component instanceof ViaComponent) || !placed.get(EDGE_CONNECTIONS))
                continue;
            // Corners sit on two edges.
            if(placed.x == 0)
                processViaOrientation(state, other, otherState, expected, placed, Orientation.LEFT, placed.y, result);
            if(placed.y == 0)
                processViaOrientation(state, other, otherState, expected, placed, Orientation.UP, placed.x, result);
            if(placed.x == 15)
                processViaOrientation(state, other, otherState, expected, placed, Orientation.RIGHT, placed.y, result);
            if(placed.y == 15)
                processViaOrientation(state, other, otherState, expected, placed, Orientation.DOWN, placed.x, result);
        }
    }

    private static void header(BoardDevice other, PlacedComponent placed, int x, int y, List<Link> result) {
        var theirs = getHeaderAt(other, x, y);
        if(theirs != null)
            result.add(new Link(placed, other, theirs));
    }

    private static void processBehind(BoardDevice self, BlockState state, BoardDevice other, BlockState otherState, Direction dir, List<Link> result) {
        for(var placed : self.baked().components) {
            if(!(placed.component instanceof ViaComponent) || !placed.get(VERTICAL_PASSTHROUGH))
                continue;
            var neighborFacing = otherState.getValue(HORIZONTAL_FACING);
            var facing = state.getValue(HORIZONTAL_FACING);
            int fx = Mth.abs(placed.x - 15), fy = Mth.abs(placed.y - 15);
            if(dir == Direction.UP) {
                if(neighborFacing == facing)
                    header(other, placed, fx, placed.y, result);
                if(neighborFacing == facing.getOpposite())
                    header(other, placed, placed.x, fy, result);
                if(neighborFacing == facing.getClockWise())
                    header(other, placed, placed.x, placed.y, result);
                if(neighborFacing == facing.getCounterClockWise())
                    header(other, placed, fx, fy, result);
            } else if(dir == Direction.DOWN) {
                if(neighborFacing == facing)
                    header(other, placed, fx, placed.y, result);
                if(neighborFacing == facing.getOpposite())
                    header(other, placed, placed.x, fy, result);
                if(neighborFacing == facing.getClockWise())
                    header(other, placed, fx, fy, result);
                if(neighborFacing == facing.getCounterClockWise())
                    header(other, placed, placed.x, placed.y, result);
            } else if(state.getValue(ROTATION) == 1 && otherState.getValue(ROTATION) == 1) {
                if(neighborFacing == facing.getOpposite())
                    header(other, placed, fx, placed.y, result);
            }
        }
    }
}
