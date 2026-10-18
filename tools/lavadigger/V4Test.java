package lavadigger;

import java.awt.event.KeyEvent;
import java.io.File;

import static lavadigger.SelfTest.check;
import static lavadigger.SelfTest.run;
import static lavadigger.SelfTest.shot;

/** 第 4 版新增功能的自测(在 SelfTest 之后单独运行) */
public final class V4Test {
    static final double T = World.T;

    public static void main(String[] args) throws Exception {
        SelfTest.out = new File(args.length > 0 ? args[0] : "selftest-out");
        SelfTest.out.mkdirs();
        SaveManager.enabled = false;
        Sfx.enabled = false;
        GamePanel gp = new GamePanel();
        gp.newGame(42);

        task1(gp);
        task3(gp);

        System.out.println(SelfTest.fails == 0 ? "V4 全部通过" : ("V4 失败 " + SelfTest.fails + " 项"));
        System.exit(SelfTest.fails == 0 ? 0 : 1);
    }

    static void setAll(Player p, int tier) {
        for (int k = 0; k < Upgrades.KINDS; k++) p.level[k] = tier;
    }

    /** 估算:在 rows 深处挖 tiles 格(每格难度 hard),再按 returnFuel 飞回地面,共用多少油 */
    static double tripFuel(GamePanel gp, int rows, int tiles, double hard) {
        Player p = gp.p;
        p.y = (World.SURFACE + rows) * T - Player.H;
        double perTile = GamePanel.DIG_BASE * hard * (1 + rows / 250.0) / p.drillMul()
            * (GamePanel.FUEL_DRILL + GamePanel.FUEL_IDLE) * gp.heat(rows);
        double f = tiles * perTile + gp.returnFuel();
        p.toSpawn();
        return f;
    }

    static void task1(GamePanel gp) throws Exception {
        Player p = gp.p;
        // 地热系数
        setAll(p, 0);
        check(Math.abs(gp.heat(50) - 1) < 1e-9, "50 格以内没有地热");
        check(Math.abs(gp.heat(10) - 1) < 1e-9, "浅层没有地热");
        check(Math.abs(gp.heat(409) - 3.5) < 0.02, "出厂散热器最底层地热 ×3.5,实际 " + gp.heat(409));
        p.level[Upgrades.RADIATOR] = 6;
        check(Math.abs(gp.heat(409) - 2.23) < 0.02, "满级散热器最底层地热 ×2.23,实际 " + gp.heat(409));
        check(gp.tempC(1) == 20, "地表 20℃");

        // 油箱
        double[] tanks = {10, 15, 25, 40, 58, 78, 100};
        boolean tankOk = true;
        for (int t = 0; t < 7; t++) tankOk &= Upgrades.VALUES[Upgrades.FUEL][t] == tanks[t];
        check(tankOk, "油箱各档 10/15/25/40/58/78/100");
        check(Upgrades.KINDS == 7 && Upgrades.PROBE == 6, "第 7 种部件:探测器");
        check(Upgrades.PRICES[Upgrades.FUEL][6] == 305000 && Upgrades.PRICES[Upgrades.PROBE][1] == 300, "价格表照抄设计");

        // 回程估算按地热累加:300 格深处比不算地热时要多
        setAll(p, 0);
        p.y = (World.SURFACE + 300) * T - Player.H;
        double plain = 300 * T / (130 * p.engineMul()) * (GamePanel.FUEL_THRUST + GamePanel.FUEL_IDLE);
        check(gp.returnFuel() > plain * 1.5, "回程估算计入地热");
        p.toSpawn();

        // 燃油够用:满级 / 第 6 档 在最底层挖满一舱回来;第 5 档能拿熔岩之心回来
        setAll(p, 6);
        double f7 = tripFuel(gp, 400, p.cargoMax() * 2, 1.3) / p.maxFuel();
        setAll(p, 5);
        double f6 = tripFuel(gp, 400, p.cargoMax() * 2, 1.3) / p.maxFuel();
        setAll(p, 4);
        double f5 = tripFuel(gp, 400, 12, 1.6) / p.maxFuel();
        System.out.printf("  底层用油:满级 %.0f%%  第6档 %.0f%%  第5档拿心 %.0f%%%n", f7 * 100, f6 * 100, f5 * 100);
        check(f7 <= 0.45, "满级在底层挖满一舱回来用油 ≤45%");
        check(f6 <= 0.60, "第 6 档在底层挖满一舱回来用油 ≤60%");
        check(f5 <= 0.75, "第 5 档下到底拿熔岩之心回来至少剩 25% 油");

        // 地下油耗乘地热:在 300 格深处怠速 10 秒
        setAll(p, 0);
        gp.respawn();
        int c = 20, r = World.SURFACE + 300;
        for (int rr = r - 2; rr <= r; rr++) gp.world.set(c, rr, World.AIR);
        gp.world.set(c, r + 1, World.ROCK);
        p.x = c * T + 3;
        p.y = r * T + T - Player.H;
        p.fallStartY = p.y;
        p.fuel = 5;
        run(gp, 10);
        double used = 5 - p.fuel;
        check(Math.abs(used - 10 * GamePanel.FUEL_IDLE * gp.heat(300)) < 0.01, "地下怠速油耗乘地热,10 秒用 " + used);
        shot(gp, "v4-01-hud-heat");

        // 道具按上限 50% 补
        gp.respawn();
        p.fuel = 0.5;
        p.items[0] = 1;
        gp.useItem(0);
        check(Math.abs(p.fuel - 5.5) < 1e-6, "出厂油箱用油桶补 5 升,实际 " + p.fuel);
        p.hull = 1;
        p.items[1] = 1;
        gp.useItem(1);
        check(Math.abs(p.hull - 6) < 1e-6, "出厂车身用修理包补 5 点,实际 " + p.hull);

        // 改装厂有探测器
        gp.openShop(Shop.UPGRADE);
        boolean hasProbe = false;
        for (GamePanel.MenuItem it : gp.shopItems()) hasProbe |= it.label.startsWith("探测器");
        check(hasProbe, "改装厂能买探测器");
        shot(gp, "v4-02-upgrade-shop");
        gp.closeShop();
        p.items[0] = p.items[1] = 0;
    }

