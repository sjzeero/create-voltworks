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
 * Modified by sjzeero: ported from Create: Power Grid to Voltworks.
 */
package io.github.sjzeero.voltworks.client;

import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;
import io.github.sjzeero.voltworks.Voltworks;

/**
 * @see com.simibubi.create.AllKeys
 */
public enum ModdedKeys {
    CATEGORY_CIRCUIT_EDITOR("key.categories.voltworks.circuit_editor"),

    ROTATE_COMPONENT("rotate_component", GLFW.GLFW_KEY_R),
    PLACE_TRACE("place_trace", GLFW.GLFW_KEY_T),
    DELETE_AREA("delete_area", GLFW.GLFW_KEY_D),
    PICK_COMPONENT("pick_component", GLFW.GLFW_KEY_S),
    SWITCH_LAYER("switch_layer", GLFW.GLFW_KEY_X),

    ;

    public KeyMapping keybind;
    public String description;
    public int key;

    public String category;

    ModdedKeys(String description, int defaultKey) {
        this.description = Voltworks.MOD_ID + ".keyinfo." + description;
        this.key = defaultKey;
    }

    ModdedKeys(String category) {
        this.category = category;
    }

    public static void register(RegisterKeyMappingsEvent event) {
        String currentCategory = null;
        for(var key : values()) {
            if(key.description == null) {
                currentCategory = key.category;
            } else {
                key.keybind = new KeyMapping(key.description, key.key, currentCategory);
                event.register(key.keybind);
            }
        }
    }

    public KeyMapping getKeybind() {
        return keybind;
    }

    public boolean isPressed() {
        return keybind.isDown();
    }

    public String getBoundKey() {
        return keybind.getTranslatedKeyMessage().getString().toUpperCase();
    }

    public boolean matchesKey(int keyCode, int scanCode) {
        return keybind.matches(keyCode, scanCode);
    }

    public boolean matchesMouse(int button) {
        return keybind.matchesMouse(button);
    }
}
