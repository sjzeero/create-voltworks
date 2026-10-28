package io.github.sjzeero.voltworks.battery;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How touching battery blocks join into banks; free of world access so it can be unit tested.
 * <p>
 * As in Create: Power Grid (patryk3211, Apache-2.0) a bank is a solid box of batteries at most {@link #MAX_HEIGHT}
 * tall; Voltworks lets its footprint be any rectangle up to {@link #MAX_WIDTH} by {@link #MAX_WIDTH} (Power Grid only
 * joins square footprints, so two batteries side by side stayed apart). The controller is the box's lowest
 * north-west corner and holds the energy of the whole bank.
 * <p>
 * Blocks are taken from the bottom up (then west to east, north to south); each still free block becomes the corner of
 * the biggest box of free blocks above and beside it. When banks are re-formed every block brings its share of its old
 * bank's energy, so energy is never made or lost by joining or splitting.
 */
public final class BankPartition {
    private BankPartition() {}

    public static final int MAX_WIDTH = 3;
    public static final int MAX_HEIGHT = 5;

    /**
     * Whether the block at ({@code x}, {@code y}, {@code z}) of a bank of {@code sx * sy * sz} blocks (counted from the
     * controller) has a block of the same bank on side {@code d}: that side of its model drops the frame.
     */
    public static boolean joined(int x, int y, int z, int sx, int sy, int sz, Direction d) {
        int nx = x + d.getStepX(), ny = y + d.getStepY(), nz = z + d.getStepZ();
        return nx >= 0 && nx < sx && ny >= 0 && ny < sy && nz >= 0 && nz < sz;
    }

    /** A bank: the box from {@code origin} spanning {@code sx} by {@code sy} by {@code sz} blocks, holding {@code energy}. */
    public record Bank(BlockPos origin, int sx, int sy, int sz, double energy) {
        public int size() {
            return sx * sy * sz;
        }

        public boolean contains(BlockPos p) {
            return p.getX() >= origin.getX() && p.getX() < origin.getX() + sx
                    && p.getY() >= origin.getY() && p.getY() < origin.getY() + sy
                    && p.getZ() >= origin.getZ() && p.getZ() < origin.getZ() + sz;
        }

        public List<BlockPos> cells() {
            List<BlockPos> out = new ArrayList<>(size());
            for (int y = 0; y < sy; y++)
                for (int x = 0; x < sx; x++)
                    for (int z = 0; z < sz; z++)
                        out.add(origin.offset(x, y, z));
            return out;
        }
    }

    /** Blocks ordered bottom-up, then west-east, then north-south: each block is reached before any box above or east/south of it. */
    public static final Comparator<BlockPos> ORDER = Comparator.comparingInt((BlockPos p) -> p.getY()).thenComparingInt(p -> p.getX()).thenComparingInt(p -> p.getZ());

    /**
     * Splits the blocks into banks.
     * @param energyPerBlock each block's share of its old bank's energy (missing = 0)
     * @param capacityPerBlock energy one block holds; a bank's energy is capped at its size times this
     */
    public static List<Bank> partition(Collection<BlockPos> blocks, Map<BlockPos, Double> energyPerBlock, double capacityPerBlock) {
        Set<BlockPos> free = new HashSet<>();
        for (BlockPos p : blocks)
            free.add(p.immutable());
        List<BlockPos> order = new ArrayList<>(free);
        order.sort(ORDER);
        List<Bank> banks = new ArrayList<>();
        for (BlockPos o : order) {
            if (!free.contains(o))
                continue;
            int bx = 1, bz = 1, by = 1, best = 0;
            for (int sx = 1; sx <= MAX_WIDTH; sx++)
                for (int sz = 1; sz <= MAX_WIDTH; sz++) {
                    if (!layerFree(free, o, sx, sz, 0))
                        continue;
                    int sy = 1;
                    while (sy < MAX_HEIGHT && layerFree(free, o, sx, sz, sy))
                        sy++;
                    int v = sx * sy * sz;
                    // bigger wins; on a tie the more compact footprint, then the taller bank
                    if (v > best || v == best && (Math.max(sx, sz) < Math.max(bx, bz) || Math.max(sx, sz) == Math.max(bx, bz) && sy > by)) {
                        best = v;
                        bx = sx;
                        by = sy;
                        bz = sz;
                    }
                }
            double energy = 0;
            for (int y = 0; y < by; y++)
                for (int x = 0; x < bx; x++)
                    for (int z = 0; z < bz; z++) {
                        BlockPos p = o.offset(x, y, z);
                        free.remove(p);
                        energy += Math.max(0, energyPerBlock.getOrDefault(p, 0.0));
                    }
            energy = Math.min(energy, capacityPerBlock * bx * by * bz);
            banks.add(new Bank(o, bx, by, bz, energy));
        }
        return banks;
    }

    private static boolean layerFree(Set<BlockPos> free, BlockPos o, int sx, int sz, int dy) {
        for (int x = 0; x < sx; x++)
            for (int z = 0; z < sz; z++)
                if (!free.contains(o.offset(x, dy, z)))
                    return false;
        return true;
    }

    /** Every block's share of the energy of the bank it is in. */
    public static Map<BlockPos, Double> shares(Collection<Bank> banks) {
        Map<BlockPos, Double> out = new HashMap<>();
        for (Bank b : banks) {
            double each = b.energy() / b.size();
            for (BlockPos p : b.cells())
                out.put(p, each);
        }
        return out;
    }
}
