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
        if (mi.style == Mineral.HEART_STYLE) {
            heart(g);
            g.dispose();
            return im;
        }
        // 随机挑几个不重叠的位置(抖动网格)
        double[][] slots = {{9, 9}, {23, 9}, {9, 23}, {23, 23}, {16, 16}};
        java.util.List<double[]> pos = new java.util.ArrayList<>();
        for (double[] sl : slots) pos.add(new double[]{sl[0] + rnd.nextInt(5) - 2, sl[1] + rnd.nextInt(5) - 2});
        java.util.Collections.shuffle(pos, rnd);
        switch (mi.style) {
            case Mineral.COAL:
                for (int i = 0; i < 3; i++) coal(g, pos.get(i)[0], pos.get(i)[1], 5 + rnd.nextInt(2), rnd);
                break;
            case Mineral.NUGGET: {
                vein(g, mi.color, rnd);
                int n = mi.value < 100 ? 3 : 3 + rnd.nextInt(2);
                for (int i = 0; i < n; i++) nugget(g, pos.get(i)[0], pos.get(i)[1], 3.6 + rnd.nextDouble() * 1.6, mi.color, rnd);
                break;
            }
            case Mineral.CRYSTAL:
                crystalCluster(g, 9 + rnd.nextInt(14), 24 + rnd.nextInt(4), mi.color, rnd, 1.0);
                if (rnd.nextBoolean()) crystalCluster(g, 6 + rnd.nextInt(20), 12 + rnd.nextInt(4), mi.color, rnd, 0.6);
                break;
            case Mineral.AMBER:
                amber(g, pos.get(0)[0], pos.get(0)[1], 6.5, rnd, true);
                amber(g, pos.get(1)[0], pos.get(1)[1], 4.5, rnd, false);
                break;
            case Mineral.FOSSIL:
                ammonite(g, 15 + rnd.nextInt(3), 15 + rnd.nextInt(3), 9, mi.color, rnd.nextDouble() * Math.PI * 2);
                bone(g, pos.get(4)[0] > 16 ? 5 : 20, rnd.nextBoolean() ? 6 : 27, mi.color, rnd);
                break;
            case Mineral.OPAL:
                opal(g, pos.get(0)[0], pos.get(0)[1], 7, rnd);
                opal(g, pos.get(1)[0], pos.get(1)[1], 4.5, rnd);
                break;
            case Mineral.METEOR:
                meteor(g, 16 + rnd.nextInt(3) - 1, 16 + rnd.nextInt(3) - 1, 10, mi.color, rnd);
                break;
            case Mineral.CUBE:
                for (int i = 0; i < 4; i++) cube(g, pos.get(i)[0], pos.get(i)[1], 3 + rnd.nextInt(2), mi.color);
                break;
            case Mineral.BANDED:
                banded(g, pos.get(0)[0], pos.get(0)[1], 7, mi.color, rnd);
                banded(g, pos.get(1)[0], pos.get(1)[1], 4.5, mi.color, rnd);
                break;
            case Mineral.MATRIX:
                matrix(g, pos.get(0)[0], pos.get(0)[1], 6.5, mi.color, rnd);
                matrix(g, pos.get(1)[0], pos.get(1)[1], 4.5, mi.color, rnd);
                break;
            case Mineral.MOON:
                moon(g, pos.get(0)[0], pos.get(0)[1], 6.5, mi.color);
                moon(g, pos.get(1)[0], pos.get(1)[1], 4.5, mi.color);
                break;
            case Mineral.STARCORE:
                starcore(g, 16, 16, 9, mi.color, rnd);
                break;
            default:
                // 切割宝石:两颗大的,有时再加一颗小的
                gem(g, pos.get(0)[0], pos.get(0)[1], 6.5, mi.color, m);
                gem(g, pos.get(1)[0], pos.get(1)[1], 5.5, mi.color, m);
                if (rnd.nextBoolean()) gem(g, pos.get(2)[0], pos.get(2)[1], 3.5, mi.color, m);
        }
        g.dispose();
        return im;
    }

    /** 岩盐:小立方体晶块(等轴测画法) */
    private static void cube(Graphics2D g, double cx, double cy, double s, Color c) {
        double[] t = {cx, cy - s}, r = {cx + s, cy - s * 0.5}, b = {cx, cy}, l = {cx - s, cy - s * 0.5};
        double[] rb = {cx + s, cy + s * 0.5}, bb = {cx, cy + s}, lb = {cx - s, cy + s * 0.5};
        g.setColor(mul(c, 0.35));
        g.translate(0.7, 0.8);
        g.fill(poly(t, r, rb, bb, lb, l));
        g.translate(-0.7, -0.8);
        g.setColor(new Color(Math.min(255, c.getRed() + 13), Math.min(255, c.getGreen() + 40), Math.min(255, c.getBlue() + 40), 230));
        g.fill(poly(t, r, b, l));                        // 顶面
        g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 225));
        g.fill(poly(l, b, bb, lb));                      // 左面
        g.setColor(new Color(mul(c, 0.78).getRed(), mul(c, 0.78).getGreen(), mul(c, 0.78).getBlue(), 225));
        g.fill(poly(b, r, rb, bb));                      // 右面
        g.setStroke(new BasicStroke(0.5f));
        g.setColor(mul(c, 0.55));
        g.draw(poly(t, r, rb, bb, lb, l));
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(255, 255, 255, 220));
        g.fill(new Rectangle2D.Double(cx - s * 0.3, cy - s * 0.65, 1.3, 1.3));
    }

    /** 孔雀石:深浅绿色同心条纹 */
    private static void banded(Graphics2D g, double cx, double cy, double s, Color c, Random rnd) {
        Path2D.Double p = blob(cx, cy, s, 9, 0.8, rnd);
        g.setColor(mul(c, 0.3));
        g.translate(0.8, 1);
        g.fill(p);
        g.translate(-0.8, -1);
        Shape oc = g.getClip();
        g.clip(p);
        double ox = cx + (rnd.nextDouble() - 0.5) * s, oy = cy + (rnd.nextDouble() - 0.5) * s;
        for (int i = 8; i >= 0; i--) {
            double rr = (i + 1) * s * 0.28;
            g.setColor(i % 2 == 0 ? lighten(c, 50) : mul(c, 0.6));
            g.fill(new Ellipse2D.Double(ox - rr, oy - rr * 0.8, rr * 2, rr * 1.6));
        }
        g.setClip(oc);
        g.setStroke(new BasicStroke(0.7f));
        g.setColor(mul(c, 0.4));
        g.draw(p);
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(255, 255, 255, 200));
        g.fill(new Ellipse2D.Double(cx - s * 0.5, cy - s * 0.55, 2, 1.4));
    }

    /** 绿松石:天蓝色块,带深色网状纹 */
    private static void matrix(Graphics2D g, double cx, double cy, double s, Color c, Random rnd) {
        Path2D.Double p = blob(cx, cy, s, 8, 0.78, rnd);
        g.setColor(mul(c, 0.3));
        g.translate(0.8, 1);
        g.fill(p);
        g.translate(-0.8, -1);
        g.setPaint(new RadialGradientPaint((float) (cx - s * 0.3), (float) (cy - s * 0.35), (float) (s * 1.5),
            new float[]{0f, 1f}, new Color[]{lighten(c, 60), mul(c, 0.75)}));
        g.fill(p);
        Shape oc = g.getClip();
        g.clip(p);
        g.setStroke(new BasicStroke(0.7f));
        g.setColor(new Color(70, 55, 40, 200));
        for (int k = 0; k < 4; k++) {
            Path2D.Double v = new Path2D.Double();
            double x = cx - s + rnd.nextDouble() * s * 2, y = cy - s;
            v.moveTo(x, y);
            for (int i = 0; i < 4; i++) {
                x += (rnd.nextDouble() - 0.5) * s;
                y += s * 0.55;
                v.lineTo(x, y);
            }
            g.draw(v);
        }
        g.setClip(oc);
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(255, 255, 255, 210));
        g.fill(new Ellipse2D.Double(cx - s * 0.5, cy - s * 0.55, 2.2, 1.5));
    }

    /** 月光石:乳白半透明圆石,中间一抹蓝色光 */
    private static void moon(Graphics2D g, double cx, double cy, double s, Color c) {
        Ellipse2D.Double e = new Ellipse2D.Double(cx - s, cy - s * 0.85, s * 2, s * 1.7);
        g.setColor(new Color(50, 60, 90));
        g.translate(0.8, 1);
        g.fill(e);
        g.translate(-0.8, -1);
        g.setPaint(new RadialGradientPaint((float) (cx - s * 0.2), (float) (cy - s * 0.2), (float) (s * 1.3),
            new float[]{0f, 0.5f, 1f}, new Color[]{new Color(250, 252, 255), c, new Color(150, 165, 200)}));
        g.fill(e);
        g.setColor(new Color(120, 170, 255, 150));
        g.fill(new Ellipse2D.Double(cx - s * 0.55, cy - s * 0.15, s * 1.1, s * 0.5));
        g.setStroke(new BasicStroke(0.6f));
        g.setColor(new Color(130, 145, 185));
        g.draw(e);
        g.setStroke(new BasicStroke(1f));
        g.setColor(Color.WHITE);
        g.fill(new Ellipse2D.Double(cx - s * 0.5, cy - s * 0.55, 2.4, 1.6));
    }

    /** 星核:黑紫色球体,里面是点点星空 */
    private static void starcore(Graphics2D g, double cx, double cy, double r, Color c, Random rnd) {
        g.setPaint(new RadialGradientPaint((float) cx, (float) cy, (float) (r * 1.6), new float[]{0f, 1f},
            new Color[]{new Color(c.getRed(), c.getGreen(), c.getBlue(), 140), new Color(c.getRed(), c.getGreen(), c.getBlue(), 0)}));
        g.fill(new Ellipse2D.Double(cx - r * 1.6, cy - r * 1.6, r * 3.2, r * 3.2));
        Ellipse2D.Double e = new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2);
        g.setPaint(new RadialGradientPaint((float) (cx - r * 0.3), (float) (cy - r * 0.3), (float) (r * 1.4),
            new float[]{0f, 0.6f, 1f}, new Color[]{lighten(c, 40), new Color(30, 10, 60), new Color(8, 4, 20)}));
        g.fill(e);
        Shape oc = g.getClip();
        g.clip(e);
        for (int i = 0; i < 14; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, rr = rnd.nextDouble() * r;
            int br = 160 + rnd.nextInt(95);
            g.setColor(new Color(br, br, 255));
            double sz = rnd.nextDouble() < 0.2 ? 1.6 : 0.9;
            g.fill(new Ellipse2D.Double(cx + Math.cos(a) * rr - sz / 2, cy + Math.sin(a) * rr - sz / 2, sz, sz));
        }
        g.setColor(new Color(200, 150, 255, 90));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(new Arc2D.Double(cx - r * 0.7, cy - r * 0.35, r * 1.4, r * 0.7, 0, 360, Arc2D.OPEN));
        g.setClip(oc);
        g.setStroke(new BasicStroke(0.8f));
        g.setColor(new Color(170, 120, 255));
        g.draw(e);
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(255, 255, 255, 230));
        g.fill(new Ellipse2D.Double(cx - r * 0.55, cy - r * 0.6, 2.6, 1.8));
    }

    /** 水晶簇:几根六棱柱从底部斜着长出来 */
    private static void crystalCluster(Graphics2D g, double bx, double by, Color c, Random rnd, double scale) {
        int n = 3;
        double[] ang = {-0.45, 0.05, 0.5};
        double[] len = {9, 13, 8};
        for (int k = 0; k < n; k++) {
            double a = ang[k] + (rnd.nextDouble() - 0.5) * 0.2, L = len[k] * scale * (0.85 + rnd.nextDouble() * 0.3), w = 2.6 * scale;
            AffineTransform old = g.getTransform();
            g.translate(bx + (k - 1) * 3 * scale, by);
            g.rotate(a);
            Path2D.Double body = poly(new double[]{-w, 0}, new double[]{-w, -L}, new double[]{0, -L - w * 1.4},
                new double[]{w, -L}, new double[]{w, 0});
            g.setColor(mul(c, 0.35));
            g.translate(0.7, 0.7);
            g.fill(body);
            g.translate(-0.7, -0.7);
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 215));
            g.fill(body);
            g.setColor(lighten(c, 55));
            g.fill(poly(new double[]{-w, 0}, new double[]{-w, -L}, new double[]{0, -L - w * 1.4}, new double[]{0, 0}));
            g.setColor(mul(c, 0.55));
            g.setStroke(new BasicStroke(0.6f));
            g.draw(body);
            g.setColor(new Color(255, 255, 255, 220));
            g.draw(new Line2D.Double(-w * 0.45, -1, -w * 0.45, -L + 1));
            g.setStroke(new BasicStroke(1f));
            g.setTransform(old);
        }
    }

    /** 琥珀:半透明橙色圆块,里面封着一只小虫 */
    private static void amber(Graphics2D g, double cx, double cy, double s, Random rnd, boolean bug) {
        Path2D.Double p = blob(cx, cy, s, 9, 0.8, rnd);
        g.setColor(new Color(120, 60, 0, 200));
        g.translate(0.8, 1);
        g.fill(p);
        g.translate(-0.8, -1);
        g.setPaint(new RadialGradientPaint((float) (cx - s * 0.3), (float) (cy - s * 0.35), (float) (s * 1.5),
            new float[]{0f, 0.6f, 1f}, new Color[]{new Color(255, 225, 120, 240), new Color(240, 150, 25, 230), new Color(170, 80, 5, 230)}));
        g.fill(p);
        if (bug) {
            g.setColor(new Color(60, 30, 10, 200));
            g.fill(new Ellipse2D.Double(cx - 1.5, cy - 0.5, 3, 2.2));
            g.setStroke(new BasicStroke(0.5f));
            for (int i = -1; i <= 1; i++) {
                g.draw(new Line2D.Double(cx - 1, cy + 0.5 + i * 0.6, cx - 2.8, cy + 1 + i * 1.2));
                g.draw(new Line2D.Double(cx + 1, cy + 0.5 + i * 0.6, cx + 2.8, cy + 1 + i * 1.2));
            }
            g.setStroke(new BasicStroke(1f));
        }
        g.setColor(new Color(255, 250, 220, 230));
        g.fill(new Ellipse2D.Double(cx - s * 0.55, cy - s * 0.6, 2.4, 1.6));
    }

    /** 菊石化石:螺旋壳 + 放射状纹路 */
    private static void ammonite(Graphics2D g, double cx, double cy, double r, Color c, double rot) {
        g.setColor(mul(c, 0.4));
        g.fill(new Ellipse2D.Double(cx - r + 0.8, cy - r + 1, r * 2, r * 2));
        g.setPaint(new RadialGradientPaint((float) (cx - r * 0.3), (float) (cy - r * 0.3), (float) (r * 1.4),
            new float[]{0f, 1f}, new Color[]{lighten(c, 25), mul(c, 0.75)}));
        g.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
        // 螺旋线
        Path2D.Double sp = new Path2D.Double();
        for (int i = 0; i <= 80; i++) {
            double t = i / 80.0, a = rot + t * Math.PI * 4.2, rr = r * 0.92 * (1 - t * 0.9);
            double x = cx + Math.cos(a) * rr, y = cy + Math.sin(a) * rr;
            if (i == 0) sp.moveTo(x, y);
            else sp.lineTo(x, y);
        }
        g.setStroke(new BasicStroke(1.1f));
        g.setColor(mul(c, 0.5));
        g.draw(sp);
        // 放射纹
        g.setStroke(new BasicStroke(0.6f));
        g.setColor(mul(c, 0.62));
        for (int i = 0; i < 14; i++) {
            double a = rot + i * Math.PI * 2 / 14;
            g.draw(new Line2D.Double(cx + Math.cos(a) * r * 0.55, cy + Math.sin(a) * r * 0.55, cx + Math.cos(a) * r * 0.95, cy + Math.sin(a) * r * 0.95));
        }
        g.setStroke(new BasicStroke(0.8f));
        g.setColor(mul(c, 0.45));
        g.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(255, 250, 235, 200));
        g.fill(new Ellipse2D.Double(cx - r * 0.6, cy - r * 0.65, 2.2, 1.6));
    }

    /** 小骨头 */
    private static void bone(Graphics2D g, double cx, double cy, Color c, Random rnd) {
        AffineTransform old = g.getTransform();
        g.translate(cx, cy);
        g.rotate((rnd.nextDouble() - 0.5) * 1.2);
        g.setColor(mul(c, 0.45));
        g.fill(new RoundRectangle2D.Double(-4, -0.8, 8, 2.6, 2, 2));
        g.setColor(c);
        g.fill(new RoundRectangle2D.Double(-4, -1.3, 8, 2.4, 2, 2));
        for (int sx : new int[]{-4, 4})
            for (int sy : new int[]{-1, 1}) g.fill(new Ellipse2D.Double(sx - 1.4, sy - 1.6, 2.6, 2.6));
        g.setTransform(old);
    }

    /** 欧泊:乳白色椭圆里布满彩色斑点 */
    private static void opal(Graphics2D g, double cx, double cy, double s, Random rnd) {
        Ellipse2D.Double e = new Ellipse2D.Double(cx - s, cy - s * 0.78, s * 2, s * 1.56);
        g.setColor(new Color(40, 60, 70));
        g.translate(0.8, 1);
        g.fill(e);
        g.translate(-0.8, -1);
        g.setPaint(new RadialGradientPaint((float) (cx - s * 0.3), (float) (cy - s * 0.3), (float) (s * 1.4),
            new float[]{0f, 1f}, new Color[]{new Color(235, 250, 248), new Color(150, 200, 205)}));
        g.fill(e);
        Shape oc = g.getClip();
        g.clip(e);
        Color[] fire = {new Color(255, 90, 140), new Color(80, 230, 120), new Color(70, 140, 255), new Color(255, 220, 60), new Color(190, 90, 255)};
        for (int i = 0; i < 12; i++) {
            Color f = fire[rnd.nextInt(fire.length)];
            g.setColor(new Color(f.getRed(), f.getGreen(), f.getBlue(), 170));
            double px = cx + (rnd.nextDouble() - 0.5) * s * 1.8, py = cy + (rnd.nextDouble() - 0.5) * s * 1.4, ps = 1.5 + rnd.nextDouble() * 2.2;
            g.fill(new Ellipse2D.Double(px - ps / 2, py - ps / 2, ps, ps * 0.8));
        }
        g.setClip(oc);
        g.setStroke(new BasicStroke(0.7f));
        g.setColor(new Color(90, 130, 140));
        g.draw(e);
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(255, 255, 255, 230));
        g.fill(new Ellipse2D.Double(cx - s * 0.55, cy - s * 0.5, 2.4, 1.6));
    }

    /** 陨铁:粗糙的深色金属块,裂缝里透出蓝光,表面有金属亮点 */
    private static void meteor(Graphics2D g, double cx, double cy, double s, Color c, Random rnd) {
        Path2D.Double p = blob(cx, cy, s, 11, 0.7, rnd);
        g.setColor(new Color(15, 12, 22));
        g.translate(1, 1.2);
        g.fill(p);
        g.translate(-1, -1.2);
        g.setPaint(new RadialGradientPaint((float) (cx - s * 0.35), (float) (cy - s * 0.4), (float) (s * 1.5),
            new float[]{0f, 1f}, new Color[]{lighten(c, 45), mul(c, 0.4)}));
        g.fill(p);
        Shape oc = g.getClip();
        g.clip(p);
        // 发光裂缝
        g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int k = 0; k < 3; k++) {
            Path2D.Double cr = new Path2D.Double();
            double x = cx + (rnd.nextDouble() - 0.5) * s, y = cy - s;
            cr.moveTo(x, y);
            for (int i = 0; i < 5; i++) {
                x += (rnd.nextDouble() - 0.5) * 5;
                y += s * 0.45;
                cr.lineTo(x, y);
            }
            g.setColor(new Color(90, 220, 255, 110));
            g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(cr);
            g.setColor(new Color(190, 250, 255));
            g.setStroke(new BasicStroke(0.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(cr);
        }
        // 撞击坑
        g.setColor(mul(c, 0.3));
        for (int i = 0; i < 4; i++) {
            double px = cx + (rnd.nextDouble() - 0.5) * s * 1.3, py = cy + (rnd.nextDouble() - 0.5) * s * 1.3;
            g.fill(new Ellipse2D.Double(px - 1.2, py - 1, 2.4, 2));
        }
        g.setClip(oc);
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(230, 235, 255));
        for (int i = 0; i < 3; i++) g.fill(new Rectangle2D.Double(cx + (rnd.nextDouble() - 0.6) * s, cy + (rnd.nextDouble() - 0.6) * s, 1.2, 1.2));
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
