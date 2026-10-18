package lavadigger;

import java.awt.*;
import java.awt.geom.*;
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

    /** 矿石格子:泥土底 + 矿脉 + 矿块/宝石 */
    private BufferedImage makeOre(BufferedImage d, int m, Random rnd) {
        BufferedImage im = copy(d);
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Mineral mi = Mineral.ALL[m];
        if (m == Mineral.HEART) {
            heart(g);
            g.dispose();
            return im;
        }
        // 随机挑几个不重叠的位置(抖动网格)
        double[][] slots = {{9, 9}, {23, 9}, {9, 23}, {23, 23}, {16, 16}};
        java.util.List<double[]> pos = new java.util.ArrayList<>();
        for (double[] sl : slots) pos.add(new double[]{sl[0] + rnd.nextInt(5) - 2, sl[1] + rnd.nextInt(5) - 2});
        java.util.Collections.shuffle(pos, rnd);
        if (m == 0) {
            for (int i = 0; i < 3; i++) coal(g, pos.get(i)[0], pos.get(i)[1], 5 + rnd.nextInt(2), rnd);
        } else if (m <= 4) {
            vein(g, mi.color, rnd);
            int n = m <= 2 ? 3 : 3 + rnd.nextInt(2);
            for (int i = 0; i < n; i++) nugget(g, pos.get(i)[0], pos.get(i)[1], 3.6 + rnd.nextDouble() * 1.6, mi.color, rnd);
        } else {
            // 宝石:两颗大的,有时再加一颗小的
            gem(g, pos.get(0)[0], pos.get(0)[1], 6.5, mi.color, m);
            gem(g, pos.get(1)[0], pos.get(1)[1], 5.5, mi.color, m);
            if (rnd.nextBoolean()) gem(g, pos.get(2)[0], pos.get(2)[1], 3.5, mi.color, m);
        }
        g.dispose();
        return im;
    }

    private static void vein(Graphics2D g, Color c, Random rnd) {
        g.setStroke(new BasicStroke(1.4f));
        g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 90));
        Path2D.Double p = new Path2D.Double();
        double y = 4 + rnd.nextInt(24);
        p.moveTo(0, y);
        for (int x = 6; x <= T; x += 6) {
            y = Math.max(2, Math.min(T - 2, y + rnd.nextInt(9) - 4));
            p.lineTo(x, y);
        }
        g.draw(p);
        g.setStroke(new BasicStroke(1f));
    }

    /** 煤块:有棱角的黑块,带一点蓝灰色反光 */
    private static void coal(Graphics2D g, double cx, double cy, double s, Random rnd) {
        Path2D.Double p = blob(cx, cy, s, 6, 0.75, rnd);
        g.setColor(new Color(10, 10, 12));
        g.translate(0.8, 0.8);
        g.fill(p);
        g.translate(-0.8, -0.8);
        g.setPaint(new GradientPaint((float) (cx - s), (float) (cy - s), new Color(70, 72, 84), (float) (cx + s), (float) (cy + s), new Color(18, 18, 22)));
        g.fill(p);
        Path2D.Double facet = new Path2D.Double();
        facet.moveTo(cx - s * 0.7, cy - s * 0.1);
        facet.lineTo(cx - s * 0.1, cy - s * 0.75);
        facet.lineTo(cx + s * 0.2, cy - s * 0.1);
        facet.closePath();
        g.setColor(new Color(95, 100, 118, 170));
        g.fill(facet);
        g.setColor(new Color(170, 180, 205));
        g.fill(new Rectangle2D.Double(cx - s * 0.35, cy - s * 0.45, 1.5, 1.5));
    }

    /** 金属矿块:不规则圆块,渐变 + 高光 + 小坑 */
    private static void nugget(Graphics2D g, double cx, double cy, double s, Color c, Random rnd) {
        Path2D.Double p = blob(cx, cy, s, 8, 0.72, rnd);
        g.setColor(mul(c, 0.38));
        g.translate(0.8, 1);
        g.fill(p);
        g.translate(-0.8, -1);
        g.setPaint(new RadialGradientPaint((float) (cx - s * 0.35), (float) (cy - s * 0.4), (float) (s * 1.6),
            new float[]{0f, 0.55f, 1f}, new Color[]{lighten(c, 90), c, mul(c, 0.55)}));
        g.fill(p);
        g.setColor(mul(c, 0.45));
        g.setStroke(new BasicStroke(0.8f));
        g.draw(p);
        g.setStroke(new BasicStroke(1f));
        g.setColor(mul(c, 0.6));
        g.fill(new Ellipse2D.Double(cx + s * 0.15, cy + s * 0.15, 1.6, 1.6));
        g.setColor(new Color(255, 255, 255, 230));
        g.fill(new Ellipse2D.Double(cx - s * 0.5, cy - s * 0.55, 2.2, 1.6));
    }

    /** 切割宝石:台面 + 冠部 + 亭部,不同切面不同明暗 */
    private static void gem(Graphics2D g, double cx, double cy, double s, Color c, int m) {
        double tY = cy - s * 0.7, gY = cy - s * 0.15;
        double[] pL = {cx - s, gY}, pR = {cx + s, gY}, pB = {cx, cy + s};
        double[] tL = {cx - s * 0.55, tY}, tR = {cx + s * 0.55, tY};
        double[] iL = {cx - s * 0.4, gY}, iR = {cx + s * 0.4, gY};
        Path2D.Double outline = poly(tL, tR, pR, pB, pL);
        g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 60));
        g.fill(new Ellipse2D.Double(cx - s * 1.5, cy - s * 1.4, s * 3, s * 2.8));
        g.setColor(mul(c, 0.3));
        g.translate(0.8, 1);
        g.fill(outline);
        g.translate(-0.8, -1);
        g.setColor(lighten(c, 70));
        g.fill(poly(tL, tR, iR, iL));
        g.setColor(lighten(c, 30));
        g.fill(poly(pL, tL, iL));
        g.setColor(c);
        g.fill(poly(tR, pR, iR));
        g.setColor(mul(c, 0.85));
        g.fill(poly(pL, iL, pB));
        g.setColor(mul(c, 0.62));
        g.fill(poly(iL, iR, pB));
        g.setColor(mul(c, 0.45));
        g.fill(poly(iR, pR, pB));
        g.setStroke(new BasicStroke(0.7f));
        g.setColor(mul(c, 0.35));
        g.draw(outline);
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(255, 255, 255, m == 7 ? 255 : 220));
        g.fill(new Rectangle2D.Double(cx - s * 0.35, tY + 1, Math.max(1.5, s * 0.3), 1.4));
        if (s > 4) g.fill(new Rectangle2D.Double(cx + s * 0.55, gY - s * 0.45, 1, 1));
    }

    private static void heart(Graphics2D g) {
        g.setPaint(new RadialGradientPaint(16, 16, 15, new float[]{0f, 0.45f, 1f},
            new Color[]{new Color(255, 250, 200), new Color(255, 120, 0, 230), new Color(120, 20, 0, 0)}));
        g.fill(new Ellipse2D.Double(1, 1, 30, 30));
        double[] a = {16, 5}, b = {25, 14}, c = {16, 28}, d = {7, 14}, mid = {16, 14};
        g.setColor(new Color(170, 30, 0));
        g.fill(poly(a, b, c, d));
        g.setColor(new Color(255, 140, 20));
        g.fill(poly(a, mid, d));
        g.setColor(new Color(255, 200, 70));
        g.fill(poly(a, b, mid));
        g.setColor(new Color(220, 70, 0));
        g.fill(poly(d, mid, c));
        g.setColor(new Color(255, 245, 210));
        g.fill(new Rectangle2D.Double(13, 9, 2.5, 2.5));
    }

    private static Path2D.Double blob(double cx, double cy, double s, int n, double minR, Random rnd) {
        Path2D.Double p = new Path2D.Double();
        double a0 = rnd.nextDouble() * Math.PI;
        for (int i = 0; i < n; i++) {
            double a = a0 + i * Math.PI * 2 / n, r = s * (minR + rnd.nextDouble() * (1.05 - minR));
            double x = cx + Math.cos(a) * r, y = cy + Math.sin(a) * r;
            if (i == 0) p.moveTo(x, y);
            else p.lineTo(x, y);
        }
        p.closePath();
        return p;
    }

    private static Path2D.Double poly(double[]... pts) {
        Path2D.Double p = new Path2D.Double();
        p.moveTo(pts[0][0], pts[0][1]);
        for (int i = 1; i < pts.length; i++) p.lineTo(pts[i][0], pts[i][1]);
        p.closePath();
        return p;
    }

    private static Color mul(Color c, double k) {
        return new Color(clamp((int) (c.getRed() * k)), clamp((int) (c.getGreen() * k)), clamp((int) (c.getBlue() * k)));
    }

    private static Color lighten(Color c, int v) {
        return new Color(clamp(c.getRed() + v), clamp(c.getGreen() + v), clamp(c.getBlue() + v));
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
