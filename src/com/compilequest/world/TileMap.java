package com.compilequest.world;

import com.compilequest.core.Draw;
import com.compilequest.core.Theme;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The grid of cells that makes up a level, plus its per-cell animation state and rendering.
 * Walls show three characters of real source code per tile, so they read as lines of code.
 */
public final class TileMap {
    public static final int T = Theme.TILE;
    private static final int TOP = 1, RIGHT = 2, BOTTOM = 4, LEFT = 8;
    private static final int VOLATILE_PERIOD = 210, VOLATILE_ON = 120;
    public static final int TRANSIENT_DELAY = 60, TRANSIENT_GONE = 180;

    public final int w, h;
    public final TileType[] type;
    /** Single display character for lifts, pillars and bridges. */
    public final char[] glyph;
    /** Offset into {@link #wallText} for each solid cell. */
    public final int[] off;
    /** Transient: >0 counting to vanish, <0 counting to return. Spring: squash frames. */
    public final int[] timer;
    /** Frames left of the "pop in" animation for cells created by a program. */
    public final int[] pop;
    /** Comment text, keyed by the index of the first cell of each comment platform. */
    public final Map<Integer, String> commentText = new HashMap<>();
    public String wallText = "";
    /** Faint source lines drawn behind the level, one per tile row like an editor page. */
    public String[] backgroundLines = {"// Compile Quest"};

    private final byte[] depth;
    private final byte[] edges;
    private final int[] runIdx;
    private final int[] runLen;
    private final String[] cellText;
    private final List<int[]> changes = new ArrayList<>();
    private boolean dirty = true;
    private int clock;

    public int highlightX0 = -1, highlightX1 = -1, highlightTimer;

    private final Rectangle2D.Double r = new Rectangle2D.Double();
    private static final Stroke DASH = Draw.dash(1.2, 3);
    private static final Stroke DASH_FAINT = Draw.dash(1, 4);

    public TileMap(int w, int h) {
        this.w = w;
        this.h = h;
        int n = w * h;
        type = new TileType[n];
        glyph = new char[n];
        off = new int[n];
        timer = new int[n];
        pop = new int[n];
        depth = new byte[n];
        edges = new byte[n];
        runIdx = new int[n];
        runLen = new int[n];
        cellText = new String[n];
        java.util.Arrays.fill(type, TileType.EMPTY);
        java.util.Arrays.fill(glyph, ' ');
    }

    public boolean in(int x, int y) { return x >= 0 && y >= 0 && x < w && y < h; }
    public int idx(int x, int y) { return y * w + x; }

    /** Left, right and top edges count as walls; below the map is open (a pit). */
    public TileType get(int x, int y) {
        if (x < 0 || x >= w || y < 0) return TileType.SOLID;
        if (y >= h) return TileType.EMPTY;
        return type[y * w + x];
    }

    public char glyphAt(int x, int y) { return in(x, y) ? glyph[idx(x, y)] : ' '; }

    public boolean blocks(int x, int y) {
        TileType t = get(x, y);
        if (!t.solid) return false;
        if (!in(x, y)) return true;
        int i = y * w + x;
        return switch (t) {
            case TRANSIENT -> timer[i] >= 0;
            case VOLATILE -> volatileOn();
            default -> true;
        };
    }

    public boolean oneWay(int x, int y) { return get(x, y) == TileType.COMMENT; }

    public boolean volatileOn() { return clock % VOLATILE_PERIOD < VOLATILE_ON; }

    public boolean volatileWarning() {
        int p = clock % VOLATILE_PERIOD;
        return p >= VOLATILE_ON - 34 && p < VOLATILE_ON;
    }

    public void set(int x, int y, TileType t, char c, boolean log) {
        if (!in(x, y)) return;
        int i = idx(x, y);
        if (log) changes.add(new int[] {i, type[i].ordinal(), glyph[i]});
        type[i] = t;
        glyph[i] = c;
        timer[i] = 0;
        dirty = true;
        invalidate(x, y);
    }

    public void setGlyph(int x, int y, char c, boolean log) {
        if (!in(x, y)) return;
        int i = idx(x, y);
        if (log) changes.add(new int[] {i, type[i].ordinal(), glyph[i]});
        glyph[i] = c;
        invalidate(x, y);
    }

