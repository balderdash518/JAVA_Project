package com.compilequest.entity;

import com.compilequest.core.Draw;
import com.compilequest.core.Theme;

import java.awt.Font;
import java.awt.Graphics2D;

/** A compiler error pinned in the world by a failed program; it stays until the next run. */
public final class ErrorText {
    private final String text;
    private final double x, y;
    private int t;

    public ErrorText(String text, double x, double y) {
        this.text = text;
        this.x = x;
        this.y = y;
    }

    public void update() { t++; }

    public void render(Graphics2D g, double camX, double camY) {
        Font f = Theme.bold(14);
        double w = Draw.width(f, text) + 34;
        double h = 30;
        double appear = Draw.easeOutBack(Math.min(1, t / 14.0));
        double px = x - camX - w / 2, py = y - camY - h / 2 - (1 - appear) * 10;
        double pulse = 0.5 + 0.5 * Math.sin(t * 0.12);
        Draw.glow(g, px + w / 2, py + h / 2, w * 0.7, Theme.ERR, 0.18 + 0.12 * pulse);
        Draw.panel(g, px, py, w, h, 6, Draw.alpha(Theme.BG_DEEP, 0.92 * appear), Draw.alpha(Theme.ERR, (0.6 + 0.4 * pulse) * appear));
        Draw.circle(g, px + 14, py + h / 2, 4, Draw.alpha(Theme.ERR, appear));
        Draw.text(g, text, px + 25, Draw.mid(f, py, h), f, Draw.alpha(Theme.ERR, appear));
    }
}
