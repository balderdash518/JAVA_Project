package com.compilequest.entity;

import com.compilequest.core.Draw;
import com.compilequest.core.Theme;
import com.compilequest.world.TileMap;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;

/** A "while(true)" platform that loops between two points forever. One-way: land on it from above. */
public final class MovingPlatform extends Entity {
    private static final String WORD = "while(true)";
    private final double sx, sy, ex, ey;
    private final int period;
    private int t;
    public double dx, dy;

    public MovingPlatform(int cellX, int cellY, int widthTiles, double dxTiles, double dyTiles, int period) {
        int T = TileMap.T;
        this.sx = cellX * T;
        this.sy = cellY * T;
        this.ex = sx + dxTiles * T;
        this.ey = sy + dyTiles * T;
        this.period = Math.max(30, period);
        this.x = sx;
        this.y = sy;
        this.w = widthTiles * T;
        this.h = 16;
    }

    public double top() { return y; }

    /** A static copy at one end of the path (used by the map checker). */
    public MovingPlatform at(double k) {
        MovingPlatform p = new MovingPlatform(0, 0, (int) Math.round(w / TileMap.T), 0, 0, period);
        p.x = sx + (ex - sx) * k;
        p.y = sy + (ey - sy) * k;
        return p;
    }

    public void update() {
        t++;
        double k = 0.5 - 0.5 * Math.cos(2 * Math.PI * t / period);
        double nx = sx + (ex - sx) * k, ny = sy + (ey - sy) * k;
        dx = nx - x;
        dy = ny - y;
        x = nx;
        y = ny;
    }

    public void render(Graphics2D g, double camX, double camY) {
        double px = x - camX, py = y - camY;
        Draw.glow(g, px + w / 2, py + h / 2, w * 0.6, Theme.MOVER, 0.12);
        Draw.panel(g, px, py, w, h, 5, Draw.alpha(Theme.MOVER, 0.22), Draw.alpha(Theme.MOVER, 0.9));
        Draw.rect(g, px + 4, py, w - 8, 2, Draw.mix(Theme.MOVER, Color.WHITE, 0.35));
        Font f = Theme.bold(12);
        String s = WORD;
        double tw = Draw.width(f, s);
        while (tw + Draw.width(f, " " + WORD) < w - 12) {
            s = s + " " + WORD;
            tw = Draw.width(f, s);
        }
        if (tw > w - 8) {
            s = "while";
            tw = Draw.width(f, s);
        }
        Draw.text(g, s, px + (w - tw) / 2, Draw.mid(f, py + 1, h), f, Draw.mix(Theme.MOVER, Color.WHITE, 0.4));
        // direction chevrons at both ends
        double dirX = Math.signum(dx), dirY = Math.signum(dy);
        if (dirX != 0 || dirY != 0) {
            Font cf = Theme.bold(11);
            String arrow = dirX > 0 ? ">" : dirX < 0 ? "<" : dirY > 0 ? "v" : "^";
            Draw.text(g, arrow, px + 3, Draw.mid(cf, py + 1, h), cf, Draw.alpha(Theme.MOVER, 0.8));
            Draw.text(g, arrow, px + w - 9, Draw.mid(cf, py + 1, h), cf, Draw.alpha(Theme.MOVER, 0.8));
        }
    }
}
