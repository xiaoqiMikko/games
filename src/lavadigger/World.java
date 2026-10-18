package lavadigger;

import java.util.Arrays;
import java.util.Random;

/** 地图:按格子存储,每格一个字节 */
final class World {
    static final int T = 32;          // 一格的像素大小
    static final int COLS = 48;
    static final int ROWS = 420;
    static final int SURFACE = 8;     // 地表所在行(上面是天空)

    static final byte AIR = 0, DIRT = 1, ROCK = 2, BEDROCK = 3, CONCRETE = 4, LAVA = 5, GAS = 6;
    static final byte ORE = 10;       // ORE + 矿物编号

    byte[] tiles = new byte[COLS * ROWS];
    long seed;

    byte get(int c, int r) {
        if (c < 0 || c >= COLS || r < 0 || r >= ROWS) return r < 0 ? AIR : BEDROCK;
        return tiles[r * COLS + c];
    }

    void set(int c, int r, byte t) {
        if (c < 0 || c >= COLS || r < 0 || r >= ROWS) return;
        tiles[r * COLS + c] = t;
    }

    /** 左右边界和最底层视为实心;天空以上视为空 */
    boolean solid(int c, int r) {
        if (c < 0 || c >= COLS) return true;
        if (r < 0) return false;
        if (r >= ROWS) return true;
        return tiles[r * COLS + c] != AIR;
    }

    static boolean diggable(byte t) {
        return t == DIRT || t == LAVA || t == GAS || t >= ORE;
    }

    static int mineralOf(byte t) {
        return t >= ORE ? t - ORE : -1;
    }

    /** 挖掘难度:越深的土越硬 */
    double hardness(int c, int r) {
        byte t = get(c, r);
        int d = Math.max(0, r - SURFACE);
        double base;
        if (t >= ORE) base = 1.15 + 0.1 * (t - ORE);
        else if (t == LAVA) base = 1.25;
        else base = 1.0;
        return base * (1 + d / 250.0);
    }

    void generate(long seed) {
        this.seed = seed;
        Random rnd = new Random(seed);
        Arrays.fill(tiles, AIR);
        for (int r = SURFACE; r < ROWS; r++) {
            int d = r - SURFACE;
            for (int c = 0; c < COLS; c++) {
                byte t;
                if (r >= ROWS - 3) t = BEDROCK;
                else if (d == 0) t = Shop.isUnderShop(c) ? CONCRETE : DIRT;
                else {
                    t = DIRT;
                    double rockP = d < 4 ? 0 : Math.min(0.2, 0.025 + d * 0.0007);
                    double lavaP = d < 45 ? 0 : Math.min(0.09, 0.012 + (d - 45) * 0.0003);
                    double gasP = d < 15 ? 0 : Math.min(0.03, 0.006 + d * 0.00008);
                    double oreP = Math.min(0.2, 0.09 + d * 0.00025);
                    double x = rnd.nextDouble();
                    if (x < rockP) t = ROCK;
                    else if ((x -= rockP) < lavaP) t = LAVA;
                    else if ((x -= lavaP) < gasP) t = GAS;
                    else if ((x -= gasP) < oreP) {
                        int m = Mineral.pick(d, rnd);
                        if (m >= 0) t = (byte) (ORE + m);
                    }
                }
                set(c, r, t);
            }
        }
        // 让岩石、熔岩、矿脉成团出现
        byte[] copy = tiles.clone();
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int r = SURFACE + 2; r < ROWS - 3; r++) {
            for (int c = 0; c < COLS; c++) {
                byte t = copy[r * COLS + c];
                double p = t == ROCK ? 0.35 : t == LAVA ? 0.5 : t >= ORE ? 0.35 : 0;
                if (p == 0) continue;
                for (int[] dd : dirs) {
                    if (rnd.nextDouble() < p) {
                        int nc = c + dd[0], nr = r + dd[1];
                        if (nr > SURFACE + 1 && nr < ROWS - 3 && get(nc, nr) == DIRT) set(nc, nr, t);
                    }
                }
            }
        }
        // 地下空洞(掉下去会摔)
        for (int i = 0; i < 70; i++) {
            int cc = rnd.nextInt(COLS);
            int cr = SURFACE + 20 + rnd.nextInt(ROWS - SURFACE - 30);
            int rx = 1 + rnd.nextInt(3), ry = 1 + rnd.nextInt(2);
            for (int r = cr - ry; r <= cr + ry; r++) {
                for (int c = cc - rx; c <= cc + rx; c++) {
                    double nx = (c - cc) / (rx + 0.5), ny = (r - cr) / (ry + 0.5);
                    if (nx * nx + ny * ny <= 1 && r < ROWS - 3) set(c, r, AIR);
                }
            }
        }
        // 熔岩之心:最底部附近只放 6 颗,四周围着熔岩
        for (int i = 0; i < 6; i++) {
            int c = 2 + rnd.nextInt(COLS - 4);
            int r = ROWS - 18 + rnd.nextInt(12);
            for (int dr = -1; dr <= 1; dr++)
                for (int dc = -1; dc <= 1; dc++)
                    if (rnd.nextDouble() < 0.6) set(c + dc, r + dr, LAVA);
            set(c, r, (byte) (ORE + Mineral.HEART));
        }
        // 出生点附近前几层保证是普通泥土,新手友好
        for (int r = SURFACE; r < SURFACE + 3; r++) {
            for (int c = Shop.START_DIG_COL - 2; c <= Shop.START_DIG_COL + 2; c++) {
                byte t = get(c, r);
                if (t == ROCK || t == GAS || t == LAVA || t == AIR) set(c, r, DIRT);
            }
        }
    }
}
