package com.compilequest.entity;

import com.compilequest.core.Draw;
import com.compilequest.core.Theme;
import com.compilequest.world.TileMap;

import java.awt.Font;
import java.awt.Graphics2D;

/** The closing brace "}" at the end of a level. It opens once the program runs successfully. */
public final class Exit extends Entity {
    public boolean active;
    private int t;
    private int activateAnim;

    public Exit(int cellX, int cellY) {
        int T = TileMap.T;
        this.w = 44;
        this.h = 76;
        this.x = cellX * T + T / 2.0 - w / 2;
        this.y = cellY * T + T - h;
    }

    public void activate() {
        if (active) return;
        active = true;
        activateAnim = 60;
    }

    public void update(Particles particles) {
        t++;
        if (activateAnim > 0) activateAnim--;
        if (active && t % 9 == 0) particles.sparkle(cx(), cy(), Theme.EXIT);
    }

    public void render(Graphics2D g, double camX, double camY) {
        double px = x - camX, py = y - camY;
        Font f = Theme.bold(80);
        double cx = px + w / 2;
        double base = py + h - 10;
        if (active) {
            double pulse = 0.5 + 0.5 * Math.sin(t * 0.07);
            double burst = activateAnim / 60.0;
            Draw.glow(g, cx, py + h / 2, 70 + 30 * burst, Theme.EXIT, 0.35 + 0.2 * pulse + 0.4 * burst);
            double bob = Math.sin(t * 0.05) * 2;
            Draw.outlined(g, "}", cx - Draw.width(f, "}") / 2, base + bob, f, Theme.EXIT, Draw.RIM);
        } else {
            Draw.text(g, "}", cx - Draw.width(f, "}") / 2, base, f, Draw.alpha(Theme.EXIT, 0.22));
            Draw.lock(g, cx - 8, py + h / 2 - 6, 16, Draw.alpha(Theme.EXIT, 0.55));
        }
    }
}
