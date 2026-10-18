package lavadigger;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;

/** 离屏自测:模拟按键跑游戏逻辑,并把关键画面存成 PNG */
public final class SelfTest {
    static int fails = 0;
    static File out;

    public static void main(String[] args) throws Exception {
        out = new File(args.length > 0 ? args[0] : "selftest-out");
        out.mkdirs();
        SaveManager.enabled = false;
        Sfx.enabled = false;
        GamePanel gp = new GamePanel();
        shot(gp, "01-title");
        gp.openHelp(GamePanel.State.TITLE);
        for (int i = 0; i < GamePanel.HELP_TITLES.length; i++) {
            gp.helpPage = i;
            shot(gp, "00-help-" + i);
        }
        gp.closeHelp();
        check(gp.state == GamePanel.State.TITLE, "说明页返回标题");

        gp.newGame(42);
        check(gp.state == GamePanel.State.PLAY, "新游戏进入 PLAY");
        run(gp, 0.5);
        check(gp.nearShop() == Shop.FUEL, "开局停在加油站门口");
        drive(gp, Shop.START_DIG_COL * World.T + World.T / 2.0);
        shot(gp, "02-surface");

        // 向下钻 6 秒
        double fuel0 = gp.p.fuel;
        hold(gp, KeyEvent.VK_DOWN, 6);
        double depth = gp.p.depthRows();
        System.out.printf("钻 6 秒后深度 %.2f 格, 油 %.2f -> %.2f, 货舱 %d%n", depth, fuel0, gp.p.fuel, gp.p.cargoCount());
        check(depth >= 4, "6 秒至少钻下 4 格");
        check(gp.p.fuel < fuel0, "钻探耗油");
        shot(gp, "03-digging");

        // 往右横钻 3 秒
        double x0 = gp.p.x;
        double shaftX = gp.p.x + Player.W / 2.0;
        hold(gp, KeyEvent.VK_RIGHT, 3);
        System.out.printf("横钻: x %.1f -> %.1f%n", x0, gp.p.x);
        check(gp.p.x > x0 + 20 || gp.state != GamePanel.State.PLAY, "能横向钻");

        // 开回竖井,再飞回地面
        drive(gp, shaftX);
        hold(gp, KeyEvent.VK_UP, 8);
        System.out.printf("飞行后深度 %.2f 格%n", gp.p.depthRows());
        check(gp.p.depthRows() < 0.2, "能飞回地面");
        // 在空中往左飘,别掉回竖井
        gp.keys[KeyEvent.VK_UP] = true;
        gp.keys[KeyEvent.VK_LEFT] = true;
        run(gp, 1.2);
        gp.keys[KeyEvent.VK_UP] = false;
        gp.keys[KeyEvent.VK_LEFT] = false;
        run(gp, 1.5);
        check(gp.p.onGround && Math.abs(gp.p.depthRows()) < 0.01, "落回地面");

        // 开到收购站
        drive(gp, Shop.COL[Shop.SELL] * World.T + 60);
        check(gp.nearShop() == Shop.SELL, "到达收购站, nearShop=" + gp.nearShop());
        // 停在店门口时,帧间隔有抖动也不能丢失"着地"(否则进店提示会闪)
        java.util.Random jr = new java.util.Random(3);
        int lost = 0;
        for (int i = 0; i < 600; i++) {
            gp.update(0.010 + jr.nextDouble() * 0.012);
            if (gp.nearShop() != Shop.SELL) lost++;
        }
        check(lost == 0, "停在店门口 600 帧进店判定不闪, 丢失 " + lost + " 帧");
        double fSurface = gp.p.fuel;
        run(gp, 20);
        check(gp.p.fuel == fSurface, "停在地面 20 秒不耗油");
        int cargo = gp.p.cargoCount(), money = gp.p.money, val = gp.p.cargoValue();
        gp.openShop(Shop.SELL);
        shot(gp, "04-sell-shop");
        gp.sellAll();
        check(gp.p.money == money + val && gp.p.cargoCount() == 0, "卖矿 " + cargo + " 件得 $" + val);
        gp.closeShop();

        // 改装厂界面
        gp.p.money = 3000;
        gp.openShop(Shop.UPGRADE);
        gp.buyUpgrade(Upgrades.FUEL);
        check(gp.p.level[Upgrades.FUEL] == 1 && gp.p.money == 2500, "升级油箱扣钱");
        gp.menuSel = 1;
        shot(gp, "05-upgrade-shop");
        gp.closeShop();
        gp.openShop(Shop.FUEL);
        shot(gp, "06-fuel-shop");
        gp.buyFuel();
        check(Math.abs(gp.p.fuel - gp.p.maxFuel()) < 1e-6, "加满油");
        gp.closeShop();
        gp.openShop(Shop.ITEMS);
        shot(gp, "07-item-shop");
        gp.closeShop();

        // 深处画面:直接把车放到 150 格深的空洞里
        gp.p.x = 20 * World.T + 3;
        gp.p.y = (World.SURFACE + 150) * World.T + World.T - Player.H;
        gp.p.fallStartY = gp.p.y;
        for (int c = 18; c <= 23; c++) for (int r = World.SURFACE + 148; r <= World.SURFACE + 150; r++) gp.world.set(c, r, World.AIR);
        gp.snapCamera();
        gp.p.fuel = gp.p.maxFuel() * 0.15;
        double fDeep = gp.p.fuel;
        run(gp, 10);
        System.out.printf("地下停 10 秒: 油 %.3f -> %.3f%n", fDeep, gp.p.fuel);
        check(fDeep - gp.p.fuel > 0.05 && fDeep - gp.p.fuel < 0.15, "停在地下 10 秒少量耗油");
        shot(gp, "08-deep-lowfuel");

        // 油耗尽 -> 爆炸 -> 结算
        gp.p.money = 1000;
        gp.p.cargo[3] = 2;
        gp.p.fuel = 0.005;
        run(gp, 1.0);
        check(gp.state == GamePanel.State.EXPLODING, "油耗尽后爆炸, state=" + gp.state);
        run(gp, 0.15);
        shot(gp, "09-explosion");
        run(gp, 2.5);
        check(gp.state == GamePanel.State.REPORT, "爆炸后进入结算");
        check(gp.p.money == 750 && gp.p.cargoCount() == 0, "扣 25% 救援费并丢货, money=" + gp.p.money);
        shot(gp, "10-report");
        gp.respawn();
        check(gp.state == GamePanel.State.PLAY && gp.p.depthRows() < 0.01, "回到地面");

        // 熔岩伤害
        int lc = 20, lr = World.SURFACE + 80;
        gp.p.x = lc * World.T + 3;
        gp.p.y = (lr - 1) * World.T + World.T - Player.H;
        gp.world.set(lc, lr - 1, World.AIR);
        gp.world.set(lc, lr, World.LAVA);
        gp.world.set(lc, lr + 1, World.DIRT);
        gp.p.hull = gp.p.maxHull();
        double h0 = gp.p.hull;
        hold(gp, KeyEvent.VK_DOWN, 0.2);
        run(gp, 3);
        System.out.printf("熔岩: 车身 %.1f -> %.1f%n", h0, gp.p.hull);
        check(gp.p.hull < h0 || gp.state != GamePanel.State.PLAY, "熔岩扣血");

        // 摔落伤害
        gp.respawn();
        gp.p.y = 0;
        gp.p.fallStartY = 0;
        gp.p.x = 25 * World.T;
        double h1 = gp.p.hull;
        run(gp, 2);
        System.out.printf("从天上摔下: 车身 %.1f -> %.1f%n", h1, gp.p.hull);

        // 地面开车不耗油,地面起飞照常耗油
        gp.respawn();
        double f0 = gp.p.fuel;
        hold(gp, KeyEvent.VK_RIGHT, 2);
        hold(gp, KeyEvent.VK_LEFT, 2);
        check(gp.p.fuel == f0, "地面开车不耗油");
        hold(gp, KeyEvent.VK_UP, 1);
        check(gp.p.fuel < f0, "地面起飞耗油");
        run(gp, 3);
        // 油不多时:停在地面不报警,飞起来报警
        gp.p.fuel = gp.p.maxFuel() * 0.12;
        run(gp, 0.5);
        check(gp.fuelAlarm() == 0, "低油但停在地面:不报警");
        gp.keys[KeyEvent.VK_UP] = true;
        run(gp, 0.4);
        check(gp.fuelAlarm() > 0, "低油从地面飞起来:报警");
        gp.keys[KeyEvent.VK_UP] = false;
        run(gp, 3);
        check(gp.fuelAlarm() == 0, "落回地面:警报解除");
        gp.p.fuel = gp.p.maxFuel();

        // 右边两家店的画面
        gp.p.x = 36 * World.T;
        gp.p.y = World.SURFACE * World.T - Player.H;
        gp.snapCamera();
        run(gp, 0.3);
        shot(gp, "11-shops-right");
        gp.p.x = 6 * World.T;
        gp.snapCamera();
        run(gp, 0.3);
        shot(gp, "12-shops-left");

        // 拐角辅助:横向隧道里没对准头顶的一格竖井,按住↑也能滑进去
        for (int offset : new int[]{6, 10, 14, -8}) {
            int sc = 20, tr = World.SURFACE + 30;
            for (int r = tr - 12; r <= tr + 1; r++)
                for (int c = sc - 3; c <= sc + 4; c++) gp.world.set(c, r, World.DIRT);
            for (int r = tr - 10; r <= tr; r++) gp.world.set(sc, r, World.AIR);      // 一格宽竖井
            for (int c = sc - 2; c <= sc + 3; c++) gp.world.set(c, tr, World.AIR);    // 横向隧道
            gp.p.x = sc * World.T + 3 + offset;
            gp.p.y = tr * World.T + World.T - Player.H;
            gp.p.vx = gp.p.vy = 0;
            gp.p.fuel = gp.p.maxFuel();
            gp.p.hull = gp.p.maxHull();
            double d0 = gp.p.depthRows();
            hold(gp, KeyEvent.VK_UP, 2.5);
            double rise = d0 - gp.p.depthRows();
            System.out.printf("拐角辅助 偏移 %dpx: 上升 %.1f 格%n", offset, rise);
            check(rise > 5, "偏移 " + offset + "px 也能飞进竖井");
        }
        // 偏太远(整整一格)不应该被吸过去
        {
            int sc = 20, tr = World.SURFACE + 30;
            for (int r = tr - 12; r <= tr + 1; r++)
                for (int c = sc - 3; c <= sc + 4; c++) gp.world.set(c, r, World.DIRT);
            for (int r = tr - 10; r <= tr; r++) gp.world.set(sc, r, World.AIR);
            for (int c = sc - 2; c <= sc + 3; c++) gp.world.set(c, tr, World.AIR);
            gp.p.x = (sc + 2) * World.T + 3;
            gp.p.y = tr * World.T + World.T - Player.H;
            gp.p.vx = gp.p.vy = 0;
            double xFar = gp.p.x;
            hold(gp, KeyEvent.VK_UP, 1.5);
            check(Math.abs(gp.p.x - xFar) < 0.5, "离竖井太远时不会被自动挪动");
        }

        // ===== 第三轮:刹车 / 摔伤 / 修车价 / 报警 / 救济油 / 货舱 / 熔岩之心 / 结局 =====
        {
            // 深竖井里自由落体到最快,再按↑,看要多远才停住
            int sc = 26, top = World.SURFACE + 40;
            for (int r = top; r < top + 60; r++) gp.world.set(sc, r, World.AIR);
            gp.world.set(sc, top + 60, World.DIRT);
            gp.respawn();
            gp.p.x = sc * World.T + 3;
            gp.p.y = top * World.T;
            gp.p.vy = 0;
            run(gp, 2.5);
            double vmax = gp.p.vy, y0 = gp.p.y;
            gp.keys[KeyEvent.VK_UP] = true;
            for (int i = 0; i < 300 && gp.p.vy > 0; i++) gp.update(1 / 60.0);
            gp.keys[KeyEvent.VK_UP] = false;
            double tiles = (gp.p.y - y0) / World.T;
            System.out.printf("刹车: 下落速度 %.0f 时按↑, %.2f 格停住%n", vmax, tiles);
            check(tiles < 3.2, "最快下落时 3 格内刹住");
            gp.p.vy = 400;
            y0 = gp.p.y;
            gp.keys[KeyEvent.VK_UP] = true;
            for (int i = 0; i < 300 && gp.p.vy > 0; i++) gp.update(1 / 60.0);
            gp.keys[KeyEvent.VK_UP] = false;
            tiles = (gp.p.y - y0) / World.T;
            System.out.printf("刹车: 下落速度 400 时按↑, %.2f 格停住%n", tiles);
            check(tiles < 1.2, "一般下落速度 1 格左右刹住");
        }
        // 摔伤按高度:从不同高度掉进竖井
        {
            int sc = 27, bottom = World.SURFACE + 100;
            double[] expect = {0, 0.25, 1.5, 4, 9};
            int[] hs = {3, 5, 10, 20, 40};
            for (int k = 0; k < hs.length; k++) {
                for (int r = bottom - 50; r < bottom; r++) gp.world.set(sc, r, World.AIR);
                gp.world.set(sc, bottom, World.DIRT);
                gp.respawn();
                gp.p.hull = gp.p.maxHull();
                gp.p.x = sc * World.T + 3;
                gp.p.y = (bottom - hs[k]) * World.T - Player.H;   // 车底离井底 hs 格
                gp.p.fallStartY = gp.p.y;
                gp.p.onGround = false;
                gp.p.vy = 1;
                run(gp, 5);
                double dmg = gp.p.maxHull() - gp.p.hull;
                System.out.printf("掉 %d 格: 扣 %.2f (预期 %.2f)%n", hs[k], dmg, expect[k]);
                check(Math.abs(dmg - expect[k]) < 0.15, "掉 " + hs[k] + " 格扣血符合高度");
            }
            // 掉 40 格,快落地前刹车
            for (int r = bottom - 50; r < bottom; r++) gp.world.set(sc, r, World.AIR);
            gp.respawn();
            gp.p.hull = gp.p.maxHull();
            gp.p.x = sc * World.T + 3;
            gp.p.y = (bottom - 40) * World.T - Player.H;
            gp.p.fallStartY = gp.p.y;
            gp.p.onGround = false;
            gp.p.vy = 1;
            while (gp.p.y < (bottom - 4) * World.T - Player.H) gp.update(1 / 60.0);
            gp.keys[KeyEvent.VK_UP] = true;
            for (int i = 0; i < 120 && gp.p.vy > 0; i++) gp.update(1 / 60.0);
            gp.keys[KeyEvent.VK_UP] = false;
            run(gp, 3);
            double dmg = gp.p.maxHull() - gp.p.hull;
            System.out.printf("掉 40 格但快落地时刹车: 扣 %.2f%n", dmg);
            check(dmg < 0.5, "刹车后基本不摔伤");
        }
        gp.respawn();
        check(gp.p.cargoMax() == 10, "出厂货舱 10 格");
        // 修车价 $5/点
        gp.p.hull = gp.p.maxHull() - 4;
        gp.p.money = 1000;
        gp.openShop(Shop.FUEL);
        gp.repair();
        check(gp.p.money == 980, "修 4 点花 $20, money=" + gp.p.money);
        // 救济油
        gp.p.money = 0;
        gp.p.fuel = 0.5;
        java.util.Arrays.fill(gp.p.cargo, 0);
        check(gp.canGetRelief() && gp.shopItems().get(0).label.contains("救济"), "没钱没货时加油站有救济油");
        shot(gp, "13-relief");
        gp.relief();
        check(Math.abs(gp.p.fuel - gp.p.maxFuel() * 0.4) < 1e-6, "救济油补到 40%");
        check(!gp.canGetRelief(), "领过后不能再领");
        gp.p.money = 100;
        gp.p.fuel = 0.5;
        check(!gp.canGetRelief(), "有钱时不给救济油");
        gp.closeShop();
        // 地下报警:按回程油量 1.2 倍
        {
            gp.p.x = 26 * World.T + 3;
            gp.p.y = (World.SURFACE + 60) * World.T + World.T - Player.H;
            gp.p.onGround = true;
            double ret = gp.returnFuel();
            gp.p.fuel = ret * 1.3;
            int a1 = gp.fuelAlarm();
            gp.p.fuel = ret * 1.15;
            int a2 = gp.fuelAlarm();
            gp.p.fuel = ret * 1.0;
            int a3 = gp.fuelAlarm();
            System.out.printf("回程 %.2f 升: 剩1.3倍→%d, 1.15倍→%d, 1.0倍→%d%n", ret, a1, a2, a3);
            check(a1 == 0 && a2 == 1 && a3 == 2, "报警阈值按回程 1.2 倍");
            gp.p.fuel = gp.p.maxFuel();
        }
        // 熔岩之心:全图正好 6 颗
        {
            World hw = new World();
            boolean all6 = true;
            for (long seed = 1; seed <= 30; seed++) {
                hw.generate(seed);
                int n = 0;
                for (byte t : hw.tiles) if (t == World.ORE + Mineral.HEART) n++;
                if (n != World.HEARTS) { all6 = false; System.out.println("seed " + seed + " 熔岩之心 " + n); }
            }
            check(all6, "30 张随机地图都正好 6 颗熔岩之心");
        }
        // 炸药炸不掉熔岩之心;货舱满时挖不了熔岩之心
        {
            int hc = 26, hr = World.SURFACE + 70;
            gp.respawn();
            gp.world.set(hc, hr, World.AIR);
            gp.world.set(hc + 1, hr, (byte) (World.ORE + Mineral.HEART));
            gp.world.set(hc, hr + 1, World.DIRT);
            gp.p.x = hc * World.T + 3;
            gp.p.y = hr * World.T + World.T - Player.H;
            gp.p.items[2] = 1;
            gp.useItem(2);
            check(gp.world.get(hc + 1, hr) == World.ORE + Mineral.HEART, "炸药炸不掉熔岩之心");
            gp.world.set(hc, hr + 1, World.DIRT);
            gp.p.x = hc * World.T + 3;
            gp.p.y = hr * World.T + World.T - Player.H;
            gp.p.vy = 0;
            gp.p.cargo[0] = gp.p.cargoMax();
            run(gp, 0.3);
            hold(gp, KeyEvent.VK_RIGHT, 1.5);
            check(gp.world.get(hc + 1, hr) == World.ORE + Mineral.HEART && !gp.p.digging, "货舱满时不会把熔岩之心挖丢");
            run(gp, 0.2);
            shot(gp, "14-cargo-full");
            gp.world.set(hc + 1, hr, World.DIRT); // 清掉测试放的那颗
            java.util.Arrays.fill(gp.p.cargo, 0);
            gp.respawn();
        }
        // 结局:卖掉最后一颗熔岩之心
        {
            int goal = gp.heartGoal();
            check(goal == World.HEARTS, "通关目标 = 6 颗, 实际 " + goal);
            // 把地图上的熔岩之心"挖走":前 5 颗算已卖出,最后 1 颗放货舱
            int left = 0;
            for (int i = 0; i < gp.world.tiles.length; i++)
                if (gp.world.tiles[i] == World.ORE + Mineral.HEART) { gp.world.tiles[i] = World.AIR; left++; }
            gp.p.hearts = left - 1;
            gp.p.cargo[Mineral.HEART] = 1;
            check(gp.heartGoal() == World.HEARTS, "挖走后目标不变");
            gp.p.totalEarned = 123456;
            gp.p.playTime = 3725;
            gp.openShop(Shop.SELL);
            gp.sellAll();
            check(gp.state == GamePanel.State.ENDING && gp.p.won, "卖出全部熔岩之心后通关");
            run(gp, 0.5);
            shot(gp, "15-ending");
            gp.state = GamePanel.State.PLAY;
            gp.p.cargo[Mineral.HEART] = 1;
            gp.openShop(Shop.SELL);
            gp.sellAll();
            check(gp.state == GamePanel.State.SHOP, "通关后再卖不会重复弹结局");
            gp.closeShop();
        }

        // ===== 新矿种 =====
        {
            // 老地图补新矿:已有的矿不变,只在泥土里补
            World ow = new World();
            ow.generate(99);
            for (int i = 0; i < ow.tiles.length; i++)
                if (ow.tiles[i] >= World.ORE + Mineral.FIRST_V2) ow.tiles[i] = World.DIRT;
            byte[] before = ow.tiles.clone();
            ow.injectNewMinerals(Mineral.FIRST_V2, 0.6);
            int added = 0;
            boolean oldKept = true;
            for (int i = 0; i < before.length; i++) {
                if (before[i] != ow.tiles[i]) {
                    if (before[i] != World.DIRT || ow.tiles[i] < World.ORE + Mineral.FIRST_V2) oldKept = false;
                    added++;
                }
            }
            System.out.println("老地图补新矿: " + added + " 格");
            check(added > 300 && oldKept, "老地图只在泥土里补新矿,原有的不动");
            // 第 1 版存档读档时自动补
            SaveManager.enabled = true;
            File vdir = new File(out, "v1save");
            SaveManager.dirOverride = vdir;
            World w1 = new World();
            w1.generate(99);
            for (int i = 0; i < w1.tiles.length; i++)
                if (w1.tiles[i] >= World.ORE + Mineral.FIRST_V2) w1.tiles[i] = World.DIRT;
            SaveManager.save(w1, gp.p);
            File sf = SaveManager.file();
            String txt = new String(java.nio.file.Files.readAllBytes(sf.toPath()), "ISO-8859-1").replace("version=3", "version=1");
            java.nio.file.Files.write(sf.toPath(), txt.getBytes("ISO-8859-1"));
            World w1b = new World();
            Player p1b = new Player();
            SaveManager.load(w1b, p1b);
            int nNew = 0;
            for (byte t : w1b.tiles) if (t >= World.ORE + Mineral.FIRST_V2) nNew++;
            check(nNew > 300, "第 1 版存档读档后补上新矿 " + nNew + " 格");
            SaveManager.dirOverride = null;
            SaveManager.enabled = false;

            // 所有矿石放大对照图
            BufferedImage atlas = new BufferedImage(GamePanel.VW, GamePanel.VH, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = atlas.createGraphics();
            g.setColor(new Color(30, 25, 22));
            g.fillRect(0, 0, GamePanel.VW, GamePanel.VH);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(new Font(gp.fontName, Font.BOLD, 14));
            for (int k = 0; k < Mineral.BY_VALUE.length; k++) {
                int i = Mineral.BY_VALUE[k];
                Mineral m = Mineral.ALL[i];
                int ax = 12 + (k % 7) * 135, ay = 10 + (k / 7) * 158;
                for (int v = 0; v < 2; v++)
                    g.drawImage(gp.tex.ore[Textures.band(World.SURFACE + m.peakDepth)][i][v], ax + v * 62, ay, 58, 58, null);
                g.drawImage(gp.tex.ore[Textures.band(World.SURFACE + m.peakDepth)][i][2], ax + 30, ay + 62, 58, 58, null);
                g.setColor(Color.WHITE);
                g.drawString(m.name + " $" + m.value, ax, ay + 140);
            }
            g.dispose();
            ImageIO.write(atlas, "png", new File(out, "16-ore-atlas.png"));

            // 收购站:货舱里有很多种矿
            gp.respawn();
            gp.p.level[Upgrades.CARGO] = 6;
            java.util.Arrays.fill(gp.p.cargo, 0);
            for (int i = 0; i < Mineral.ALL.length; i++) if (i != Mineral.HEART) gp.p.cargo[i] = 1 + i % 4;
            gp.openShop(Shop.SELL);
            shot(gp, "17-sell-many");
            gp.closeShop();
            java.util.Arrays.fill(gp.p.cargo, 0);
            gp.p.level[Upgrades.CARGO] = 0;

            // 地下特效:在车附近摆一圈贵重矿,截几帧
            int bc = 20, br = World.SURFACE + 120;
            for (int r = br - 4; r <= br + 4; r++) for (int c = bc - 8; c <= bc + 8; c++) gp.world.set(c, r, World.DIRT);
            int[] show = {16, 17, 12, 19, 20, 23, 24, 25, 26, 27, 8, 7};
            for (int k = 0; k < show.length; k++) gp.world.set(bc - 6 + k, br - 2, (byte) (World.ORE + show[k]));
            for (int k = 0; k < show.length; k++) gp.world.set(bc - 6 + k, br + 2, (byte) (World.ORE + show[(k + 5) % show.length]));
            for (int c = bc - 6; c <= bc + 6; c++) { gp.world.set(c, br, World.AIR); gp.world.set(c, br + 1, World.DIRT); }
            gp.p.x = bc * World.T + 3;
            gp.p.y = br * World.T + World.T - Player.H;
            gp.p.fallStartY = gp.p.y;
            gp.snapCamera();
            for (int f = 0; f < 3; f++) {
                gp.time += 0.37;
                shot(gp, "18-ore-fx-" + f);
            }
            // 挖到矿的粒子
            gp.world.set(bc + 1, br, (byte) (World.ORE + 15));
            int before2 = gp.particles.size();
            hold(gp, KeyEvent.VK_RIGHT, 4);
            check(gp.p.cargo[15] == 1 && gp.particles.size() > before2, "挖到矿有粒子特效");
            java.util.Arrays.fill(gp.p.cargo, 0);
            gp.respawn();
        }

        // 存读档往返
        SaveManager.enabled = true;
        String home = System.getProperty("user.home");
        System.setProperty("user.home", new File(out, "home").getAbsolutePath());
        // 默认位置:程序所在文件夹/存档/熔岩矿车存档.properties
        File def = SaveManager.file();
        System.out.println("默认存档位置: " + def.getAbsolutePath());
        check(def.getName().equals("lava.properties") && def.getParentFile().getName().equals("save")
            && def.getParentFile().getParentFile().equals(SaveManager.gameDir()), "存档默认放在游戏文件夹/save/");
        File saveDir = new File(out, "save");
        SaveManager.dirOverride = saveDir;
        File nf = SaveManager.file();
        nf.delete();
        gp.p.money = 777;
        SaveManager.save(gp.world, gp.p);
        // 老位置的存档会被搬过来
        File legacy = SaveManager.legacyFile();
        legacy.getParentFile().mkdirs();
        java.nio.file.Files.copy(nf.toPath(), legacy.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        nf.delete();
        SaveManager.migrateLegacy();
        check(nf.isFile() && !legacy.exists() && !legacy.getParentFile().exists(), "旧存档自动搬到新位置并清理旧文件夹");
        World wm = new World();
        Player pm = new Player();
        check(SaveManager.load(wm, pm) && pm.money == 777, "搬过来的存档能读");
        // 上一版中文位置(游戏文件夹/存档/熔岩矿车存档.properties)也会被搬过来
        File cn = new File(new File(saveDir.getParentFile(), "存档"), "熔岩矿车存档.properties");
        cn.getParentFile().mkdirs();
        java.nio.file.Files.copy(nf.toPath(), cn.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        nf.delete();
        SaveManager.migrateLegacy();
        check(nf.isFile() && !cn.exists() && !cn.getParentFile().exists(), "中文存档文件夹自动搬到 save/ 并删除");
        // 上一版英文长文件名 save/lava-digger-save.properties 自动改名
        File en = new File(saveDir, "lava-digger-save.properties");
        java.nio.file.Files.copy(nf.toPath(), en.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        nf.delete();
        SaveManager.migrateLegacy();
        check(nf.isFile() && !en.exists() && nf.getName().equals("lava.properties"), "旧文件名自动改成 lava.properties");
        gp.p.money = 12345;
        gp.p.level[Upgrades.DRILL] = 3;
        SaveManager.save(gp.world, gp.p);
        World w2 = new World();
        Player p2 = new Player();
        check(SaveManager.load(w2, p2), "读档成功");
        check(p2.fallStartY == p2.y, "读档后下落起点重置为当前位置");
        check(p2.money == 12345 && p2.level[Upgrades.DRILL] == 3 && java.util.Arrays.equals(w2.tiles, gp.world.tiles), "存档内容一致");
        System.setProperty("user.home", home);
        SaveManager.dirOverride = null;

        // 地图统计
        int[] count = new int[Mineral.ALL.length];
        int rock = 0, lava = 0, gas = 0;
        World w = new World();
        w.generate(7);
        for (byte t : w.tiles) {
            if (t >= World.ORE) count[t - World.ORE]++;
            if (t == World.ROCK) rock++;
            if (t == World.LAVA) lava++;
            if (t == World.GAS) gas++;
        }
        StringBuilder sb = new StringBuilder("地图统计:");
        for (int i = 0; i < count.length; i++) sb.append(Mineral.ALL[i].name).append('=').append(count[i]).append(' ');
        sb.append("岩石=").append(rock).append(" 熔岩=").append(lava).append(" 瓦斯=").append(gas);
        System.out.println(sb);
        for (int i = 0; i < count.length; i++) check(count[i] > 0, Mineral.ALL[i].name + " 存在");

        System.out.println(fails == 0 ? "全部通过" : ("失败 " + fails + " 项"));
        System.exit(fails == 0 ? 0 : 1);
    }

    static void check(boolean ok, String what) {
        System.out.println((ok ? "  [OK]   " : "  [FAIL] ") + what);
        if (!ok) fails++;
    }

    static void run(GamePanel gp, double sec) {
        for (double t = 0; t < sec; t += 1 / 60.0) gp.update(1 / 60.0);
    }

    static void hold(GamePanel gp, int key, double sec) {
        gp.keys[key] = true;
        run(gp, sec);
        gp.keys[key] = false;
    }

    static void drive(GamePanel gp, double targetCenterX) {
        for (int i = 0; i < 60 * 30; i++) {
            double cx = gp.p.x + Player.W / 2.0;
            if (Math.abs(cx - targetCenterX) < 6) break;
            int k = cx < targetCenterX ? KeyEvent.VK_RIGHT : KeyEvent.VK_LEFT;
            gp.keys[KeyEvent.VK_RIGHT] = gp.keys[KeyEvent.VK_LEFT] = false;
            gp.keys[k] = true;
            gp.update(1 / 60.0);
        }
        gp.keys[KeyEvent.VK_RIGHT] = gp.keys[KeyEvent.VK_LEFT] = false;
        run(gp, 1);
    }

    static void shot(GamePanel gp, String name) throws Exception {
        BufferedImage img = new BufferedImage(GamePanel.VW, GamePanel.VH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        gp.render(g);
        g.dispose();
        ImageIO.write(img, "png", new File(out, name + ".png"));
    }
}
