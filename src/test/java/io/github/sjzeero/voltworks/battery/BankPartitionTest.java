package io.github.sjzeero.voltworks.battery;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BankPartitionTest {
    static final double C = BatterySpec.CAPACITY;

    static List<BlockPos> box(int sx, int sy, int sz) {
        List<BlockPos> out = new ArrayList<>();
        for (int x = 0; x < sx; x++)
            for (int y = 0; y < sy; y++)
                for (int z = 0; z < sz; z++)
                    out.add(new BlockPos(10 + x, 64 + y, -5 + z));
        return out;
    }

    static Map<BlockPos, Double> each(List<BlockPos> cells, double e) {
        Map<BlockPos, Double> m = new HashMap<>();
        cells.forEach(p -> m.put(p, e));
        return m;
    }

    static void checkCover(List<BlockPos> cells, List<BankPartition.Bank> banks) {
        Set<BlockPos> seen = new HashSet<>();
        for (var b : banks) {
            assertTrue(b.sx() <= 3 && b.sz() <= 3 && b.sy() <= 5, b.toString());
            for (BlockPos p : b.cells())
                assertTrue(seen.add(p), "covered twice " + p);
        }
        assertEquals(new HashSet<>(cells), seen);
    }

    @Test
    void singleSideBySideSquareAndColumnFormOneBank() {
        for (int[] s : new int[][]{{1, 1, 1}, {2, 1, 1}, {1, 1, 2}, {2, 1, 2}, {1, 3, 1}, {3, 5, 3}, {2, 2, 3}}) {
            var cells = box(s[0], s[1], s[2]);
            var banks = BankPartition.partition(cells, each(cells, C / 2), C);
            assertEquals(1, banks.size(), Arrays.toString(s));
            var b = banks.get(0);
            assertEquals(new BlockPos(10, 64, -5), b.origin(), "controller is the lowest north-west corner");
            assertEquals(cells.size() * C / 2, b.energy(), 1e-6);
        }
    }

    @Test
    void tooBigGroupsSplitAndKeepEveryJoule() {
        var cells = box(4, 7, 3);
        Map<BlockPos, Double> e = new HashMap<>();
        double total = 0;
        int i = 0;
        for (BlockPos p : cells) {
            double v = C * ((i++ * 37) % 100) / 100.0;
            e.put(p, v);
            total += v;
        }
        var banks = BankPartition.partition(cells, e, C);
        checkCover(cells, banks);
        assertTrue(banks.size() >= 2);
        assertEquals(total, banks.stream().mapToDouble(BankPartition.Bank::energy).sum(), 1e-6);
        assertEquals(15 * 3, banks.get(0).size(), "biggest box first from the bottom corner");
    }

    @Test
    void breakingABlockSplitsTheBankWithoutLosingEnergy() {
        var cells = box(3, 1, 1);
        var banks = BankPartition.partition(cells, each(cells, C * 0.6), C);
        assertEquals(1, banks.size());
        Map<BlockPos, Double> shares = BankPartition.shares(banks);
        BlockPos middle = new BlockPos(11, 64, -5);
        double removed = shares.remove(middle);
        List<BlockPos> rest = new ArrayList<>(shares.keySet());
        var after = BankPartition.partition(rest, shares, C);
        assertEquals(2, after.size(), "the two ends are no longer touching");
        assertEquals(3 * C * 0.6, removed + after.stream().mapToDouble(BankPartition.Bank::energy).sum(), 1e-6);
    }

    @Test
    void lShapeAndStacksPartitionIntoBoxes() {
        List<BlockPos> cells = new ArrayList<>(box(2, 1, 2));
        cells.add(new BlockPos(12, 64, -5));
        cells.add(new BlockPos(10, 65, -5));
        cells.add(new BlockPos(10, 66, -5));
        var banks = BankPartition.partition(cells, each(cells, C), C);
        checkCover(cells, banks);
        for (var b : banks)
            assertEquals(b.size() * C, b.energy(), 1e-6);
    }

    @Test
    void energyIsCappedAtCapacity() {
        var cells = box(1, 2, 1);
        var banks = BankPartition.partition(cells, each(cells, 5 * C), C);
        assertEquals(2 * C, banks.get(0).energy(), 1e-6);
    }

    @Test
    void joinedSidesFaceTheRestOfTheBank() {
        for (Direction d : Direction.values())
            assertFalse(BankPartition.joined(0, 0, 0, 1, 1, 1, d), d.toString());
        assertTrue(BankPartition.joined(1, 0, 0, 3, 2, 2, Direction.EAST));
        assertTrue(BankPartition.joined(1, 0, 0, 3, 2, 2, Direction.WEST));
        assertTrue(BankPartition.joined(1, 0, 0, 3, 2, 2, Direction.UP));
        assertTrue(BankPartition.joined(1, 0, 0, 3, 2, 2, Direction.SOUTH));
        assertFalse(BankPartition.joined(1, 0, 0, 3, 2, 2, Direction.NORTH));
        assertFalse(BankPartition.joined(1, 0, 0, 3, 2, 2, Direction.DOWN));
        assertFalse(BankPartition.joined(2, 1, 1, 3, 2, 2, Direction.EAST));
        assertFalse(BankPartition.joined(2, 1, 1, 3, 2, 2, Direction.UP));
        assertFalse(BankPartition.joined(2, 1, 1, 3, 2, 2, Direction.SOUTH));
        assertTrue(BankPartition.joined(2, 1, 1, 3, 2, 2, Direction.WEST));
        assertTrue(BankPartition.joined(2, 1, 1, 3, 2, 2, Direction.DOWN));
        assertTrue(BankPartition.joined(2, 1, 1, 3, 2, 2, Direction.NORTH));
        for (int x = 0; x < 3; x++)
            for (int y = 0; y < 2; y++)
                for (int z = 0; z < 2; z++)
                    for (Direction d : Direction.values())
                        if (BankPartition.joined(x, y, z, 3, 2, 2, d))
                            assertTrue(BankPartition.joined(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ(), 3, 2, 2, d.getOpposite()));
    }
}
