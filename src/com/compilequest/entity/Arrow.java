package com.compilequest.entity;

import com.compilequest.core.Draw;
import com.compilequest.core.Theme;
import com.compilequest.level.LevelScreen;
import com.compilequest.world.TileMap;
import com.compilequest.world.TileType;

import java.awt.Font;
import java.awt.Graphics2D;

/** The player's "->" arrow: flies straight, breaks break blocks, fixes Bugs. */
public final class Arrow extends Entity {
    public static final double SPEED = 12, RANGE = 600;
    private final int dir;
    private double dist;

    public Arrow(double x, double y, int dir) {
        this.x = x;
        this.y = y;
        this.w = 24;
        this.h = 10;
        this.dir = dir;
        this.vx = SPEED * dir;
    }

    public void update(LevelScreen L) {
        x += vx;
        dist += SPEED;
        double nx = dir > 0 ? x + w : x, ny = y + h / 2;
        if (dist > RANGE) {
            dead = true;
            L.particles.sparks(nx, ny, Theme.ARROW, 4);
            return;
        }
        int tx = (int) Math.floor(nx / TileMap.T), ty = (int) Math.floor(ny / TileMap.T);
        if (L.map.blocks(tx, ty)) {
            dead = true;
            if (L.map.get(tx, ty) == TileType.BREAK) L.breakTile(tx, ty);
            else L.particles.sparks(nx, ny, Theme.ARROW, 6);
        }
    }

    public void render(Graphics2D g, double camX, double camY) {
        Font f = Theme.bold(18);
        String s = dir > 0 ? "->" : "<-";
        double bx = x - camX, by = Draw.mid(f, y - camY, h);
        for (int k = 3; k >= 1; k--) {
            Draw.text(g, s, bx - dir * k * 7, by, f, Draw.alpha(Theme.ARROW, 0.12 * (4 - k)));
        }
        Draw.glow(g, bx + w / 2, y - camY + h / 2, 22, Theme.ARROW, 0.25);
        Draw.outlined(g, s, bx, by, f, Theme.ARROW, Draw.RIM);
    }
}
