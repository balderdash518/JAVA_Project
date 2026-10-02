package com.compilequest.level;

import com.compilequest.core.Draw;
import com.compilequest.core.Game;
import com.compilequest.core.Input;
import com.compilequest.core.Res;
import com.compilequest.core.Save;
import com.compilequest.core.Screen;
import com.compilequest.core.Sound.Sfx;
import com.compilequest.core.Theme;
import com.compilequest.editor.BlockDef;
import com.compilequest.editor.EditorOverlay;
import com.compilequest.entity.Arrow;
import com.compilequest.entity.Bug;
import com.compilequest.entity.Checkpoint;
import com.compilequest.entity.ErrorText;
import com.compilequest.entity.ExceptionTurret;
import com.compilequest.entity.Exit;
import com.compilequest.entity.FloatText;
import com.compilequest.entity.MovingPlatform;
import com.compilequest.entity.Particles;
import com.compilequest.entity.Pickup;
import com.compilequest.entity.Player;
import com.compilequest.entity.Terminal;
import com.compilequest.entity.ThrowShot;
import com.compilequest.entity.Tuning;
import com.compilequest.ui.ClearOverlay;
import com.compilequest.ui.ConfirmDialog;
import com.compilequest.ui.DeathOverlay;
import com.compilequest.ui.PauseOverlay;
import com.compilequest.ui.SettingsDialog;
import com.compilequest.world.Camera;
import com.compilequest.world.TileMap;
import com.compilequest.world.TileType;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One level being played: the world, its entities, and every overlay on top of it. */
public final class LevelScreen implements Screen, Player.Host {
    public enum Mode { PLAYING, EDITOR, RUNNING, PAUSED, DEAD, CLEARED }

    private static final int T = TileMap.T;

    public final Game game;
    public final int index;
    public final LevelConfig cfg;
    public TileMap map;
    public final Camera cam = new Camera();
    public Player player;
    public final List<Bug> bugs = new ArrayList<>();
    public final List<ExceptionTurret> turrets = new ArrayList<>();
    public final List<ThrowShot> shots = new ArrayList<>();
    public final List<Arrow> arrows = new ArrayList<>();
    public final List<Pickup> pickups = new ArrayList<>();
    public final List<Checkpoint> checkpoints = new ArrayList<>();
    public final List<Terminal> terminals = new ArrayList<>();
    public final List<MovingPlatform> platforms = new ArrayList<>();
    public final List<FloatText> floats = new ArrayList<>();
    public final List<ErrorText> errorTexts = new ArrayList<>();
    public final Particles particles = new Particles();
    public final Map<Character, List<int[]>> anchors = new HashMap<>();
    public Exit exit;

    public int[] pillarValues, pillarInitial;
    public int pillarX, pillarY, pillarMaxH;

    public final List<BlockDef> inventory = new ArrayList<>();
    public final Map<String, String> assignment = new LinkedHashMap<>();
    public final List<ConsoleLine> console = new ArrayList<>();
    public boolean hasKey, solved;
    public final boolean[] stars = new boolean[3];
    public double spawnX, spawnY;
    public int runs, fails, time;
    public long playFrames;
    public Mode mode = Mode.PLAYING;
    public String deathCause = "Player.hurt";
    public int hurtFlash, okFlash, whiteFlash;

    final Hud hud;
    private EditorOverlay editor;
    private RunSequence run;
    private PauseOverlay pause;
    private ConfirmDialog confirm;
    private DeathOverlay death;
    private ClearOverlay clear;
    private SettingsDialog settings;
    private int debugParam;

    public record ConsoleLine(String text, Color color, boolean memory) { }

    public LevelScreen(Game game, int index) {
        this.game = game;
        this.index = index;
        this.cfg = game.levels.get(index);
        LevelLoader.load(this);
        cam.setBounds(map.w * T, map.h * T);
        cam.snap(player.cx(), player.cy());
        hud = new Hud(this);
        map.backgroundLines = buildBackground();
        console.add(new ConsoleLine("Collect code blocks, then run the program at a javac terminal.", Theme.TEXT_2, false));
    }

    // ---------------------------------------------------------------- helpers used by effects and entities

    public void sfx(Sfx s) { game.sound.play(s); }
    public void sfx(Sfx s, float vol) { game.sound.play(s, vol, 1f); }
    public void sfxVary(Sfx s, float vol) { game.sound.vary(s, vol); }

    public int[] anchor(char c) {
        List<int[]> l = anchors.get(c);
        return l == null || l.isEmpty() ? null : l.get(0);
    }

    public List<int[]> anchors(char c) { return anchors.getOrDefault(c, new ArrayList<>()); }

