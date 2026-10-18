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

        // 存读档往返
        SaveManager.enabled = true;
        String home = System.getProperty("user.home");
        System.setProperty("user.home", new File(out, "home").getAbsolutePath());
        gp.p.money = 12345;
        gp.p.level[Upgrades.DRILL] = 3;
        SaveManager.save(gp.world, gp.p);
        World w2 = new World();
        Player p2 = new Player();
        check(SaveManager.load(w2, p2), "读档成功");
        check(p2.money == 12345 && p2.level[Upgrades.DRILL] == 3 && java.util.Arrays.equals(w2.tiles, gp.world.tiles), "存档内容一致");
        System.setProperty("user.home", home);

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
