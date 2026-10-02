package com.compilequest.ui;

import com.compilequest.core.Draw;
import com.compilequest.core.Game;
import com.compilequest.core.Input;
import com.compilequest.core.Save;
import com.compilequest.core.Screen;
import com.compilequest.core.Sound.Sfx;
import com.compilequest.core.Theme;
import com.compilequest.editor.Syntax;
import com.compilequest.level.LevelConfig;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** The main menu, styled as an IDE: project tree, file preview, console and status bar (DESIGN.md 3.1). */
public final class MenuScreen implements Screen {
    private static final double TITLE_H = 32, BAR_H = 28, TOP = TITLE_H + BAR_H;
    private static final double LEFT_W = 340, CONSOLE_H = 160, STATUS_H = 24;
    private static final double EDIT_BOTTOM = Theme.H - STATUS_H - CONSOLE_H;
    private static final double TAB_H = 34, ROW = 28, TREE_Y = TOP + 44;
    private static final String[] MENUS = {"File", "Run", "Help"};

    private enum Kind { FOLDER, LEVEL, SETTINGS, README }

    private record Row(Kind kind, String name, int indent, int level) {
        boolean selectable() { return kind != Kind.FOLDER; }
    }

    private record Line(String text, Color color) { }

    private final Game game;
    private final List<Row> rows = new ArrayList<>();
    private final List<Line> console = new ArrayList<>();
    private final BufferedImage[] previews;
    private final SettingsPanel settings;
    private ConfirmDialog confirm;
    private int sel;
    private int hover = -1;
    private int t;
    private boolean settingsFocus;
    private int openMenu = -1;
    private int menuHover = -1;
    private int launching = -1;
    private int launchT;
    private final Rectangle2D.Double runRect = new Rectangle2D.Double();
    private double mx, my;

    public MenuScreen(Game game, int select) {
        this.game = game;
        rows.add(new Row(Kind.FOLDER, "CompileQuest", 0, -1));
        rows.add(new Row(Kind.FOLDER, "src", 1, -1));
        for (int i = 0; i < game.levels.size(); i++) rows.add(new Row(Kind.LEVEL, game.levels.get(i).file, 2, i));
        rows.add(new Row(Kind.FOLDER, "assets", 1, -1));
        rows.add(new Row(Kind.SETTINGS, "settings.json", 1, -1));
        rows.add(new Row(Kind.README, "README.md", 1, -1));
        previews = new BufferedImage[game.levels.size()];
        for (int i = 0; i < previews.length; i++) previews[i] = preview(game.levels.get(i));
        settings = new SettingsPanel(game, () -> confirm = new ConfirmDialog("Reset save?", "All progress and stars will be erased.", "Reset",
                () -> {
                    game.save.reset();
                    confirm = null;
                    log("Save reset. Only Level0 is unlocked.", Theme.WARN);
                },
                () -> confirm = null));

        int level = select;
        if (level < 0) {
            level = Math.min(game.save.unlocked, game.levels.size() - 1);
            for (int i = 0; i <= Math.min(game.save.unlocked, game.levels.size() - 1); i++) {
                if (!game.save.rec(i).cleared) {
                    level = i;
                    break;
                }
            }
        }
        sel = rowOfLevel(Math.max(0, level));
        log("Compile Quest 1.0  |  a platformer written in code", Theme.TEXT_BRIGHT);
        log("Double-click a .java file (or press Enter) to start a level.", Theme.TEXT_2);
        if (game.save.clearedCount() >= game.levels.size() && !game.levels.isEmpty()) {
            log("Every level compiles. Can you find all hidden stars?", Theme.OK);
        }
    }

