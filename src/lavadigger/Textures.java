package lavadigger;

import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.Random;

/** 程序生成的像素风贴图 */
final class Textures {
    static final int T = World.T;
    static final int BANDS = 5, VARIANTS = 4;

    /** 不同深度的土层颜色:黄褐 → 红褐 → 暗紫 → 火山黑 */
    static final Color[] BAND_COLORS = {
        new Color(152, 102, 56), new Color(128, 82, 50), new Color(112, 62, 46),
        new Color(88, 48, 52), new Color(72, 36, 30),
    };

    final BufferedImage[][] dirt = new BufferedImage[BANDS][VARIANTS];
    static final int ORE_VARIANTS = 3;
    final BufferedImage[][][] ore = new BufferedImage[BANDS][Mineral.ALL.length][ORE_VARIANTS];
    final BufferedImage[] gas = new BufferedImage[BANDS];
    final BufferedImage[] cave = new BufferedImage[BANDS];
    final BufferedImage[] grass = new BufferedImage[VARIANTS];
    final BufferedImage[] rock = new BufferedImage[VARIANTS];
    final BufferedImage[] lava = new BufferedImage[4];
    final BufferedImage bedrock, concrete;

    static int band(int row) {
        int d = row - World.SURFACE;
        if (d < 35) return 0;
        if (d < 100) return 1;
        if (d < 200) return 2;
        if (d < 300) return 3;
        return 4;
    }

    static int hash(int c, int r) {
        int h = c * 73856093 ^ r * 19349663;
        h ^= (h >>> 13);
        return h & 0x7fffffff;
    }

    Textures() {
        Random rnd = new Random(12345);
        for (int b = 0; b < BANDS; b++) {
            for (int v = 0; v < VARIANTS; v++) dirt[b][v] = makeDirt(BAND_COLORS[b], rnd);
            for (int m = 0; m < Mineral.ALL.length; m++)
                for (int v = 0; v < ORE_VARIANTS; v++) ore[b][m][v] = makeOre(dirt[b][(m + v) % VARIANTS], m, rnd);
            gas[b] = makeGas(dirt[b][1], rnd);
            cave[b] = makeCave(BAND_COLORS[b], rnd);
        }
        for (int v = 0; v < VARIANTS; v++) {
            grass[v] = makeGrass(dirt[0][v], rnd);
            rock[v] = makeRock(rnd);
        }
        for (int f = 0; f < 4; f++) lava[f] = makeLava(f, rnd);
        bedrock = makeBedrock(rnd);
        concrete = makeConcrete(rnd);
    }

    BufferedImage tile(byte t, int c, int r, double time) {
        int b = band(r), v = hash(c, r) % VARIANTS;
        if (t == World.DIRT) return r == World.SURFACE ? grass[v] : dirt[b][v];
        if (t == World.GAS) return gas[b];
        if (t == World.ROCK) return rock[v];
        if (t == World.LAVA) return lava[(int) (time * 5 + c * 3 + r) & 3];
        if (t == World.BEDROCK) return bedrock;
        if (t == World.CONCRETE) return concrete;
        if (t >= World.ORE) return ore[b][t - World.ORE][hash(r, c) % ORE_VARIANTS];
        return null;
    }

    // ------------------------------------------------------------------

    private static BufferedImage img() {
        return new BufferedImage(T, T, BufferedImage.TYPE_INT_ARGB);
    }

    private static int shade(Color c, double k) {
        int r = clamp((int) (c.getRed() * k)), g = clamp((int) (c.getGreen() * k)), b = clamp((int) (c.getBlue() * k));
        return 0xff000000 | (r << 16) | (g << 8) | b;
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : v > 255 ? 255 : v;
    }

    private static BufferedImage copy(BufferedImage src) {
        BufferedImage o = img();
        Graphics2D g = o.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return o;
    }

