package lavadigger;

import javax.sound.sampled.*;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 程序合成的简单音效;声卡不可用时静默 */
final class Sfx {
    private static final float RATE = 22050f;
    static boolean enabled = true;

    private Clip beep, boom, cash, buy, error, pick, hit;
    private boolean ok;
    private final ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "sfx");
        t.setDaemon(true);
        return t;
    });

    Sfx() {
        if (!enabled) return;
        try {
            beep = make(cat(tone(980, 0.09, 0.35), silence(0.04), tone(980, 0.09, 0.35)));
            boom = make(noise(1.1, 0.9));
            cash = make(cat(tone(880, 0.07, 0.3), tone(1320, 0.12, 0.3)));
            buy = make(cat(tone(660, 0.06, 0.3), tone(990, 0.06, 0.3), tone(1320, 0.1, 0.3)));
            error = make(tone(180, 0.18, 0.4));
            pick = make(tone(1500, 0.05, 0.22));
            hit = make(noise(0.25, 0.6));
            ok = true;
        } catch (Throwable t) {
            ok = false;
        }
    }

    void beep() { play(beep); }
    void boom() { play(boom); }
    void cash() { play(cash); }
    void buy() { play(buy); }
    void error() { play(error); }
    void pick() { play(pick); }
    void hit() { play(hit); }

    private void play(final Clip c) {
        if (!ok || c == null) return;
        exec.submit(() -> {
            try {
                c.stop();
                c.setFramePosition(0);
                c.start();
            } catch (Throwable ignored) {
            }
        });
    }

    private static Clip make(byte[] pcm) throws Exception {
        AudioFormat fmt = new AudioFormat(RATE, 16, 1, true, false);
        Clip c = AudioSystem.getClip();
        c.open(fmt, pcm, 0, pcm.length);
        return c;
    }

    private static byte[] tone(double freq, double dur, double vol) {
        int n = (int) (RATE * dur);
        byte[] b = new byte[n * 2];
        for (int i = 0; i < n; i++) {
            double env = Math.min(1, Math.min(i / 200.0, (n - i) / 400.0));
            double s = Math.signum(Math.sin(2 * Math.PI * freq * i / RATE)) * 0.6 + Math.sin(2 * Math.PI * freq * i / RATE) * 0.4;
            put(b, i, s * vol * env);
        }
        return b;
    }

    private static byte[] noise(double dur, double vol) {
        int n = (int) (RATE * dur);
        byte[] b = new byte[n * 2];
        Random r = new Random(7);
        double lp = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) n;
            lp += (r.nextDouble() * 2 - 1 - lp) * (0.25 - 0.2 * t);
            double env = Math.pow(1 - t, 2.2) * Math.min(1, i / 60.0);
            put(b, i, lp * vol * env * 2.2);
        }
        return b;
    }

    private static byte[] silence(double dur) {
        return new byte[(int) (RATE * dur) * 2];
    }

    private static byte[] cat(byte[]... parts) {
        int len = 0;
        for (byte[] p : parts) len += p.length;
        byte[] o = new byte[len];
        int off = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, o, off, p.length);
            off += p.length;
        }
        return o;
    }

    private static void put(byte[] b, int i, double s) {
        int v = (int) (Math.max(-1, Math.min(1, s)) * 32000);
        b[i * 2] = (byte) v;
        b[i * 2 + 1] = (byte) (v >> 8);
    }
}
