package dev.irltunes.hud;

import com.mojang.blaze3d.platform.NativeImage;
import dev.irltunes.Config;
import dev.irltunes.IrlTunes;
import dev.irltunes.media.AppNames;
import dev.irltunes.media.CoverImage;
import dev.irltunes.media.MediaService;
import dev.irltunes.media.TrackInfo;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.neoforged.neoforge.client.gui.GuiLayer;
import org.jspecify.annotations.Nullable;

/** Draws the now-playing widget. Everything here runs on the render thread. */
public final class NowPlayingHud implements GuiLayer {
    private static final Identifier COVER_ID = Identifier.fromNamespaceAndPath(IrlTunes.MODID, "dynamic/cover");
    private static final long HIDE_AFTER_PAUSE_MS = 3000;
    private static final String ELLIPSIS = "…";

    // Layout at 100% scale, in GUI pixels.
    private static final int PAD = 5;
    private static final int COVER = 34;
    private static final int MIN_TEXT_W = 70;
    private static final int MAX_TEXT_W = 140;
    private static final int COMPACT_MAX_W = 200;

    private float alpha;
    private long lastFrameNanos = System.nanoTime();
    private @Nullable TrackInfo shown;
    private long pausedSince = -1;
    private float spinAngle;

    // Cover texture state.
    private @Nullable CoverImage uploadedCover;
    private boolean uploadedAsVinyl;
    private boolean hasTexture;

