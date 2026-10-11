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
package io.github.sjzeero.voltworks.circuits.editor;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import io.github.sjzeero.voltworks.circuits.schematic.ISchematicHolder;
import io.github.sjzeero.voltworks.Voltworks;

public abstract class CircuitEditMenu<T extends BlockEntity & IMenuContent & ISchematicHolder> extends AbstractCircuitDesignTableMenu<T> {
    protected CircuitEditMenu(MenuType<?> type, int id, Inventory inv, RegistryFriendlyByteBuf extraData) {
        super(type, id, inv, extraData);
    }

    protected CircuitEditMenu(MenuType<?> type, int id, Inventory inv, T contentHolder) {
        super(type, id, inv, contentHolder);
    }

    @Override
    protected void addSlots() {
        addPlayerSlots(3, 182);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }
}
