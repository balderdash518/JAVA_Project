package com.compilequest.entity;

import com.compilequest.core.Draw;
import com.compilequest.core.Theme;
import com.compilequest.level.LevelScreen;
import com.compilequest.world.TileMap;
import com.compilequest.world.TileType;

import java.awt.Font;
import java.awt.Graphics2D;

/** A walking "Bug": patrols a platform, turns at walls and edges, dies to one arrow. */
public final class Bug extends Entity {
    private static final int T = TileMap.T;
    public boolean fromRun;
    public int spawnAnim;
    private int dir = -1;
    private int t;
    private boolean onGround;

    public Bug(double x, double y) {
        this.w = 36;
        this.h = 28;
        this.x = x;
        this.y = y;
    }

    public void update(LevelScreen L) {
        t++;
        if (spawnAnim > 0) spawnAnim--;
        TileMap m = L.map;
        vy = Math.min(vy + 0.6, 10);
        vx = spawnAnim > 0 ? 0 : dir * 1.2;
        x += vx;
        int top = (int) Math.floor(y / T), bot = (int) Math.floor((y + h - 0.01) / T);
        int col = dir > 0 ? (int) Math.floor((x + w - 0.01) / T) : (int) Math.floor(x / T);
        for (int r = top; r <= bot; r++) {
            if (m.blocks(col, r)) {
                x = dir > 0 ? col * T - w : (col + 1) * T;
                dir = -dir;
                break;
            }
        }
        double prevBottom = y + h;
        y += vy;
        onGround = false;
        int row = (int) Math.floor((y + h - 0.01) / T);
        int l = (int) Math.floor(x / T), rr = (int) Math.floor((x + w - 0.01) / T);
        for (int c = l; c <= rr; c++) {
            if (m.blocks(c, row) || (m.oneWay(c, row) && prevBottom <= row * T + 0.5)) {
                y = row * T - h;
                vy = 0;
                onGround = true;
                break;
            }
        }
        if (onGround && spawnAnim == 0) {
            double fx = dir > 0 ? x + w + 2 : x - 2;
            int fc = (int) Math.floor(fx / T);
            int below = (int) Math.floor((y + h + 2) / T);
            int level = (int) Math.floor((y + h - 4) / T);
            TileType ahead = m.get(fc, level);
            boolean ground = m.blocks(fc, below) || m.oneWay(fc, below);
            if (!ground || ahead == TileType.SPIKE || ahead == TileType.WATER) dir = -dir;
        }
        if (y > m.h * T + 64) dead = true;
    }

    public void render(Graphics2D g, double camX, double camY) {
        double px = x - camX, py = y - camY;
        double pop = spawnAnim > 0 ? Draw.easeOutBack(1 - spawnAnim / 20.0) : 1;
        double bodyH = 19;
        double bw = w * pop, bh = bodyH * pop;
        double bx = px + (w - bw) / 2, by = py + bodyH - bh;
        Draw.glow(g, px + w / 2, py + h / 2, 30, Theme.BUG, 0.16);
        Draw.panel(g, bx, by, bw, bh, bh / 2, Draw.alpha(Theme.BUG, 0.2), Draw.alpha(Theme.BUG, 0.9));
        // antennae on the walking side
        double ax = dir > 0 ? bx + bw - 7 : bx + 7;
        Draw.line(g, ax, by + 2, ax + dir * 5, by - 5, Draw.alpha(Theme.BUG, 0.8), 1.3);
        Draw.line(g, ax - dir * 5, by + 1, ax - dir * 2, by - 6, Draw.alpha(Theme.BUG, 0.8), 1.3);
        Font f = Theme.bold(13);
        Draw.center(g, "Bug", bx + bw / 2, Draw.mid(f, by, bh), f, Draw.mix(Theme.BUG, java.awt.Color.WHITE, 0.25));
        Font legs = Theme.bold(12);
        String l = (t / 7) % 2 == 0 ? "^^^" : "vvv";
        if (vx == 0) l = "^^^";
        Draw.center(g, l, px + w / 2, py + h + 1, legs, Draw.alpha(Theme.BUG, 0.85));
    }
}
