package com.compilequest.entity;

import com.compilequest.core.Draw;
import com.compilequest.core.Theme;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;

/** A short label that rises and fades, such as "// saved" or "+2 Memory". */
public final class FloatText {
    private final String text;
    private final Color color;
    private final double size;
    private double x, y;
    private int life;
    private final int max;

    public FloatText(String text, Color color, double x, double y, int life, double size) {
        this.text = text;
        this.color = color;
        this.x = x;
        this.y = y;
        this.life = this.max = life;
        this.size = size;
    }

    public boolean update() {
        y -= 0.55 * Math.min(1, life / 20.0) + 0.1;
        return --life > 0;
    }

    public void render(Graphics2D g, double camX, double camY) {
        double a = Math.min(1, life / 18.0) * Math.min(1, (max - life) / 6.0 + 0.3);
        Font f = Theme.bold(size);
        double w = Draw.width(f, text);
        Draw.outlined(g, text, x - camX - w / 2, y - camY, f, Draw.alpha(color, a), Draw.alpha(Draw.RIM, a));
    }
}
