package dev.tr7zw.itemswapper.overlay.logic;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.tr7zw.itemswapper.overlay.SwitchItemOverlay;
import net.minecraft.client.gui.GuiComponent;

public interface GuiWidget {

    public List<GuiSlot> getSlots();

    public WidgetArea getWidgetArea();

    public void render(GuiComponent parent, PoseStack poseStack, int originX, int originY, boolean overwrideAvailable);

    public void renderSelectedSlotName(GuiSlot selected, int yOffset, int maxWidth, boolean overwrideAvailable);
    
    public default void renderSelectedTooltip(SwitchItemOverlay overlay, PoseStack poseStack, GuiSlot selected, double x, double y) {
        
    }

    /**
     * A click is done via the middle mouse key
     * 
     * @param slot
     */
    public void onSecondaryClick(SwitchItemOverlay overlay, GuiSlot slot, int xOffset, int yOffset);

    /**
     * Close is called when letting go from the key/re-pressing it in toggle
     * mode/left click
     * 
     * @param slot
     * @return should the ui stay open if possible
     */
    public boolean onPrimaryClick(SwitchItemOverlay overlay, GuiSlot slot, int xOffset, int yOffset);

    public default int titleYOffset() {
        return getWidgetArea().getBackgroundSizeY();
    }
    
    public default String getSelector(GuiSlot slot) {
        return null;
    }

}