    private int rowOfLevel(int level) {
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).level == level) return i;
        return 2;
    }

    private void log(String text, Color c) {
        console.add(new Line(text, c));
        while (console.size() > 30) console.remove(0);
    }

    private boolean unlocked(int level) { return level <= game.save.unlocked; }

    private Row current() { return rows.get(sel); }

    // ---------------------------------------------------------------- update

    @Override
    public void update(Game g) {
        t++;
        Input in = g.input;
        mx = in.mouseX;
        my = in.mouseY;
        if (confirm != null) {
            confirm.update(g);
            return;
        }
        if (launching >= 0) {
            if (++launchT == 24) game.openLevel(launching);
            return;
        }
        if (openMenu >= 0) {
            updateDropdown(in);
            return;
        }
        boolean hand = false;

        // Title bar: red dot quits.
        if (Math.hypot(mx - 20, my - TITLE_H / 2) < 8) {
            hand = true;
            if (in.mousePressed(Input.LEFT)) {
                game.quit();
                return;
            }
        }
        // Menu bar.
        int barItem = barItemAt(mx, my);
        if (barItem >= 0) {
            hand = true;
            if (in.mousePressed(Input.LEFT)) {
                openMenu = barItem;
                game.sound.play(Sfx.CLICK);
                return;
            }
        }

        // Project tree.
        hover = -1;
        if (mx >= 0 && mx < LEFT_W && my >= TREE_Y) {
            int r = (int) ((my - TREE_Y) / ROW);
            if (r >= 0 && r < rows.size() && rows.get(r).selectable()) hover = r;
        }
        if (hover >= 0) {
            hand = true;
            if (in.mousePressed(Input.LEFT)) {
                if (hover != sel) select(hover);
                if (in.clicks >= 2) activate();
            }
        }

        // Editor area.
        Row cur = current();
        if (cur.kind == Kind.SETTINGS) {
            hand |= settings.update(in, settingsFocus);
            if (in.mousePressed(Input.LEFT) && mx > LEFT_W && my > TOP && my < EDIT_BOTTOM) settingsFocus = true;
        }
        if (cur.kind == Kind.LEVEL && runRect.contains(mx, my)) {
            hand = true;
            if (in.mousePressed(Input.LEFT)) activate();
        }

        // Keyboard.
        if (settingsFocus) {
            if (in.pressedAny(KeyEvent.VK_ESCAPE, KeyEvent.VK_LEFT)) {
                settingsFocus = false;
                game.sound.play(Sfx.CLICK);
            }
        } else {
            if (in.pressedAny(KeyEvent.VK_W, KeyEvent.VK_UP)) move(-1);
            if (in.pressedAny(KeyEvent.VK_S, KeyEvent.VK_DOWN)) move(1);
            if (in.pressedAny(KeyEvent.VK_ENTER, KeyEvent.VK_SPACE, KeyEvent.VK_RIGHT)) activate();
        }
        g.setCursor(hand ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR);
    }

    private void move(int dir) {
        int i = sel;
        do {
            i = (i + dir + rows.size()) % rows.size();
        } while (!rows.get(i).selectable());
        select(i);
    }

    private void select(int i) {
        sel = i;
        settingsFocus = false;
        game.sound.play(Sfx.HOVER);
    }

    private void activate() {
        Row r = current();
        switch (r.kind) {
            case LEVEL -> {
                if (!unlocked(r.level)) {
                    log("error: " + r.name + " is locked. Clear " + game.levels.get(r.level - 1).file.replace(".java", "") + " first.", Theme.ERR);
                    game.sound.play(Sfx.ERROR);
                    return;
                }
                log("> javac " + r.name + " ... done", Theme.OK);
                game.sound.play(Sfx.BUILD_OK, 0.6f, 1.2f);
                launching = r.level;
                launchT = 0;
                game.setCursor(Cursor.DEFAULT_CURSOR);
            }
            case SETTINGS -> {
                settingsFocus = true;
                game.sound.play(Sfx.CLICK);
            }
            case README -> game.sound.play(Sfx.CLICK);
            default -> { }
        }
    }

    private int barItemAt(double x, double y) {
        if (y < TITLE_H || y >= TOP) return -1;
        double bx = 12;
        Font f = Theme.mono(14);
        for (int i = 0; i < MENUS.length; i++) {
            double w = Draw.width(f, MENUS[i]) + 20;
            if (x >= bx && x < bx + w) return i;
            bx += w;
        }
        return -1;
    }

    private String[] menuItems(int m) {
        return switch (m) {
            case 0 -> new String[] {"Settings", "Quit"};
            case 1 -> new String[] {"Run selected level"};
            default -> new String[] {"Controls (README.md)"};
        };
    }

    private double menuX(int m) {
        double bx = 12;
        Font f = Theme.mono(14);
        for (int i = 0; i < m; i++) bx += Draw.width(f, MENUS[i]) + 20;
        return bx;
    }

    private void updateDropdown(Input in) {
        String[] items = menuItems(openMenu);
        double x = menuX(openMenu), y = TOP + 2, w = 230;
        menuHover = -1;
        for (int i = 0; i < items.length; i++) if (in.mouseIn(x, y + 6 + i * 32, w, 32)) menuHover = i;
        game.setCursor(menuHover >= 0 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR);
        if (in.pressed(KeyEvent.VK_ESCAPE)) {
            openMenu = -1;
            return;
        }
        if (in.mousePressed(Input.LEFT)) {
            int m = openMenu;
            openMenu = -1;
            int other = barItemAt(mx, my);
            if (menuHover < 0) {
                if (other >= 0 && other != m) openMenu = other;
                return;
            }
            game.sound.play(Sfx.CLICK);
            switch (m) {
                case 0 -> {
                    if (menuHover == 0) {
                        sel = rows.indexOf(rows.stream().filter(r -> r.kind == Kind.SETTINGS).findFirst().orElse(rows.get(0)));
                        settingsFocus = true;
                    } else {
                        game.quit();
                    }
                }
                case 1 -> {
                    if (current().kind == Kind.LEVEL) activate();
                    else log("Select a level file first.", Theme.WARN);
                }
                default -> sel = rows.indexOf(rows.stream().filter(r -> r.kind == Kind.README).findFirst().orElse(rows.get(0)));
            }
        }
    }

    // ---------------------------------------------------------------- render

    private BufferedImage cache;
    private String cacheKey;
    private double cacheScale;

    /** Everything that can change what the menu looks like. The picture is redrawn only when this changes. */
    private String stateKey() {
        Save sv = game.save;
        return sel + "|" + hover + "|" + settingsFocus + "|" + openMenu + "|" + menuHover + "|" + console.size()
                + "|" + sv.unlocked + "|" + sv.totalStars() + "|" + sv.clearedCount() + "|" + sv.sfxVolume
                + "|" + sv.fullscreen + "|" + sv.showDebug + "|" + settings.stateKey()
                + "|" + (Math.hypot(mx - 20, my - TITLE_H / 2) < 8) + "|" + barItemAt(mx, my)
                + "|" + runRect.contains(mx, my) + "|" + (t / 30) % 2;
    }

    @Override
    public void render(Graphics2D g) {
        String key = stateKey();
        double s = Draw.deviceScale(g);
        if (cache == null || cacheScale != s || !key.equals(cacheKey)) {
            if (cache == null || cacheScale != s) {
                cache = new BufferedImage((int) Math.round(Theme.W * s), (int) Math.round(Theme.H * s), BufferedImage.TYPE_INT_RGB);
                cacheScale = s;
            }
            Graphics2D cg = cache.createGraphics();
            com.compilequest.core.GameWindow.hints(cg);
            cg.scale(s, s);
            renderStatic(cg);
            cg.dispose();
            cacheKey = stateKey();
        }
        Draw.blit(g, cache, 0, 0);
        if (launching >= 0) {
            double k = Draw.easeInOut(launchT / 24.0);
            Draw.rect(g, 0, 0, Theme.W, Theme.H, new Color(0, 0, 0, (int) (120 * k)));
        }
        if (confirm != null) confirm.render(g);
    }

    private void renderStatic(Graphics2D g) {
        Draw.rect(g, 0, 0, Theme.W, Theme.H, Theme.BG);
        renderTitleBar(g);
        renderTree(g);
        renderEditor(g);
        renderConsole(g);
        renderStatus(g);
        if (openMenu >= 0) renderDropdown(g);
    }

    private void renderTitleBar(Graphics2D g) {
        Draw.rect(g, 0, 0, Theme.W, TOP, Theme.PANEL);
        Draw.rect(g, 0, TOP - 1, Theme.W, 1, Theme.BORDER);
        boolean quitHover = Math.hypot(mx - 20, my - TITLE_H / 2) < 8 && openMenu < 0;
        Draw.circle(g, 20, TITLE_H / 2, 6, new Color(0xFF5F57));
        Draw.circle(g, 40, TITLE_H / 2, 6, new Color(0xFEBC2E));
        Draw.circle(g, 60, TITLE_H / 2, 6, new Color(0x28C840));
        if (quitHover) {
            Draw.cross(g, 20, TITLE_H / 2, 2.5, new Color(0x7A1A16), 1.4);
            Font f = Theme.mono(12);
            Draw.panel(g, 12, TITLE_H + 2, Draw.width(f, "Quit") + 16, 22, 4, Theme.BG_DEEP, Theme.BORDER_STRONG);
            Draw.text(g, "Quit", 20, TITLE_H + 17, f, Theme.TEXT);
        }
        Font tf = Theme.mono(13);
        Draw.center(g, "Compile Quest - CompileQuest [~/src]", Theme.W / 2.0, Draw.mid(tf, 0, TITLE_H), tf, Theme.TEXT_2);
        Font mf = Theme.mono(14);
        double bx = 12;
        int barHover = openMenu >= 0 ? openMenu : barItemAt(mx, my);
        for (int i = 0; i < MENUS.length; i++) {
            double w = Draw.width(mf, MENUS[i]) + 20;
            if (i == barHover) Draw.panel(g, bx, TITLE_H + 2, w, BAR_H - 5, 5, Theme.HOVER, null);
            Draw.text(g, MENUS[i], bx + 10, Draw.mid(mf, TITLE_H, BAR_H - 2), mf, Theme.TEXT);
            bx += w;
        }
    }

    private void renderTree(Graphics2D g) {
        Draw.rect(g, 0, TOP, LEFT_W, EDIT_BOTTOM - TOP, Theme.PANEL);
        Draw.rect(g, LEFT_W, TOP, 1, EDIT_BOTTOM - TOP, Theme.BORDER);
        Font hf = Theme.bold(13);
        Draw.text(g, "Project", 16, TOP + 26, hf, Theme.TEXT_BRIGHT);
        Draw.chevron(g, 80, TOP + 17, true, Theme.TEXT_2);
        Font f = Theme.mono(14);
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            double y = TREE_Y + i * ROW;
            if (i == sel) Draw.rect(g, 0, y, LEFT_W, ROW, settingsFocus ? Draw.alpha(Theme.SELECT, 0.5) : Theme.SELECT);
            else if (i == hover) Draw.rect(g, 0, y, LEFT_W, ROW, Theme.HOVER);
            double x = 14 + r.indent * 20;
            double base = Draw.mid(f, y, ROW);
            switch (r.kind) {
                case FOLDER -> {
                    boolean open = !r.name.equals("assets");
                    Draw.chevron(g, x, y + (open ? 11 : 10), open, Theme.TEXT_2);
                    Draw.folder(g, x + 14, y + 6, new Color(0x8C9AAF));
                    Draw.text(g, r.name, x + 38, base, f, Theme.TEXT);
                }
                case LEVEL -> renderLevelRow(g, r, x, y, base, f);
                case SETTINGS -> {
                    Draw.fileIcon(g, x + 14, y + 6, Theme.SYN_NUMBER);
                    Draw.text(g, r.name, x + 38, base, f, Theme.TEXT);
                }
                case README -> {
                    Draw.fileIcon(g, x + 14, y + 6, Theme.TEXT_2);
                    Draw.text(g, r.name, x + 38, base, f, Theme.TEXT);
                }
            }
        }
        Font tip = Theme.mono(12);
        Draw.text(g, "W/S select   Enter open", 16, EDIT_BOTTOM - 16, tip, Theme.TEXT_DIM);
    }

    private void renderLevelRow(Graphics2D g, Row r, double x, double y, double base, Font f) {
        boolean open = unlocked(r.level);
        Save.Rec rec = game.save.rec(r.level);
        if (!open) {
            Draw.lock(g, x, y + 7, 12, Theme.TEXT_DIM);
        } else if (rec.cleared) {
            Draw.check(g, x, y + 8, 12, Theme.OK);
        } else {
            Draw.circle(g, x + 6, y + ROW / 2, 4, Theme.ACCENT_HOVER);
        }
        Draw.classIcon(g, x + 18, y + 6, 16, !open);
        Draw.text(g, r.name, x + 42, base, f, open ? Theme.TEXT : Theme.TEXT_DIM);
        int count = Math.max(1, game.levels.get(r.level).starCount);
        for (int k = 0; k < Math.min(3, count); k++) {
            double sx = LEFT_W - 62 + k * 17, sy = y + ROW / 2;
            if (rec.stars[k]) {
                g.setColor(Theme.STAR);
                g.fill(Draw.starShape(sx, sy, 6, 0));
            } else if (open) {
                Draw.star(g, sx, sy, 6, false, Theme.TEXT_DIM);
            }
        }
    }

    private void renderEditor(Graphics2D g) {
        double x0 = LEFT_W + 1;
        Draw.rect(g, x0, TOP, Theme.W - x0, TAB_H, Theme.PANEL);
        Draw.rect(g, x0, TOP + TAB_H - 1, Theme.W - x0, 1, Theme.BORDER);
        Row cur = current();
        Font tf = Theme.mono(14);
        double tabW = Draw.width(tf, cur.name) + 70;
        Draw.rect(g, x0, TOP, tabW, TAB_H, Theme.BG);
        Draw.rect(g, x0, TOP + TAB_H - 2, tabW, 2, Theme.ACCENT);
        switch (cur.kind) {
            case LEVEL -> Draw.classIcon(g, x0 + 14, TOP + 9, 16, !unlocked(cur.level));
            case SETTINGS -> Draw.fileIcon(g, x0 + 14, TOP + 9, Theme.SYN_NUMBER);
            default -> Draw.fileIcon(g, x0 + 14, TOP + 9, Theme.TEXT_2);
        }
        Draw.text(g, cur.name, x0 + 38, Draw.mid(tf, TOP, TAB_H), tf, Theme.TEXT_BRIGHT);
        Draw.cross(g, x0 + tabW - 16, TOP + TAB_H / 2, 3.5, Theme.TEXT_DIM, 1.3);

        double top = TOP + TAB_H;
        switch (cur.kind) {
            case LEVEL -> renderLevelPreview(g, x0, top, cur.level);
            case SETTINGS -> {
                renderGutterLines(g, x0, top, 0);
                settings.render(g, x0 + 70, top + 30, settingsFocus);
                Font hf = Theme.mono(13);
                String hint = settingsFocus ? "W/S choose   A/D change   Enter toggle   Esc back" : "Click a value or press Enter to edit";
                Draw.text(g, hint, x0 + 70, top + 30 + settings.height() + 34, hf, Theme.TEXT_DIM);
            }
            case README -> renderReadme(g, x0, top);
            default -> { }
        }
    }

    private void renderGutterLines(Graphics2D g, double x0, double top, int count) {
        Draw.rect(g, x0, top, 50, EDIT_BOTTOM - top, Draw.alpha(Theme.PANEL_DARK, 0.5));
        Font lf = Theme.mono(14);
        for (int i = 0; i < count; i++) {
            Draw.right(g, String.valueOf(i + 1), x0 + 38, top + 34 + i * 26, lf, Theme.LINE_NO);
        }
    }

    private void renderLevelPreview(Graphics2D g, double x0, double top, int level) {
        LevelConfig c = game.levels.get(level);
        boolean open = unlocked(level);
        Save.Rec rec = game.save.rec(level);
        List<String> lines = new ArrayList<>();
        lines.add("/**");
        lines.add(" * Level " + level + ": " + c.title);
        lines.add(" *");
        if (open) {
            lines.add(" * Task: " + c.task);
            lines.add(" * New:  " + (c.news.isEmpty() ? "-" : String.join(", ", c.news)));
            String best = rec.bestMs >= 0 ? ClearOverlay.time(rec.bestMs) : "not cleared";
            lines.add(" * Best: " + best + "   Stars: " + rec.starCount() + "/" + Math.max(1, c.starCount));
        } else {
            lines.add(" * Locked.");
            lines.add(" * Clear " + game.levels.get(level - 1).file + " to unlock.");
        }
        lines.add(" */");
        lines.add("public class " + c.className() + " extends Level { }");
        renderGutterLines(g, x0, top, lines.size());
        Font f = Theme.mono(15);
        double y = top + 34;
        for (int i = 0; i < lines.size(); i++) {
            String s = lines.get(i);
            double base = y + i * 26;
            if (s.startsWith("/**") || s.startsWith(" *")) {
                Draw.text(g, s, x0 + 70, base, f, Theme.SYN_DOC);
            } else {
                double end = Syntax.draw(g, s, x0 + 70, base, f, 1);
                if ((t / 30) % 2 == 0) Draw.rect(g, end + 2, base - Draw.cap(f) - 2, 2, Draw.cap(f) + 5, Theme.TEXT);
            }
        }

        double by = y + lines.size() * 26 + 22;
        runRect.setRect(x0 + 70, by, 250, 52);
        boolean hov = runRect.contains(mx, my) && open;
        if (open) {
            Draw.glow(g, runRect.getCenterX(), runRect.getCenterY(), 110, Theme.ACCENT, 0.2);
            Draw.panel(g, runRect.x, runRect.y, runRect.width, runRect.height, 8, hov ? Theme.ACCENT_HOVER : Theme.ACCENT, null);
            Font bf = Theme.bold(17);
            String label = "Run " + c.className();
            double lw = Draw.width(bf, label) + 24;
            double lx = runRect.getCenterX() - lw / 2;
            Draw.play(g, lx, runRect.y + 18, 16, Color.WHITE);
            Draw.text(g, label, lx + 24, Draw.mid(bf, runRect.y, runRect.height), bf, Color.WHITE);
        } else {
            Draw.panel(g, runRect.x, runRect.y, runRect.width, runRect.height, 8, null, Theme.BORDER_STRONG);
            Font bf = Theme.bold(17);
            Draw.lock(g, runRect.x + 76, runRect.y + 17, 15, Theme.TEXT_DIM);
            Draw.text(g, "Locked", runRect.x + 100, Draw.mid(bf, runRect.y, runRect.height), bf, Theme.TEXT_DIM);
        }
        Font kf = Theme.mono(13);
        double kb = Draw.mid(kf, runRect.y, runRect.height);
        double kx = runRect.x + runRect.width + 18;
        if (open) {
            kx += Draw.keycap(g, "Enter", kx, kb, kf, Theme.TEXT) + 8;
            Draw.text(g, "or double-click the file", kx, kb, kf, Theme.TEXT_DIM);
        }

        // Map preview.
        double px = x0 + 640, py = top + 30, pw = Theme.W - px - 36, ph = 300;
        Draw.text(g, "// map preview", px, py - 8, Theme.mono(13), Theme.SYN_COMMENT);
        Draw.panel(g, px, py, pw, ph, 10, Theme.BG_DEEP, Theme.BORDER);
        BufferedImage img = previews[level];
        double s = Math.min((pw - 24) / img.getWidth(), (ph - 24) / img.getHeight());
        double iw = img.getWidth() * s, ih = img.getHeight() * s;
        double ix = px + (pw - iw) / 2, iy = py + (ph - ih) / 2;
        Object oldHint = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        var old = Draw.pushAlpha(g, open ? 1 : 0.25);
        g.drawImage(img, (int) ix, (int) iy, (int) iw, (int) ih, null);
        g.setComposite(old);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldHint);
        if (!open) {
            Draw.lock(g, px + pw / 2 - 14, py + ph / 2 - 20, 28, Theme.TEXT_2);
            Draw.center(g, "locked", px + pw / 2, py + ph / 2 + 36, Theme.mono(14), Theme.TEXT_2);
        }
        // Legend.
        String[] names = {"code block", "javac", "exit", "try {", "hazard"};
        Color[] cols = {Theme.ACCENT_HOVER, Theme.ACCENT, Theme.EXIT, Theme.CHECKPOINT, Theme.SPIKE};
        Font lf = Theme.mono(12);
        double lx = px, ly = py + ph + 22;
        for (int i = 0; i < names.length; i++) {
            Draw.panel(g, lx, ly - 9, 10, 10, 2, cols[i], null);
            Draw.text(g, names[i], lx + 16, ly, lf, Theme.TEXT_2);
            lx += Draw.width(lf, names[i]) + 36;
        }

        renderLogo(g, px, py + ph + 70, pw);
    }

    private void renderLogo(Graphics2D g, double x, double y, double w) {
        Font big = Theme.bold(46), sub = Theme.mono(15);
        String word = "Compile Quest";
        double bw = Draw.width(big, "{ ");
        double ww = Draw.width(big, word);
        double total = bw * 2 + ww;
        double sx = x + (w - total) / 2;
        double base = y + 60;
        double bob = 0;
        Draw.glow(g, x + w / 2, base - 16, 170, Theme.EXIT, 0.1);
        Draw.text(g, "{", sx, base + bob, big, Theme.EXIT);
        Draw.text(g, word, sx + bw, base, big, Theme.TEXT_BRIGHT);
        Draw.text(g, "}", sx + bw + ww + Draw.width(big, " "), base - bob, big, Theme.EXIT);
        Draw.center(g, "// collect code, build programs, escape the source", x + w / 2, base + 34, sub, Theme.SYN_COMMENT);
    }

    private void renderReadme(Graphics2D g, double x0, double top) {
        String[] lines = {
            "# Compile Quest",
            "",
            "Explore levels made of code. Collect code blocks,",
            "assemble the program in the editor, and run it at a",
            "javac terminal. What the program prints changes the map.",
            "",
            "## Controls",
            "A / D           Move",
            "W / Space       Jump (hold to jump higher)",
            "S               Drop through // comment platforms",
            "Click / J       Shoot an arrow ->",
            "E               Use a javac terminal",
            "Tab             Open the editor",
            "Esc             Pause      R  Restart level",
            "",
            "## Editor",
            "Drag blocks into slots. Drop on a filled slot to swap.",
            "Right-click a placed block to send it back.",
            "A wrong program costs 1 Memory, so read before you run.",
        };
        renderGutterLines(g, x0, top, lines.length);
        Font f = Theme.mono(15), h1 = Theme.bold(19), h2 = Theme.bold(16);
        for (int i = 0; i < lines.length; i++) {
            String s = lines[i];
            double base = top + 34 + i * 26;
            if (s.startsWith("# ")) {
                Draw.text(g, s, x0 + 70, base, h1, Theme.EXIT);
            } else if (s.startsWith("## ")) {
                Draw.text(g, s, x0 + 70, base, h2, Theme.SYN_KEYWORD);
            } else if (i >= 7 && i <= 13) {
                Draw.text(g, s.substring(0, Math.min(16, s.length())), x0 + 70, base, f, Theme.SYN_METHOD);
                if (s.length() > 16) Draw.text(g, s.substring(16), x0 + 70 + Draw.adv(f) * 16, base, f, Theme.TEXT);
            } else {
                Draw.text(g, s, x0 + 70, base, f, Theme.TEXT);
            }
        }
    }

    private void renderConsole(Graphics2D g) {
        double y = EDIT_BOTTOM;
        Draw.rect(g, 0, y, Theme.W, 1, Theme.BORDER);
        Draw.rect(g, 0, y + 1, Theme.W, CONSOLE_H - 1, Theme.PANEL_DARK);
        Font hf = Theme.bold(13);
        Draw.text(g, "Console", 16, y + 24, hf, Theme.TEXT_BRIGHT);
        Draw.rect(g, 16, y + 31, Draw.width(hf, "Console"), 2, Theme.ACCENT);
        Font f = Theme.mono(14);
        int from = Math.max(0, console.size() - 5);
        double ly = y + 58;
        for (int i = from; i < console.size(); i++) {
            Line ln = console.get(i);
            Draw.text(g, (ln.text.startsWith(">") ? "" : "> ") + ln.text, 16, ly, f, ln.color);
            ly += 21;
        }
    }

    private void renderStatus(Graphics2D g) {
        double y = Theme.H - STATUS_H;
        Draw.rect(g, 0, y, Theme.W, STATUS_H, Theme.PANEL);
        Draw.rect(g, 0, y, Theme.W, 1, Theme.BORDER);
        Font f = Theme.mono(12);
        double base = Draw.mid(f, y, STATUS_H);
        Draw.text(g, "UTF-8     LF     Java " + System.getProperty("java.specification.version", "17"), 16, base, f, Theme.TEXT_2);
        int total = 0;
        for (LevelConfig c : game.levels) total += Math.max(0, c.starCount);
        String levels = "Levels " + game.save.clearedCount() + "/" + game.levels.size();
        double rx = Theme.W - 16 - Draw.width(f, levels);
        Draw.text(g, levels, rx, base, f, Theme.TEXT_2);
        String stars = game.save.totalStars() + "/" + total;
        rx -= 30 + Draw.width(f, stars);
        Draw.text(g, stars, rx, base, f, Theme.TEXT_2);
        g.setColor(Theme.STAR);
        g.fill(Draw.starShape(rx - 12, y + STATUS_H / 2, 6, 0));
    }

    private void renderDropdown(Graphics2D g) {
        String[] items = menuItems(openMenu);
        double x = menuX(openMenu), y = TOP + 2, w = 230, h = items.length * 32 + 12;
        Draw.shadow(g, x, y, w, h, 8, 12, 0.5);
        Draw.panel(g, x, y, w, h, 8, Theme.PANEL, Theme.BORDER_STRONG);
        Font f = Theme.mono(14);
        for (int i = 0; i < items.length; i++) {
            double ry = y + 6 + i * 32;
            if (i == menuHover) Draw.panel(g, x + 6, ry, w - 12, 30, 5, Theme.SELECT, null);
            Draw.text(g, items[i], x + 18, Draw.mid(f, ry, 30), f, Theme.TEXT_BRIGHT);
        }
    }

    // ---------------------------------------------------------------- map preview

    private static BufferedImage preview(LevelConfig c) {
        int s = 4;
        int w = 1;
        for (String r : c.map) w = Math.max(w, r.length());
        int h = Math.max(1, c.map.size());
        BufferedImage img = new BufferedImage(w * s, h * s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        for (int y = 0; y < c.map.size(); y++) {
            String row = c.map.get(y);
            for (int x = 0; x < row.length(); x++) {
                Color col = previewColor(row.charAt(x), c);
                if (col == null) continue;
                g.setColor(col);
                g.fillRect(x * s, y * s, s, s);
            }
        }
        g.dispose();
        return img;
    }

    private static Color previewColor(char ch, LevelConfig c) {
        return switch (ch) {
            case '#' -> Draw.mix(Theme.WALL, Theme.BG, 0.5);
            case '-' -> Theme.PLATFORM;
            case '+' -> Theme.SPRING;
            case '^', 'v' -> Theme.SPIKE;
            case 'B' -> Theme.BREAK;
            case 'T' -> Draw.alpha(Theme.TRANSIENT, 0.7);
            case 'V' -> Theme.VOLATILE;
            case 'F' -> Theme.FINAL;
            case '~' -> Theme.WATER;
            case 'L', 'Q' -> Theme.NUMBER;
            case 'D' -> Theme.DOOR;
            case 'M' -> Theme.MOVER;
            case 'S' -> Theme.CHECKPOINT;
            case 'E' -> Theme.EXIT;
            case 'J' -> Theme.ACCENT;
            case 'P' -> Color.WHITE;
            case 'b' -> Theme.BUG;
            case 'x' -> Theme.EXCEPTION;
            case 'G' -> Theme.GC;
            case 'K' -> Theme.KEY;
            case '_' -> Theme.TEXT_DIM;
            default -> c.pickups.containsKey(ch) ? Theme.ACCENT_HOVER : null;
        };
    }
}
