package io.github.sjzeero.voltworks.redstone.client;

import io.github.sjzeero.voltworks.VoltworksConfig;
import io.github.sjzeero.voltworks.redstone.PlaceRedstoneWirePayload;
import io.github.sjzeero.voltworks.redstone.RedstoneCableItem;
import io.github.sjzeero.voltworks.wallwire.WallCell;
import io.github.sjzeero.voltworks.wallwire.WallRoute;
import io.github.sjzeero.voltworks.wallwire.WallRouter;
import io.github.sjzeero.voltworks.wallwire.WallSpace;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RedstoneCableClient {
    public static final List<WallCell> points = new ArrayList<>();
    public static final List<WallCell> committed = new ArrayList<>();
    private static @Nullable WallCell hoverCell;
    private static @Nullable List<WallCell> hoverPath;
    private static long lastClick;

    private RedstoneCableClient() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(RedstoneCableClient::tick);
        NeoForge.EVENT_BUS.addListener(RedstoneCableClient::click);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            ClientRedstoneWires.clear();
            reset();
        });
    }

    private static boolean holding(LocalPlayer player) {
        return player.getMainHandItem().getItem() instanceof RedstoneCableItem;
    }

    public static void reset() {
        points.clear();
        committed.clear();
        hoverCell = null;
        hoverPath = null;
    }

    private static void message(LocalPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("voltworks.redstone_cable." + key, args), true);
    }

    private static double maxLength() {
        return VoltworksConfig.wallWireMaxLength();
    }

    private static double length() {
        return committed.isEmpty() ? 0 : WallRouter.pixels(committed) / 16.0;
    }

    private static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if(player == null || mc.level == null) {
            reset();
            return;
        }
        if(!holding(player)) {
            if(!points.isEmpty())
                reset();
            return;
        }
        if(!points.isEmpty())
            preview(mc, mc.level);
    }

    private static void preview(Minecraft mc, ClientLevel level) {
        WallCell first = points.getFirst();
        Outliner.getInstance().showAABB("voltworks_redstone_wire_start", AABB.ofSize(WallSpace.toWorld(level, first.point()), 0.3, 0.3, 0.3))
                .lineWidth(1 / 32f).colored(0xff3030).disableLineNormals();
        if(!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK)
            return;
        if(!WallSpace.sameSpace(level, hit.getBlockPos(), first.pos()))
            return;
        WallCell cell = WallCell.fromHit(hit);
        if(!cell.equals(hoverCell)) {
            hoverCell = cell;
            hoverPath = null;
            if(WallRouter.supports(level, cell)) {
                int left = (int) Math.floor((maxLength() - length()) * 16);
                hoverPath = left > 0 ? WallRouter.findPath(level, committed.getLast(), cell, Math.min(left, 16 * 24), WallRouter.PREVIEW_EXPANSIONS) : null;
            }
        }
        if(cell.equals(committed.getLast()) && points.size() >= 2) {
            Outliner.getInstance().showAABB("voltworks_redstone_wire_end", AABB.ofSize(WallSpace.toWorld(level, cell.point()), 0.3, 0.3, 0.3))
                    .lineWidth(1 / 32f).colored(0xff3030).disableLineNormals();
            return;
        }
        if(hoverPath == null) {
            line(level, "voltworks_redstone_wire_hover_0", committed.getLast().point(), cell.point(), false);
            return;
        }
        List<WallRoute.Segment> segments = WallRoute.segments(hoverPath);
        for(int i = 0; i < segments.size() && i < 64; i++)
            line(level, "voltworks_redstone_wire_hover_" + i, segments.get(i).a(), segments.get(i).b(), true);
    }

    private static void line(ClientLevel level, String key, Vec3 a, Vec3 b, boolean ok) {
        Outliner.getInstance().showLine(key, WallSpace.toWorld(level, a), WallSpace.toWorld(level, b))
                .lineWidth(1 / 32f).colored(ok ? 0xff3030 : 0x8c8c8c).disableLineNormals();
    }

    private static void click(InputEvent.InteractionKeyMappingTriggered event) {
        if(!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND)
            return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if(player == null || level == null || !holding(player))
            return;
        boolean shift = player.isShiftKeyDown();
        if(shift && points.isEmpty())
            return;
        event.setCanceled(true);
        event.setSwingHand(false);
        long now = level.getGameTime();
        if(now - lastClick < 5 && now >= lastClick)
            return;
        lastClick = now;

        if(shift) {
            undo(player, level);
            return;
        }
        if(!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK)
            return;
        WallCell cell = WallCell.fromHit(hit);
        if(!WallRouter.supports(level, cell)) {
            message(player, "bad_face");
            return;
        }
        if(points.isEmpty()) {
            if(!cell.onBlockFace()) {
                message(player, "bad_face");
                return;
            }
            points.add(cell);
            committed.add(cell);
            hoverCell = null;
            player.playSound(SoundEvents.ITEM_FRAME_ADD_ITEM, 0.5f, 1.0f);
            message(player, "start");
            return;
        }
        if(!WallSpace.sameSpace(level, cell.pos(), points.getFirst().pos())) {
            message(player, "invalid");
            return;
        }
        if(cell.equals(committed.getLast())) {
            finish(player);
            return;
        }
        if(points.size() >= VoltworksConfig.wallWireMaxPoints()) {
            message(player, "too_many_points");
            return;
        }
        int left = (int) Math.floor((maxLength() - length()) * 16);
        List<WallCell> path = left > 0 ? WallRouter.findPath(level, committed.getLast(), cell, left) : null;
        if(path == null) {
            message(player, left > 0 ? "no_path" : "too_long");
            return;
        }
        points.add(cell);
        committed.addAll(path.subList(1, path.size()));
        hoverCell = null;
        player.playSound(SoundEvents.ITEM_FRAME_ADD_ITEM, 0.5f, 1.5f);
        message(player, "length", String.format(Locale.ROOT, "%.2f", length()), String.format(Locale.ROOT, "%.0f", maxLength()));
    }

    private static void finish(LocalPlayer player) {
        if(points.size() < 2) {
            message(player, "need_point");
            return;
        }
        PacketDistributor.sendToServer(new PlaceRedstoneWirePayload(List.copyOf(points)));
        reset();
    }

    private static void undo(LocalPlayer player, ClientLevel level) {
        if(points.size() <= 1) {
            reset();
            message(player, "cancelled");
            return;
        }
        points.removeLast();
        committed.clear();
        if(points.size() == 1)
            committed.add(points.getFirst());
        else {
            List<WallCell> rebuilt = WallRouter.route(level, points, 16 * 512);
            if(rebuilt == null) {
                reset();
                return;
            }
            committed.addAll(rebuilt);
        }
        hoverCell = null;
        message(player, "undo");
    }
}
