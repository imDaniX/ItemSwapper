package dev.tr7zw.itemswapper;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import com.mojang.blaze3d.platform.InputConstants;

import dev.tr7zw.config.CustomConfigScreen;
import dev.tr7zw.itemswapper.config.CacheManager;
import dev.tr7zw.itemswapper.config.ConfigManager;
import dev.tr7zw.itemswapper.manager.ClientProviderManager;
import dev.tr7zw.itemswapper.manager.ItemGroupManager;
import dev.tr7zw.itemswapper.manager.ItemGroupManager.Page;
import dev.tr7zw.itemswapper.manager.itemgroups.ItemGroup;
import dev.tr7zw.itemswapper.manager.itemgroups.ItemList;
import dev.tr7zw.itemswapper.overlay.EditListScreen;
import dev.tr7zw.itemswapper.overlay.ItemListOverlay;
import dev.tr7zw.itemswapper.overlay.ItemSwapperUI;
import dev.tr7zw.itemswapper.overlay.SwitchItemOverlay;
import dev.tr7zw.itemswapper.provider.InstrumentItemNameProvider;
import dev.tr7zw.itemswapper.provider.PotionNameProvider;
import dev.tr7zw.itemswapper.provider.RecordNameProvider;
import dev.tr7zw.itemswapper.provider.ShulkerContainerProvider;
import dev.tr7zw.util.ComponentProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Button.OnPress;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.item.Item;

public abstract class ItemSwapperSharedMod {

    public static final Logger LOGGER = LogManager.getLogger("ItemSwapper");
    public static final String MODID = "itemswapper";
    private static final Minecraft minecraft = Minecraft.getInstance();

    public static ItemSwapperSharedMod instance;

    private final ConfigManager configManager = ConfigManager.getInstance();
    private final CacheManager cacheManager = CacheManager.getInstance();
    private final ItemGroupManager itemGroupManager = new ItemGroupManager();
    private final ClientProviderManager clientProviderManager = new ClientProviderManager();
    private final List<String> enableOnIp = cacheManager.getCache().enableOnIp;
    private final List<String> disableOnIp = cacheManager.getCache().disableOnIp;

    protected KeyMapping keybind = new KeyMapping("key.itemswapper.itemswitcher", InputConstants.KEY_R, "ItemSwapper");

    private boolean enableShulkers = false;
    private boolean enableRefill = false;
    private boolean modDisabled = false;
    private boolean disabledByPlayer = false;
    private boolean bypassAccepted = false;
    private boolean pressed = false;
    private boolean lateInitCompleted = false;
    private Item lastItem;
    private Page lastPage;

    public void init() {
        instance = this;
        LOGGER.info("Loading ItemSwapper!");

        initModloader();
    }

    private void lateInit() {
        clientProviderManager.registerContainerProvider(new ShulkerContainerProvider());
        clientProviderManager.registerNameProvider(new PotionNameProvider());
        clientProviderManager.registerNameProvider(new RecordNameProvider());
        clientProviderManager.registerNameProvider(new InstrumentItemNameProvider());
    }

    public void clientTick() {
        // run this code later, so all other mods are done loading
        if (!lateInitCompleted) {
            lateInitCompleted = true;
            lateInit();
        }
        Screen screen = minecraft.screen;

        ServerData server = Minecraft.getInstance().getCurrentServer();

        if (server != null && disableOnIp.contains(server.ip) && !disabledByPlayer) {
            setDisabledByPlayer(true);
            LOGGER.info("Itemswapper is deactivated for the server {}, because the player did not accept the warning!",
                    server.ip);
        } else if (keybind.isDown()) {
            if (isModDisabled()) {
                minecraft.gui.setOverlayMessage(
                        ComponentProvider.translatable("text.itemswapper.disabled").withStyle(ChatFormatting.RED), false);
            } else if (screen instanceof ItemSwapperUI ui) {
                onPress(ui);
            } else if (screen != null) {
                // not our screen, don't do anything
            } else {
                onPress(null);
            }
        } else {
            pressed = false;

            if (!configManager.getConfig().toggleMode && screen instanceof ItemSwapperUI ui) {
                onPrimaryClick(ui, true);
            }
        }
    }