    /** Undoes every logged change (the previous program's effects), newest first. */
    public void revertChanges() {
        TileType[] all = TileType.values();
        for (int k = changes.size() - 1; k >= 0; k--) {
            int[] c = changes.get(k);
            type[c[0]] = all[c[1]];
            glyph[c[0]] = (char) c[2];
            timer[c[0]] = 0;
            pop[c[0]] = 0;
        }
        changes.clear();
        dirty = true;
        chunks.clear();
    }

    public void update(double px, double py, double pw, double ph) {
        clock++;
        if (highlightTimer > 0) highlightTimer--;
        for (int i = 0; i < type.length; i++) {
            if (pop[i] > 0 && --pop[i] == 0) invalidate(i % w, i / w);
            TileType t = type[i];
            if (t == TileType.TRANSIENT && timer[i] != 0) {
                if (timer[i] > 0) {
                    if (++timer[i] > TRANSIENT_DELAY) timer[i] = -TRANSIENT_GONE;
                } else if (++timer[i] == 0 && overlapsCell(i, px, py, pw, ph)) {
                    timer[i] = -1;
                }
            } else if (t == TileType.SPRING && timer[i] > 0) {
                timer[i]--;
            }
        }
    }

    private boolean overlapsCell(int i, double px, double py, double pw, double ph) {
        double cx = (i % w) * T, cy = (i / w) * T;
        return px < cx + T && px + pw > cx && py < cy + T && py + ph > cy;
    }

    /** Starts the vanish timer for the whole horizontal run of transient cells. */
    public void triggerTransient(int x, int y) {
        if (get(x, y) != TileType.TRANSIENT) return;
        int l = x, rgt = x;
        while (get(l - 1, y) == TileType.TRANSIENT) l--;
        while (get(rgt + 1, y) == TileType.TRANSIENT) rgt++;
        for (int k = l; k <= rgt; k++) {
            int i = idx(k, y);
            if (timer[i] == 0) timer[i] = 1;
        }
    }

    public void bounceSpring(int x, int y) {
        if (in(x, y)) timer[idx(x, y)] = 12;
    }

    // ---------------------------------------------------------------- shape cache

    private static String word(TileType t) {
        return switch (t) {
            case DOOR -> "locked";
            case SPIKE, SPIKE_DOWN -> "null";
            default -> t.word;
        };
    }