    static int countTile(World w, int m) {
        int n = 0;
        for (byte t : w.tiles) if (t == World.ORE + m) n++;
        return n;
    }

    static void task3(GamePanel gp) throws Exception {
        String[] names = {"古钱罐", "恐龙骨架", "远古神像", "外星残骸"};
        int[] values = {800, 5000, 12000, 25000}, counts = {4, 3, 2, 2};
        int[][] meters = {{50, 150}, {300, 600}, {600, 900}, {900, 1150}};
        World w = new World();
        w.generate(7);
        for (int k = 0; k < 4; k++) {
            int m = Mineral.FIRST_V4 + k;
            Mineral mi = Mineral.ALL[m];
            check(mi.name.equals(names[k]) && mi.value == values[k] && Mineral.isTreasure(m), "宝藏定义:" + names[k]);
            check(countTile(w, m) == counts[k], names[k] + " 全图 " + counts[k] + " 个,实际 " + countTile(w, m));
            boolean inBand = true;
            for (int i = 0; i < w.tiles.length; i++) {
                if (w.tiles[i] != World.ORE + m) continue;
                int d = i / World.COLS - World.SURFACE;
                if (d * 3 < meters[k][0] || d * 3 > meters[k][1]) inBand = false;
            }
            check(inBand, names[k] + " 在 " + meters[k][0] + "~" + meters[k][1] + " 米");
        }
        check(Mineral.ALL.length == Mineral.FIRST_V4 + 4, "宝藏排在矿物表末尾");
        java.util.Random rnd = new java.util.Random(1);
        boolean never = true;
        for (int i = 0; i < 40000; i++) {
            int m = Mineral.pick(rnd.nextInt(410), rnd);
            if (m >= 0 && (Mineral.isTreasure(m) || m == Mineral.HEART)) never = false;
        }
        check(never, "随机生成从不产出宝藏");

        // 挖到宝藏:进货舱 + 屏幕中间的说明
        gp.respawn();
        Player p = gp.p;
        int c = 20, r = World.SURFACE + 40;
        for (int rr = r - 1; rr <= r; rr++) { gp.world.set(c, rr, World.AIR); gp.world.set(c + 1, rr, World.DIRT); }
        gp.world.set(c, r + 1, World.ROCK);
        gp.world.set(c + 1, r, (byte) (World.ORE + Mineral.FIRST_V4 + 1));
        p.x = c * T + 3;
        p.y = r * T + T - Player.H;
        p.fallStartY = p.y;
        p.fuel = p.maxFuel();
        SelfTest.hold(gp, KeyEvent.VK_RIGHT, 2.5);
        check(p.cargo[Mineral.FIRST_V4 + 1] == 1, "挖到恐龙骨架进货舱");
        check(gp.centerMsgTimer > 0 && gp.centerMsg.contains("恐龙骨架"), "挖到宝藏有居中说明");
        shot(gp, "v4-03-treasure-found");
        java.util.Arrays.fill(p.cargo, 0);
        gp.respawn();

        // 宝藏放大图
        java.awt.image.BufferedImage atlas = new java.awt.image.BufferedImage(640, 220, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = atlas.createGraphics();
        for (int k = 0; k < 4; k++) {
            int m = Mineral.FIRST_V4 + k;
            g.drawImage(gp.tex.ore[Textures.band(World.SURFACE + Mineral.ALL[m].peakDepth)][m][0], 20 + k * 155, 20, 128, 128, null);
            g.setColor(java.awt.Color.WHITE);
            g.setFont(new java.awt.Font(gp.fontName, java.awt.Font.BOLD, 16));
            g.drawString(Mineral.ALL[m].name, 20 + k * 155, 180);
        }
        g.dispose();
        javax.imageio.ImageIO.write(atlas, "png", new File(SelfTest.out, "v4-04-treasure-atlas.png"));
    }
}
