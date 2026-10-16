package io.github.sjzeero.voltworks.wallwire.client;

import io.github.sjzeero.voltworks.cord.CordPair;

import com.george_vi.electroenergetics.CEERegistries;
import com.george_vi.electroenergetics.client.WireRenderer;
import com.george_vi.electroenergetics.content.wire.interaction.WireInteractionBehaviour;
import com.george_vi.electroenergetics.content.wire.interaction.WireInteractionHandler;
import com.george_vi.electroenergetics.content.wire_spool.DyeWireInteractionBehaviour;
import com.george_vi.electroenergetics.content.wire_spool.EmptySpoolWireInteractionBehaviour;
import com.george_vi.electroenergetics.content.wire_spool.WireApplyingBehaviour;
import com.george_vi.electroenergetics.content.wire_spool.WireCuttersWireInteractionBehaviour;
import com.george_vi.electroenergetics.content.wire_spool.WireSpoolItem;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.foundation.nodes.NodeConnectionPoint;
import com.george_vi.electroenergetics.simulation.WireType;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import com.mojang.blaze3d.platform.InputConstants;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import io.github.sjzeero.voltworks.Voltworks;
import io.github.sjzeero.voltworks.VoltworksClientConfig;
import io.github.sjzeero.voltworks.VoltworksConfig;
import io.github.sjzeero.voltworks.mixin.WireSpoolItemAccessor;
import io.github.sjzeero.voltworks.wallwire.PlaceWallWirePayload;
import io.github.sjzeero.voltworks.wallwire.WallCell;
import io.github.sjzeero.voltworks.wallwire.WallRoute;
import io.github.sjzeero.voltworks.wallwire.WallRouter;
import io.github.sjzeero.voltworks.wallwire.WallSpace;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import io.github.sjzeero.voltworks.compat.CeeCompat;

/** Wire placement mode toggle and the click by click wall wire placement. */
public final class WallWireClient {
    public enum Mode { AIR, WALL }

    public static Mode mode = Mode.AIR;
    public static KeyMapping toggleKey;
    private static boolean modeLoaded;

    static @Nullable InWorldNode start;
    static final List<WallCell> points = new ArrayList<>();
    static final List<WallCell> committed = new ArrayList<>();
    /** Socket the player is pointing at with a cord in hand; the renderer draws the plug on it. */
    static @Nullable InWorldNode hoverSocket;
    private static @Nullable WallCell hoverCell;
    private static @Nullable List<WallCell> hoverPath;
    private static long lastClick;

    private WallWireClient() {
    }

    public static void registerKeys(RegisterKeyMappingsEvent event) {
        toggleKey = new KeyMapping("key.voltworks.wire_mode", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_V, "key.categories.voltworks");
        event.register(toggleKey);
    }