    public void addConsole(String text, Color color, boolean memory) {
        console.add(new ConsoleLine(text, color, memory));
        while (console.size() > 40) console.remove(0);
    }

    public void floatText(String text, Color c, double x, double y) {
        floats.add(new FloatText(text, c, x, y, 70, 15));
    }

    public Map<String, String> flags() {
        Map<String, String> f = new HashMap<>();
        f.put("key", hasKey ? "yes" : "no");
        return f;
    }

    public void breakTile(int tx, int ty) {
        map.set(tx, ty, TileType.EMPTY, ' ', false);
        particles.burst((tx + 0.5) * T, (ty + 0.5) * T, "break", Theme.BREAK, 8, 3.5, 15);
        sfx(Sfx.BREAK);
        cam.addShake(2.5);
    }

    public void killBug(Bug b) {
        b.dead = true;
        particles.burst(b.cx(), b.cy(), "Bug^v", Theme.BUG, 12, 4, 15);
        floatText("// fixed", Theme.OK, b.cx(), b.y - 6);
        sfx(Sfx.BUG_DIE);
        cam.addShake(2);
    }

    public void buildPillar(int i, int value, boolean log) {
        int unit = cfg.pillarUnit;
        int x0 = pillarX + i * unit;
        int height = value * unit;
        for (int c = 0; c < unit; c++) {
            for (int k = 0; k < Math.max(pillarMaxH, height); k++) {
                int row = pillarY - k;
                if (k < height) {
                    char g = '|';
                    if (k == height - 1) g = c == 0 ? Character.forDigit(Math.min(9, value), 10) : ' ';
                    map.set(x0 + c, row, TileType.PILLAR, g, log);
                } else if (map.get(x0 + c, row) == TileType.PILLAR) {
                    map.set(x0 + c, row, TileType.EMPTY, ' ', log);
                }
            }
        }
    }

    public void highlightPillars(int j, int count) {
        map.highlightX0 = pillarX + j * cfg.pillarUnit;
        map.highlightX1 = pillarX + (j + count) * cfg.pillarUnit - 1;
        map.highlightTimer = 14;
        sfxVary(Sfx.TYPE, 0.4f);
    }

    public void swapPillars(int j) {
        int a = pillarValues[j];
        pillarValues[j] = pillarValues[j + 1];
        pillarValues[j + 1] = a;
        buildPillar(j, pillarValues[j], true);
        buildPillar(j + 1, pillarValues[j + 1], true);
        double x = (pillarX + (j + 1) * cfg.pillarUnit) * T;
        particles.burst(x, (pillarY - 2) * T, "<>", Theme.NUMBER, 6, 3, 14);
        sfxVary(Sfx.POP, 0.8f);
        player.pushOut(map);
    }

    public void collapsePillars() {
        for (int i = 0; i < pillarValues.length; i++) {
            pillarValues[i] = 0;
            buildPillar(i, 0, true);
            for (int c = 0; c < cfg.pillarUnit; c++) {
                map.set(pillarX + i * cfg.pillarUnit + c, pillarY, TileType.PILLAR, c == 0 ? '?' : ' ', true);
            }
        }
        double cx = (pillarX + pillarValues.length * cfg.pillarUnit / 2.0) * T;
        particles.burst(cx, (pillarY - 3) * T, "0123456789|[]", Theme.NUMBER, 40, 6, 17);
        sfx(Sfx.BREAK);
        cam.addShake(10);
    }

    /** Undoes everything the previous program did to the map. */
    public void revertRun() {
        map.revertChanges();
        bugs.removeIf(b -> b.fromRun);
        errorTexts.clear();
        if (pillarInitial != null) pillarValues = pillarInitial.clone();
        player.pushOut(map);
    }

    public boolean debug() { return game.save.showDebug; }

    public boolean nearTerminal() {
        for (Terminal t : terminals) if (t.inReach(player)) return true;
        return false;
    }

    // ---------------------------------------------------------------- Player.Host

    @Override public TileMap map() { return map; }
    @Override public List<MovingPlatform> platforms() { return platforms; }

    @Override public void onJump(Player p) {
        sfxVary(Sfx.JUMP, 0.7f);
        particles.dust(p.cx(), p.y + Player.H, 3);
    }

    @Override public void onDoubleJump(Player p) {
        game.sound.play(Sfx.JUMP, 0.7f, 1.3f);
        particles.ring(p.cx(), p.y + Player.H, Theme.TEXT_BRIGHT);
        particles.glyph(p.cx(), p.y + Player.H + 4, '^', Theme.TEXT_2, 0, 1.2, 16, 14, 0);
    }

