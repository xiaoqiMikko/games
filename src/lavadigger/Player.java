package lavadigger;

/** 钻地车的状态、资产和升级 */
final class Player {
    static final int W = 26, H = 24;

    double x, y, vx, vy;
    double fallStartY;  // 摔伤计算:等效的自由落体起点
    boolean onGround, touchL, touchR, thrusting;
    int facing = 1;
    double wheelAngle;

    double fuel, hull;
    int money;
    int[] cargo = new int[Mineral.ALL.length];
    int[] level = new int[Upgrades.KINDS];
    int[] items = new int[Upgrades.ITEM_NAMES.length];
    int maxDepth;      // 最深纪录(格)
    int hearts;        // 卖出过的熔岩之心
    int deaths;        // 爆炸次数
    long totalEarned;  // 累计卖矿收入
    double playTime;   // 游戏用时(秒)
    boolean won;       // 已通关
    int milestones;    // 已领取的深度里程碑(位)
    int tips;          // 已弹过的新手指引(位)

    // 钻探中
    boolean digging;
    int digCol, digRow, digDir; // digDir: 0 向下, -1 向左, 1 向右
    double digProgress, digTime, digSX, digSY, digTX, digTY;

    void reset() {
        money = 0;
        java.util.Arrays.fill(cargo, 0);
        java.util.Arrays.fill(level, 0);
        java.util.Arrays.fill(items, 0);
        maxDepth = 0;
        hearts = 0;
        deaths = 0;
        totalEarned = 0;
        playTime = 0;
        won = false;
        milestones = 0;
        tips = 0;
        fuel = maxFuel();
        hull = maxHull();
        toSpawn();
    }

    void toSpawn() {
        x = Shop.SPAWN_COL * World.T + (World.T - W) / 2.0;
        y = World.SURFACE * World.T - H;
        vx = vy = 0;
        digging = false;
        onGround = true;
        fallStartY = y;
    }

    double val(int kind) { return Upgrades.VALUES[kind][level[kind]]; }
    double maxFuel() { return val(Upgrades.FUEL); }
    double drillMul() { return val(Upgrades.DRILL); }
    double engineMul() { return val(Upgrades.ENGINE); }
    int cargoMax() { return (int) val(Upgrades.CARGO); }
    double maxHull() { return val(Upgrades.HULL); }
    double radiator() { return val(Upgrades.RADIATOR); }
    int probeRadius() { return (int) val(Upgrades.PROBE); }

    int cargoCount() {
        int n = 0;
        for (int c : cargo) n += c;
        return n;
    }

    int cargoValue() {
        int v = 0;
        for (int i = 0; i < cargo.length; i++) v += cargo[i] * Mineral.ALL[i].value;
        return v;
    }

    /** 车底所在深度(格,地表为 0) */
    double depthRows() {
        return (y + H - World.SURFACE * World.T) / (double) World.T;
    }
}
