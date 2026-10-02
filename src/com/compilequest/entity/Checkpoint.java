package com.compilequest.entity;

import com.compilequest.core.Draw;
import com.compilequest.core.Theme;
import com.compilequest.world.TileMap;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;

/** A "try {" flag. Touching it saves the respawn point and refills Memory. */
public final class Checkpoint extends Entity {
    public boolean active;
    public final double spawnX, spawnY;
    private int t;
    private int activateAnim;

    public Checkpoint(int cellX, int cellY) {
        int T = TileMap.T;
        this.x = cellX * T + 2;
        this.y = cellY * T + T - 64;
        this.w = 28;
        this.h = 64;
        this.spawnX = cellX * T + (T - Player.W) / 2;
        this.spawnY = (cellY + 1) * T - Player.H;
    }

    public void activate() {
        active = true;
        activateAnim = 30;
    }

    public void update() {
        t++;
        if (activateAnim > 0) activateAnim--;
    }

    public void render(Graphics2D g, double camX, double camY) {
        double px = x - camX, py = y - camY;
        Color c = active ? Theme.CHECKPOINT : Theme.TEXT_DIM;
        double poleX = px + 5;
        if (active) Draw.glow(g, poleX + 26, py + 14, 46, Theme.CHECKPOINT, 0.22 + 0.06 * Math.sin(t * 0.06));
        Draw.panel(g, poleX - 6, py + h - 4, 14, 4, 2, Draw.alpha(c, 0.8), null);
        Draw.line(g, poleX, py + h - 3, poleX, py + 2, c, 2.2);
        Draw.circle(g, poleX, py + 2, 2.5, c);

        double raise = active ? 0 : 22;
        if (activateAnim > 0) raise = 22 * Draw.easeOut(activateAnim / 30.0);
        double fy = py + 5 + raise;
        double fw = 52, fh = 21;
        Path2D flag = new Path2D.Double();
        int seg = 10;
        for (int i = 0; i <= seg; i++) {
            double u = i / (double) seg;
            double wave = active ? Math.sin(t * 0.12 - u * 4) * 2.2 * u : 0;
            if (i == 0) flag.moveTo(poleX + 1, fy + wave);
            else flag.lineTo(poleX + 1 + fw * u, fy + wave);
        }
        for (int i = seg; i >= 0; i--) {
            double u = i / (double) seg;
            double wave = active ? Math.sin(t * 0.12 - u * 4) * 2.2 * u : 0;
            flag.lineTo(poleX + 1 + fw * u, fy + fh + wave);
        }
        flag.closePath();
        g.setColor(Draw.alpha(c, active ? 0.28 : 0.15));
        g.fill(flag);
        g.setColor(Draw.alpha(c, 0.9));
        g.setStroke(Draw.stroke(1.2));
        g.draw(flag);
        Font f = Theme.bold(12);
        double wave = active ? Math.sin(t * 0.12 - 2) * 1.1 : 0;
        Draw.text(g, "try {", poleX + 8, Draw.mid(f, fy + wave, fh), f, active ? Draw.mix(c, Color.WHITE, 0.35) : c);
    }
}