    @Override public void onLand(Player p, double impact) {
        if (impact > 5) {
            sfx(Sfx.LAND, (float) Math.min(1, impact / 11));
            particles.dust(p.cx(), p.y + Player.H, 5);
        }
    }

    @Override public void onSpring(Player p, int tx, int ty) {
        map.bounceSpring(tx, ty);
        sfx(Sfx.SPRING);
        particles.burst((tx + 0.5) * T, ty * T, "++", Theme.SPRING, 5, 2.5, 13);
    }

    @Override public void onHeadBreak(Player p, int tx, int ty) { breakTile(tx, ty); }
    @Override public void onTransient(Player p, int tx, int ty) { map.triggerTransient(tx, ty); }

    @Override public void onSpike(Player p, int tx, int ty) {
        if (hurtPlayer((tx + 0.5) * T, "NullSpike.onTouch") && player.hp > 0) player.vy = -8;
    }

    @Override public void onWater(Player p) { drown("Water.drown"); }
    @Override public void onFallOut(Player p) { drown("World.fallOut"); }

    @Override public void onShoot(Player p) {
        arrows.add(new Arrow(p.facing > 0 ? p.x + Player.W - 4 : p.x - 20, p.y + 15, p.facing));
        sfxVary(Sfx.SHOOT, 0.6f);
    }

    // ---------------------------------------------------------------- damage and death

    public boolean hurtPlayer(double fromX, String cause) {
        if (mode != Mode.PLAYING) return false;
        if (!player.hurt(fromX)) return false;
        hud.loseMemory(player.hp);
        deathCause = cause;
        hurtFlash = 26;
        cam.addShake(6);
        sfx(Sfx.HURT);
        particles.sparks(player.cx(), player.cy(), Theme.ERR, 10);
        if (player.hp <= 0) die(cause);
        return true;
    }

    private void drown(String cause) {
        if (mode != Mode.PLAYING) return;
        particles.burst(player.cx(), player.y + Player.H, "~~~", Theme.WATER, 10, 3.5, 16);
        sfx(Sfx.SPLASH);
        player.hp--;
        hud.loseMemory(player.hp);
        deathCause = cause;
        if (player.hp <= 0) {
            die(cause);
            return;
        }
        int hp = player.hp;
        placeAtSpawn();
        player.hp = hp;
        player.invincible = 60;
        whiteFlash = 20;
        cam.snap(player.cx(), player.cy());
        floatText("// respawned", Theme.CHECKPOINT, player.cx(), player.y - 10);
    }

    private void placeAtSpawn() {
        int dir = player.facing;
        player = new Player(spawnX, spawnY);
        player.facing = dir;
    }

    public void die(String cause) {
        deathCause = cause;
        mode = Mode.DEAD;
        player.hp = 0;
        player.state = Player.State.DEAD;
        particles.burst(player.cx(), player.cy(), "o/|\\x", Theme.PLAYER, 16, 4.5, 17);
        sfx(Sfx.DEATH);
        cam.addShake(10);
        death = new DeathOverlay(cause, cfg.className());
        game.input.consume();
    }

    private void respawn() {
        placeAtSpawn();
        player.hp = 5;
        player.invincible = 90;
        shots.clear();
        arrows.clear();
        mode = Mode.PLAYING;
        death = null;
        cam.snap(player.cx(), player.cy());
        whiteFlash = 16;
        floatText("catch (OutOfMemoryError e)", Theme.CHECKPOINT, player.cx(), player.y - 14);
        sfx(Sfx.CHECKPOINT);
        game.input.consume();
    }

    // ---------------------------------------------------------------- mode changes

    public void openEditor(boolean canRun) {
        if (editor == null) editor = new EditorOverlay(this);
        editor.open(canRun);
        mode = Mode.EDITOR;
        sfx(Sfx.CLICK);
        game.input.consume();
    }

    public void closeEditor() {
        mode = Mode.PLAYING;
        game.setCursor(Cursor.DEFAULT_CURSOR);
        sfx(Sfx.CLICK);
        game.input.consume();
    }

    /** Called by the editor's Run button once every slot is filled. */
    public void startRun() {
        runs++;
        LevelConfig.Outcome out = Judge.judge(cfg, assignment, flags());
        run = new RunSequence(this, out);
        mode = Mode.RUNNING;
        game.setCursor(Cursor.DEFAULT_CURSOR);
        game.input.consume();
    }

    void finishRun() {
        run = null;
        if (player.hp <= 0) {
            mode = Mode.PLAYING;
            die("Javac.compile");
            return;
        }
        mode = Mode.PLAYING;
        game.input.consume();
    }

