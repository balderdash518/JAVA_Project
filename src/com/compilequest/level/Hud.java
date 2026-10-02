package com.compilequest.level;

import com.compilequest.core.Draw;
import com.compilequest.core.Theme;
import com.compilequest.editor.Syntax;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

/** The in-level overlay: Memory, task, block and star counters, pickup toasts and the intro card. */
final class Hud {
    private static final double BAR = 48;

    private record Toast(String text, Color color, boolean code, int[] life) { }

    private static final class Fly {
        final String text;
        final double sx, sy;
        int t;

        Fly(String text, double sx, double sy) {
            this.text = text;
            this.sx = sx;
            this.sy = sy;
        }
    }

    private final LevelScreen L;
    private final List<Toast> toasts = new ArrayList<>();
    private final List<Fly> flies = new ArrayList<>();
    private final int[] lost = new int[5];
    private int blocksBump;
    private int starBump;
    private int intro = 200;
    private int t;
    private double blocksX = Theme.W - 200;

    Hud(LevelScreen L) { this.L = L; }

    void toast(String text, Color color, boolean code) {
        toasts.add(0, new Toast(text, color, code, new int[] {150}));
        while (toasts.size() > 3) toasts.remove(toasts.size() - 1);
    }

    void loseMemory(int newHp) {
        if (newHp >= 0 && newHp < lost.length) lost[newHp] = 24;
    }

    void flyBlock(String text, double screenX, double screenY) {
        flies.add(new Fly(text, screenX, screenY));
    }

    void bumpStar() { starBump = 14; }

    void update() {
        t++;
        if (intro > 0) intro--;
        if (blocksBump > 0) blocksBump--;
        if (starBump > 0) starBump--;
        for (int i = 0; i < lost.length; i++) if (lost[i] > 0) lost[i]--;
        for (int i = toasts.size() - 1; i >= 0; i--) if (--toasts.get(i).life[0] <= 0) toasts.remove(i);
        for (int i = flies.size() - 1; i >= 0; i--) {
            if (++flies.get(i).t >= 34) {
                flies.remove(i);
                blocksBump = 12;
            }
        }
    }

