package io.github.sjzeero.voltworks.board;

import io.github.sjzeero.voltworks.Voltworks;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public class BoardDyeRecipe extends CustomRecipe {
    public BoardDyeRecipe(CraftingBookCategory category) {
        super(category);
    }

    private record Parts(ItemStack board, BoardColor color) {
    }

    private static Parts find(CraftingInput input) {
        ItemStack board = ItemStack.EMPTY;
        BoardColor color = null;
        for(int i = 0; i < input.size(); i++) {
            var stack = input.getItem(i);
            if(stack.isEmpty())
                continue;
            if(stack.is(Voltworks.BOARD_ITEM.get()) && board.isEmpty()) {
                board = stack;
                continue;
            }
            var dye = BoardColor.ofDye(stack);
            if(dye != null && color == null) {
                color = dye;
                continue;
            }
            return null;
        }
        if(board.isEmpty() || color == null || BoardColor.ofBoard(board) == color)
            return null;
        return new Parts(board, color);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return find(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        var parts = find(input);
        if(parts == null)
            return ItemStack.EMPTY;
        return BoardColor.paint(parts.board().copyWithCount(1), parts.color());
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return Voltworks.BOARD_DYEING.get();
    }
}