    public static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, Voltworks.asResource("wire_mode"), WallWireClient::renderOverlay);
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(WallWireClient::tick);
        NeoForge.EVENT_BUS.addListener(WallWireClient::click);
        NeoForge.EVENT_BUS.addListener(WallWireRenderer::render);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            ClientWallRoutes.clear();
            reset();
        });
    }

    static @Nullable WireType heldWireType(LocalPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if(!(stack.getItem() instanceof WireSpoolItem spool))
            return null;
        return ((WireSpoolItemAccessor) spool).voltworks$wireType().get();
    }

    static @Nullable DyeColor offhandDye(LocalPlayer player) {
        return player.getOffhandItem().getItem() instanceof DyeItem dye ? dye.getDyeColor() : null;
    }

    /** The copper cord is only ever laid along surfaces, whatever the toggle says. */
    static boolean wall(LocalPlayer player) {
        return mode == Mode.WALL || CordPair.isCord(heldWireType(player));
    }

    private static boolean holdingSpool(LocalPlayer player) {
        return player.getMainHandItem().getItem() instanceof WireSpoolItem;
    }

    static void reset() {
        start = null;
        points.clear();
        committed.clear();
        hoverSocket = null;
        hoverCell = null;
        hoverPath = null;
    }

    private static void message(LocalPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("voltworks.wall_wire." + key, args), true);
    }

    private static double maxLength(WireType type) {
        return Math.min(VoltworksConfig.wallWireMaxLength(), type.getMaxLength());
    }

    private static @Nullable Vec3 startPos(ClientLevel level) {
        return start == null ? null : WallSpace.nodePos(level, start);
    }

    /** Length placed so far: the lead from the start node plus the routed cells. */
    static double placedLength(ClientLevel level) {
        Vec3 s = startPos(level);
        if(s == null || committed.isEmpty())
            return 0;
        return s.distanceTo(committed.getFirst().point()) + WallRouter.pixels(committed) / 16.0;
    }

    private static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if(player == null || mc.level == null) {
            reset();
            return;
        }
        if(!modeLoaded && VoltworksClientConfig.SPEC.isLoaded()) {
            mode = VoltworksClientConfig.wallMode() ? Mode.WALL : Mode.AIR;
            modeLoaded = true;
        }
        while(toggleKey != null && toggleKey.consumeClick()) {
            if(!holdingSpool(player))
                continue;
            mode = mode == Mode.AIR ? Mode.WALL : Mode.AIR;
            VoltworksClientConfig.setWallMode(mode == Mode.WALL);
            reset();
            player.displayClientMessage(Component.translatable(mode == Mode.WALL ? "voltworks.wire_mode.wall" : "voltworks.wire_mode.air")
                    .withStyle(ChatFormatting.GOLD), true);
            player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.3f, mode == Mode.WALL ? 1.3f : 0.9f);
        }
        if(!holdingSpool(player) || !wall(player)) {
            if(start != null)
                reset();
            return;
        }
        if(start != null)
            preview(mc, player, mc.level);
    }

    private static void preview(Minecraft mc, LocalPlayer player, ClientLevel level) {
        Vec3 s = startPos(level);
        if(s == null) {
            reset();
            return;
        }
        Outliner.getInstance().showAABB("voltworks_wall_wire_start", AABB.ofSize(WallSpace.toWorld(level, s), 0.3, 0.3, 0.3))
                .lineWidth(1 / 32f).colored(0xffffff).disableLineNormals();
        hoverSocket = null;
        WireType type = heldWireType(player);
        if(type == null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK)
            return;
        if(!WallSpace.sameSpace(level, hit.getBlockPos(), start.sourcePos()))
            return;
        double maxLead = VoltworksConfig.wallWireMaxLead();
        InWorldNode node = hoveredNode(level, hit);
        if(node != null && CordPair.isCord(type) && !node.equals(start))
            hoverSocket = node;
        Vec3 from = committed.isEmpty() ? s : committed.getLast().point();
        if(node != null) {
            Vec3 to = WallSpace.nodePos(level, node);
            if(committed.isEmpty())
                return;
            boolean ok = !node.equals(start) && from.distanceTo(to) <= maxLead
                    && placedLength(level) + from.distanceTo(to) <= maxLength(type);
            line("voltworks_wall_wire_lead", from, to, ok);
            return;
        }
        WallCell cell = WallCell.fromHit(hit);
        if(!cell.equals(hoverCell)) {
            hoverCell = cell;
            hoverPath = null;
            if(WallRouter.supports(level, cell)) {
                if(committed.isEmpty())
                    hoverPath = List.of(cell);
                else {
                    int left = (int) Math.floor((maxLength(type) - placedLength(level)) * 16);
                    hoverPath = left > 0 ? WallRouter.findPath(level, committed.getLast(), cell, Math.min(left, 16 * 24), WallRouter.PREVIEW_EXPANSIONS) : null;
                }
            }
        }
        if(hoverPath == null) {
            line("voltworks_wall_wire_lead", from, cell.point(), false);
            return;
        }
        if(committed.isEmpty()) {
            line("voltworks_wall_wire_lead", s, cell.point(), s.distanceTo(cell.point()) <= maxLead);
            return;
        }
        List<WallRoute.Segment> segments = WallRoute.segments(hoverPath);
        for(int i = 0; i < segments.size() && i < 64; i++)
            line("voltworks_wall_wire_hover_" + i, segments.get(i).a(), segments.get(i).b(), true);
    }

    private static void line(String key, Vec3 a, Vec3 b, boolean ok) {
        ClientLevel level = Minecraft.getInstance().level;
        Outliner.getInstance().showLine(key, WallSpace.toWorld(level, a), WallSpace.toWorld(level, b)).lineWidth(1 / 32f).colored(ok ? 0x68c586 : 0xff5d6c).disableLineNormals();
    }

    private static @Nullable InWorldNode hoveredNode(ClientLevel level, BlockHitResult hit) {
        BlockState state = level.getBlockState(hit.getBlockPos());
        if(!(state.getBlock() instanceof ElectricalDeviceBlock<?>))
            return null;
        InWorldNode node = InWorldNode.getHitNode(hit, level);
        LocalPlayer player = Minecraft.getInstance().player;
        if(node == null || player == null || !CordPair.isCord(heldWireType(player)))
            return node;
        // a cord always plugs into the supply contact of the block; the return strand follows on the server
        InWorldNode[] ends = CordPair.ends(level, node.sourcePos());
        return ends == null ? null : ends[0];
    }

    private static void click(InputEvent.InteractionKeyMappingTriggered event) {
        if(!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND)
            return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if(player == null || level == null || !holdingSpool(player) || !wall(player))
            return;
        event.setCanceled(true);
        event.setSwingHand(false);
        long now = level.getGameTime();
        if(now - lastClick < 5 && now >= lastClick)
            return;
        lastClick = now;

        if(player.isShiftKeyDown()) {
            undo(player, level);
            return;
        }
        WireType type = heldWireType(player);
        if(type == null || !WallRouter.canLay(type)) {
            message(player, "not_this_wire");
            return;
        }
        if(!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK)
            return;

        if(start != null && !WallSpace.sameSpace(level, hit.getBlockPos(), start.sourcePos())) {
            message(player, "invalid");
            return;
        }
        InWorldNode node = hoveredNode(level, hit);
        if(node != null) {
            clickNode(player, level, type, node);
            return;
        }
        if(start == null) {
            message(player, "start_on_node");
            return;
        }
        WallCell cell = WallCell.fromHit(hit);
        if(!WallRouter.supports(level, cell)) {
            message(player, "bad_face");
            return;
        }
        if(points.size() >= VoltworksConfig.wallWireMaxPoints()) {
            message(player, "too_many_points");
            return;
        }
        if(committed.isEmpty()) {
            Vec3 s = startPos(level);
            if(s == null || s.distanceTo(cell.point()) > VoltworksConfig.wallWireMaxLead()) {
                message(player, "lead_too_long");
                return;
            }
            points.add(cell);
            committed.add(cell);
        } else {
            if(cell.equals(committed.getLast()))
                return;
            int left = (int) Math.floor((maxLength(type) - placedLength(level)) * 16);
            List<WallCell> path = left > 0 ? WallRouter.findPath(level, committed.getLast(), cell, left) : null;
            if(path == null) {
                message(player, left > 0 ? "no_path" : "too_long");
                return;
            }
            points.add(cell);
            committed.addAll(path.subList(1, path.size()));
        }
        hoverCell = null;
        player.playSound(SoundEvents.ITEM_FRAME_ADD_ITEM, 0.5f, 1.5f);
        message(player, "length", String.format(Locale.ROOT, "%.2f", placedLength(level)), String.format(Locale.ROOT, "%.0f", maxLength(type)));
    }

    private static void clickNode(LocalPlayer player, ClientLevel level, WireType type, InWorldNode node) {
        if(start == null) {
            start = node;
            points.clear();
            committed.clear();
            hoverCell = null;
            player.playSound(SoundEvents.ITEM_FRAME_ADD_ITEM, 0.5f, 1.0f);
            message(player, "started");
            return;
        }
        if(node.equals(start)) {
            message(player, "invalid");
            return;
        }
        if(committed.isEmpty()) {
            message(player, "need_point");
            return;
        }
        Vec3 end = WallSpace.nodePos(level, node);
        double lead = end.distanceTo(committed.getLast().point());
        if(lead > VoltworksConfig.wallWireMaxLead()) {
            message(player, "lead_too_long");
            return;
        }
        if(placedLength(level) + lead > maxLength(type)) {
            message(player, "too_long");
            return;
        }
        PacketDistributor.sendToServer(new PlaceWallWirePayload(start, node, List.copyOf(points)));
        reset();
    }

    private static void undo(LocalPlayer player, ClientLevel level) {
        if(start == null)
            return;
        if(points.isEmpty()) {
            reset();
            message(player, "cancelled");
            return;
        }
        points.removeLast();
        committed.clear();
        if(!points.isEmpty()) {
            List<WallCell> rebuilt = WallRouter.route(level, points, 16 * 512);
            if(rebuilt == null) {
                points.clear();
            } else
                committed.addAll(rebuilt);
        }
        hoverCell = null;
        message(player, "undo");
    }

    private static void renderOverlay(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if(player == null || mc.options.hideGui || mc.level == null || !holdingSpool(player) || toggleKey == null)
            return;
        Component text = Component.translatable(wall(player) ? "voltworks.wire_mode.wall" : "voltworks.wire_mode.air")
                .append(Component.literal(" [").append(toggleKey.getTranslatedKeyMessage()).append("]").withStyle(ChatFormatting.GRAY));
        int x = Math.min(g.guiWidth() / 2 + 91 + 8, g.guiWidth() - mc.font.width(text) - 2);
        int y = g.guiHeight() - 12;
        g.drawString(mc.font, text, x, y, wall(player) ? 0xffd27a : 0xdddddd, true);
        WireType type = heldWireType(player);
        if(wall(player) && start != null && type != null) {
            Component len = Component.translatable("voltworks.wall_wire.length",
                    String.format(Locale.ROOT, "%.2f", placedLength(mc.level)), String.format(Locale.ROOT, "%.0f", maxLength(type)));
            g.drawString(mc.font, len, x, y - 10, 0xaaaaaa, true);
        }
    }

    /** Lets the empty spool, wire cutters and dyes pick a wall wire by looking at it on its face. */
    public static void pickWallWire() {
        try {
            pick();
        } catch(Throwable t) {
            if(!pickFailureLogged) {
                pickFailureLogged = true;
                Voltworks.LOGGER.error("wall wire picking failed", t);
            }
        }
    }

    private static boolean pickFailureLogged;

    private static void pick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if(player == null || level == null || WireInteractionHandler.targetedPoint != null || WireApplyingBehaviour.targetingDetachedNode != null)
            return;
        ItemStack stack = player.getMainHandItem();
        WireInteractionBehaviour behaviour = CEERegistries.WIRE_INTERACTION_BEHAVIOUR.stream()
                .filter(h -> h.isActiveFor(stack, player)).findFirst().orElse(null);
        boolean dye = behaviour instanceof DyeWireInteractionBehaviour;
        if(!(behaviour instanceof EmptySpoolWireInteractionBehaviour) && !(behaviour instanceof WireCuttersWireInteractionBehaviour) && !dye)
            return;
        double range = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 1;
        Vec3 from = player.getEyePosition();
        Vec3 to = from.add(player.getViewVector(1).scale(range));
        double best = mc.hitResult != null && mc.hitResult.getType() != HitResult.Type.MISS ? WallSpace.distance(level, mc.hitResult.getLocation(), from) + 0.1 : range;
        float partial = mc.getTimer().getGameTimeDeltaPartialTick(true);
        Pose3dc bestSpace = null;
        ClientWallRoutes.Entry bestEntry = null;
        Vec3 bestPos = null;
        for(ClientWallRoutes.Entry entry : ClientWallRoutes.all()) {
            if(dye && (entry.route().color == ((DyeItem) stack.getItem()).getDyeColor()
                    || !(WireRenderer.getConnectionData(entry.route().connection) instanceof WireData data && data.wireType() instanceof WireType.Dyeable)))
                continue;
            Pose3dc space = WallSpace.renderPose(level, entry.route().cells.getFirst().pos(), partial);
            Vec3 localFrom = WallSpace.toLocal(space, from), localTo = WallSpace.toLocal(space, to);
            if(!entry.bounds().contains(localFrom) && entry.bounds().clip(localFrom, localTo).isEmpty())
                continue;
            for(WallRoute.Segment seg : entry.segments()) {
                Optional<Vec3> hit = new AABB(seg.a(), seg.b()).inflate(1.5 / 16).clip(localFrom, localTo);
                if(hit.isEmpty())
                    continue;
                Vec3 world = WallSpace.toWorld(space, hit.get());
                double d = world.distanceTo(from);
                if(d < best) {
                    best = d;
                    bestEntry = entry;
                    bestPos = world;
                    bestSpace = space;
                }
            }
        }
        if(bestEntry == null)
            return;
        InWorldNodeConnection c = bestEntry.route().connection;
        NodeConnectionPoint point = new NodeConnectionPoint(c.node1(), c.node2(), 0.5f);
        if(!behaviour.isActuallyActiveFor(point, level, player, stack))
            return;
        WireInteractionHandler.targetedPoint = point;
        CeeCompat.setTargetedPos(bestPos);
        int color = behaviour.getWireDisplayColor(point, level, player, stack);
        List<WallRoute.Segment> segments = bestEntry.segments();
        for(int i = 0; i < segments.size() && i < 128; i++)
            Outliner.getInstance().showLine("voltworks_wall_wire_pick_" + i, WallSpace.toWorld(bestSpace, segments.get(i).a()), WallSpace.toWorld(bestSpace, segments.get(i).b()))
                    .lineWidth(1 / 16f).colored(color).disableLineNormals();
    }
}
