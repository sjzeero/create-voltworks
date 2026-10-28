package io.github.sjzeero.voltworks.registry;

import io.github.sjzeero.voltworks.Voltworks;
import io.github.sjzeero.voltworks.reel.CableEndEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class VoltworksEntities {
    private VoltworksEntities() {}

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Voltworks.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<CableEndEntity>> CABLE_END = ENTITIES.register("cable_end",
            () -> EntityType.Builder.<CableEndEntity>of(CableEndEntity::new, MobCategory.MISC)
                    .sized(0.3f, 0.2f)
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .setShouldReceiveVelocityUpdates(true)
                    .build("cable_end"));

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
    }
}
