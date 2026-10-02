package com.compilequest.core;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/** Drawing helpers shared by every screen: text, panels, glows and vector icons. */
public final class Draw {
    private Draw() {}

    public static final FontRenderContext FRC = new FontRenderContext(null, true, true);
    private static final Map<Long, BufferedImage> GLOWS = new java.util.LinkedHashMap<>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long, BufferedImage> e) { return size() > 160; }
    };
    private static final Map<Font, Double> CAP = new HashMap<>();
    private static final Map<Font, Double> ADV = new HashMap<>();
    private static final Map<Float, Stroke> STROKES = new HashMap<>();
    private static final String[] CHARS = new String[128];

    static {
        for (int i = 0; i < 128; i++) CHARS[i] = String.valueOf((char) i);
    }

    // ---------------------------------------------------------------- math

    public static double clamp(double v, double lo, double hi) { return v < lo ? lo : (v > hi ? hi : v); }
    public static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    public static double easeOut(double t) { t = clamp(t, 0, 1); return 1 - Math.pow(1 - t, 3); }
    public static double easeInOut(double t) { t = clamp(t, 0, 1); return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2; }

    public static double easeOutBack(double t) {
        t = clamp(t, 0, 1);
        double c1 = 1.70158, c3 = c1 + 1;
        return 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2);
    }

    // ---------------------------------------------------------------- color

    public static Color alpha(Color c, double a) {
        int al = (int) Math.round(clamp(a, 0, 1) * c.getAlpha());
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), al);
    }

    public static Color mix(Color a, Color b, double t) {
        t = clamp(t, 0, 1);
        return new Color(
                (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t),
                (int) Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
    }

    /** Multiplies the current composite alpha; returns the old composite to restore. */
    public static Composite pushAlpha(Graphics2D g, double a) {
        Composite old = g.getComposite();
        float base = old instanceof AlphaComposite ac ? ac.getAlpha() : 1f;
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) clamp(base * a, 0, 1)));
        return old;
    }

    public static Stroke stroke(double w) {
        return STROKES.computeIfAbsent((float) w, k -> new BasicStroke(k, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    }

    public static Stroke dash(double w, double len) {
        return new BasicStroke((float) w, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f, new float[] {(float) len, (float) len}, 0f);
    }

    // ---------------------------------------------------------------- text

    public static String ch(char c) { return c < 128 ? CHARS[c] : String.valueOf(c); }

    public static double width(Font f, String s) { return f.getStringBounds(s, FRC).getWidth(); }

    /** Advance of one character in a monospace font. */
    public static synchronized double adv(Font f) {
        return ADV.computeIfAbsent(f, k -> k.getStringBounds("MMMMMMMMMM", FRC).getWidth() / 10.0);
    }

    public static synchronized double cap(Font f) {
        return CAP.computeIfAbsent(f, k -> k.createGlyphVector(FRC, "H").getVisualBounds().getHeight());
    }

    /** Baseline that vertically centers a line of text inside [top, top + height]. */
    public static double mid(Font f, double top, double height) {
        return top + height / 2 + cap(f) / 2;
    }

    public static void text(Graphics2D g, String s, double x, double y, Font f, Color c) {
        g.setFont(f);
        g.setColor(c);
        g.drawString(s, (float) x, (float) y);
    }

    public static void center(Graphics2D g, String s, double cx, double y, Font f, Color c) {
        text(g, s, cx - width(f, s) / 2, y, f, c);
    }

    public static void right(Graphics2D g, String s, double rx, double y, Font f, Color c) {
        text(g, s, rx - width(f, s), y, f, c);
    }

    /** Text with a dark rim, used for anything drawn over the busy world. */
    public static void outlined(Graphics2D g, String s, double x, double y, Font f, Color c, Color rim) {
        g.setFont(f);
        g.setColor(rim);
        float fx = (float) x, fy = (float) y;
        g.drawString(s, fx - 1, fy);
        g.drawString(s, fx + 1, fy);
        g.drawString(s, fx, fy - 1);
        g.drawString(s, fx, fy + 1);
        g.drawString(s, fx + 1, fy + 1.5f);
        g.setColor(c);
        g.drawString(s, fx, fy);
    }

    public static final Color RIM = new Color(10, 11, 13, 210);

    // ---------------------------------------------------------------- shapes

    public static RoundRectangle2D rr(double x, double y, double w, double h, double r) {
        return new RoundRectangle2D.Double(x, y, w, h, r * 2, r * 2);
    }

    public static void panel(Graphics2D g, double x, double y, double w, double h, double r, Color fill, Color border) {
        if (fill != null) {
            if (fill.getAlpha() == 255 && w > 4 * r && h > 4 * r && r > 0) {
                // Opaque: plain rectangles for the body, anti-aliased shapes only at the corners.
                rect(g, x + r, y, w - 2 * r, h, fill);
                rect(g, x, y + r, r, h - 2 * r, fill);
                rect(g, x + w - r, y + r, r, h - 2 * r, fill);
                g.setColor(fill);
                g.fill(new Ellipse2D.Double(x, y, 2 * r, 2 * r));
                g.fill(new Ellipse2D.Double(x + w - 2 * r, y, 2 * r, 2 * r));
                g.fill(new Ellipse2D.Double(x, y + h - 2 * r, 2 * r, 2 * r));
                g.fill(new Ellipse2D.Double(x + w - 2 * r, y + h - 2 * r, 2 * r, 2 * r));
            } else {
                g.setColor(fill);
                g.fill(rr(x, y, w, h, r));
            }
        }
        if (border != null) {
            if (w > 200 && h > 100 && r > 0) {
                rect(g, x + r, y, w - 2 * r, 1, border);
                rect(g, x + r, y + h - 1, w - 2 * r, 1, border);
                rect(g, x, y + r, 1, h - 2 * r, border);
                rect(g, x + w - 1, y + r, 1, h - 2 * r, border);
                g.setColor(border);
                g.setStroke(stroke(1));
                double d = 2 * r - 1;
                g.draw(new java.awt.geom.Arc2D.Double(x + 0.5, y + 0.5, d, d, 90, 90, java.awt.geom.Arc2D.OPEN));
                g.draw(new java.awt.geom.Arc2D.Double(x + w - 0.5 - d, y + 0.5, d, d, 0, 90, java.awt.geom.Arc2D.OPEN));
                g.draw(new java.awt.geom.Arc2D.Double(x + 0.5, y + h - 0.5 - d, d, d, 180, 90, java.awt.geom.Arc2D.OPEN));
                g.draw(new java.awt.geom.Arc2D.Double(x + w - 0.5 - d, y + h - 0.5 - d, d, d, 270, 90, java.awt.geom.Arc2D.OPEN));
            } else {
                g.setColor(border);
                g.setStroke(stroke(1));
                g.draw(rr(x + 0.5, y + 0.5, w - 1, h - 1, r));
            }
        }
    }

    /**
     * A soft drop shadow. The shadow of a small rounded box is rendered once and drawn as nine
     * slices (corners copied, edges stretched, centre skipped because the panel covers it).
     */
    public static void shadow(Graphics2D g, double x, double y, double w, double h, double r, double spread, double strength) {
        AffineTransform t = g.getTransform();
        double sc = t.getScaleX();
        int ds = (int) Math.round(spread * sc), dr = (int) Math.round(r * sc), e = ds + dr;
        int dw = (int) Math.round(w * sc) + 2 * ds, dh = (int) Math.round(h * sc) + 2 * ds;
        if (t.getShearX() != 0 || t.getShearY() != 0 || ds < 1 || dw < 2 * e + 1 || dh < 2 * e + 1) {
            g.setColor(new Color(0, 0, 0, (int) clamp(strength * 110, 0, 255)));
            g.fill(rr(x + 1, y + spread * 0.4, w, h, r));
            return;
        }
        BufferedImage img = shadowImage(ds, dr, strength);
        Point2D p = t.transform(new Point2D.Double(x, y + spread * 0.4), null);
        int dx = (int) Math.round(p.getX()) - ds, dy = (int) Math.round(p.getY()) - ds;
        int n = 2 * e + 1;
        g.setTransform(new AffineTransform());
        g.drawImage(img, dx, dy, dx + e, dy + e, 0, 0, e, e, null);
        g.drawImage(img, dx + dw - e, dy, dx + dw, dy + e, e + 1, 0, n, e, null);
        g.drawImage(img, dx, dy + dh - e, dx + e, dy + dh, 0, e + 1, e, n, null);
        g.drawImage(img, dx + dw - e, dy + dh - e, dx + dw, dy + dh, e + 1, e + 1, n, n, null);
        BufferedImage[] strips = shadowStrips(ds, dr, strength, img);
        int len = strips[0].getWidth();
        for (int sx = dx + e; sx < dx + dw - e; sx += len) {
            int seg = Math.min(len, dx + dw - e - sx);
            g.drawImage(strips[0], sx, dy, sx + seg, dy + e, 0, 0, seg, e, null);
            g.drawImage(strips[1], sx, dy + dh - e, sx + seg, dy + dh, 0, 0, seg, e, null);
        }
        for (int sy = dy + e; sy < dy + dh - e; sy += len) {
            int seg = Math.min(len, dy + dh - e - sy);
            g.drawImage(strips[2], dx, sy, dx + e, sy + seg, 0, 0, e, seg, null);
            g.drawImage(strips[3], dx + dw - e, sy, dx + dw, sy + seg, 0, 0, e, seg, null);
        }
        g.setTransform(t);
    }

    private static final Map<Long, BufferedImage> SHADOWS = new HashMap<>();
    private static final Map<Long, BufferedImage[]> STRIPS = new HashMap<>();

    /** Top, bottom, left and right edge strips of a shadow, 256 px long, for drawing without scaling. */
    private static synchronized BufferedImage[] shadowStrips(int ds, int dr, double strength, BufferedImage img) {
        long key = ((long) ds << 40) | ((long) dr << 20) | (long) (strength * 1000);
        return STRIPS.computeIfAbsent(key, k -> {
            int e = ds + dr, len = 256;
            BufferedImage[] out = new BufferedImage[4];
            for (int side = 0; side < 4; side++) {
                boolean horizontal = side < 2;
                BufferedImage strip = new BufferedImage(horizontal ? len : e, horizontal ? e : len, BufferedImage.TYPE_INT_ARGB_PRE);
                Graphics2D g = strip.createGraphics();
                switch (side) {
                    case 0 -> g.drawImage(img, 0, 0, len, e, e, 0, e + 1, e, null);
                    case 1 -> g.drawImage(img, 0, 0, len, e, e, e + 1, e + 1, 2 * e + 1, null);
                    case 2 -> g.drawImage(img, 0, 0, e, len, 0, e, e, e + 1, null);
                    default -> g.drawImage(img, 0, 0, e, len, e + 1, e, 2 * e + 1, e + 1, null);
                }
                g.dispose();
                out[side] = strip;
            }
            return out;
        });
    }

    private static synchronized BufferedImage shadowImage(int ds, int dr, double strength) {
        long key = ((long) ds << 40) | ((long) dr << 20) | (long) (strength * 1000);
        return SHADOWS.computeIfAbsent(key, k -> {
            int e = ds + dr, n = 2 * e + 1;
            BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB_PRE);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int steps = 10;
            for (int i = steps; i >= 1; i--) {
                double s = ds * i / (double) steps;
                g.setColor(new Color(0, 0, 0, (int) clamp(strength * 255 / steps * 1.1, 0, 255)));
                g.fill(rr(ds - s, ds - s, 2 * dr + 1 + 2 * s, 2 * dr + 1 + 2 * s, dr + s));
            }
            g.dispose();
            return img;
        });
    }

    public static void dashed(Graphics2D g, Shape s, Color c, double width, double len) {
        g.setColor(c);
        g.setStroke(dash(width, len));
        g.draw(s);
    }

    public static void line(Graphics2D g, double x1, double y1, double x2, double y2, Color c, double w) {
        g.setColor(c);
        g.setStroke(stroke(w));
        g.draw(new Line2D.Double(x1, y1, x2, y2));
    }

    public static void circle(Graphics2D g, double cx, double cy, double r, Color c) {
        g.setColor(c);
        g.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
    }

    /** Axis-aligned rectangles skip anti-aliasing, which is much faster in software rendering. */
    public static void rect(Graphics2D g, double x, double y, double w, double h, Color c) {
        Object old = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setColor(c);
        g.fill(new Rectangle2D.Double(x, y, w, h));
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, old);
    }

    // ---------------------------------------------------------------- glow

    /**
     * A soft radial glow. Glow images are cached at device resolution and copied 1:1, which is
     * far cheaper than scaling one image up every frame when Java2D renders in software.
     */
    public static void glow(Graphics2D g, double cx, double cy, double radius, Color c, double alpha) {
        if (alpha <= 0.01 || radius <= 0) return;
        AffineTransform t = g.getTransform();
        if (t.getShearX() != 0 || t.getShearY() != 0) return;
        double dr = radius * t.getScaleX();
        int bucket = (int) Math.max(4, Math.round(dr / 4) * 4);
        BufferedImage img = glowImage(c.getRGB() & 0xffffff, bucket);
        Point2D p = t.transform(new Point2D.Double(cx, cy), null);
        Composite old = pushAlpha(g, alpha);
        g.setTransform(new AffineTransform());
        g.drawImage(img, (int) Math.round(p.getX() - bucket), (int) Math.round(p.getY() - bucket), null);
        g.setTransform(t);
        g.setComposite(old);
    }

    private static synchronized BufferedImage glowImage(int rgb, int radius) {
        long key = ((long) rgb << 20) | radius;
        BufferedImage cached = GLOWS.get(key);
        if (cached != null) return cached;
        int s = radius * 2;
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = img.createGraphics();
        Color c = new Color(rgb);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(radius, radius), radius,
                new float[] {0f, 0.3f, 1f},
                new Color[] {alpha(c, 0.9), alpha(c, 0.35), alpha(c, 0)}));
        g.fillRect(0, 0, s, s);
        g.dispose();
        GLOWS.put(key, img);
        return img;
    }

    /** Draws an image so each of its pixels lands on exactly one device pixel (no scaling). */
    public static void blit(Graphics2D g, BufferedImage img, double x, double y) {
        AffineTransform t = g.getTransform();
        Point2D p = t.transform(new Point2D.Double(x, y), null);
        g.setTransform(new AffineTransform());
        g.drawImage(img, (int) Math.round(p.getX()), (int) Math.round(p.getY()), null);
        g.setTransform(t);
    }

    /** The device pixels per logical pixel of this graphics context (2 on a 200% display). */
    public static double deviceScale(Graphics2D g) { return g.getTransform().getScaleX(); }

    // ---------------------------------------------------------------- icons

    public static Path2D starShape(double cx, double cy, double r, double rot) {
        Path2D p = new Path2D.Double();
        for (int i = 0; i < 10; i++) {
            double rr = (i % 2 == 0) ? r : r * 0.45;
            double a = -Math.PI / 2 + rot + i * Math.PI / 5;
            double px = cx + Math.cos(a) * rr, py = cy + Math.sin(a) * rr;
            if (i == 0) p.moveTo(px, py);
            else p.lineTo(px, py);
        }
        p.closePath();
        return p;
    }

    public static void star(Graphics2D g, double cx, double cy, double r, boolean filled, Color c) {
        Path2D s = starShape(cx, cy, r, 0);
        g.setColor(c);
        if (filled) {
            g.fill(s);
        } else {
            g.setStroke(stroke(1.3));
            g.draw(s);
        }
    }

    public static void play(Graphics2D g, double x, double y, double size, Color c) {
        Path2D p = new Path2D.Double();
        p.moveTo(x, y);
        p.lineTo(x + size * 0.9, y + size / 2);
        p.lineTo(x, y + size);
        p.closePath();
        g.setColor(c);
        g.fill(p);
    }

    public static void check(Graphics2D g, double x, double y, double s, Color c) {
        Path2D p = new Path2D.Double();
        p.moveTo(x, y + s * 0.55);
        p.lineTo(x + s * 0.38, y + s * 0.9);
        p.lineTo(x + s, y + s * 0.1);
        g.setColor(c);
        g.setStroke(stroke(Math.max(1.6, s / 6)));
        g.draw(p);
    }

    public static void cross(Graphics2D g, double cx, double cy, double s, Color c, double w) {
        line(g, cx - s, cy - s, cx + s, cy + s, c, w);
        line(g, cx - s, cy + s, cx + s, cy - s, c, w);
    }

    public static void lock(Graphics2D g, double x, double y, double s, Color c) {
        g.setColor(c);
        g.setStroke(stroke(Math.max(1.4, s / 8)));
        g.draw(new java.awt.geom.Arc2D.Double(x + s * 0.2, y, s * 0.6, s * 0.7, 0, 180, java.awt.geom.Arc2D.OPEN));
        g.draw(new Line2D.Double(x + s * 0.2, y + s * 0.35, x + s * 0.2, y + s * 0.5));
        g.draw(new Line2D.Double(x + s * 0.8, y + s * 0.35, x + s * 0.8, y + s * 0.5));
        g.fill(rr(x, y + s * 0.45, s, s * 0.6, s * 0.12));
    }

    public static void folder(Graphics2D g, double x, double y, Color c) {
        g.setColor(c);
        Path2D p = new Path2D.Double();
        p.moveTo(x, y + 2);
        p.lineTo(x + 6, y + 2);
        p.lineTo(x + 8, y + 4);
        p.lineTo(x + 16, y + 4);
        p.lineTo(x + 16, y + 14);
        p.lineTo(x, y + 14);
        p.closePath();
        g.fill(p);
    }

    public static void chevron(Graphics2D g, double x, double y, boolean down, Color c) {
        Path2D p = new Path2D.Double();
        if (down) {
            p.moveTo(x, y + 2);
            p.lineTo(x + 4, y + 6);
            p.lineTo(x + 8, y + 2);
        } else {
            p.moveTo(x + 2, y);
            p.lineTo(x + 6, y + 4);
            p.lineTo(x + 2, y + 8);
        }
        g.setColor(c);
        g.setStroke(stroke(1.5));
        g.draw(p);
    }

    /** IntelliJ-style class icon: a blue circle with a C. */
    public static void classIcon(Graphics2D g, double x, double y, double s, boolean dim) {
        circle(g, x + s / 2, y + s / 2, s / 2, dim ? new Color(0x3A4A63) : new Color(0x3D7BD6));
        Font f = Theme.bold(s * 0.62);
        center(g, "C", x + s / 2, mid(f, y, s), f, dim ? new Color(0x8C96A6) : Color.WHITE);
    }

    public static void fileIcon(Graphics2D g, double x, double y, Color c) {
        g.setColor(c);
        g.setStroke(stroke(1.2));
        Path2D p = new Path2D.Double();
        p.moveTo(x + 2, y);
        p.lineTo(x + 10, y);
        p.lineTo(x + 14, y + 4);
        p.lineTo(x + 14, y + 16);
        p.lineTo(x + 2, y + 16);
        p.closePath();
        g.draw(p);
    }

    /** A keyboard key cap with a label; returns its width. */
    public static double keycap(Graphics2D g, String key, double x, double baseline, Font f, Color text) {
        double w = Math.max(width(f, key) + 12, 22);
        double h = cap(f) + 12;
        double top = baseline - cap(f) - 6;
        panel(g, x, top, w, h, 5, alpha(Theme.PANEL, 0.95), Theme.BORDER_STRONG);
        g.setColor(alpha(Color.BLACK, 0.35));
        g.fill(new Rectangle2D.Double(x + 3, top + h - 2, w - 6, 1.5));
        center(g, key, x + w / 2, baseline, f, text);
        return w;
    }
}
