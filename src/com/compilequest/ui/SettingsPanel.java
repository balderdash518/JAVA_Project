package com.compilequest.ui;

import com.compilequest.core.Draw;
import com.compilequest.core.Game;
import com.compilequest.core.Input;
import com.compilequest.core.Sound.Sfx;
import com.compilequest.core.Theme;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.geom.Rectangle2D;

/** settings.json shown as an editable JSON file (DESIGN.md 3.8). */
public final class SettingsPanel {
    public static final double LINE = 40;
    private static final String[] KEYS = {"\"sfxVolume\":", "\"fullscreen\":", "\"showDebug\":", "\"resetSave\":"};
    private static final double VALUE_X = 230, CELL = 18;

    private final Game game;
    private final Runnable onReset;
    private int sel;
    private int hover = -1;
    private double x, y;

    public SettingsPanel(Game game, Runnable onReset) {
        this.game = game;
        this.onReset = onReset;
    }

    public double height() { return LINE * 6; }

    /** Changes whenever the panel would look different (used by the menu's picture cache). */
    public String stateKey() { return sel + "," + hover; }

    private double rowY(int i) { return y + (i + 1) * LINE; }

    private Rectangle2D.Double rowRect(int i) { return new Rectangle2D.Double(x, rowY(i), 620, LINE); }

    private Rectangle2D.Double cell(int k) {
        return new Rectangle2D.Double(x + VALUE_X + 26 + k * (CELL + 4), rowY(0) + (LINE - CELL) / 2, CELL, CELL);
    }

    private Rectangle2D.Double arrow(boolean right) {
        double cx = right ? x + VALUE_X + 26 + 10 * (CELL + 4) + 4 : x + VALUE_X;
        return new Rectangle2D.Double(cx, rowY(0) + 6, 20, LINE - 12);
    }

    private Rectangle2D.Double box(int i) { return new Rectangle2D.Double(x + VALUE_X, rowY(i) + 10, 20, 20); }

    private Rectangle2D.Double resetButton() { return new Rectangle2D.Double(x + VALUE_X, rowY(3) + 6, 96, LINE - 12); }

    /** Handles mouse and (when keyboard is true) keys. Returns true if the cursor is over something clickable. */
    public boolean update(Input in, boolean keyboard) {
        double mx = in.mouseX, my = in.mouseY;
        hover = -1;
        for (int i = 0; i < 4; i++) if (rowRect(i).contains(mx, my)) hover = i;
        boolean clickable = false;
        if (in.mousePressed(Input.LEFT)) {
            for (int k = 0; k < 10; k++) {
                if (cell(k).contains(mx, my)) {
                    setVolume((k + 1) / 10.0);
                    sel = 0;
                }
            }
            if (arrow(false).contains(mx, my)) adjustVolume(-1);
            if (arrow(true).contains(mx, my)) adjustVolume(1);
            if (box(1).contains(mx, my) || (hover == 1 && mx < x + VALUE_X)) toggle(1);
            if (box(2).contains(mx, my) || (hover == 2 && mx < x + VALUE_X)) toggle(2);
            if (resetButton().contains(mx, my)) toggle(3);
            if (hover >= 0) sel = hover;
        }
        for (int k = 0; k < 10; k++) clickable |= cell(k).contains(mx, my);
        clickable |= arrow(false).contains(mx, my) || arrow(true).contains(mx, my) || box(1).contains(mx, my)
                || box(2).contains(mx, my) || resetButton().contains(mx, my);
        if (keyboard) {
            if (in.pressedAny(KeyEvent.VK_W, KeyEvent.VK_UP)) sel = (sel + 3) % 4;
            if (in.pressedAny(KeyEvent.VK_S, KeyEvent.VK_DOWN)) sel = (sel + 1) % 4;
            if (sel == 0) {
                if (in.pressedAny(KeyEvent.VK_A, KeyEvent.VK_LEFT)) adjustVolume(-1);
                if (in.pressedAny(KeyEvent.VK_D, KeyEvent.VK_RIGHT)) adjustVolume(1);
            } else if (in.pressedAny(KeyEvent.VK_ENTER, KeyEvent.VK_SPACE)) {
                toggle(sel);
            }
        }
        return clickable;
    }

