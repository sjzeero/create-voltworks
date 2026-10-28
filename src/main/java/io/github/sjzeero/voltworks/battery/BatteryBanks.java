package io.github.sjzeero.voltworks.battery;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BatteryBanks {
    private BatteryBanks() {}

    public static final int MAX_GROUP = 1024;

    private static @Nullable BatteryBlockEntity battery(Level level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof BatteryBlockEntity be ? be : null;
    }

    public static void ensure(Level level, BlockPos pos) {
        BatteryBlockEntity be = battery(level, pos);
        if (be == null)
            return;
        if (be.validBank()) {
            BatteryBlockEntity c = be.controllerBE();
            if (c != null) {
                BlockPos o = c.getBlockPos(), p = be.getBlockPos();
                BlockState s = level.getBlockState(pos);
                if (s.getBlock() instanceof BatteryBlock && s != BatteryBlock.withBankLook(s, p.equals(terminalPos(c)),
                        p.getX() - o.getX(), p.getY() - o.getY(), p.getZ() - o.getZ(), c.sizeX(), c.sizeY(), c.sizeZ()))
                    refreshDisplay(level, c);
            }
            return;
        }
        reform(level, List.of(pos), null);
    }

    public static void removed(Level level, BlockPos pos, BatteryBlockEntity be) {
        double share = be.cellEnergy();
        List<BlockPos> seeds = new ArrayList<>();
        for (Direction d : Direction.values())
            if (battery(level, pos.relative(d)) != null)
                seeds.add(pos.relative(d));
        if (!seeds.isEmpty()) {
            reform(level, seeds, pos);
            if (level instanceof ServerLevel sl && BatteryWires.hasWires(sl, pos)) {
                BatteryBlockEntity next = battery(level, seeds.get(0));
                BatteryBlockEntity c = next == null ? null : next.controllerBE();
                if (c != null)
                    BatteryWires.move(sl, pos, terminalPos(c));
            }
        }
        be.makeSingle(share);
    }

    public static BlockPos terminalPos(BatteryBlockEntity controller) {
        return controller.getBlockPos().above(controller.sizeY() - 1);
    }

    static void reform(Level level, Collection<BlockPos> seeds, @Nullable BlockPos excluded) {
        Set<BlockPos> group = new LinkedHashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos s : seeds)
            if (group.add(s.immutable()))
                queue.add(s.immutable());
        while (!queue.isEmpty() && group.size() < MAX_GROUP) {
            BlockPos p = queue.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (!n.equals(excluded) && !group.contains(n) && battery(level, n) != null && group.size() < MAX_GROUP) {
                    group.add(n);
                    queue.add(n);
                }
            }
        }
        Map<BlockPos, Double> shares = new HashMap<>();
        for (BlockPos p : group) {
            BatteryBlockEntity be = battery(level, p);
            if (be != null)
                shares.put(p, be.cellEnergy());
        }
        Set<BlockPos> orphans = new LinkedHashSet<>();
        for (BlockPos p : group) {
            BatteryBlockEntity be = battery(level, p);
            BatteryBlockEntity c = be == null || !be.validBank() ? null : be.controllerBE();
            if (c == null)
                continue;
            BlockPos o = c.getBlockPos();
            for (int x = 0; x < c.sizeX(); x++)
                for (int y = 0; y < c.sizeY(); y++)
                    for (int z = 0; z < c.sizeZ(); z++) {
                        BlockPos q = o.offset(x, y, z);
                        if (!group.contains(q) && !q.equals(excluded))
                            orphans.add(q);
                    }
        }
        Map<BlockPos, Double> orphanShares = new HashMap<>();
        for (BlockPos q : orphans) {
            BatteryBlockEntity be = battery(level, q);
            if (be != null)
                orphanShares.put(q, be.cellEnergy());
        }
        List<BankPartition.Bank> banks = BankPartition.partition(shares.keySet(), shares, BatterySpec.CAPACITY);
        for (BankPartition.Bank b : banks)
            apply(level, b);
        orphanShares.forEach((q, e) -> {
            BatteryBlockEntity be = battery(level, q);
            if (be != null) {
                be.makeSingle(e);
                refreshDisplay(level, be);
            }
        });
    }

    private static void apply(Level level, BankPartition.Bank b) {
        for (BlockPos p : b.cells()) {
            BatteryBlockEntity be = battery(level, p);
            if (be != null)
                be.setBank(b.origin(), b.sx(), b.sy(), b.sz(), b.energy());
        }
        BatteryBlockEntity c = battery(level, b.origin());
        if (c != null)
            refreshDisplay(level, c);
    }

    public static boolean refreshDisplay(Level level, BatteryBlockEntity controller) {
        boolean changed = false;
        BlockPos o = controller.getBlockPos();
        BlockPos terminal = terminalPos(controller);
        for (int y = 0; y < controller.sizeY(); y++) {
            for (int x = 0; x < controller.sizeX(); x++)
                for (int z = 0; z < controller.sizeZ(); z++) {
                    BlockPos p = o.offset(x, y, z);
                    boolean top = p.equals(terminal);
                    if (!level.isLoaded(p))
                        continue;
                    BlockState s = level.getBlockState(p);
                    if (!(s.getBlock() instanceof BatteryBlock))
                        continue;
                    BlockState n = BatteryBlock.withBankLook(s, top, x, y, z, controller.sizeX(), controller.sizeY(), controller.sizeZ());
                    if (n != s) {
                        level.setBlock(p, n, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                        changed = true;
                    }
                    if (!top && level instanceof ServerLevel sl && BatteryWires.hasWires(sl, p))
                        BatteryWires.move(sl, p, terminal);
                }
        }
        return changed;
    }

    public static void updateComparators(Level level, BatteryBlockEntity controller) {
        BlockPos o = controller.getBlockPos();
        for (int x = 0; x < controller.sizeX(); x++)
            for (int y = 0; y < controller.sizeY(); y++)
                for (int z = 0; z < controller.sizeZ(); z++) {
                    BlockPos p = o.offset(x, y, z);
                    if (level.isLoaded(p))
                        level.updateNeighbourForOutputSignal(p, controller.getBlockState().getBlock());
                }
    }
}
