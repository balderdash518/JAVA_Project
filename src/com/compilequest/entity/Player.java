package com.compilequest.entity;

import com.compilequest.core.Input;
import com.compilequest.world.TileMap;
import com.compilequest.world.TileType;

import java.awt.event.KeyEvent;
import java.util.List;

/**
 * The player: acceleration, coyote time, jump buffering, variable jump height, one-way
 * platforms and moving platforms. Collisions are resolved on X first, then on Y.
 */
public final class Player extends Entity {
    public static final double W = 22, H = 44;
    private static final int T = TileMap.T;

    public enum State { IDLE, RUN, BRAKE, TURN, JUMP, FALL, HURT, DEAD }

    /** What the player needs from the world it lives in. */
    public interface Host {
        TileMap map();

        List<MovingPlatform> platforms();

        default void onJump(Player p) { }
        default void onDoubleJump(Player p) { }
        default void onLand(Player p, double impact) { }
        default void onSpring(Player p, int tx, int ty) { }
        default void onHeadBreak(Player p, int tx, int ty) { }
        default void onTransient(Player p, int tx, int ty) { }
        default void onSpike(Player p, int tx, int ty) { }
        default void onWater(Player p) { }
        default void onFallOut(Player p) { }
        default void onShoot(Player p) { }
    }

    /** One tick of player intent, read from the keyboard or scripted by tools. */
    public static final class Controls {
        public boolean left, right, jumpPressed, jumpHeld, jumpReleased, downPressed, downHeld, shootPressed;

        public static Controls from(Input in) {
            Controls c = new Controls();
            c.left = in.downAny(KeyEvent.VK_A, KeyEvent.VK_LEFT);
            c.right = in.downAny(KeyEvent.VK_D, KeyEvent.VK_RIGHT);
            c.jumpPressed = in.pressedAny(KeyEvent.VK_W, KeyEvent.VK_SPACE, KeyEvent.VK_UP);
            c.jumpHeld = in.downAny(KeyEvent.VK_W, KeyEvent.VK_SPACE, KeyEvent.VK_UP);
            c.jumpReleased = in.releasedAny(KeyEvent.VK_W, KeyEvent.VK_SPACE, KeyEvent.VK_UP) && !c.jumpHeld;
            c.downPressed = in.pressedAny(KeyEvent.VK_S, KeyEvent.VK_DOWN);
            c.downHeld = in.downAny(KeyEvent.VK_S, KeyEvent.VK_DOWN);
            c.shootPressed = in.pressed(KeyEvent.VK_J) || in.mousePressed(Input.LEFT);
            return c;
        }
    }

    public int facing = 1;
    public boolean onGround;
    public State state = State.IDLE;
    public int hp = 5;
    public int invincible;
    public MovingPlatform riding;
    public int shootAnim, landAnim, jumpAnim, turnTimer, doubleJumpAnim;
    public double lastImpact;
    public final PlayerAnimator anim = new PlayerAnimator();

    private int coyote, buffer, shootCooldown, dropTimer;
    private boolean cuttable;
    /** Jumps left before touching the ground again (the double jump). */
    private int airJumps = Tuning.AIR_JUMPS;
    private int lastDir;

    public Player(double x, double y) {
        this.x = x;
        this.y = y;
        this.w = W;
        this.h = H;
    }

