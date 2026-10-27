package com.example.tpamacro;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Locale;

/** Movable, expandable Dynamic-Island style pill with spring animation. */
public final class Island {
    private static final int GREEN = 0xFF34C759;
    private static final int ACCENT = 0xFF7C6CFF;

    private static String popTitle = "";
    private static String popSub = "";
    private static int popColor = 0x34C759;
    private static long popUntil = 0;

    private static float w = 60, h = 20, vw = 0, vh = 0, alpha = 0;
    private static long lastNs = 0;

    private static final int[] bounds = new int[4];
    private static final int[] btn = new int[4];
    private static boolean btnShown = false;

    private Island() {}

    public static void pop(String title, String sub, int rgb, long ms) {
        popTitle = title;
        popSub = sub == null ? "" : sub;
        popColor = rgb;
        popUntil = System.currentTimeMillis() + ms;
    }

    public static boolean hit(int mx, int my) {
        return alpha > 0.3f && mx >= bounds[0] && mx < bounds[0] + bounds[2]
                && my >= bounds[1] && my < bounds[1] + bounds[3];
    }

    public static boolean hitButton(int mx, int my) {
        return btnShown && alpha > 0.3f && mx >= btn[0] && mx < btn[0] + btn[2]
                && my >= btn[1] && my < btn[1] + btn[3];
    }

    private static String summary() {
        StringBuilder sb = new StringBuilder();
        for (Config.Target t : Config.targets) {
            if (t.got) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(t.name);
        }
        String s = sb.toString();
        return s.length() > 22 ? s.substring(0, 21) + "…" : s;
    }

