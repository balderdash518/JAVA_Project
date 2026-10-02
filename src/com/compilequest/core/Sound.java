package com.compilequest.core;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Sound effects. Every effect is synthesized at startup (no audio files needed) and mixed in
 * software onto one output line, so many sounds can overlap cheaply.
 */
public final class Sound {
    public enum Sfx {
        JUMP, LAND, SPRING, PICKUP, STAR, HEAL, SHOOT, BUG_DIE, HURT, TYPE, BUILD_OK, BUILD_FAIL,
        CHECKPOINT, CLICK, CLEAR, BREAK, THROW, DROP, ERROR, SPLASH, OPEN, WHOOSH, HOVER, ALARM, POP, DEATH
    }

    private static final int RATE = 44100;
    private static final int SINE = 0, SQUARE = 1, TRIANGLE = 2, SAW = 3;

    private final float[][] bank = new float[Sfx.values().length][];
    private final List<Voice> voices = new ArrayList<>();
    private final Random rng = new Random();
    private volatile float volume = 0.7f;
    private volatile boolean output;

    private static final class Voice {
        final float[] data;
        final double step;
        final float vol;
        double pos;

        Voice(float[] data, double step, float vol) {
            this.data = data;
            this.step = step;
            this.vol = vol;
        }
    }

    public Sound(boolean enableOutput) {
        build();
        if (enableOutput) startMixer();
    }

    public void setVolume(float v) { volume = Math.max(0f, Math.min(1f, v)); }
    public float volume() { return volume; }

    public void play(Sfx s) { play(s, 1f, 1f); }

    public void play(Sfx s, float vol, float pitch) {
        if (!output || volume <= 0f) return;
        float[] d = bank[s.ordinal()];
        if (d == null) return;
        synchronized (voices) {
            if (voices.size() > 28) voices.remove(0);
            voices.add(new Voice(d, pitch, vol));
        }
    }

    /** Plays with a small random pitch change so repeated sounds do not feel mechanical. */
    public void vary(Sfx s, float vol) {
        play(s, vol, 0.9f + rng.nextFloat() * 0.22f);
    }

    private void startMixer() {
        Thread t = new Thread(() -> {
            try {
                AudioFormat fmt = new AudioFormat(RATE, 16, 1, true, false);
                SourceDataLine line = AudioSystem.getSourceDataLine(fmt);
                line.open(fmt, 4096);
                line.start();
                output = true;
                final int frames = 256;
                float[] mix = new float[frames];
                byte[] out = new byte[frames * 2];
                while (true) {
                    java.util.Arrays.fill(mix, 0f);
                    synchronized (voices) {
                        for (int v = voices.size() - 1; v >= 0; v--) {
                            Voice voice = voices.get(v);
                            for (int i = 0; i < frames; i++) {
                                int idx = (int) voice.pos;
                                if (idx >= voice.data.length) break;
                                mix[i] += voice.data[idx] * voice.vol;
                                voice.pos += voice.step;
                            }
                            if (voice.pos >= voice.data.length) voices.remove(v);
                        }
                    }
                    float master = volume * 0.85f;
                    for (int i = 0; i < frames; i++) {
                        float s = mix[i] * master;
                        if (s > 1f) s = 1f;
                        if (s < -1f) s = -1f;
                        short v = (short) (s * 32000);
                        out[i * 2] = (byte) v;
                        out[i * 2 + 1] = (byte) (v >> 8);
                    }
                    line.write(out, 0, out.length);
                }
            } catch (Exception | LinkageError e) {
                output = false;
                System.err.println("Audio disabled: " + e.getMessage());
            }
        }, "audio-mixer");
        t.setDaemon(true);
        t.start();
    }

    // ---------------------------------------------------------------- synthesis

    private void put(Sfx s, float[] data) { bank[s.ordinal()] = data; }

