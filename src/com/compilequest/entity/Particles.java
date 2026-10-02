package com.compilequest.entity;

import com.compilequest.core.Draw;
import com.compilequest.core.Theme;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.Random;

/** Character and dot particles: dust, sparks, shattered words. */
public final class Particles {
    private static final int MAX = 700;

    private static final class P {
        double x, y, vx, vy, g, drag = 0.985, rot, vr;
        int life, max;
        char c;
        Color color;
        double size;
        boolean dot;
    }

    private final ArrayList<P> list = new ArrayList<>();
    private final Random rng = new Random();

    public Random rng() { return rng; }

    private P add() {
        if (list.size() >= MAX) list.remove(0);
        P p = new P();
        list.add(p);
        return p;
    }

    public void glyph(double x, double y, char c, Color col, double vx, double vy, int life, double size, double gravity) {
        P p = add();
        p.x = x;
        p.y = y;
        p.vx = vx;
        p.vy = vy;
        p.c = c;
        p.color = col;
        p.life = p.max = life;
        p.size = size;
        p.g = gravity;
        p.vr = (rng.nextDouble() - 0.5) * 0.3;
    }

    public void dot(double x, double y, Color col, double vx, double vy, int life, double size, double gravity) {
        P p = add();
        p.x = x;
        p.y = y;
        p.vx = vx;
        p.vy = vy;
        p.color = col;
        p.life = p.max = life;
        p.size = size;
        p.g = gravity;
        p.dot = true;
    }

    /** Throws the characters of a word in every direction (used when things break). */
    public void burst(double x, double y, String chars, Color col, int n, double speed, double size) {
        for (int i = 0; i < n; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            double s = speed * (0.4 + rng.nextDouble() * 0.8);
            char c = chars.charAt(i % chars.length());
            glyph(x + (rng.nextDouble() - 0.5) * 10, y + (rng.nextDouble() - 0.5) * 10, c, col,
                    Math.cos(a) * s, Math.sin(a) * s - 1.5, 30 + rng.nextInt(25), size, 0.18);
        }
    }

    public void dust(double x, double y, int n) {
        for (int i = 0; i < n; i++) {
            char c = ".,'`".charAt(rng.nextInt(4));
            glyph(x + (rng.nextDouble() - 0.5) * 14, y - 3, c, Theme.TEXT_2,
                    (rng.nextDouble() - 0.5) * 1.6, -rng.nextDouble() * 1.2, 18 + rng.nextInt(10), 13, 0.04);
        }
    }

    /** A flat ring of dots under the feet, used for the double jump. */
    public void ring(double x, double y, Color col) {
        for (int i = 0; i < 14; i++) {
            double a = i * Math.PI * 2 / 14;
            dot(x, y, col, Math.cos(a) * 3.2, Math.sin(a) * 1.1 + 0.6, 16 + rng.nextInt(6), 1.8, 0.02);
        }
    }

    public void sparks(double x, double y, Color col, int n) {
        for (int i = 0; i < n; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            double s = 1 + rng.nextDouble() * 3;
            dot(x, y, col, Math.cos(a) * s, Math.sin(a) * s, 14 + rng.nextInt(12), 1.5 + rng.nextDouble() * 1.5, 0.08);
        }
    }

    public void sparkle(double x, double y, Color col) {
        glyph(x + (rng.nextDouble() - 0.5) * 30, y + (rng.nextDouble() - 0.5) * 30, rng.nextBoolean() ? '*' : '+', col,
                0, -0.3 - rng.nextDouble() * 0.4, 30 + rng.nextInt(20), 11 + rng.nextInt(4), 0);
    }

    public void update() {
        for (int i = list.size() - 1; i >= 0; i--) {
            P p = list.get(i);
            p.vy += p.g;
            p.vx *= p.drag;
            p.vy *= p.drag;
            p.x += p.vx;
            p.y += p.vy;
            p.rot += p.vr;
            if (--p.life <= 0) {
                int last = list.size() - 1;
                list.set(i, list.get(last));
                list.remove(last);
            }
        }
    }

    public void clear() { list.clear(); }

    public void render(Graphics2D g, double camX, double camY) {
        Ellipse2D.Double e = new Ellipse2D.Double();
        for (P p : list) {
            double t = p.life / (double) p.max;
            double a = Math.min(1, t * 1.6);
            double x = p.x - camX, y = p.y - camY;
            if (x < -40 || y < -40 || x > Theme.W + 40 || y > Theme.H + 40) continue;
            g.setColor(Draw.alpha(p.color, a));
            if (p.dot) {
                double s = p.size * (0.5 + 0.5 * t);
                e.setFrame(x - s, y - s, s * 2, s * 2);
                g.fill(e);
            } else {
                Font f = Theme.bold(Math.round(p.size));
                g.setFont(f);
                g.drawString(Draw.ch(p.c), (float) (x - Draw.adv(f) / 2), (float) (y + Draw.cap(f) / 2));
            }
        }
    }
}
