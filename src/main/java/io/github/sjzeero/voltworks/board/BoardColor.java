package io.github.sjzeero.voltworks.board;

import net.minecraft.core.component.DataComponents;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;

public enum BoardColor implements StringRepresentable {
    GREEN(DyeColor.GREEN),
    WHITE(DyeColor.WHITE),
    BLACK(DyeColor.BLACK),
    ORANGE(DyeColor.ORANGE),
    RED(DyeColor.RED),
    BLUE(DyeColor.BLUE),
    YELLOW(DyeColor.YELLOW),
    PURPLE(DyeColor.PURPLE);

    private final DyeColor dye;

    BoardColor(DyeColor dye) {
        this.dye = dye;
    }

    public DyeColor dye() {
        return dye;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Nullable
    public static BoardColor of(DyeColor dye) {
        for(var color : values()) {
            if(color.dye == dye)
                return color;
        }
        return null;
    }

    @Nullable
    public static BoardColor ofDye(ItemStack stack) {
        return stack.getItem() instanceof DyeItem dye ? of(dye.getDyeColor()) : null;
    }

    public static BoardColor ofBoard(ItemStack stack) {
        var color = stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY).get(BoardBlock.COLOR);
        return color == null ? GREEN : color;
    }

    public static ItemStack paint(ItemStack stack, BoardColor color) {
        var props = new HashMap<>(stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY).properties());
        props.remove(BoardBlock.COLOR.getName());
        if(color != GREEN)
            props.put(BoardBlock.COLOR.getName(), color.getSerializedName());
        if(props.isEmpty())
            stack.remove(DataComponents.BLOCK_STATE);
        else
            stack.set(DataComponents.BLOCK_STATE, new BlockItemStateProperties(props));
        return stack;
    }
}
