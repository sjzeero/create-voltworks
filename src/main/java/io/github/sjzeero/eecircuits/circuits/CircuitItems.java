package io.github.sjzeero.eecircuits.circuits;

import com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem;
import io.github.sjzeero.eecircuits.EECircuits;
import io.github.sjzeero.eecircuits.circuits.circuitboard.IncompleteCircuitItem;
import io.github.sjzeero.eecircuits.circuits.fluid.AcidFluidType;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.List;

public class CircuitItems {
    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, EECircuits.MOD_ID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, EECircuits.MOD_ID);

    public static final DeferredItem<Item> EMPTY_CIRCUIT = EECircuits.ITEMS.registerSimpleItem("empty_circuit");
    public static final DeferredItem<SequencedAssemblyItem> INCOMPLETE_UNETCHED_CIRCUIT = EECircuits.ITEMS.registerItem("incomplete_unetched_circuit", SequencedAssemblyItem::new);
    public static final DeferredItem<Item> UNETCHED_CIRCUIT = EECircuits.ITEMS.registerSimpleItem("unetched_circuit");
    public static final DeferredItem<IncompleteCircuitItem> INCOMPLETE_CIRCUIT = EECircuits.ITEMS.registerItem("incomplete_circuit", IncompleteCircuitItem::new);
    public static final DeferredItem<SequencedAssemblyItem> INCOMPLETE_BJT_NPN = EECircuits.ITEMS.registerItem("incomplete_bjt_npn", SequencedAssemblyItem::new);
    public static final DeferredItem<SequencedAssemblyItem> INCOMPLETE_BJT_PNP = EECircuits.ITEMS.registerItem("incomplete_bjt_pnp", SequencedAssemblyItem::new);

    public static final DeferredHolder<FluidType, AcidFluidType> ACID_TYPE = FLUID_TYPES.register("acid", () -> new AcidFluidType(FluidType.Properties.create()
            .descriptionId("fluid.eecircuits.acid")
            .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
            .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> ACID = FLUIDS.register("acid", () -> new BaseFlowingFluid.Source(acidProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> ACID_FLOWING = FLUIDS.register("flowing_acid", () -> new BaseFlowingFluid.Flowing(acidProperties()));
    public static final DeferredBlock<LiquidBlock> ACID_BLOCK = EECircuits.BLOCKS.register("acid", () -> new LiquidBlock(ACID.get(),
            BlockBehaviour.Properties.ofFullCopy(Blocks.WATER).noLootTable()));
    public static final DeferredItem<BucketItem> ACID_BUCKET = EECircuits.ITEMS.registerItem("acid_bucket", p -> new BucketItem(ACID.get(),
            p.craftRemainder(Items.BUCKET).stacksTo(1)));

    public static final List<DeferredItem<?>> HIDDEN = List.of(INCOMPLETE_UNETCHED_CIRCUIT, INCOMPLETE_BJT_NPN, INCOMPLETE_BJT_PNP);

    private static BaseFlowingFluid.Properties acidProperties() {
        return new BaseFlowingFluid.Properties(ACID_TYPE, ACID, ACID_FLOWING)
                .bucket(ACID_BUCKET)
                .block(ACID_BLOCK);
    }

    public static void register(IEventBus modBus) {
        FLUID_TYPES.register(modBus);
        FLUIDS.register(modBus);
    }
}