    private void computeShape() {
        dirty = false;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = idx(x, y);
                TileType t = type[i];
                int e = 0;
                if (t != TileType.EMPTY) {
                    if (in(x, y - 1) && type[i - w] != t) e |= TOP;
                    if (in(x + 1, y) && type[i + 1] != t) e |= RIGHT;
                    if (in(x, y + 1) && type[i + w] != t) e |= BOTTOM;
                    if (in(x - 1, y) && type[i - 1] != t) e |= LEFT;
                }
                edges[i] = (byte) e;
                depth[i] = (byte) (t == TileType.SOLID ? (e != 0 ? 0 : 9) : 0);
                runIdx[i] = (x > 0 && type[i - 1] == t) ? runIdx[i - 1] + 1 : 0;
            }
            for (int x = w - 1; x >= 0; x--) {
                int i = idx(x, y);
                runLen[i] = (x < w - 1 && type[i + 1] == type[i]) ? runLen[i + 1] : runIdx[i] + 1;
            }
        }
        for (int pass = 0; pass < 3; pass++) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int i = idx(x, y);
                    if (type[i] != TileType.SOLID || depth[i] == 0) continue;
                    int m = 9;
                    if (y > 0) m = Math.min(m, depth[i - w]);
                    if (y < h - 1) m = Math.min(m, depth[i + w]);
                    if (x > 0) m = Math.min(m, depth[i - 1]);
                    if (x < w - 1) m = Math.min(m, depth[i + 1]);
                    depth[i] = (byte) Math.min(depth[i], m + 1);
                }
            }
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = idx(x, y);
                TileType t = type[i];
                String text = null;
                if (t == TileType.SOLID) {
                    text = slice(wallText.isEmpty() ? "###" : wallText, off[i]);
                } else if (t == TileType.FINAL) {
                    text = slice("final ", x * 3 + (y % 2) * 3);
                } else if (t == TileType.BREAK || t == TileType.TRANSIENT || t == TileType.VOLATILE || t == TileType.DOOR) {
                    String wd = word(t);
                    text = runLen[i] == 1 ? wd : slice(wd + " ", runIdx[i] * 3);
                }
                cellText[i] = text;
            }
        }
    }

    private static String slice(String s, int from) {
        int n = s.length();
        char[] c = new char[3];
        for (int k = 0; k < 3; k++) c[k] = s.charAt(Math.floorMod(from + k, n));
        return new String(c);
    }

    // ---------------------------------------------------------------- rendering

    private static final Color[] WALL_TEXT = {
        Draw.mix(Theme.WALL, Theme.BG, 0.12),
        Draw.mix(Theme.WALL, Theme.BG, 0.5),
        Draw.mix(Theme.WALL, Theme.BG, 0.66)
    };
    private static final Color WALL_TOP = Draw.alpha(Theme.WALL, 0.9);
    private static final Color WALL_SIDE = Draw.alpha(Theme.WALL, 0.34);
    private static final Color WALL_BOTTOM = Draw.alpha(Theme.WALL, 0.24);
    private static final Color COMMENT_FILL = Draw.alpha(Theme.PLATFORM, 0.07);
    private static final Color COMMENT_BAND = Draw.alpha(Theme.PLATFORM, 0.12);
    private static final Color COMMENT_TOP = Draw.alpha(Theme.PLATFORM, 0.85);
    private static final Color FINAL_FILL = new Color(0x303135);
    private static final Color FINAL_TEXT = new Color(0x8E8F94);
    private static final Color BRIDGE_FILL = new Color(0x27334A);
    private static final Color WATER_SURF = Draw.alpha(Theme.WATER, 0.45);
    private static final Color WATER_DEEP = Draw.alpha(Theme.WATER, 0.6);
    private static final Color WATER_WAVE = Draw.mix(Theme.WATER, Color.WHITE, 0.5);

    private static Font codeFont;
    private static Font codeBold;

    /** A font whose three-character width is exactly one tile. */
    private static Font code(boolean bold) {
        if (codeFont == null) {
            double size = 18 * (T / 3.0) / Draw.adv(Theme.mono(18));
            codeFont = Theme.mono(Math.round(size * 4) / 4.0);
            codeBold = Theme.bold(Math.round(size * 4) / 4.0);
        }
        return bold ? codeBold : codeFont;
    }

    // ---------------------------------------------------------------- chunk cache

    /** Static cells are pre-rendered in chunks of CH x CH tiles at device resolution. */
    private static final int CH = 8;
    private final Map<Integer, BufferedImage> chunks = new LinkedHashMap<>(96, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Integer, BufferedImage> e) { return size() > 90; }
    };
    private double chunkScale;

    private static int chunkKey(int cx, int cy) { return cy * 4096 + cx; }

    /** Forgets the cached pictures that show cell (x, y) or its neighbours (their edges may change). */
    private void invalidate(int x, int y) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) chunks.remove(chunkKey(Math.floorDiv(x + dx, CH), Math.floorDiv(y + dy, CH)));
        }
    }

    /** Cells that animate are drawn every frame; everything else comes from the chunk cache. */
    private boolean animated(int i) {
        if (pop[i] > 0) return true;
        return switch (type[i]) {
            case WATER, TRANSIENT, VOLATILE, SPRING, LIFT, PILLAR -> true;
            default -> false;
        };
    }

    // per-frame drawing state shared by drawCell
    private Font glyphFont, small, code, codeB;
    private double gx, gy, cx3, cy3;
    private boolean vOn, vBlink;

    private void prepare() {
        glyphFont = Theme.bold(19);
        small = Theme.bold(12);
        code = code(false);
        codeB = code(true);
        gx = (T - Draw.adv(glyphFont)) / 2;
        gy = Draw.mid(glyphFont, 0, T);
        cx3 = (T - Draw.adv(code) * 3) / 2;
        cy3 = Draw.mid(code, 0, T);
        vOn = volatileOn();
        vBlink = volatileWarning() && (clock / 5) % 2 == 0;
    }

    public void render(Graphics2D g, double camX, double camY) {
        render(g, camX, camY, Theme.W, Theme.H);
    }

    public void render(Graphics2D g, double camX, double camY, double viewW, double viewH) {
        if (dirty) computeShape();
        prepare();
        double s = Draw.deviceScale(g);
        if (s != chunkScale) {
            chunks.clear();
            chunkScale = s;
        }
        int x0 = Math.max(0, (int) Math.floor(camX / T));
        int y0 = Math.max(0, (int) Math.floor(camY / T));
        int x1 = Math.min(w - 1, (int) Math.floor((camX + viewW) / T));
        int y1 = Math.min(h - 1, (int) Math.floor((camY + viewH) / T));
        Object aaHint = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        for (int cy = y0 / CH; cy <= y1 / CH; cy++) {
            for (int cx = x0 / CH; cx <= x1 / CH; cx++) {
                Draw.blit(g, chunk(cx, cy, s), cx * CH * T - camX, cy * CH * T - camY);
            }
        }
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                int i = y * w + x;
                if (type[i] != TileType.EMPTY && animated(i)) drawCell(g, x, y, x * T - camX, y * T - camY);
            }
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aaHint);
    }

    private BufferedImage chunk(int cx, int cy, double s) {
        int key = chunkKey(cx, cy);
        BufferedImage cached = chunks.get(key);
        if (cached != null) return cached;
        int tx0 = cx * CH, ty0 = cy * CH;
        int tx1 = Math.min(w, tx0 + CH) - 1, ty1 = Math.min(h, ty0 + CH) - 1;
        // Two spare pixels so rounding at fractional scales never leaves a gap between chunks;
        // the next chunk (drawn later) covers the spare edge.
        int size = (int) Math.ceil(CH * T * s) + 2;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        com.compilequest.core.GameWindow.hints(g);
        g.setColor(Theme.BG);
        g.fillRect(0, 0, size, size);
        g.scale(s, s);
        renderBackgroundText(g, tx0, ty0, ty1);
        for (int y = ty0; y <= ty1; y++) {
            for (int x = tx0; x <= tx1; x++) {
                int i = idx(x, y);
                if (type[i] != TileType.EMPTY && type[i] != TileType.ANCHOR && !animated(i)) drawCell(g, x, y, (x - tx0) * T, (y - ty0) * T);
            }
        }
        renderComments(g, tx0 * T, ty0 * T, tx0, tx1, ty0, ty1);
        g.dispose();
        chunks.put(key, img);
        return img;
    }

    private static final double BG_SEGMENT = 1100;
    private static final Color BG_TEXT = Draw.mix(Theme.BG, Theme.TEXT, 0.06);

    /** One faint line of code per tile row; long rows repeat with a different line every segment. */
    private void renderBackgroundText(Graphics2D g, int tx0, int ty0, int ty1) {
        Font f = Theme.mono(14);
        g.setFont(f);
        g.setColor(BG_TEXT);
        double base = Draw.mid(f, 0, T);
        double left = tx0 * T, right = left + CH * T;
        int n = backgroundLines.length;
        for (int y = ty0; y <= ty1; y++) {
            for (int k = (int) Math.floor((left - BG_SEGMENT) / BG_SEGMENT); k * BG_SEGMENT < right; k++) {
                String line = backgroundLines[Math.floorMod(y + k * 17, n)];
                g.drawString(line, (float) (k * BG_SEGMENT + 96 - left), (float) ((y - ty0) * T + base));
            }
        }
    }

    private void drawCell(Graphics2D g, int x, int y, double px, double py) {
        int i = y * w + x;
        TileType t = type[i];
        AffineTransform saved = null;
        if (pop[i] > 0) {
            saved = g.getTransform();
            double s = Draw.easeOutBack(1 - pop[i] / 16.0);
            Draw.glow(g, px + T / 2.0, py + T / 2.0, 34, t == TileType.BRIDGE ? Theme.BRIDGE : Theme.NUMBER, pop[i] / 16.0 * 0.7);
            g.translate(px + T / 2.0, py + T / 2.0);
            g.scale(s, s);
            g.translate(-(px + T / 2.0), -(py + T / 2.0));
        }
        char c = glyph[i];
        int e = edges[i];
        String text = cellText[i];
        switch (t) {
            case SOLID -> {
                fill(g, Theme.WALL_FILL, px, py, T, T);
                if ((e & TOP) != 0) fill(g, WALL_TOP, px, py, T, 2);
                if ((e & LEFT) != 0) fill(g, WALL_SIDE, px, py, 1, T);
                if ((e & RIGHT) != 0) fill(g, WALL_SIDE, px + T - 1, py, 1, T);
                if ((e & BOTTOM) != 0) fill(g, WALL_BOTTOM, px, py + T - 1, T, 1);
                // Text fades out three tiles into a wall: deep interiors are plain fill.
                if (depth[i] < 3) {
                    Color col = (e & TOP) != 0 ? Theme.WALL : WALL_TEXT[depth[i]];
                    str(g, code, col, text, px + cx3, py + cy3);
                }
            }
            case COMMENT -> {
                fill(g, COMMENT_FILL, px, py, T, T);
                fill(g, COMMENT_BAND, px, py, T, 9);
                fill(g, COMMENT_TOP, px, py, T, 2);
            }
            case SPRING -> drawSpring(g, px, py, timer[i]);
            case SPIKE -> drawSpike(g, px, py, "null".charAt(runIdx[i] % 4), small, false);
            case SPIKE_DOWN -> drawSpike(g, px, py, "null".charAt(runIdx[i] % 4), small, true);
            case BREAK -> {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Draw.panel(g, px + 1.5, py + 1.5, T - 3, T - 3, 4, Draw.alpha(Theme.BREAK, 0.16), Draw.alpha(Theme.BREAK, 0.75));
                Draw.line(g, px + 22, py + 4, px + 18, py + 11, Draw.alpha(Theme.BREAK, 0.45), 1);
                Draw.line(g, px + 18, py + 11, px + 23, py + 16, Draw.alpha(Theme.BREAK, 0.45), 1);
                wordText(g, text, runLen[i], px, py, Theme.BREAK, codeB, cx3, cy3);
            }
            case TRANSIENT -> drawTransient(g, px, py, timer[i], text, runLen[i], codeB, cx3, cy3);
            case VOLATILE -> {
                if (!vOn) {
                    outline(g, px, py, Draw.alpha(Theme.VOLATILE, 0.16), DASH_FAINT);
                } else {
                    double a = vBlink ? 0.3 : 1;
                    fill(g, Draw.alpha(Theme.VOLATILE, 0.2 * a), px, py, T, T);
                    if ((e & TOP) != 0) fill(g, Draw.alpha(Theme.VOLATILE, 0.95 * a), px, py, T, 2);
                    if ((e & LEFT) != 0) fill(g, Draw.alpha(Theme.VOLATILE, 0.6 * a), px, py, 1, T);
                    if ((e & RIGHT) != 0) fill(g, Draw.alpha(Theme.VOLATILE, 0.6 * a), px + T - 1, py, 1, T);
                    fill(g, Draw.alpha(Theme.VOLATILE, 0.5 * a), px, py + T - 1, T, 1);
                    wordText(g, text, runLen[i], px, py, Draw.alpha(Draw.mix(Theme.VOLATILE, Color.WHITE, 0.2), a), codeB, cx3, cy3);
                }
            }
            case FINAL -> {
                fill(g, FINAL_FILL, px, py, T, T);
                if ((e & TOP) != 0) fill(g, Draw.alpha(Theme.FINAL, 0.95), px, py, T, 2);
                if ((e & LEFT) != 0) fill(g, Draw.alpha(Theme.FINAL, 0.45), px, py, 1, T);
                if ((e & RIGHT) != 0) fill(g, Draw.alpha(Theme.FINAL, 0.45), px + T - 1, py, 1, T);
                if ((e & BOTTOM) != 0) fill(g, Draw.alpha(Theme.FINAL, 0.35), px, py + T - 1, T, 1);
                if ((e & (TOP | LEFT)) == (TOP | LEFT)) circle(g, px + 4, py + 5, Draw.alpha(Theme.FINAL, 0.6));
                if ((e & (TOP | RIGHT)) == (TOP | RIGHT)) circle(g, px + T - 7, py + 5, Draw.alpha(Theme.FINAL, 0.6));
                str(g, code, (e & TOP) != 0 ? Draw.mix(FINAL_TEXT, Color.WHITE, 0.2) : FINAL_TEXT, text, px + cx3, py + cy3);
            }
            case WATER -> {
                boolean surface = y == 0 || type[i - w] != TileType.WATER;
                fill(g, surface ? WATER_SURF : WATER_DEEP, px, py, T, T);
                double wave = Math.sin(clock * 0.06 + x * 0.9) * 3;
                if (surface) {
                    fill(g, Draw.alpha(WATER_WAVE, 0.55), px, py + 3 + Math.sin(clock * 0.05 + x) * 1.5, T, 1.5);
                    glyph(g, glyphFont, WATER_WAVE, '~', px + gx + wave, py + gy + 2);
                } else {
                    glyph(g, glyphFont, Draw.alpha(WATER_WAVE, 0.16), '~', px + gx - wave, py + gy);
                }
            }
            case BRIDGE -> {
                fill(g, BRIDGE_FILL, px, py, T, T);
                fill(g, Draw.alpha(Theme.BRIDGE, 0.75), px, py, T, 2);
                fill(g, Draw.alpha(Theme.BRIDGE, 0.18), px, py + T - 1, T, 1);
                Font bf = Theme.bold(21);
                glyph(g, bf, Theme.BRIDGE, '=', px + (T - Draw.adv(bf)) / 2, py + gy + 1);
            }
            case LIFT, PILLAR -> drawNumberBlock(g, x, y, px, py, e, c, glyphFont, gx, gy);
            case DOOR -> {
                fill(g, Draw.alpha(Theme.DOOR, 0.14), px, py, T, T);
                if ((e & LEFT) != 0) fill(g, Draw.alpha(Theme.DOOR, 0.8), px, py, 2, T);
                if ((e & RIGHT) != 0) fill(g, Draw.alpha(Theme.DOOR, 0.8), px + T - 2, py, 2, T);
                if ((e & TOP) != 0) fill(g, Draw.alpha(Theme.DOOR, 0.9), px, py, T, 2);
                fill(g, Draw.alpha(Theme.DOOR, 0.22), px, py + T - 1, T, 1);
                wordText(g, text, runLen[i], px, py, Theme.DOOR, codeB, cx3, cy3);
            }
            case HINT -> {
                g.setColor(Draw.alpha(Theme.TEXT_DIM, 0.75));
                g.setStroke(DASH);
                r.setRect(px + 4.5, py + 4.5, T - 9, T - 9);
                g.draw(r);
            }
            default -> { }
        }
        if (saved != null) g.setTransform(saved);
    }

    /** Draws each comment platform's text once, starting at the platform's first cell. */
    private void renderComments(Graphics2D g, double camX, double camY, int x0, int x1, int y0, int y1) {
        Font f = Theme.italic(17);
        double adv = Draw.adv(f);
        double base = Draw.mid(f, 3, T);
        for (int y = y0; y <= y1; y++) {
            int x = x0;
            while (x <= x1) {
                if (type[idx(x, y)] != TileType.COMMENT) {
                    x++;
                    continue;
                }
                int s = x;
                while (s > 0 && type[idx(s - 1, y)] == TileType.COMMENT) s--;
                int e = x;
                while (e < w - 1 && type[idx(e + 1, y)] == TileType.COMMENT) e++;
                String text = commentText.getOrDefault(idx(s, y), "//");
                int max = (int) Math.floor(((e - s + 1) * T - 14) / adv);
                if (text.length() > max) text = text.substring(0, Math.max(0, max));
                Draw.text(g, text, s * T - camX + 9, y * T - camY + base, f, Theme.PLATFORM);
                x = e + 1;
            }
        }
    }

    private void fill(Graphics2D g, Color c, double x, double y, double ww, double hh) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setColor(c);
        r.setRect(x, y, ww, hh);
        g.fill(r);
    }

    private void outline(Graphics2D g, double px, double py, Color c, Stroke s) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setColor(c);
        g.setStroke(s);
        r.setRect(px + 1.5, py + 1.5, T - 3, T - 3);
        g.draw(r);
    }

    private static void circle(Graphics2D g, double x, double y, Color c) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(c);
        g.fill(new Ellipse2D.Double(x, y, 3, 3));
    }

    private static void glyph(Graphics2D g, Font f, Color c, char ch, double x, double y) {
        if (ch == ' ' || ch == 0) return;
        g.setFont(f);
        g.setColor(c);
        g.drawString(Draw.ch(ch), (float) x, (float) y);
    }

    private static void str(Graphics2D g, Font f, Color c, String s, double x, double y) {
        if (s == null) return;
        g.setFont(f);
        g.setColor(c);
        g.drawString(s, (float) x, (float) y);
    }

    /** A word block: three characters of the word per tile, or the whole word squeezed into a lone tile. */
    private static void wordText(Graphics2D g, String text, int len, double px, double py, Color c, Font code, double cx3, double cy3) {
        if (text == null) return;
        if (len == 1 && text.length() > 3) {
            double size = Math.min(13, 27 / (text.length() * 0.6));
            Font f = Theme.bold(Math.round(size * 2) / 2.0);
            Draw.center(g, text, px + T / 2.0, Draw.mid(f, py, T), f, c);
        } else {
            str(g, code, c, text, px + cx3, py + cy3);
        }
    }

    private void drawSpring(Graphics2D g, double px, double py, int anim) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double squash = anim > 0 ? Math.sin(anim / 12.0 * Math.PI) * 7 : 0;
        Draw.glow(g, px + T / 2.0, py + 4, 22, Theme.SPRING, 0.18 + (anim > 0 ? 0.3 : 0));
        Draw.panel(g, px + 3, py + squash, T - 6, 5, 2, Theme.SPRING, null);
        Font f = Theme.bold(17);
        Draw.center(g, "++", px + T / 2.0, py + 23 + squash * 0.5, f, Draw.alpha(Theme.SPRING, 0.9));
        fill(g, Draw.alpha(Theme.SPRING, 0.35), px + 5, py + T - 3, T - 10, 3);
    }

    /** Three null spikes pointing up from the floor, or down from the ceiling. */
    private void drawSpike(Graphics2D g, double px, double py, char c, Font small, boolean down) {
        Path2D p = new Path2D.Double();
        double bw = (T - 4) / 3.0;
        double base = down ? py : py + T, tip = down ? py + 14 : py + T - 14;
        for (int k = 0; k < 3; k++) {
            double bx = px + 2 + k * bw;
            p.moveTo(bx, base);
            p.lineTo(bx + bw / 2, tip);
            p.lineTo(bx + bw, base);
            p.closePath();
        }
        g.setColor(Draw.alpha(Theme.SPIKE, 0.08));
        r.setRect(px, down ? py : py + 6, T, T - 6);
        g.fill(r);
        g.setColor(Draw.alpha(Theme.SPIKE, 0.9));
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.fill(p);
        double a = Draw.adv(small);
        glyph(g, small, Draw.alpha(Theme.SPIKE, 0.85), c, px + (T - a) / 2, down ? py + 26 : py + 14);
    }

    private void drawTransient(Graphics2D g, double px, double py, int tm, String text, int len, Font code, double cx3, double cy3) {
        if (tm < 0) {
            outline(g, px, py, Draw.alpha(Theme.TRANSIENT, 0.13), DASH_FAINT);
            return;
        }
        double shake = tm > 0 ? Math.sin(tm * 1.9) * Math.min(2.2, tm / 14.0) : 0;
        double a = tm > 0 ? 1 - tm / (double) TRANSIENT_DELAY * 0.6 : 1;
        fill(g, Draw.alpha(Theme.TRANSIENT, 0.12 * a), px + shake, py, T, T);
        outline(g, px + shake, py, Draw.alpha(Theme.TRANSIENT, 0.65 * a), DASH);
        wordText(g, text, len, px + shake, py, Draw.alpha(Theme.TRANSIENT, a), code, cx3, cy3);
    }

    private void drawNumberBlock(Graphics2D g, int x, int y, double px, double py, int e, char c, Font f, double gx, double gy) {
        boolean lit = highlightTimer > 0 && x >= highlightX0 && x <= highlightX1;
        fill(g, Draw.alpha(lit ? Theme.WARN : Theme.NUMBER, lit ? 0.3 : 0.15), px, py, T, T);
        Color edge = Draw.alpha(lit ? Theme.WARN : Theme.NUMBER, 0.6);
        if ((e & LEFT) != 0) fill(g, edge, px, py, 1.5, T);
        if ((e & RIGHT) != 0) fill(g, edge, px + T - 1.5, py, 1.5, T);
        if ((e & TOP) != 0) fill(g, Draw.alpha(lit ? Theme.WARN : Theme.NUMBER, 0.95), px, py, T, 2);
        if (c == ' ') return;
        boolean digit = Character.isDigit(c) || c == '?';
        Color col = digit ? Draw.mix(Theme.NUMBER, Color.WHITE, 0.45) : Draw.alpha(Theme.NUMBER, 0.75);
        double ox = gx;
        if (digit && type[idx(x, y)] == TileType.PILLAR && get(x + 1, y) == TileType.PILLAR && glyphAt(x + 1, y) == ' ') {
            ox = T - Draw.adv(f) / 2;
        }
        glyph(g, f, col, c, px + ox, py + gy);
    }
}
