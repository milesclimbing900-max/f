package com.example.tpamacro;

import net.minecraft.client.gui.DrawContext;

/** Dark smoked-glass drawing helpers with anti-aliased rounded corners. */
public final class Glass {
    private Glass() {}

    public static int rgba(int argb, float alphaMul) {
        int a = Math.round(((argb >>> 24) & 0xFF) * alphaMul);
        a = Math.max(0, Math.min(255, a));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    public static int lerp(int c1, int c2, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a = (int) (((c1 >>> 24) & 255) * (1 - t) + ((c2 >>> 24) & 255) * t);
        int r = (int) (((c1 >> 16) & 255) * (1 - t) + ((c2 >> 16) & 255) * t);
        int g = (int) (((c1 >> 8) & 255) * (1 - t) + ((c2 >> 8) & 255) * t);
        int b = (int) ((c1 & 255) * (1 - t) + (c2 & 255) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Exact horizontal distance from the box edge to the arc at this row. */
    private static double edge(int r, int row, int h) {
        if (r <= 0) return 0;
        double dy;
        if (row < r) dy = r - (row + 0.5);
        else if (row >= h - r) dy = (row + 0.5) - (h - r);
        else return 0;
        double v = (double) r * r - dy * dy;
        if (v < 0) v = 0;
        return r - Math.sqrt(v);
    }

    private static void cornerRow(DrawContext c, int x, int y, int w, int row, int r, int h, int col) {
        double e = edge(r, row, h);
        int i = (int) Math.ceil(e);
        if (x + i < x + w - i) c.fill(x + i, y + row, x + w - i, y + row + 1, col);
        double cov = i - e;
        if (i > 0 && cov > 0.03) {
            int pc = rgba(col, (float) cov);
            c.fill(x + i - 1, y + row, x + i, y + row + 1, pc);
            c.fill(x + w - i, y + row, x + w - i + 1, y + row + 1, pc);
        }
    }

    /** Filled rounded rect with a vertical gradient and smoothed corners. */
    public static void fill(DrawContext c, int x, int y, int w, int h, int r, int top, int bottom) {
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        for (int row = 0; row < r && row < h; row++) {
            cornerRow(c, x, y, w, row, r, h, lerp(top, bottom, (row + 0.5f) / h));
        }
        int mt = r, mb = h - r;
        if (mb > mt) {
            c.fillGradient(x, y + mt, x + w, y + mb,
                    lerp(top, bottom, (float) mt / h), lerp(top, bottom, (float) mb / h));
        }
        for (int row = Math.max(h - r, r); row < h; row++) {
            cornerRow(c, x, y, w, row, r, h, lerp(top, bottom, (row + 0.5f) / h));
        }
    }

    /** 1px smoothed outline, brighter at the top-left, fading to the bottom-right. */
    public static void stroke(DrawContext c, int x, int y, int w, int h, int r, int tl, int br) {
        if (w <= 2 || h <= 2) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        for (int row = 0; row < h; row++) {
            float t = (row + 0.5f) / h;
            double eo = edge(r, row, h);
            int io = (int) Math.ceil(eo);
            double cov = io - eo;
            if (row == 0 || row == h - 1) {
                if (x + io < x + w - io) {
                    c.fill(x + io, y + row, x + w - io, y + row + 1, lerp(tl, br, row == 0 ? 0.15f : 0.85f));
                }
                continue;
            }
            double ei = 1 + edge(Math.max(0, r - 1), row - 1, h - 2);
            int ii = Math.max(io + 1, (int) Math.ceil(ei));
            int cl = lerp(tl, br, t * 0.7f);
            int cr = lerp(tl, br, 0.3f + t * 0.7f);
            c.fill(x + io, y + row, x + ii, y + row + 1, cl);
            c.fill(x + w - ii, y + row, x + w - io, y + row + 1, cr);
            if (io > 0 && cov > 0.03) {
                c.fill(x + io - 1, y + row, x + io, y + row + 1, rgba(cl, (float) cov));
                c.fill(x + w - io, y + row, x + w - io + 1, y + row + 1, rgba(cr, (float) cov));
            }
        }
    }

    /** Dark frosted window like a ClickGUI panel. */
    public static void darkPanel(DrawContext c, int x, int y, int w, int h, int r) {
        for (int i = 3; i >= 1; i--) {
            int e = i * 3;
            fill(c, x - e, y - e + 5, w + 2 * e, h + 2 * e, r + e, 0x0C000000, 0x12000000);
        }
        fill(c, x, y, w, h, r, 0xE6141419, 0xE60C0C10);
        fill(c, x + 2, y + 1, w - 4, Math.min(h / 3, 30), Math.max(2, r - 2), 0x16FFFFFF, 0x00FFFFFF);
        stroke(c, x, y, w, h, r, 0x44FFFFFF, 0x12FFFFFF);
    }

    /** Sunken track / text field. */
    public static void well(DrawContext c, int x, int y, int w, int h) {
        fill(c, x, y, w, h, h / 2, 0x78000000, 0x58000000);
        stroke(c, x, y, w, h, h / 2, 0x10FFFFFF, 0x30FFFFFF);
    }

    /** Small tag-like chip. rgb is 0xRRGGBB, a is the strength 0..1. */
    public static void chip(DrawContext c, int x, int y, int w, int h, int rgb, float a) {
        int top = ((int) (255 * a) << 24) | (rgb & 0xFFFFFF);
        int bot = ((int) (255 * a * 0.7f) << 24) | (rgb & 0xFFFFFF);
        fill(c, x, y, w, h, h / 2, top, bot);
        stroke(c, x, y, w, h, h / 2, 0x40FFFFFF, 0x14FFFFFF);
    }
}
