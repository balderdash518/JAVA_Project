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

/** "BUILD SUCCESSFUL" level-complete card with stats (DESIGN.md 3.7). */
public final class ClearOverlay {
    private static final double W = 680, H = 470;

    private final String file;
    private final long ms;
    private final boolean newBest;
    private final boolean[] stars;
    private final int starCount, runs, fails, hp;
    private final boolean hasNext;
    private final Runnable next, menu;
    private final Button nextButton, menuButton;
    private int t;

    public ClearOverlay(String file, long ms, boolean newBest, boolean[] stars, int starCount, int runs, int fails, int hp,
                        boolean hasNext, Runnable next, Runnable menu) {
        this.file = file;
        this.ms = ms;
        this.newBest = newBest;
        this.stars = stars;
        this.starCount = Math.max(starCount, 1);
        this.runs = runs;
        this.fails = fails;
        this.hp = hp;
        this.hasNext = hasNext;
        this.next = next;
        this.menu = menu;
        this.nextButton = new Button("Next level", "Enter", Button.Style.PRIMARY);
        this.menuButton = new Button("Main menu", hasNext ? "Esc" : "Enter", hasNext ? Button.Style.SECONDARY : Button.Style.PRIMARY);
    }

    /** Set by the level when the panel's shadow is already part of the frozen background. */
    public boolean shadowBaked;

    public void renderShadow(Graphics2D g) {
        Draw.shadow(g, (Theme.W - W) / 2, (Theme.H - H) / 2, W, H, 14, 26, 0.6);
    }

    public static String time(long ms) {
        long s = ms / 1000;
        return s >= 60 ? (s / 60) + "m " + (s % 60) + "s" : s + "s";
    }

    private int starLitAt(int i) { return 40 + i * 18; }

    public void update(Game game) {
        t++;
        Input in = game.input;
        for (int i = 0; i < 3; i++) {
            if (t == starLitAt(i) && stars[i]) game.sound.play(Sfx.STAR, 0.6f, 1f + i * 0.12f);
        }
        double x = (Theme.W - W) / 2, y = (Theme.H - H) / 2;
        if (hasNext) {
            nextButton.at(x + W / 2 - 10 - 220, y + H - 78, 220, 48);
            menuButton.at(x + W / 2 + 10, y + H - 78, 220, 48);
        } else {
            menuButton.at(x + W / 2 - 120, y + H - 78, 240, 48);
        }
        nextButton.hover = hasNext && nextButton.contains(in.mouseX, in.mouseY);
        menuButton.hover = menuButton.contains(in.mouseX, in.mouseY);
        game.setCursor(nextButton.hover || menuButton.hover ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR);
        if (t < 30) return;
        boolean click = in.mousePressed(Input.LEFT);
        if (hasNext && (in.pressed(KeyEvent.VK_ENTER) || (click && nextButton.hover))) {
            game.sound.play(Sfx.CLICK);
            game.setCursor(Cursor.DEFAULT_CURSOR);
            next.run();
        } else if (in.pressed(KeyEvent.VK_ESCAPE) || (!hasNext && in.pressed(KeyEvent.VK_ENTER)) || (click && menuButton.hover)) {
            game.sound.play(Sfx.CLICK);
            game.setCursor(Cursor.DEFAULT_CURSOR);
            menu.run();
        }
    }

    public void render(Graphics2D g) {
        double a = Draw.easeOut(Math.min(1, t / 14.0));
        var old = Draw.pushAlpha(g, a);
        double x = (Theme.W - W) / 2, y = (Theme.H - H) / 2 + (1 - a) * 20;
        Draw.glow(g, Theme.W / 2.0, y + 70, 380, Theme.OK, 0.16);
        if (!shadowBaked) Draw.shadow(g, x, y, W, H, 14, 26, 0.6);
        Draw.panel(g, x, y, W, H, 14, Theme.PANEL, Draw.alpha(Theme.OK, 0.6));
        Draw.rect(g, x + 1, y + 1, W - 2, 3, Theme.OK);

        Font tf = Theme.bold(32);
        String title = hasNext ? "BUILD SUCCESSFUL" : "ALL LEVELS COMPILED";
        Draw.center(g, title, x + W / 2, y + 74, tf, Theme.OK);
        Font sub = Theme.mono(16);
        Draw.center(g, "in " + time(ms), x + W / 2, y + 104, sub, Theme.TEXT_2);

        Font ff = Theme.bold(17);
        double fw = Draw.width(ff, file) + 30;
        double fx = x + W / 2 - fw / 2;
        Draw.classIcon(g, fx, y + 132, 18, false);
        Draw.text(g, file, fx + 28, y + 147, ff, Theme.TEXT_BRIGHT);
        Draw.check(g, fx + fw + 8, y + 135, 14, Theme.OK);
        Draw.rect(g, x + 60, y + 172, W - 120, 1, Theme.BORDER);

        Font lf = Theme.mono(16), vf = Theme.bold(16);
        double lx = x + 150, vx = x + 380, ry = y + 212;
        Draw.text(g, "Hidden stars", lx, ry, lf, Theme.TEXT_2);
        for (int i = 0; i < 3; i++) {
            boolean lit = stars[i] && t >= starLitAt(i);
            double pop = lit ? Draw.easeOutBack(Math.min(1, (t - starLitAt(i)) / 12.0)) : 1;
            double sx = vx + 12 + i * 34, sy = ry - 6;
            if (i >= starCount) continue;
            if (lit) {
                Draw.glow(g, sx, sy, 22, Theme.STAR, 0.4);
                g.setColor(Theme.STAR);
                g.fill(Draw.starShape(sx, sy, 12 * pop, 0));
            } else {
                Draw.star(g, sx, sy, 12, false, Theme.TEXT_DIM);
            }
        }
        ry += 40;
        Draw.text(g, "Runs", lx, ry, lf, Theme.TEXT_2);
        Draw.text(g, runs + (fails > 0 ? "  (" + fails + " failed)" : "  (first try)"), vx, ry, vf, fails == 0 ? Theme.OK : Theme.TEXT_BRIGHT);
        ry += 40;
        Draw.text(g, "Memory left", lx, ry, lf, Theme.TEXT_2);
        for (int i = 0; i < 5; i++) {
            Color c = i < hp ? (hp >= 3 ? Theme.OK : Theme.ERR) : null;
            Draw.panel(g, vx + i * 24, ry - 15, 18, 18, 3, c, c == null ? Theme.BORDER_STRONG : null);
        }
        ry += 40;
        Draw.text(g, "Time", lx, ry, lf, Theme.TEXT_2);
        Draw.text(g, time(ms), vx, ry, vf, Theme.TEXT_BRIGHT);
        if (newBest) {
            Font nf = Theme.bold(12);
            double nx = vx + Draw.width(vf, time(ms)) + 14;
            Draw.panel(g, nx, ry - 15, Draw.width(nf, "NEW BEST") + 14, 20, 4, Draw.alpha(Theme.STAR, 0.2), Theme.STAR);
            Draw.text(g, "NEW BEST", nx + 7, ry - 1, nf, Theme.STAR);
        }

        if (hasNext) nextButton.render(g);
        menuButton.render(g);
        g.setComposite(old);
    }
}
