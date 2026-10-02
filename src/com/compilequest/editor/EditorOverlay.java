package com.compilequest.editor;

import com.compilequest.core.Draw;
import com.compilequest.core.Input;
import com.compilequest.core.Sound.Sfx;
import com.compilequest.core.Theme;
import com.compilequest.level.Judge;
import com.compilequest.level.LevelConfig;
import com.compilequest.level.LevelScreen;

import java.awt.Color;
import java.awt.Composite;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.event.KeyEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * The Tab editor (DESIGN.md 3.3): the inventory on the left, the program skeleton with slots on
 * the right, and a console below. Blocks are dragged with the mouse; dropping on a filled slot
 * swaps; right-click sends a block back to the inventory.
 */
public final class EditorOverlay {
    private static final double PX = 80, PY = 70, PW = 1280, PH = 760;
    private static final double TITLE = 44, INV_W = 390, CONSOLE = 150, LINE_H = 54;
    private static final double LINE_SLOT_W = 430, TOKEN_SLOT_W = 58;

    private final LevelScreen L;
    private final LevelConfig cfg;
    private final Font code = Theme.mono(15);
    private final Map<String, Rectangle2D.Double> slotRects = new LinkedHashMap<>();
    private final Map<String, Rectangle2D.Double> invRects = new LinkedHashMap<>();
    private final Rectangle2D.Double runRect = new Rectangle2D.Double();
    private double tokensHeaderY = -1;
    private boolean canRun;
    private int t;
    private double scroll, contentH;

    private BlockDef drag;
    private boolean dragging;
    private double grabDX, grabDY, pressX, pressY, dragW, dragH;

    private BlockDef bounce;
    private double bx0, by0, bx1, by1, bw, bh;
    private int bounceT;

    private String flashSlot;
    private boolean flashOk;
    private int flashT, runHintFlash;
    private String hoverSlot, hoverInv;
    private boolean hoverRun;
    private double mx, my;

    public EditorOverlay(LevelScreen L) {
        this.L = L;
        this.cfg = L.cfg;
    }

    /** Set by the level when the panel's shadow is already part of the frozen background. */
    public boolean shadowBaked;

    public void renderShadow(Graphics2D g) { Draw.shadow(g, PX, PY, PW, PH, 12, 26, 0.55); }

    public void open(boolean canRun) {
        this.canRun = canRun;
        t = 0;
        drag = null;
        dragging = false;
        bounce = null;
    }

    // ---------------------------------------------------------------- model helpers

    private BlockDef blockIn(String slot) {
        String id = L.assignment.get(slot);
        return id == null ? null : cfg.blocks.get(id);
    }

    private String slotOf(String blockId) {
        for (Map.Entry<String, String> e : L.assignment.entrySet()) if (e.getValue().equals(blockId)) return e.getKey();
        return null;
    }

    private BlockDef inventoryBlock(String id) {
        for (BlockDef b : L.inventory) if (b.id().equals(id)) return b;
        return null;
    }

    private Rectangle2D.Double invClip() {
        return new Rectangle2D.Double(PX, PY + TITLE + 44, INV_W, PH - TITLE - CONSOLE - 44);
    }

    private double naturalW(BlockDef b) {
        double tw = Syntax.width(code, b.text());
        return b.kind() == BlockDef.Kind.LINE ? Math.max(200, tw + 40) : Math.max(52, tw + 30);
    }

    private double naturalH(BlockDef b) { return b.kind() == BlockDef.Kind.LINE ? 40 : 36; }

    // ---------------------------------------------------------------- layout

