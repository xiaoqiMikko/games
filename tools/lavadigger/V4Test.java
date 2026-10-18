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
        task2(gp);
        task4(gp);
        task5(gp);
        task6(gp);
        task7(gp);
        reviewFixes(gp);
        minimapEdge(gp);
        alarmMargin(gp);
        textOverMinimap(gp);

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
        check(Upgrades.PRICES[Upgrades.FUEL][6] == 410000 && Upgrades.PRICES[Upgrades.PROBE][1] == 550, "价格表按经济模拟校准后的值");

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

    /** 把地图里第一个 m 挖出来(变成泥土),模拟它进了货舱 */
    static void takeOne(GamePanel gp, int m) {
        for (int i = 0; i < gp.world.tiles.length; i++)
            if (gp.world.tiles[i] == World.ORE + m) {
                gp.world.tiles[i] = World.DIRT;
                gp.p.cargo[m]++;
                return;
            }
    }

    static void task2(GamePanel gp) throws Exception {
        gp.newGame(42);
        Player p = gp.p;
        int alien = Mineral.FIRST_V4 + 3;
        takeOne(gp, Mineral.HEART);
        takeOne(gp, alien);
        check(countTile(gp.world, Mineral.HEART) == 5 && countTile(gp.world, alien) == 1, "准备:心和外星残骸各一个在货舱");
        gp.explode("测试");
        run(gp, 3);
        check(gp.state == GamePanel.State.REPORT, "爆炸后进入结算");
        check(countTile(gp.world, Mineral.HEART) == 6, "爆炸丢失的熔岩之心回到地图,共 " + countTile(gp.world, Mineral.HEART));
        check(countTile(gp.world, alien) == 2, "爆炸丢失的宝藏回到地图");
        boolean said = false;
        for (String line : gp.report) said |= line.contains("回到了地底深处");
        check(said, "结算报告说明宝物回到地底");
        check(gp.heartGoal() == World.HEARTS, "通关目标恒为 6 颗");
        shot(gp, "v4-05-report-heart");
        gp.respawn();

        // 炸药炸不掉宝藏
        int c = 20, r = World.SURFACE + 60;
        gp.world.set(c + 1, r, (byte) (World.ORE + alien));
        gp.world.set(c - 1, r, (byte) (World.ORE + Mineral.FIRST_V4));
        gp.world.set(c, r, World.AIR);
        p.x = c * T + 3;
        p.y = r * T + T - Player.H;
        p.fallStartY = p.y;
        p.items[2] = 1;
        gp.useItem(2);
        check(gp.world.get(c + 1, r) == World.ORE + alien && gp.world.get(c - 1, r) == World.ORE + Mineral.FIRST_V4, "炸药炸不掉宝藏");
        gp.respawn();
    }

    /** 在 depth 格深处挖一个小房间并把车放进去,返回车所在的列和行 */
    static int[] room(GamePanel gp, int depth) {
        gp.respawn();
        int c = 20, r = World.SURFACE + depth;
        for (int rr = r - 4; rr <= r + 4; rr++)
            for (int cc = c - 6; cc <= c + 6; cc++) gp.world.set(cc, rr, World.DIRT);
        gp.world.set(c, r, World.AIR);
        gp.world.set(c, r - 1, World.AIR);
        gp.p.x = c * T + 3;
        gp.p.y = r * T + T - Player.H;
        gp.p.fallStartY = gp.p.y;
        gp.p.fuel = gp.p.maxFuel();
        gp.snapCamera();
        return new int[]{c, r};
    }

    static void task4(GamePanel gp) throws Exception {
        Player p = gp.p;
        // 钻头 Lv5(下标 4)钻不动岩石
        setAll(p, 0);
        p.level[Upgrades.DRILL] = 4;
        int[] cr = room(gp, 80);
        gp.world.set(cr[0], cr[1] + 1, World.ROCK);
        gp.texts.clear();
        gp.tryDigDown();
        check(!p.digging, "铂金钻头钻不动岩石");
        boolean hint = false;
        for (GamePanel.FloatText t : gp.texts) hint |= t.s.contains("钻石级");
        check(hint, "提示升级到钻石级钻头可以钻岩石");
        // Lv6(下标 5)能钻
        p.level[Upgrades.DRILL] = 5;
        gp.rockMsgCooldown = 0;
        gp.tryDigDown();
        double expect = GamePanel.DIG_BASE * 3.0 * (1 + 81 / 250.0) / p.drillMul();
        check(p.digging && Math.abs(p.digTime - expect) < 1e-6, "钻石级钻头能钻岩石,耗时 " + p.digTime);
        p.digging = false;

        // 探测器
        setAll(p, 0);
        cr = room(gp, 120);
        gp.world.set(cr[0] + 2, cr[1], World.GAS);
        gp.world.set(cr[0] - 2, cr[1] + 1, (byte) (World.ORE + 4));
        check(gp.probeTargets() == 0, "没有探测器时什么都探不到");
        p.level[Upgrades.PROBE] = 1;
        check(gp.probeTargets() >= 2, "3 格探测器能探到旁边的瓦斯和金矿");
        gp.world.set(cr[0] + 6, cr[1], World.GAS);
        int near = gp.probeTargets();
        p.level[Upgrades.PROBE] = 6;
        check(gp.probeTargets() > near, "满级探测器看得更远");
        gp.snapCamera();
        shot(gp, "v4-06-probe");
        gp.respawn();
    }

    static void task5(GamePanel gp) throws Exception {
        Player p = gp.p;
        setAll(p, 3);
        // 车停在一条竖井底部(井上方 8 格是空的)
        int[] cr = room(gp, 200);
        for (int rr = cr[1] - 8; rr <= cr[1]; rr++) gp.world.set(cr[0], rr, World.AIR);
        gp.world.set(cr[0], cr[1] + 1, World.ROCK);
        byte[] before = gp.world.tiles.clone();
        double hull0 = p.hull;
        gp.triggerQuake();
        check(gp.rocks.size() >= 1, "地震时竖井顶上掉下碎石");
        check(gp.rocks.get(0).warn > 0, "碎石落下前先掉灰尘预警");
        run(gp, 0.6);
        shot(gp, "v4-07-quake-warn");
        run(gp, 3);
        check(p.hull < hull0, "碎石砸中车会扣车身");
        double dmg = hull0 - p.hull;
        check(Math.abs(dmg - (2 + 201 / 80.0)) < 0.2, "同一次地震最多扣一次 2+深度/80,实际 " + dmg);
        // 50 次地震后地图不变(碎石落地就碎,不会堵路)
        for (int i = 0; i < 50; i++) {
            p.hull = p.maxHull();
            gp.triggerQuake();
            run(gp, 2.5);
        }
        check(java.util.Arrays.equals(before, gp.world.tiles), "落石从不改变地图");
        check(gp.rocks.isEmpty(), "碎石全部碎掉");

        // 200 米深处 5 分钟都不会地震
        cr = room(gp, 66);
        gp.world.set(cr[0], cr[1] + 1, World.ROCK);
        p.fuel = p.maxFuel();
        int q0 = gp.quakeCount;
        for (int i = 0; i < 30; i++) { p.fuel = p.maxFuel(); run(gp, 10); }
        check(gp.quakeCount == q0, "300 米以上不会地震");
        // 300 米以下 5 分钟内会地震
        cr = room(gp, 120);
        gp.world.set(cr[0], cr[1] + 1, World.ROCK);
        for (int i = 0; i < 30; i++) { p.fuel = p.maxFuel(); p.hull = p.maxHull(); run(gp, 10); }
        check(gp.quakeCount > q0, "300 米以下会地震,5 分钟内 " + (gp.quakeCount - q0) + " 次");
        gp.respawn();
        check(gp.rocks.isEmpty(), "重生时清掉碎石");
    }

    static void press(GamePanel gp, int key) {
        gp.keyPressed(new KeyEvent(gp, KeyEvent.KEY_PRESSED, 0, 0, key, KeyEvent.CHAR_UNDEFINED));
        gp.keyReleased(new KeyEvent(gp, KeyEvent.KEY_RELEASED, 0, 0, key, KeyEvent.CHAR_UNDEFINED));
    }

    static void task6(GamePanel gp) throws Exception {
        Player p = gp.p;
        setAll(p, 0);
        // 里程碑
        p.money = 0;
        p.milestones = 0;
        room(gp, 34);
        gp.world.set(20, World.SURFACE + 35, World.ROCK);
        run(gp, 0.3);
        check(p.money == 300 && (p.milestones & 1) != 0, "第一次到 100 米奖励 $300,现金 " + p.money);
        check(gp.radioTimer > 0 && gp.radioMsg.length() > 0, "弹出公司无线电");
        shot(gp, "v4-08-radio");
        run(gp, 0.3);
        room(gp, 34);
        run(gp, 0.3);
        check(p.money == 300, "里程碑只奖励一次");

        // 新手指引
        p.tips = 0;
        gp.tipTimer = 0;
        gp.tip(GamePanel.TIP_FULL);
        check(gp.tipTimer > 0 && (p.tips & (1 << GamePanel.TIP_FULL)) != 0, "第一次满舱弹出指引");
        shot(gp, "v4-09-tip");
        gp.tipTimer = 0;
        gp.tip(GamePanel.TIP_FULL);
        check(gp.tipTimer <= 0, "同一条指引只弹一次");
        room(gp, 60);
        gp.world.set(20, World.SURFACE + 61, World.ROCK);
        run(gp, 0.3);
        check((p.tips & (1 << GamePanel.TIP_HEAT)) != 0, "进入地热区弹出指引");

        // 失去焦点:游戏中自动暂停,商店里不变
        gp.onFocusLost();
        check(gp.state == GamePanel.State.PAUSE, "游戏中切出窗口自动暂停");
        gp.state = GamePanel.State.PLAY;
        gp.respawn();
        gp.openShop(Shop.FUEL);
        gp.onFocusLost();
        check(gp.state == GamePanel.State.SHOP, "商店里切出窗口不打断");
        gp.closeShop();

        // M 静音、F11 全屏
        boolean m0 = Sfx.muted;
        press(gp, KeyEvent.VK_M);
        check(Sfx.muted != m0, "M 键切换静音");
        press(gp, KeyEvent.VK_M);
        final int[] fs = {0};
        gp.fullscreenToggler = () -> fs[0]++;
        press(gp, KeyEvent.VK_F11);
        check(fs[0] == 1, "F11 切换全屏");

        // 满舱时贵重矿闪得更勤
        java.util.Arrays.fill(p.cargo, 0);
        Mineral gold = Mineral.ALL[4], dia = Mineral.ALL[7];
        int g1 = gp.twinkleRate(gold), d1 = gp.twinkleRate(dia);
        p.cargo[0] = p.cargoMax();
        check(gp.twinkleRate(gold) == g1 * 2 && gp.twinkleRate(dia) == d1 * 2, "满舱时闪光频率加倍");
        java.util.Arrays.fill(p.cargo, 0);

        // 小地图:只记录玩家挖开的格子
        World fresh = new World();
        fresh.generate(5);
        boolean none = true;
        for (int r = 0; r < World.ROWS; r++) for (int c = 0; c < World.COLS; c++) none &= !fresh.isDug(c, r);
        check(none, "新地图没有已挖格子(天然空洞不算)");
        gp.respawn();
        drive(gp, Shop.START_DIG_COL * T + T / 2.0);
        SelfTest.hold(gp, KeyEvent.VK_DOWN, 3);
        check(gp.world.isDug(Shop.START_DIG_COL, World.SURFACE + 1), "挖过的格子记入小地图");
        shot(gp, "v4-10-minimap");
        gp.respawn();

        // 说明书新页
        boolean page = false;
        for (String t : GamePanel.HELP_TITLES) page |= t.contains("宝藏");
        check(page, "说明书有宝藏一页");
        gp.openHelp(GamePanel.State.PLAY);
        for (int i = 0; i < GamePanel.HELP_TITLES.length; i++) {
            gp.helpPage = i;
            shot(gp, "v4-help-" + i);
        }
        gp.closeHelp();
    }

    static void drive(GamePanel gp, double x) {
        SelfTest.drive(gp, x);
    }

    static void task7(GamePanel gp) throws Exception {
        File dir = new File(SelfTest.out, "v3save");
        dir.mkdirs();
        File[] old = dir.listFiles();
        if (old != null) for (File f : old) f.delete(); // 上次运行留下的存档
        SaveManager.enabled = true;
        SaveManager.dirOverride = dir;
        try {
            // 造一个第 3 版存档:地图里只剩 4 颗心(卖了 1 颗,炸丢 1 颗),没有宝藏,部件只有 6 个数
            World w = new World();
            w.generate(99);
            int removed = 0;
            for (int i = 0; i < w.tiles.length; i++) {
                int m = World.mineralOf(w.tiles[i]);
                if (m == Mineral.HEART && removed < 2) { w.tiles[i] = World.DIRT; removed++; }
                else if (m >= 0 && Mineral.isTreasure(m)) w.tiles[i] = World.DIRT;
            }
            Player p = new Player();
            p.reset();
            p.level[Upgrades.FUEL] = 6;
            p.fuel = 100;
            p.money = 777;
            p.hearts = 1;
            p.maxDepth = 120;
            p.totalEarned = 5000;
            SaveManager.save(w, p);
            File f = SaveManager.file();
            java.util.List<String> out = new java.util.ArrayList<>();
            for (String line : java.nio.file.Files.readAllLines(f.toPath(), java.nio.charset.StandardCharsets.ISO_8859_1)) {
                if (line.startsWith("milestones=") || line.startsWith("tips=") || line.startsWith("muted=")) continue;
                if (line.startsWith("version=")) line = "version=3";
                if (line.startsWith("level=")) line = "level=6,0,0,0,0,0";
                if (line.startsWith("fuel=")) line = "fuel=130.0";
                out.add(line);
            }
            java.nio.file.Files.write(f.toPath(), out, java.nio.charset.StandardCharsets.ISO_8859_1);

            World w2 = new World();
            Player p2 = new Player();
            p2.level[Upgrades.PROBE] = 3; // 读档前残留的值不能带进来
            check(SaveManager.load(w2, p2), "第 3 版存档能读");
            check(p2.level[Upgrades.PROBE] == 0, "老存档探测器为 0 档");
            check(countTile(w2, Mineral.HEART) + p2.hearts == World.HEARTS, "老存档补回丢失的熔岩之心,地图 " + countTile(w2, Mineral.HEART));
            boolean all = true;
            for (int k = 0; k < Mineral.TREASURES; k++) all &= countTile(w2, Mineral.FIRST_V4 + k) == Mineral.TREASURE_COUNT[k];
            check(all, "老存档补齐全部宝藏");
            check((p2.milestones & 0b11) == 0b11 && (p2.milestones & 0b100) == 0 && p2.money == 777,
                "老存档按最深纪录(360 米)标记已领的里程碑,不补发奖金");
            int want = (1 << GamePanel.TIP_DIG) | (1 << GamePanel.TIP_SELL) | (1 << GamePanel.TIP_HEAT) | (1 << GamePanel.TIP_QUAKE);
            check((p2.tips & want) == want, "老存档按进度标记已看过的指引");
            check(p2.fuel <= p2.maxFuel() + 1e-9 && p2.maxFuel() == 100, "老存档油量截到新油箱上限");

            // 第 4 版存档往返
            p2.milestones = 0b10101;
            p2.tips = 0b1010;
            SaveManager.save(w2, p2);
            World w3 = new World();
            Player p3 = new Player();
            check(SaveManager.load(w3, p3), "第 4 版存档能读");
            check(p3.milestones == 0b10101 && p3.tips == 0b1010 && java.util.Arrays.equals(w2.tiles, w3.tiles), "第 4 版存档往返一致");
            check(countTile(w3, Mineral.HEART) == countTile(w2, Mineral.HEART), "第 4 版读档不会重复补心");
        } finally {
            SaveManager.dirOverride = null;
            SaveManager.enabled = false;
        }
    }

    static void reviewFixes(GamePanel gp) throws Exception {
        Player p = gp.p;
        // 满舱时钻宝藏:不能开钻,宝藏留在原地
        setAll(p, 0);
        int[] cr = room(gp, 40);
        int alien = Mineral.FIRST_V4 + 3;
        gp.world.set(cr[0], cr[1] + 1, (byte) (World.ORE + alien));
        p.cargo[0] = p.cargoMax();
        gp.rockMsgCooldown = 0;
        gp.tryDigDown();
        run(gp, 2);
        check(!p.digging && gp.world.get(cr[0], cr[1] + 1) == World.ORE + alien, "满舱时挖不了宝藏,宝藏留在原地");
        java.util.Arrays.fill(p.cargo, 0);
        gp.respawn();

        // 熔岩之心的深度带几乎全被挖空,放回地底也不能失败
        World w = new World();
        w.generate(3);
        for (int r = World.ROWS - 18; r <= World.ROWS - 7; r++)
            for (int c = 0; c < World.COLS; c++)
                if (!(w.get(c, r) >= World.ORE && Mineral.isProtected(w.get(c, r) - World.ORE))) w.set(c, r, World.AIR);
        int before = countTile(w, Mineral.HEART);
        boolean ok = true;
        for (int i = 0; i < 3; i++) ok &= w.placeProtected(Mineral.HEART, new java.util.Random(i));
        check(ok && countTile(w, Mineral.HEART) == before + 3, "深度带被挖空时熔岩之心仍能放回地底");
    }

    /** 车在地图最右边一列时,不能被右侧小地图挡住 */
    static void minimapEdge(GamePanel gp) throws Exception {
        int[] cr = room(gp, 60);
        int c = World.COLS - 1;
        gp.world.set(c, cr[1], World.AIR);
        gp.p.x = c * T + (T - Player.W) / 2.0;
        gp.snapCamera();
        double right = gp.p.x + Player.W - gp.camX;
        check(right <= GamePanel.MINI_X - 6, "最右一列的车在小地图左边,没被挡住(车右边缘 " + (int) right + ")");
        double tileRight = World.COLS * T - gp.camX;
        check(tileRight <= GamePanel.MINI_X - 3, "地图最右一列完整露在小地图左边");
        shot(gp, "v4-11-right-edge");
        gp.respawn();
    }

    /**
     * 燃油报警的余量:报警一响,玩家再挖 2 块矿、开回竖井、再往上飞,也必须能回到地面。
     * 竖井在 c,矿在 c+1、c+2(同一行),车从竖井往右挖。
     */
    static boolean alarmThenTwoMore(GamePanel gp, int tier, int depth) {
        Player p = gp.p;
        gp.respawn();
        setAll(p, tier);
        int c = 10, r = World.SURFACE + depth;
        for (int rr = World.SURFACE - 3; rr <= r; rr++) gp.world.set(c, rr, World.AIR);
        for (int cc = c + 1; cc <= c + 4; cc++) { gp.world.set(cc, r, (byte) (World.ORE + 6)); gp.world.set(cc, r - 1, World.DIRT); gp.world.set(cc, r + 1, World.ROCK); }
        gp.world.set(c, r + 1, World.ROCK);
        gp.world.set(c - 1, r, World.BEDROCK); // 开回竖井时挡住,别往左钻进土里
        p.x = c * T + 3;
        p.y = r * T + T - Player.H;
        p.fallStartY = p.y;
        gp.snapCamera();
        // 油量从高往下降,直到刚好响起第一级报警
        p.fuel = p.maxFuel();
        for (double f = p.maxFuel(); f > 0; f -= 0.01) {
            p.fuel = f;
            if (gp.fuelAlarm() >= 1) break;
        }
        // 再挖两块
        int got = 0;
        for (int i = 0; i < 2; i++) {
            int before = p.cargoCount();
            SelfTest.hold(gp, KeyEvent.VK_RIGHT, 0.2);
            for (int t = 0; t < 600 && p.digging; t++) gp.update(1 / 60.0);
            got += p.cargoCount() - before;
            if (gp.state != GamePanel.State.PLAY) return false;
        }
        // 开回竖井,往上飞
        SelfTest.hold(gp, KeyEvent.VK_LEFT, 1.5);
        gp.keys[KeyEvent.VK_UP] = true;
        for (int t = 0; t < 60 * 300 && p.depthRows() > -0.2 && gp.state == GamePanel.State.PLAY; t++) gp.update(1 / 60.0);
        gp.keys[KeyEvent.VK_UP] = false;
        boolean ok = gp.state == GamePanel.State.PLAY && got == 2;
        if (!ok) System.out.printf("    第%d档 %d格深:挖到 %d 块,状态 %s,剩油 %.2f%n", tier + 1, depth, got, gp.state, p.fuel);
        for (int rr = World.SURFACE - 3; rr <= r; rr++) gp.world.set(c, rr, World.DIRT);
        if (gp.state != GamePanel.State.PLAY) { run(gp, 3); gp.respawn(); }
        java.util.Arrays.fill(p.cargo, 0);
        return ok;
    }

    static void alarmMargin(GamePanel gp) throws Exception {
        boolean all = true;
        int[][] cases = {{0, 40}, {0, 60}, {1, 90}, {2, 150}, {3, 220}, {4, 300}, {5, 380}, {6, 400}};
        for (int[] k : cases) all &= alarmThenTwoMore(gp, k[0], k[1]);
        check(all, "报警响起后再挖 2 块矿、开回竖井,仍能飞回地面");

        // 地温只看深度,不随散热器变化
        Player p = gp.p;
        setAll(p, 0);
        int t0 = gp.groundTempC(300);
        p.level[Upgrades.RADIATOR] = 6;
        check(gp.groundTempC(300) == t0 && t0 > 100, "地温不随散热器变化,300 格深 " + t0 + "℃");
        check(gp.heat(300) < 1 + GamePanel.HEAT_GAIN * 250 / 359.0, "散热器仍然减轻地热油耗");
        gp.respawn();
    }

    /** 飘字(挖到矿加的钱等)要画在小地图前面 */
    static void textOverMinimap(GamePanel gp) throws Exception {
        int[] cr = room(gp, 60);
        gp.p.x = (World.COLS - 1) * T + 3;
        gp.snapCamera();
        gp.texts.clear();
        gp.addText(gp.camX + GamePanel.MINI_X + 12, gp.camY + 300, "测试测试", new java.awt.Color(255, 0, 255));
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(GamePanel.VW, GamePanel.VH, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        gp.render(g);
        g.dispose();
        int hits = 0;
        for (int y = GamePanel.MINI_Y; y < GamePanel.MINI_Y + GamePanel.MINI_H; y++)
            for (int x = GamePanel.MINI_X; x < GamePanel.MINI_X + GamePanel.MINI_W; x++) {
                int rgb = img.getRGB(x, y), rr = (rgb >> 16) & 255, gg = (rgb >> 8) & 255, bb = rgb & 255;
                if (rr > 200 && bb > 200 && gg < 90) hits++;
            }
        check(hits > 5, "飘字画在小地图前面(小地图区域里看得到飘字 " + hits + " 个像素)");
        gp.texts.clear();
        gp.respawn();
    }
}