    public void update(Host host, Controls c) {
        TileMap map = host.map();
        if (riding != null) {
            if (riding.dead) {
                riding = null;
            } else {
                x += riding.dx;
                y = riding.top() - H;
            }
        }

        boolean locked = state == State.HURT && invincible > Tuning.INVINCIBLE - 14;
        int dir = locked ? 0 : (c.right ? 1 : 0) - (c.left ? 1 : 0);
        if (dir != 0) {
            if (onGround && vx * dir < -1.2) turnTimer = 8;
            vx += dir * (onGround ? Tuning.ACCEL : Tuning.AIR_ACCEL);
            vx = Math.max(-Tuning.MAX_SPEED, Math.min(Tuning.MAX_SPEED, vx));
            facing = dir;
        } else {
            vx = approach(vx, 0, onGround ? Tuning.FRICTION : Tuning.AIR_DRAG);
        }
        lastDir = dir;
        if (turnTimer > 0) turnTimer--;

        coyote = onGround ? Tuning.COYOTE : coyote - 1;
        buffer = c.jumpPressed ? Tuning.BUFFER : buffer - 1;
        if (buffer > 0 && coyote > 0 && !locked) {
            vy = Tuning.JUMP_V;
            buffer = 0;
            coyote = 0;
            onGround = false;
            riding = null;
            cuttable = true;
            jumpAnim = 6;
            host.onJump(this);
        } else if (buffer > 0 && !onGround && airJumps > 0 && !locked) {
            // Double jump: a second, slightly weaker jump in mid-air.
            vy = Tuning.DOUBLE_JUMP_V;
            airJumps--;
            buffer = 0;
            riding = null;
            cuttable = true;
            jumpAnim = 6;
            doubleJumpAnim = 16;
            host.onDoubleJump(this);
        }
        if (c.jumpReleased && vy < 0 && cuttable) {
            vy *= Tuning.JUMP_CUT;
            cuttable = false;
        }

        if (c.downPressed && onGround && onOneWay(map)) {
            dropTimer = 14;
            onGround = false;
            riding = null;
            y += 1;
        }
        if (dropTimer > 0) dropTimer--;

        vy = Math.min(vy + (vy > 0 ? Tuning.FALL_GRAVITY : Tuning.GRAVITY), Tuning.MAX_FALL);
        moveX(map);
        moveY(host, dropTimer > 0);
        hazards(host);

        if (shootCooldown > 0) shootCooldown--;
        if (c.shootPressed && shootCooldown == 0 && state != State.DEAD) {
            shootCooldown = Tuning.SHOOT_COOLDOWN;
            shootAnim = 10;
            host.onShoot(this);
        }
        if (shootAnim > 0) shootAnim--;
        if (invincible > 0) invincible--;
        if (landAnim > 0) landAnim--;
        if (jumpAnim > 0) jumpAnim--;
        if (doubleJumpAnim > 0) doubleJumpAnim--;
        updateState();
        anim.update(this);
    }

    private void moveX(TileMap map) {
        x += vx;
        int top = (int) Math.floor(y / T), bot = (int) Math.floor((y + H - 0.01) / T);
        if (vx > 0) {
            int col = (int) Math.floor((x + W - 0.01) / T);
            for (int r = top; r <= bot; r++) {
                if (map.blocks(col, r)) {
                    x = col * T - W;
                    vx = 0;
                    break;
                }
            }
        } else if (vx < 0) {
            int col = (int) Math.floor(x / T);
            for (int r = top; r <= bot; r++) {
                if (map.blocks(col, r)) {
                    x = (col + 1) * T;
                    vx = 0;
                    break;
                }
            }
        }
    }

    private void moveY(Host host, boolean ignoreOneWay) {
        TileMap map = host.map();
        double prevBottom = y + H;
        boolean wasGround = onGround;
        y += vy;
        onGround = false;
        int l = (int) Math.floor(x / T), r = (int) Math.floor((x + W - 0.01) / T);
        if (vy > 0) {
            int row = (int) Math.floor((y + H - 0.01) / T);
            boolean land = false;
            int spring = Integer.MIN_VALUE;
            for (int col = l; col <= r; col++) {
                boolean solid = map.blocks(col, row);
                boolean ledge = !ignoreOneWay && map.oneWay(col, row) && prevBottom <= row * T + 0.5;
                if (solid || ledge) {
                    land = true;
                    if (map.get(col, row) == TileType.SPRING) spring = col;
                }
            }
            if (land) {
                double impact = vy;
                y = row * T - H;
                vy = 0;
                onGround = true;
                riding = null;
                airJumps = Tuning.AIR_JUMPS;
                if (spring != Integer.MIN_VALUE) {
                    vy = Tuning.SPRING_V;
                    onGround = false;
                    cuttable = false;
                    jumpAnim = 8;
                    host.onSpring(this, spring, row);
                } else {
                    if (!wasGround) {
                        lastImpact = impact;
                        if (impact > 6) landAnim = 6;
                        host.onLand(this, impact);
                    }
                    for (int col = l; col <= r; col++) {
                        if (map.get(col, row) == TileType.TRANSIENT) host.onTransient(this, col, row);
                    }
                }
                return;
            }
            if (!ignoreOneWay) {
                for (MovingPlatform p : host.platforms()) {
                    double prevTop = p.top() - p.dy;
                    if (x + W > p.x && x < p.x + p.w && prevBottom <= prevTop + 0.5 && y + H >= p.top()) {
                        double impact = vy;
                        y = p.top() - H;
                        vy = 0;
                        onGround = true;
                        riding = p;
                        airJumps = Tuning.AIR_JUMPS;
                        if (!wasGround) host.onLand(this, impact);
                        return;
                    }
                }
            }
        } else if (vy < 0) {
            int row = (int) Math.floor(y / T);
            boolean hit = false;
            for (int col = l; col <= r; col++) {
                if (map.blocks(col, row)) {
                    hit = true;
                    if (map.get(col, row) == TileType.BREAK) host.onHeadBreak(this, col, row);
                }
            }
            if (hit) {
                y = (row + 1) * T;
                vy = 0;
                cuttable = false;
            }
        }
    }

