package com.compilequest.ui;

import com.compilequest.core.Draw;
import com.compilequest.core.Theme;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

/** A clickable button with an optional key hint, drawn in the IDE style. */
final class Button {
    enum Style { PRIMARY, DANGER, SECONDARY }

    final Rectangle2D.Double rect = new Rectangle2D.Double();
    final String label;
    final String key;
    final Style style;
    boolean hover;

    Button(String label, String key, Style style) {
        this.label = label;
        this.key = key;
        this.style = style;
    }

    Button at(double x, double y, double w, double h) {
        rect.setRect(x, y, w, h);
        return this;
    }

    boolean contains(double x, double y) { return rect.contains(x, y); }

    void render(Graphics2D g) {
        Rectangle2D.Double r = rect;
        Color fill, border, text;
        switch (style) {
            case PRIMARY -> {
                fill = hover ? Theme.ACCENT_HOVER : Theme.ACCENT;
                border = null;
                text = Color.WHITE;
            }
            case DANGER -> {
                fill = hover ? Draw.mix(Theme.ERR, Color.WHITE, 0.1) : Draw.mix(Theme.ERR, Theme.BG, 0.25);
                border = null;
                text = Color.WHITE;
            }
            default -> {
                fill = hover ? Theme.HOVER : null;
                border = Theme.BORDER_STRONG;
                text = Theme.TEXT_BRIGHT;
            }
        }
        Draw.panel(g, r.x, r.y, r.width, r.height, 7, fill, border);
        Font f = Theme.bold(15);
        Font kf = Theme.mono(12);
        double kw = key == null ? 0 : Draw.width(kf, key) + 10;
        double tw = Draw.width(f, label) + kw;
        double x = r.getCenterX() - tw / 2;
        Draw.text(g, label, x, Draw.mid(f, r.y, r.height), f, text);
        if (key != null) Draw.text(g, key, x + Draw.width(f, label) + 10, Draw.mid(kf, r.y, r.height), kf, Draw.alpha(text, 0.6));
    }
}
