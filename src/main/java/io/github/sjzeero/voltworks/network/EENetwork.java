package io.github.sjzeero.voltworks.network;

import io.github.sjzeero.voltworks.client.ClientPayloads;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public class EENetwork {
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(SaveSchematicPayload.TYPE, SaveSchematicPayload.CODEC, SaveSchematicPayload::handle);
        registrar.playToServer(ChangeScreenPayload.TYPE, ChangeScreenPayload.CODEC, ChangeScreenPayload::handle);
        registrar.playToServer(UpdateComponentPayload.TYPE, UpdateComponentPayload.CODEC, UpdateComponentPayload::handle);
        registrar.playToClient(BoardSchematicPayload.TYPE, BoardSchematicPayload.CODEC, (p, c) -> ClientPayloads.schematic(p));
        registrar.playToClient(BoardStatePayload.TYPE, BoardStatePayload.CODEC, (p, c) -> ClientPayloads.state(p));
    }
}