    private void onPress(ItemSwapperUI overlay) {
        if (minecraft.player != null && !itemGroupManager.isResourcepackSelected()) {
            minecraft.player.displayClientMessage(
                    ComponentProvider.translatable("text.itemswapper.resourcepack.notSelected").withStyle(ChatFormatting.RED),
                    true);
        }

        if (!pressed && isModDisabled()) {
            pressed = true;
            minecraft.gui.setOverlayMessage(
                    ComponentProvider.translatable("text.itemswapper.disabled").withStyle(ChatFormatting.RED), false);
            return;
        }

        ServerData server = Minecraft.getInstance().getCurrentServer();

        if(!pressed) {
            if (isDisabledByPlayer()) {
                minecraft.gui.setOverlayMessage(
                        ComponentProvider.translatable("text.itemswapper.disabledByPlayer").withStyle(ChatFormatting.RED), false);
            } else if (server != null && !enableOnIp.contains(server.ip) && !enableShulkers && !bypassAccepted) {
                openConfirmationScreen();
            } else if (overlay == null) {
                if(!bypassAccepted && server != null && enableOnIp.contains(server.ip)) {
                    bypassAccepted = true;
                    minecraft.gui.setOverlayMessage(
                            ComponentProvider.translatable("text.itemswapper.usedwhitelist").withStyle(ChatFormatting.GOLD), false);
                }
                if (couldOpenScreen()) {
                    pressed = true;
                    return;
                }
            } else {
                onPrimaryClick(overlay, true);
            }
        }
        pressed = true;
    }

    private void openConfirmationScreen() {
        minecraft.setScreen(
                new ConfirmScreen(this::acceptBypassCallback, ComponentProvider.translatable("text.itemswapper.confirm.title"),
                        ComponentProvider.translatable("text.itemswapper.confirm.description")));
    }

    private boolean couldOpenScreen() {
        if (minecraft.player.getMainHandItem().isEmpty()) {
            openInventoryScreen();
            return true;
        }

        Item itemInHand = minecraft.player.getMainHandItem().getItem();
        ItemList entries = itemGroupManager.getList(itemInHand);

        if (entries != null) {
            openListSwitchScreen(new ItemListOverlay(entries));
            return true;
        } else {
            ItemGroup group = itemGroupManager.getItemPage(itemInHand);
            if (group != null) {
                openSquareSwitchScreen(group);
                return true;
            }
        }
        if (configManager.getConfig().fallbackInventory) {
            openInventoryScreen();
            return true;
        }
        return false;
    }

    public static void openInventoryScreen() {
        if (minecraft.screen instanceof SwitchItemOverlay overlay) {
            overlay.openInventory();
            minecraft.getSoundManager().resume();
            minecraft.mouseHandler.grabMouse();
            return;
        }
        minecraft.setScreen(SwitchItemOverlay.createInventoryOverlay());
        minecraft.getSoundManager().resume();
        minecraft.mouseHandler.grabMouse();
    }

    public static void openListSwitchScreen(ItemListOverlay entries) {
        minecraft.setScreen(entries);
        minecraft.getSoundManager().resume();
        minecraft.mouseHandler.grabMouse();
    }

    public void openSquareSwitchScreen(ItemGroup group) {
        if (minecraft.screen instanceof SwitchItemOverlay overlay) {
            overlay.openItemGroup(group);
            return;
        }
        minecraft.setScreen(SwitchItemOverlay.createPaletteOverlay(group));
        minecraft.getSoundManager().resume();
        minecraft.mouseHandler.grabMouse();
    }

    public void openPage(Page page) {
        if (minecraft.screen instanceof SwitchItemOverlay overlay) {
            overlay.openPage(page);
            return;
        }
        minecraft.setScreen(SwitchItemOverlay.createPageOverlay(page));
        minecraft.getSoundManager().resume();
        minecraft.mouseHandler.grabMouse();
    }

    public static void onPrimaryClick(@NotNull ItemSwapperUI xtOverlay, boolean forceClose) {
        boolean keepOpen = xtOverlay.onPrimaryClick();
        if(forceClose || !keepOpen) {
            if (xtOverlay instanceof Overlay) {
                minecraft.setOverlay(null);
            } else if (xtOverlay instanceof Screen) {
                minecraft.setScreen(null);
            }
        }
    }

