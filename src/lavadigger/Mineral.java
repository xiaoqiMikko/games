package lavadigger;

import java.awt.Color;

/** 矿物定义:越深越值钱。深度单位是"格"(地表以下第几行)。 */
final class Mineral {
    // 贴图样式
    static final int COAL = 0, NUGGET = 1, GEM = 2, CRYSTAL = 3, AMBER = 4, FOSSIL = 5, OPAL = 6, METEOR = 7, HEART_STYLE = 8,
        CUBE = 9, BANDED = 10, MATRIX = 11, MOON = 12, STARCORE = 13;
    // 动态特效(画在黑暗遮罩之后)
    static final int FX_NONE = 0, FX_OPAL = 1, FX_METEOR = 2, FX_AMBER = 3, FX_HEART = 4, FX_FUMES = 5, FX_SHEEN = 6,
        FX_MOON = 7, FX_LIGHTNING = 8, FX_FLAME = 9, FX_PINK = 10, FX_SWIRL = 11;

    final String name;
    final int value;
    final Color color;
    final int minDepth;   // 最浅出现深度
    final int peakDepth;  // 最常见深度
    final int spread;     // 分布宽度
    final double weight;  // 相对出现率
    final int style;
    final int fx;

    Mineral(String name, int value, Color color, int minDepth, int peakDepth, int spread, double weight, int style, int fx) {
        this.name = name;
        this.value = value;
        this.color = color;
        this.minDepth = minDepth;
        this.peakDepth = peakDepth;
        this.spread = spread;
        this.weight = weight;
        this.style = style;
        this.fx = fx;
    }

    static final int HEART = 8;
    /** 第 2 版、第 3 版新增的矿分别从这两个编号开始(老存档读档时会补进地图) */
    static final int FIRST_V2 = 9, FIRST_V3 = 18;

    /** 注意:编号就是存档里的格子编码,只能往后追加,不能插队或改顺序 */
    static final Mineral[] ALL = {
        new Mineral("煤炭", 15, new Color(30, 30, 34), 1, 8, 30, 1.0, COAL, FX_NONE),
        new Mineral("铁矿", 30, new Color(190, 150, 125), 4, 25, 35, 1.0, NUGGET, FX_NONE),
        new Mineral("铜矿", 60, new Color(225, 125, 50), 12, 50, 40, 0.9, NUGGET, FX_NONE),
        new Mineral("银矿", 140, new Color(215, 220, 232), 30, 85, 45, 0.85, NUGGET, FX_NONE),
        new Mineral("金矿", 320, new Color(255, 205, 40), 55, 130, 55, 0.8, NUGGET, FX_NONE),
        new Mineral("翡翠", 750, new Color(40, 210, 95), 95, 190, 60, 0.7, GEM, FX_NONE),
        new Mineral("红宝石", 1600, new Color(230, 30, 65), 140, 260, 70, 0.6, GEM, FX_NONE),
        new Mineral("钻石", 4000, new Color(160, 240, 255), 200, 340, 80, 0.5, GEM, FX_NONE),
        new Mineral("熔岩之心", 30000, new Color(255, 95, 0), 355, 395, 20, 0.22, HEART_STYLE, FX_HEART),
        // ---- 第 2 版新增 ----
        new Mineral("石英", 25, new Color(238, 234, 226), 2, 15, 30, 0.8, CRYSTAL, FX_NONE),
        new Mineral("锡矿", 45, new Color(150, 165, 185), 8, 38, 35, 0.8, NUGGET, FX_NONE),
        new Mineral("紫水晶", 220, new Color(175, 95, 230), 45, 105, 45, 0.7, CRYSTAL, FX_NONE),
        new Mineral("琥珀", 420, new Color(245, 160, 30), 70, 150, 50, 0.6, AMBER, FX_AMBER),
        new Mineral("铂金", 520, new Color(205, 222, 245), 85, 165, 55, 0.65, NUGGET, FX_NONE),
        new Mineral("化石", 950, new Color(228, 215, 185), 110, 210, 60, 0.4, FOSSIL, FX_NONE),
        new Mineral("蓝宝石", 1100, new Color(45, 95, 235), 120, 225, 65, 0.6, GEM, FX_NONE),
        new Mineral("欧泊", 2500, new Color(150, 225, 215), 180, 300, 70, 0.5, OPAL, FX_OPAL),
        new Mineral("陨铁", 6000, new Color(95, 85, 120), 260, 380, 50, 0.35, METEOR, FX_METEOR),
        // ---- 第 3 版新增 ----
        new Mineral("岩盐", 20, new Color(242, 196, 202), 1, 10, 25, 0.7, CUBE, FX_NONE),
        new Mineral("硫磺", 35, new Color(235, 215, 50), 5, 30, 30, 0.7, NUGGET, FX_FUMES),
        new Mineral("孔雀石", 90, new Color(40, 170, 110), 20, 60, 40, 0.7, BANDED, FX_SHEEN),
        new Mineral("绿松石", 180, new Color(70, 200, 200), 35, 95, 45, 0.65, MATRIX, FX_NONE),
        new Mineral("黄玉", 380, new Color(255, 175, 50), 60, 140, 50, 0.6, GEM, FX_NONE),
        new Mineral("月光石", 650, new Color(215, 230, 255), 95, 175, 55, 0.55, MOON, FX_MOON),
        new Mineral("雷晶", 1800, new Color(140, 120, 255), 150, 245, 60, 0.45, CRYSTAL, FX_LIGHTNING),
        new Mineral("火焰晶", 3200, new Color(255, 90, 40), 200, 290, 65, 0.4, CRYSTAL, FX_FLAME),
        new Mineral("粉钻", 5000, new Color(255, 140, 200), 240, 330, 70, 0.35, GEM, FX_PINK),
        new Mineral("星核", 12000, new Color(120, 60, 200), 320, 395, 35, 0.25, STARCORE, FX_SWIRL),
    };

    /** 按价格从低到高的编号顺序(用于价目表、收购清单) */
    static final int[] BY_VALUE = byValue();

    private static int[] byValue() {
        Integer[] idx = new Integer[ALL.length];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        java.util.Arrays.sort(idx, (a, b) -> Integer.compare(ALL[a].value, ALL[b].value));
        int[] out = new int[idx.length];
        for (int i = 0; i < idx.length; i++) out[i] = idx[i];
        return out;
    }

    /** 挖掘难度系数:越贵越硬(煤 1.1,钻石约 1.9,熔岩之心约 2.2) */
    double hardness() {
        return 1.1 + 0.1 * (Math.log(value / 15.0) / Math.log(2));
    }

    /** 按深度随机挑一种矿,没有合适的返回 -1 */
    static int pick(int depth, java.util.Random rnd) {
        return pick(depth, rnd, 0);
    }

    /** 只在编号 >= from 的矿里挑 */
    static int pick(int depth, java.util.Random rnd, int from) {
        double[] w = new double[ALL.length];
        double sum = 0;
        for (int i = from; i < ALL.length; i++) {
            Mineral m = ALL[i];
            if (i == HEART) continue; // 熔岩之心是单独摆放的稀有宝物
            if (depth < m.minDepth) continue;
            double z = (depth - m.peakDepth) / (double) m.spread;
            w[i] = m.weight * Math.exp(-z * z);
            sum += w[i];
        }
        if (sum <= 1e-9) return -1;
        double x = rnd.nextDouble() * sum;
        for (int i = 0; i < w.length; i++) {
            x -= w[i];
            if (x <= 0 && w[i] > 0) return i;
        }
        return -1;
    }
}