    private void layout() {
        invRects.clear();
        double x0 = PX + 18, iw = INV_W - 36;
        double top = PY + TITLE + 54;
        double y = top - scroll;
        for (BlockDef b : L.inventory) {
            if (b.kind() != BlockDef.Kind.LINE) continue;
            invRects.put(b.id(), new Rectangle2D.Double(x0, y, iw, 40));
            y += 52;
        }
        tokensHeaderY = -1;
        boolean anyToken = L.inventory.stream().anyMatch(b -> b.kind() == BlockDef.Kind.TOKEN);
        if (anyToken) {
            tokensHeaderY = y + 14;
            y += 32;
            double x = x0;
            for (BlockDef b : L.inventory) {
                if (b.kind() != BlockDef.Kind.TOKEN) continue;
                double cw = naturalW(b);
                if (x + cw > x0 + iw && x > x0) {
                    x = x0;
                    y += 60;
                }
                invRects.put(b.id(), new Rectangle2D.Double(x, y, cw, 36));
                x += cw + 12;
            }
            y += 60;
        }
        contentH = y + scroll - top;

        slotRects.clear();
        double cx0 = PX + INV_W, cy0 = PY + TITLE;
        for (int i = 0; i < cfg.skeleton.size(); i++) {
            String line = cfg.skeleton.get(i);
            double mid = cy0 + 40 + i * LINE_H;
            double x = cx0 + 76;
            Matcher m = LevelConfig.SLOT.matcher(line);
            int last = 0;
            while (m.find()) {
                x += Syntax.width(code, line.substring(last, m.start()));
                String id = m.group(1);
                BlockDef in = blockIn(id);
                if (cfg.slots.get(id) == BlockDef.Kind.TOKEN) {
                    double sw = in == null ? TOKEN_SLOT_W : Math.max(TOKEN_SLOT_W, naturalW(in));
                    slotRects.put(id, new Rectangle2D.Double(x, mid - 18, sw, 36));
                    x += sw;
                } else {
                    double sw = in == null ? LINE_SLOT_W : Math.max(LINE_SLOT_W, naturalW(in));
                    slotRects.put(id, new Rectangle2D.Double(x, mid - 20, sw, 40));
                    x += sw;
                }
                last = m.end();
            }
        }
        runRect.setRect(PX + PW - 190, PY + PH - CONSOLE - 68, 164, 46);
    }

    private String slotAt(double x, double y) {
        for (Map.Entry<String, Rectangle2D.Double> e : slotRects.entrySet()) {
            Rectangle2D.Double r = e.getValue();
            if (x >= r.x - 4 && x <= r.x + r.width + 4 && y >= r.y - 6 && y <= r.y + r.height + 6) return e.getKey();
        }
        return null;
    }

    private String invAt(double x, double y) {
        if (!invClip().contains(x, y)) return null;
        for (Map.Entry<String, Rectangle2D.Double> e : invRects.entrySet()) if (e.getValue().contains(x, y)) return e.getKey();
        return null;
    }

    // ---------------------------------------------------------------- update

    public void update(Input in) {
        t++;
        if (flashT > 0) flashT--;
        if (runHintFlash > 0) runHintFlash--;
        if (bounce != null && ++bounceT >= 10) bounce = null;
        layout();
        mx = in.mouseX;
        my = in.mouseY;
        Rectangle2D.Double clip = invClip();
        if (in.wheel != 0 && clip.contains(mx, my)) {
            double max = Math.max(0, contentH - clip.height + 30);
            scroll = Draw.clamp(scroll + in.wheel * 42, 0, max);
            layout();
        }
        hoverSlot = slotAt(mx, my);
        hoverInv = invAt(mx, my);
        hoverRun = runRect.contains(mx, my);

        if (drag == null) {
            if (in.pressedAny(KeyEvent.VK_ESCAPE, KeyEvent.VK_TAB)) {
                L.closeEditor();
                return;
            }
            if (in.mousePressed(Input.LEFT)) {
                if (hoverRun) {
                    tryRun();
                    return;
                } else if (hoverSlot != null && blockIn(hoverSlot) != null) {
                    beginDrag(blockIn(hoverSlot), slotRects.get(hoverSlot));
                } else if (hoverInv != null) {
                    BlockDef b = inventoryBlock(hoverInv);
                    if (in.clicks >= 2) autoPlace(b);
                    else beginDrag(b, invRects.get(hoverInv));
                }
            } else if (in.mousePressed(Input.RIGHT)) {
                if (hoverSlot != null && L.assignment.containsKey(hoverSlot)) {
                    L.assignment.remove(hoverSlot);
                    flash(hoverSlot, true);
                    L.sfx(Sfx.DROP);
                } else if (hoverInv != null && slotOf(hoverInv) != null) {
                    L.assignment.remove(slotOf(hoverInv));
                    L.sfx(Sfx.DROP);
                }
            }
        } else {
            if (!dragging && Math.hypot(mx - pressX, my - pressY) > 4) dragging = true;
            if (in.mouseReleased(Input.LEFT) || !in.mouseDown(Input.LEFT)) {
                if (dragging) drop();
                drag = null;
                dragging = false;
            }
        }
        boolean hand = drag != null || hoverRun || hoverInv != null || (hoverSlot != null && blockIn(hoverSlot) != null);
        L.game.setCursor(hand ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR);
    }

