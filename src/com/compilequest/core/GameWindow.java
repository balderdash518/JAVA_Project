package com.compilequest.core;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;

/**
 * The window and its canvas. Everything is drawn on a 1440x900 logical canvas that is scaled
 * to fit the screen, so the game looks the same on any resolution and stays sharp on HiDPI.
 */
public final class GameWindow {
    private final Game game;
    private final Object lock = new Object();
    private JFrame frame;
    private Canvas canvas;
    private volatile boolean fullscreen;
    private int cursor = Cursor.DEFAULT_CURSOR;

    public GameWindow(Game game, boolean fullscreen) {
        this.game = game;
        this.fullscreen = fullscreen;
        try {
            SwingUtilities.invokeAndWait(this::build);
        } catch (Exception e) {
            throw new IllegalStateException("Could not open the game window", e);
        }
    }

    private void build() {
        frame = new JFrame("Compile Quest");
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { game.quit(); }
        });
        frame.setIconImage(icon());
        canvas = new Canvas();
        canvas.setBackground(Color.BLACK);
        canvas.setIgnoreRepaint(true);
        canvas.setFocusTraversalKeysEnabled(false);
        // A Chinese/Japanese IME would swallow WASD into its composition window.
        canvas.enableInputMethods(false);
        Input in = game.input;
        canvas.addKeyListener(in);
        canvas.addMouseListener(in);
        canvas.addMouseMotionListener(in);
        canvas.addMouseWheelListener(in);
        canvas.addFocusListener(in);
        frame.add(canvas);
        applyMode();
    }

    private void applyMode() {
        if (frame.isDisplayable()) frame.dispose();
        frame.setUndecorated(fullscreen);
        if (fullscreen) {
            Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice().getDefaultConfiguration().getBounds();
            frame.setBounds(screen);
        } else {
            canvas.setPreferredSize(new Dimension(1280, 800));
            frame.pack();
            frame.setLocationRelativeTo(null);
        }
        frame.setVisible(true);
        canvas.createBufferStrategy(2);
        canvas.requestFocus();
    }

    public void setFullscreen(boolean fs) {
        if (fs == fullscreen) return;
        fullscreen = fs;
        SwingUtilities.invokeLater(() -> {
            synchronized (lock) {
                applyMode();
            }
        });
    }

    public void setCursor(int type) {
        if (type == cursor || canvas == null) return;
        cursor = type;
        canvas.setCursor(Cursor.getPredefinedCursor(type));
    }

    public void render(Consumer<Graphics2D> painter) {
        synchronized (lock) {
            if (canvas == null || !canvas.isDisplayable()) return;
            BufferStrategy bs = canvas.getBufferStrategy();
            int cw = canvas.getWidth(), ch = canvas.getHeight();
            if (bs == null || cw <= 0 || ch <= 0) return;
            double scale = Math.min(cw / (double) Theme.W, ch / (double) Theme.H);
            double ox = (cw - Theme.W * scale) / 2, oy = (ch - Theme.H * scale) / 2;
            game.input.setTransform(scale, ox, oy);
            do {
                do {
                    Graphics2D g = (Graphics2D) bs.getDrawGraphics();
                    try {
                        g.setColor(Color.BLACK);
                        g.fillRect(0, 0, cw, ch);
                        g.translate(ox, oy);
                        g.scale(scale, scale);
                        g.clipRect(0, 0, Theme.W, Theme.H);
                        hints(g);
                        painter.accept(g);
                    } finally {
                        g.dispose();
                    }
                } while (bs.contentsRestored());
                bs.show();
            } while (bs.contentsLost());
            Toolkit.getDefaultToolkit().sync();
        }
    }

    public static void hints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    }

    private static BufferedImage icon() {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        hints(g);
        Draw.panel(g, 2, 2, 60, 60, 14, Theme.BG, Theme.BORDER_STRONG);
        Font f = Theme.bold(34);
        Draw.center(g, "{}", 32, Draw.mid(f, 2, 60), f, Theme.EXIT);
        g.dispose();
        return img;
    }
}