    private void adjustVolume(int dir) {
        setVolume(Math.round(game.save.sfxVolume * 10 + dir) / 10.0);
    }

    private void setVolume(double v) {
        v = Draw.clamp(v, 0, 1);
        game.save.sfxVolume = v;
        game.sound.setVolume((float) v);
        game.save.write();
        game.sound.play(Sfx.PICKUP, 0.8f, 1f);
    }

    private void toggle(int i) {
        game.sound.play(Sfx.CLICK);
        switch (i) {
            case 1 -> game.setFullscreen(!game.save.fullscreen);
            case 2 -> {
                game.save.showDebug = !game.save.showDebug;
                game.save.write();
            }
            case 3 -> onReset.run();
            default -> { }
        }
    }

    public void render(Graphics2D g, double px, double py, boolean focused) {
        this.x = px;
        this.y = py;
        Font f = Theme.mono(15);
        Font lf = Theme.mono(14);
        String[] lines = {"{", "", "", "", "", "}"};
        for (int i = 0; i < 6; i++) {
            double ry = y + i * LINE;
            double base = Draw.mid(f, ry, LINE);
            Draw.right(g, String.valueOf(i + 1), x - 18, base, lf, Theme.LINE_NO);
            if (i >= 1 && i <= 4) {
                int row = i - 1;
                if (row == sel && focused) Draw.panel(g, x - 8, ry + 2, 640, LINE - 4, 6, SELECTED, null);
                else if (row == hover) Draw.panel(g, x - 8, ry + 2, 640, LINE - 4, 6, Theme.HOVER, null);
                Draw.text(g, "  " + KEYS[row], x, base, f, Theme.SYN_FIELD);
                renderValue(g, row, base);
            } else {
                Draw.text(g, lines[i], x, base, f, Theme.SYN_CODE);
            }
        }
    }

    private static final java.awt.Color SELECTED = Draw.alpha(Theme.SELECT, 0.8);

    private void renderValue(Graphics2D g, int row, double base) {
        Font f = Theme.mono(15), cf = Theme.mono(13);
        switch (row) {
            case 0 -> {
                Rectangle2D.Double l = arrow(false), r = arrow(true);
                Draw.center(g, "<", l.getCenterX(), base, f, Theme.TEXT);
                Draw.center(g, ">", r.getCenterX(), base, f, Theme.TEXT);
                int on = (int) Math.round(game.save.sfxVolume * 10);
                for (int k = 0; k < 10; k++) {
                    Rectangle2D.Double c = cell(k);
                    Draw.panel(g, c.x, c.y, c.width, c.height, 3, k < on ? Theme.SYN_NUMBER : Draw.alpha(Theme.BG, 0.6),
                            k < on ? null : Theme.BORDER_STRONG);
                }
                Draw.text(g, Math.round(game.save.sfxVolume * 100) + "%", r.x + 34, base, f, Theme.SYN_NUMBER);
            }
            case 1, 2 -> {
                boolean v = row == 1 ? game.save.fullscreen : game.save.showDebug;
                Rectangle2D.Double b = box(row);
                Draw.panel(g, b.x, b.y, b.width, b.height, 4, v ? Theme.ACCENT : null, v ? null : Theme.BORDER_STRONG);
                if (v) Draw.check(g, b.x + 4, b.y + 5, 12, java.awt.Color.WHITE);
                Draw.text(g, String.valueOf(v), b.x + 34, base, f, Theme.SYN_KEYWORD);
                if (row == 2) Draw.text(g, "// F1 also toggles this", b.x + 110, base, cf, Theme.SYN_COMMENT);
            }
            default -> {
                Rectangle2D.Double b = resetButton();
                boolean h = hover == 3;
                Draw.panel(g, b.x, b.y, b.width, b.height, 6, h ? Draw.alpha(Theme.ERR, 0.2) : null, Draw.alpha(Theme.ERR, 0.8));
                Font bf = Theme.bold(13);
                Draw.center(g, "Reset", b.getCenterX(), Draw.mid(bf, b.y, b.height), bf, Theme.ERR);
                Draw.text(g, "// asks for confirmation", b.x + b.width + 16, base, cf, Theme.SYN_COMMENT);
            }
        }
    }
}
