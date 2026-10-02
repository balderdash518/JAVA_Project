package com.compilequest.core;

import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Keyboard and mouse state. AWT events are queued on the event thread and applied once per
 * tick on the game thread, so "pressed" and "released" are true for exactly one tick.
 */
public final class Input implements KeyListener, MouseListener, MouseMotionListener, MouseWheelListener, FocusListener {
    private static final int KEYS = 1024;
    private static final int KEY_DOWN = 0, KEY_UP = 1, MOUSE_DOWN = 2, MOUSE_UP = 3, MOUSE_MOVE = 4, WHEEL = 5, CLEAR = 6;

    public static final int LEFT = 0, RIGHT = 1, MIDDLE = 2;

    private final boolean[] down = new boolean[KEYS];
    private final boolean[] pressed = new boolean[KEYS];
    private final boolean[] released = new boolean[KEYS];
    private final boolean[] mDown = new boolean[3];
    private final boolean[] mPressed = new boolean[3];
    private final boolean[] mReleased = new boolean[3];
    private final ConcurrentLinkedQueue<double[]> queue = new ConcurrentLinkedQueue<>();

    private volatile double scale = 1, offX, offY;

    public double mouseX = -1000, mouseY = -1000;
    public int clicks;
    public double wheel;
    public boolean anyPressed;

    /** Maps window pixels to the 1440x900 logical canvas. */
    public void setTransform(double s, double ox, double oy) {
        scale = s;
        offX = ox;
        offY = oy;
    }

    public void poll() {
        Arrays.fill(pressed, false);
        Arrays.fill(released, false);
        Arrays.fill(mPressed, false);
        Arrays.fill(mReleased, false);
        wheel = 0;
        anyPressed = false;
        double[] e;
        while ((e = queue.poll()) != null) {
            switch ((int) e[0]) {
                case KEY_DOWN -> {
                    int k = (int) e[1];
                    if (k >= 0 && k < KEYS) {
                        if (!down[k]) {
                            pressed[k] = true;
                            anyPressed = true;
                        }
                        down[k] = true;
                    }
                }
                case KEY_UP -> {
                    int k = (int) e[1];
                    if (k >= 0 && k < KEYS) {
                        if (down[k]) released[k] = true;
                        down[k] = false;
                    }
                }
                case MOUSE_DOWN -> {
                    int b = (int) e[1];
                    mouseX = e[2];
                    mouseY = e[3];
                    if (b >= 0) {
                        if (!mDown[b]) {
                            mPressed[b] = true;
                            anyPressed = true;
                        }
                        mDown[b] = true;
                        clicks = (int) e[4];
                    }
                }
                case MOUSE_UP -> {
                    int b = (int) e[1];
                    mouseX = e[2];
                    mouseY = e[3];
                    if (b >= 0) {
                        if (mDown[b]) mReleased[b] = true;
                        mDown[b] = false;
                    }
                }
                case MOUSE_MOVE -> {
                    mouseX = e[2];
                    mouseY = e[3];
                }
                case WHEEL -> wheel += e[1];
                case CLEAR -> {
                    Arrays.fill(down, false);
                    Arrays.fill(mDown, false);
                }
                default -> { }
            }
        }
    }

    /** Forgets this tick's presses so a key that switched screens is not handled twice. */
    public void consume() {
        Arrays.fill(pressed, false);
        Arrays.fill(released, false);
        Arrays.fill(mPressed, false);
        Arrays.fill(mReleased, false);
        anyPressed = false;
        wheel = 0;
    }

    public boolean down(int key) { return key >= 0 && key < KEYS && down[key]; }
    public boolean pressed(int key) { return key >= 0 && key < KEYS && pressed[key]; }
    public boolean released(int key) { return key >= 0 && key < KEYS && released[key]; }
    public boolean mouseDown(int b) { return mDown[b]; }
    public boolean mousePressed(int b) { return mPressed[b]; }
    public boolean mouseReleased(int b) { return mReleased[b]; }

    public boolean pressedAny(int... keys) {
        for (int k : keys) if (pressed(k)) return true;
        return false;
    }

    public boolean downAny(int... keys) {
        for (int k : keys) if (down(k)) return true;
        return false;
    }

    public boolean releasedAny(int... keys) {
        for (int k : keys) if (released(k)) return true;
        return false;
    }

    public boolean mouseIn(double x, double y, double w, double h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    /** Feeds synthetic events (used by the snapshot tool). */
    public void injectKey(int key, boolean isDown) {
        queue.add(new double[] {isDown ? KEY_DOWN : KEY_UP, key});
    }

    public void injectMouse(double x, double y) {
        queue.add(new double[] {MOUSE_MOVE, 0, x, y});
    }

    public void injectButton(int button, boolean isDown, double x, double y) {
        queue.add(new double[] {isDown ? MOUSE_DOWN : MOUSE_UP, button, x, y, 1});
    }

    private double lx(int x) { return (x - offX) / scale; }
    private double ly(int y) { return (y - offY) / scale; }

    private static int button(MouseEvent e) {
        return switch (e.getButton()) {
            case MouseEvent.BUTTON1 -> LEFT;
            case MouseEvent.BUTTON3 -> RIGHT;
            case MouseEvent.BUTTON2 -> MIDDLE;
            default -> -1;
        };
    }

    @Override public void keyPressed(KeyEvent e) { queue.add(new double[] {KEY_DOWN, e.getKeyCode()}); }
    @Override public void keyReleased(KeyEvent e) { queue.add(new double[] {KEY_UP, e.getKeyCode()}); }
    @Override public void keyTyped(KeyEvent e) { }

    @Override public void mousePressed(MouseEvent e) {
        queue.add(new double[] {MOUSE_DOWN, button(e), lx(e.getX()), ly(e.getY()), e.getClickCount()});
    }

    @Override public void mouseReleased(MouseEvent e) {
        queue.add(new double[] {MOUSE_UP, button(e), lx(e.getX()), ly(e.getY()), 0});
    }

    @Override public void mouseMoved(MouseEvent e) { queue.add(new double[] {MOUSE_MOVE, 0, lx(e.getX()), ly(e.getY())}); }
    @Override public void mouseDragged(MouseEvent e) { queue.add(new double[] {MOUSE_MOVE, 0, lx(e.getX()), ly(e.getY())}); }
    @Override public void mouseWheelMoved(MouseWheelEvent e) { queue.add(new double[] {WHEEL, e.getPreciseWheelRotation()}); }
    @Override public void mouseClicked(MouseEvent e) { }
    @Override public void mouseEntered(MouseEvent e) { }
    @Override public void mouseExited(MouseEvent e) { }
    @Override public void focusGained(FocusEvent e) { }
    @Override public void focusLost(FocusEvent e) { queue.add(new double[] {CLEAR}); }
}
