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
package io.github.sjzeero.eecircuits.compat.jei;

import io.github.sjzeero.eecircuits.EECircuits;
import io.github.sjzeero.eecircuits.circuits.CircuitTags;
import io.github.sjzeero.eecircuits.circuits.editor.CircuitDesignTableEditScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

@JeiPlugin
@SuppressWarnings({"rawtypes", "unchecked"})
public class EECircuitsJEI implements IModPlugin {
    public static final ResourceLocation UID = EECircuits.asResource("jei");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(CircuitDesignTableEditScreen.class, new IGhostIngredientHandler<CircuitDesignTableEditScreen>() {
            @Override
            public <I> List<Target<I>> getTargetsTyped(CircuitDesignTableEditScreen gui, ITypedIngredient<I> ingredient, boolean doStart) {
                if(ingredient.getType() != VanillaTypes.ITEM_STACK)
                    return List.of();
                if(!ingredient.getItemStack().get().is(CircuitTags.CIRCUIT_COMPONENT))
                    return List.of();
                return List.of(new Target<>() {
                    @Override
                    public Rect2i getArea() {
                        return gui.ghostTarget();
                    }

                    @Override
                    public void accept(I ingredient) {
                        if(ingredient instanceof ItemStack stack) {
                            gui.toolSelect(stack.getItem());
                        }
                    }
                });
            }

            @Override
            public void onComplete() {

            }
        });
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registry) {
        var design = new CircuitDesignCategory();
        var assembly = new CircuitAssemblyCategory();
        registry.addRecipeCategories(design);
        registry.addRecipeCategories(assembly);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create", "mechanical_arm")), CircuitAssemblyCategory.TYPE);
        registration.addRecipeCatalyst(EECircuits.DESIGN_TABLE_ITEM.get(), CircuitDesignCategory.TYPE);
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        // Register dummy recipes.
        registration.addRecipes(CircuitDesignCategory.TYPE, List.of(new Object()));
        registration.addRecipes(CircuitAssemblyCategory.TYPE, List.of(new Object()));
    }
}
