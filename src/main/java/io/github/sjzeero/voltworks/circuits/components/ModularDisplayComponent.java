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
 * Modified by sjzeero: ported from Create: Power Grid to Voltworks.
 */
package io.github.sjzeero.voltworks.circuits.components;

import com.google.common.collect.ImmutableCollection;
import net.minecraft.world.item.DyeColor;
import org.jetbrains.annotations.NotNull;
import io.github.sjzeero.voltworks.Voltworks;
import io.github.sjzeero.voltworks.circuits.circuitboard.ComponentCircuitBuilder;
import io.github.sjzeero.voltworks.circuits.components.properties.*;
import io.github.sjzeero.voltworks.circuits.schematic.ComponentFootprint;
import io.github.sjzeero.voltworks.circuits.schematic.PlacedComponent;
import io.github.sjzeero.voltworks.circuits.thermal.ThermalBuilder;
import io.github.sjzeero.voltworks.circuits.CircuitSounds;
import io.github.sjzeero.voltworks.pgsim.SwitchedWire;
import io.github.sjzeero.voltworks.circuits.Unit;

public class ModularDisplayComponent extends OrientableComponent {
    public static final IntProperty INDEX = new IntProperty(Voltworks.MOD_ID, "modular_display_index", 0, 0, 30).hidden().cast();
    public static final BooleanProperty HALF_CLICK = new BooleanProperty(Voltworks.MOD_ID, "modular_display_half_click").hidden().cast();
    public static final BooleanProperty WIRE_RESET = new BooleanProperty(Voltworks.MOD_ID, "modular_display_reset").hidden().cast();
    public static final ConstantProperty MIN_CURRENT = new ConstantProperty(Voltworks.MOD_ID, "modular_display_current", Unit.CURRENT.formatWithPrefixes(.5f).component());
    public static final ConstantProperty RESISTANCE = new ConstantProperty(Voltworks.MOD_ID, "modular_display_resistance", Unit.RESISTANCE.formatWithPrefixes(25).component());
    public static final BooleanProperty REMOVE_BLANKING_PAGE = new BooleanProperty(Voltworks.MOD_ID, "modular_display_blanking_page");
    public static final EnumProperty<DisplayModuleType> CURRENT_MODULE = new EnumProperty<DisplayModuleType>(Voltworks.MOD_ID, "modular_display_module",
            DisplayModuleType.class, new DisplayModuleType[]{DisplayModuleType.ZERO_TO_NINE, DisplayModuleType.NINE_TO_ZERO, DisplayModuleType.ONE_TO_ZERO, DisplayModuleType.HEXADECIMAL, DisplayModuleType.SYMBOLS, DisplayModuleType.ALPHABET});
    public static final EnumProperty<DyeColor> CURRENT_COLOR = new EnumProperty<DyeColor>(Voltworks.MOD_ID, "modular_display_text_color", DyeColor.class);

    public ModularDisplayComponent(ComponentFootprint footprint) {
        super(footprint);
    }

    //some of these might be moved to enum
    private static final float SHEET_HEIGHT = 16f;
    private static final float FRAME_WIDTH = 5f;
    private static final float FRAME_HEIGHT = 7f;
    private static final float FRAME_PADDING = 1f;
    private static final float PIXEL = 1f / 16f;
    private static final float INNER_OFFSET = 1f * PIXEL;
    private static final float INNER_UD_OFFSET = .75f * PIXEL;
    private static final float INNER_UD_SIZE = 2.5f * PIXEL;
    private static final float INNER_RL_SIZE = 2f * PIXEL;
    private static final float Y_NUDGE = 0.0001f;

    @Override
    protected void addProperties(ImmutableCollection.Builder<ComponentProperty<?>> properties) {
        super.addProperties(properties);
        properties.add(CURRENT_MODULE, CURRENT_COLOR, REMOVE_BLANKING_PAGE, RESISTANCE, MIN_CURRENT, INDEX, HALF_CLICK, WIRE_RESET, power(25));
    }