    private BufferedImage makeDirt(Color base, Random rnd) {
        BufferedImage im = img();
        double[][] block = new double[T / 4][T / 4];
        for (int i = 0; i < block.length; i++)
            for (int j = 0; j < block.length; j++) block[i][j] = 0.88 + rnd.nextDouble() * 0.2;
        for (int y = 0; y < T; y++)
            for (int x = 0; x < T; x++) {
                double k = block[y / 4][x / 4] * (0.96 + rnd.nextDouble() * 0.08);
                im.setRGB(x, y, shade(base, k));
            }
        // 小石子
        for (int i = 0; i < 4; i++) {
            int px = 2 + rnd.nextInt(T - 6), py = 2 + rnd.nextInt(T - 5);
            int dark = shade(base, 0.62), light = shade(base, 1.2);
            im.setRGB(px, py + 1, dark);
            im.setRGB(px + 1, py + 1, dark);
            im.setRGB(px + 2, py + 1, dark);
            im.setRGB(px + 1, py, light);
            im.setRGB(px + 2, py, dark);
        }
        return im;
    }

    private BufferedImage makeCave(Color base, Random rnd) {
        BufferedImage im = img();
        for (int y = 0; y < T; y++)
            for (int x = 0; x < T; x++) {
                double k = 0.28 + rnd.nextDouble() * 0.05 + ((x / 8 + y / 8) % 2) * 0.02;
                im.setRGB(x, y, shade(base, k));
            }
        return im;
    }

    private BufferedImage makeGrass(BufferedImage d, Random rnd) {
        BufferedImage im = copy(d);
        Color g1 = new Color(80, 170, 60), g2 = new Color(55, 130, 45);
        for (int x = 0; x < T; x++) {
            int h = 4 + rnd.nextInt(4);
            for (int y = 0; y < h; y++) im.setRGB(x, y, (y < 2 ? g1 : (rnd.nextBoolean() ? g1 : g2)).getRGB());
            im.setRGB(x, h, g2.darker().getRGB());
        }
        return im;
    }

    private BufferedImage makeGas(BufferedImage d, Random rnd) {
        BufferedImage im = copy(d);
        // 很淡的绿色斑点:细心能看出来
        for (int i = 0; i < 5; i++) {
            int x = 3 + rnd.nextInt(T - 6), y = 3 + rnd.nextInt(T - 6);
            Color c = new Color(im.getRGB(x, y));
            im.setRGB(x, y, new Color(clamp(c.getRed() - 10), clamp(c.getGreen() + 22), clamp(c.getBlue() - 5)).getRGB());
        }
        return im;
    }

    private BufferedImage makeOre(BufferedImage d, int m, Random rnd) {
        BufferedImage im = copy(d);
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        Mineral mi = Mineral.ALL[m];
        if (m == Mineral.HEART) {
            g.setPaint(new RadialGradientPaint(16, 16, 13, new float[]{0f, 0.5f, 1f},
                new Color[]{new Color(255, 250, 180), new Color(255, 120, 0), new Color(120, 20, 0, 0)}));
            g.fillOval(3, 3, 26, 26);
            g.setColor(new Color(255, 240, 200));
            g.fillRect(12, 10, 3, 3);
        } else if (m == 0) {
            // 煤:几块不规则的黑色大块
            for (int i = 0; i < 3; i++) {
                int x = 3 + rnd.nextInt(18), y = 3 + rnd.nextInt(18), s = 7 + rnd.nextInt(4);
                g.setColor(new Color(15, 15, 18));
                g.fillRect(x, y + 1, s, s - 2);
                g.fillRect(x + 1, y, s - 2, s);
                g.setColor(mi.color);
                g.fillRect(x + 1, y + 1, s - 2, s - 2);
                g.setColor(new Color(95, 95, 108));
                g.fillRect(x + 2, y + 2, 3, 1);
                g.fillRect(x + 2, y + 3, 1, 2);
            }
        } else {
            // 宝石/矿块:菱形,带暗边和高光
            boolean gem = m >= 5;
            int n = gem ? 2 + rnd.nextInt(2) : 3 + rnd.nextInt(2);
            for (int i = 0; i < n; i++) {
                int s = gem ? 6 + rnd.nextInt(2) : 4 + rnd.nextInt(3);
                int x = s + 1 + rnd.nextInt(T - 2 * s - 2), y = s + 1 + rnd.nextInt(T - 2 * s - 2);
                Path2D.Double p = diamond(x, y, s);
                g.setColor(mi.color.darker().darker());
                g.fill(p);
                g.setColor(mi.color);
                g.fill(diamond(x, y, s - 1.5));
                g.setColor(brighter(mi.color));
                g.fill(diamond(x - 1, y - 1, (s - 1.5) * 0.45));
                g.setColor(new Color(255, 255, 255, 230));
                g.fillRect(x - 2, y - s + 3, 2, 2);
            }
        }
        g.dispose();
        return im;
    }

