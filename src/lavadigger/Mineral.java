package lavadigger;

import java.awt.Color;

/** 矿物定义:越深越值钱。深度单位是"格"(地表以下第几行)。 */
final class Mineral {
    final String name;
    final int value;
    final Color color;
    final int minDepth;   // 最浅出现深度
    final int peakDepth;  // 最常见深度
    final int spread;     // 分布宽度
    final double weight;  // 相对出现率

    Mineral(String name, int value, Color color, int minDepth, int peakDepth, int spread, double weight) {
        this.name = name;
        this.value = value;
        this.color = color;
        this.minDepth = minDepth;
        this.peakDepth = peakDepth;
        this.spread = spread;
        this.weight = weight;
    }

    static final int HEART = 8;

    static final Mineral[] ALL = {
        new Mineral("煤炭", 15, new Color(30, 30, 34), 1, 8, 30, 1.0),
        new Mineral("铁矿", 30, new Color(190, 150, 125), 4, 25, 35, 1.0),
        new Mineral("铜矿", 60, new Color(225, 125, 50), 12, 50, 40, 0.9),
        new Mineral("银矿", 140, new Color(215, 220, 232), 30, 85, 45, 0.85),
        new Mineral("金矿", 320, new Color(255, 205, 40), 55, 130, 55, 0.8),
        new Mineral("翡翠", 750, new Color(40, 210, 95), 95, 190, 60, 0.7),
        new Mineral("红宝石", 1600, new Color(230, 30, 65), 140, 260, 70, 0.6),
        new Mineral("钻石", 4000, new Color(160, 240, 255), 200, 340, 80, 0.5),
        new Mineral("熔岩之心", 30000, new Color(255, 95, 0), 355, 395, 20, 0.22),
    };

    /** 按深度随机挑一种矿,没有合适的返回 -1 */
    static int pick(int depth, java.util.Random rnd) {
        double[] w = new double[ALL.length];
        double sum = 0;
        for (int i = 0; i < ALL.length; i++) {
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
