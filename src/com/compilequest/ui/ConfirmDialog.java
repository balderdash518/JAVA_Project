package com.compilequest.ui;

import com.compilequest.core.Draw;
import com.compilequest.core.Game;
import com.compilequest.core.Input;
import com.compilequest.core.Sound.Sfx;
import com.compilequest.core.Theme;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;

/** "Are you sure?" dialog: Enter confirms, Esc cancels. */
public final class ConfirmDialog {
    private static final double W = 520, H = 210;

    private final String title, message;
    private final Runnable onYes, onNo;
    private final Button yes, no;
    private int t;

    public ConfirmDialog(String title, String message, String yesLabel, Runnable onYes, Runnable onNo) {
        this.title = title;
        this.message = message;
        this.onYes = onYes;
        this.onNo = onNo;
        this.yes = new Button(yesLabel, "Enter", Button.Style.DANGER);
        this.no = new Button("Cancel", "Esc", Button.Style.SECONDARY);
    }

    public void update(Game game) {
        t++;
        Input in = game.input;
        double x = (Theme.W - W) / 2, y = (Theme.H - H) / 2;
        no.at(x + W - 24 - 150, y + H - 24 - 44, 150, 44);
        yes.at(x + W - 24 - 150 - 12 - 170, y + H - 24 - 44, 170, 44);
        yes.hover = yes.contains(in.mouseX, in.mouseY);
        no.hover = no.contains(in.mouseX, in.mouseY);
        game.setCursor(yes.hover || no.hover ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR);
        if (t < 3) return;
        if (in.pressedAny(KeyEvent.VK_ENTER, KeyEvent.VK_Y) || (in.mousePressed(Input.LEFT) && yes.hover)) {
            game.sound.play(Sfx.CLICK);
            game.input.consume();
            game.setCursor(Cursor.DEFAULT_CURSOR);
            onYes.run();
        } else if (in.pressedAny(KeyEvent.VK_ESCAPE, KeyEvent.VK_N) || (in.mousePressed(Input.LEFT) && no.hover)) {
            game.sound.play(Sfx.CLICK);
            game.input.consume();
            game.setCursor(Cursor.DEFAULT_CURSOR);
            onNo.run();
        }
    }

    public void render(Graphics2D g) {
        double a = Draw.easeOut(Math.min(1, t / 8.0));
        Draw.rect(g, 0, 0, Theme.W, Theme.H, new Color(0, 0, 0, (int) (150 * a)));
        var old = Draw.pushAlpha(g, a);
        double x = (Theme.W - W) / 2, y = (Theme.H - H) / 2 + (1 - a) * 12;
        Draw.shadow(g, x, y, W, H, 12, 22, 0.55);
        Draw.panel(g, x, y, W, H, 12, Theme.PANEL, Theme.BORDER_STRONG);
        Draw.circle(g, x + 34, y + 42, 13, Draw.alpha(Theme.WARN, 0.18));
        Font bang = Theme.bold(17);
        Draw.center(g, "!", x + 34, Draw.mid(bang, y + 29, 26), bang, Theme.WARN);
        Font tf = Theme.bold(19);
        Draw.text(g, title, x + 60, y + 49, tf, Theme.TEXT_BRIGHT);
        Font mf = Theme.mono(15);
        Draw.text(g, message, x + 60, y + 84, mf, Theme.TEXT);
        yes.render(g);
        no.render(g);
        g.setComposite(old);
    }
}
