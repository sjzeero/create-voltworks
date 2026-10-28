/*
 * Copyright 2025-2026 patryk3211
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
 * Modified by sjzeero: ported from Create: Power Grid to Voltworks.
 */
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

public final class BankPartition {
    private BankPartition() {}

    public static final int MAX_WIDTH = 3;
    public static final int MAX_HEIGHT = 5;

    public static boolean joined(int x, int y, int z, int sx, int sy, int sz, Direction d) {
        int nx = x + d.getStepX(), ny = y + d.getStepY(), nz = z + d.getStepZ();
        return nx >= 0 && nx < sx && ny >= 0 && ny < sy && nz >= 0 && nz < sz;
    }

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

    public static final Comparator<BlockPos> ORDER = Comparator.comparingInt((BlockPos p) -> p.getY()).thenComparingInt(p -> p.getX()).thenComparingInt(p -> p.getZ());

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