    private void openPause() {
        pause = new PauseOverlay(new PauseOverlay.Actions() {
            @Override public void resume() { closePause(); }
            @Override public void restart() { askRestart(); }
            @Override public void settings() { openSettings(); }
            @Override public void menu() { askMenu(); }
        });
        mode = Mode.PAUSED;
        sfx(Sfx.CLICK);
        game.input.consume();
    }

    private void closePause() {
        pause = null;
        mode = Mode.PLAYING;
        game.input.consume();
    }

    private void openSettings() {
        settings = new SettingsDialog(game, () -> settings = null, () -> confirm = new ConfirmDialog(
                "Reset save?", "All progress and stars will be erased.", "Reset",
                () -> {
                    game.save.reset();
                    confirm = null;
                },
                () -> confirm = null));
    }

    private void askRestart() {
        confirm = new ConfirmDialog("Restart level?", "Collected blocks will be lost.", "Restart",
                () -> game.screens.go(new LevelScreen(game, index)),
                () -> confirm = null);
    }

    private void askMenu() {
        confirm = new ConfirmDialog("Back to main menu?", "Collected blocks will be lost.", "Main menu",
                () -> game.openMenu(index),
                () -> confirm = null);
    }

    private void clearLevel() {
        mode = Mode.CLEARED;
        sfx(Sfx.CLEAR);
        long ms = playFrames * 1000 / 60;
        Save.Rec rec = game.save.rec(index);
        boolean newBest = rec.bestMs < 0 || ms < rec.bestMs;
        rec.cleared = true;
        if (newBest) rec.bestMs = ms;
        for (int k = 0; k < 3; k++) rec.stars[k] |= stars[k];
        game.save.unlocked = Math.max(game.save.unlocked, Math.min(index + 1, game.levels.size() - 1));
        game.save.write();
        particles.burst(exit.cx(), exit.cy(), "{}();=+*", Theme.EXIT, 40, 7, 16);
        boolean hasNext = index + 1 < game.levels.size();
        clear = new ClearOverlay(cfg.file, ms, newBest, stars.clone(), cfg.starCount, runs, fails, player.hp, hasNext,
                () -> game.openLevel(index + 1), () -> game.openMenu(index));
        game.input.consume();
    }

    // ---------------------------------------------------------------- update

    @Override
    public void update(Game g) {
        Input in = g.input;
        time++;
        hud.update();
        if (whiteFlash > 0) whiteFlash--;
        if (hurtFlash > 0) hurtFlash--;
        if (okFlash > 0) okFlash--;
        if (confirm != null) {
            game.setCursor(Cursor.DEFAULT_CURSOR);
            confirm.update(game);
            ambient();
            return;
        }
        if (settings != null) {
            settings.update(game);
            ambient();
            return;
        }
        switch (mode) {
            case PLAYING -> updatePlaying(in);
            case EDITOR -> {
                editor.update(in);
                ambient();
            }
            case RUNNING -> {
                run.update(in);
                if (run != null) updateRunWorld();
            }
            case PAUSED -> {
                pause.update(game);
                ambient();
            }
            case DEAD -> {
                ambient();
                if (death.update(game)) respawn();
            }
            case CLEARED -> {
                clear.update(game);
                ambient();
            }
        }
    }

    private void updatePlaying(Input in) {
        game.setCursor(Cursor.DEFAULT_CURSOR);
        if (in.pressed(KeyEvent.VK_ESCAPE)) {
            openPause();
            return;
        }
        if (in.pressed(KeyEvent.VK_TAB)) {
            openEditor(nearTerminal());
            return;
        }
        if (in.pressed(KeyEvent.VK_E) && nearTerminal()) {
            openEditor(true);
            return;
        }
        if (in.pressed(KeyEvent.VK_R)) {
            askRestart();
            return;
        }
        if (in.pressed(KeyEvent.VK_F1)) {
            game.save.showDebug = !game.save.showDebug;
            game.save.write();
        }
        if (debug()) {
            int n = Tuning.NAMES.length;
            if (in.pressed(KeyEvent.VK_OPEN_BRACKET)) debugParam = (debugParam + n - 1) % n;
            if (in.pressed(KeyEvent.VK_CLOSE_BRACKET)) debugParam = (debugParam + 1) % n;
            if (in.pressed(KeyEvent.VK_MINUS)) Tuning.adjust(debugParam, -1);
            if (in.pressed(KeyEvent.VK_EQUALS)) Tuning.adjust(debugParam, 1);
        }
        playFrames++;
        updateWorld(Player.Controls.from(in));
    }