    private void build() {
        put(Sfx.JUMP, lp(tone(0.14, 260, 640, SQUARE, 0.5, 0.20, 0.004, 1.6), 0.45));
        put(Sfx.LAND, mix(noise(0.07, 0.22, 2.2, 0.12), tone(0.09, 150, 55, SINE, 0, 0.35, 0.002, 2)));
        put(Sfx.SPRING, mix(tone(0.30, 210, 940, TRIANGLE, 0, 0.32, 0.003, 1.1), tone(0.30, 420, 1880, SINE, 0, 0.08, 0.003, 1.4)));
        put(Sfx.PICKUP, lp(seq(tone(0.05, 988, 988, SQUARE, 0.25, 0.16, 0.002, 0.4), tone(0.12, 1319, 1319, SQUARE, 0.25, 0.16, 0.002, 1.6)), 0.5));
        put(Sfx.STAR, mix(seq(tone(0.055, 1047, 1047, TRIANGLE, 0, 0.26, 0.002, 0.3), tone(0.055, 1319, 1319, TRIANGLE, 0, 0.26, 0.002, 0.3),
                tone(0.055, 1568, 1568, TRIANGLE, 0, 0.26, 0.002, 0.3), tone(0.3, 2093, 2093, TRIANGLE, 0, 0.26, 0.002, 2.0)),
                delay(tone(0.3, 3136, 3136, SINE, 0, 0.06, 0.01, 2.0), 0.16)));
        put(Sfx.HEAL, seq(tone(0.07, 523, 523, TRIANGLE, 0, 0.28, 0.002, 0.3), tone(0.07, 659, 659, TRIANGLE, 0, 0.28, 0.002, 0.3), tone(0.18, 784, 784, TRIANGLE, 0, 0.28, 0.002, 1.8)));
        put(Sfx.SHOOT, mix(lp(tone(0.10, 1250, 330, SQUARE, 0.3, 0.12, 0.001, 1.5), 0.5), noise(0.05, 0.07, 3, 0.6)));
        put(Sfx.BUG_DIE, mix(noise(0.20, 0.26, 1.4, 0.3), lp(tone(0.2, 560, 70, SQUARE, 0.5, 0.14, 0.002, 1.2), 0.4)));
        put(Sfx.HURT, mix(lp(tone(0.28, 470, 105, SQUARE, 0.5, 0.20, 0.002, 1.0), 0.35), noise(0.12, 0.14, 2, 0.4)));
        put(Sfx.TYPE, lp(tone(0.03, 1500, 1250, SQUARE, 0.5, 0.07, 0.001, 2.0), 0.6));
        put(Sfx.BUILD_OK, mix(seq(tone(0.09, 523, 523, TRIANGLE, 0, 0.30, 0.002, 0.2), tone(0.09, 659, 659, TRIANGLE, 0, 0.30, 0.002, 0.2),
                tone(0.09, 784, 784, TRIANGLE, 0, 0.30, 0.002, 0.2), tone(0.42, 1047, 1047, TRIANGLE, 0, 0.30, 0.002, 1.6)),
                delay(lp(tone(0.42, 523, 523, SQUARE, 0.5, 0.07, 0.01, 1.6), 0.3), 0.27)));
        put(Sfx.BUILD_FAIL, mix(lp(tone(0.45, 233, 110, SAW, 0, 0.16, 0.003, 0.8), 0.4), lp(tone(0.45, 220, 104, SQUARE, 0.5, 0.09, 0.003, 0.8), 0.3)));
        put(Sfx.CHECKPOINT, seq(tone(0.08, 660, 660, TRIANGLE, 0, 0.26, 0.002, 0.4), tone(0.24, 990, 990, TRIANGLE, 0, 0.26, 0.002, 1.8)));
        put(Sfx.CLICK, lp(tone(0.025, 1800, 1500, SQUARE, 0.5, 0.05, 0.001, 3), 0.5));
        put(Sfx.CLEAR, mix(seq(tone(0.11, 523, 523, TRIANGLE, 0, 0.28, 0.002, 0.2), tone(0.11, 659, 659, TRIANGLE, 0, 0.28, 0.002, 0.2),
                tone(0.11, 784, 784, TRIANGLE, 0, 0.28, 0.002, 0.2), tone(0.11, 1047, 1047, TRIANGLE, 0, 0.28, 0.002, 0.2),
                tone(0.6, 1319, 1319, TRIANGLE, 0, 0.28, 0.002, 1.5)),
                delay(lp(tone(0.6, 659, 659, SQUARE, 0.5, 0.06, 0.02, 1.5), 0.3), 0.44)));
        put(Sfx.BREAK, mix(noise(0.18, 0.30, 1.3, 0.25), lp(tone(0.12, 190, 60, SQUARE, 0.5, 0.12, 0.002, 1.5), 0.4)));
        put(Sfx.THROW, tone(0.16, 720, 250, TRIANGLE, 0, 0.14, 0.003, 1.0));
        put(Sfx.DROP, tone(0.06, 720, 480, SINE, 0, 0.28, 0.002, 2.0));
        put(Sfx.ERROR, seq(lp(tone(0.07, 170, 170, SQUARE, 0.5, 0.12, 0.002, 0.5), 0.3), silence(0.03), lp(tone(0.11, 130, 130, SQUARE, 0.5, 0.12, 0.002, 1.2), 0.3)));
        put(Sfx.SPLASH, mix(noise(0.38, 0.28, 1.3, 0.18), tone(0.22, 420, 110, SINE, 0, 0.2, 0.003, 1.5)));
        put(Sfx.OPEN, seq(tone(0.07, 392, 392, TRIANGLE, 0, 0.26, 0.002, 0.3), tone(0.07, 523, 523, TRIANGLE, 0, 0.26, 0.002, 0.3), tone(0.18, 659, 659, TRIANGLE, 0, 0.26, 0.002, 1.6)));
        put(Sfx.WHOOSH, sweepNoise(0.35, 0.22));
        put(Sfx.HOVER, tone(0.018, 2400, 2400, SINE, 0, 0.035, 0.001, 2));
        put(Sfx.ALARM, lp(seq(tone(0.12, 880, 880, SQUARE, 0.5, 0.12, 0.003, 0.2), tone(0.12, 660, 660, SQUARE, 0.5, 0.12, 0.003, 0.2),
                tone(0.12, 880, 880, SQUARE, 0.5, 0.12, 0.003, 0.2), tone(0.16, 660, 660, SQUARE, 0.5, 0.12, 0.003, 1.2)), 0.35));
        put(Sfx.POP, tone(0.05, 500, 900, SINE, 0, 0.22, 0.002, 1.5));
        put(Sfx.DEATH, mix(lp(seq(tone(0.16, 392, 392, SQUARE, 0.5, 0.14, 0.003, 0.3), tone(0.16, 330, 330, SQUARE, 0.5, 0.14, 0.003, 0.3),
                tone(0.5, 262, 196, SQUARE, 0.5, 0.14, 0.003, 1.2)), 0.35), noise(0.2, 0.08, 2, 0.3)));
    }

