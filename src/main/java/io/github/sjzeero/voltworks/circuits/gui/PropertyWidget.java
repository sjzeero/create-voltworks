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
package io.github.sjzeero.voltworks.circuits.gui;

import net.createmod.catnip.gui.widget.AbstractSimiWidget;
import net.minecraft.client.gui.Font;
import io.github.sjzeero.voltworks.circuits.components.properties.PropertyEntry;

public abstract class PropertyWidget<T, P extends PropertyEntry<T>> extends AbstractSimiWidget {
    protected final Font textRenderer;
    protected final P property;

    protected PropertyWidget(Font textRenderer, int x, int y, P property) {
        super(x, y, 60, 20);
        this.textRenderer = textRenderer;
        this.property = property;
    }
}
