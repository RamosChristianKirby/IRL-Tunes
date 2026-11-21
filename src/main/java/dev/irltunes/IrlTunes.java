package dev.irltunes;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import dev.irltunes.hud.NowPlayingHud;
import dev.irltunes.hud.Theme;
import dev.irltunes.media.MediaService;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

import org.slf4j.Logger;

/**
 * IRL Tunes: shows the music playing on your computer as a small HUD widget.
 * Client-only; it never talks to the server.
 */
@Mod(value = IrlTunes.MODID, dist = Dist.CLIENT)
public final class IrlTunes {
    public static final String MODID = "irltunes";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(MODID, "main"));

    public static final KeyMapping TOGGLE_KEY = new KeyMapping(
            "key.irltunes.toggle", InputConstants.Type.KEYBOARD, InputConstants.KEY_N, CATEGORY);
    public static final KeyMapping CONFIG_KEY = new KeyMapping(
            "key.irltunes.config", InputConstants.Type.KEYBOARD, InputConstants.KEY_M, CATEGORY);
    public static final KeyMapping THEME_KEY = new KeyMapping(
            "key.irltunes.theme", InputConstants.Type.KEYBOARD, InputConstants.KEY_COMMA, CATEGORY);

    private static ModContainer container;

    public IrlTunes(IEventBus modBus, ModContainer container) {
        IrlTunes.container = container;
        container.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        modBus.addListener(IrlTunes::registerKeys);
        modBus.addListener(IrlTunes::registerLayers);
        modBus.addListener(ModConfigEvent.Loading.class, e -> MediaService.get().start());
        modBus.addListener(ModConfigEvent.Reloading.class, e -> MediaService.get().onConfigChanged());
        NeoForge.EVENT_BUS.addListener(IrlTunes::onClientTick);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> MediaService.get().stop(), "irltunes-shutdown"));
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(TOGGLE_KEY);
        event.register(CONFIG_KEY);
        event.register(THEME_KEY);
    }

    private static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Identifier.fromNamespaceAndPath(MODID, "now_playing"), new NowPlayingHud());
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (TOGGLE_KEY.consumeClick()) {
            boolean now = !Config.ENABLED.get();
            Config.ENABLED.set(now);
            Config.SPEC.save();
            notify(Component.translatable(now ? "irltunes.msg.shown" : "irltunes.msg.hidden"));
        }
        while (THEME_KEY.consumeClick()) {
            Theme next = Theme.byName(Config.THEME.get()).next();
            Config.THEME.set(next.name());
            Config.SPEC.save();
            notify(Component.translatable("irltunes.msg.theme", next.displayName()));
        }
        while (CONFIG_KEY.consumeClick()) {
            if (container != null && mc.gui.screen() == null) {
                mc.gui.setScreen(new ConfigurationScreen(container, null));
            }
        }
    }

    private static void notify(Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendOverlayMessage(message);
        }
    }
}
