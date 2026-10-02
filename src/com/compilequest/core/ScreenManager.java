package com.compilequest.core;

import java.awt.Color;
import java.awt.Graphics2D;

/** Holds the current screen and fades through black when switching. */
public final class ScreenManager {
    private static final int FADE = 14;

    private final Game game;
    private Screen current;
    private Screen pending;
    private int fade;
    private int phase; // 0 idle, 1 fading out, 2 fading in

    public ScreenManager(Game game) { this.game = game; }

    public void set(Screen s) {
        current = s;
        s.enter(game);
    }

    public void go(Screen s) {
        if (phase == 1) return;
        pending = s;
        phase = 1;
        fade = 0;
    }

    public Screen current() { return current; }

    public boolean transitioning() { return phase != 0; }

    public void update() {
        if (phase == 1) {
            fade++;
            if (fade >= FADE) {
                current = pending;
                pending = null;
                current.enter(game);
                phase = 2;
                game.input.consume();
            }
            return;
        }
        if (phase == 2 && --fade <= 0) {
            fade = 0;
            phase = 0;
        }
        if (current != null) current.update(game);
    }

    public void render(Graphics2D g) {
        if (current != null) current.render(g);
        if (phase != 0) {
            double a = Draw.easeInOut(fade / (double) FADE);
            g.setColor(new Color(0, 0, 0, (int) (255 * a)));
            g.fillRect(0, 0, Theme.W, Theme.H);
        }
    }
}
