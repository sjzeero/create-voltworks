package io.github.sjzeero.eecircuits.circuits.circuitboard;

import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import com.simibubi.create.content.kinetics.deployer.DeployerRecipeSearchEvent;
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipeParams;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import io.github.sjzeero.eecircuits.EECircuits;
import io.github.sjzeero.eecircuits.circuits.CircuitItems;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// Lets a deployer put parts into an incomplete circuit sitting on a belt or depot.
public class CircuitInsertion {
    public static void onDeployerRecipeSearch(DeployerRecipeSearchEvent event) {
        var inventory = event.getInventory();
        var circuit = inventory.getItem(0);
        var held = inventory.getItem(1);
        var level = event.getBlockEntity().getLevel();
        if(level == null || held.isEmpty() || !circuit.is(CircuitItems.INCOMPLETE_CIRCUIT.get()))
            return;
        var result = IncompleteCircuitItem.insert(level, circuit, held);
        if(result == null || !event.shouldAddRecipeWithPriority(100))
            return;
        var recipe = new InsertRecipe(new Params(circuit, held, result), result);
        event.addRecipe(() -> Optional.of(new RecipeHolder<>(EECircuits.asResource("circuit_insertion"), recipe)), 100);
    }

    private static class Params extends ItemApplicationRecipeParams {
        Params(ItemStack circuit, ItemStack part, ItemStack result) {
            ingredients.add(Ingredient.of(circuit.getItem()));
            ingredients.add(Ingredient.of(part.getItem()));
            results.add(new ProcessingOutput(result, 1));
        }
    }

    private static class InsertRecipe extends DeployerApplicationRecipe {
        private final ItemStack result;

        InsertRecipe(ItemApplicationRecipeParams params, ItemStack result) {
            super(params);
            this.result = result;
        }

        @Override
        public List<ItemStack> rollResults(RandomSource random) {
            return new ArrayList<>(List.of(result.copy()));
        }

        @Override
        public List<ItemStack> rollResults(List<ProcessingOutput> outputs, RandomSource random) {
            return rollResults(random);
        }
    }
}
