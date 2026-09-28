package io.github.sjzeero.voltworks.circuits.editor;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;

public interface IMenuContent {
    void sendToMenu(RegistryFriendlyByteBuf buf);

    void readClient(CompoundTag tag, HolderLookup.Provider registries);
}