    @Override
    public void render(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        long nowNanos = System.nanoTime();
        float dt = Math.min(0.1f, (nowNanos - lastFrameNanos) / 1e9f);
        lastFrameNanos = nowNanos;

        MediaService.Snapshot snap = MediaService.get().snapshot();
        TrackInfo track = snap.track();
        long now = System.currentTimeMillis();

        if (track != null) {
            shown = track;
            pausedSince = track.playing() ? -1 : (pausedSince < 0 ? now : pausedSince);
        }
        boolean visible = Config.ENABLED.get() && !mc.gui.hud.isHidden() && track != null
                && (track.playing() || !Config.HIDE_WHEN_PAUSED.get() || now - pausedSince < HIDE_AFTER_PAUSE_MS);

        // Fade in/out over ~200 ms.
        alpha = visible ? Math.min(1f, alpha + dt * 5f) : Math.max(0f, alpha - dt * 5f);
        if (alpha <= 0.01f || shown == null) return;

        boolean spin = Config.SPIN_COVER.get();
        if (spin && shown.playing()) {
            spinAngle = (spinAngle + dt * Config.SPIN_RPM.get() / 60f * (float) (Math.PI * 2)) % (float) (Math.PI * 2);
        }
        updateTexture(mc, snap.cover(), spin);

        Theme theme = Theme.byName(Config.THEME.get());
        float scale = Config.SCALE.get() / 100f;
        Font font = mc.font;

        if (Config.COMPACT.get()) {
            drawCompact(g, font, theme, scale, shown);
        } else {
            drawFull(g, font, theme, scale, shown, spin && hasTexture);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Layouts

    private void drawFull(GuiGraphicsExtractor g, Font font, Theme theme, float scale, TrackInfo t, boolean vinyl) {
        boolean cover = Config.SHOW_COVER.get();
        boolean progress = Config.SHOW_PROGRESS.get();
        String title = t.title();
        String artist = t.artist().isBlank() ? t.album() : t.artist();
        String footer = footer(t, progress);

        int textW = Math.max(font.width(title), Math.max(font.width(artist), font.width(footer)));
        textW = Math.clamp(textW, MIN_TEXT_W, MAX_TEXT_W);
        int textX = PAD + (cover ? COVER + PAD : 0);
        int w = textX + textW + PAD;
        int h = PAD + COVER + PAD;

        beginPanel(g, scale, w, h);
        panelBackground(g, theme, w, h);

        if (cover) {
            int cx = PAD, cy = PAD;
            if (hasTexture) {
                int color = ARGB.white(alpha);
                if (vinyl) {
                    g.pose().pushMatrix();
                    g.pose().translate(cx + COVER / 2f, cy + COVER / 2f);
                    g.pose().rotate(spinAngle);
                    blitCover(g, -COVER / 2, -COVER / 2, color);
                    g.pose().popMatrix();
                } else {
                    blitCover(g, cx, cy, color);
                }
            } else {
                // Placeholder: accent square with a note.
                g.fill(cx, cy, cx + COVER, cy + COVER, ARGB.color(alpha * 0.35f, theme.accent));
                g.centeredText(font, net.minecraft.network.chat.Component.literal("♪"), cx + COVER / 2, cy + COVER / 2 - 4,
                        ARGB.color(alpha, theme.text));
            }
        }

        int y = PAD + 1;
        scrollingText(g, font, title, textX, y, textW, ARGB.color(alpha, theme.text), true);
        scrollingText(g, font, artist, textX, y + 11, textW, ARGB.color(alpha, theme.subtext), false);
        if (!footer.isEmpty()) {
            scrollingText(g, font, footer, textX, y + 22, textW, ARGB.color(alpha * 0.85f, theme.subtext), false);
        }
        if (progress && t.durationMs() > 0) {
            int barY = PAD + COVER - 2;
            progressBar(g, theme, textX, barY, textW, 2, t);
        }
        g.pose().popMatrix();
    }

    private void drawCompact(GuiGraphicsExtractor g, Font font, Theme theme, float scale, TrackInfo t) {
        String line = t.artist().isBlank() ? t.title() : t.title() + " · " + t.artist();
        int noteW = font.width("♪ ");
        int textW = Math.min(font.width(line), COMPACT_MAX_W - noteW - PAD * 2);
        int pad = 3;
        int w = pad + noteW + textW + pad;
        int h = pad + 9 + pad;

        beginPanel(g, scale, w, h);
        panelBackground(g, theme, w, h);
        g.text(font, "♪", pad, pad + 1, ARGB.color(alpha, theme.accent), false);
        scrollingText(g, font, line, pad + noteW, pad + 1, textW, ARGB.color(alpha, theme.text), false);
        if (Config.SHOW_PROGRESS.get() && t.durationMs() > 0) {
            progressBar(g, theme, 0, h - 1, w, 1, t);
        }
        g.pose().popMatrix();
    }

    // ------------------------------------------------------------------------------------------
    // Pieces

    /** Pushes a matrix positioned at the anchored corner and scaled. Caller pops. */
    private void beginPanel(GuiGraphicsExtractor g, float scale, int w, int h) {
        Anchor anchor = Config.ANCHOR.get();
        int sw = Math.round(w * scale), sh = Math.round(h * scale);
        int x = anchor.x(g.guiWidth(), sw, Config.OFFSET_X.get());
        int y = anchor.y(g.guiHeight(), sh, Config.OFFSET_Y.get());
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
    }

    private void panelBackground(GuiGraphicsExtractor g, Theme theme, int w, int h) {
        float bgAlpha = alpha * Config.OPACITY.get() / 100f * theme.backgroundAlphaFactor();
        g.fill(0, 0, w, h, ARGB.color(bgAlpha, theme.background));
        g.fill(0, 0, 1, h, ARGB.color(alpha, theme.accent)); // accent edge
    }

    private void progressBar(GuiGraphicsExtractor g, Theme theme, int x, int y, int w, int h, TrackInfo t) {
        float frac = Math.clamp((float) t.positionNow() / t.durationMs(), 0f, 1f);
        g.fill(x, y, x + w, y + h, ARGB.color(alpha * 0.25f, theme.subtext));
        g.fill(x, y, x + Math.round(w * frac), y + h, ARGB.color(alpha, theme.accent));
    }

    private String footer(TrackInfo t, boolean progress) {
        StringBuilder sb = new StringBuilder();
        if (Config.SHOW_SOURCE.get()) {
            sb.append(AppNames.display(t.app()));
        }
        if (progress && t.positionMs() >= 0) {
            if (!sb.isEmpty()) sb.append("  ");
            sb.append(time(t.positionNow()));
            if (t.durationMs() > 0) sb.append(" / ").append(time(t.durationMs()));
        }
        if (!t.playing()) {
            sb.insert(0, "❚❚ ");
        }
        return sb.toString();
    }

    private static String time(long ms) {
        long s = Math.max(0, ms / 1000);
        return s >= 3600
                ? String.format("%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
                : String.format("%d:%02d", s / 60, s % 60);
    }

    /** Draws text clipped to {@code w}; scrolls (marquee) or ellipsizes when it does not fit. */
    private void scrollingText(GuiGraphicsExtractor g, Font font, String s, int x, int y, int w, int color, boolean shadow) {
        int tw = font.width(s);
        if (tw <= w) {
            g.text(font, s, x, y, color, shadow);
            return;
        }
        if (!Config.MARQUEE.get()) {
            String cut = font.plainSubstrByWidth(s, w - font.width(ELLIPSIS)) + ELLIPSIS;
            g.text(font, cut, x, y, color, shadow);
            return;
        }
        int gap = 30;
        int cycle = tw + gap;
        double pxPerMs = 0.03; // 30 px per second
        long pauseMs = 2000;
        long period = (long) (cycle / pxPerMs) + pauseMs;
        long phase = System.currentTimeMillis() % period;
        int offset = phase < pauseMs ? 0 : (int) ((phase - pauseMs) * pxPerMs);
        g.enableScissor(x, y - 1, x + w, y + 10);
        g.text(font, s, x - offset, y, color, shadow);
        g.text(font, s, x - offset + cycle, y, color, shadow);
        g.disableScissor();
    }

    private void blitCover(GuiGraphicsExtractor g, int x, int y, int color) {
        g.blit(RenderPipelines.GUI_TEXTURED, COVER_ID, x, y, 0f, 0f, COVER, COVER,
                CoverImageSize.SIZE, CoverImageSize.SIZE, CoverImageSize.SIZE, CoverImageSize.SIZE, color);
    }

    // ------------------------------------------------------------------------------------------
    // Texture upload

    private void updateTexture(Minecraft mc, @Nullable CoverImage cover, boolean vinyl) {
        if (cover == uploadedCover && vinyl == uploadedAsVinyl) return;
        uploadedCover = cover;
        uploadedAsVinyl = vinyl;
        if (cover == null) {
            if (hasTexture) {
                mc.getTextureManager().release(COVER_ID);
                hasTexture = false;
            }
            return;
        }
        NativeImage img = new NativeImage(cover.width(), cover.height(), false);
        int w = cover.width(), h = cover.height();
        float r = w / 2f;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = cover.argb()[y * w + x];
                if (vinyl) argb = vinylPixel(argb, x + 0.5f - r, y + 0.5f - r, r);
                img.setPixel(x, y, argb);
            }
        }
        // Registering under the same id closes the previous texture.
        mc.getTextureManager().register(COVER_ID, new DynamicTexture(() -> "irltunes cover", img));
        hasTexture = true;
    }

    /** Turns a square cover into a record: circular, dark grooved rim, the cover as the label, a spindle hole. */
    private static int vinylPixel(int argb, float dx, float dy, float r) {
        float d = (float) Math.sqrt(dx * dx + dy * dy) / r;
        if (d > 1f) return 0;
        if (d < 0.06f) return 0;                                   // spindle hole
        if (d > 0.62f) {                                           // vinyl grooves
            int shade = ((int) (d * 40)) % 2 == 0 ? 0x18 : 0x24;
            if (d > 0.97f) shade = 0x10;
            return ARGB.color(255, shade, shade, shade);
        }
        if (d > 0.58f) return ARGB.color(255, 0x0C, 0x0C, 0x0C);  // label edge
        return argb | 0xFF000000;
    }

    /** Mirrors the cover size produced by the media thread. */
    private static final class CoverImageSize {
        static final int SIZE = 128;
    }
}
