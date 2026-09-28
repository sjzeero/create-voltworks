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
package io.github.sjzeero.eecircuits.circuits.editor;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import io.github.sjzeero.eecircuits.EEMenus;

public class CircuitDesignTableLoadMenu extends AbstractCircuitDesignTableMenu<CircuitDesignTableBlockEntity> {
    public CircuitDesignTableLoadMenu(MenuType<?> type, int id, Inventory inv, CircuitDesignTableBlockEntity contentHolder) {
        super(type, id, inv, contentHolder);
    }

    public CircuitDesignTableLoadMenu(MenuType<?> type, int id, Inventory inv, RegistryFriendlyByteBuf extraData) {
        super(type, id, inv, extraData);
    }

    @Override
    protected Class<CircuitDesignTableBlockEntity> clazz() {
        return CircuitDesignTableBlockEntity.class;
    }

    public static CircuitDesignTableLoadMenu create(int id, Inventory inv, CircuitDesignTableBlockEntity be) {
        return new CircuitDesignTableLoadMenu(EEMenus.CIRCUIT_DESIGN_TABLE_LOAD.get(), id, inv, be);
    }

    @Override
    protected void addSlots() {
//        addPlayerSlots(3, 182);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }
}