    /** One tick of gameplay with the given player input. Public for the snapshot tool. */
    public void updateWorld(Player.Controls c) {
        map.update(player.x, player.y, Player.W, Player.H);
        for (MovingPlatform p : platforms) p.update();
        player.update(this, c);
        if (mode != Mode.PLAYING) return;
        if (player.anim.footstep && player.onGround) particles.dust(player.cx() - player.facing * 6, player.y + Player.H, 2);

        for (Bug b : bugs) {
            b.update(this);
            if (!b.dead && b.spawnAnim == 0 && player.overlaps(b)) hurtPlayer(b.cx(), "Bug.bite");
        }
        for (ExceptionTurret t : turrets) {
            t.update(this);
            if (player.overlaps(t)) hurtPlayer(t.cx(), "Exception.touch");
        }
        for (int i = 0; i < shots.size(); i++) {
            ThrowShot s = shots.get(i);
            s.update(this);
            if (!s.dead && player.overlaps(s)) {
                s.dead = true;
                particles.burst(s.cx(), s.cy(), "throw", Theme.ERR, 6, 3, 13);
                hurtPlayer(s.cx(), "Exception.throw");
            }
        }
        for (Arrow a : arrows) {
            a.update(this);
            if (a.dead) continue;
            for (Bug b : bugs) {
                if (!b.dead && a.overlaps(b)) {
                    killBug(b);
                    a.dead = true;
                    break;
                }
            }
            if (a.dead) continue;
            for (ThrowShot s : shots) {
                if (!s.dead && a.overlaps(s)) {
                    s.dead = true;
                    a.dead = true;
                    particles.burst(s.cx(), s.cy(), "throw", Theme.ERR, 6, 3, 13);
                    floatText("catch", Theme.OK, s.cx(), s.y - 4);
                    sfx(Sfx.POP);
                    break;
                }
            }
        }
        for (Pickup p : pickups) {
            p.update();
            if (!p.dead && player.overlaps(p)) collect(p);
        }
        for (Checkpoint cp : checkpoints) {
            cp.update();
            if (!cp.active && player.overlaps(cp.x - 6, cp.y, cp.w + 12, cp.h)) activate(cp);
        }
        for (Terminal t : terminals) {
            t.near = t.inReach(player);
            t.update();
        }
        exit.update(particles);
        if (exit.active && player.overlaps(exit)) {
            clearLevel();
            return;
        }
        bugs.removeIf(b -> b.dead);
        shots.removeIf(s -> s.dead);
        arrows.removeIf(a -> a.dead);
        pickups.removeIf(p -> p.dead);
        tickCosmetics();
        cam.update(player.cx(), player.cy(), player.facing);
    }

    private void updateRunWorld() {
        map.update(-999, -999, 0, 0);
        for (MovingPlatform p : platforms) p.update();
        for (Bug b : bugs) b.update(this);
        bugs.removeIf(b -> b.dead);
        for (Terminal t : terminals) t.update();
        exit.update(particles);
        tickCosmetics();
        cam.update(player.cx(), player.cy(), player.facing);
    }

    /** Animations that keep running while the game is paused behind an overlay. */
    private void ambient() {
        for (Pickup p : pickups) p.update();
        for (Checkpoint cp : checkpoints) cp.update();
        for (Terminal t : terminals) t.update();
        exit.update(particles);
        tickCosmetics();
    }

    private void tickCosmetics() {
        particles.update();
        floats.removeIf(f -> !f.update());
        for (ErrorText e : errorTexts) e.update();
    }

    private void collect(Pickup p) {
        p.dead = true;
        double sx = p.cx() - cam.rx(), sy = p.cy() - cam.ry();
        switch (p.kind) {
            case BLOCK -> {
                inventory.add(p.block);
                hud.toast("+ " + p.block.text(), Theme.ACCENT_HOVER, true);
                hud.flyBlock(p.block.text(), sx, sy);
                particles.burst(p.cx(), p.cy(), "{};", Theme.ACCENT_HOVER, 8, 3, 13);
                sfx(Sfx.PICKUP);
            }
            case GC -> {
                int before = player.hp;
                player.hp = Math.min(5, player.hp + 2);
                floatText(player.hp > before ? "+" + (player.hp - before) + " Memory" : "Memory full", Theme.GC, p.cx(), p.y - 6);
                particles.burst(p.cx(), p.cy(), "gc()", Theme.GC, 8, 3, 13);
                sfx(Sfx.HEAL);
            }
            case STAR -> {
                stars[p.starIndex] = true;
                hud.toast("Hidden star found", Theme.STAR, false);
                hud.bumpStar();
                for (int i = 0; i < 12; i++) particles.sparkle(p.cx(), p.cy(), Theme.STAR);
                sfx(Sfx.STAR);
            }
            case KEY -> {
                hasKey = true;
                hud.toast("hasKey = true", Theme.KEY, false);
                particles.burst(p.cx(), p.cy(), "true", Theme.KEY, 10, 3, 14);
                sfx(Sfx.STAR);
            }
        }
    }

