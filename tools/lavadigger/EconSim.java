package lavadigger;

/**
 * 经济模拟:用真实地图、矿价、油耗、地热和升级价格,脚本化地"玩"一整局,估算通关和全部升满要多久。
 * 策略:沿一条主竖井下去,在当时油箱能舒服到达的深度横着挖,装满或油到警戒线就飞回来卖;
 * 攒够钱就买最便宜的下一档升级。时间按动作逐项累加,再乘一个"人手操作"系数。
 * 这是估算模型,不跑真实物理。用法:java lavadigger.EconSim [种子...]
 */
public final class EconSim {
    static final double T = World.T;
    static final double HUMAN = 1.4;      // 人手操作比脚本慢、会绕路
    static final double SHOP_TIME = 30;   // 每趟在地面买卖、开车的时间(秒)

    final GamePanel gp;
    final World w;
    final Player p;
    final int shaft = Shop.START_DIG_COL;
    int shaftDepth;         // 主竖井已经挖到第几格(地表下)
    double time, fuel, hull;
    int trips, deaths, lastCargo, dS, dH, dF, dR;
    double winHours = -1, maxHours = -1;
    int[] winLevels = new int[0];
    final StringBuilder log = new StringBuilder();
    final java.util.Random rnd = new java.util.Random(7);
    static final int SEE_COLS = 15, SEE_ROWS = 10; // 屏幕上大约能看到的范围(格)

    EconSim(long seed) {
        gp = new GamePanel();
        gp.newGame(seed);
        w = gp.world;
        p = gp.p;
    }

    double heat(int d) { return gp.heat(d); }

    double digTime(int c, int r) {
        return GamePanel.DIG_BASE * w.hardness(c, r) / p.drillMul();
    }

    double returnFuel(int d) {
        double y = p.y;
        p.y = (World.SURFACE + d) * T - Player.H;
        double f = gp.returnFuel();
        p.y = y;
        return f;
    }

    boolean canDig(byte t) {
        return World.diggable(t) || (t == World.ROCK && p.level[Upgrades.DRILL] >= GamePanel.ROCK_DRILL_LEVEL);
    }

    double gasDmg(int d) { return GamePanel.gasDamage(d); }

    static final int DYNAMITE = Upgrades.ITEM_PRICES[2];

    int curCol; // 挑目标时车所在的列(给探测器判断距离用)

    /** 探测器范围内的瓦斯看得见 */
    boolean seesGas(int c) {
        return p.probeRadius() > 0 && Math.abs(c - curCol) <= p.probeRadius() + 1;
    }

    boolean rockOnPath(int row, int c0, int c1) {
        int dir = c1 < c0 ? -1 : 1;
        for (int c = c0 + dir; c != c1; c += dir) if (w.get(c, World.SURFACE + row) == World.ROCK && !canDig(World.ROCK)) return true;
        return false;
    }

    double lavaDmg(int d) { return (6 + d / 20.0) * (1 - p.radiator()); }

    /** 挖掉一格:耗时耗油,结算矿、瓦斯、熔岩;返回 false 表示车炸了 */
    boolean dig(int c, int r) {
        byte t = w.get(c, r);
        int d = r - World.SURFACE;
        double dt = digTime(c, r);
        time += dt;
        fuel -= dt * (GamePanel.FUEL_DRILL + GamePanel.FUEL_IDLE) * heat(d);
        w.set(c, r, World.AIR);
        int m = World.mineralOf(t);
        if (m >= 0 && p.cargoCount() < p.cargoMax()) p.cargo[m]++;
        if (t == World.GAS) hull -= gasDmg(d);
        if (t == World.LAVA) hull -= lavaDmg(d);
        return hull > 0 && fuel > 0;
    }

    void drive(int tiles, int d) {
        double dt = tiles * T / (140 * p.engineMul()) * 1.2;
        time += dt;
        fuel -= dt * (GamePanel.FUEL_DRIVE + GamePanel.FUEL_IDLE) * heat(d);
    }

    /** 去深度 d 的"固定开销":把竖井挖到 d + 从 d 飞回地面 */
    double planFuel(int d) {
        double f = 0;
        for (int r = shaftDepth + 1; r <= d; r++)
            f += digTime(shaft, World.SURFACE + r) * (GamePanel.FUEL_DRILL + GamePanel.FUEL_IDLE) * heat(r);
        return f + returnFuel(d);
    }

    /** 选这一趟的目标深度:往返和挖竖井最多用 45% 的油(剩下的用来挖矿),车身至少扛得住一次瓦斯 */
    int chooseDepth() {
        int best = 2, bottom = World.ROWS - 7 - World.SURFACE;
        for (int d = 2; d <= bottom; d++) {
            if (planFuel(d) * 1.15 > p.maxFuel() * 0.45) break;
            if (d >= 15 && p.maxHull() < gasDmg(d) * 1.1) break;
            best = d;
        }
        return best;
    }

