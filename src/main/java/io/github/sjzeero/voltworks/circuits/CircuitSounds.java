/*
 * Copyright 2025-2026 patryk3211
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Modified by sjzeero: ported from Create: Power Grid to Voltworks.
 */
package io.github.sjzeero.voltworks.circuits;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import io.github.sjzeero.voltworks.Voltworks;

public enum CircuitSounds {
    RELAY_CLICK(SoundEvents.LEVER_CLICK, 1.0f, 1.0f),
    COMPONENT_EXPLODE(SoundEvents.FIREWORK_ROCKET_BLAST, 0.25f, 2.0f),
    FUSE_POPS(SoundEvents.FIREWORK_ROCKET_BLAST, 0.5f, 1.6f),
    THYRATRON_FIRE(SoundEvents.FIREWORK_ROCKET_BLAST, 0.35f, 1.85f),
    MICROSWITCH_ON(SoundEvents.WOODEN_BUTTON_CLICK_ON, 1.0f, 2.0f),
    MICROSWITCH_OFF(SoundEvents.WOODEN_BUTTON_CLICK_OFF, 1.0f, 2.0f),
    MICROBUTTON_ON(SoundEvents.WOODEN_BUTTON_CLICK_ON, 0.75f, 2.0f),
    MICROBUTTON_OFF(SoundEvents.WOODEN_BUTTON_CLICK_OFF, 0.75f, 2.0f),
    FUSE_INSTALL(SoundEvents.ITEM_FRAME_ADD_ITEM, 0.8f, 0.7f);

    private final SoundEvent event;
    private final float volume;
    private final float pitch;

    CircuitSounds(Holder<SoundEvent> event, float volume, float pitch) {
        this(event.value(), volume, pitch);
    }

    CircuitSounds(SoundEvent event, float volume, float pitch) {
        this.event = event;
        this.volume = volume;
        this.pitch = pitch;
    }

    public void playOnServer(Level level, BlockPos pos) {
        playOnServer(level, pos, 1, 1);
    }

    public void playOnServer(Level level, BlockPos pos, float volume, float pitch) {
        if (level == null || level.isClientSide)
            return;
        level.playSound(null, pos, event, SoundSource.BLOCKS, this.volume * volume, this.pitch * pitch);
    }

    public void playAt(Level level, Vec3 pos, float volume, float pitch) {
        if (level == null)
            return;
        level.playSound(null, pos.x, pos.y, pos.z, event, SoundSource.BLOCKS, this.volume * volume, this.pitch * pitch);
    }
}
