package com.compilequest.editor;

import com.compilequest.core.Draw;
import com.compilequest.core.Theme;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.util.Set;

/** A tiny Java syntax highlighter (Darcula colors) for code drawn anywhere in the game. */
public final class Syntax {
    private Syntax() {}

    private static final Set<String> KEYWORDS = Set.of(
            "public", "private", "protected", "static", "void", "class", "int", "boolean", "for", "if", "else",
            "while", "return", "new", "true", "false", "null", "final", "break", "extends", "double", "char",
            "long", "try", "catch", "throw", "this", "import", "package");

    /** Draws highlighted code starting at (x, baseline) and returns the x where it ends. */
    public static double draw(Graphics2D g, String s, double x, double y, Font f, double alpha) {
        double adv = Draw.adv(f);
        g.setFont(f);
        int i = 0, n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            int j;
            Color col;
            if (c == '"') {
                j = s.indexOf('"', i + 1);
                j = j < 0 ? n : j + 1;
                col = Theme.SYN_STRING;
            } else if (c == '/' && i + 1 < n && (s.charAt(i + 1) == '/' || s.charAt(i + 1) == '*')) {
                j = n;
                col = Theme.SYN_COMMENT;
            } else if (c == '*' && s.trim().startsWith("*")) {
                j = n;
                col = Theme.SYN_DOC;
            } else if (Character.isDigit(c)) {
                j = i;
                while (j < n && (Character.isDigit(s.charAt(j)) || s.charAt(j) == '.')) j++;
                col = Theme.SYN_NUMBER;
            } else if (Character.isJavaIdentifierStart(c)) {
                j = i;
                while (j < n && Character.isJavaIdentifierPart(s.charAt(j))) j++;
                String word = s.substring(i, j);
                if (KEYWORDS.contains(word)) col = Theme.SYN_KEYWORD;
                else if (j < n && s.charAt(j) == '(') col = Theme.SYN_METHOD;
                else if (i > 0 && s.charAt(i - 1) == '.') col = Theme.SYN_FIELD;
                else col = Theme.SYN_CODE;
            } else {
                j = i + 1;
                col = (c == ';' || c == ',') ? Theme.SYN_KEYWORD : Theme.SYN_CODE;
            }
            String part = s.substring(i, j);
            g.setColor(alpha >= 1 ? col : Draw.alpha(col, alpha));
            g.drawString(part, (float) x, (float) y);
            x += adv * part.length();
            i = j;
        }
        return x;
    }

    public static double width(Font f, String s) { return Draw.adv(f) * s.length(); }
}