    private void hazards(Host host) {
        TileMap map = host.map();
        int l = (int) Math.floor(x / T), r = (int) Math.floor((x + W - 0.01) / T);
        int t = (int) Math.floor(y / T), b = (int) Math.floor((y + H - 0.01) / T);
        for (int row = t; row <= b; row++) {
            for (int col = l; col <= r; col++) {
                TileType tt = map.get(col, row);
                boolean spike = (tt == TileType.SPIKE && overlaps(col * T + 4, row * T + T - 15, T - 8, 15))
                        || (tt == TileType.SPIKE_DOWN && overlaps(col * T + 4, row * T, T - 8, 15));
                if (spike) {
                    host.onSpike(this, col, row);
                    return;
                }
                if (tt == TileType.WATER && y + H > row * T + 14) {
                    host.onWater(this);
                    return;
                }
            }
        }
        if (y > map.h * T + 40) host.onFallOut(this);
    }

    private boolean onOneWay(TileMap map) {
        if (riding != null) return true;
        int row = (int) Math.floor((y + H + 1) / T);
        int l = (int) Math.floor(x / T), r = (int) Math.floor((x + W - 0.01) / T);
        boolean any = false;
        for (int col = l; col <= r; col++) {
            if (map.blocks(col, row)) return false;
            if (map.oneWay(col, row)) any = true;
        }
        return any;
    }

    private void updateState() {
        if (hp <= 0) {
            state = State.DEAD;
            return;
        }
        if (state == State.HURT && invincible > Tuning.INVINCIBLE - 24) return;
        if (!onGround) state = vy < 0 ? State.JUMP : State.FALL;
        else if (turnTimer > 0) state = State.TURN;
        else if (lastDir == 0 && Math.abs(vx) > 1.6) state = State.BRAKE;
        else if (Math.abs(vx) > 0.25) state = State.RUN;
        else state = State.IDLE;
    }

    /** Takes one point of damage unless invincible; knocks the player away from fromX. */
    public boolean hurt(double fromX) {
        if (invincible > 0 || hp <= 0) return false;
        hp--;
        invincible = Tuning.INVINCIBLE;
        vx = (x + W / 2 < fromX ? -1 : 1) * 5;
        vy = -6;
        onGround = false;
        riding = null;
        state = hp <= 0 ? State.DEAD : State.HURT;
        return true;
    }

    /** Moves the player up out of any solid cells (used when a program builds tiles on them). */
    public void pushOut(TileMap map) {
        for (int k = 0; k < 12; k++) {
            int l = (int) Math.floor(x / T), r = (int) Math.floor((x + W - 0.01) / T);
            int t = (int) Math.floor(y / T), b = (int) Math.floor((y + H - 0.01) / T);
            int top = Integer.MAX_VALUE;
            for (int row = t; row <= b; row++) {
                for (int col = l; col <= r; col++) {
                    if (map.blocks(col, row)) top = Math.min(top, row);
                }
            }
            if (top == Integer.MAX_VALUE) return;
            y = top * T - H;
            vy = 0;
        }
    }

    public boolean canShoot() { return shootCooldown == 0; }

    private static double approach(double v, double target, double step) {
        return v > target ? Math.max(v - step, target) : Math.min(v + step, target);
    }
}
