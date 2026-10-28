package io.github.sjzeero.voltworks.wallwire;

import com.george_vi.electroenergetics.simulation.WireType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.SupportType;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

public final class WallRouter {
    private static final int STEP = 64;
    public static final int MAX_EXPANSIONS = 30_000;
    public static final int PREVIEW_EXPANSIONS = 6_000;
    public static final int MAX_SPAN = 8 * 16;
    private static final int SPAN_COST = STEP;
    private static final int SPAN_EXTRA = 1;

    private WallRouter() {
    }

    public static boolean canLay(WireType type) {
        return type.shouldScaleLast();
    }

    public static boolean supports(BlockGetter level, WallCell cell) {
        return supports(new WallShapes(level), cell);
    }

    static boolean supports(WallShapes shapes, WallCell cell) {
        int[] v = cell.worldPixel();
        Direction f = cell.face();
        return shapes.solid(v[0], v[1], v[2]) && !shapes.solid(v[0] + f.getStepX(), v[1] + f.getStepY(), v[2] + f.getStepZ());
    }

    static boolean supportsFullFace(BlockGetter level, WallCell cell) {
        if(!cell.onBlockFace())
            return false;
        BlockPos pos = cell.pos(), front = cell.front();
        return level.getBlockState(pos).isFaceSturdy(level, pos, cell.face(), SupportType.FULL)
                && !level.getBlockState(front).isFaceSturdy(level, front, cell.face().getOpposite(), SupportType.FULL);
    }

    static void neighbours(WallShapes shapes, WallCell cell, NeighbourSink out) {
        Direction face = cell.face();
        int[] v = cell.worldPixel();
        int nx = face.getStepX(), ny = face.getStepY(), nz = face.getStepZ();
        for(Direction d : Direction.values()) {
            if(d.getAxis() == face.getAxis())
                continue;
            int tx = d.getStepX(), ty = d.getStepY(), tz = d.getStepZ();
            if(shapes.solid(v[0] + nx + tx, v[1] + ny + ty, v[2] + nz + tz))
                out.accept(WallCell.ofPixel(v[0] + nx + tx, v[1] + ny + ty, v[2] + nz + tz, d.getOpposite()), d, 1);
            else if(shapes.solid(v[0] + tx, v[1] + ty, v[2] + tz))
                out.accept(WallCell.ofPixel(v[0] + tx, v[1] + ty, v[2] + tz, face), d, 1);
            else {
                out.accept(WallCell.ofPixel(v[0], v[1], v[2], d), d, 1);
                for(int i = 2; i <= MAX_SPAN; ) {
                    int x = v[0] + tx * i, y = v[1] + ty * i, z = v[2] + tz * i;
                    int run = Math.min(shapes.emptyRun(x, y, z, d), shapes.emptyRun(x + nx, y + ny, z + nz, d));
                    if(run > 0) {
                        i += run;
                        continue;
                    }
                    if(shapes.solid(x + nx, y + ny, z + nz)) {
                        out.accept(WallCell.ofPixel(x + nx, y + ny, z + nz, d.getOpposite()), d, i);
                        break;
                    }
                    if(shapes.solid(x, y, z)) {
                        out.accept(WallCell.ofPixel(x, y, z, face), d, i);
                        break;
                    }
                    i++;
                }
            }
        }
        for(int i = 2; i <= MAX_SPAN; ) {
            int x = v[0] + nx * i, y = v[1] + ny * i, z = v[2] + nz * i;
            int run = shapes.emptyRun(x, y, z, face);
            for(Direction d : Direction.values())
                if(run > 0 && d.getAxis() != face.getAxis())
                    run = Math.min(run, shapes.emptyRun(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ(), face));
            if(run > 0) {
                i += run;
                continue;
            }
            if(shapes.solid(x, y, z)) {
                if(i >= 3)
                    out.accept(WallCell.ofPixel(x, y, z, face.getOpposite()), face, i - 1);
                break;
            }
            for(Direction d : Direction.values()) {
                if(d.getAxis() == face.getAxis())
                    continue;
                int dx = d.getStepX(), dy = d.getStepY(), dz = d.getStepZ();
                if(shapes.solid(x + dx, y + dy, z + dz) && !shapes.solid(x - nx + dx, y - ny + dy, z - nz + dz))
                    out.accept(WallCell.ofPixel(x + dx, y + dy, z + dz, d.getOpposite()), face, i);
            }
            i++;
        }
    }

    interface NeighbourSink {
        void accept(WallCell cell, Direction move, int pixels);
    }

    public static boolean isSpan(WallCell a, WallCell b) {
        return a.manhattan(b) > 1;
    }

    public static int pixels(List<WallCell> cells) {
        int n = 0;
        for(int i = 1; i < cells.size(); i++)
            n += cells.get(i - 1).manhattan(cells.get(i));
        return n;
    }