    public static void render(DrawContext c, MinecraftClient mc, boolean interactive) {
        TextRenderer tr = mc.textRenderer;
        long nowNs = System.nanoTime();
        float dt = lastNs == 0 ? 0.016f : Math.min(0.05f, (nowNs - lastNs) / 1_000_000_000f);
        lastNs = nowNs;

        boolean event = System.currentTimeMillis() < popUntil;
        boolean run = Macro.running;
        boolean expanded = Config.islandExpanded;
        boolean visible = interactive || run || event || expanded || Config.islandShowIdle;

        List<Config.Target> ts = Config.targets;
        int shown = Math.min(ts.size(), 6);

        String left = "";
        String right = "";
        float tw, th;
        if (expanded) {
            tw = 250;
            int listH = ts.isEmpty() ? 12 : shown * 12 + (ts.size() > shown ? 12 : 0);
            th = 14 + 12 + 10 + listH + 8 + 12 + (interactive ? 30 : 8);
        } else if (event) {
            tw = Math.max(150, Math.max(tr.getWidth(popTitle), tr.getWidth(popSub)) + 52);
            th = popSub.isEmpty() ? 28 : 38;
        } else if (run) {
            left = "/" + Config.mode + "  " + summary();
            right = String.format(Locale.ROOT, "%.1fs", Macro.secondsLeft());
            tw = tr.getWidth(left) + tr.getWidth("00.0s") + 56;
            th = 28;
        } else {
            tw = tr.getWidth("TPA Macro") + 46;
            th = 22;
        }

        vw += ((tw - w) * 380f - vw * 24f) * dt;
        w += vw * dt;
        vh += ((th - h) * 380f - vh * 24f) * dt;
        h += vh * dt;
        alpha += ((visible ? 1f : 0f) - alpha) * (1f - (float) Math.exp(-dt * 12f));
        if (alpha < 0.02f) {
            btnShown = false;
            bounds[2] = 0;
            return;
        }

        int iw = Math.max(24, Math.round(w));
        int ih = Math.max(12, Math.round(h));
        int sw = c.getScaledWindowWidth();
        int sh = c.getScaledWindowHeight();
        int x = Math.max(2, Math.min(sw - iw - 2, sw / 2 + Config.islandOx - iw / 2));
        int y = Math.max(2, Math.min(sh - ih - 2, Config.islandY));
        bounds[0] = x; bounds[1] = y; bounds[2] = iw; bounds[3] = ih;
        float a = Math.min(1f, alpha);
        int r = Math.min(ih / 2, 16);

        if (event && !expanded) {
            for (int i = 4; i >= 1; i--) {
                int col = (0x16 << 24) | (popColor & 0xFFFFFF);
                Glass.fill(c, x - i * 2, y - i * 2, iw + i * 4, ih + i * 4, Math.min((ih + i * 4) / 2, r + i * 2),
                        Glass.rgba(col, a), Glass.rgba(col, a));
            }
        }

        int body = Glass.rgba(0xF4060608, a);
        Glass.fill(c, x, y, iw, ih, r, body, body);
        Glass.stroke(c, x, y, iw, ih, r, Glass.rgba(0x3CFFFFFF, a), Glass.rgba(0x12FFFFFF, a));

        btnShown = false;
        if (alpha > 0.6f && iw > tw * 0.85f && ih > th * 0.8f) {
            c.enableScissor(x + 3, y, x + iw - 3, y + ih);
            int cx = x + 14, cr = x + iw - 14;

            if (expanded) {
                int ly = y + 12;
                int dot = run ? GREEN : 0xFF8E8E93;
                Glass.fill(c, cx, ly + 1, 6, 6, 3, dot, dot);
                String title = run ? "Running  /" + Config.mode : "Idle";
                c.drawText(tr, Text.literal(title).formatted(Formatting.BOLD), cx + 12, ly, 0xFFFFFFFF, false);
                String tm = run ? String.format(Locale.ROOT, "%.3fs", Macro.secondsLeft()) : "";
                c.drawText(tr, tm, cr - tr.getWidth(tm), ly, 0xFFB0B0B8, false);

                int by = ly + 13;
                c.fill(cx, by, cr, by + 2, 0x30FFFFFF);
                if (run) c.fill(cx, by, cx + Math.round((cr - cx) * Macro.progress()), by + 2, GREEN);

                int ty = by + 10;
                if (ts.isEmpty()) {
                    c.drawText(tr, "No streamers added", cx, ty, 0xFF7A7A84, false);
                    ty += 12;
                } else {
                    for (int i = 0; i < shown; i++) {
                        Config.Target t = ts.get(i);
                        int dc = t.got ? GREEN : 0xFF5A5A64;
                        Glass.fill(c, cx, ty + 2, 4, 4, 2, dc, dc);
                        c.drawText(tr, t.name, cx + 10, ty, 0xFFE6E6EA, false);
                        String st = t.got ? "Got" : (run ? "Trying" : "Waiting");
                        c.drawText(tr, st, cr - tr.getWidth(st), ty, t.got ? GREEN : 0xFF8E8E98, false);
                        ty += 12;
                    }
                    if (ts.size() > shown) {
                        c.drawText(tr, "+" + (ts.size() - shown) + " more", cx, ty, 0xFF7A7A84, false);
                        ty += 12;
                    }
                }
                ty += 8;
                String f = String.format(Locale.ROOT, "Delay %.1f-%.1fs  last %s",
                        Config.delayMin, Config.delayMax, Macro.lastDelayText());
                c.drawText(tr, f, cx, ty, 0xFF7A7A84, false);

                if (interactive) {
                    int bx = cx, bw = cr - cx, bh = 18, byy = y + ih - bh - 8;
                    int bc = run ? 0xFFFF453A : ACCENT;
                    Glass.fill(c, bx, byy, bw, bh, bh / 2, bc, Glass.lerp(bc, 0xFF000000, 0.25f));
                    Glass.stroke(c, bx, byy, bw, bh, bh / 2, 0x50FFFFFF, 0x14FFFFFF);
                    Text lbl = Text.literal(run ? "Stop" : "Start").formatted(Formatting.BOLD);
                    c.drawText(tr, lbl, bx + bw / 2 - tr.getWidth(lbl) / 2, byy + 5, 0xFFFFFFFF, false);
                    btn[0] = bx; btn[1] = byy; btn[2] = bw; btn[3] = bh;
                    btnShown = true;
                }
            } else if (event) {
                Glass.fill(c, x + 13, y + ih / 2 - 4, 8, 8, 4, 0xFF000000 | popColor, 0xFF000000 | popColor);
                int ty = popSub.isEmpty() ? y + (ih - 8) / 2 : y + 8;
                c.drawText(tr, Text.literal(popTitle).formatted(Formatting.BOLD), x + 29, ty, 0xFFFFFFFF, false);
                if (!popSub.isEmpty()) c.drawText(tr, popSub, x + 29, y + 21, 0xFF9A9AA0, false);
            } else if (run) {
                float pulse = 0.55f + 0.45f * (float) Math.sin(nowNs / 1.8e8);
                Glass.fill(c, x + 13, y + 7, 6, 6, 3, Glass.rgba(GREEN, pulse), Glass.rgba(GREEN, pulse));
                c.drawText(tr, left, x + 26, y + 6, 0xFFFFFFFF, false);
                c.drawText(tr, right, x + iw - 14 - tr.getWidth(right), y + 6, 0xFFB0B0B8, false);
                int bx = x + 14, bw = iw - 28, by = y + ih - 6;
                c.fill(bx, by, bx + bw, by + 2, 0x30FFFFFF);
                c.fill(bx, by, bx + Math.round(bw * Macro.progress()), by + 2, GREEN);
            } else {
                Glass.fill(c, x + 12, y + ih / 2 - 3, 6, 6, 3, 0xFF5A5A64, 0xFF5A5A64);
                c.drawText(tr, "TPA Macro", x + 24, y + (ih - 8) / 2, 0xFFB0B0B8, false);
            }
            c.disableScissor();
        }
    }
}
