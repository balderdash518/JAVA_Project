package com.compilequest.entity;

import com.compilequest.core.Draw;
import com.compilequest.core.Theme;
import com.compilequest.world.TileMap;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;

/** The "javac" terminal: the only place where a program can be run. */
public final class Terminal extends Entity {
    public boolean near;
    private int t;
    private double nearAnim;

    public Terminal(int cellX, int cellY) {
        int T = TileMap.T;
        this.x = cellX * T;
        this.y = (cellY - 1) * T;
        this.w = 2 * T;
        this.h = 2 * T;
    }

    /** True when the player stands close enough to use it. */
    public boolean inReach(Player p) {
        return p.x + Player.W > x - 30 && p.x < x + w + 30 && p.y + Player.H > y && p.y < y + h + 8;
    }

    public void update() {
        t++;
        nearAnim += ((near ? 1 : 0) - nearAnim) * 0.15;
    }

    public void render(Graphics2D g, double camX, double camY) {
        double px = x - camX, py = y - camY;
        Draw.glow(g, px + w / 2, py + 24, 58, Theme.ACCENT, 0.14 + 0.25 * nearAnim);
        // stand
        Path2D stand = new Path2D.Double();
        stand.moveTo(px + w / 2 - 6, py + 46);
        stand.lineTo(px + w / 2 + 6, py + 46);
        stand.lineTo(px + w / 2 + 14, py + h - 2);
        stand.lineTo(px + w / 2 - 14, py + h - 2);
        stand.closePath();
        g.setColor(Theme.BORDER_STRONG);
        g.fill(stand);
        Draw.rect(g, px + w / 2 - 18, py + h - 3, 36, 3, Theme.BORDER_STRONG);
        // monitor
        Color frame = Draw.mix(Theme.ACCENT, Color.WHITE, 0.2 * nearAnim);
        Draw.panel(g, px + 1, py + 2, w - 2, 46, 7, new Color(0x16181B), frame);
        Draw.panel(g, px + 5, py + 6, w - 10, 38, 4, new Color(0x0F1A14), null);
        Font f = Theme.bold(11);
        Draw.text(g, "$ javac", px + 9, py + 21, f, Theme.OK);
        if ((t / 30) % 2 == 0) Draw.rect(g, px + 9, py + 27, 7, 9, Theme.OK);
        // scan line
        double scan = (t % 90) / 90.0;
        Draw.rect(g, px + 5, py + 6 + 38 * scan, w - 10, 1, Draw.alpha(Theme.OK, 0.12));
    }
}
