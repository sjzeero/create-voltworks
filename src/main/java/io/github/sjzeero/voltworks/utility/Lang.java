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
package io.github.sjzeero.voltworks.utility;

import io.github.sjzeero.voltworks.Voltworks;
import net.createmod.catnip.lang.LangBuilder;
import net.minecraft.network.chat.MutableComponent;

public class Lang extends net.createmod.catnip.lang.Lang {
    public static MutableComponent translateDirect(String key, Object... args) {
        return builder().translate(key, args).component();
    }

    public static LangBuilder builder() {
        return Lang.builder(Voltworks.MOD_ID);
    }

    public static LangBuilder translate(String langKey, Object... args) {
        return builder().translate(langKey, args);
    }

    public static LangBuilder text(String literal) {
        return builder().text(literal);
    }
}