    private void beginDrag(BlockDef b, Rectangle2D.Double from) {
        drag = b;
        dragging = false;
        dragW = naturalW(b);
        dragH = naturalH(b);
        grabDX = Draw.clamp(mx - from.x, 6, dragW - 6);
        grabDY = Draw.clamp(my - from.y, 6, dragH - 6);
        pressX = mx;
        pressY = my;
    }

    private void drop() {
        String target = slotAt(mx, my);
        String src = slotOf(drag.id());
        if (target != null) {
            if (cfg.slots.get(target) != drag.kind()) {
                flash(target, false);
                startBounce();
                L.sfx(Sfx.ERROR, 0.6f);
                return;
            }
            String occupant = L.assignment.get(target);
            if (drag.id().equals(occupant)) return;
            if (src != null) {
                if (occupant != null) L.assignment.put(src, occupant);
                else L.assignment.remove(src);
            }
            L.assignment.put(target, drag.id());
            flash(target, true);
            L.sfx(Sfx.DROP);
        } else if (invClip().contains(mx, my)) {
            if (src != null) {
                L.assignment.remove(src);
                L.sfx(Sfx.DROP);
            }
        } else {
            startBounce();
        }
    }

    private void startBounce() {
        bounce = drag;
        bounceT = 0;
        bx0 = mx - grabDX;
        by0 = my - grabDY;
        bw = dragW;
        bh = dragH;
        String src = slotOf(drag.id());
        Rectangle2D.Double home = src != null ? slotRects.get(src) : invRects.get(drag.id());
        if (home == null) home = new Rectangle2D.Double(bx0, by0, bw, bh);
        bx1 = home.x;
        by1 = home.y;
    }

    private void autoPlace(BlockDef b) {
        if (slotOf(b.id()) != null) return;
        for (String s : cfg.slotOrder) {
            if (cfg.slots.get(s) == b.kind() && !L.assignment.containsKey(s)) {
                L.assignment.put(s, b.id());
                flash(s, true);
                L.sfx(Sfx.DROP);
                return;
            }
        }
        L.sfx(Sfx.ERROR, 0.5f);
    }

    private void flash(String slot, boolean ok) {
        flashSlot = slot;
        flashOk = ok;
        flashT = 22;
    }

    private void tryRun() {
        if (L.solved) {
            L.addConsole("Build already succeeded. Head to the exit }", Theme.OK, false);
            L.sfx(Sfx.CLICK);
            return;
        }
        if (!canRun) {
            L.addConsole("error: no javac terminal nearby. Go to a javac terminal to run.", Theme.ERR, false);
            runHintFlash = 50;
            L.sfx(Sfx.ERROR);
            return;
        }
        String empty = Judge.firstEmptySlot(cfg, L.assignment);
        if (empty != null) {
            L.addConsole("error: line " + cfg.slotLine.get(empty) + " has an empty slot", Theme.ERR, false);
            flash(empty, false);
            L.sfx(Sfx.ERROR);
            return;
        }
        L.addConsole("> javac " + cfg.file, Theme.TEXT_2, false);
        L.sfx(Sfx.CLICK);
        L.startRun();
    }