    interface PixelSink {
        boolean accept(int x, int y, int z);
    }

    static boolean forEachSpanPixel(WallCell a, WallCell b, PixelSink out) {
        int[] p = a.worldPixel(), q = b.worldPixel();
        Direction fa = a.face(), fb = b.face();
        int[] from = {p[0] + fa.getStepX(), p[1] + fa.getStepY(), p[2] + fa.getStepZ()};
        int[] to = {q[0] + fb.getStepX(), q[1] + fb.getStepY(), q[2] + fb.getStepZ()};
        int n = Math.abs(to[0] - from[0]) + Math.abs(to[1] - from[1]) + Math.abs(to[2] - from[2]);
        int sx = Integer.signum(to[0] - from[0]), sy = Integer.signum(to[1] - from[1]), sz = Integer.signum(to[2] - from[2]);
        for(int j = 0; j <= n; j++)
            if(!out.accept(from[0] + sx * j, from[1] + sy * j, from[2] + sz * j))
                return false;
        return true;
    }

    public static boolean spanClear(BlockGetter level, WallCell a, WallCell b) {
        WallShapes shapes = new WallShapes(level);
        return forEachSpanPixel(a, b, (x, y, z) -> !shapes.solid(x, y, z));
    }

    private record State(WallCell cell, int dir) {
    }

    private record Open(State state, int g, int f, int length) {
    }

    private record Move(WallCell cell, int dir, int pixels) {
    }

    public static @Nullable List<WallCell> findPath(BlockGetter level, WallCell from, WallCell to, int maxSteps) {
        return findPath(level, from, to, maxSteps, MAX_EXPANSIONS);
    }

    public static @Nullable List<WallCell> findPath(BlockGetter level, WallCell from, WallCell to, int maxSteps, int maxExpansions) {
        if(from.equals(to))
            return new ArrayList<>(List.of(from));
        if(from.manhattan(to) > maxSteps)
            return null;
        PriorityQueue<Open> open = new PriorityQueue<>(Comparator.comparingInt(Open::f).thenComparingInt(o -> -o.g()));
        Map<State, Integer> best = new HashMap<>();
        Map<State, State> parent = new HashMap<>();
        State start = new State(from, -1);
        best.put(start, 0);
        open.add(new Open(start, 0, from.manhattan(to) * STEP, 0));
        int expansions = 0;
        WallShapes shapes = new WallShapes(level);
        Map<WallCell, List<Move>> moves = new HashMap<>();
        while(!open.isEmpty()) {
            Open cur = open.poll();
            Integer known = best.get(cur.state());
            if(known != null && known < cur.g())
                continue;
            if(cur.state().cell().equals(to))
                return unwind(parent, cur.state());
            if(++expansions > maxExpansions)
                return null;
            if(cur.length() >= maxSteps)
                continue;
            List<Move> next = moves.computeIfAbsent(cur.state().cell(), c -> {
                List<Move> list = new ArrayList<>();
                neighbours(shapes, c, (cell, move, pixels) -> list.add(new Move(cell, move.ordinal(), pixels)));
                return list;
            });
            for(Move m : next) {
                WallCell cell = m.cell();
                int pixels = m.pixels(), dir = m.dir();
                if(cur.length() + pixels > maxSteps)
                    continue;
                int cost = pixels == 1 ? STEP : SPAN_EXTRA + pixels * SPAN_COST;
                int g = cur.g() + cost + (cur.state().dir() >= 0 && cur.state().dir() != dir ? 1 : 0);
                State s = new State(cell, dir);
                Integer old = best.get(s);
                if(old != null && old <= g)
                    continue;
                best.put(s, g);
                parent.put(s, cur.state());
                open.add(new Open(s, g, g + cell.manhattan(to) * STEP, cur.length() + pixels));
            }
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

    public static @Nullable List<WallCell> route(BlockGetter level, List<WallCell> waypoints, int maxSteps) {
        if(waypoints.isEmpty())
            return null;
        List<WallCell> out = new ArrayList<>();
        out.add(waypoints.getFirst());
        for(int i = 1; i < waypoints.size(); i++) {
            int left = maxSteps - pixels(out);
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
        for(int i = 0; i < cells.size(); i++) {
            WallCell c = cells.get(i);
            if(seen.add(c.pos()))
                out.accept(c.pos());
            if(seen.add(c.front()))
                out.accept(c.front());
            if(i > 0 && isSpan(cells.get(i - 1), c))
                forEachSpanPixel(cells.get(i - 1), c, (x, y, z) -> {
                    BlockPos p = new BlockPos(x >> 4, y >> 4, z >> 4);
                    if(seen.add(p))
                        out.accept(p);
                    return true;
                });
        }
    }
}
