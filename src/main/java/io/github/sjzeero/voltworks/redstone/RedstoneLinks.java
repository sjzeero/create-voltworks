package io.github.sjzeero.voltworks.redstone;

import io.github.sjzeero.voltworks.wallwire.WallCell;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Redstone wires of one dimension. A wire lies along the surfaces like a wall wire; its first cell is the source face and its
 * last cell the output face. It reads the redstone level arriving at the source face and shows it on the output face: the
 * output is added to the signal queries of the neighbour of the output face.
 */
public class RedstoneLinks extends SavedData {
    private static final String NAME = "voltworks_redstone_links";
    private static final Map<ServerLevel, RedstoneLinks> LOADED = new WeakHashMap<>();
    /** True while a source is being read, so a wire never feeds on its own output. */
    private static boolean reading;

    /** Identifies a wire by the faces it joins. */
    public record Key(BlockPos src, Direction srcFace, BlockPos dst, Direction dstFace) {}

    public static final class Link {
        public final BlockPos src, dst;
        public final Direction srcFace, dstFace;
        public final List<WallCell> cells;
        public final double length;
        int power;

        Link(List<WallCell> cells, double length, int power) {
            this.cells = List.copyOf(cells);
            this.length = length;
            WallCell a = cells.getFirst(), b = cells.getLast();
            this.src = a.pos().immutable();
            this.srcFace = a.face();
            this.dst = b.pos().immutable();
            this.dstFace = b.face();
            this.power = power;
        }

        public Key key() {
            return new Key(src, srcFace, dst, dstFace);
        }
    }

    private record Out(BlockPos pos, Direction dir) {}

    private final ServerLevel level;
    private final List<Link> links = new ArrayList<>();
    private Map<Out, Integer> out = new HashMap<>();

    private RedstoneLinks(ServerLevel level) {
        this.level = level;
    }

    public static RedstoneLinks get(ServerLevel level) {
        RedstoneLinks d = level.getDataStorage().computeIfAbsent(new Factory<>(() -> new RedstoneLinks(level),
                (tag, provider) -> load(level, tag)), NAME);
        LOADED.put(level, d);
        return d;
    }

    private static RedstoneLinks load(ServerLevel level, CompoundTag tag) {
        RedstoneLinks d = new RedstoneLinks(level);
        for(Tag t : tag.getList("Links", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            long[] pos = c.getLongArray("Pos");
            int[] local = c.getIntArray("Local");
            List<WallCell> cells = new ArrayList<>();
            for(int i = 0; i < Math.min(pos.length, local.length); i++)
                cells.add(WallCell.unpack(pos[i], local[i]));
            if(cells.size() >= 2)
                d.links.add(new Link(cells, c.getDouble("Length"), c.getInt("p")));
        }
        d.rebuild();
        return d;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider provider) {
        ListTag list = new ListTag();
        for(Link l : links) {
            CompoundTag c = new CompoundTag();
            long[] pos = new long[l.cells.size()];
            int[] local = new int[l.cells.size()];
            for(int i = 0; i < pos.length; i++) {
                pos[i] = l.cells.get(i).pos().asLong();
                local[i] = l.cells.get(i).packLocal();
            }
            c.putLongArray("Pos", pos);
            c.putIntArray("Local", local);
            c.putDouble("Length", l.length);
            c.putInt("p", l.power);
            list.add(c);
        }
        tag.put("Links", list);
        return tag;
    }

    public List<Link> all() {
        return links;
    }

    private void rebuild() {
        Map<Out, Integer> m = new HashMap<>();
        for(Link l : links)
            if(l.power > 0)
                m.merge(new Out(l.dst.relative(l.dstFace), l.dstFace), l.power, Math::max);
        out = m;
    }

    private void notifyOutput(Link l) {
        BlockPos n = l.dst.relative(l.dstFace);
        if(level.isLoaded(n))
            level.updateNeighborsAt(n, level.getBlockState(n).getBlock());
    }

    /** Signal the output faces add to a query of (pos, dir); called from the block state mixin. */
    public static int outputSignal(BlockGetter getter, BlockPos pos, Direction dir) {
        if(reading || LOADED.isEmpty() || !(getter instanceof ServerLevel sl))
            return 0;
        RedstoneLinks d = LOADED.get(sl);
        if(d == null || d.out.isEmpty())
            return 0;
        return d.out.getOrDefault(new Out(pos, dir), 0);
    }

    public boolean isSource(BlockPos pos, Direction face) {
        for(Link l : links)
            if(l.src.equals(pos) && l.srcFace == face)
                return true;
        return false;
    }

    public boolean isOutput(BlockPos pos, Direction face) {
        for(Link l : links)
            if(l.dst.equals(pos) && l.dstFace == face)
                return true;
        return false;
    }

    /** Adds a wire along {@code cells}; returns the denial key (suffix of voltworks.redstone_cable.*) or null when it was made. */
    public @Nullable String add(List<WallCell> cells, double length) {
        if(cells.size() < 2)
            return "need_point";
        WallCell a = cells.getFirst(), b = cells.getLast();
        if(a.pos().equals(b.pos()))
            return "same_block";
        if(isOutput(a.pos(), a.face()) || isSource(b.pos(), b.face()))
            return "face_busy";
        for(Link l : links)
            if(l.src.equals(a.pos()) && l.srcFace == a.face() && l.dst.equals(b.pos()) && l.dstFace == b.face())
                return "exists";
        Link l = new Link(cells, length, 0);
        links.add(l);
        setDirty();
        RedstoneSync.added(level, l);
        return null;
    }

    /** Removes every wire that has this face as source or output; returns how many went. */
    public int removeAt(BlockPos pos, Direction face) {
        List<Link> gone = new ArrayList<>();
        for(Link l : links)
            if(l.src.equals(pos) && l.srcFace == face || l.dst.equals(pos) && l.dstFace == face)
                gone.add(l);
        removeAll(gone);
        return gone.size();
    }

    private void removeAll(List<Link> gone) {
        if(gone.isEmpty())
            return;
        links.removeAll(gone);
        rebuild();
        setDirty();
        for(Link l : gone) {
            RedstoneSync.removed(level, l);
            if(l.power > 0)
                notifyOutput(l);
        }
    }

    public void tick() {
        if(links.isEmpty() || level.getGameTime() % 2 != 0)
            return;
        List<Link> gone = new ArrayList<>();
        List<Link> changed = new ArrayList<>();
        for(Link l : links) {
            // a wire whose end block was broken goes with it
            if(level.isLoaded(l.src) && level.getBlockState(l.src).isAir() || level.isLoaded(l.dst) && level.getBlockState(l.dst).isAir()) {
                gone.add(l);
                continue;
            }
            BlockPos n = l.src.relative(l.srcFace);
            if(!level.isLoaded(l.src) || !level.isLoaded(n))
                continue;
            int in;
            reading = true;
            try {
                BlockState srcState = level.getBlockState(l.src);
                Direction toSrc = l.srcFace.getOpposite();
                int own = Math.max(srcState.getSignal(level, l.src, toSrc), srcState.getDirectSignal(level, l.src, toSrc));
                in = Math.max(0, Math.min(15, Math.max(level.getSignal(n, l.srcFace), own)));
            } finally {
                reading = false;
            }
            if(in != l.power) {
                l.power = in;
                changed.add(l);
            }
        }
        removeAll(gone);
        if(changed.isEmpty())
            return;
        changed.removeAll(gone);
        rebuild();
        setDirty();
        for(Link l : changed)
            notifyOutput(l);
    }
}