    private void activate(Checkpoint cp) {
        for (Checkpoint o : checkpoints) o.active = false;
        cp.activate();
        spawnX = cp.spawnX;
        spawnY = cp.spawnY;
        if (player.hp < 5) floatText("Memory restored", Theme.GC, cp.cx() + 20, cp.y - 22);
        player.hp = 5;
        floatText("// saved", Theme.CHECKPOINT, cp.cx() + 20, cp.y - 4);
        particles.burst(cp.x + 30, cp.y + 14, "try{", Theme.CHECKPOINT, 8, 2.5, 13);
        sfx(Sfx.CHECKPOINT);
    }

    // ---------------------------------------------------------------- render

    @Override
    public void render(Graphics2D g) {
        boolean overlay = confirm != null || settings != null || mode == Mode.EDITOR || mode == Mode.PAUSED
                || mode == Mode.DEAD || mode == Mode.CLEARED;
        if (overlay) {
            // The world does not change behind an overlay: draw it once, dim it once, then reuse it.
            if (frozenMode != mode) {
                frozenMode = mode;
                frozenAge = 0;
                baked = false;
            }
            frozenAge++;
            boolean live = (mode == Mode.DEAD || mode == Mode.CLEARED) && frozenAge <= 45;
            int ramp = mode == Mode.DEAD ? 30 : 10;
            double target = switch (mode) {
                case EDITOR -> 0.66;
                case DEAD -> 0.84;
                case CLEARED -> 0.62;
                default -> 0.55;
            };
            double s = Draw.deviceScale(g);
            if (frozen == null || frozenScale != s || live) {
                if (frozen == null || frozenScale != s) {
                    frozen = new BufferedImage((int) Math.round(Theme.W * s), (int) Math.round(Theme.H * s), BufferedImage.TYPE_INT_RGB);
                    frozenScale = s;
                }
                Graphics2D fg = frozen.createGraphics();
                com.compilequest.core.GameWindow.hints(fg);
                fg.scale(s, s);
                renderWorld(fg);
                hud.render(fg, false);
                fg.dispose();
                baked = false;
            }
            if (!baked && !live && frozenAge > ramp) {
                Graphics2D fg = frozen.createGraphics();
                fg.setColor(new Color(0, 0, 0, (int) (255 * target)));
                fg.fillRect(0, 0, frozen.getWidth(), frozen.getHeight());
                com.compilequest.core.GameWindow.hints(fg);
                fg.scale(s, s);
                if (confirm == null && settings == null) {
                    if (mode == Mode.EDITOR) editor.renderShadow(fg);
                    if (mode == Mode.PAUSED && pause != null) pause.renderShadow(fg);
                    if (mode == Mode.CLEARED && clear != null) clear.renderShadow(fg);
                }
                fg.dispose();
                baked = true;
            }
            Draw.blit(g, frozen, 0, 0);
            boolean shadowIn = baked && confirm == null && settings == null;
            if (editor != null) editor.shadowBaked = shadowIn && mode == Mode.EDITOR;
            if (pause != null) pause.shadowBaked = shadowIn && mode == Mode.PAUSED;
            if (clear != null) clear.shadowBaked = shadowIn && mode == Mode.CLEARED;
            if (!baked) {
                double k = Draw.easeOut(Math.min(1, frozenAge / (double) ramp));
                g.setColor(new Color(0, 0, 0, (int) (255 * target * k)));
                g.fillRect(0, 0, Theme.W, Theme.H);
            }
        } else {
            frozen = null;
            frozenMode = null;
            renderWorld(g);
            hud.render(g, mode == Mode.PLAYING);
        }
        if (mode == Mode.RUNNING && run != null) run.render(g);
        if (mode == Mode.EDITOR) editor.render(g);
        if (mode == Mode.PAUSED && pause != null) pause.render(g);
        if (mode == Mode.DEAD && death != null) death.render(g);
        if (mode == Mode.CLEARED && clear != null) clear.render(g);
        if (settings != null) settings.render(g);
        if (confirm != null) confirm.render(g);
        if (debug()) renderDebugPanel(g);
    }

    private BufferedImage frozen;
    private double frozenScale;
    private int frozenAge;
    private Mode frozenMode;
    private boolean baked;

