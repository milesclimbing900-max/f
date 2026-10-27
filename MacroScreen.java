package com.example.tpamacro;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MacroScreen extends Screen {
    private static final int ACCENT = 0xFF7C6CFF;
    private static final int ROW = 24, HEADER = 28, PW = 250, PAD = 10, LBL = 62, KW = 10;
    private static final String[] MODES = {"/tpahere", "/tpa"};
    private static final String[] STOPS = {"Keep", "First", "All"};

    private TextFieldWidget field;
    private boolean prevDown, prevEnter;
    private int drag = 0; // 1 delay min, 2 delay max, 3 range, 4 panel, 5 island
    private int pressX, pressY, startA, startB;
    private boolean moved;
    private final Map<String, Float> anims = new HashMap<>();
    private float bodyAnim;
    private long lastNs;
    private int clipBottom = Integer.MAX_VALUE;
    private int mX, mY;
    private boolean mClick, mDown;
    private float kAnim = 0.2f;

    public MacroScreen() {
        super(Text.literal("TPA Macro"));
        bodyAnim = Config.panelCollapsed ? 0f : 1f;
    }

    @Override
    protected void init() {
        field = new TextFieldWidget(this.textRenderer, 0, 0, 100, 10, Text.literal("Streamer names"));
        field.setDrawsBackground(false);
        field.setMaxLength(100);
        field.setEditableColor(0xFFFFFFFF);
        field.setTextPredicate(s -> s.matches("[A-Za-z0-9_., ]*"));
        this.addSelectableChild(field);
        this.setInitialFocus(field);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        Config.save();
        super.close();
    }

    // ---------- small helpers ----------
    private boolean in(int x, int y, int w, int h) {
        return mX >= x && mX < x + w && mY >= y && mY < y + h && mY < clipBottom;
    }

    private float anim(String id, float target) {
        float v = anims.getOrDefault(id, target);
        v += (target - v) * kAnim;
        anims.put(id, v);
        return v;
    }

    private void text(DrawContext c, String s, int x, int y, int color) {
        c.drawText(textRenderer, s, x, y, color, false);
    }

    private void textB(DrawContext c, String s, int x, int y, int color) {
        c.drawText(textRenderer, Text.literal(s).formatted(Formatting.BOLD), x, y, color, false);
    }

    private void rowBase(DrawContext c, int x, int y, int w, String label) {
        if (in(x - 6, y, w + 12, ROW)) c.fill(x - 6, y, x + w + 6, y + ROW, 0x0AFFFFFF);
        c.fill(x, y + ROW - 1, x + w, y + ROW, 0x0EFFFFFF);
        text(c, label, x, y + 8, 0xFFE6E6EA);
    }

    private double frac(int mx, int sx, int sw) {
        return Math.max(0, Math.min(1, (mx - sx - KW / 2.0) / (sw - KW)));
    }

    private void knob(DrawContext c, int kx, int ky) {
        Glass.fill(c, kx - 1, ky + 1, KW + 2, KW + 2, (KW + 2) / 2, 0x30000000, 0x42000000);
        Glass.fill(c, kx, ky, KW, KW, KW / 2, 0xFFFFFFFF, 0xFFD6D9E4);
    }

    // ---------- rows ----------
    private boolean rowToggle(DrawContext c, int x, int y, int w, String label, boolean on, String id) {
        rowBase(c, x, y, w, label);
        boolean hov = in(x - 6, y, w + 12, ROW);
        float a = anim(id, on ? 1f : 0f);
        int sw = 28, sh = 14, sx = x + w - sw, sy = y + (ROW - sh) / 2;
        int track = Glass.lerp(0xFF2B2B33, ACCENT, a);
        Glass.fill(c, sx, sy, sw, sh, sh / 2, track, Glass.lerp(track, 0xFF000000, 0.2f));
        Glass.stroke(c, sx, sy, sw, sh, sh / 2, 0x2CFFFFFF, 0x10FFFFFF);
        int kx = sx + 2 + Math.round(a * (sw - sh));
        int kc = Glass.lerp(0xFF8A8A94, 0xFFFFFFFF, a);
        Glass.fill(c, kx, sy + 2, sh - 4, sh - 4, (sh - 4) / 2, kc, kc);
        return mClick && hov;
    }

    private int rowSeg(DrawContext c, int x, int y, int w, String label, String[] opts, float pos) {
        rowBase(c, x, y, w, label);
        int cx = x + LBL, cw = w - LBL, ch = 16, cy = y + (ROW - ch) / 2;
        Glass.well(c, cx, cy, cw, ch);
        int n = opts.length;
        float sw = cw / (float) n;
        int tx = cx + 1 + Math.round(pos * sw);
        int tw = Math.round(sw) - 2;
        Glass.fill(c, tx, cy + 1, tw, ch - 2, (ch - 2) / 2, ACCENT, Glass.lerp(ACCENT, 0xFF000000, 0.28f));
        Glass.fill(c, tx + 2, cy + 2, tw - 4, (ch - 2) / 2, 3, 0x30FFFFFF, 0x00FFFFFF);
        for (int i = 0; i < n; i++) {
            int mid = cx + Math.round(sw * i + sw / 2);
            boolean sel = Math.abs(pos - i) < 0.5f;
            Text t = sel ? Text.literal(opts[i]).formatted(Formatting.BOLD) : Text.literal(opts[i]);
            c.drawText(textRenderer, t, mid - textRenderer.getWidth(t) / 2, cy + 4,
                    sel ? 0xFFFFFFFF : 0xFFA0A0AA, false);
        }
        return in(cx, cy, cw, ch) ? Math.min(n - 1, (int) ((mX - cx) / sw)) : -1;
    }

    private void rowDelay(DrawContext c, int x, int y, int w) {
        rowBase(c, x, y, w, "Delay");
        int vw = 70;
        int sx = x + LBL, sw = w - LBL - vw - 4;
        int ty = y + ROW / 2 - 2;
        Glass.well(c, sx, ty, sw, 4);
        int k1 = sx + Math.round((float) (Config.delayMin / 10.0) * (sw - KW));
        int k2 = sx + Math.round((float) (Config.delayMax / 10.0) * (sw - KW));
        Glass.fill(c, k1 + KW / 2, ty, Math.max(2, k2 - k1), 4, 2, ACCENT, ACCENT);
        knob(c, k1, y + ROW / 2 - KW / 2);
        knob(c, k2, y + ROW / 2 - KW / 2);
        String v = String.format(Locale.ROOT, "%.1f-%.1fs", Config.delayMin, Config.delayMax);
        text(c, v, x + w - textRenderer.getWidth(v), y + 8, 0xFFFFFFFF);

        if (mClick && in(sx - 4, y, sw + 8, ROW)) {
            int c1 = k1 + KW / 2, c2 = k2 + KW / 2;
            if (c1 == c2) drag = mX < c1 ? 1 : 2;
            else drag = Math.abs(mX - c1) <= Math.abs(mX - c2) ? 1 : 2;
        }
        if (mDown && (drag == 1 || drag == 2)) {
            double val = Math.round(frac(mX, sx, sw) * 100) / 10.0;
            if (drag == 1) Config.delayMin = Math.min(val, Config.delayMax);
            else Config.delayMax = Math.max(val, Config.delayMin);
        }
    }

    private void rowRange(DrawContext c, int x, int y, int w) {
        rowBase(c, x, y, w, "Range");
        int vw = 70;
        int sx = x + LBL, sw = w - LBL - vw - 4;
        int ty = y + ROW / 2 - 2;
        Glass.well(c, sx, ty, sw, 4);
        int k = sx + Math.round((float) ((Config.range - 1.0) / 15.0) * (sw - KW));
        Glass.fill(c, sx, ty, k - sx + KW / 2, 4, 2, ACCENT, ACCENT);
        knob(c, k, y + ROW / 2 - KW / 2);
        String v = String.format(Locale.ROOT, "%.1f blocks", Config.range);
        text(c, v, x + w - textRenderer.getWidth(v), y + 8, 0xFFFFFFFF);

        if (mClick && in(sx - 4, y, sw + 8, ROW)) drag = 3;
        if (mDown && drag == 3) Config.range = 1.0 + Math.round(frac(mX, sx, sw) * 30) / 2.0;
    }

    private void addNames() {
        for (String n : field.getText().trim().split("[\\s,]+")) {
            if (!n.isBlank()) Config.addTarget(n);
        }
        field.setText("");
        Config.save();
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        try {
            renderBackground(c, mx, my, delta);
        } catch (IllegalStateException ignored) {
            // blur was already applied this frame
        }
        Macro.update(this.client);

        long nowNs = System.nanoTime();
        float dt = lastNs == 0 ? 0.016f : Math.min(0.05f, (nowNs - lastNs) / 1_000_000_000f);
        lastNs = nowNs;
        kAnim = 1f - (float) Math.exp(-dt * 14f);

        // polled input so we don't depend on the 1.21.9+ click/key event classes
        long win = GLFW.glfwGetCurrentContext();
        boolean down = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean click = down && !prevDown;
        boolean released = !down && prevDown;
        prevDown = down;
        boolean enterNow = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_ENTER) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(win, GLFW.GLFW_KEY_KP_ENTER) == GLFW.GLFW_PRESS;
        boolean enter = enterNow && !prevEnter;
        prevEnter = enterNow;

        mX = mx; mY = my; mDown = down; clipBottom = Integer.MAX_VALUE;

        // ---- island interaction (uses last frame's bounds) ----
        boolean uiClick = click;
        if (click) {
            if (Island.hitButton(mx, my)) {
                Macro.toggle(this.client);
                uiClick = false;
            } else if (Island.hit(mx, my)) {
                drag = 5; pressX = mx; pressY = my;
                startA = Config.islandOx; startB = Config.islandY;
                moved = false;
                uiClick = false;
            }
        }
        mClick = uiClick;
        if (drag == 5 && down) {
            int dx = mx - pressX, dy = my - pressY;
            if (Math.abs(dx) + Math.abs(dy) > 3) moved = true;
            if (moved) {
                int lim = this.width / 2 - 16;
                Config.islandOx = Math.max(-lim, Math.min(lim, startA + dx));
                Config.islandY = Math.max(2, Math.min(this.height - 30, startB + dy));
            }
        }

        // ---- layout ----
        final int iw = PW - 2 * PAD;
        int n = Math.min(Config.targets.size(), 30);
        int[] chX = new int[n], chY = new int[n], chW = new int[n];
        int curX = 0, row = 0;
        for (int i = 0; i < n; i++) {
            int cw = textRenderer.getWidth(Config.targets.get(i).name) + 26;
            if (curX > 0 && curX + cw > iw) { curX = 0; row++; }
            chX[i] = curX; chY[i] = row * 20; chW[i] = cw;
            curX += cw + 4;
        }
        int rows = n == 0 ? 0 : row + 1;
        int chipH = rows * 20 + (rows > 0 ? 4 : 0);
        int bodyH = 6 + ROW * 5 + 32 + chipH + ROW * 3 + 6;

        bodyAnim += ((Config.panelCollapsed ? 0f : 1f) - bodyAnim) * kAnim;
        int panelH = HEADER + Math.round(bodyH * bodyAnim);
        int px = Config.panelX < 0 ? (this.width - PW) / 2
                : Math.max(0, Math.min(Config.panelX, this.width - PW));
        int py = Config.panelY < 0 ? Math.max(56, (this.height - (HEADER + bodyH)) / 2 + 10)
                : Math.max(0, Math.min(Config.panelY, this.height - HEADER));

        Glass.darkPanel(c, px, py, PW, panelH, 10);

        // ---- header ----
        Glass.fill(c, px + PAD, py + 10, 8, 8, 3, ACCENT, Glass.lerp(ACCENT, 0xFF000000, 0.3f));
        textB(c, "TPA Macro", px + PAD + 14, py + 10, 0xFFFFFFFF);
        String col = Config.panelCollapsed ? "+" : "-";
        textB(c, col, px + PW - PAD - textRenderer.getWidth(col) - 2, py + 10, 0xFFB0B0B8);
        if (bodyAnim > 0.02f) c.fill(px + PAD, py + HEADER - 1, px + PW - PAD, py + HEADER, 0x14FFFFFF);

        if (mClick && in(px + PW - 28, py, 28, HEADER)) {
            Config.panelCollapsed = !Config.panelCollapsed;
            Config.save();
        } else if (mClick && in(px, py, PW - 28, HEADER)) {
            drag = 4; pressX = mx; pressY = my;
            startA = px; startB = py;
            Config.panelX = px; Config.panelY = py;
        }
        if (drag == 4 && down) {
            Config.panelX = Math.max(0, Math.min(this.width - PW, startA + mx - pressX));
            Config.panelY = Math.max(0, Math.min(this.height - HEADER, startB + my - pressY));
        }

        // ---- body ----
        if (bodyAnim > 0.01f) {
            int bottom = py + panelH;
            c.enableScissor(px, py + HEADER, px + PW, bottom);
            clipBottom = bottom;
            int x = px + PAD;
            int y = py + HEADER + 6;

            if (rowToggle(c, x, y, iw, "Enabled", Macro.running, "en")) Macro.toggle(this.client);
            y += ROW;

            int h1 = rowSeg(c, x, y, iw, "Mode", MODES, anim("mode", Config.mode.equals("tpa") ? 1f : 0f));
            if (mClick && h1 >= 0) { Config.mode = h1 == 0 ? "tpahere" : "tpa"; Config.save(); }
            y += ROW;

            rowDelay(c, x, y, iw);
            y += ROW;

            rowRange(c, x, y, iw);
            y += ROW;

            int h2 = rowSeg(c, x, y, iw, "Auto-stop", STOPS, anim("stop", Config.stopMode.ordinal()));
            if (mClick && h2 >= 0) { Config.stopMode = Config.StopMode.values()[h2]; Config.save(); }
            y += ROW;

            // streamer input
            int fieldW = iw - 46;
            Glass.well(c, x, y + 6, fieldW, 20);
            field.setX(x + 10);
            field.setY(y + 12);
            field.setWidth(fieldW - 20);
            field.render(c, mx, my, delta);
            if (field.getText().isEmpty() && !field.isFocused()) {
                text(c, "Streamer names (space separated)", x + 10, y + 12, 0x66FFFFFF);
            }
            int ax = x + iw - 42;
            boolean addHover = in(ax, y + 6, 42, 20);
            int ac = addHover ? Glass.lerp(ACCENT, 0xFFFFFFFF, 0.15f) : ACCENT;
            Glass.fill(c, ax, y + 6, 42, 20, 10, ac, Glass.lerp(ac, 0xFF000000, 0.28f));
            Glass.stroke(c, ax, y + 6, 42, 20, 10, 0x50FFFFFF, 0x14FFFFFF);
            textB(c, "Add", ax + 21 - textRenderer.getWidth("Add") / 2 - 1, y + 12, 0xFFFFFFFF);
            if (mClick && addHover) addNames();
            else if (mClick && in(x, y + 6, fieldW, 20)) { this.setFocused(field); field.setFocused(true); }
            if (enter && field.isFocused()) addNames();
            y += 32;

            // chips
            int removeIdx = -1;
            for (int i = 0; i < n; i++) {
                Config.Target t = Config.targets.get(i);
                int cx2 = x + chX[i], cy2 = y + chY[i], cw2 = chW[i];
                boolean hov = in(cx2, cy2, cw2, 18);
                if (t.got) Glass.chip(c, cx2, cy2, cw2, 18, 0x34C759, hov ? 0.6f : 0.5f);
                else Glass.chip(c, cx2, cy2, cw2, 18, 0xFFFFFF, hov ? 0.2f : 0.12f);
                text(c, t.name, cx2 + 9, cy2 + 5, 0xFFFFFFFF);
                text(c, t.got ? "✓" : "×", cx2 + cw2 - 13, cy2 + 5, 0xFFD0D0D8);
                if (mClick && in(cx2 + cw2 - 20, cy2, 20, 18)) removeIdx = i;
            }
            if (removeIdx >= 0) { Config.targets.remove(removeIdx); Config.save(); }
            y += chipH;

            // island settings
            if (rowToggle(c, x, y, iw, "Island when idle", Config.islandShowIdle, "idle")) {
                Config.islandShowIdle = !Config.islandShowIdle;
                Config.save();
            }
            y += ROW;
            if (rowToggle(c, x, y, iw, "Island expanded", Config.islandExpanded, "exp")) {
                Config.islandExpanded = !Config.islandExpanded;
                Config.save();
            }
            y += ROW;
            rowBase(c, x, y, iw, "Move island");
            int rbx = x + iw - 46, rby = y + 4;
            boolean rh = in(rbx, rby, 46, 16);
            Glass.fill(c, rbx, rby, 46, 16, 8, rh ? 0x34FFFFFF : 0x1CFFFFFF, rh ? 0x24FFFFFF : 0x10FFFFFF);
            Glass.stroke(c, rbx, rby, 46, 16, 8, 0x38FFFFFF, 0x12FFFFFF);
            text(c, "Reset", rbx + 23 - textRenderer.getWidth("Reset") / 2, rby + 4, 0xFFE6E6EA);
            if (mClick && rh) { Config.islandOx = 0; Config.islandY = 6; Config.save(); }

            c.disableScissor();
            clipBottom = Integer.MAX_VALUE;
        }

        // ---- release handling ----
        if (!down) {
            if (released) {
                if (drag == 5 && !moved) Config.islandExpanded = !Config.islandExpanded;
                Config.save();
            }
            drag = 0;
        }

        // island on top of everything
        Island.render(c, this.client, true);
    }
}