    void render(Graphics2D g, boolean playing) {
        Draw.rect(g, 0, 0, Theme.W, BAR, Draw.alpha(Theme.BG_DEEP, 0.9));
        Draw.rect(g, 0, BAR, Theme.W, 1, Theme.BORDER);

        // Memory
        Font lf = Theme.mono(14);
        Draw.text(g, "Memory", 60, Draw.mid(lf, 0, BAR), lf, Theme.TEXT_2);
        double mx = 60 + Draw.width(lf, "Memory") + 12;
        int hp = Math.max(0, L.player.hp);
        Color full = hp >= 3 ? Theme.OK : Theme.ERR;
        double pulse = hp == 1 ? 0.55 + 0.45 * Math.sin(t * 0.15) : 1;
        for (int i = 0; i < 5; i++) {
            double shake = lost[i] > 0 ? Math.sin(lost[i] * 1.4) * 2.5 : 0;
            double x = mx + i * 24 + shake, y = 15;
            if (i < hp) {
                Draw.panel(g, x, y, 18, 18, 3, Draw.alpha(full, pulse), null);
                Draw.rect(g, x + 3, y + 3, 12, 3, Draw.alpha(Color.WHITE, 0.25 * pulse));
            } else {
                Draw.panel(g, x, y, 18, 18, 3, Draw.alpha(Theme.BG, 0.6), Theme.BORDER_STRONG);
                if (lost[i] > 0) Draw.panel(g, x, y, 18, 18, 3, Draw.alpha(Theme.ERR, lost[i] / 24.0), null);
            }
        }

        // File and task
        Font ff = Theme.bold(15), tf = Theme.mono(14);
        String file = L.cfg.file;
        String task = L.cfg.task;
        double tw = Draw.width(ff, file) + 30 + Draw.width(tf, task);
        double x0 = Theme.W / 2.0 - tw / 2;
        Draw.classIcon(g, x0 - 24, 16, 16, false);
        Draw.text(g, file, x0, Draw.mid(ff, 0, BAR), ff, Theme.TEXT_BRIGHT);
        Draw.circle(g, x0 + Draw.width(ff, file) + 15, BAR / 2, 2, Theme.TEXT_DIM);
        Draw.text(g, task, x0 + Draw.width(ff, file) + 30, Draw.mid(tf, 0, BAR), tf, L.solved ? Theme.OK : Theme.TEXT_2);

        // Right side: blocks, stars, key
        double rx = Theme.W - 24;
        Font cf = Theme.bold(15);
        String starsText = countStars() + "/" + Math.max(1, L.cfg.starCount);
        rx -= Draw.width(cf, starsText);
        Draw.text(g, starsText, rx, Draw.mid(cf, 0, BAR), cf, Theme.TEXT_BRIGHT);
        double sScale = 1 + 0.4 * Math.sin(starBump / 14.0 * Math.PI);
        rx -= 16;
        g.setColor(countStars() > 0 ? Theme.STAR : Theme.TEXT_DIM);
        g.fill(Draw.starShape(rx, BAR / 2, 8 * sScale, 0));
        rx -= 34;
        String blocks = String.valueOf(L.inventory.size());
        double bScale = 1 + 0.35 * Math.sin(blocksBump / 12.0 * Math.PI);
        rx -= Draw.width(cf, blocks);
        var saved = g.getTransform();
        g.translate(rx + Draw.width(cf, blocks) / 2, BAR / 2);
        g.scale(bScale, bScale);
        Draw.text(g, blocks, -Draw.width(cf, blocks) / 2, Draw.cap(cf) / 2, cf, Theme.TEXT_BRIGHT);
        g.setTransform(saved);
        rx -= 8 + Draw.width(lf, "Blocks");
        Draw.text(g, "Blocks", rx, Draw.mid(lf, 0, BAR), lf, Theme.TEXT_2);
        blocksX = rx + 30;
        if (L.cfg.hasKey) {
            String key = "hasKey = " + L.hasKey;
            rx -= 34 + Draw.width(lf, key);
            Draw.panel(g, rx - 10, 12, Draw.width(lf, key) + 20, 24, 5, Draw.alpha(L.hasKey ? Theme.KEY : Theme.PANEL, 0.25), L.hasKey ? Theme.KEY : Theme.BORDER);
            Syntax.draw(g, key, rx, Draw.mid(lf, 12, 24), lf, 1);
        }

        // Key hints, bottom right
        Font kf = Theme.mono(13);
        double hy = Theme.H - 20;
        double hx = Theme.W - 24;
        hx -= Draw.width(kf, "Menu");
        Draw.text(g, "Menu", hx, hy, kf, Theme.TEXT_2);
        hx -= 8 + Math.max(Draw.width(kf, "Esc") + 12, 22);
        Draw.keycap(g, "Esc", hx, hy, kf, Theme.TEXT);
        hx -= 22 + Draw.width(kf, "Editor");
        Draw.text(g, "Editor", hx, hy, kf, Theme.TEXT_2);
        hx -= 8 + Math.max(Draw.width(kf, "Tab") + 12, 22);
        Draw.keycap(g, "Tab", hx, hy, kf, Theme.TEXT);

        renderToasts(g);
        renderFlies(g);
        if (intro > 0 && playing) renderIntro(g);
    }

    private int countStars() {
        int n = 0;
        for (boolean s : L.stars) if (s) n++;
        return n;
    }

