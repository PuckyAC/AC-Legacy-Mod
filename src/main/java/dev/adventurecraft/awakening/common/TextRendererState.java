package dev.adventurecraft.awakening.common;

import dev.adventurecraft.awakening.extension.client.render.ExTesselator;
import dev.adventurecraft.awakening.extension.client.render.ExTextRenderer;
import dev.adventurecraft.awakening.image.Rgba;
import dev.adventurecraft.awakening.text.TextMeasurer;
import dev.adventurecraft.awakening.util.HexConvert;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.Tesselator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

public class TextRendererState implements TextMeasurer {

    private final Font font;
    private @Nullable Tesselator tesselator;

    private int color;
    private int activeColor;
    private int shadow;
    private int activeShadow;

    private float shadowOffsetX, shadowOffsetY;

    public TextRendererState(Font font) {
        this.font = font;
    }

    public @NotNull TextRect measureText(CharSequence text, int start, int end, long maxWidth, boolean newLines) {
        return ((ExTextRenderer) this.font).measureText(text, start, end, maxWidth, newLines);
    }

    public void bindTexture() {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.font.fontTexture);
    }

    public void begin(Tesselator tesselator) {
        this.bindTexture();

        this.tesselator = tesselator;
        this.tesselator.begin();

        this.resetFormat();
    }

    public void end() {
        assert this.tesselator != null;

        this.tesselator.end();
        this.tesselator = null;
    }

    public void drawText(CharSequence text, int start, int end, float x, float y) {
        this.drawText(text, start, end, x, y, false);
    }

    /**
     * Apply formatting without rendering text.
     */
    public void formatText(CharSequence text, int start, int end) {
        this.drawText(text, start, end, 0, 0, true);
    }

    private void drawText(CharSequence text, int start, int end, float x, float y, boolean formatOnly) {
        if (text == null) {
            return;
        }
        if (end - start == 0) {
            return;
        }
        validateCharSequence(text, start, end);

        formatOnly = formatOnly || (Rgba.getAlpha(this.activeColor) == 0);

        var ts = this.tesselator;
        assert ts != null;

        var exTs = (ExTesselator) ts;
        exTs.ac$color(this.activeColor);

        var font = (ExTextRenderer) this.font;
        int[] colorPalette = font.getColorPalette();
        int[] widthLookup = font.getCharWidths();

        float xOff = 0;

        for (int i = start; i < end; ++i) {
            char c = text.charAt(i);
            if (end > i + 1 && c == '§') {
                int formatCode = text.charAt(i + 1);
                if (formatCode == 'r') {
                    this.resetFormat();
                }
                else {
                    int colorIndex = HexConvert.fromHexDigit(formatCode);
                    if (colorIndex < 0 || colorIndex > 15) {
                        colorIndex = 15;
                    }

                    this.activeColor = Rgba.withRgb(this.activeColor, colorPalette[colorIndex]);
                    if (this.hasShadow()) {
                        this.activeShadow = Rgba.withRgb(this.activeShadow, colorPalette[colorIndex + 16]);
                    }
                    else {
                        exTs.ac$color(this.activeColor);
                    }
                }
                i++; // skip the format code digit
                continue;
            }

            if (formatOnly) {
                continue;
            }

            int ch = font.getCharIndex(c);
            if (ch == -1) {
                continue;
            }

            int column = ch % 16 * 8;
            int row = ch / 16 * 8;
            if (this.hasShadow()) {
                exTs.ac$color(this.activeShadow);
                drawChar(ts, column, row, xOff + x + this.shadowOffsetX, y + this.shadowOffsetY);
                exTs.ac$color(this.activeColor);
            }
            drawChar(ts, column, row, xOff + x, y);
            xOff += widthLookup[ch];
        }
    }

    public void drawText(CharSequence text, float x, float y) {
        this.drawText(text, 0, text.length(), x, y);
    }

    public void setColor(int color) {
        this.color = color;
    }

    public void setShadow(int color) {
        this.shadow = color;
    }

    public void setShadowToColor() {
        this.shadow = ExTextRenderer.getShadowColor(this.color);
    }

    public void resetFormat() {
        this.activeColor = this.color;
        this.activeShadow = this.shadow;
    }

    public boolean hasShadow() {
        return Rgba.getAlpha(this.activeShadow) != 0;
    }

    public void setShadowOffsetX(float offsetX) {
        this.shadowOffsetX = offsetX;
    }

    public void setShadowOffsetY(float offsetY) {
        this.shadowOffsetY = offsetY;
    }

    public void setShadowOffset(float offsetX, float offsetY) {
        this.setShadowOffsetX(offsetX);
        this.setShadowOffsetY(offsetY);
    }

    private static void drawChar(Tesselator ts, int column, int row, float x, float y) {
        float u = column / 128f;
        float v = row / 128f;
        float f = 7.99f;
        float t = f / 128f;
        ts.vertexUV(x, y + f, 0.0, u, v + t);
        ts.vertexUV(x + f, y + f, 0.0, u + t, v + t);
        ts.vertexUV(x + f, y, 0.0, u + t, v);
        ts.vertexUV(x, y, 0.0, u, v);
    }

    public static void validateCharSequence(CharSequence text, int start, int end) {
        if (start > text.length()) {
            throw new IllegalArgumentException(String.format(
                "start (%d) exceeds text length (%d).",
                start,
                text.length()
            ));
        }
        if (end > text.length()) {
            throw new IllegalArgumentException(String.format("end (%d) exceeds text length (%d).", end, text.length()));
        }
        if ((end - start) > text.length()) {
            throw new IllegalArgumentException(String.format(
                "(end (%d) - start (%d)) exceeds text length (%d).",
                end,
                start,
                text.length()
            ));
        }
    }
}