    private static float[] tone(double dur, double f0, double f1, int wave, double duty, double vol, double attack, double decay) {
        int n = (int) (dur * RATE);
        float[] out = new float[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE;
            double u = i / (double) n;
            double f = f0 * Math.pow(f1 / f0, u);
            phase += f / RATE;
            double p = phase - Math.floor(phase);
            double s = switch (wave) {
                case SQUARE -> p < duty ? 1 : -1;
                case TRIANGLE -> 4 * Math.abs(p - 0.5) - 1;
                case SAW -> 2 * p - 1;
                default -> Math.sin(phase * 2 * Math.PI);
            };
            double env = Math.min(1, t / attack) * Math.pow(1 - u, decay);
            out[i] = (float) (s * env * vol);
        }
        return out;
    }

    private static float[] noise(double dur, double vol, double decay, double smooth) {
        int n = (int) (dur * RATE);
        float[] out = new float[n];
        Random r = new Random(n);
        double y = 0;
        for (int i = 0; i < n; i++) {
            double u = i / (double) n;
            y += smooth * ((r.nextDouble() * 2 - 1) - y);
            out[i] = (float) (y * vol * Math.pow(1 - u, decay) * Math.min(1, i / 60.0));
        }
        return out;
    }

    private static float[] sweepNoise(double dur, double vol) {
        int n = (int) (dur * RATE);
        float[] out = new float[n];
        Random r = new Random(7);
        double y = 0;
        for (int i = 0; i < n; i++) {
            double u = i / (double) n;
            double k = 0.03 + 0.5 * Math.sin(u * Math.PI);
            y += k * ((r.nextDouble() * 2 - 1) - y);
            out[i] = (float) (y * vol * Math.sin(u * Math.PI));
        }
        return out;
    }

    private static float[] silence(double dur) { return new float[(int) (dur * RATE)]; }

    private static float[] seq(float[]... parts) {
        int n = 0;
        for (float[] p : parts) n += p.length;
        float[] out = new float[n];
        int at = 0;
        for (float[] p : parts) {
            System.arraycopy(p, 0, out, at, p.length);
            at += p.length;
        }
        return out;
    }

    private static float[] mix(float[]... parts) {
        int n = 0;
        for (float[] p : parts) n = Math.max(n, p.length);
        float[] out = new float[n];
        for (float[] p : parts) for (int i = 0; i < p.length; i++) out[i] += p[i];
        return out;
    }

    private static float[] delay(float[] a, double sec) {
        int d = (int) (sec * RATE);
        float[] out = new float[a.length + d];
        System.arraycopy(a, 0, out, d, a.length);
        return out;
    }

    /** One-pole low-pass filter that takes the harsh edge off square waves. */
    private static float[] lp(float[] a, double k) {
        float y = 0;
        for (int i = 0; i < a.length; i++) {
            y += (float) (k * (a[i] - y));
            a[i] = y;
        }
        return a;
    }
}
