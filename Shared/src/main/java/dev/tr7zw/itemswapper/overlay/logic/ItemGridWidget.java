package dev.tr7zw.itemswapper.overlay.logic;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import dev.tr7zw.itemswapper.ItemSwapperSharedMod;
import dev.tr7zw.itemswapper.api.client.ItemSwapperClientAPI;
import dev.tr7zw.itemswapper.config.ConfigManager;
import dev.tr7zw.itemswapper.manager.ClientProviderManager;
import dev.tr7zw.itemswapper.util.WidgetUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;

public abstract class ItemGridWidget implements GuiWidget {

    protected final Minecraft minecraft = Minecraft.getInstance();
    protected final ClientProviderManager providerManager = ItemSwapperSharedMod.instance.getClientProviderManager();
    protected final ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
    protected final ConfigManager configManager = ConfigManager.getInstance();
    protected final ItemSwapperClientAPI clientAPI = ItemSwapperClientAPI.getInstance();
    protected final List<GuiSlot> slots = new ArrayList<>();
    protected WidgetArea widgetArea = new WidgetArea(0, 0, 128, 128, null, 0, 0);

    protected ItemGridWidget(int x, int y) {
        this.widgetArea.setX(x);
        this.widgetArea.setY(y);
    }

    public void render(GuiComponent parent, PoseStack poseStack, int originX, int originY, boolean overwrideAvailable) {
        originX += getWidgetArea().getX();
        originY += getWidgetArea().getY();
        WidgetUtil.renderBackground(getWidgetArea(), poseStack, originX, originY);
        RenderSystem.setShaderTexture(0, WidgetUtil.WIDGETS_LOCATION);
        List<Runnable> itemRenderList = new ArrayList<>();
        List<Runnable> lateRenderList = new ArrayList<>();
        for (int i = 0; i < getSlots().size(); i++) {
            renderSelection(parent, poseStack, i, originX + getSlots().get(i).x(), originY + getSlots().get(i).y(),
                    itemRenderList,
                    lateRenderList, overwrideAvailable);
        }
        RenderSystem.enableBlend();
        itemRenderList.forEach(Runnable::run);
        RenderSystem.enableBlend();
        lateRenderList.forEach(Runnable::run);
    }

    private void renderSelection(GuiComponent parent, PoseStack poseStack, int listId, int x, int y,
            List<Runnable> itemRenderList,
            List<Runnable> lateRenderList,
            boolean overwrideAvailable) {
        if (getWidgetArea().getBackgroundTexture() == null) {
            RenderSystem.setShaderTexture(0, WidgetUtil.WIDGETS_LOCATION);
            parent.blit(poseStack, x, y, 24, 22, 29, 24);
        }
        GuiSlot guiSlot = getSlots().get(listId);
        if (guiSlot.selected().get()) {
            itemRenderList = lateRenderList;
                RenderSystem.setShader(GameRenderer::getPositionTexShader);
                RenderSystem.setShaderTexture(0, WidgetUtil.SELECTION_LOCATION);
                GuiComponent.blit(poseStack, x - 1, y, 200, 0, 0, 24, 24, 24, 24);
        }
        renderSlot(poseStack, x, y, itemRenderList, guiSlot, overwrideAvailable);
    }

    protected abstract void renderSlot(PoseStack poseStack, int x, int y, List<Runnable> itemRenderList,
            GuiSlot guiSlot, boolean overwrideAvailable);

    @Override
    public List<GuiSlot> getSlots() {
        return slots;
    }

    @Override
    public WidgetArea getWidgetArea() {
        return widgetArea;
    }

    @Override
    public int titleYOffset() {
        return widgetArea.getBackgroundSizeY();
    }

}