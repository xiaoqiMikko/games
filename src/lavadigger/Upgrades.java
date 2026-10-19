package lavadigger;

/** 七种可升级部件,每种 7 档(第 0 档是出厂配置) */
final class Upgrades {
    static final int FUEL = 0, DRILL = 1, ENGINE = 2, CARGO = 3, HULL = 4, RADIATOR = 5, PROBE = 6;
    static final int KINDS = 7, TIERS = 7;

    static final String[] NAMES = {"油箱", "钻头", "引擎", "货舱", "车身", "散热器", "探测器"};
    static final String[] DESC = {
        "能装更多燃油,跑得更远、挖得更深。",
        "挖穿一格更快,也就更省油。",
        "左右行驶和向上飞行都更快。",
        "一趟能装更多矿石。",
        "更耐摔,瓦斯爆炸和落石也扣得更少。升级时补上新增的耐久。",
        "降低车温(车温越高越费油),也减少熔岩灼烧。",
        "透过黑暗显示周围的矿石、宝藏和瓦斯,档次越高看得越远。",
    };
    static final String[] TIER_NAMES = {"出厂", "青铜", "白银", "黄金", "铂金", "钻石", "传说"};

    static final double[][] VALUES = {
        {10, 15, 25, 40, 60, 92, 115},
        {1.0, 1.35, 1.8, 2.4, 3.2, 4.3, 6.0},
        {1.0, 1.15, 1.3, 1.5, 1.75, 2.0, 2.4},
        {15, 24, 36, 52, 75, 110, 160},
        {10, 18, 30, 48, 75, 110, 160},
        {0, 0.15, 0.30, 0.45, 0.60, 0.72, 0.85},
        {0, 3, 4, 5, 6, 8, 10},
    };

    /** 车身各档对瓦斯爆炸和地震落石的减伤比例 */
    static final double[] HULL_ARMOR = {0, 0.05, 0.10, 0.15, 0.20, 0.25, 0.30};

    static final int[][] PRICES = {
        {0, 750, 2950, 13000, 60000, 230000, 375000},
        {0, 850, 3150, 14500, 66000, 260000, 410000},
        {0, 650, 2850, 13000, 58000, 225000, 360000},
        {0, 650, 2650, 12000, 54500, 205000, 335000},
        {0, 750, 2950, 13000, 60000, 230000, 375000},
        {0, 850, 3300, 14500, 70000, 265000, 435000},
        {0, 500, 2000, 9100, 43000, 160000, 260000},
    };

    static String fmt(int kind, int tier) {
        double v = VALUES[kind][tier];
        switch (kind) {
            case FUEL: return (int) v + " 升";
            case DRILL: return "速度×" + trim(v);
            case ENGINE: return "动力×" + trim(v);
            case CARGO: return (int) v + " 格";
            case HULL: return (int) v + " 点";
            case PROBE: return v == 0 ? "无" : "半径 " + (int) v + " 格";
            default: return "减伤 " + Math.round(v * 100) + "%";
        }
    }

    private static String trim(double v) {
        String s = String.format("%.2f", v);
        while (s.endsWith("0")) s = s.substring(0, s.length() - 1);
        if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        return s;
    }

    // ---- 消耗道具 ----
    static final String[] ITEM_NAMES = {"备用油桶", "修理包", "炸药", "紧急传送"};
    static final String[] ITEM_KEYS = {"F", "R", "X", "T"};
    static final int[] ITEM_PRICES = {300, 600, 800, 1500};
    static final String[] ITEM_DESC = {
        "立即补充油箱容量一半的燃油,快没油时救命用。",
        "立即修复车身上限一半的耐久。",
        "炸开周围 3×3 的岩石和泥土(里面的矿也会炸没)。",
        "瞬间传送回地面,货物保留。",
    };
}