    private void renderWorld(Graphics2D g) {
        double cx = cam.rx(), cy = cam.ry();
        if (cx < 0 || cy < 0 || cx + Theme.W > map.w * T || cy + Theme.H > map.h * T) Draw.rect(g, 0, 0, Theme.W, Theme.H, Theme.BG);
        map.render(g, cx, cy);
        for (Terminal t : terminals) t.render(g, cx, cy);
        for (Checkpoint c : checkpoints) c.render(g, cx, cy);
        exit.render(g, cx, cy);
        for (MovingPlatform p : platforms) p.render(g, cx, cy);
        for (Pickup p : pickups) p.render(g, cx, cy);
        for (ExceptionTurret t : turrets) t.render(g, cx, cy);
        for (Bug b : bugs) b.render(g, cx, cy);
        if (mode != Mode.DEAD) player.anim.render(g, player, cx, cy);
        for (ThrowShot s : shots) s.render(g, cx, cy);
        for (Arrow a : arrows) a.render(g, cx, cy);
        particles.render(g, cx, cy);
        for (ErrorText e : errorTexts) e.render(g, cx, cy);
        for (FloatText f : floats) f.render(g, cx, cy);
        if (mode == Mode.PLAYING) renderTerminalBubble(g, cx, cy);
        if (debug()) renderDebugWorld(g, cx, cy);
        renderGutter(g, cy);
        renderFlashes(g);
    }

    private String[] buildBackground() {
        String text = Res.exists("text/" + cfg.wallText) ? Res.text("text/" + cfg.wallText) : "";
        List<String> lines = new ArrayList<>();
        for (String s : text.split("\n")) if (!s.isBlank()) lines.add(s.replace("\t", "    "));
        if (lines.isEmpty()) lines.add("// Compile Quest");
        return lines.toArray(new String[0]);
    }

    private void renderGutter(Graphics2D g, double cy) {
        double gw = 46;
        Draw.rect(g, 0, 0, gw, Theme.H, Draw.alpha(Theme.BG_DEEP, 0.78));
        Draw.rect(g, gw, 0, 1, Theme.H, Draw.alpha(Theme.BORDER, 0.7));
        Font f = Theme.mono(12);
        int r0 = Math.max(0, (int) Math.floor(cy / T)), r1 = Math.min(map.h - 1, (int) Math.floor((cy + Theme.H) / T));
        int prow = (int) Math.floor((player.y + Player.H - 1) / T);
        for (int r = r0; r <= r1; r++) {
            double y = r * T - cy;
            boolean cur = r == prow;
            if (cur) Draw.rect(g, 0, y, gw, T, Draw.alpha(Theme.SELECT, 0.45));
            Draw.right(g, String.valueOf(r + 1), gw - 10, Draw.mid(f, y, T), f, cur ? Theme.TEXT : Theme.LINE_NO);
        }
    }

    private void renderTerminalBubble(Graphics2D g, double cx, double cy) {
        for (Terminal t : terminals) {
            if (!t.near) continue;
            Font f = Theme.mono(14);
            String label = "Run code";
            double kw = Math.max(Draw.width(f, "E") + 12, 22);
            double w = kw + 10 + Draw.width(f, label) + 24, h = 34;
            double bx = t.cx() - cx - w / 2, by = t.y - cy - h - 16 + Math.sin(time * 0.1) * 2;
            Draw.shadow(g, bx, by, w, h, 8, 8, 0.4);
            Draw.panel(g, bx, by, w, h, 8, Draw.alpha(Theme.PANEL, 0.97), Theme.ACCENT);
            double base = Draw.mid(f, by, h);
            Draw.keycap(g, "E", bx + 12, base, f, Theme.TEXT_BRIGHT);
            Draw.text(g, label, bx + 12 + kw + 10, base, f, Theme.TEXT_BRIGHT);
            java.awt.geom.Path2D tip = new java.awt.geom.Path2D.Double();
            tip.moveTo(t.cx() - cx - 7, by + h - 1);
            tip.lineTo(t.cx() - cx + 7, by + h - 1);
            tip.lineTo(t.cx() - cx, by + h + 7);
            tip.closePath();
            g.setColor(Theme.PANEL);
            g.fill(tip);
        }
    }

    private void renderFlashes(Graphics2D g) {
        if (hurtFlash > 0) edgeFlash(g, Theme.ERR, hurtFlash / 30.0);
        if (okFlash > 0) edgeFlash(g, Theme.OK, okFlash / 44.0 * 0.8);
        if (whiteFlash > 0) Draw.rect(g, 0, 0, Theme.W, Theme.H, Draw.alpha(Color.WHITE, whiteFlash / 20.0 * 0.3));
    }

