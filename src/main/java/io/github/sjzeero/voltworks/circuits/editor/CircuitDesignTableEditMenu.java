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
 * Modified by sjzeero: ported from Create: Power Grid to Create: Voltworks.
 */
package io.github.sjzeero.voltworks.circuits.editor;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import io.github.sjzeero.voltworks.EEMenus;

public class CircuitDesignTableEditMenu extends CircuitEditMenu<CircuitDesignTableBlockEntity> {
    public CircuitDesignTableEditMenu(MenuType<?> type, int id, Inventory inv, CircuitDesignTableBlockEntity contentHolder) {
        super(type, id, inv, contentHolder);
    }

    public CircuitDesignTableEditMenu(MenuType<?> type, int id, Inventory inv, RegistryFriendlyByteBuf extraData) {
        super(type, id, inv, extraData);
    }

    @Override
    protected Class<CircuitDesignTableBlockEntity> clazz() {
        return CircuitDesignTableBlockEntity.class;
    }

    public static CircuitDesignTableEditMenu create(int id, Inventory inv, CircuitDesignTableBlockEntity be) {
        return new CircuitDesignTableEditMenu(EEMenus.CIRCUIT_DESIGN_TABLE_EDIT.get(), id, inv, be);
    }
}