    // ---------------------------------------------------------------- render

    public void render(Graphics2D g) {
        double appear = Draw.easeOut(Math.min(1, t / 10.0));
        Composite oldC = Draw.pushAlpha(g, appear);
        AffineTransform oldT = g.getTransform();
        double s = 0.97 + 0.03 * appear;
        g.translate(Theme.W / 2.0, Theme.H / 2.0);
        g.scale(s, s);
        g.translate(-Theme.W / 2.0, -Theme.H / 2.0);

        if (!shadowBaked) renderShadow(g);
        Draw.panel(g, PX, PY, PW, PH, 12, Theme.BG, null);
        renderTitle(g);
        renderInventory(g);
        renderCode(g);
        renderConsole(g);
        Draw.panel(g, PX, PY, PW, PH, 12, null, Theme.BORDER_STRONG);

        if (dragging && drag != null) {
            double gx = mx - grabDX, gy = my - grabDY;
            Draw.shadow(g, gx, gy + 4, dragW, dragH, 6, 12, 0.45);
            AffineTransform before = g.getTransform();
            g.translate(gx + dragW / 2, gy + dragH / 2);
            g.rotate(Math.toRadians(-1.2));
            g.scale(1.04, 1.04);
            drawBlock(g, drag, -dragW / 2, -dragH / 2, dragW, dragH, 0.95, true);
            g.setTransform(before);
        }
        if (bounce != null) {
            double k = Draw.easeOut(bounceT / 10.0);
            drawBlock(g, bounce, Draw.lerp(bx0, bx1, k), Draw.lerp(by0, by1, k), bw, bh, 0.9, false);
        }
        g.setTransform(oldT);
        g.setComposite(oldC);
    }

    private void renderTitle(Graphics2D g) {
        Draw.panel(g, PX, PY, PW, TITLE, 12, Theme.PANEL, null);
        Draw.rect(g, PX, PY + TITLE - 12, PW, 12, Theme.PANEL);
        Draw.rect(g, PX, PY + TITLE, PW, 1, Theme.BORDER);
        Font f = Theme.bold(15);
        Draw.classIcon(g, PX + 18, PY + 14, 16, false);
        Draw.text(g, "Editor - " + cfg.file, PX + 44, Draw.mid(f, PY, TITLE), f, Theme.TEXT_BRIGHT);
        Font kf = Theme.mono(13);
        double base = Draw.mid(kf, PY, TITLE);
        double x = PX + PW - 20 - Draw.width(kf, "close");
        Draw.text(g, "close", x, base, kf, Theme.TEXT_2);
        x -= 10 + Math.max(Draw.width(kf, "Esc") + 12, 22);
        Draw.keycap(g, "Esc", x, base, kf, Theme.TEXT);
        x -= 18;
        Draw.text(g, "/", x + 4, base, kf, Theme.TEXT_DIM);
        x -= 6 + Math.max(Draw.width(kf, "Tab") + 12, 22);
        Draw.keycap(g, "Tab", x, base, kf, Theme.TEXT);
        String status = canRun ? "javac terminal: connected" : "javac terminal: not in reach";
        Font sf = Theme.mono(13);
        double sx = PX + PW / 2.0 - Draw.width(sf, status) / 2;
        Draw.circle(g, sx - 12, PY + TITLE / 2, 4, canRun ? Theme.OK : Theme.TEXT_DIM);
        Draw.text(g, status, sx, Draw.mid(sf, PY, TITLE), sf, canRun ? Theme.OK : Theme.TEXT_2);
    }

