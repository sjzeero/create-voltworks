package io.github.sjzeero.voltworks.board;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import io.github.sjzeero.voltworks.util.WiringTools;
import io.github.sjzeero.voltworks.wallwire.ContraptionWireView;
import io.github.sjzeero.voltworks.wallwire.FrozenDevices;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class BoardMovingInteraction extends MovingInteractionBehaviour {
    public static final BoardMovingInteraction INSTANCE = new BoardMovingInteraction();

    @Override
    public boolean handlePlayerInteraction(Player player, InteractionHand hand, BlockPos localPos, AbstractContraptionEntity entity) {
        if(WiringTools.isWiringTool(player.getItemInHand(hand)))
            return false;
        var contraption = entity.getContraption();
        var info = contraption == null ? null : contraption.getBlocks().get(localPos);
        if(info == null || !(info.state().getBlock() instanceof BoardBlock))
            return false;
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(player.blockInteractionRange() + 2));
        Vec3 origin = entity.toLocalVector(eye, 1f).subtract(localPos.getX(), localPos.getY(), localPos.getZ());
        Vec3 target = entity.toLocalVector(end, 1f).subtract(localPos.getX(), localPos.getY(), localPos.getZ());
        if(entity.level().isClientSide) {
            var be = contraption.getOrCreateClientContraptionLazy().getBlockEntity(localPos);
            if(!(be instanceof BoardBlockEntity board))
                return false;
            var hit = BoardBlock.pick(board.interactables(), info.state(), origin, target);
            return hit != null && !hit.clientScreen();
        }
        if(!(entity.level() instanceof ServerLevel level) || !(contraption instanceof ContraptionWireView view))
            return false;
        var world = FrozenDevices.get(level).worldPos(view.voltworks$frozenId(), localPos);
        if(world == null)
            return false;
        var device = DevicesSavedData.load(level).getDevice(world, BoardDevice.class);
        if(device == null)
            return false;
        device.queueUseRay(info.state(), origin, target, player);
        return true;
    }
}
