package com.compilequest.entity;

import com.compilequest.core.Draw;
import com.compilequest.core.Theme;
import com.compilequest.editor.BlockDef;
import com.compilequest.editor.Syntax;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;

/** Something the player can pick up: a code block, a gc() heal, a hidden star or the "true" key. */
public final class Pickup extends Entity {
    public enum Kind { BLOCK, GC, STAR, KEY }

    public final Kind kind;
    public final BlockDef block;
    public final int starIndex;
    private final double baseY;
    private int t;

    public Pickup(Kind kind, double cx, double cy, BlockDef block, int starIndex) {
        this.kind = kind;
        this.block = block;
        this.starIndex = starIndex;
        this.t = (int) (cx * 7) % 120;
        switch (kind) {
            case BLOCK -> {
                w = Syntax.width(font(), block.text()) + (block.kind() == BlockDef.Kind.LINE ? 30 : 24);
                w = Math.max(w, 34);
                h = 26;
            }
            case GC -> { w = 46; h = 22; }
            case STAR -> { w = 26; h = 26; }
            case KEY -> { w = 58; h = 28; }
        }
        this.x = cx - w / 2;
        this.baseY = cy - h / 2;
        this.y = baseY;
    }

    private static Font font() { return Theme.mono(13.5); }

    public void update() {
        t++;
        y = baseY + Math.sin(t * Math.PI * 2 / 120) * 4;
    }

    public void render(Graphics2D g, double camX, double camY) {
        double px = x - camX, py = y - camY;
        double pulse = 0.5 + 0.5 * Math.sin(t * 0.08);
        switch (kind) {
            case BLOCK -> {
                Draw.glow(g, px + w / 2, py + h / 2, w * 0.55 + 18, Theme.ACCENT, 0.2 + 0.12 * pulse);
                Draw.panel(g, px, py, w, h, 5, Theme.PICKUP_BG, Draw.alpha(Theme.ACCENT_HOVER, 0.6 + 0.4 * pulse));
                double tx = px + 12;
                if (block.kind() == BlockDef.Kind.LINE) {
                    Draw.rect(g, px + 5, py + 6, 3, h - 12, Theme.ACCENT_HOVER);
                    tx = px + 15;
                }
                Font f = font();
                Syntax.draw(g, block.text(), tx, Draw.mid(f, py, h), f, 1);
            }
            case GC -> {
                Draw.glow(g, px + w / 2, py + h / 2, 34, Theme.GC, 0.25 + 0.15 * pulse);
                Draw.panel(g, px, py, w, h, h / 2, Draw.alpha(Theme.GC, 0.16), Draw.alpha(Theme.GC, 0.85));
                Font f = Theme.bold(14);
                Draw.center(g, "gc()", px + w / 2, Draw.mid(f, py, h), f, Draw.mix(Theme.GC, Color.WHITE, 0.3));
            }
            case STAR -> {
                Draw.glow(g, px + w / 2, py + h / 2, 30 + 6 * pulse, Theme.STAR, 0.35 + 0.15 * pulse);
                double rot = Math.sin(t * 0.04) * 0.25;
                g.setColor(Theme.STAR);
                g.fill(Draw.starShape(px + w / 2, py + h / 2, 12, rot));
                g.setColor(Draw.alpha(Color.WHITE, 0.6));
                g.fill(Draw.starShape(px + w / 2, py + h / 2 - 1, 5, rot));
            }
            case KEY -> {
                Draw.glow(g, px + w / 2, py + h / 2, 44, Theme.KEY, 0.3 + 0.15 * pulse);
                Draw.panel(g, px, py, w, h, 6, Draw.alpha(Theme.KEY, 0.2), Draw.mix(Theme.KEY, Color.WHITE, 0.3));
                Font f = Theme.bold(16);
                Draw.center(g, "true", px + w / 2, Draw.mid(f, py, h), f, Draw.mix(Theme.KEY, Color.WHITE, 0.5));
            }
        }
    }
}
