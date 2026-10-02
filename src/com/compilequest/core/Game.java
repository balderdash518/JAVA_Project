package com.compilequest.core;

import com.compilequest.level.LevelConfig;
import com.compilequest.level.LevelScreen;
import com.compilequest.ui.MenuScreen;

import java.awt.Graphics2D;
import java.util.List;
import java.util.concurrent.locks.LockSupport;

/** Owns the shared services and runs the fixed-step game loop (60 updates per second). */
public final class Game {
    public static final int FPS = 60;

    public final Input input = new Input();
    public final Sound sound;
    public final Save save;
    public final List<LevelConfig> levels;
    public final ScreenManager screens = new ScreenManager(this);
    public final boolean headless;
    public long frame;

    private GameWindow window;
    private final SelfTest selfTest = System.getProperty("cq.selftest") != null ? new SelfTest(this) : null;
    private volatile boolean running = true;
    private String lastError;

    public Game(boolean headless) {
        this.headless = headless;
        Theme.initFonts();
        this.save = headless ? Save.inMemory() : Save.load(Save.defaultPath());
        this.sound = new Sound(!headless);
        this.sound.setVolume((float) save.sfxVolume);
        this.levels = LevelConfig.loadAll();
    }

    public void start() {
        String level = System.getProperty("cq.level");
        if (level != null) screens.set(new LevelScreen(this, Integer.parseInt(level)));
        else screens.set(new MenuScreen(this, -1));
        window = new GameWindow(this, save.fullscreen);
        Thread loop = new Thread(this::loop, "game-loop");
        loop.start();
    }

    private void loop() {
        final long step = 1_000_000_000L / FPS;
        long next = System.nanoTime();
        while (running) {
            long now = System.nanoTime();
            if (now < next) {
                // Sleep most of the wait, then yield for the last moment: Windows sleeps are coarse.
                long wait = next - now;
                if (wait > 3_500_000L) LockSupport.parkNanos(wait - 3_000_000L);
                else Thread.yield();
                continue;
            }
            int ticks = 0;
            while (now >= next && ticks < 5) {
                tick();
                next += step;
                ticks++;
            }
            if (now - next > step * 5) next = now + step;
            long r0 = System.nanoTime();
            window.render(this::render);
            if (selfTest != null) selfTest.frame(System.nanoTime() - r0);
        }
    }

    public void tick() {
        try {
            input.poll();
            screens.update();
            frame++;
        } catch (RuntimeException e) {
            report(e);
        }
    }

    public void render(Graphics2D g) {
        try {
            screens.render(g);
        } catch (RuntimeException e) {
            report(e);
        }
    }

    private void report(RuntimeException e) {
        String key = String.valueOf(e);
        if (key.equals(lastError)) return;
        lastError = key;
        e.printStackTrace();
    }

    public void openLevel(int index) {
        if (index < 0 || index >= levels.size()) {
            openMenu(-1);
            return;
        }
        screens.go(new LevelScreen(this, index));
    }

    public void openMenu(int select) { screens.go(new MenuScreen(this, select)); }

    public void setFullscreen(boolean fs) {
        save.fullscreen = fs;
        save.write();
        if (window != null) window.setFullscreen(fs);
    }

    public void setCursor(int type) {
        if (window != null) window.setCursor(type);
    }

    public void quit() {
        running = false;
        save.write();
        System.exit(0);
    }
}
