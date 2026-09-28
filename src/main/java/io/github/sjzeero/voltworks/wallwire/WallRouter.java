package io.github.sjzeero.voltworks.wallwire;

import com.george_vi.electroenergetics.simulation.WireType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

/** Finds axis aligned pixel paths along block faces, wrapping over edges and into corners. */
public final class WallRouter {
    private static final int STEP = 64;
    private static final int MAX_EXPANSIONS = 120_000;

    private WallRouter() {
    }

    public static boolean canLay(WireType type) {
        return type.shouldScaleLast();
    }

    /** A face can carry a wire when it is a full face and nothing solid is pressed against it. */
    public static boolean supports(BlockGetter level, BlockPos pos, Direction face) {
        BlockState state = level.getBlockState(pos);
        if(!state.isFaceSturdy(level, pos, face, SupportType.FULL))
            return false;
        BlockPos front = pos.relative(face);
        return !level.getBlockState(front).isFaceSturdy(level, front, face.getOpposite(), SupportType.FULL);
    }

    public static boolean supports(BlockGetter level, WallCell cell) {
        return supports(level, cell.pos(), cell.face());
    }

    /** Calls {@code out} with every cell reachable in one pixel step and the direction of that step on the source face. */
    static void neighbours(BlockGetter level, WallCell cell, NeighbourSink out) {
        Direction face = cell.face();
        int[] p = cell.pixels();
        for(Direction d : Direction.values()) {
            if(d.getAxis() == face.getAxis())
                continue;
            int a = d.getAxis().ordinal();
            int step = d.getAxisDirection().getStep();
            int[] np = p.clone();
            np[a] += step;
            if(np[a] >= 0 && np[a] <= 15) {
                out.accept(WallCell.of(cell.pos(), face, np), d);
                continue;
            }
            int edge = step > 0 ? 0 : 15;
            // inside corner: climb onto the face of the block standing on the edge
            BlockPos corner = cell.pos().relative(face).relative(d);
            if(supports(level, corner, d.getOpposite())) {
                int[] q = p.clone();
                q[a] = edge;
                q[face.getAxis().ordinal()] = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 0 : 15;
                out.accept(WallCell.of(corner, d.getOpposite(), q), d);
            }
            // flat: continue on the same face of the next block
            BlockPos next = cell.pos().relative(d);
            if(supports(level, next, face)) {
                int[] q = p.clone();
                q[a] = edge;
                out.accept(WallCell.of(next, face, q), d);
            }
            // outside corner: wrap around the edge of this block
            if(supports(level, cell.pos(), d))
                out.accept(WallCell.of(cell.pos(), d, p), d);
        }
    }

    interface NeighbourSink {
        void accept(WallCell cell, Direction move);
    }

    private record State(WallCell cell, int dir) {
    }

    private record Open(State state, int g, int f) {
    }

    /**
     * Shortest path from {@code from} to {@code to} (both included), preferring fewer turns among paths of equal length.
     * @return null if there is no path within {@code maxSteps} pixel steps
     */
    public static @Nullable List<WallCell> findPath(BlockGetter level, WallCell from, WallCell to, int maxSteps) {
        if(from.equals(to))
            return new ArrayList<>(List.of(from));
        if(from.manhattan(to) > maxSteps)
            return null;
        PriorityQueue<Open> open = new PriorityQueue<>(Comparator.comparingInt(Open::f).thenComparingInt(o -> -o.g()));
        Map<State, Integer> best = new HashMap<>();
        Map<State, State> parent = new HashMap<>();
        State start = new State(from, -1);
        best.put(start, 0);
        open.add(new Open(start, 0, from.manhattan(to) * STEP));
        int expansions = 0;
        while(!open.isEmpty()) {
            Open cur = open.poll();
            Integer known = best.get(cur.state());
            if(known != null && known < cur.g())
                continue;
            if(cur.state().cell().equals(to))
                return unwind(parent, cur.state());
            if(++expansions > MAX_EXPANSIONS)
                return null;
            int steps = cur.g() / STEP;
            if(steps >= maxSteps)
                continue;
            neighbours(level, cur.state().cell(), (cell, move) -> {
                int dir = move.ordinal();
                int g = cur.g() + STEP + (cur.state().dir() >= 0 && cur.state().dir() != dir ? 1 : 0);
                State s = new State(cell, dir);
                Integer old = best.get(s);
                if(old != null && old <= g)
                    return;
                best.put(s, g);
                parent.put(s, cur.state());
                open.add(new Open(s, g, g + cell.manhattan(to) * STEP));
            });
        }
        return null;
    }

    private static List<WallCell> unwind(Map<State, State> parent, State end) {
        ArrayList<WallCell> out = new ArrayList<>();
        for(State s = end; s != null; s = parent.get(s))
            out.add(s.cell());
        Collections.reverse(out);
        return out;
    }

    /** Joins a path through the given waypoints. Returns null if any leg has no path. */
    public static @Nullable List<WallCell> route(BlockGetter level, List<WallCell> waypoints, int maxSteps) {
        if(waypoints.isEmpty())
            return null;
        List<WallCell> out = new ArrayList<>();
        out.add(waypoints.getFirst());
        for(int i = 1; i < waypoints.size(); i++) {
            int left = maxSteps - (out.size() - 1);
            if(left <= 0)
                return null;
            List<WallCell> leg = findPath(level, waypoints.get(i - 1), waypoints.get(i), left);
            if(leg == null)
                return null;
            out.addAll(leg.subList(1, leg.size()));
        }
        return out;
    }

    public static void forEachTouchedPos(List<WallCell> cells, Consumer<BlockPos> out) {
        Set<BlockPos> seen = new HashSet<>();
        for(WallCell c : cells) {
            if(seen.add(c.pos()))
                out.accept(c.pos());
            if(seen.add(c.front()))
                out.accept(c.front());
        }
    }
}