    /** A colored glow around the screen edges, built from a few cheap rectangle frames. */
    private static void edgeFlash(Graphics2D g, Color c, double a) {
        int layers = 6;
        double band = 90;
        for (int i = 0; i < layers; i++) {
            double in = band * i / layers, th = band / layers;
            g.setColor(Draw.alpha(c, Math.min(1, a) * 0.2 * (1 - i / (double) layers)));
            int x0 = (int) in, y0 = (int) in, w = (int) (Theme.W - 2 * in), h = (int) (Theme.H - 2 * in), t = (int) Math.ceil(th);
            g.fillRect(x0, y0, w, t);
            g.fillRect(x0, y0 + h - t, w, t);
            g.fillRect(x0, y0 + t, t, h - 2 * t);
            g.fillRect(x0 + w - t, y0 + t, t, h - 2 * t);
        }
    }

    private void renderDebugWorld(Graphics2D g, double cx, double cy) {
        g.setStroke(Draw.stroke(1));
        g.setColor(Theme.OK);
        g.draw(new Rectangle2D.Double(player.x - cx, player.y - cy, Player.W, Player.H));
        g.setColor(Theme.WARN);
        for (Bug b : bugs) g.draw(new Rectangle2D.Double(b.x - cx, b.y - cy, b.w, b.h));
        for (ExceptionTurret t : turrets) g.draw(new Rectangle2D.Double(t.x - cx, t.y - cy, t.w, t.h));
        for (Pickup p : pickups) g.draw(new Rectangle2D.Double(p.x - cx, p.y - cy, p.w, p.h));
        double px = player.cx() - cx, py = player.cy() - cy;
        Draw.line(g, px, py, px + player.vx * 8, py + player.vy * 8, Theme.SPRING, 2);
    }

    private void renderDebugPanel(Graphics2D g) {
        double w = 300, x = Theme.W - w - 16, y = 64;
        List<String> lines = new ArrayList<>();
        lines.add("pos   " + Math.round(player.x) + ", " + Math.round(player.y));
        lines.add("vel   " + String.format("%.2f, %.2f", player.vx, player.vy));
        lines.add("state " + player.state + (player.onGround ? " (ground)" : ""));
        lines.add("cell  " + (int) (player.cx() / T) + ", " + (int) ((player.y + Player.H - 1) / T));
        lines.add("time  " + playFrames / 60 + "s   runs " + runs);
        lines.add("");
        for (int i = 0; i < Tuning.NAMES.length; i++) {
            lines.add((i == debugParam ? "> " : "  ") + String.format("%-13s%6.2f", Tuning.NAMES[i], Tuning.get(i)));
        }
        lines.add("");
        lines.add("[ ] select   - = adjust   F1 hide");
        double h = lines.size() * 18 + 24;
        Draw.panel(g, x, y, w, h, 8, Draw.alpha(Theme.BG_DEEP, 0.88), Theme.BORDER);
        Font f = Theme.mono(12);
        double ly = y + 22;
        for (String s : lines) {
            Draw.text(g, s, x + 14, ly, f, s.startsWith(">") ? Theme.SPRING : Theme.TEXT);
            ly += 18;
        }
    }

    // ---------------------------------------------------------------- tool access

    /** Draws the whole map at once (used by the snapshot tool). */
    public void renderOverview(Graphics2D g) {
        g.setColor(Theme.BG);
        g.fillRect(0, 0, map.w * T, map.h * T);
        map.render(g, 0, 0, map.w * T, map.h * T);
        for (Terminal t : terminals) t.render(g, 0, 0);
        for (Checkpoint c : checkpoints) c.render(g, 0, 0);
        exit.render(g, 0, 0);
        for (MovingPlatform p : platforms) p.render(g, 0, 0);
        for (Pickup p : pickups) p.render(g, 0, 0);
        for (ExceptionTurret t : turrets) t.render(g, 0, 0);
        for (Bug b : bugs) b.render(g, 0, 0);
        player.anim.render(g, player, 0, 0);
    }

    /** Applies an outcome's effects with no animation (used by the map checker). */
    public void applyInstant(LevelConfig.Outcome out) {
        revertRun();
        for (Map<String, Object> e : out.effects) {
            for (Step s : Effects.build(this, e)) s.action().run();
        }
    }

    public EditorOverlay editor() { return editor; }
    public boolean running() { return mode == Mode.RUNNING; }
    public void forceClear() { clearLevel(); }
    public void forcePause() { openPause(); }
}
