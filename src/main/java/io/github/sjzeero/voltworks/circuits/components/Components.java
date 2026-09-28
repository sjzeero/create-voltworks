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
package io.github.sjzeero.voltworks.circuits.components;

import io.github.sjzeero.voltworks.Voltworks;
import io.github.sjzeero.voltworks.circuits.schematic.ComponentFootprint;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public class Components {
    public static final DeferredRegister<Component> COMPONENTS = DeferredRegister.create(ComponentRegistry.REGISTRY, Voltworks.MOD_ID);

    public static final DeferredHolder<Component, ViaComponent> VIA = component("via", ViaComponent::new, 1, 1, () -> BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", "copper_nugget")),
            b -> b.addPad(0, 0));

    public static final DeferredHolder<Component, LabelComponent> LABEL = component("label", LabelComponent::new, 3, 1, () -> Items.PAPER,
            b -> b.withOutline());

    public static final DeferredHolder<Component, ElectronTubeComponent> TRIODE = component("triode", ElectronTubeComponent::new, 3, 3, null,
            b -> b
                    .addPadSharedText(0, 0, 2, "tube.anode")
                    .addPadSharedText(1, 1, 0, "tube.cathode")
                    .addPadSharedText(2, 0, 1, "tube.grid")
                    .addPadSharedText(0, 2, 3, "tube.heater")
                    .addPadSharedText(2, 2, 4, "tube.heater")
                    .withItem().withOutline());

    public static final DeferredHolder<Component, PentodeComponent> PENTODE = component("pentode", PentodeComponent::new, 3, 3, null,
            b -> b
                    .addPadSharedText(0, 0, 2, "tube.anode")
                    .addPad(1, 0, 5, "Screen", "G2")
                    .addPadSharedText(2, 0, 1, "tube.grid")
                    .addPadSharedText(1, 1, 0, "tube.cathode")
                    .addPadSharedText(0, 2, 3, "tube.heater")
                    .addPadSharedText(2, 2, 4, "tube.heater")
                    .withItem().withOutline());

    public static final DeferredHolder<Component, ThyratronComponent> THYRATRON = component("thyratron", ThyratronComponent::new, 3, 3, null,
            b -> b
                    .addPadSharedText(1, 1, 0, "tube.cathode")
                    .addPadSharedText(1, 0, 1, "tube.grid")
                    .addPadSharedText(0, 2, 3, "tube.heater")
                    .addPadSharedText(2, 2, 4, "tube.heater")
                    .withItem().withOutline());

    public static final DeferredHolder<Component, VFETComponent> VFET = component("vfet", VFETComponent::new, 3, 3, null,
            b -> b
                    .addPad(0, 0, 0, "Drain", "D")
                    .addPad(2, 0, 1, "Source", "S")
                    .addPad(1, 2, 2, "Gate", "G")
                    .withItem().withOutline());

    public static final DeferredHolder<Component, NPNComponent> BJT_NPN = component("bjt_npn", NPNComponent::new, 3, 2, null,
            b -> b
                    .addPad(0, 0, 0, "Collector", "C")
                    .addPad(2, 0, 2, "Emitter", "E")
                    .addPad(1, 1, 1, "Base", "B")
                    .withItem().withOutline());

    public static final DeferredHolder<Component, PNPComponent> BJT_PNP = component("bjt_pnp", PNPComponent::new, 3, 2, null,
            b -> b
                    .addPad(2, 0, 0, "Collector", "C")
                    .addPad(0, 0, 2, "Emitter", "E")
                    .addPad(1, 1, 1, "Base", "B")
                    .withItem().withOutline());

    public static final DeferredHolder<Component, RegulatorTubeComponent> REGULATOR_TUBE = component("regulator_tube", RegulatorTubeComponent::new, 3, 3, null,
            b -> b
                    .addPadSharedText(0, 1, 0, "generic.anode", "generic.anode.short")
                    .addPadSharedText(2, 1, 1, "generic.cathode", "generic.cathode.short")
                    .withItem().withOutline());

    public static final DeferredHolder<Component, BarretterTubeComponent> BARRETTER_TUBE = component("barretter_tube", BarretterTubeComponent::new, 2, 2, null,
            b -> b
                    .addPad(0, 0, 0)
                    .addPad(1, 1, 1)
                    .withItem().withOutline());

    public static final DeferredHolder<Component, NeonBulbComponent> NEON_BULB = component("neon_bulb", NeonBulbComponent::new, 2, 2, null,
            b -> b
                    .addPad(0, 0, 0)
                    .addPad(1, 1, 1)
                    .withItem().withOutline());

    public static final DeferredHolder<Component, LightBulbComponent> LIGHT_BULB = component("light_bulb", LightBulbComponent::new, 3, 3, null,
            b -> b
                    .addPad(0, 1, 0)
                    .addPad(2, 1, 1)
                    .withItem().withOutline());

    public static final DeferredHolder<Component, ConnectorComponent> CONNECTOR = component("connector", ConnectorComponent::new, 3, 3, null,
            b -> b
                    .addPad(1, 1, 0)
                    .withOutline());

    public static final DeferredHolder<Component, SwitchComponent> SWITCH = component("switch", SwitchComponent::new, 4, 3, null,
            b -> b
                    .addPad(0, 1, 0)
                    .addPad(3, 1, 1)
                    .withItem().withOutline());

    public static final DeferredHolder<Component, RelayComponent> RELAY = component("relay", RelayComponent::new, 4, 3, null,
            b -> b
                    .addPadSharedText(0, 0, 0, "relay.coil", null)
                    .addPadSharedText(0, 2, 1, "relay.coil", null)
                    .addPadSharedText(2, 0, 2, "relay.nc", "relay.nc.short")
                    .addPadSharedText(3, 1, 3, "relay.cc", "relay.cc.short")
                    .addPadSharedText(2, 2, 4, "relay.no", "relay.no.short")
                    .withItem().withOutline());

    public static final DeferredHolder<Component, DoubleRelayComponent> RELAY_DPDT = component("relay_dpdt", DoubleRelayComponent::new, 5, 3, null,
            b -> b
                    .addPadSharedText(0, 0, 0, "relay.coil", null)
                    .addPadSharedText(0, 2, 1, "relay.coil", null)
                    .addPadSharedText(2, 0, 2, "relay.nc", "relay.nc.short")
                    .addPadSharedText(2, 1, 3, "relay.cc", "relay.cc.short")
                    .addPadSharedText(2, 2, 4, "relay.no", "relay.no.short")
                    .addPadSharedText(4, 0, 5, "relay.nc", "relay.nc.short")
                    .addPadSharedText(4, 1, 6, "relay.cc", "relay.cc.short")
                    .addPadSharedText(4, 2, 7, "relay.no", "relay.no.short")
                    .withItem().withOutline());

    public static final DeferredHolder<Component, ResistorComponent> RESISTOR = component("resistor", ResistorComponent::new, 5, 3, null,
            b -> b
                    .addPad(0, 1, 0)
                    .addPad(4, 1, 1)
                    .withItem().withOutline());

    public static final DeferredHolder<Component, RedstoneRelayComponent> REDSTONE_RELAY = component("redstone_relay", RedstoneRelayComponent::new, 3, 5, null,
            b -> b
                    .addPad(1, 0, 0)
                    .addPad(1, 4, 1)
                    .withItem().withArrow().withOutline());

    public static final DeferredHolder<Component, VoltageGaugeComponent> VOLTAGE_GAUGE = component("voltage_gauge", VoltageGaugeComponent::new, 5, 5, null,
            b -> b
                    .addPad(2, 0, 0)
                    .addPad(2, 4, 1)
                    .withItem().withArrow().withOutline());

    public static final DeferredHolder<Component, CurrentGaugeComponent> CURRENT_GAUGE = component("current_gauge", CurrentGaugeComponent::new, 5, 5, null,
            b -> b
                    .addPad(2, 0, 0)
                    .addPad(2, 4, 1)
                    .withItem().withArrow().withOutline());

    public static final DeferredHolder<Component, DiodeComponent> DIODE = component("diode", DiodeComponent::new, 5, 3, null,
            b -> b
                    .addPadSharedText(0, 1, 0, "generic.cathode", "generic.cathode.short")
                    .addPadSharedText(4, 1, 1, "generic.anode", "generic.anode.short")
                    .withItem().withOutline());

    public static final DeferredHolder<Component, CapacitorComponent> CAPACITOR = component("capacitor", CapacitorComponent::new, 3, 3, null,
            b -> b
                    .addPad(0, 1, 0)
                    .addPad(2, 1, 1)
                    .withItem().withOutline());

    public static final DeferredHolder<Component, InductorComponent> INDUCTOR = component("inductor", InductorComponent::new, 3, 3, null,
            b -> b
                    .addPad(0, 1, 0)
                    .addPad(2, 1, 1)
                    .withItem().withOutline());

    public static final DeferredHolder<Component, ButtonComponent> BUTTON = component("button", ButtonComponent::new, 3, 3, null,
            b -> b
                    .addPad(0, 1, 0)
                    .addPad(2, 1, 1)
                    .withItem().withOutline());

    public static final DeferredHolder<Component, PotentiometerComponent> POTENTIOMETER = component("potentiometer", PotentiometerComponent::new, 5, 5, null,
            b -> b
                    .addPad(1, 2, 0)
                    .addPad(2, 3, 1)
                    .addPad(3, 2, 2)
                    .withItem().withOutline());

    public static final DeferredHolder<Component, VaristorComponent> VARISTOR = component("varistor", VaristorComponent::new, 4, 4, null,
            b -> b
                    .addPad(0, 1, 0)
                    .addPad(3, 2, 1)
                    .withItem().withOutline());

    public static final DeferredHolder<Component, FuseHolderComponent> FUSE_HOLDER = component("fuse_holder", FuseHolderComponent::new, 4, 3, null,
            b -> b
                    .addPad(0, 1, 0)
                    .addPad(3, 1, 1)
                    .withItem().withOutline());

    public static final DeferredHolder<Component, ModularDisplayComponent> DISPLAY_MODULE = component("display_module", ModularDisplayComponent::new, 4, 4, null,
            b -> b
                    .addPadSharedText(0, 1, 0, "generic.positive", "generic.positive.short")
                    .addPadSharedText(3, 2, 1, "generic.negative", "generic.negative.short")
                    .addPadSharedText(2, 0, 2, "display_module.reset", "display_module.reset.short")
                    .withItem().withOutline());

    private static <T extends Component> DeferredHolder<Component, T> component(String name, Function<ComponentFootprint, T> factory, int width, int height,
                                                                            @Nullable Supplier<? extends Item> item, UnaryOperator<ComponentFootprint.Builder> transform) {
        var sharedKeyBase = "component." + Voltworks.MOD_ID;
        var keyBase = sharedKeyBase + "." + name;
        var holder = COMPONENTS.register(name, () -> factory.apply(transform.apply(new ComponentFootprint.Builder(width, height, keyBase, sharedKeyBase)).build()));
        ComponentRegistry.bindItem(holder.getId(), item != null ? item : Voltworks.ITEMS.registerSimpleItem(name));
        return holder;
    }

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
    }
}
