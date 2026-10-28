package io.github.sjzeero.voltworks.wallwire.client;

import io.github.sjzeero.voltworks.wallwire.SchematicWires;
import io.github.sjzeero.voltworks.wallwire.WallRoute;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.ListTag;

import java.util.ArrayList;
import java.util.List;

public final class SchematicWiresClient {
    private SchematicWiresClient() {
    }

    public static ListTag capture(BlockPos origin, Vec3i size) {
        List<WallRoute> routes = new ArrayList<>();
        for(ClientWallRoutes.Entry entry : ClientWallRoutes.all())
            routes.add(entry.route());
        return SchematicWires.capture(routes, origin, size);
    }
}