    private void renderInventory(Graphics2D g) {
        double top = PY + TITLE + 1, h = PH - TITLE - CONSOLE - 1;
        Draw.rect(g, PX, top, INV_W, h, Theme.PANEL_DARK);
        Draw.rect(g, PX + INV_W - 1, top, 1, h, Theme.BORDER);
        Font hf = Theme.bold(15), sf = Theme.mono(13);
        Draw.text(g, "Inventory", PX + 18, top + 28, hf, Theme.TEXT_BRIGHT);
        Draw.right(g, "Blocks " + L.inventory.size(), PX + INV_W - 18, top + 28, sf, Theme.TEXT_2);
        Draw.rect(g, PX + 18, top + 40, INV_W - 36, 1, Theme.BORDER);

        Rectangle2D.Double clip = invClip();
        Shape oldClip = g.getClip();
        g.clip(clip);
        if (L.inventory.isEmpty()) {
            Font f = Theme.mono(14);
            Draw.center(g, "No blocks yet.", PX + INV_W / 2, top + 120, f, Theme.TEXT_2);
            Draw.center(g, "Explore the level to collect code.", PX + INV_W / 2, top + 144, Theme.mono(13), Theme.TEXT_DIM);
        }
        if (tokensHeaderY > 0) Draw.text(g, "Tokens", PX + 18, tokensHeaderY, Theme.mono(12), Theme.TEXT_2);
        Font lf = Theme.mono(11.5);
        for (BlockDef b : L.inventory) {
            Rectangle2D.Double r = invRects.get(b.id());
            if (r == null) continue;
            String slot = slotOf(b.id());
            boolean beingDragged = dragging && drag != null && drag.id().equals(b.id());
            if (beingDragged) {
                Draw.dashed(g, Draw.rr(r.x + 0.5, r.y + 0.5, r.width - 1, r.height - 1, 6), Theme.TEXT_DIM, 1.2, 4);
                continue;
            }
            boolean hover = b.id().equals(hoverInv) && drag == null;
            double a = slot != null ? 0.4 : 1;
            if (b.kind() == BlockDef.Kind.LINE) {
                drawBlock(g, b, r.x, r.y, r.width, r.height, a, hover);
                if (slot != null) {
                    String label = "in line " + cfg.slotLine.get(slot);
                    double lw = Draw.width(lf, label) + 12;
                    Draw.panel(g, r.x + r.width - lw - 8, r.y + 11, lw, 18, 4, Theme.SELECT, null);
                    Draw.text(g, label, r.x + r.width - lw - 2, Draw.mid(lf, r.y + 11, 18), lf, Theme.TEXT_BRIGHT);
                }
            } else {
                drawBlock(g, b, r.x, r.y, r.width, r.height, a, hover);
                if (slot != null) Draw.center(g, "used", r.x + r.width / 2, r.y + r.height + 15, lf, Theme.TEXT_2);
            }
        }
        g.setClip(oldClip);
        if (contentH > clip.height) {
            double barH = clip.height * clip.height / (contentH + 30);
            double barY = clip.y + (clip.height - barH) * (scroll / Math.max(1, contentH - clip.height + 30));
            Draw.panel(g, PX + INV_W - 8, barY, 4, barH, 2, Theme.BORDER_STRONG, null);
        }
        Font tip = Theme.mono(12);
        double ty = top + h - 16;
        Draw.text(g, "Drag into a slot   Right-click: return", PX + 18, ty, tip, Theme.TEXT_DIM);
    }

