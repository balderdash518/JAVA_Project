package com.compilequest.core;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Every color, size and font used by the game (DESIGN.md section 2). */
public final class Theme {
    private Theme() {}

    public static final int W = 1440;
    public static final int H = 900;
    public static final int TILE = 32;

    // Interface
    public static final Color BG = rgb(0x1E1F22);
    public static final Color BG_DEEP = rgb(0x18191B);
    public static final Color PANEL = rgb(0x2B2D30);
    public static final Color PANEL_DARK = rgb(0x232427);
    public static final Color BORDER = rgb(0x393B40);
    public static final Color BORDER_STRONG = rgb(0x4E5157);
    public static final Color SELECT = rgb(0x2E436E);
    public static final Color HOVER = rgb(0x323438);
    public static final Color TEXT = rgb(0xBCBEC4);
    public static final Color TEXT_BRIGHT = rgb(0xDFE1E5);
    public static final Color TEXT_2 = rgb(0x7A7E85);
    public static final Color TEXT_DIM = rgb(0x5A5D63);
    public static final Color LINE_NO = rgb(0x4B4D52);
    public static final Color ACCENT = rgb(0x3574F0);
    public static final Color ACCENT_HOVER = rgb(0x4A84F5);
    public static final Color OK = rgb(0x6AAB73);
    public static final Color OK_FILL = rgb(0x57965C);
    public static final Color ERR = rgb(0xFF6B68);
    public static final Color WARN = rgb(0xE0A050);

    // Syntax highlighting
    public static final Color SYN_KEYWORD = rgb(0xCC7832);
    public static final Color SYN_STRING = rgb(0x6A8759);
    public static final Color SYN_NUMBER = rgb(0x6897BB);
    public static final Color SYN_METHOD = rgb(0xFFC66D);
    public static final Color SYN_CODE = rgb(0xA9B7C6);
    public static final Color SYN_COMMENT = rgb(0x808080);
    public static final Color SYN_DOC = rgb(0x629755);
    public static final Color SYN_FIELD = rgb(0x9876AA);

    // World
    public static final Color WALL = rgb(0x9876AA);
    public static final Color WALL_FILL = rgb(0x26232C);
    public static final Color PLATFORM = rgb(0x629755);
    public static final Color SPRING = rgb(0xFFC66D);
    public static final Color SPIKE = rgb(0xFF6B68);
    public static final Color BREAK = rgb(0xCC7832);
    public static final Color TRANSIENT = rgb(0xA9B7C6);
    public static final Color VOLATILE = rgb(0x8888C6);
    public static final Color MOVER = rgb(0x6897BB);
    public static final Color FINAL = rgb(0x808080);
    public static final Color CHECKPOINT = rgb(0x4EC9B0);
    public static final Color EXIT = rgb(0xFF79C6);
    public static final Color WATER = rgb(0x3D6A99);
    public static final Color PICKUP_BG = rgb(0x2E436E);
    public static final Color PLAYER = rgb(0xFFFFFF);
    public static final Color ARROW = rgb(0xCC7832);
    public static final Color BUG = rgb(0xF28B54);
    public static final Color EXCEPTION = rgb(0xBC3F3C);
    public static final Color GC = rgb(0x6AAB73);
    public static final Color STAR = rgb(0xFFD700);
    public static final Color KEY = rgb(0x6897BB);
    public static final Color DOOR = rgb(0xD5B778);
    public static final Color BRIDGE = rgb(0xE6E6E6);
    public static final Color NUMBER = rgb(0x6897BB);

    private static String family;
    private static final Map<Long, Font> FONTS = new HashMap<>();

    /** Picks the best installed monospace font; a bundled JetBrains Mono in res/fonts wins. */
    public static synchronized void initFonts() {
        if (family != null) return;
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        for (String file : new String[] {"fonts/JetBrainsMono-Regular.ttf", "fonts/JetBrainsMono-Bold.ttf", "fonts/JetBrainsMono-Italic.ttf"}) {
            try (InputStream in = Res.open(file)) {
                if (in != null) ge.registerFont(Font.createFont(Font.TRUETYPE_FONT, in));
            } catch (Exception ignored) {
                // A broken font file just falls back to the system fonts.
            }
        }
        Set<String> names = new HashSet<>(Arrays.asList(ge.getAvailableFontFamilyNames()));
        for (String pref : new String[] {"JetBrains Mono", "Cascadia Mono", "Cascadia Code", "Consolas", "DejaVu Sans Mono", "Menlo", "Lucida Console"}) {
            if (names.contains(pref)) {
                family = pref;
                break;
            }
        }
        if (family == null) family = Font.MONOSPACED;
    }

    public static String fontFamily() {
        if (family == null) initFonts();
        return family;
    }

    public static Font mono(double size) { return font(Font.PLAIN, size); }
    public static Font bold(double size) { return font(Font.BOLD, size); }
    public static Font italic(double size) { return font(Font.ITALIC, size); }
    public static Font boldItalic(double size) { return font(Font.BOLD | Font.ITALIC, size); }

    public static synchronized Font font(int style, double size) {
        long key = ((long) style << 32) | (Float.floatToIntBits((float) size) & 0xffffffffL);
        Font f = FONTS.get(key);
        if (f == null) {
            f = new Font(fontFamily(), style, 12).deriveFont(style, (float) size);
            FONTS.put(key, f);
        }
        return f;
    }

    public static Color rgb(int hex) { return new Color(hex); }
}
