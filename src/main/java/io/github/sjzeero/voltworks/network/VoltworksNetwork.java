package io.github.sjzeero.voltworks.network;

import io.github.sjzeero.voltworks.client.ClientPayloads;
import io.github.sjzeero.voltworks.redstone.PlaceRedstoneWirePayload;
import io.github.sjzeero.voltworks.redstone.RedstoneWiresPayload;
import io.github.sjzeero.voltworks.redstone.client.ClientRedstoneWires;
import io.github.sjzeero.voltworks.wallwire.PlaceWallWirePayload;
import io.github.sjzeero.voltworks.wallwire.WallRoutesPayload;
import io.github.sjzeero.voltworks.wallwire.client.ClientWallRoutes;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public class VoltworksNetwork {
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(SaveSchematicPayload.TYPE, SaveSchematicPayload.CODEC, SaveSchematicPayload::handle);
        registrar.playToServer(ChangeScreenPayload.TYPE, ChangeScreenPayload.CODEC, ChangeScreenPayload::handle);
        registrar.playToServer(UpdateComponentPayload.TYPE, UpdateComponentPayload.CODEC, UpdateComponentPayload::handle);
        registrar.playToClient(BoardSchematicPayload.TYPE, BoardSchematicPayload.CODEC, (p, c) -> ClientPayloads.schematic(p));
        registrar.playToServer(PlaceWallWirePayload.TYPE, PlaceWallWirePayload.CODEC, PlaceWallWirePayload::handle);
        registrar.playToClient(WallRoutesPayload.TYPE, WallRoutesPayload.CODEC, (p, c) -> ClientWallRoutes.apply(p));
        registrar.playToServer(PlaceRedstoneWirePayload.TYPE, PlaceRedstoneWirePayload.CODEC, PlaceRedstoneWirePayload::handle);
        registrar.playToClient(RedstoneWiresPayload.TYPE, RedstoneWiresPayload.CODEC, (p, c) -> ClientRedstoneWires.apply(p));
        registrar.playToClient(ContraptionBoardPayload.TYPE, ContraptionBoardPayload.CODEC, (p, c) -> ClientPayloads.contraptionBoard(p));
        registrar.playToClient(BoardStatePayload.TYPE, BoardStatePayload.CODEC, (p, c) -> ClientPayloads.state(p));
    }
}