    public Screen createConfigScreen(Screen parent) {
        return new CustomConfigScreen(parent, "text.itemswapper.title") {

            private CustomConfigScreen inst = this;
            
            @Override
            public void initialize() {
                List<OptionInstance<?>> options = new ArrayList<>();
                options.add(getOnOffOption("text.itemswapper.showTooltips", () -> configManager.getConfig().showTooltips,
                        b -> configManager.getConfig().showTooltips = b));
                options.add(getOnOffOption("text.itemswapper.toggleMode", () -> configManager.getConfig().toggleMode,
                        b -> configManager.getConfig().toggleMode = b));
                options.add(getOnOffOption("text.itemswapper.showCursor", () -> configManager.getConfig().showCursor,
                        b -> configManager.getConfig().showCursor = b));
                options.add(getOnOffOption("text.itemswapper.editMode", () -> configManager.getConfig().editMode,
                        b -> configManager.getConfig().editMode = b));
                options.add(getOnOffOption("text.itemswapper.creativeCheatMode",
                        () -> configManager.getConfig().creativeCheatMode,
                        b -> configManager.getConfig().creativeCheatMode = b));
                options.add(
                        getOnOffOption("text.itemswapper.ignoreHotbar", () -> configManager.getConfig().ignoreHotbar,
                                b -> configManager.getConfig().ignoreHotbar = b));
                options.add(
                        getOnOffOption("text.itemswapper.unlockListMouse",
                                () -> configManager.getConfig().unlockListMouse,
                                b -> configManager.getConfig().unlockListMouse = b));
                options.add(
                        getOnOffOption("text.itemswapper.disableShulkers",
                                () -> configManager.getConfig().disableShulkers,
                                b -> configManager.getConfig().disableShulkers = b));

                options.add(getDoubleOption("text.itemswapper.controllerSpeed", 1, 16, 0.1f,
                        () -> (double) configManager.getConfig().controllerSpeed,
                        d -> configManager.getConfig().controllerSpeed = d.floatValue()));
                options.add(getDoubleOption("text.itemswapper.mouseSpeed", 0.1f, 3, 0.1f,
                        () -> (double) configManager.getConfig().mouseSpeed,
                        d -> configManager.getConfig().mouseSpeed = d.floatValue()));
                options.add(
                        getOnOffOption("text.itemswapper.fallbackInventory",
                                () -> configManager.getConfig().fallbackInventory,
                                b -> configManager.getConfig().fallbackInventory = b));

                getOptions().addSmall(options.toArray(new OptionInstance[0]));
                this.addRenderableWidget(Button.builder(ComponentProvider.translatable("text.itemswapper.whitelist"), new OnPress() {
                    @Override
                    public void onPress(Button button) {
                        Minecraft.getInstance().setScreen(new EditListScreen(inst, Minecraft.getInstance().options, true));
                    }
                }).pos(this.width / 2 - 210, this.height - 27).size(50, 20).build());
                this.addRenderableWidget(Button.builder(ComponentProvider.translatable("text.itemswapper.blacklist"), new OnPress() {
                    @Override
                    public void onPress(Button button) {
                        Minecraft.getInstance().setScreen(new EditListScreen(inst, Minecraft.getInstance().options, false));
                    }
                }).pos(this.width / 2 - 160, this.height - 27).size(50, 20).build());
            }

            @Override
            public void save() {
                configManager.writeConfig();
            }

            @Override
            public void reset() {
                configManager.reset();
            }

        };
    }

    public abstract void initModloader();

    public ItemGroupManager getItemGroupManager() {
        return itemGroupManager;
    }

    public void setEnableShulkers(boolean value) {
        this.enableShulkers = value;
    }

    public boolean areShulkersEnabled() {
        return this.enableShulkers;
    }

    public boolean isEnableRefill() {
        return enableRefill;
    }

    public void setEnableRefill(boolean enableRefill) {
        this.enableRefill = enableRefill;
    }

    public void setBypassExcepted(boolean bypassExcepted) {
        this.bypassAccepted = bypassExcepted;
    }

    public void setModDisabled(boolean value) {
        this.modDisabled = value;
    }

    public boolean isModDisabled() {
        return this.modDisabled;
    }

    private void acceptBypassCallback(boolean accepted) {
        ServerData server = Minecraft.getInstance().getCurrentServer();

        if (server != null) {
            if (accepted) {
                bypassAccepted = true;
                cacheManager.getCache().enableOnIp.add(server.ip);
            } else {
                cacheManager.getCache().disableOnIp.add(server.ip);
            }
            cacheManager.writeConfig();
            ItemSwapperSharedMod.LOGGER.info("Add {} to cached ip-addresses", server.ip);
        }
        minecraft.setScreen(null);
    }

    public ClientProviderManager getClientProviderManager() {
        return clientProviderManager;
    }

    public boolean isDisabledByPlayer() {
        return disabledByPlayer;
    }

    public void setDisabledByPlayer(boolean disabledByPlayer) {
        this.disabledByPlayer = disabledByPlayer;
    }
    
    public KeyMapping getKeybind() {
        return keybind;
    }

    public Item getLastItem() {
        return lastItem;
    }

    public void setLastItem(Item lastItem) {
        this.lastItem = lastItem;
    }

    public Page getLastPage() {
        return lastPage;
    }

    public void setLastPage(Page lastPage) {
        this.lastPage = lastPage;
    }
    
}
