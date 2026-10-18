package lavadigger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/** 游戏主体:状态机、物理、渲染、输入 */
final class GamePanel extends JPanel implements KeyListener, ActionListener {
    static final int VW = 960, VH = 640;
    static final int T = World.T;

    // 物理与经济参数
    static final double GRAVITY = 700;
    static final double FUEL_IDLE = 0.0085, FUEL_DRIVE = 0.06, FUEL_THRUST = 0.14, FUEL_DRILL = 0.13; // 升/秒(怠速:出厂油箱约 20 分钟耗光)
    static final double FUEL_PRICE = 2.0, REPAIR_PRICE = 12.0;
    static final double DIG_BASE = 0.75;     // 出厂钻头挖一格泥土的秒数
    static final double DEATH_FEE = 0.25;    // 爆炸后的救援费:现金的 25%
    static final double FALL_SAFE = 500;     // 落地速度超过这个才扣血
    static final double LOW_FUEL = 0.2;
    static final double RETURN_MARGIN = 1.5, RETURN_EXTRA = 0.5; // 剩油 < 回程油×1.5+0.5 升 就报警
    static final double METERS_PER_ROW = 3;

    enum State { TITLE, PLAY, SHOP, PAUSE, EXPLODING, REPORT, HELP }

    // 游戏说明页
    int helpPage;
    State helpReturn = State.TITLE;

    State state = State.TITLE;
    final World world = new World();
    final Player p = new Player();
    final Textures tex = new Textures();
    final Sfx sfx = new Sfx();
    final boolean[] keys = new boolean[1024];
    final List<Particle> particles = new ArrayList<>();
    final List<FloatText> texts = new ArrayList<>();
    final Random fx = new Random();
    final BufferedImage back = new BufferedImage(VW, VH, BufferedImage.TYPE_INT_RGB);

    double camX, camY, shake, time;
    int menuSel, shopType = -1;
    String shopMsg = "";
    Color shopMsgColor = Color.WHITE;
    double shopMsgTimer, rockMsgCooldown, beepTimer, explodeTimer;
    boolean confirmNew, hasSave;
    String deathReason = "";
    final List<String> report = new ArrayList<>();
    long lastNanos;
    Timer timer;

    final String fontName;

