package com.compilequest.ui;

import com.compilequest.core.Draw;
import com.compilequest.core.Game;
import com.compilequest.core.Input;
import com.compilequest.core.Sound.Sfx;
import com.compilequest.core.Theme;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;

/** The Esc menu: Resume, Restart level, Settings, Main menu. */
public final class PauseOverlay {
    public interface Actions {
        void resume();
        void restart();
        void settings();
        void menu();
    }

    private static final String[] ITEMS = {"Resume", "Restart level", "Settings", "Main menu"};
    private static final String[] KEYS = {"Esc", "R", null, null};
    private static final double W = 440, HEAD = 64, ROW = 52, FOOT = 46;

    private final Actions actions;
    private int sel;
    private int t;

    /** Set by the level when the panel's shadow is already part of the frozen background. */
    public boolean shadowBaked;

    public PauseOverlay(Actions actions) { this.actions = actions; }

    public void renderShadow(Graphics2D g) {
        double h = HEAD + ROW * ITEMS.length + FOOT;
        Draw.shadow(g, (Theme.W - W) / 2, top(), W, h, 12, 24, 0.55);
    }

    private double top() { return (Theme.H - (HEAD + ROW * ITEMS.length + FOOT)) / 2; }

    public void update(Game game) {
        t++;
        Input in = game.input;
        double x = (Theme.W - W) / 2, y = top() + HEAD;
        int hover = -1;
        for (int i = 0; i < ITEMS.length; i++) {
            if (in.mouseIn(x + 12, y + i * ROW, W - 24, ROW - 4)) hover = i;
        }
        if (hover >= 0 && hover != sel) {
            sel = hover;
            game.sound.play(Sfx.HOVER);
        }
        game.setCursor(hover >= 0 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR);
        if (in.pressedAny(KeyEvent.VK_W, KeyEvent.VK_UP)) {
            sel = (sel + ITEMS.length - 1) % ITEMS.length;
            game.sound.play(Sfx.HOVER);
        }
        if (in.pressedAny(KeyEvent.VK_S, KeyEvent.VK_DOWN)) {
            sel = (sel + 1) % ITEMS.length;
            game.sound.play(Sfx.HOVER);
        }
        if (in.pressed(KeyEvent.VK_ESCAPE)) {
            choose(game, 0);
        } else if (in.pressed(KeyEvent.VK_R)) {
            choose(game, 1);
        } else if (in.pressedAny(KeyEvent.VK_ENTER, KeyEvent.VK_SPACE) || (in.mousePressed(Input.LEFT) && hover >= 0)) {
            choose(game, sel);
        }
    }

    private void choose(Game game, int i) {
        game.sound.play(Sfx.CLICK);
        game.input.consume();
        game.setCursor(Cursor.DEFAULT_CURSOR);
        switch (i) {
            case 0 -> actions.resume();
            case 1 -> actions.restart();
            case 2 -> actions.settings();
            default -> actions.menu();
        }
    }

    public void render(Graphics2D g) {
        double a = Draw.easeOut(Math.min(1, t / 8.0));
        var old = Draw.pushAlpha(g, a);
        double h = HEAD + ROW * ITEMS.length + FOOT;
        double x = (Theme.W - W) / 2, y = top() + (1 - a) * 14;
        if (!shadowBaked) Draw.shadow(g, x, y, W, h, 12, 24, 0.55);
        Draw.panel(g, x, y, W, h, 12, Theme.PANEL, Theme.BORDER_STRONG);
        Font tf = Theme.bold(20);
        Draw.rect(g, x + 26, y + 24, 5, 18, Theme.TEXT_BRIGHT);
        Draw.rect(g, x + 35, y + 24, 5, 18, Theme.TEXT_BRIGHT);
        Draw.text(g, "Paused", x + 52, Draw.mid(tf, y + 12, 42), tf, Theme.TEXT_BRIGHT);
        Draw.rect(g, x, y + HEAD - 6, W, 1, Theme.BORDER);
        Font f = Theme.mono(16), kf = Theme.mono(13);
        for (int i = 0; i < ITEMS.length; i++) {
            double ry = y + HEAD + i * ROW;
            boolean on = i == sel;
            if (on) {
                Draw.panel(g, x + 12, ry, W - 24, ROW - 6, 7, Theme.SELECT, null);
                Draw.play(g, x + 28, ry + (ROW - 6) / 2 - 6, 12, Theme.TEXT_BRIGHT);
            }
            Draw.text(g, ITEMS[i], x + 50, Draw.mid(f, ry, ROW - 6), f, on ? Theme.TEXT_BRIGHT : Theme.TEXT);
            if (KEYS[i] != null) {
                double kw = Math.max(Draw.width(kf, KEYS[i]) + 12, 22);
                Draw.keycap(g, KEYS[i], x + W - 30 - kw, Draw.mid(kf, ry, ROW - 6), kf, Theme.TEXT);
            }
        }
        Font hf = Theme.mono(12);
        Draw.center(g, "W/S or mouse to choose, Enter to confirm", x + W / 2, y + h - 18, hf, Theme.TEXT_DIM);
        g.setComposite(old);
    }
}
