package lavadigger;

/** 六种可升级部件,每种 7 档(第 0 档是出厂配置) */
final class Upgrades {
    static final int FUEL = 0, DRILL = 1, ENGINE = 2, CARGO = 3, HULL = 4, RADIATOR = 5;
    static final int KINDS = 6, TIERS = 7;

    static final String[] NAMES = {"油箱", "钻头", "引擎", "货舱", "车身", "散热器"};
    static final String[] DESC = {
        "能装更多燃油,跑得更远、挖得更深。",
        "挖穿一格更快,也就更省油。",
        "左右行驶和向上飞行都更快。",
        "一趟能装更多矿石。",
        "更耐摔、更耐炸。升级时会同时补上新增的耐久。",
        "减少熔岩造成的灼烧伤害。",
    };
    static final String[] TIER_NAMES = {"出厂", "青铜", "白银", "黄金", "铂金", "钻石", "传说"};

    static final double[][] VALUES = {
        {10, 15, 25, 40, 60, 90, 130},
        {1.0, 1.35, 1.8, 2.4, 3.2, 4.3, 6.0},
        {1.0, 1.15, 1.3, 1.5, 1.75, 2.0, 2.4},
        {6, 12, 20, 32, 50, 75, 110},
        {10, 18, 30, 48, 75, 110, 160},
        {0, 0.15, 0.30, 0.45, 0.60, 0.72, 0.85},
    };

    static final int[][] PRICES = {
        {0, 500, 1500, 4000, 10000, 25000, 60000},
        {0, 600, 1800, 4500, 11000, 27000, 65000},
        {0, 500, 1500, 4000, 10000, 24000, 55000},
        {0, 450, 1300, 3500, 9000, 22000, 50000},
        {0, 550, 1600, 4200, 10500, 26000, 60000},
        {0, 700, 2000, 5000, 12000, 28000, 62000},
    };

    static String fmt(int kind, int tier) {
        double v = VALUES[kind][tier];
        switch (kind) {
            case FUEL: return (int) v + " 升";
            case DRILL: return "速度×" + trim(v);
            case ENGINE: return "动力×" + trim(v);
            case CARGO: return (int) v + " 格";
            case HULL: return (int) v + " 点";
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
        "立即补充 20 升燃油,快没油时救命用。",
        "立即修复 30 点车身。",
        "炸开周围 3×3 的岩石和泥土(里面的矿也会炸没)。",
        "瞬间传送回地面,货物保留。",
    };
}