    GamePanel() {
        setPreferredSize(new Dimension(VW, VH));
        setBackground(Color.BLACK);
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);
        addKeyListener(this);
        String name = Font.DIALOG;
        for (String f : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
            if (f.equals("Microsoft YaHei") || f.equals("微软雅黑")) {
                name = f;
                break;
            }
        }
        fontName = name;
        hasSave = SaveManager.exists();
        if (!(hasSave && SaveManager.load(world, p))) {
            hasSave = false;
            world.generate(System.nanoTime());
            p.reset();
        }
        snapCamera();
    }

    void start() {
        lastNanos = System.nanoTime();
        timer = new Timer(15, this);
        timer.start();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        long now = System.nanoTime();
        double dt = Math.min(0.05, (now - lastNanos) / 1e9);
        lastNanos = now;
        update(dt);
        repaint();
    }

    // =================================================================== 更新

    void update(double dt) {
        time += dt;
        if (shopMsgTimer > 0) shopMsgTimer -= dt;
        switch (state) {
            case PLAY: {
                double left = dt;
                while (left > 1e-9) {
                    double step = Math.min(left, 1 / 120.0);
                    updatePlay(step);
                    left -= step;
                    if (state != State.PLAY) break;
                }
                updateEffects(dt);
                updateCamera(dt);
                break;
            }
            case EXPLODING:
                explodeTimer += dt;
                updateEffects(dt);
                updateCamera(dt);
                if (explodeTimer > 2.2) buildReport();
                break;
            default:
                updateEffects(dt);
        }
    }

    boolean key(int... codes) {
        for (int c : codes) if (c < keys.length && keys[c]) return true;
        return false;
    }

    void updatePlay(double dt) {
        boolean left = key(KeyEvent.VK_LEFT, KeyEvent.VK_A);
        boolean right = key(KeyEvent.VK_RIGHT, KeyEvent.VK_D);
        boolean up = key(KeyEvent.VK_UP, KeyEvent.VK_W);
        boolean down = key(KeyEvent.VK_DOWN, KeyEvent.VK_S);
        if (rockMsgCooldown > 0) rockMsgCooldown -= dt;

        if (p.digging) {
            p.thrusting = false;
            p.digProgress += dt / p.digTime;
            double k = Math.min(1, p.digProgress);
            p.x = p.digSX + (p.digTX - p.digSX) * k;
            p.y = p.digSY + (p.digTY - p.digSY) * k;
            p.fuel -= FUEL_DRILL * dt;
            if (fx.nextDouble() < dt * 40) spawnDigChip();
            if (p.digProgress >= 1) finishDig();
        } else {
            double eng = p.engineMul();
            double maxV = 140 * eng, acc = 650 * eng;
            if (left ^ right) {
                int dir = left ? -1 : 1;
                p.facing = dir;
                p.vx += dir * acc * dt;
                if (p.depthRows() > 0.5) p.fuel -= FUEL_DRIVE * dt; // 地面上开车不耗油,飞行照常耗油
            } else {
                double fr = (p.onGround ? 900 : 260) * dt;
                p.vx = Math.abs(p.vx) <= fr ? 0 : p.vx - Math.signum(p.vx) * fr;
            }
            p.vx = Math.max(-maxV, Math.min(maxV, p.vx));
            p.thrusting = up;
            if (up) {
                p.vy -= (GRAVITY + 430 * eng) * dt;
                p.fuel -= FUEL_THRUST * dt;
                if (fx.nextDouble() < dt * 30) spawnSmoke(p.x + Player.W / 2.0, p.y + Player.H, true);
            }
            p.vy += GRAVITY * dt;
            p.vy = Math.max(-130 * eng, Math.min(750, p.vy));

            moveX(dt);
            if (up) cornerAssist(dt, left ^ right ? (left ? -1 : 1) : 0);
            double impact = moveY(dt);
            p.wheelAngle += p.vx * dt / 4;
            if (impact > FALL_SAFE) damage((impact - FALL_SAFE) / 50.0, "摔伤");

            if (state == State.PLAY && p.onGround && !up) {
                if (down && !left && !right) tryDigDown();
                else if (left && !right && p.touchL) tryDigSide(-1);
                else if (right && !left && p.touchR) tryDigSide(1);
            }
        }

        // 怠速耗油只在地下发生:地面上停着不耗油
        if (p.depthRows() > 0.5) p.fuel -= FUEL_IDLE * dt;
        int depth = (int) Math.floor(p.depthRows());
        if (depth > p.maxDepth) p.maxDepth = depth;
        if (p.hull < p.maxHull() * 0.3 && fx.nextDouble() < dt * 6) spawnSmoke(p.x + 6, p.y, false);

        int alarm = fuelAlarm();
        if (state == State.PLAY && alarm > 0) {
            beepTimer -= dt;
            if (beepTimer <= 0) {
                sfx.beep();
                beepTimer = alarm == 2 ? 0.45 : 0.9;
            }
        }
        if (p.fuel <= 0 && state == State.PLAY) {
            p.fuel = 0;
            explode("燃油耗尽");
        }
    }

    static final double ASSIST_RANGE = 14;  // 最多帮忙挪多少像素
    static final double ASSIST_SPEED = 90;  // 挪动速度(像素/秒),慢一点才不显眼

    /**
     * 拐角辅助:往上飞时车顶快撞到边缘,但旁边不远就有能钻上去的开口,
     * 就把车慢慢推向开口,免得一格宽的竖井必须对得很准才能飞进去。
     * 玩家按着反方向时不推。
     */
    void cornerAssist(double dt, int inputDir) {
        int rA = (int) Math.floor(p.y / T) - 1;           // 车顶上方即将进入的那一行
        if (p.y - (rA + 1) * T > 10) return;              // 离天花板还远
        int c0 = (int) Math.floor(p.x / T), c1 = (int) Math.floor((p.x + Player.W - 0.001) / T);
        if (!blocked2(rA, c0, c1)) return;                // 上面是通的,不用帮
        int r0 = (int) Math.floor(p.y / T), r1 = (int) Math.floor((p.y + Player.H - 0.001) / T);
        double best = Double.MAX_VALUE;
        for (int c = c0 - 1; c <= c1 + 1; c++) {
            if (world.solid(c, rA)) continue;
            double lo = c * T, hi = c * T + T - Player.W;  // 车整个落在这一列里的 x 范围
            double dx = p.x < lo ? lo - p.x : p.x > hi ? hi - p.x : 0;
            if (dx == 0 || Math.abs(dx) > ASSIST_RANGE || Math.abs(dx) >= Math.abs(best)) continue;
            // 挪过去的路上不能被墙挡住
            double nx = p.x + dx;
            int n0 = (int) Math.floor(nx / T), n1 = (int) Math.floor((nx + Player.W - 0.001) / T);
            boolean clear = true;
            for (int cc = n0; cc <= n1 && clear; cc++) if (blocked(cc, r0, r1)) clear = false;
            if (clear) best = dx;
        }
        if (best == Double.MAX_VALUE) return;
        if (inputDir != 0 && Math.signum(best) != inputDir) return;
        p.x += Math.signum(best) * Math.min(Math.abs(best), ASSIST_SPEED * dt);
        if (inputDir == 0) p.vx = 0;
    }

    /** 估算从当前深度直线飞回地面需要的油(升) */
    double returnFuel() {
        double rows = Math.max(0, p.depthRows());
        double upSpeed = 130 * p.engineMul();
        return rows * T / upSpeed * (FUEL_THRUST + FUEL_IDLE);
    }

    /** 0 = 正常, 1 = 燃油不足(该往回走了), 2 = 危险(只剩勉强够回去的油) */
    int fuelAlarm() {
        double ff = p.fuel / p.maxFuel();
        boolean under = p.depthRows() > 0.5;
        double ret = returnFuel();
        if (ff < 0.1 || (under && p.fuel < ret * 1.1 + 0.2)) return 2;
        if (ff < LOW_FUEL || (under && p.fuel < ret * RETURN_MARGIN + RETURN_EXTRA)) return 1;
        return 0;
    }

    boolean blocked(int c, int r0, int r1) {
        for (int r = r0; r <= r1; r++) if (world.solid(c, r)) return true;
        return false;
    }

    void moveX(double dt) {
        p.touchL = p.touchR = false;
        double nx = p.x + p.vx * dt;
        int r0 = (int) Math.floor(p.y / T), r1 = (int) Math.floor((p.y + Player.H - 0.001) / T);
        if (p.vx > 0) {
            int c = (int) Math.floor((nx + Player.W - 0.001) / T);
            if (blocked(c, r0, r1)) {
                nx = c * T - Player.W;
                p.vx = 0;
                p.touchR = true;
            }
        } else if (p.vx < 0) {
            int c = (int) Math.floor(nx / T);
            if (blocked(c, r0, r1)) {
                nx = (c + 1) * T;
                p.vx = 0;
                p.touchL = true;
            }
        }
        p.x = nx;
    }

    /** 纵向移动,返回落地瞬间的速度(没落地返回 0) */
    double moveY(double dt) {
        double impact = 0;
        p.onGround = false;
        double ny = p.y + p.vy * dt;
        int c0 = (int) Math.floor(p.x / T), c1 = (int) Math.floor((p.x + Player.W - 0.001) / T);
        if (p.vy > 0) {
            int r = (int) Math.floor((ny + Player.H - 0.001) / T);
            if (blocked2(r, c0, c1)) {
                ny = r * T - Player.H;
                impact = p.vy;
                p.vy = 0;
                p.onGround = true;
            }
        } else if (p.vy < 0) {
            int r = (int) Math.floor(ny / T);
            if (blocked2(r, c0, c1)) {
                ny = (r + 1) * T;
                p.vy = 0;
            }
        }
        if (ny < 0) {
            ny = 0;
            p.vy = Math.max(0, p.vy);
        }
        // 贴地检测:很短的子步长里车只下沉极小的距离,会落在碰撞容差里被误判为悬空,
        // 导致"着地"状态一帧有一帧没(进店提示闪烁、偶尔按↓钻不动)
        if (!p.onGround && p.vy >= 0) {
            int r = (int) Math.floor((ny + Player.H + 0.5) / T);
            double gap = r * T - (ny + Player.H);
            if (gap > -0.01 && gap <= 0.5 && blocked2(r, c0, c1)) {
                ny = r * T - Player.H;
                impact = Math.max(impact, p.vy);
                p.vy = 0;
                p.onGround = true;
            }
        }
        p.y = ny;
        return impact;
    }

    boolean blocked2(int r, int c0, int c1) {
        for (int c = c0; c <= c1; c++) if (world.solid(c, r)) return true;
        return false;
    }

    void tryDigDown() {
        int c = (int) Math.floor((p.x + Player.W / 2.0) / T);
        int r = (int) Math.floor((p.y + Player.H + 1) / T);
        startDig(c, r, 0);
    }

    void tryDigSide(int dir) {
        int c = (int) Math.floor((p.x + Player.W / 2.0) / T) + dir;
        int r = (int) Math.floor((p.y + Player.H - 1) / T);
        startDig(c, r, dir);
    }

    void startDig(int c, int r, int dir) {
        byte t = world.get(c, r);
        if (t == World.AIR) return;
        if (!World.diggable(t)) {
            if (rockMsgCooldown <= 0) {
                String m = t == World.ROCK ? "岩石太硬,钻不动!(可以用炸药)"
                    : t == World.CONCRETE ? "这是商店的水泥地基" : "这里是基岩,再也挖不下去了";
                addText(p.x + Player.W / 2.0, p.y - 6, m, new Color(255, 220, 150));
                rockMsgCooldown = 1.5;
            }
            return;
        }
        p.digging = true;
        p.digCol = c;
        p.digRow = r;
        p.digDir = dir;
        if (dir != 0) p.facing = dir;
        p.digProgress = 0;
        p.digTime = DIG_BASE * world.hardness(c, r) / p.drillMul();
        p.digSX = p.x;
        p.digSY = p.y;
        p.digTX = c * T + (T - Player.W) / 2.0;
        p.digTY = r * T + T - Player.H;
        p.vx = p.vy = 0;
    }

    void finishDig() {
        int c = p.digCol, r = p.digRow;
        byte t = world.get(c, r);
        world.set(c, r, World.AIR);
        p.digging = false;
        p.x = p.digTX;
        p.y = p.digTY;
        p.vx = p.vy = 0;
        double cx = c * T + T / 2.0, cy = r * T + T / 2.0;
        int m = World.mineralOf(t);
        if (m >= 0) {
            Mineral mi = Mineral.ALL[m];
            if (p.cargoCount() < p.cargoMax()) {
                p.cargo[m]++;
                addText(cx, cy - 14, "+" + mi.name + "  $" + mi.value, brighten(mi.color));
                sfx.pick();
                if (m == Mineral.HEART) addText(cx, cy - 34, "传说中的熔岩之心!!", new Color(255, 160, 40));
            } else {
                addText(cx, cy - 14, "货舱满了! " + mi.name + "掉了", new Color(255, 90, 80));
                sfx.error();
            }
        } else if (t == World.LAVA) {
            for (int i = 0; i < 25; i++) spawnFire(cx, cy);
            double dmg = (6 + (r - World.SURFACE) / 20.0) * (1 - p.radiator());
            damage(dmg, "熔岩灼烧");
        } else if (t == World.GAS) {
            for (int dr = -1; dr <= 1; dr++)
                for (int dc = -1; dc <= 1; dc++) {
                    byte n = world.get(c + dc, r + dr);
                    if (n == World.DIRT || n == World.GAS) world.set(c + dc, r + dr, World.AIR);
                }
            spawnExplosion(cx, cy, 60);
            sfx.boom();
            damage(6 + (r - World.SURFACE) / 30.0, "瓦斯爆炸");
        }
    }

    void damage(double dmg, String reason) {
        if (state != State.PLAY || dmg <= 0) return;
        p.hull -= dmg;
        addText(p.x + Player.W / 2.0, p.y - 4, String.format("-%.1f %s", dmg, reason), new Color(255, 80, 70));
        shake = Math.max(shake, 7);
        sfx.hit();
        if (p.hull <= 0) {
            p.hull = 0;
            explode("车身损毁(" + reason + ")");
        }
    }

    void explode(String reason) {
        if (state != State.PLAY) return;
        state = State.EXPLODING;
        explodeTimer = 0;
        deathReason = reason;
        p.digging = false;
        p.thrusting = false;
        spawnExplosion(p.x + Player.W / 2.0, p.y + Player.H / 2.0, 140);
        shake = 20;
        sfx.boom();
    }

    void buildReport() {
        int lost = p.cargoCount(), lostVal = p.cargoValue();
        int fee = (int) (p.money * DEATH_FEE);
        p.money -= fee;
        java.util.Arrays.fill(p.cargo, 0);
        report.clear();
        report.add("原因:" + deathReason);
        report.add("丢失货物:" + lost + " 件(价值 $" + fmtMoney(lostVal) + ")");
        report.add("救援费:$" + fmtMoney(fee) + "(现金的 25%)");
        report.add("救援队已把新车送到地面,油箱加满。");
        state = State.REPORT;
    }

    void respawn() {
        p.toSpawn();
        p.fuel = p.maxFuel();
        p.hull = p.maxHull();
        particles.clear();
        state = State.PLAY;
        snapCamera();
        save();
    }

    // ---- 道具 ----
    void useItem(int i) {
        if (p.items[i] <= 0) {
            addText(p.x + Player.W / 2.0, p.y - 6, "没有" + Upgrades.ITEM_NAMES[i] + "(去道具店买)", new Color(220, 220, 220));
            sfx.error();
            return;
        }
        double cx = p.x + Player.W / 2.0, cy = p.y + Player.H / 2.0;
        switch (i) {
            case 0:
                if (p.fuel >= p.maxFuel() - 0.01) return;
                p.fuel = Math.min(p.maxFuel(), p.fuel + 20);
                addText(cx, p.y - 6, "+20 升燃油", new Color(120, 255, 120));
                sfx.buy();
                break;
            case 1:
                if (p.hull >= p.maxHull() - 0.01) return;
                p.hull = Math.min(p.maxHull(), p.hull + 30);
                addText(cx, p.y - 6, "车身 +30", new Color(120, 220, 255));
                sfx.buy();
                break;
            case 2: {
                if (p.digging) return;
                int c = (int) Math.floor(cx / T), r = (int) Math.floor(cy / T);
                for (int dr = -1; dr <= 1; dr++)
                    for (int dc = -1; dc <= 1; dc++) {
                        byte t = world.get(c + dc, r + dr);
                        if (t != World.BEDROCK && t != World.CONCRETE && r + dr >= World.SURFACE) world.set(c + dc, r + dr, World.AIR);
                    }
                spawnExplosion(cx, cy, 90);
                shake = 14;
                sfx.boom();
                break;
            }
            default:
                for (int k = 0; k < 40; k++) spawnSpark(cx, cy);
                p.toSpawn();
                for (int k = 0; k < 40; k++) spawnSpark(p.x + Player.W / 2.0, p.y + Player.H / 2.0);
                sfx.buy();
                break;
        }
        p.items[i]--;
    }

    // ---- 商店 ----
    int nearShop() {
        if (!p.onGround || p.digging) return -1;
        if (Math.abs(p.y + Player.H - World.SURFACE * T) > 1) return -1;
        return Shop.at(p.x + Player.W / 2.0);
    }

    void openShop(int s) {
        shopType = s;
        menuSel = 0;
        shopMsg = "";
        p.vx = 0;
        state = State.SHOP;
        if (s == Shop.SELL && p.cargoCount() > 0) menuSel = 0;
    }

    void closeShop() {
        state = State.PLAY;
        shopType = -1;
        save();
    }

    static final class MenuItem {
        final String label, detail;
        final boolean enabled;
        final Runnable action;

        MenuItem(String label, String detail, boolean enabled, Runnable action) {
            this.label = label;
            this.detail = detail;
            this.enabled = enabled;
            this.action = action;
        }
    }

    List<MenuItem> shopItems() {
        List<MenuItem> list = new ArrayList<>();
        switch (shopType) {
            case Shop.FUEL: {
                double need = Math.max(0, p.maxFuel() - p.fuel);
                int cost = (int) Math.ceil(need * FUEL_PRICE);
                list.add(new MenuItem(String.format("加满燃油     %.1f 升     $%s", need, fmtMoney(cost)),
                    "油价 $2 / 升。钱不够时会尽量多加。", need > 0.05, this::buyFuel));
                double hn = Math.max(0, p.maxHull() - p.hull);
                int hc = (int) Math.ceil(hn * REPAIR_PRICE);
                list.add(new MenuItem(String.format("修复车身     %.1f 点     $%s", hn, fmtMoney(hc)),
                    "维修费 $12 / 点。", hn > 0.05, this::repair));
                break;
            }
            case Shop.SELL: {
                int v = p.cargoValue();
                list.add(new MenuItem("全部卖出     +$" + fmtMoney(v), "把货舱里的矿石全部换成钱。", p.cargoCount() > 0, this::sellAll));
                break;
            }
            case Shop.UPGRADE:
                for (int k = 0; k < Upgrades.KINDS; k++) {
                    final int kind = k;
                    int lv = p.level[k];
                    if (lv < Upgrades.TIERS - 1) {
                        list.add(new MenuItem(String.format("%s  Lv%d   %s → %s   $%s", pad(Upgrades.NAMES[k]), lv + 1,
                            Upgrades.fmt(k, lv), Upgrades.fmt(k, lv + 1), fmtMoney(Upgrades.PRICES[k][lv + 1])),
                            Upgrades.DESC[k] + "  下一级:" + Upgrades.TIER_NAMES[lv + 1] + Upgrades.NAMES[k],
                            p.money >= Upgrades.PRICES[k][lv + 1], () -> buyUpgrade(kind)));
                    } else {
                        list.add(new MenuItem(String.format("%s  Lv7   %s   已满级", pad(Upgrades.NAMES[k]), Upgrades.fmt(k, lv)),
                            Upgrades.DESC[k], false, () -> msg("已经是最高级了", Color.LIGHT_GRAY)));
                    }
                }
                break;
            case Shop.ITEMS:
                for (int i = 0; i < Upgrades.ITEM_NAMES.length; i++) {
                    final int idx = i;
                    list.add(new MenuItem(String.format("%s   $%s     持有 %d", Upgrades.ITEM_NAMES[i],
                        fmtMoney(Upgrades.ITEM_PRICES[i]), p.items[i]),
                        Upgrades.ITEM_DESC[i] + "(地下按 " + Upgrades.ITEM_KEYS[i] + " 使用)",
                        p.money >= Upgrades.ITEM_PRICES[i], () -> buyItem(idx)));
                }
                break;
            default:
        }
        list.add(new MenuItem("离开", "回到地面(Esc 也可以)", true, this::closeShop));
        return list;
    }

    static String pad(String s) {
        return s.length() == 2 ? s + "  " : s;
    }

    void msg(String s, Color c) {
        shopMsg = s;
        shopMsgColor = c;
        shopMsgTimer = 2.5;
    }

    void buyFuel() {
        double need = p.maxFuel() - p.fuel;
        int cost = (int) Math.ceil(need * FUEL_PRICE);
        if (need <= 0.05) {
            msg("油箱已经是满的", Color.LIGHT_GRAY);
        } else if (p.money >= cost) {
            p.money -= cost;
            p.fuel = p.maxFuel();
            msg("加满了!", new Color(120, 255, 120));
            sfx.buy();
        } else if (p.money > 0) {
            double l = p.money / FUEL_PRICE;
            p.fuel += l;
            p.money = 0;
            msg(String.format("钱不够,只加了 %.1f 升", l), new Color(255, 210, 90));
            sfx.buy();
        } else {
            msg("一分钱都没有了……", new Color(255, 100, 90));
            sfx.error();
        }
    }

    void repair() {
        double need = p.maxHull() - p.hull;
        int cost = (int) Math.ceil(need * REPAIR_PRICE);
        if (need <= 0.05) {
            msg("车身完好", Color.LIGHT_GRAY);
        } else if (p.money >= cost) {
            p.money -= cost;
            p.hull = p.maxHull();
            msg("修好了!", new Color(120, 255, 120));
            sfx.buy();
        } else if (p.money > 0) {
            double h = p.money / REPAIR_PRICE;
            p.hull += h;
            p.money = 0;
            msg(String.format("钱不够,只修了 %.1f 点", h), new Color(255, 210, 90));
            sfx.buy();
        } else {
            msg("一分钱都没有了……", new Color(255, 100, 90));
            sfx.error();
        }
    }

    void sellAll() {
        int n = p.cargoCount(), v = p.cargoValue();
        if (n == 0) {
            msg("货舱是空的", Color.LIGHT_GRAY);
            return;
        }
        if (p.cargo[Mineral.HEART] > 0) p.hearts += p.cargo[Mineral.HEART];
        p.money += v;
        java.util.Arrays.fill(p.cargo, 0);
        msg("卖出 " + n + " 件矿石,收入 $" + fmtMoney(v), new Color(255, 215, 80));
        sfx.cash();
    }

    void buyUpgrade(int k) {
        int lv = p.level[k];
        if (lv >= Upgrades.TIERS - 1) return;
        int price = Upgrades.PRICES[k][lv + 1];
        if (p.money < price) {
            msg("钱不够!还差 $" + fmtMoney(price - p.money), new Color(255, 100, 90));
            sfx.error();
            return;
        }
        double oldHull = p.maxHull();
        p.money -= price;
        p.level[k]++;
        if (k == Upgrades.HULL) p.hull += p.maxHull() - oldHull;
        msg(Upgrades.NAMES[k] + " 升级到 Lv" + (p.level[k] + 1) + "!", new Color(120, 255, 120));
        sfx.buy();
    }

    void buyItem(int i) {
        int price = Upgrades.ITEM_PRICES[i];
        if (p.money < price) {
            msg("钱不够!还差 $" + fmtMoney(price - p.money), new Color(255, 100, 90));
            sfx.error();
            return;
        }
        p.money -= price;
        p.items[i]++;
        msg("买了一个" + Upgrades.ITEM_NAMES[i], new Color(120, 255, 120));
        sfx.buy();
    }

    // ---- 新游戏 / 存档 ----
    void newGame(long seed) {
        world.generate(seed);
        p.reset();
        particles.clear();
        texts.clear();
        state = State.PLAY;
        snapCamera();
        save();
        hasSave = SaveManager.exists();
    }

    void save() {
        if (state == State.EXPLODING || state == State.REPORT) return;
        SaveManager.save(world, p);
    }

    void saveOnExit() {
        if (state == State.PLAY || state == State.SHOP || state == State.PAUSE) SaveManager.save(world, p);
        else if (state == State.EXPLODING || state == State.REPORT) {
            // 爆炸中途退出:按爆炸结算后再存
            if (state == State.EXPLODING) buildReport();
            p.toSpawn();
            p.fuel = p.maxFuel();
            p.hull = p.maxHull();
            SaveManager.save(world, p);
        }
    }

    // =================================================================== 特效

    static final class Particle {
        double x, y, vx, vy, life, max, size, drag;
        Color c;
        boolean grav;
    }

    static final class FloatText {
        double x, y, life = 1.8;
        String s;
        Color c;
    }

    void addText(double x, double y, String s, Color c) {
        FloatText f = new FloatText();
        f.x = x;
        f.y = y;
        f.s = s;
        f.c = c;
        texts.add(f);
    }

    Particle part(double x, double y, double vx, double vy, double life, double size, Color c, boolean grav) {
        Particle q = new Particle();
        q.x = x;
        q.y = y;
        q.vx = vx;
        q.vy = vy;
        q.life = q.max = life;
        q.size = size;
        q.c = c;
        q.grav = grav;
        q.drag = 1;
        if (particles.size() < 900) particles.add(q);
        return q;
    }

    void spawnDigChip() {
        double tx, ty;
        if (p.digDir == 0) {
            tx = p.x + Player.W / 2.0;
            ty = p.y + Player.H + 4;
        } else {
            tx = p.x + Player.W / 2.0 + p.digDir * 16;
            ty = p.y + Player.H / 2.0 + 2;
        }
        byte t = world.get(p.digCol, p.digRow);
        Color c = Textures.BAND_COLORS[Textures.band(p.digRow)];
        int m = World.mineralOf(t);
        if (m >= 0 && fx.nextBoolean()) c = Mineral.ALL[m].color;
        if (t == World.LAVA) c = new Color(255, 140, 20);
        part(tx, ty, (fx.nextDouble() - 0.5) * 160 - p.digDir * 60, -60 - fx.nextDouble() * 120, 0.5, 2 + fx.nextInt(2), c, true);
    }

    void spawnSmoke(double x, double y, boolean flame) {
        if (flame) {
            part(x + (fx.nextDouble() - 0.5) * 16, y + 4, (fx.nextDouble() - 0.5) * 20, 80 + fx.nextDouble() * 60, 0.35, 3,
                new Color(255, 150 + fx.nextInt(90), 40), false);
        } else {
            Particle q = part(x + fx.nextDouble() * 14, y, (fx.nextDouble() - 0.5) * 15, -30 - fx.nextDouble() * 20, 1.0, 4,
                new Color(90, 90, 90), false);
            q.drag = 0.98;
        }
    }

    void spawnFire(double x, double y) {
        Particle q = part(x + (fx.nextDouble() - 0.5) * 20, y + (fx.nextDouble() - 0.5) * 20, (fx.nextDouble() - 0.5) * 120,
            -40 - fx.nextDouble() * 140, 0.6 + fx.nextDouble() * 0.4, 3 + fx.nextInt(3), new Color(255, 100 + fx.nextInt(140), 20), false);
        q.drag = 0.97;
    }

    void spawnSpark(double x, double y) {
        double a = fx.nextDouble() * Math.PI * 2, s = 40 + fx.nextDouble() * 160;
        Particle q = part(x, y, Math.cos(a) * s, Math.sin(a) * s, 0.7, 3, new Color(150, 220, 255), false);
        q.drag = 0.95;
    }

    void spawnExplosion(double x, double y, int n) {
        for (int i = 0; i < n; i++) {
            double a = fx.nextDouble() * Math.PI * 2, s = 50 + fx.nextDouble() * 320;
            boolean smoke = i % 3 == 0;
            Particle q = part(x, y, Math.cos(a) * s, Math.sin(a) * s, 0.5 + fx.nextDouble() * (smoke ? 1.6 : 0.9),
                smoke ? 6 + fx.nextInt(5) : 3 + fx.nextInt(4),
                smoke ? new Color(70, 65, 60) : new Color(255, 90 + fx.nextInt(160), fx.nextInt(40)), false);
            q.drag = 0.93;
        }
        shake = Math.max(shake, 10);
    }

    void updateEffects(double dt) {
        for (Iterator<Particle> it = particles.iterator(); it.hasNext(); ) {
            Particle q = it.next();
            q.life -= dt;
            if (q.life <= 0) {
                it.remove();
                continue;
            }
            if (q.grav) q.vy += 600 * dt;
            if (q.drag != 1) {
                double d = Math.pow(q.drag, dt * 60);
                q.vx *= d;
                q.vy *= d;
            }
            q.x += q.vx * dt;
            q.y += q.vy * dt;
        }
        for (Iterator<FloatText> it = texts.iterator(); it.hasNext(); ) {
            FloatText f = it.next();
            f.life -= dt;
            f.y -= 28 * dt;
            if (f.life <= 0) it.remove();
        }
        shake = Math.max(0, shake - 35 * dt);
    }

    void updateCamera(double dt) {
        double tx = p.x + Player.W / 2.0 - VW / 2.0, ty = p.y + Player.H / 2.0 - VH / 2.0 + 30;
        double k = 1 - Math.pow(0.0015, dt);
        camX += (tx - camX) * k;
        camY += (ty - camY) * k;
        clampCamera();
    }

    void snapCamera() {
        camX = p.x + Player.W / 2.0 - VW / 2.0;
        camY = p.y + Player.H / 2.0 - VH / 2.0 + 30;
        clampCamera();
    }

    void clampCamera() {
        camX = Math.max(0, Math.min(World.COLS * T - VW, camX));
        camY = Math.max(0, Math.min(World.ROWS * T - VH, camY));
    }

    // =================================================================== 渲染

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D bg = back.createGraphics();
        render(bg);
        bg.dispose();
        Graphics2D g = (Graphics2D) g0;
        double s = Math.min(getWidth() / (double) VW, getHeight() / (double) VH);
        int w = (int) (VW * s), h = (int) (VH * s);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(back, (getWidth() - w) / 2, (getHeight() - h) / 2, w, h, null);
    }

    Font font(int size) { return new Font(fontName, Font.PLAIN, size); }
    Font fontB(int size) { return new Font(fontName, Font.BOLD, size); }

    void render(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        double sx = shake > 0 ? (fx.nextDouble() - 0.5) * shake : 0, sy = shake > 0 ? (fx.nextDouble() - 0.5) * shake : 0;
        int ox = (int) Math.round(camX + sx), oy = (int) Math.round(camY + sy);

        drawSky(g, ox, oy);
        for (int i = 0; i < Shop.NAMES.length; i++) drawShop(g, i, ox, oy);
        drawTiles(g, ox, oy);
        if (!(state == State.EXPLODING && explodeTimer > 0.1) && state != State.REPORT) drawVehicle(g, p.x - ox, p.y - oy);
        drawParticles(g, ox, oy);
        drawDarkness(g, ox, oy);
        drawTexts(g, ox, oy);

        switch (state) {
            case TITLE:
                drawTitle(g);
                break;
            case PLAY:
            case EXPLODING:
                drawHud(g);
                drawPrompts(g);
                break;
            case SHOP:
                drawHud(g);
                drawShopMenu(g);
                break;
            case PAUSE:
                drawHud(g);
                drawPause(g);
                break;
            case HELP:
                if (helpReturn != State.TITLE) drawHud(g);
                drawHelp(g);
                break;
            case REPORT:
                drawHud(g);
                drawReport(g);
                break;
            default:
        }
    }

    void drawSky(Graphics2D g, int ox, int oy) {
        int ground = World.SURFACE * T - oy;
        g.setColor(new Color(20, 12, 10));
        g.fillRect(0, 0, VW, VH);
        if (ground <= 0) return;
        g.setPaint(new GradientPaint(0, -oy, new Color(70, 140, 220), 0, ground, new Color(190, 225, 250)));
        g.fillRect(0, 0, VW, ground);
        // 太阳
        g.setColor(new Color(255, 240, 180, 120));
        g.fillOval(760 - ox / 10 - 18, 40 - oy - 18, 96, 96);
        g.setColor(new Color(255, 236, 150));
        g.fillOval(760 - ox / 10, 40 - oy, 60, 60);
        // 云
        g.setColor(new Color(255, 255, 255, 200));
        for (int i = 0; i < 5; i++) {
            double cx = ((i * 337 + time * (8 + i * 2)) % 1900) - 200 - ox * 0.2;
            double cy = 30 + (i * 53) % 110 - oy;
            g.fill(new Ellipse2D.Double(cx, cy, 90, 26));
            g.fill(new Ellipse2D.Double(cx + 22, cy - 12, 52, 30));
        }
        // 远山与近丘
        drawHills(g, ox, ground, 0.25, 120, new Color(120, 140, 175), 3);
        drawHills(g, ox, ground, 0.5, 70, new Color(105, 140, 95), 7);
    }

    void drawHills(Graphics2D g, int ox, int ground, double par, int amp, Color c, int seed) {
        Path2D.Double path = new Path2D.Double();
        path.moveTo(0, ground);
        for (int x = 0; x <= VW; x += 8) {
            double wx = (x + ox * par) * 0.006;
            double h = (Math.sin(wx + seed) * 0.5 + Math.sin(wx * 2.3 + seed * 1.7) * 0.3 + Math.sin(wx * 5.1) * 0.12 + 1) * 0.5;
            path.lineTo(x, ground - 20 - h * amp);
        }
        path.lineTo(VW, ground);
        path.closePath();
        g.setColor(c);
        g.fill(path);
    }

    void drawShop(Graphics2D g, int i, int ox, int oy) {
        int x = Shop.COL[i] * T - ox, w = Shop.WIDTH[i] * T, ground = World.SURFACE * T - oy;
        if (x + w < -20 || x > VW + 20 || ground < 0 || ground - 160 > VH) return;
        Art.shop(g, i, x, w, ground, time, fontB(14));
    }

    void drawTiles(Graphics2D g, int ox, int oy) {
        int c0 = Math.max(0, ox / T - 1), c1 = Math.min(World.COLS - 1, (ox + VW) / T + 1);
        int r0 = Math.max(0, oy / T - 1), r1 = Math.min(World.ROWS - 1, (oy + VH) / T + 1);
        for (int r = r0; r <= r1; r++) {
            int b = Textures.band(Math.max(r, World.SURFACE));
            for (int c = c0; c <= c1; c++) {
                byte t = world.get(c, r);
                int x = c * T - ox, y = r * T - oy;
                if (t == World.AIR) {
                    if (r >= World.SURFACE) g.drawImage(tex.cave[b], x, y, null);
                    continue;
                }
                g.drawImage(tex.tile(t, c, r, time), x, y, null);
                if (t >= World.ORE + 3) sparkle(g, c, r, x, y);
            }
        }
        // 和空洞相邻的边加暗边,更有立体感
        g.setColor(new Color(0, 0, 0, 95));
        for (int r = Math.max(r0, World.SURFACE); r <= r1; r++) {
            for (int c = c0; c <= c1; c++) {
                if (world.get(c, r) == World.AIR) continue;
                int x = c * T - ox, y = r * T - oy;
                if (c > 0 && world.get(c - 1, r) == World.AIR) g.fillRect(x, y, 3, T);
                if (c < World.COLS - 1 && world.get(c + 1, r) == World.AIR) g.fillRect(x + T - 3, y, 3, T);
                if (r > World.SURFACE && world.get(c, r - 1) == World.AIR) g.fillRect(x, y, T, 3);
                if (world.get(c, r + 1) == World.AIR) g.fillRect(x, y + T - 3, T, 3);
            }
        }
    }

    /** 银矿以上的贵重矿石偶尔闪一下 */
    void sparkle(Graphics2D g, int c, int r, int x, int y) {
        int h = Textures.hash(c * 7 + 3, r * 13 + 1);
        double phase = (time * 0.6 + (h % 1000) / 1000.0 * 3) % 3;   // 每 5 秒左右闪一次
        if (phase > 0.35) return;
        double k = Math.sin(phase / 0.35 * Math.PI);
        double sx = x + 6 + (h >> 4) % 20, sy = y + 6 + (h >> 9) % 20, len = 2 + 4 * k;
        g.setColor(new Color(255, 255, 255, (int) (230 * k)));
        g.fillRect((int) (sx - len), (int) sy, (int) (len * 2 + 1), 1);
        g.fillRect((int) sx, (int) (sy - len), 1, (int) (len * 2 + 1));
        g.fillRect((int) sx - 1, (int) sy - 1, 3, 3);
    }

    void drawVehicle(Graphics2D g, double sx, double sy) {
        Art.vehicle(g, sx + Player.W / 2.0, sy + Player.H / 2.0, p.facing, p.digging && p.digDir == 0, p.digging,
            p.thrusting && state == State.PLAY && !p.digging, p.wheelAngle, time, fx, p.hull < p.maxHull() * 0.3);
    }

    void drawParticles(Graphics2D g, int ox, int oy) {
        for (Particle q : particles) {
            int a = (int) (255 * Math.max(0, Math.min(1, q.life / q.max)));
            g.setColor(new Color(q.c.getRed(), q.c.getGreen(), q.c.getBlue(), a));
            int s = (int) q.size;
            g.fillRect((int) (q.x - ox - s / 2.0), (int) (q.y - oy - s / 2.0), s, s);
        }
    }

    void drawDarkness(Graphics2D g, int ox, int oy) {
        double d = p.depthRows();
        double a = Math.max(0, Math.min(1, (d - 3) / 60.0)) * 0.92;
        if (a <= 0.01) return;
        float cx = (float) (p.x + Player.W / 2.0 - ox), cy = (float) (p.y + Player.H / 2.0 - oy);
        float rad = 250;
        g.setPaint(new RadialGradientPaint(cx, cy, rad, new float[]{0f, 0.45f, 1f},
            new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, (int) (a * 0.35 * 255)), new Color(0, 0, 0, (int) (a * 255))}));
        g.fillRect(0, 0, VW, VH);
        // 熔岩在黑暗中发光
        int c0 = Math.max(0, ox / T), c1 = Math.min(World.COLS - 1, (ox + VW) / T);
        int r0 = Math.max(0, oy / T), r1 = Math.min(World.ROWS - 1, (oy + VH) / T);
        Composite oc = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) (a * 0.75)));
        for (int r = r0; r <= r1; r++)
            for (int c = c0; c <= c1; c++) {
                byte t = world.get(c, r);
                if (t == World.LAVA || t == World.ORE + Mineral.HEART)
                    g.drawImage(tex.tile(t, c, r, time), c * T - ox, r * T - oy, null);
            }
        g.setComposite(oc);
    }

    void drawTexts(Graphics2D g, int ox, int oy) {
        g.setFont(fontB(14));
        FontMetrics fm = g.getFontMetrics();
        for (FloatText f : texts) {
            int a = (int) (255 * Math.min(1, f.life / 0.6));
            int x = (int) (f.x - ox - fm.stringWidth(f.s) / 2.0), y = (int) (f.y - oy);
            outlined(g, f.s, x, y, new Color(f.c.getRed(), f.c.getGreen(), f.c.getBlue(), a), a);
        }
    }

    void outlined(Graphics2D g, String s, int x, int y, Color c, int alpha) {
        g.setColor(new Color(0, 0, 0, Math.min(255, alpha)));
        g.drawString(s, x - 1, y);
        g.drawString(s, x + 1, y);
        g.drawString(s, x, y - 1);
        g.drawString(s, x, y + 1);
        g.drawString(s, x + 1, y + 1);
        g.setColor(c);
        g.drawString(s, x, y);
    }

    void drawHud(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 180));
        g.fillRect(0, 0, VW, 48);
        g.setColor(new Color(255, 255, 255, 40));
        g.drawLine(0, 48, VW, 48);

        g.setFont(fontB(21));
        g.setColor(new Color(255, 214, 70));
        g.drawString("$ " + fmtMoney(p.money), 14, 32);

        g.setFont(font(12));
        g.setColor(new Color(200, 200, 200));
        g.drawString("深度", 160, 19);
        g.setFont(fontB(17));
        g.setColor(Color.WHITE);
        g.drawString(depthMeters() + " 米", 160, 39);

        double ff = p.fuel / p.maxFuel();
        int alarm = fuelAlarm();
        boolean flash = alarm > 0 && ((int) (time * 4)) % 2 == 0;
        Color fuelC = alarm > 0 ? (flash ? new Color(255, 60, 50) : new Color(150, 30, 25))
            : ff > 0.5 ? new Color(80, 210, 90) : new Color(240, 200, 50);
        drawBar(g, 250, "燃油", ff, fuelC, String.format("%.1f / %.0f 升", p.fuel, p.maxFuel()));
        // 白色刻度线:飞回地面大约需要的油
        double ret = returnFuel();
        if (ret > 0.05) {
            int bx = 284 + (int) (170 * Math.min(1, ret / p.maxFuel()));
            g.setColor(Color.WHITE);
            g.fillRect(bx - 1, 5, 2, 20);
            g.setFont(font(11));
            g.setColor(new Color(220, 220, 220));
            g.drawString(String.format("回程约 %.1f 升", ret), 372, 40);
        }
        double hf = p.hull / p.maxHull();
        Color hullC = hf > 0.5 ? new Color(80, 170, 240) : hf > 0.25 ? new Color(240, 160, 50) : new Color(240, 60, 50);
        drawBar(g, 470, "车身", hf, hullC, String.format("%.1f / %.0f", p.hull, p.maxHull()));

        g.setFont(font(12));
        g.setColor(new Color(200, 200, 200));
        g.drawString("货舱", 690, 19);
        g.setFont(fontB(17));
        boolean full = p.cargoCount() >= p.cargoMax();
        g.setColor(full ? new Color(255, 90, 80) : Color.WHITE);
        g.drawString(p.cargoCount() + " / " + p.cargoMax() + (full ? " 满" : ""), 690, 39);

        g.setFont(font(12));
        g.setColor(new Color(220, 220, 220));
        g.drawString("F 油桶×" + p.items[0] + "   R 修理×" + p.items[1], 790, 20);
        g.drawString("X 炸药×" + p.items[2] + "   T 传送×" + p.items[3], 790, 39);
    }

    void drawBar(Graphics2D g, int x, String label, double frac, Color c, String text) {
        g.setFont(font(12));
        g.setColor(new Color(200, 200, 200));
        g.drawString(label, x, 19);
        int bx = x + 34, bw = 170;
        g.setColor(new Color(40, 40, 40));
        g.fillRect(bx, 8, bw, 14);
        g.setColor(c);
        g.fillRect(bx, 8, (int) (bw * Math.max(0, Math.min(1, frac))), 14);
        g.setColor(new Color(255, 255, 255, 120));
        g.drawRect(bx, 8, bw, 14);
        g.setColor(Color.WHITE);
        g.drawString(text, bx, 40);
    }

    void drawPrompts(Graphics2D g) {
        if (state == State.PLAY) {
            int s = nearShop();
            if (s >= 0) {
                String t = "按 回车 进入" + Shop.NAMES[s];
                g.setFont(fontB(16));
                FontMetrics fm = g.getFontMetrics();
                int w = fm.stringWidth(t) + 24;
                int x = (int) (p.x + Player.W / 2.0 - camX - w / 2.0), y = (int) (World.SURFACE * T - camY - 182);
                x = Math.max(4, Math.min(VW - w - 4, x));
                g.setColor(new Color(0, 0, 0, 190));
                g.fillRoundRect(x, y, w, 30, 10, 10);
                g.setColor(new Color(255, 215, 90));
                g.drawRoundRect(x, y, w, 30, 10, 10);
                g.drawString(t, x + 12, y + 21);
            }
        }
        int alarm = fuelAlarm();
        if (alarm > 0 && state == State.PLAY) {
            float pulse = (float) (0.5 + 0.5 * Math.sin(time * (alarm == 2 ? 14 : 8)));
            g.setColor(new Color(255, 0, 0, (int) (40 + 90 * pulse)));
            for (int i = 0; i < 4; i++) g.drawRect(i * 3, 48 + i * 3, VW - 1 - i * 6, VH - 49 - i * 6);
            g.setColor(new Color(255, 0, 0, (int) (30 * pulse)));
            g.fillRect(0, 48, VW, VH - 48);
            if (((int) (time * 4)) % 2 == 0) {
                g.setFont(fontB(26));
                String t = alarm == 2 ? "燃油只够勉强飞回去了!马上往上飞!" : "燃油不足!该回地面加油了!";
                int w = g.getFontMetrics().stringWidth(t);
                outlined(g, t, (VW - w) / 2, 100, new Color(255, 70, 60), 255);
            }
        }
        // 底部按键提示
        g.setFont(font(12));
        String hint = "←→ 移动   ↑ 飞行   ↓ 向下钻   贴墙按←→ 横向钻   回车 进店   F/R/X/T 用道具   Esc 菜单/说明";
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRect(0, VH - 22, VW, 22);
        g.setColor(new Color(230, 230, 230, 200));
        g.drawString(hint, (VW - g.getFontMetrics().stringWidth(hint)) / 2, VH - 7);
    }

    void panel(Graphics2D g, int x, int y, int w, int h) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRect(0, 0, VW, VH);
        g.setColor(new Color(28, 24, 22, 245));
        g.fillRoundRect(x, y, w, h, 18, 18);
        g.setColor(new Color(255, 190, 70));
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(x, y, w, h, 18, 18);
        g.setStroke(new BasicStroke(1));
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
    }

    void drawShopMenu(Graphics2D g) {
        int x = 150, y = 80, w = 660, h = 480;
        panel(g, x, y, w, h);
        g.setFont(fontB(26));
        g.setColor(new Color(255, 200, 80));
        g.drawString(Shop.NAMES[shopType], x + 28, y + 46);
        g.setFont(fontB(20));
        g.setColor(new Color(255, 214, 70));
        String m = "现金 $" + fmtMoney(p.money);
        g.drawString(m, x + w - 28 - g.getFontMetrics().stringWidth(m), y + 44);
        g.setColor(new Color(255, 255, 255, 50));
        g.drawLine(x + 20, y + 62, x + w - 20, y + 62);

        int ly = y + 96;
        if (shopType == Shop.SELL) {
            g.setFont(font(15));
            if (p.cargoCount() == 0) {
                g.setColor(new Color(180, 180, 180));
                g.drawString("货舱是空的。下去挖点矿再来吧!", x + 32, ly);
                ly += 34;
            } else {
                for (int i = 0; i < Mineral.ALL.length; i++) {
                    if (p.cargo[i] == 0) continue;
                    Mineral mi = Mineral.ALL[i];
                    g.setColor(mi.color);
                    g.fillRect(x + 32, ly - 12, 14, 14);
                    g.setColor(Color.WHITE);
                    g.drawString(mi.name, x + 56, ly);
                    g.drawString("× " + p.cargo[i], x + 180, ly);
                    g.drawString("单价 $" + fmtMoney(mi.value), x + 260, ly);
                    g.setColor(new Color(255, 214, 70));
                    g.drawString("$" + fmtMoney(mi.value * p.cargo[i]), x + 430, ly);
                    ly += 24;
                }
                ly += 10;
            }
        }

        List<MenuItem> items = shopItems();
        menuSel = Math.max(0, Math.min(items.size() - 1, menuSel));
        g.setFont(fontB(17));
        for (int i = 0; i < items.size(); i++) {
            MenuItem it = items.get(i);
            int iy = ly + i * 40;
            if (i == menuSel) {
                g.setColor(new Color(255, 190, 70, 60));
                g.fillRoundRect(x + 20, iy - 26, w - 40, 36, 8, 8);
                arrow(g, x + 32, iy - 8);
            }
            g.setColor(it.enabled ? Color.WHITE : new Color(140, 140, 140));
            g.drawString(it.label, x + 56, iy);
        }
        MenuItem sel = items.get(menuSel);
        g.setFont(font(14));
        g.setColor(new Color(200, 200, 200));
        g.drawString(sel.detail, x + 32, y + h - 56);
        if (shopMsgTimer > 0) {
            g.setFont(fontB(16));
            g.setColor(shopMsgColor);
            g.drawString(shopMsg, x + 32, y + h - 26);
        } else {
            g.setFont(font(13));
            g.setColor(new Color(150, 150, 150));
            g.drawString("↑↓ 选择    回车 确定    Esc 离开", x + 32, y + h - 26);
        }
    }

    static final String[] PAUSE_ITEMS = {"继续游戏", "游戏说明", "保存游戏", "保存并回到标题"};

    void openHelp(State back) {
        helpReturn = back;
        helpPage = 0;
        state = State.HELP;
    }

    void closeHelp() {
        state = helpReturn;
        menuSel = 0;
    }

    static final String[] HELP_TITLES = {"操作", "怎么玩", "矿石价目表", "危险", "商店与升级"};

    /** 说明页内容;"#"开头的行是小标题 */
    String[] helpLines(int page) {
        switch (page) {
            case 0:
                return new String[]{
                    "#开车",
                    "← →  (或 A D)    左右行驶",
                    "↑    (或 W)      喷气往上飞(很费油)",
                    "↓    (或 S)      向下钻",
                    "贴着墙按住 ← / →  横着钻(不能往上钻)",
                    "#地面上",
                    "停在商店门口按 回车(或 E)  进店",
                    "#道具(先在道具店买)",
                    "F 备用油桶    R 修理包    X 炸药    T 紧急传送",
                    "#其它",
                    "Esc  暂停 / 存档 / 看说明        窗口可以拉大、最大化",
                };
            case 1:
                return new String[]{
                    "#一趟的流程",
                    "1. 在地面加满油,从空地往下钻",
                    "2. 挖到的矿自动装进货舱(货舱满了再挖到的矿会丢掉)",
                    "3. 看着油量,在油烧光之前飞回地面",
                    "4. 去「矿石收购站」把矿卖掉换钱",
                    "5. 去「改装厂」升级,再去「加油站」加油修车,继续往下挖",
                    "#诀窍",
                    "越深的矿越值钱,但往回飞的路也越长 —— 算好来回的油",
                    "燃油条上的白线 = 飞回地面大约要的油;剩油快不够回程时会报警、屏幕闪红",
                    "停在地面不耗油;停在地下也会慢慢耗油,别在下面发呆",
                    "地底最深处藏着 6 颗「熔岩之心」,一颗值 $30,000",
                };
            case 3:
                return new String[]{
                    "#燃油耗尽 → 车会爆炸!",
                    "爆炸后:这一趟的货全部丢失,并扣掉现金的 25% 作为救援费",
                    "救援队会把新车送回地面,油加满、车修好",
                    "#岩石(灰色方块)",
                    "钻不动,只能绕开,或者用炸药炸掉",
                    "#熔岩(橙红色)",
                    "钻进去会被烧伤,越深伤害越高;升级「散热器」可以减伤",
                    "#瓦斯(看起来像普通泥土,细看有淡绿色斑点)",
                    "一挖就爆炸,扣车身并炸开周围的土",
                    "#摔落",
                    "从高处掉下来速度太快会摔伤;车身归零同样会爆炸",
                };
            default:
                return new String[0];
        }
    }

    void drawHelp(Graphics2D g) {
        int x = 110, y = 60, w = 740, h = 530;
        panel(g, x, y, w, h);
        g.setFont(fontB(26));
        g.setColor(new Color(255, 200, 80));
        g.drawString("游戏说明", x + 28, y + 46);
        // 页签
        g.setFont(fontB(15));
        int tx = x + 170;
        for (int i = 0; i < HELP_TITLES.length; i++) {
            String s = HELP_TITLES[i];
            int sw = g.getFontMetrics().stringWidth(s) + 20;
            if (i == helpPage) {
                g.setColor(new Color(255, 190, 70));
                g.fillRoundRect(tx, y + 24, sw, 28, 8, 8);
                g.setColor(new Color(30, 20, 10));
            } else {
                g.setColor(new Color(200, 200, 200));
            }
            g.drawString(s, tx + 10, y + 44);
            tx += sw + 8;
        }
        g.setColor(new Color(255, 255, 255, 50));
        g.drawLine(x + 20, y + 66, x + w - 20, y + 66);

        int ly = y + 100;
        if (helpPage == 2) drawMineralTable(g, x + 36, ly);
        else if (helpPage == 4) drawShopHelp(g, x + 36, ly);
        else {
            for (String line : helpLines(helpPage)) {
                if (line.startsWith("#")) {
                    ly += 6;
                    g.setFont(fontB(17));
                    g.setColor(new Color(255, 200, 80));
                    g.drawString(line.substring(1), x + 36, ly);
                } else {
                    g.setFont(font(16));
                    g.setColor(Color.WHITE);
                    g.drawString(line, x + 52, ly);
                }
                ly += 32;
            }
        }
        g.setFont(font(13));
        g.setColor(new Color(150, 150, 150));
        g.drawString("← → 翻页 (" + (helpPage + 1) + "/" + HELP_TITLES.length + ")      Esc / 回车 返回", x + 28, y + h - 20);
    }

    void drawMineralTable(Graphics2D g, int x, int y) {
        g.setFont(fontB(15));
        g.setColor(new Color(255, 200, 80));
        g.drawString("矿石", x + 40, y);
        g.drawString("单价", x + 200, y);
        g.drawString("大约从多深开始出现", x + 340, y);
        y += 34;
        for (int i = 0; i < Mineral.ALL.length; i++) {
            Mineral m = Mineral.ALL[i];
            int b = Textures.band(World.SURFACE + Math.max(1, m.minDepth));
            g.drawImage(tex.ore[b][i][0], x, y - 22, 28, 28, null);
            g.setFont(fontB(16));
            g.setColor(Color.WHITE);
            g.drawString(m.name, x + 40, y);
            g.setColor(new Color(255, 214, 70));
            g.drawString("$" + fmtMoney(m.value), x + 200, y);
            g.setFont(font(15));
            g.setColor(new Color(210, 210, 210));
            String d = i == Mineral.HEART ? "最底部附近,全图只有几颗,周围全是熔岩"
                : Math.round(m.minDepth * METERS_PER_ROW) + " 米以下,约 " + Math.round(m.peakDepth * METERS_PER_ROW) + " 米最多";
            g.drawString(d, x + 340, y);
            y += 38;
        }
    }

    void drawShopHelp(Graphics2D g, int x, int y) {
        String[] shops = {
            "加油站:加油 $2/升,修车 $12/点(钱不够会尽量多加)",
            "矿石收购站:把货舱里的矿全部卖掉",
            "改装厂:升级下面 6 种部件,每种 7 级,越往后越贵",
            "道具店:买消耗道具,在地下按对应键使用",
        };
        g.setFont(fontB(17));
        g.setColor(new Color(255, 200, 80));
        g.drawString("商店", x, y);
        y += 26;
        g.setFont(font(15));
        g.setColor(Color.WHITE);
        for (String s : shops) {
            g.drawString(s, x + 16, y);
            y += 22;
        }
        y += 6;
        g.setFont(fontB(17));
        g.setColor(new Color(255, 200, 80));
        g.drawString("部件(出厂 → 满级)", x, y);
        y += 26;
        for (int k = 0; k < Upgrades.KINDS; k++) {
            g.setFont(fontB(15));
            g.setColor(Color.WHITE);
            g.drawString(Upgrades.NAMES[k], x + 16, y);
            g.setFont(font(14));
            g.setColor(new Color(150, 220, 255));
            g.drawString(Upgrades.fmt(k, 0) + " → " + Upgrades.fmt(k, Upgrades.TIERS - 1), x + 86, y);
            g.setColor(new Color(210, 210, 210));
            g.drawString(Upgrades.DESC[k], x + 290, y);
            y += 21;
        }
        y += 6;
        g.setFont(fontB(17));
        g.setColor(new Color(255, 200, 80));
        g.drawString("道具", x, y);
        y += 26;
        for (int i = 0; i < Upgrades.ITEM_NAMES.length; i++) {
            g.setFont(fontB(15));
            g.setColor(Color.WHITE);
            g.drawString(Upgrades.ITEM_KEYS[i] + "  " + Upgrades.ITEM_NAMES[i], x + 16, y);
            g.setFont(font(14));
            g.setColor(new Color(255, 214, 70));
            g.drawString("$" + fmtMoney(Upgrades.ITEM_PRICES[i]), x + 140, y);
            g.setColor(new Color(210, 210, 210));
            g.drawString(Upgrades.ITEM_DESC[i], x + 220, y);
            y += 21;
        }
    }

    void drawPause(Graphics2D g) {
        int x = 300, y = 170, w = 360, h = 300;
        panel(g, x, y, w, h);
        g.setFont(fontB(26));
        g.setColor(new Color(255, 200, 80));
        g.drawString("暂停", x + 30, y + 48);
        g.setFont(font(13));
        g.setColor(new Color(200, 200, 200));
        g.drawString("最深纪录 " + Math.round(p.maxDepth * METERS_PER_ROW) + " 米    熔岩之心 " + p.hearts + " 颗", x + 30, y + 76);
        drawList(g, PAUSE_ITEMS, x + 30, y + 130, w - 60);
        if (shopMsgTimer > 0) {
            g.setFont(fontB(15));
            g.setColor(shopMsgColor);
            g.drawString(shopMsg, x + 30, y + h - 24);
        }
    }

    void arrow(Graphics2D g, int x, int cy) {
        g.setColor(new Color(255, 190, 70));
        g.fillPolygon(new int[]{x, x + 12, x}, new int[]{cy - 7, cy, cy + 7}, 3);
    }

    void drawList(Graphics2D g, String[] items, int x, int y, int w) {
        g.setFont(fontB(19));
        for (int i = 0; i < items.length; i++) {
            int iy = y + i * 44;
            if (i == menuSel) {
                g.setColor(new Color(255, 190, 70, 60));
                g.fillRoundRect(x - 10, iy - 28, w + 20, 40, 8, 8);
                arrow(g, x + 2, iy - 8);
            }
            g.setColor(Color.WHITE);
            g.drawString(items[i], x + 28, iy);
        }
    }

    String[] titleItems() {
        if (hasSave) return new String[]{"继续游戏", confirmNew ? "确定要覆盖存档吗?再按一次回车" : "新游戏", "游戏说明", "退出"};
        return new String[]{"开始游戏", "游戏说明", "退出"};
    }

    void drawTitle(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 130));
        g.fillRect(0, 0, VW, VH);
        g.setFont(fontB(72));
        String t = "熔岩矿车";
        int tw = g.getFontMetrics().stringWidth(t);
        int tx = (VW - tw) / 2, ty = 150;
        g.setColor(new Color(60, 10, 0));
        g.drawString(t, tx + 4, ty + 4);
        g.setPaint(new GradientPaint(0, ty - 60, new Color(255, 230, 120), 0, ty, new Color(255, 90, 20)));
        g.drawString(t, tx, ty);
        g.setFont(font(18));
        String sub = "钻得越深,矿越值钱 —— 记得留够油回家!";
        g.setColor(new Color(255, 230, 200));
        g.drawString(sub, (VW - g.getFontMetrics().stringWidth(sub)) / 2, ty + 42);

        String[] items = titleItems();
        int w = 380, x = (VW - w) / 2;
        drawList(g, items, x + 20, 270, w - 40);

        g.setFont(font(14));
        g.setColor(new Color(220, 220, 220));
        String[] help = {
            "← → 左右开    ↑ 喷气飞行    ↓ 向下钻    贴着墙按 ← → 横着钻",
            "回到地面:回车 进店 —— 加油站 / 矿石收购站 / 改装厂 / 道具店",
            "油烧光或车身打坏,车会爆炸:货物全丢,还要交救援费",
        };
        for (int i = 0; i < help.length; i++)
            g.drawString(help[i], (VW - g.getFontMetrics().stringWidth(help[i])) / 2, 470 + i * 26);
    }

    void drawReport(Graphics2D g) {
        int x = 230, y = 170, w = 500, h = 300;
        panel(g, x, y, w, h);
        g.setFont(fontB(30));
        g.setColor(new Color(255, 80, 60));
        g.drawString("轰!车炸了!", x + 30, y + 54);
        g.setFont(font(17));
        g.setColor(Color.WHITE);
        for (int i = 0; i < report.size(); i++) g.drawString(report.get(i), x + 30, y + 100 + i * 32);
        g.setFont(fontB(16));
        g.setColor(new Color(255, 200, 80));
        g.drawString("按 回车 重新出发", x + 30, y + h - 28);
    }

    String depthMeters() {
        return Long.toString(Math.max(0, Math.round(p.depthRows() * METERS_PER_ROW)));
    }

    static String fmtMoney(long v) {
        return String.format("%,d", v);
    }

    static Color brighten(Color c) {
        if (c.getRed() + c.getGreen() + c.getBlue() < 150) return new Color(200, 200, 210);
        return c;
    }

    // =================================================================== 输入

    @Override
    public void keyPressed(KeyEvent e) {
        int k = e.getKeyCode();
        boolean repeat = k < keys.length && keys[k];
        if (k < keys.length) keys[k] = true;
        boolean upK = k == KeyEvent.VK_UP || k == KeyEvent.VK_W;
        boolean downK = k == KeyEvent.VK_DOWN || k == KeyEvent.VK_S;
        boolean ok = k == KeyEvent.VK_ENTER || k == KeyEvent.VK_SPACE;
        boolean esc = k == KeyEvent.VK_ESCAPE;
        switch (state) {
            case TITLE: {
                String[] items = titleItems();
                if (upK || downK) {
                    menuSel = (menuSel + (upK ? -1 : 1) + items.length) % items.length;
                    confirmNew = false;
                } else if (ok && !repeat) {
                    titleAction(menuSel);
                }
                break;
            }
            case PLAY:
                if (repeat) break;
                if (esc) {
                    state = State.PAUSE;
                    menuSel = 0;
                    shopMsgTimer = 0;
                } else if (k == KeyEvent.VK_ENTER || k == KeyEvent.VK_E) {
                    int s = nearShop();
                    if (s >= 0) openShop(s);
                } else if (k == KeyEvent.VK_F) useItem(0);
                else if (k == KeyEvent.VK_R) useItem(1);
                else if (k == KeyEvent.VK_X) useItem(2);
                else if (k == KeyEvent.VK_T) useItem(3);
                break;
            case SHOP: {
                List<MenuItem> items = shopItems();
                if (upK || downK) menuSel = (menuSel + (upK ? -1 : 1) + items.size()) % items.size();
                else if (ok && !repeat) items.get(Math.max(0, Math.min(items.size() - 1, menuSel))).action.run();
                else if (esc) closeShop();
                break;
            }
            case PAUSE:
                if (upK || downK) menuSel = (menuSel + (upK ? -1 : 1) + PAUSE_ITEMS.length) % PAUSE_ITEMS.length;
                else if (esc) state = State.PLAY;
                else if (ok && !repeat) {
                    if (menuSel == 0) state = State.PLAY;
                    else if (menuSel == 1) openHelp(State.PAUSE);
                    else if (menuSel == 2) {
                        save();
                        msg("已保存", new Color(120, 255, 120));
                    } else {
                        save();
                        hasSave = SaveManager.exists();
                        state = State.TITLE;
                        menuSel = 0;
                    }
                }
                break;
            case REPORT:
                if (ok && !repeat) respawn();
                break;
            case HELP: {
                boolean leftK = k == KeyEvent.VK_LEFT || k == KeyEvent.VK_A;
                boolean rightK = k == KeyEvent.VK_RIGHT || k == KeyEvent.VK_D;
                if (leftK || upK) helpPage = (helpPage + HELP_TITLES.length - 1) % HELP_TITLES.length;
                else if (rightK || downK) helpPage = (helpPage + 1) % HELP_TITLES.length;
                else if ((ok || esc) && !repeat) closeHelp();
                break;
            }
            default:
        }
    }

    void titleAction(int i) {
        if (hasSave) {
            if (i == 0) {
                state = State.PLAY;
                snapCamera();
            } else if (i == 1) {
                if (!confirmNew) confirmNew = true;
                else {
                    confirmNew = false;
                    newGame(System.nanoTime());
                    openHelp(State.PLAY);
                }
            } else if (i == 2) openHelp(State.TITLE);
            else exitGame();
        } else {
            if (i == 0) {
                newGame(System.nanoTime());
                openHelp(State.PLAY);
            } else if (i == 1) openHelp(State.TITLE);
            else exitGame();
        }
    }

    void exitGame() {
        Window w = SwingUtilities.getWindowAncestor(this);
        if (w != null) w.dispatchEvent(new java.awt.event.WindowEvent(w, java.awt.event.WindowEvent.WINDOW_CLOSING));
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int k = e.getKeyCode();
        if (k < keys.length) keys[k] = false;
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

    void releaseAllKeys() {
        java.util.Arrays.fill(keys, false);
    }
}