    private static Path2D.Double diamond(double x, double y, double s) {
        Path2D.Double p = new Path2D.Double();
        p.moveTo(x, y - s);
        p.lineTo(x + s, y);
        p.lineTo(x, y + s);
        p.lineTo(x - s, y);
        p.closePath();
        return p;
    }

    private static Color brighter(Color c) {
        return new Color(Math.min(255, c.getRed() + 60), Math.min(255, c.getGreen() + 60), Math.min(255, c.getBlue() + 60));
    }

    private BufferedImage makeRock(Random rnd) {
        BufferedImage im = img();
        Color base = new Color(118, 118, 126);
        for (int y = 0; y < T; y++)
            for (int x = 0; x < T; x++) im.setRGB(x, y, shade(base, 0.9 + rnd.nextDouble() * 0.14));
        Graphics2D g = im.createGraphics();
        g.setColor(new Color(170, 170, 180));
        g.fillRect(0, 0, T, 2);
        g.fillRect(0, 0, 2, T);
        g.setColor(new Color(60, 60, 68));
        g.fillRect(0, T - 2, T, 2);
        g.fillRect(T - 2, 0, 2, T);
        g.setColor(new Color(72, 72, 80));
        int x = 4 + rnd.nextInt(10), y = 4 + rnd.nextInt(6);
        for (int i = 0; i < 6; i++) {
            int nx = x + 2 + rnd.nextInt(4), ny = y + rnd.nextInt(5) - 1;
            g.drawLine(x, y, nx, ny);
            x = nx;
            y = Math.max(3, Math.min(T - 4, ny));
        }
        g.dispose();
        return im;
    }

    private BufferedImage makeLava(int f, Random rnd) {
        BufferedImage im = img();
        for (int y = 0; y < T; y++)
            for (int x = 0; x < T; x++) {
                double v = Math.sin(x * 0.33 + f * Math.PI / 2 + Math.sin(y * 0.27 + f) * 1.6) * 0.5 + 0.5;
                v = v * 0.75 + rnd.nextDouble() * 0.12;
                int r = 255, gg = clamp((int) (60 + v * 170)), b = clamp((int) (v * 60));
                if (v < 0.25) {
                    r = 190;
                    gg = 40;
                    b = 10;
                }
                im.setRGB(x, y, 0xff000000 | (r << 16) | (gg << 8) | b);
            }
        Graphics2D g = im.createGraphics();
        g.setColor(new Color(255, 245, 160));
        for (int i = 0; i < 3; i++) {
            int bx = rnd.nextInt(T - 4), by = rnd.nextInt(T - 4);
            g.fillOval(bx, by, 3, 3);
        }
        g.dispose();
        return im;
    }

    private BufferedImage makeBedrock(Random rnd) {
        BufferedImage im = img();
        for (int y = 0; y < T; y++)
            for (int x = 0; x < T; x++) {
                boolean stripe = ((x + y) / 4) % 3 == 0;
                im.setRGB(x, y, shade(new Color(48, 42, 58), (stripe ? 0.75 : 1.0) * (0.92 + rnd.nextDouble() * 0.14)));
            }
        return im;
    }

    private BufferedImage makeConcrete(Random rnd) {
        BufferedImage im = img();
        for (int y = 0; y < T; y++)
            for (int x = 0; x < T; x++) {
                boolean line = y == 0 || y == 16 || (y < 16 ? x == 0 : x == 16);
                im.setRGB(x, y, shade(new Color(168, 168, 162), line ? 0.7 : 0.94 + rnd.nextDouble() * 0.1));
            }
        return im;
    }
}