    void explode() {
        deaths++;
        // 和游戏一样:熔岩之心和宝藏回到地底
        for (int m = 0; m < p.cargo.length; m++)
            if (Mineral.isProtected(m)) for (int k = 0; k < p.cargo[m]; k++) w.placeProtected(m, rnd);
        java.util.Arrays.fill(p.cargo, 0);
        p.money -= (int) (p.money * GamePanel.DEATH_FEE);
    }

    void trip() {
        trips++;
        time += SHOP_TIME;
        // 加油修车
        double needF = p.maxFuel() - fuel, needH = p.maxHull() - hull;
        p.money -= (int) Math.ceil(needF * GamePanel.FUEL_PRICE + needH * GamePanel.REPAIR_PRICE);
        if (p.money < 0) p.money = 0;
        fuel = p.maxFuel();
        hull = p.maxHull();
        int d = chooseDepth();
        // 上一趟收成差(附近挖空了):这趟随机换一个浅一点的深度
        if (trips > 1 && lastCargo < p.cargoMax() / 3) d = Math.max(2, d / 2 + rnd.nextInt(d / 2 + 1));
        // 顺着竖井掉下去(按住刹车,落点不摔)
        time += shaftDepth * T / 500 + 0.5;
        fuel -= (shaftDepth * T / 500) * GamePanel.FUEL_IDLE * heat(Math.min(shaftDepth, d)) + 0.4 * GamePanel.FUEL_THRUST;
        // 加深竖井;遇到钻不动的岩石就绕一下
        while (shaftDepth < d) {
            int r = World.SURFACE + shaftDepth + 1;
            if (!canDig(w.get(shaft, r))) {
                w.set(shaft, r, World.AIR); // 绕路:按多挖两格算
                time += 2 * digTime(shaft, r - 1);
                fuel -= 2 * digTime(shaft, r - 1) * GamePanel.FUEL_DRILL * heat(shaftDepth);
            } else if (!dig(shaft, r)) { dS++; explode(); return; }
            shaftDepth++;
        }
        // 横着挖:每次挑"价值 / 要挖的格数"最高的一块矿,从竖井横着挖过去(沿路的矿也收);
        // 装满、没有可挖的,或者油到警戒线就回去
        int cur = shaft, curRow = d, explores = 0;
        while (p.cargoCount() < p.cargoMax()) {
            int bRow = -1, bCol = -1;
            curCol = cur;
            double bScore = 0;
            // 只看得到车附近的矿(屏幕范围);熔岩之心在最底层发光,到了那一带就看得到
            for (int row = Math.max(2, curRow - SEE_ROWS); row <= Math.min(d, curRow + SEE_ROWS); row++) {
                int r = World.SURFACE + row;
                for (int dir = -1; dir <= 1; dir += 2) {
                    double cost = 0, lava = 0;
                    for (int c = shaft + dir; c >= 0 && c < World.COLS; c += dir) {
                        byte t = w.get(c, r);
                        if (t == World.AIR) { cost += 0.15; continue; }
                        if (t == World.GAS && seesGas(c)) {
                            if (hull < gasDmg(row) * 2.2) break;
                            cost += 3; // 车身够厚,为了值钱的东西硬挖过去
                        }
                        if (t == World.LAVA) {
                            lava += lavaDmg(row);
                            if (lava > hull - p.maxHull() * 0.3) break;
                        } else if (!canDig(t)) {
                            if (t != World.ROCK || p.money < DYNAMITE) break;
                            cost += 4; // 炸药:贵,只为值钱的东西用
                        }
                        cost += 1;
                        int m = World.mineralOf(t);
                        if (m < 0 || Math.abs(c - cur) > SEE_COLS) continue;
                        double sc = Mineral.ALL[m].value / (cost + Math.abs(row - curRow) * 0.05);
                        if (cost > 4 && Mineral.ALL[m].value < 3000 && rockOnPath(row, shaft, c)) continue;
                        if (sc > bScore) { bScore = sc; bRow = row; bCol = c; }
                    }
                }
            }
            if (bRow < 0) {
                // 附近看不到矿:往没挖过的一边开一条探路隧道(真人会这么做)
                if (explores >= 4) break;
                explores++;
                int row = Math.max(2, d - rnd.nextInt(Math.min(d - 1, 25)));
                int r0 = World.SURFACE + row, dir = rnd.nextBoolean() ? -1 : 1, end = shaft;
                while (end + dir >= 0 && end + dir < World.COLS && (canDig(w.get(end + dir, r0)) || w.get(end + dir, r0) == World.AIR)
                    && w.get(end + dir, r0) != World.LAVA) end += dir;
                if (end == shaft) continue;
                bRow = row;
                bCol = end;
            }
            // 回到竖井,飞/掉到目标那一行
            drive(Math.abs(cur - shaft), curRow);
            double fly = Math.abs(bRow - curRow) * T / (130 * p.engineMul());
            time += fly;
            if (bRow < curRow) fuel -= fly * (GamePanel.FUEL_THRUST + GamePanel.FUEL_IDLE) * heat(curRow);
            cur = shaft;
            curRow = bRow;
            int dir = bCol < shaft ? -1 : 1, r = World.SURFACE + bRow;
            boolean stop = false;
            while (cur != bCol) {
                int nc = cur + dir;
                double reserve = returnFuel(bRow) * 1.15 + (Math.abs(nc - shaft) + 1) * T / (140 * p.engineMul()) * GamePanel.FUEL_DRIVE * heat(bRow) + 0.4;
                if (fuel < reserve + 0.3) { stop = true; break; }
                byte nt = w.get(nc, r);
                if (hull < gasDmg(bRow) * 1.05) { stop = true; break; }
                curCol = cur;
                if (nt == World.GAS && seesGas(nc) && hull < gasDmg(bRow) * 2.2) break;
                if (nt == World.AIR) drive(1, bRow);
                else if (!canDig(nt) && nt != World.LAVA) {
                    if (nt != World.ROCK || p.money < DYNAMITE) break;
                    p.money -= DYNAMITE; // 买炸药炸开
                    w.set(nc, r, World.AIR);
                    time += 3;
                } else if (!dig(nc, r)) { if (hull <= 0) dH++; else dF++; explode(); return; }
                cur = nc;
                if (p.cargoCount() >= p.cargoMax()) break;
            }
            if (stop) break;
        }
        drive(Math.abs(cur - shaft), curRow); // 开回竖井
        int curDepth = curRow;
        // 飞回地面
        time += curDepth * T / (130 * p.engineMul());
        fuel -= returnFuel(curDepth);
        if (fuel <= 0) { dR++; explode(); return; }
        // 卖矿
        int v = p.cargoValue();
        lastCargo = p.cargoCount();
        p.hearts += p.cargo[Mineral.HEART];
        p.money += v;
        java.util.Arrays.fill(p.cargo, 0);
        // 里程碑奖金
        for (int i = 0; i < GamePanel.MILESTONE_M.length; i++)
            if (curDepth * GamePanel.METERS_PER_ROW >= GamePanel.MILESTONE_M[i] && (p.milestones & (1 << i)) == 0) {
                p.milestones |= 1 << i;
                p.money += GamePanel.MILESTONE_BONUS[i];
            }
        // 攒够就买最便宜的下一档(留一点加油钱)
        while (true) {
            int best = -1, price = Integer.MAX_VALUE;
            for (int k = 0; k < Upgrades.KINDS; k++) {
                if (p.level[k] >= Upgrades.TIERS - 1) continue;
                int pr = Upgrades.PRICES[k][p.level[k] + 1];
                if (pr < price) { price = pr; best = k; }
            }
            if (best < 0 || p.money - price < p.maxFuel() * 2.5) break;
            p.money -= price;
            p.level[best]++;
            log.append(String.format("  %5.2f 小时 第%3d 趟  %s 升到 Lv%d  (这趟挖到 %d 米)%n", time * HUMAN / 3600, trips,
                Upgrades.NAMES[best], p.level[best] + 1, (int) (d * GamePanel.METERS_PER_ROW)));
        }
    }

