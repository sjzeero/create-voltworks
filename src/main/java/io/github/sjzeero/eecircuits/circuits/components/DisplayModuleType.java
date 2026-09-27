/*
 * Copyright 2025-2026 Create: Power Grid contributors (https://github.com/patryk3211/PowerGrid)
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
 * Modified by sjzeero: ported from Create: Power Grid to EE Circuits.
 */
package io.github.sjzeero.eecircuits.circuits.components;
import net.minecraft.util.ByIdMap;

import java.util.function.IntFunction;

public enum DisplayModuleType {
    ZERO_TO_NINE(0, "0 - 9", "zerotonine", 80f, 9f),
    NINE_TO_ZERO(1, "9 - 0", "ninetozero", 80f, 9f),
    ONE_TO_ZERO(2, "1 - 0", "onetozero", 80f, 9f),
    HEXADECIMAL(3, "Hexadecimal", "zerotof", 112f, 15f),
    SYMBOLS(4, "Symbols", "symbols", 80f, 8f),
    ALPHABET(5, "Alphabet", "alphabet", 176f, 25f);

    private static final IntFunction<DisplayModuleType> BY_ID = ByIdMap.continuous(DisplayModuleType::getId, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    private final int id;

    private final String name;
    private final String displayTexture;
    private final float spriteWidth;
    private final float characterCount;

    DisplayModuleType(int id, String name, String displayTexture, float spriteWidth, float characterCount) {
        this.id = id;
        this.name = name;
        this.displayTexture = displayTexture;
        this.spriteWidth = spriteWidth;
        this.characterCount = characterCount;
    }

    public int getId() {
        return this.id;
    }

    public String getDisplayTexture() {
        return this.displayTexture;
    }

    public float getSpriteWidth() {
        return this.spriteWidth;
    }

    public float getCharacterCount() {
        return this.characterCount;
    }

    public static DisplayModuleType byId(int moduleId) {
        return BY_ID.apply(moduleId);
    }

    public String getDisplayName() {
        return name;
    }
}