    private void renderCode(Graphics2D g) {
        double cx0 = PX + INV_W, cy0 = PY + TITLE + 1;
        double cw = PW - INV_W, ch = PH - TITLE - CONSOLE - 1;
        Draw.rect(g, cx0, cy0, 56, ch, Draw.alpha(Theme.PANEL_DARK, 0.6));
        Draw.rect(g, cx0 + 56, cy0, 1, ch, Theme.BORDER);
        Font lf = Theme.mono(14);
        for (int i = 0; i < cfg.skeleton.size(); i++) {
            String line = cfg.skeleton.get(i);
            double mid = PY + TITLE + 40 + i * LINE_H;
            double base = mid + Draw.cap(code) / 2;
            boolean dropLine = false;
            if (dragging) {
                for (Map.Entry<String, Integer> e : cfg.slotLine.entrySet()) {
                    if (e.getValue() == i + 1 && e.getKey().equals(slotAt(mx, my))) dropLine = true;
                }
            }
            if (dropLine) Draw.rect(g, cx0 + 57, mid - LINE_H / 2, cw - 57, LINE_H, Draw.alpha(Theme.SELECT, 0.35));
            Draw.right(g, String.valueOf(i + 1), cx0 + 40, base, lf, dropLine ? Theme.TEXT : Theme.LINE_NO);
            double x = cx0 + 76;
            Matcher m = LevelConfig.SLOT.matcher(line);
            int last = 0;
            while (m.find()) {
                x = Syntax.draw(g, line.substring(last, m.start()), x, base, code, 1);
                String id = m.group(1);
                Rectangle2D.Double r = slotRects.get(id);
                drawSlot(g, id, r);
                x = r.x + r.width;
                last = m.end();
            }
            Syntax.draw(g, line.substring(last), x, base, code, 1);
        }

        // Run button and hint
        boolean ready = canRun && !L.solved && Judge.firstEmptySlot(cfg, L.assignment) == null;
        Rectangle2D.Double r = runRect;
        Color fill;
        Color border;
        Color text;
        if (L.solved) {
            fill = Draw.alpha(Theme.OK, 0.15);
            border = Theme.OK;
            text = Theme.OK;
        } else if (!canRun) {
            fill = null;
            border = Theme.BORDER_STRONG;
            text = Theme.TEXT_2;
        } else if (ready) {
            fill = hoverRun ? Draw.mix(Theme.OK_FILL, Color.WHITE, 0.12) : Theme.OK_FILL;
            border = null;
            text = Color.WHITE;
        } else {
            fill = null;
            border = Theme.OK_FILL;
            text = Theme.OK;
        }
        if (ready) Draw.glow(g, r.getCenterX(), r.getCenterY(), 110, Theme.OK, 0.18 + 0.08 * Math.sin(t * 0.1));
        if (hoverRun && !ready) fill = Draw.alpha(Theme.TEXT, 0.06);
        Draw.panel(g, r.x, r.y, r.width, r.height, 8, fill, border);
        Font bf = Theme.bold(16);
        String label = L.solved ? "Built" : "Run";
        double lw = Draw.width(bf, label) + 22;
        double lx = r.getCenterX() - lw / 2;
        if (L.solved) Draw.check(g, lx, r.y + 15, 14, text);
        else Draw.play(g, lx + 2, r.y + 15, 14, text);
        Draw.text(g, label, lx + 22, Draw.mid(bf, r.y, r.height), bf, text);
        String hint = null;
        Color hc = Theme.TEXT_2;
        if (L.solved) {
            hint = "Program built. Head to the exit }";
            hc = Theme.OK;
        } else if (!canRun) {
            hint = "Go to a javac terminal to run";
            if (runHintFlash > 0 && (runHintFlash / 5) % 2 == 0) hc = Theme.ERR;
        } else if (!ready) {
            hint = "Fill every slot, then run";
        }
        if (hint != null) {
            Font hf = Theme.mono(14);
            Draw.right(g, hint, r.x - 18, Draw.mid(hf, r.y, r.height), hf, hc);
        }
    }

