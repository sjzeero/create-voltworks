package io.github.sjzeero.voltworks.registry;

import io.github.sjzeero.voltworks.Voltworks;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Mod sound events (files and subtitles are declared in assets/voltworks/sounds.json). */
public final class VoltworksSounds {
    private VoltworksSounds() {}

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Voltworks.MOD_ID);

    /** Continuous DC buzzer tone (~2.7 kHz with buzzy harmonics), a 0.5 s loopable chunk. */
    public static final DeferredHolder<SoundEvent, SoundEvent> BUZZER = SOUNDS.register("buzzer",
            () -> SoundEvent.createVariableRangeEvent(Voltworks.asResource("buzzer")));

    public static void register(IEventBus bus) {
        SOUNDS.register(bus);
    }
}