    private void renderToasts(Graphics2D g) {
        Font f = Theme.mono(14);
        double y = 72;
        for (Toast toast : toasts) {
            int life = toast.life()[0];
            double a = Math.min(1, life / 20.0) * Math.min(1, (150 - life) / 6.0 + 0.2);
            double w = Draw.width(f, toast.text()) + 40;
            double x = Theme.W / 2.0 - w / 2;
            var old = Draw.pushAlpha(g, a);
            Draw.shadow(g, x, y, w, 32, 8, 8, 0.35);
            Draw.panel(g, x, y, w, 32, 8, Draw.alpha(Theme.PANEL, 0.96), Draw.alpha(toast.color(), 0.6));
            Draw.rect(g, x + 10, y + 10, 3, 12, toast.color());
            if (toast.code()) {
                Draw.text(g, "+ ", x + 22, Draw.mid(f, y, 32), f, Theme.OK);
                Syntax.draw(g, toast.text().substring(2), x + 22 + Draw.width(f, "+ "), Draw.mid(f, y, 32), f, 1);
            } else {
                Draw.text(g, toast.text(), x + 22, Draw.mid(f, y, 32), f, Theme.TEXT_BRIGHT);
            }
            g.setComposite(old);
            y += 40;
        }
    }

    private void renderFlies(Graphics2D g) {
        Font f = Theme.mono(13);
        for (Fly fly : flies) {
            double k = Draw.easeInOut(fly.t / 34.0);
            double tx = blocksX, ty = 24;
            double x = Draw.lerp(fly.sx, tx, k);
            double y = Draw.lerp(fly.sy, ty, k) - Math.sin(k * Math.PI) * 60;
            double s = 1 - 0.6 * k;
            String text = fly.text.length() > 22 ? fly.text.substring(0, 21) + "..." : fly.text;
            double w = (Draw.width(f, text) + 16) * s, h = 22 * s;
            Draw.glow(g, x, y, 30, Theme.ACCENT, 0.4 * (1 - k));
            Draw.panel(g, x - w / 2, y - h / 2, w, h, 4, Theme.PICKUP_BG, Theme.ACCENT_HOVER);
            if (s > 0.6) {
                var saved = g.getTransform();
                g.translate(x, y);
                g.scale(s, s);
                Syntax.draw(g, text, -Draw.width(f, text) / 2, Draw.cap(f) / 2, f, 1);
                g.setTransform(saved);
            }
        }
    }

    private void renderIntro(Graphics2D g) {
        int age = 200 - intro;
        double a = Math.min(1, age / 20.0) * Math.min(1, intro / 30.0);
        double cy = 190;
        Draw.rect(g, 0, cy - 70, Theme.W, 140, Draw.alpha(Theme.BG_DEEP, 0.6 * a));
        Draw.rect(g, 0, cy - 70, Theme.W, 1, Draw.alpha(Theme.EXIT, 0.4 * a));
        Draw.rect(g, 0, cy + 69, Theme.W, 1, Draw.alpha(Theme.EXIT, 0.4 * a));
        Font big = Theme.bold(36);
        String file = L.cfg.file;
        int shown = Math.min(file.length(), Math.max(0, age - 6) / 2);
        String typed = file.substring(0, shown);
        double fw = Draw.width(big, file);
        double x = Theme.W / 2.0 - fw / 2;
        Draw.outlined(g, typed, x, cy - 8, big, Draw.alpha(Theme.TEXT_BRIGHT, a), Draw.alpha(Draw.RIM, a));
        if (shown < file.length() || (age / 15) % 2 == 0) {
            Draw.rect(g, x + Draw.width(big, typed) + 3, cy - 8 - Draw.cap(big), 14, Draw.cap(big) + 4, Draw.alpha(Theme.EXIT, a));
        }
        Font sf = Theme.mono(18);
        Draw.center(g, "// " + L.cfg.task, Theme.W / 2.0, cy + 30, sf, Draw.alpha(Theme.SYN_DOC, a));
        if (!L.cfg.news.isEmpty()) {
            Font nf = Theme.mono(14);
            Draw.center(g, "new: " + String.join(", ", L.cfg.news), Theme.W / 2.0, cy + 56, nf, Draw.alpha(Theme.TEXT_2, a));
        }
    }
}