    private void drawSlot(Graphics2D g, String id, Rectangle2D.Double r) {
        BlockDef b = blockIn(id);
        BlockDef.Kind kind = cfg.slots.get(id);
        boolean beingDragged = dragging && drag != null && b != null && b.id().equals(drag.id());
        boolean over = dragging && id.equals(slotAt(mx, my));
        boolean match = over && drag.kind() == kind;
        if (b != null && !beingDragged) {
            drawBlock(g, b, r.x, r.y, r.width, r.height, 1, id.equals(hoverSlot) && drag == null);
        } else {
            Color dc = over ? (match ? Theme.OK : Theme.ERR) : Theme.TEXT_DIM;
            Draw.panel(g, r.x, r.y, r.width, r.height, 6, Draw.alpha(dc, over ? 0.12 : 0.05), null);
            Draw.dashed(g, Draw.rr(r.x + 0.5, r.y + 0.5, r.width - 1, r.height - 1, 6), Draw.alpha(dc, 0.9), 1.3, 5);
            if (kind == BlockDef.Kind.LINE) {
                Font f = Theme.italic(13.5);
                Draw.center(g, "Drop a line here", r.getCenterX(), Draw.mid(f, r.y, r.height), f, Draw.alpha(dc, 0.9));
            } else {
                Font f = Theme.bold(16);
                Draw.center(g, "?", r.getCenterX(), Draw.mid(f, r.y, r.height), f, Draw.alpha(dc, 0.9));
            }
        }
        if (over) {
            g.setColor(match ? Theme.OK : Theme.ERR);
            g.setStroke(Draw.stroke(2));
            g.draw(Draw.rr(r.x - 1, r.y - 1, r.width + 2, r.height + 2, 7));
        }
        if (id.equals(flashSlot) && flashT > 0) {
            double a = flashT / 22.0;
            Color c = flashOk ? Theme.OK : Theme.ERR;
            Draw.glow(g, r.getCenterX(), r.getCenterY(), Math.max(r.width, 60) * 0.7, c, 0.35 * a);
            g.setColor(Draw.alpha(c, a));
            g.setStroke(Draw.stroke(2));
            g.draw(Draw.rr(r.x - 2, r.y - 2, r.width + 4, r.height + 4, 8));
        }
    }

    private void drawBlock(Graphics2D g, BlockDef b, double x, double y, double w, double h, double alpha, boolean hover) {
        Composite old = Draw.pushAlpha(g, alpha);
        double tw = Syntax.width(code, b.text());
        double base = Draw.mid(code, y, h);
        if (b.kind() == BlockDef.Kind.LINE) {
            Draw.panel(g, x, y, w, h, 6, new Color(0x2F3237), hover ? Theme.ACCENT_HOVER : Theme.BORDER_STRONG);
            Draw.rect(g, x + 7, y + 9, 3, h - 18, Theme.ACCENT);
            Syntax.draw(g, b.text(), x + 20, base, code, 1);
        } else {
            Draw.panel(g, x, y, w, h, 6, new Color(0x393B40), hover ? Theme.ACCENT_HOVER : Theme.BORDER_STRONG);
            Syntax.draw(g, b.text(), x + (w - tw) / 2, base, code, 1);
        }
        g.setComposite(old);
    }

    private void renderConsole(Graphics2D g) {
        double top = PY + PH - CONSOLE;
        Draw.rect(g, PX, top, PW, 1, Theme.BORDER);
        Draw.rect(g, PX, top + 1, PW, CONSOLE - 13, Theme.PANEL_DARK);
        Draw.panel(g, PX, PY + PH - 24, PW, 24, 12, Theme.PANEL_DARK, null);
        Font hf = Theme.bold(13);
        Draw.text(g, "Console", PX + 18, top + 24, hf, Theme.TEXT_BRIGHT);
        Draw.rect(g, PX + 18, top + 31, Draw.width(hf, "Console"), 2, Theme.ACCENT);
        Font f = Theme.mono(14);
        List<LevelScreen.ConsoleLine> lines = L.console;
        int from = Math.max(0, lines.size() - 4);
        double y = top + 60;
        for (int i = from; i < lines.size(); i++) {
            LevelScreen.ConsoleLine ln = lines.get(i);
            String text = ln.text().startsWith(">") ? ln.text() : "> " + ln.text();
            Draw.text(g, text, PX + 18, y, f, ln.color());
            if (ln.memory()) {
                Font mf = Theme.bold(13);
                Draw.right(g, "Memory -1", PX + PW - 20, y, mf, Theme.ERR);
            }
            y += 22;
        }
    }
}