    @Override
    public boolean tick(@NotNull PlacedComponent placed) {
        var module = placed.get(CURRENT_MODULE);

        if (placed.isClient()) return true;
        if (placed.wires.isEmpty())
            return true;

        var coilNodeToReset = (SwitchedWire) placed.wires.get(0);
        var coilNodeToNegative =  (SwitchedWire) placed.wires.get(2);

        var coilNodeToNegativeCurrent = Math.abs(coilNodeToNegative.current());
        var coilNodeToResetCurrent = Math.abs(coilNodeToReset.current());
        var charCount = (placed.get(REMOVE_BLANKING_PAGE) ? module.getCharacterCount() - 1 : module.getCharacterCount());
        var index = placed.get(INDEX);
        //every module display texture has the characters in the sprite plus a blank space and the first character again for smooth transition
        //but im only counting characters before the blank space and adding one for the blank space and two for the transition

        if (placed.get(WIRE_RESET) == true){
            coilNodeToReset.setState(false);
            coilNodeToNegative.setState(true);
            placed.set(WIRE_RESET, false);
        }

        if (coilNodeToNegative.isConverged()){

            if (coilNodeToNegativeCurrent >= .5 && index != charCount+1 && !placed.get(HALF_CLICK)) {
                placed.set(INDEX, index +1);
                placed.set(HALF_CLICK, true);
                placed.onServerWorld(() -> world -> CircuitSounds.RELAY_CLICK.playOnServer(world, placed.getPos(), 0.75f, 2f));
                placed.notifyClients(INDEX);
                placed.notifyClients(HALF_CLICK);
            }

            if (coilNodeToNegativeCurrent < .5 && index == charCount+1 && coilNodeToNegative.getState()){
                placed.onServerWorld(() -> world -> CircuitSounds.RELAY_CLICK.playOnServer(world, placed.getPos(), 0.75f, 1.9f));
                coilNodeToNegative.setState(false);
                coilNodeToReset.setState(true);
                placed.set(HALF_CLICK, false);
                placed.notifyClients(HALF_CLICK);
            }

            if (coilNodeToNegativeCurrent < .5 && coilNodeToNegative.getState() && placed.get(HALF_CLICK)) {
                placed.set(HALF_CLICK, false);
                placed.onServerWorld(() -> world -> CircuitSounds.RELAY_CLICK.playOnServer(world, placed.getPos(), 0.75f, 1.9f));
                placed.notifyClients(HALF_CLICK);
            }

            if (coilNodeToReset.getState() && coilNodeToResetCurrent >= .5 && index == charCount+1) {
                placed.onServerWorld(() -> world -> CircuitSounds.RELAY_CLICK.playOnServer(world, placed.getPos(), 0.75f, 2f));
                placed.set(INDEX, index +1);
                placed.set(HALF_CLICK, true);
                coilNodeToNegative.setState(true);
                coilNodeToReset.setState(false);
                placed.notifyClients(INDEX);
                placed.notifyClients(HALF_CLICK);
            }

            if (index >= charCount+2 && !placed.get(HALF_CLICK)){
                placed.set(INDEX, 0);
                placed.notifyClients(INDEX);
            }
        }
        return true;
    }

    @Override
    public void bake(@NotNull PlacedComponent placed, @NotNull ComponentCircuitBuilder builder, ThermalBuilder.@NotNull IEmitter thermals) {

        var coilNode = builder.addInternalNode();
        var coil = builder.connect(25, builder.terminalNode(0), coilNode);
        var coilNodeToNegitive = builder.connectSwitch(0.1f, builder.terminalNode(1), coilNode, true);
        var coilNodeToReset = builder.connectSwitch(0.1f, builder.terminalNode(2), coilNode, false);
        placed.add(coilNodeToReset); placed.add(coil); placed.add(coilNodeToNegitive);

        thermals.builder()
                .setThermalMass(0.15f)
                .setMaxPower(25, 125f)
                .addHeatSource(coil);

    }

}
