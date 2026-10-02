package com.compilequest.entity;

import com.compilequest.core.Draw;
import com.compilequest.core.Theme;
import com.compilequest.level.LevelScreen;
import com.compilequest.world.TileMap;

import java.awt.Font;
import java.awt.Graphics2D;

/** A "throw" thrown by an Exception. An arrow can catch it. */
public final class ThrowShot extends Entity {
    private final int dir;
    private double dist;

    public ThrowShot(double x, double y, int dir) {
        this.x = x;
        this.y = y;
        this.w = 56;
        this.h = 18;
        this.dir = dir;
        this.vx = 5 * dir;
    }

    public void update(LevelScreen L) {
        x += vx;
        dist += Math.abs(vx);
        double nx = dir > 0 ? x + w : x;
        int tx = (int) Math.floor(nx / TileMap.T), ty = (int) Math.floor((y + h / 2) / TileMap.T);
        if (L.map.blocks(tx, ty) || dist > 900) {
            dead = true;
            L.particles.burst(nx, y + h / 2, "throw", Theme.ERR, 5, 2.5, 12);
        }
    }

    public void render(Graphics2D g, double camX, double camY) {
        Font f = Theme.boldItalic(16);
        double px = x - camX, base = Draw.mid(f, y - camY, h);
        for (int k = 3; k >= 1; k--) {
            Draw.text(g, "throw", px - dir * k * 9, base, f, Draw.alpha(Theme.ERR, 0.1 * (4 - k)));
        }
        Draw.glow(g, px + w / 2, y - camY + h / 2, 40, Theme.ERR, 0.25);
        Draw.outlined(g, "throw", px, base, f, Theme.ERR, Draw.RIM);
    }
}
