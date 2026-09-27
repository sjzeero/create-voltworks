package io.github.sjzeero.eecircuits.client;

import io.github.sjzeero.eecircuits.circuits.components.PotentiometerComponent;
import io.github.sjzeero.eecircuits.circuits.schematic.PlacedComponent;
import io.github.sjzeero.eecircuits.network.UpdateComponentPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class PotentiometerScreen extends Screen {
    private final BlockPos pos;
    private final PlacedComponent placed;
    private int value;
    private int sent;

    public PotentiometerScreen(BlockPos pos, PlacedComponent placed) {
        super(Component.translatable("eecircuits.gui.potentiometer.title"));
        this.pos = pos;
        this.placed = placed;
        this.value = placed.get(PotentiometerComponent.VALUE);
        this.sent = value;
    }

    @Override
    protected void init() {
        addRenderableWidget(new AbstractSliderButton(width / 2 - 100, height / 2 - 10, 200, 20, Component.empty(), value / 100.0) {
            {
                updateMessage();
            }

            @Override
            protected void updateMessage() {
                setMessage(Component.translatable("eecircuits.gui.potentiometer.value", (int) Math.round(this.value * 100)));
            }

            @Override
            protected void applyValue() {
                PotentiometerScreen.this.value = (int) Math.round(this.value * 100);
            }
        });
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(width / 2 - 50, height / 2 + 20, 100, 20).build());
    }

    @Override
    public void tick() {
        if(value != sent)
            send();
    }

    private void send() {
        sent = value;
        placed.set(PotentiometerComponent.VALUE, value);
        var tag = new CompoundTag();
        tag.put(PotentiometerComponent.VALUE.id().toString(), IntTag.valueOf(value));
        PacketDistributor.sendToServer(new UpdateComponentPayload(pos, placed.uuid, PotentiometerComponent.VALUE.id(), tag));
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float partialTicks) {
        super.render(ctx, mouseX, mouseY, partialTicks);
        ctx.drawCenteredString(font, title, width / 2, height / 2 - 30, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        if(value != sent)
            send();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
