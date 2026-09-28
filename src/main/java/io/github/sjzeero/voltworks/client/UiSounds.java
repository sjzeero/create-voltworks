/*
 * Copyright 2025 patryk3211
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
 * Modified by sjzeero: ported from Create: Power Grid to CEE: Voltworks.
 */
package io.github.sjzeero.voltworks.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

public enum UiSounds {
    CLICK(SoundEvents.UI_BUTTON_CLICK, 1f, 1f),
    COMPONENT_ROTATE(Holder.direct(SoundEvents.ITEM_FRAME_ROTATE_ITEM), 0.75f, 0.75f),
    PLACE_TRACE(Holder.direct(SoundEvents.METAL_PLACE), 0.75f, 1.75f),
    DELETE_AREA(Holder.direct(SoundEvents.ITEM_FRAME_REMOVE_ITEM), 0.5f, 1f),
    PLACE_COMPONENT(Holder.direct(SoundEvents.ITEM_FRAME_ADD_ITEM), 0.5f, 1f),
    FAIL(SoundEvents.NOTE_BLOCK_BASS, 1f, 0.5f),
    SELECT_COMPONENT(SoundEvents.UI_BUTTON_CLICK, 1f, 1f);

    private final Holder<SoundEvent> event;
    private final float volume;
    private final float pitch;

    UiSounds(Holder<SoundEvent> event, float volume, float pitch) {
        this.event = event;
        this.volume = volume;
        this.pitch = pitch;
    }

    public void play() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event.value(), pitch, volume));
    }
}