    boolean allMax() {
        for (int k = 0; k < Upgrades.KINDS; k++) if (p.level[k] < Upgrades.TIERS - 1) return false;
        return true;
    }

    void run() {
        fuel = p.maxFuel();
        hull = p.maxHull();
        while (trips < 3000 && (winHours < 0 || maxHours < 0)) {
            trip();
            double h = time * HUMAN / 3600;
            if (winHours < 0 && p.hearts >= World.HEARTS) {
                winHours = h;
                winLevels = p.level.clone();
            }
            if (maxHours < 0 && allMax()) maxHours = h;
        }
    }

    public static void main(String[] args) {
        SaveManager.enabled = false;
        Sfx.enabled = false;
        double scale = Double.parseDouble(System.getProperty("priceScale", "1"));
        double top = Double.parseDouble(System.getProperty("topScale", String.valueOf(scale)));
        for (int[] row : Upgrades.PRICES) for (int i = 0; i < row.length; i++) row[i] = (int) Math.round(row[i] * (i == Upgrades.TIERS - 1 ? top : scale));
        long[] seeds = args.length > 0 ? new long[args.length] : new long[]{1, 2, 3, 42, 99};
        for (int i = 0; i < args.length; i++) seeds[i] = Long.parseLong(args[i]);
        for (long seed : seeds) {
            EconSim s = new EconSim(seed);
            s.run();
            if (seeds.length == 1) System.out.print(s.log);
            System.out.printf("种子 %d: 通关 %.1f 小时, 全部升满 %.1f 小时, 共 %d 趟, 爆炸 %d 次, 拿到心 %d%n",
                seed, s.winHours, s.maxHours, s.trips, s.deaths, s.p.hearts);
            System.out.println("    通关时各部件档位(1~7): " + levels(s.winLevels));
            System.out.printf("    爆炸原因: 挖竖井 %d, 横挖时车身 %d, 横挖时没油 %d, 回程没油 %d%n", s.dS, s.dH, s.dF, s.dR);
        }
    }

    static String levels(int[] lv) {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < lv.length; k++) sb.append(Upgrades.NAMES[k]).append(lv[k] + 1).append(' ');
        return sb.toString();
    }
}
