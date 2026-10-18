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
    static final double FUEL_PRICE = 2.0, REPAIR_PRICE = 5.0;
    static final double DIG_BASE = 0.75;     // 出厂钻头挖一格泥土的秒数
    static final double DEATH_FEE = 0.25;    // 爆炸后的救援费:现金的 25%
    static final double FALL_SAFE_ROWS = 4;  // 下落 4 格以内不掉血
    static final double FALL_DMG_PER_ROW = 0.25; // 超过的部分每格扣 0.25 点:10 格 1.5、20 格 4、40 格 9
    static final double MAX_FALL = 650;      // 最大下落速度
    static final double BRAKE = 2200;        // 下落时按↑的额外刹车加速度
    static final double RELIEF_FUEL = 0.4;   // 救济油:补到油箱的 40%
    static final double LOW_FUEL = 0.2;
    static final double METERS_PER_ROW = 3;
    // 深度里程碑:第一次到达给奖金
    static final int[] MILESTONE_M = {100, 250, 500, 750, 1000};
    static final int[] MILESTONE_BONUS = {300, 1200, 6500, 25000, 65000};
    static final String[] MILESTONE_TEXT = {
        "总部:干得漂亮,已经下到 100 米了!奖金 $%s 打到你账上了。",
        "总部:250 米!这一带的矿开始值钱了。奖金 $%s。",
        "总部:500 米,了不起!注意地热,油烧得更快。奖金 $%s。",
        "总部:750 米……我们的仪器都快测不到你了。奖金 $%s。",
        "总部:1000 米!熔岩之心就在下面不远了。奖金 $%s。",
    };
    // 新手指引:各种情况第一次发生时提示一次
    static final int TIP_DIG = 0, TIP_ALARM = 1, TIP_FULL = 2, TIP_LAVA = 3, TIP_GAS = 4, TIP_SELL = 5, TIP_HEAT = 6, TIP_QUAKE = 7;
    static final String[] TIP_TEXT = {
        "往下钻的时候看看燃油条:白线是飞回地面大约要的油,别让油掉到白线以下",
        "燃油报警了!现在就按住 ↑ 往上飞,回地面加油",
        "货舱满了,再挖到的矿会丢掉。回地面去矿石收购站卖钱吧",
        "橙红色的是熔岩,钻进去会被烧伤。绕开它,或者升级散热器",
        "刚才是瓦斯!它看起来像泥土,细看有淡绿色斑点。探测器能提前标出来",
        "卖了钱去改装厂升级,油箱和钻头最要紧",
        "进入地热区:越往下越热,干什么都更费油。深度旁边的温度越高越费油",
        "地震了!头顶掉灰就是碎石要落下来,赶紧挪开",
    };
    static final double QUAKE_ROWS = 100;    // 300 米以下会地震
    static final double QUAKE_MIN = 60, QUAKE_MAX = 120; // 两次地震的间隔(秒)
    static final int ROCK_DRILL_LEVEL = 5;   // 钻头升到第 6 档(钻石级)起能钻岩石
    static final double HEAT_START = 50;     // 150 米以下开始有地热
    static final double HEAT_GAIN = 2.5;     // 到最底层额外增加的油耗倍数(出厂散热器)

    enum State { TITLE, PLAY, SHOP, PAUSE, EXPLODING, REPORT, HELP, ENDING }

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
    // 地震落石
    static final class Rock {
        double x, y, vy, warn, size;
        int quake;
    }
    final List<Rock> rocks = new ArrayList<>();
    double quakeTimer = QUAKE_MIN;
    int quakeCount, rocksSpawned, lastRockHit;
    int menuSel, shopType = -1;
    String shopMsg = "";
    Color shopMsgColor = Color.WHITE;
    double shopMsgTimer, rockMsgCooldown, beepTimer, explodeTimer;
    boolean confirmNew, hasSave;
    String deathReason = "";
    // 屏幕中间的大字消息(挖到宝藏等)
    String centerMsg = "", centerSub = "";
    double centerMsgTimer;
    // 公司无线电(里程碑)和新手指引
    String radioMsg = "", tipMsg = "";
    double radioTimer, tipTimer;
    /** F11 全屏切换,由窗口(Main)提供 */
    Runnable fullscreenToggler;
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
        SaveManager.migrateLegacy();
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
        if (state == State.PLAY || state == State.SHOP) p.playTime += dt;
        if (shopMsgTimer > 0) shopMsgTimer -= dt;
        if (centerMsgTimer > 0) centerMsgTimer -= dt;
        if (radioTimer > 0) radioTimer -= dt;
        if (tipTimer > 0 && state == State.PLAY) tipTimer -= dt;
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
        double heatNow = heat(p.depthRows());

        if (p.digging) {
            p.thrusting = false;
            p.digProgress += dt / p.digTime;
            double k = Math.min(1, p.digProgress);
            p.x = p.digSX + (p.digTX - p.digSX) * k;
            p.y = p.digSY + (p.digTY - p.digSY) * k;
            p.fuel -= FUEL_DRILL * heatNow * dt;
            if (fx.nextDouble() < dt * 40) spawnDigChip();
            if (p.digProgress >= 1) finishDig();
        } else {
            double eng = p.engineMul();
            double maxV = 140 * eng, acc = 650 * eng;
            if (left ^ right) {
                int dir = left ? -1 : 1;
                p.facing = dir;
                p.vx += dir * acc * dt;
                if (p.depthRows() > 0.5) p.fuel -= FUEL_DRIVE * heatNow * dt; // 地面上开车不耗油,飞行照常耗油
            } else {
                double fr = (p.onGround ? 900 : 260) * dt;
                p.vx = Math.abs(p.vx) <= fr ? 0 : p.vx - Math.signum(p.vx) * fr;
            }
            p.vx = Math.max(-maxV, Math.min(maxV, p.vx));
            p.thrusting = up;
            if (up) {
                p.vy -= (GRAVITY + 430 * eng) * dt;
                if (p.vy > 0) p.vy = Math.max(0, p.vy - BRAKE * dt); // 正在往下掉:用力刹车
                p.fuel -= FUEL_THRUST * heatNow * dt;
                if (fx.nextDouble() < dt * 30) spawnSmoke(p.x + Player.W / 2.0, p.y + Player.H, true);
            }
            p.vy += GRAVITY * dt;
            p.vy = Math.max(-130 * eng, Math.min(MAX_FALL, p.vy));

            moveX(dt);
            if (up) cornerAssist(dt, left ^ right ? (left ? -1 : 1) : 0);
            double impact = moveY(dt);
            trackFall(up, impact);
            p.wheelAngle += p.vx * dt / 4;

            if (state == State.PLAY && p.onGround && !up) {
                if (down && !left && !right) tryDigDown();
                else if (left && !right && p.touchL) tryDigSide(-1);
                else if (right && !left && p.touchR) tryDigSide(1);
            }
        }

        // 怠速耗油只在地下发生:地面上停着不耗油
        if (p.depthRows() > 0.5) p.fuel -= FUEL_IDLE * heatNow * dt;
        int depth = (int) Math.floor(p.depthRows());
        if (depth > p.maxDepth) p.maxDepth = depth;
        if (p.hull < p.maxHull() * 0.3 && fx.nextDouble() < dt * 6) spawnSmoke(p.x + 6, p.y, false);

        updateQuake(dt);
        checkMilestones();
        if (p.depthRows() > HEAT_START) tip(TIP_HEAT);
        if (p.cargoCount() >= p.cargoMax()) tip(TIP_FULL);
        if (p.depthRows() > 1 && (p.tips & (1 << TIP_LAVA)) == 0 && lavaNear(3)) tip(TIP_LAVA);

        int alarm = fuelAlarm();
        if (state == State.PLAY && alarm > 0) {
            tip(TIP_ALARM);
            beepTimer -= dt;
            if (beepTimer <= 0) {
                sfx.beep();
                beepTimer = alarm == 2 ? 1.2 : 2.0; // 提醒节奏放缓,不要太急促
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

    /** 地热:150 米以下所有地下油耗乘这个系数,越深越大;散热器能减轻 */
    double heat(double rows) {
        if (rows <= HEAT_START) return 1;
        return 1 + HEAT_GAIN * (rows - HEAT_START) / (World.ROWS - 3 - World.SURFACE - HEAT_START) * (1 - 0.6 * p.radiator());
    }

    /** 地温(℃),只用来显示 */
    int tempC(double heat) {
        return (int) Math.round(20 + (heat - 1) * 60);
    }

    /** 估算从当前深度直线飞回地面需要的油(升),逐格累加地热 */
    double returnFuel() {
        double rows = Math.max(0, p.depthRows());
        double perRow = T / (130 * p.engineMul()) * (FUEL_THRUST + FUEL_IDLE);
        double sum = 0;
        int whole = (int) rows;
        for (int d = 0; d < whole; d++) sum += perRow * heat(d + 0.5);
        sum += perRow * (rows - whole) * heat(rows);
        return sum;
    }

    void checkMilestones() {
        double meters = p.depthRows() * METERS_PER_ROW;
        for (int i = 0; i < MILESTONE_M.length; i++) {
            if (meters < MILESTONE_M[i] || (p.milestones & (1 << i)) != 0) continue;
            p.milestones |= 1 << i;
            p.money += MILESTONE_BONUS[i];
            radioMsg = String.format(MILESTONE_TEXT[i], fmtMoney(MILESTONE_BONUS[i]));
            radioTimer = 5;
            sfx.cash();
        }
    }

    /** 显示一条新手指引(每条只显示一次) */
    void tip(int id) {
        if ((p.tips & (1 << id)) != 0) return;
        p.tips |= 1 << id;
        tipMsg = TIP_TEXT[id];
        tipTimer = 6;
    }

    boolean lavaNear(int rad) {
        int pc = (int) Math.floor((p.x + Player.W / 2.0) / T), pr = (int) Math.floor((p.y + Player.H / 2.0) / T);
        for (int r = pr - rad; r <= pr + rad; r++)
            for (int c = pc - rad; c <= pc + rad; c++)
                if (world.get(c, r) == World.LAVA) return true;
        return false;
    }

    /** 窗口失去焦点:松开所有按键;正在玩就自动暂停 */
    void onFocusLost() {
        releaseAllKeys();
        if (state == State.PLAY) {
            state = State.PAUSE;
            menuSel = 0;
            shopMsgTimer = 0;
        }
    }

    /** 深处每隔一阵地震一次;碎石只是粒子实体,落地就碎,从不写进地图 */
    void updateQuake(double dt) {
        if (p.depthRows() >= QUAKE_ROWS && !p.digging) {
            quakeTimer -= dt;
            if (quakeTimer <= 0) triggerQuake();
        }
        double dmg = 2 + Math.max(0, p.depthRows()) / 80.0;
        for (int i = rocks.size() - 1; i >= 0; i--) {
            Rock k = rocks.get(i);
            if (k.warn > 0) {
                k.warn -= dt;
                if (fx.nextDouble() < dt * 25) {
                    Particle q = part(k.x + (fx.nextDouble() - 0.5) * 16, k.y, (fx.nextDouble() - 0.5) * 10, 20 + fx.nextDouble() * 30,
                        0.8, 2, new Color(170, 150, 130), true);
                    q.drag = 0.97;
                }
                continue;
            }
            k.vy = Math.min(MAX_FALL, k.vy + GRAVITY * dt);
            k.y += k.vy * dt;
            boolean hitCar = k.x + k.size > p.x && k.x - k.size < p.x + Player.W && k.y + k.size > p.y && k.y - k.size < p.y + Player.H;
            boolean hitGround = world.solid((int) Math.floor(k.x / T), (int) Math.floor((k.y + k.size) / T));
            if (hitCar || hitGround) {
                for (int j = 0; j < 10; j++) {
                    double a = fx.nextDouble() * Math.PI;
                    part(k.x, k.y, Math.cos(a) * 60, -Math.sin(a) * 80, 0.6, 2 + fx.nextInt(2), new Color(130, 120, 115), true);
                }
                rocks.remove(i);
                if (hitCar && k.quake != lastRockHit) { // 同一次地震只扣一次血,窄竖井里躲不开也不至于被连砸
                    lastRockHit = k.quake;
                    damage(dmg, "落石");
                } else if (hitCar) shake = Math.max(shake, 4);
                if (state != State.PLAY) return;
            }
        }
    }

    /** 地震:震屏 + 隆隆声;车头顶上有空通道时,从通道顶部掉下 1~3 块碎石 */
    void triggerQuake() {
        quakeTimer = QUAKE_MIN + fx.nextDouble() * (QUAKE_MAX - QUAKE_MIN);
        quakeCount++;
        shake = Math.max(shake, 6);
        sfx.rumble();
        int c = (int) Math.floor((p.x + Player.W / 2.0) / T);
        int r = (int) Math.floor(p.y / T);
        int top = r;
        while (top - 1 >= World.SURFACE && r - (top - 1) <= 6 && !world.solid(c, top - 1)) top--;
        if (top >= r) return; // 头顶紧贴着土,没地方掉东西
        int n = 1 + fx.nextInt(3);
        for (int i = 0; i < n; i++) {
            Rock k = new Rock();
            k.size = 4 + fx.nextDouble() * 3;
            k.x = c * T + T / 2.0 + (fx.nextDouble() - 0.5) * 18;
            k.y = top * T + k.size;
            k.warn = 1.0 + i * 0.25;
            k.quake = quakeCount;
            rocks.add(k);
            rocksSpawned++;
        }
        addText(p.x + Player.W / 2.0, p.y - 10, "地震!小心头顶的落石", new Color(255, 200, 120));
        tip(TIP_QUAKE);
    }

    void drawRocks(Graphics2D g, int ox, int oy) {
        Art.aa(g, true);
        for (Rock k : rocks) {
            if (k.warn > 0) continue;
            double x = k.x - ox, y = k.y - oy, s = k.size;
            Path2D.Double sh = new Path2D.Double();
            sh.moveTo(x - s, y - s * 0.3);
            sh.lineTo(x - s * 0.4, y - s);
            sh.lineTo(x + s * 0.7, y - s * 0.8);
            sh.lineTo(x + s, y + s * 0.2);
            sh.lineTo(x + s * 0.3, y + s);
            sh.lineTo(x - s * 0.7, y + s * 0.7);
            sh.closePath();
            g.setColor(new Color(120, 112, 108));
            g.fill(sh);
            g.setColor(new Color(60, 55, 52));
            g.draw(sh);
        }
    }

    /** 0 = 正常, 1 = 燃油不足(该往回走了), 2 = 危险(只剩勉强够回去的油) */
    int fuelAlarm() {
        double ff = p.fuel / p.maxFuel();
        // 已经安全停在地面上:地面开车不耗油,不用再报警;一旦离地飞起来或钻下去就恢复报警
        if (p.onGround && Math.abs(p.depthRows()) < 0.5 && !p.digging) return 0;
        if (p.depthRows() > 0.5) {
            // 地下:按"直线飞回地面要多少油"判断。回程要 5 升,剩 6 升左右开始提醒
            double ret = returnFuel();
            if (p.fuel < ret * 1.05 + 0.15) return 2;
            if (p.fuel < Math.max(ret * 1.2, ret + 0.3)) return 1;
            return 0;
        }
        // 在地面上方飞:按油箱比例
        if (ff < 0.1) return 2;
        if (ff < LOW_FUEL) return 1;
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

    /**
     * 摔伤按下落高度算。fallStartY 记录"相当于从哪个高度开始自由落体":
     * 往上飞或站在地上时重置;下落中按↑刹车,就按剩余速度折算成等效高度 v²/2g。
     */
    void trackFall(boolean up, double impact) {
        if (p.fallStartY > p.y) p.fallStartY = p.y; // 起点不可能在车下面(瞬移/读档后的保险)
        if (p.onGround) {
            if (impact > 0) {
                double rows = (p.y - p.fallStartY) / T;
                if (rows > FALL_SAFE_ROWS) damage((rows - FALL_SAFE_ROWS) * FALL_DMG_PER_ROW, String.format("摔伤(%.0f 格)", rows));
            }
            p.fallStartY = p.y;
        } else if (p.vy <= 0) {
            p.fallStartY = p.y;
        } else if (up) {
            p.fallStartY = Math.max(p.fallStartY, p.y - p.vy * p.vy / (2 * GRAVITY));
        }
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
        if (t >= World.ORE && Mineral.isProtected(t - World.ORE) && p.cargoCount() >= p.cargoMax()) {
            if (rockMsgCooldown <= 0) {
                addText(p.x + Player.W / 2.0, p.y - 6, "货舱满了,装不下" + Mineral.ALL[t - World.ORE].name + "!先回去卖矿", new Color(255, 160, 40));
                rockMsgCooldown = 1.5;
            }
            return;
        }
        boolean rockOk = t == World.ROCK && p.level[Upgrades.DRILL] >= ROCK_DRILL_LEVEL;
        if (!World.diggable(t) && !rockOk) {
            if (rockMsgCooldown <= 0) {
                String m = t == World.ROCK ? "岩石太硬,钻不动!(用炸药,或把钻头升到钻石级)"
                    : t == World.CONCRETE ? "这是商店的水泥地基" : "这里是基岩,再也挖不下去了";
                addText(p.x + Player.W / 2.0, p.y - 6, m, new Color(255, 220, 150));
                rockMsgCooldown = 1.5;
            }
            return;
        }
        p.digging = true;
        if (dir == 0) tip(TIP_DIG);
        p.fallStartY = p.y;
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
        p.fallStartY = p.y;
        double cx = c * T + T / 2.0, cy = r * T + T / 2.0;
        int m = World.mineralOf(t);
        if (m >= 0) {
            Mineral mi = Mineral.ALL[m];
            if (p.cargoCount() < p.cargoMax()) {
                p.cargo[m]++;
                addText(cx, cy - 14, "+" + mi.name + "  $" + mi.value, brighten(mi.color));
                sfx.pick();
                spawnPickup(cx, cy, mi);
                if (m == Mineral.HEART) addText(cx, cy - 34, "传说中的熔岩之心!!", new Color(255, 160, 40));
                if (Mineral.isTreasure(m)) {
                    centerMsg = "发现宝藏:" + mi.name + "   $" + fmtMoney(mi.value);
                    centerSub = Mineral.TREASURE_TEXT[m - Mineral.FIRST_V4];
                    centerMsgTimer = 4;
                    spawnPickup(cx, cy, mi);
                    for (int i = 0; i < 30; i++) spawnSpark(cx, cy);
                }
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
            tip(TIP_GAS);
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
        p.deaths++;
        p.money -= fee;
        // 熔岩之心和宝藏不会永久丢失:放回它原来的深度
        StringBuilder back = new StringBuilder();
        for (int m = 0; m < p.cargo.length; m++) {
            if (!Mineral.isProtected(m) || p.cargo[m] == 0) continue;
            for (int k = 0; k < p.cargo[m]; k++) world.placeProtected(m, fx);
            if (back.length() > 0) back.append("、");
            back.append(Mineral.ALL[m].name);
        }
        java.util.Arrays.fill(p.cargo, 0);
        report.clear();
        report.add("原因:" + deathReason);
        report.add("丢失货物:" + lost + " 件(价值 $" + fmtMoney(lostVal) + ")");
        if (back.length() > 0) report.add(back + " 没有毁掉,回到了地底深处。");
        report.add("救援费:$" + fmtMoney(fee) + "(现金的 25%)");
        report.add("救援队已把新车送到地面,油箱加满。");
        state = State.REPORT;
    }

    void respawn() {
        p.toSpawn();
        p.fuel = p.maxFuel();
        p.hull = p.maxHull();
        particles.clear();
        rocks.clear();
        state = State.PLAY;
        centerMsgTimer = 0;
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
                double add = p.maxFuel() * 0.5;
                p.fuel = Math.min(p.maxFuel(), p.fuel + add);
                addText(cx, p.y - 6, String.format("+%.0f 升燃油", add), new Color(120, 255, 120));
                sfx.buy();
                break;
            case 1:
                if (p.hull >= p.maxHull() - 0.01) return;
                double fix = p.maxHull() * 0.5;
                p.hull = Math.min(p.maxHull(), p.hull + fix);
                addText(cx, p.y - 6, String.format("车身 +%.0f", fix), new Color(120, 220, 255));
                sfx.buy();
                break;
            case 2: {
                if (p.digging) return;
                int c = (int) Math.floor(cx / T), r = (int) Math.floor(cy / T);
                for (int dr = -1; dr <= 1; dr++)
                    for (int dc = -1; dc <= 1; dc++) {
                        byte t = world.get(c + dc, r + dr);
                        boolean keep = t >= World.ORE && Mineral.isProtected(t - World.ORE);
                        if (t != World.BEDROCK && t != World.CONCRETE && !keep && r + dr >= World.SURFACE)
                            world.set(c + dc, r + dr, World.AIR);
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
                if (canGetRelief()) {
                    list.add(new MenuItem("领取救济油(免费)   补到 " + Math.round(RELIEF_FUEL * 100) + "%",
                        "身无分文、货舱也是空的?先免费给你一点油,下去挖一趟再说。", true, this::relief));
                }
                double need = Math.max(0, p.maxFuel() - p.fuel);
                int cost = (int) Math.ceil(need * FUEL_PRICE);
                list.add(new MenuItem(String.format("加满燃油     %.1f 升     $%s", need, fmtMoney(cost)),
                    "油价 $2 / 升。钱不够时会尽量多加。", need > 0.05, this::buyFuel));
                double hn = Math.max(0, p.maxHull() - p.hull);
                int hc = (int) Math.ceil(hn * REPAIR_PRICE);
                list.add(new MenuItem(String.format("修复车身     %.1f 点     $%s", hn, fmtMoney(hc)),
                    "维修费 $" + (int) REPAIR_PRICE + " / 点。", hn > 0.05, this::repair));
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

    boolean canGetRelief() {
        return p.money < FUEL_PRICE && p.cargoCount() == 0 && p.fuel < p.maxFuel() * RELIEF_FUEL - 0.05;
    }

    void relief() {
        if (!canGetRelief()) return;
        p.fuel = p.maxFuel() * RELIEF_FUEL;
        msg("领到救济油,油箱补到 " + Math.round(RELIEF_FUEL * 100) + "%", new Color(120, 255, 120));
        sfx.buy();
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
        p.totalEarned += v;
        java.util.Arrays.fill(p.cargo, 0);
        msg("卖出 " + n + " 件矿石,收入 $" + fmtMoney(v), new Color(255, 215, 80));
        tip(TIP_SELL);
        sfx.cash();
        if (!p.won && heartGoal() > 0 && p.hearts >= heartGoal()) {
            p.won = true;
            shopType = -1;
            state = State.ENDING;
            save();
        }
    }

    /** 通关目标:卖出全部熔岩之心(爆炸丢失的会回到地底,所以总数不变) */
    int heartGoal() {
        return World.HEARTS;
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
        rocks.clear();
        quakeTimer = QUAKE_MIN;
        state = State.PLAY;
        centerMsgTimer = 0;
        snapCamera();
        save();
        hasSave = SaveManager.exists();
    }

    void save() {
        if (state == State.EXPLODING || state == State.REPORT) return;
        SaveManager.save(world, p);
    }

    void saveOnExit() {
        if (state == State.PLAY || state == State.SHOP || state == State.PAUSE || state == State.ENDING
            || state == State.HELP) SaveManager.save(world, p);
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
        drawOreEffects(g, ox, oy);
        drawProbe(g, ox, oy);
        drawRocks(g, ox, oy);
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
            case ENDING:
                drawHud(g);
                drawEnding(g);
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

    static boolean twinkles(Mineral m) {
        return m.value >= 140 || m.style == Mineral.CRYSTAL || m.style == Mineral.CUBE;
    }

    /**
     * 矿石特效,画在黑暗遮罩之后:在地下也能看到远处的矿在黑暗里闪(离车越远越暗)。
     */
    void drawOreEffects(Graphics2D g, int ox, int oy) {
        int c0 = Math.max(0, ox / T), c1 = Math.min(World.COLS - 1, (ox + VW) / T);
        int r0 = Math.max(World.SURFACE, oy / T), r1 = Math.min(World.ROWS - 1, (oy + VH) / T);
        double pcx = p.x + Player.W / 2.0, pcy = p.y + Player.H / 2.0;
        boolean dark = p.depthRows() > 3;
        Art.aa(g, true);
        Shape oc = g.getClip();
        for (int r = r0; r <= r1; r++)
            for (int c = c0; c <= c1; c++) {
                int m = World.mineralOf(world.get(c, r));
                if (m < 0) continue;
                Mineral mi = Mineral.ALL[m];
                int x = c * T - ox, y = r * T - oy;
                double dist = Math.hypot(c * T + 16 - pcx, r * T + 16 - pcy);
                double lit = dark ? Math.max(0.3, Math.min(1, 1 - (dist - 160) / 320)) : 1;
                int h = Textures.hash(c * 7 + 3, r * 13 + 1);
                double seed = (h % 1000) / 1000.0;
                switch (mi.fx) {
                    case Mineral.FX_OPAL: {
                        float hue = (float) ((time * 0.25 + seed) % 1.0);
                        Color sh = Color.getHSBColor(hue, 0.7f, 1f);
                        g.setColor(new Color(sh.getRed(), sh.getGreen(), sh.getBlue(), (int) (70 * lit)));
                        g.fill(new Ellipse2D.Double(x + 6, y + 8, 20, 16));
                        break;
                    }
                    case Mineral.FX_METEOR:
                        glow(g, x, y, 18, new Color(90, 220, 255), 110 * pulse(2.2, seed) * lit);
                        break;
                    case Mineral.FX_AMBER:
                        glow(g, x, y, 17, new Color(255, 180, 60), 70 * pulse(1.5, seed) * lit);
                        break;
                    case Mineral.FX_MOON:
                        glow(g, x, y, 20, new Color(170, 205, 255), (40 + 80 * pulse(1.1, seed)) * lit);
                        break;
                    case Mineral.FX_PINK:
                        glow(g, x, y, 19, new Color(255, 130, 200), (30 + 70 * pulse(1.8, seed)) * lit);
                        break;
                    case Mineral.FX_TREASURE:
                        glow(g, x, y, 20, new Color(255, 210, 90), (40 + 70 * pulse(1.3, seed)) * lit);
                        break;
                    case Mineral.FX_ALIEN:
                        glow(g, x, y, 22, new Color(90, 255, 160), (50 + 100 * pulse(2.6, seed)) * lit);
                        break;
                    case Mineral.FX_HEART: {
                        double k = pulse(3, 0);
                        float rad = (float) (22 + 8 * k);
                        g.setPaint(new RadialGradientPaint(x + 16f, y + 16f, rad, new float[]{0f, 1f},
                            new Color[]{new Color(255, 150, 40, (int) (150 * k + 60)), new Color(255, 80, 0, 0)}));
                        g.fill(new Ellipse2D.Double(x + 16 - rad, y + 16 - rad, rad * 2, rad * 2));
                        break;
                    }
                    case Mineral.FX_FUMES:
                        // 淡黄色烟雾慢慢往上飘
                        for (int k = 0; k < 2; k++) {
                            double ph = (time * 0.45 + k * 0.5 + seed) % 1.0;
                            double px = x + 9 + k * 12 + Math.sin(ph * 6 + k) * 2.5, py = y + 22 - ph * 30;
                            int a = (int) (Math.sin(ph * Math.PI) * 75 * lit);
                            g.setColor(new Color(240, 225, 90, a));
                            g.fill(new Ellipse2D.Double(px - 3 - ph * 2, py - 2.5 - ph, 6 + ph * 4, 5 + ph * 2));
                        }
                        break;
                    case Mineral.FX_SHEEN: {
                        // 一道光斜着扫过
                        double ph = (time * 0.4 + seed * 2) % 2.0;
                        if (ph < 1) {
                            double bx = x - 10 + ph * 52;
                            g.clipRect(x, y, T, T);
                            g.setColor(new Color(255, 255, 255, (int) (70 * lit)));
                            g.fill(new Polygon(new int[]{(int) bx, (int) bx + 6, (int) bx - 6, (int) bx - 12},
                                new int[]{y, y, y + T, y + T}, 4));
                            g.setClip(oc);
                        }
                        break;
                    }
                    case Mineral.FX_LIGHTNING: {
                        glow(g, x, y, 17, new Color(150, 130, 255), 45 * lit);
                        // 每 1.6 秒左右噼啪一下,每次形状不同
                        double period = 1.6;
                        double ph = (time + seed * period) % period;
                        if (ph < 0.18) {
                            long cycle = (long) ((time + seed * period) / period);
                            Random lr = new Random(h * 31L + cycle);
                            double ax = x + 4 + lr.nextInt(10), ay = y + 4 + lr.nextInt(24);
                            double bx2 = x + 18 + lr.nextInt(10), by2 = y + 4 + lr.nextInt(24);
                            Path2D.Double bolt = new Path2D.Double();
                            bolt.moveTo(ax, ay);
                            for (int i = 1; i < 5; i++) {
                                double t = i / 5.0;
                                bolt.lineTo(ax + (bx2 - ax) * t + (lr.nextDouble() - 0.5) * 7, ay + (by2 - ay) * t + (lr.nextDouble() - 0.5) * 7);
                            }
                            bolt.lineTo(bx2, by2);
                            int a = (int) (255 * Math.sin(ph / 0.18 * Math.PI) * lit);
                            g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                            g.setColor(new Color(160, 140, 255, a / 2));
                            g.draw(bolt);
                            g.setStroke(new BasicStroke(1.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                            g.setColor(new Color(235, 230, 255, a));
                            g.draw(bolt);
                            g.setStroke(new BasicStroke(1f));
                        }
                        break;
                    }
                    case Mineral.FX_FLAME: {
                        double flick = 0.6 + 0.25 * Math.sin(time * 9 + seed * 10) + 0.15 * Math.sin(time * 23 + seed * 3);
                        glow(g, x, y, 19, new Color(255, 120, 30), 100 * flick * lit);
                        for (int k = 0; k < 3; k++) {
                            double ph = (time * 0.8 + k / 3.0 + seed) % 1.0;
                            double px = x + 8 + k * 8 + Math.sin(ph * 9 + k) * 2, py = y + 24 - ph * 28;
                            int a = (int) ((1 - ph) * 230 * lit);
                            g.setColor(new Color(255, 200 - (int) (ph * 120), 60, a));
                            g.fill(new Rectangle2D.Double(px, py, 1.8, 1.8));
                        }
                        break;
                    }
                    case Mineral.FX_SWIRL: {
                        glow(g, x, y, 22, new Color(150, 80, 255), (60 + 60 * pulse(1.3, seed)) * lit);
                        for (int k = 0; k < 3; k++) {
                            double a = time * 1.6 + k * Math.PI * 2 / 3 + seed * 6;
                            double px = x + 16 + Math.cos(a) * 13, py = y + 16 + Math.sin(a) * 6;
                            g.setColor(new Color(220, 200, 255, (int) (220 * lit)));
                            g.fill(new Ellipse2D.Double(px - 1.3, py - 1.3, 2.6, 2.6));
                        }
                        break;
                    }
                    default:
                }
                if (twinkles(mi)) twinkle(g, h, x, y, lit, twinkleRate(mi));
            }
        g.setClip(oc);
        Art.aa(g, false);
    }

    /** 探测器范围内能标出来的东西(矿、宝藏、瓦斯) */
    boolean probeVisible(byte t) {
        return t == World.GAS || t >= World.ORE;
    }

    int probeTargets() {
        int n = 0, rad = p.probeRadius();
        if (rad <= 0) return 0;
        int pc = (int) Math.floor((p.x + Player.W / 2.0) / T), pr = (int) Math.floor((p.y + Player.H / 2.0) / T);
        for (int r = pr - rad; r <= pr + rad; r++)
            for (int c = pc - rad; c <= pc + rad; c++)
                if ((c - pc) * (c - pc) + (r - pr) * (r - pr) <= rad * rad && r > World.SURFACE && probeVisible(world.get(c, r))) n++;
        return n;
    }

    /** 探测器:在黑暗里标出范围内的矿(彩色框)和瓦斯(绿色感叹号),外加一圈慢慢扩散的扫描波 */
    void drawProbe(Graphics2D g, int ox, int oy) {
        int rad = p.probeRadius();
        if (rad <= 0 || p.depthRows() < 1) return;
        Art.aa(g, true);
        double pcx = p.x + Player.W / 2.0, pcy = p.y + Player.H / 2.0;
        double ph = (time * 0.5) % 1.0;
        g.setStroke(new BasicStroke(1.5f));
        g.setColor(new Color(120, 230, 255, (int) (70 * (1 - ph))));
        double sr = ph * rad * T;
        g.draw(new Ellipse2D.Double(pcx - ox - sr, pcy - oy - sr, sr * 2, sr * 2));
        int pc = (int) Math.floor(pcx / T), pr = (int) Math.floor(pcy / T);
        g.setFont(fontB(16));
        for (int r = pr - rad; r <= pr + rad; r++)
            for (int c = pc - rad; c <= pc + rad; c++) {
                double d2 = (c - pc) * (c - pc) + (r - pr) * (r - pr);
                if (d2 > rad * rad || r <= World.SURFACE) continue;
                byte t = world.get(c, r);
                if (!probeVisible(t)) continue;
                int a = (int) (200 * (1 - 0.6 * Math.sqrt(d2) / rad));
                int x = c * T - ox, y = r * T - oy;
                if (t == World.GAS) {
                    g.setColor(new Color(110, 255, 110, a));
                    g.drawRoundRect(x + 4, y + 4, T - 8, T - 8, 8, 8);
                    g.drawString("!", x + 13, y + 22);
                } else {
                    Mineral mi = Mineral.ALL[t - World.ORE];
                    Color mc = brighten(mi.color);
                    g.setColor(new Color(mc.getRed(), mc.getGreen(), mc.getBlue(), a));
                    g.drawRoundRect(x + 3, y + 3, T - 6, T - 6, 6, 6);
                    if (Mineral.isProtected(t - World.ORE)) {
                        g.setColor(new Color(255, 215, 80, a));
                        g.drawRoundRect(x + 1, y + 1, T - 2, T - 2, 8, 8);
                    }
                }
            }
        g.setStroke(new BasicStroke(1f));
    }

    double pulse(double speed, double seed) {
        return 0.5 + 0.5 * Math.sin(time * speed + seed * 6.28);
    }

    static void glow(Graphics2D g, int x, int y, float rad, Color c, double alpha) {
        int a = (int) Math.max(0, Math.min(255, alpha));
        if (a <= 2) return;
        g.setPaint(new RadialGradientPaint(x + 16f, y + 16f, rad, new float[]{0f, 1f},
            new Color[]{new Color(c.getRed(), c.getGreen(), c.getBlue(), a), new Color(c.getRed(), c.getGreen(), c.getBlue(), 0)}));
        g.fill(new Rectangle2D.Double(x + 16 - rad, y + 16 - rad, rad * 2, rad * 2));
    }

    /** 一闪一闪的十字星光;贵的矿闪得更勤 */
    /** 闪光频率:$1000 以上闪得勤;货舱满了再翻一倍,让人看到"可惜了" */
    int twinkleRate(Mineral mi) {
        int rate = mi.value >= 1000 ? 2 : 1;
        return p.cargoCount() >= p.cargoMax() ? rate * 2 : rate;
    }

    void twinkle(Graphics2D g, int h, int x, int y, double lit, int rate) {
        double period = rate >= 2 ? 2.2 : 3.2;
        double phase = (time * 0.6 * rate + (h % 1000) / 1000.0 * period) % period;
        if (phase > 0.4) return;
        double k = Math.sin(phase / 0.4 * Math.PI);
        double sx = x + 6 + (h >> 4) % 20, sy = y + 6 + (h >> 9) % 20, len = 2 + 5 * k;
        int a = (int) (240 * k * lit);
        g.setColor(new Color(255, 255, 255, a / 3));
        g.fill(new Ellipse2D.Double(sx - len * 0.8, sy - len * 0.8, len * 1.6, len * 1.6));
        g.setColor(new Color(255, 255, 255, a));
        g.fill(new Rectangle2D.Double(sx - len, sy - 0.6, len * 2, 1.2));
        g.fill(new Rectangle2D.Double(sx - 0.6, sy - len, 1.2, len * 2));
        g.fill(new Ellipse2D.Double(sx - 1.4, sy - 1.4, 2.8, 2.8));
    }

    /** 挖到矿:炸开一圈矿石颜色的亮晶晶粒子,越贵越多 */
    void spawnPickup(double cx, double cy, Mineral mi) {
        int n = 10 + (int) (Math.log10(mi.value) * 6);
        Color base = brighten(mi.color);
        for (int i = 0; i < n; i++) {
            double a = fx.nextDouble() * Math.PI * 2, s = 40 + fx.nextDouble() * 110;
            Color c = i % 3 == 0 ? Color.WHITE : base;
            Particle q = part(cx, cy, Math.cos(a) * s, Math.sin(a) * s - 30, 0.5 + fx.nextDouble() * 0.4, 2 + fx.nextInt(2), c, false);
            q.drag = 0.92;
        }
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
        double hz = heat(p.depthRows());
        if (hz > 1.001) {
            g.setColor(hz > 2.5 ? new Color(255, 80, 60) : new Color(255, 165, 60));
            g.drawString(tempC(hz) + "℃", 196, 19);
        }
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
        g.drawString("货舱  $" + fmtMoney(p.cargoValue()), 690, 19);
        g.setFont(fontB(17));
        boolean full = p.cargoCount() >= p.cargoMax();
        g.setColor(full ? new Color(255, 90, 80) : Color.WHITE);
        g.drawString(p.cargoCount() + " / " + p.cargoMax() + (full ? " 满" : ""), 690, 39);

        g.setFont(font(12));
        g.setColor(new Color(220, 220, 220));
        g.drawString("F 油桶×" + p.items[0] + "   R 修理×" + p.items[1], 790, 20);
        g.drawString("X 炸药×" + p.items[2] + "   T 传送×" + p.items[3], 790, 39);
        drawMinimap(g);
    }

    BufferedImage miniImg;
    int miniVersion = -1;
    static final int MINI_X = VW - 30, MINI_Y = 58, MINI_W = 24, MINI_H = VH - 58 - 30;

    /** 右侧小地图:整条深度的标尺,画出挖过的地方、车的位置、里程碑和地热线 */
    void drawMinimap(Graphics2D g) {
        int rows = World.ROWS - World.SURFACE;
        if (miniImg == null || miniVersion != world.mapVersion) {
            if (miniImg == null) miniImg = new BufferedImage(World.COLS, rows, BufferedImage.TYPE_INT_ARGB);
            for (int r = 0; r < rows; r++)
                for (int c = 0; c < World.COLS; c++)
                    miniImg.setRGB(c, r, world.isDug(c, r + World.SURFACE) ? 0xFFE8D0A0 : 0x00000000);
            miniVersion = world.mapVersion;
        }
        int x = MINI_X, y = MINI_Y, w = MINI_W, h = MINI_H;
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRoundRect(x - 3, y - 3, w + 6, h + 6, 6, 6);
        g.setColor(new Color(60, 40, 30, 200));
        g.fillRect(x, y, w, h);
        double hs = h / (double) (World.ROWS - 3 - World.SURFACE);
        // 地热区
        int hy = y + (int) (HEAT_START * hs);
        g.setColor(new Color(255, 120, 40, 40));
        g.fillRect(x, hy, w, y + h - hy);
        g.drawImage(miniImg, x, y, w, (int) (rows * hs), null);
        g.setFont(font(9));
        for (int i = 0; i < MILESTONE_M.length; i++) {
            int my = y + (int) (MILESTONE_M[i] / METERS_PER_ROW * hs);
            boolean got = (p.milestones & (1 << i)) != 0;
            String lab = String.valueOf(MILESTONE_M[i]);
            g.setColor(got ? new Color(120, 255, 170, 200) : new Color(200, 200, 200, 140));
            g.drawLine(x - 4, my, x, my);
            g.drawString(lab, x - 5 - g.getFontMetrics().stringWidth(lab), my + 3);
        }
        g.setColor(new Color(255, 150, 60, 200));
        g.drawLine(x, hy, x + w, hy);
        // 车的位置
        double d = Math.max(0, p.depthRows());
        int vy = y + (int) Math.min(h, d * hs), vx = x + (int) ((p.x + Player.W / 2.0) / (World.COLS * T) * w);
        boolean blink = ((int) (time * 3)) % 2 == 0;
        g.setColor(blink ? new Color(255, 230, 60) : new Color(255, 140, 40));
        g.fillRect(vx - 2, vy - 2, 5, 5);
        g.setColor(new Color(255, 255, 255, 160));
        g.drawRect(x - 1, y - 1, w + 1, h + 1);
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
        if (centerMsgTimer > 0 && (state == State.PLAY || state == State.EXPLODING)) {
            int a = (int) (255 * Math.min(1, centerMsgTimer / 0.6));
            g.setFont(fontB(28));
            int w1 = g.getFontMetrics().stringWidth(centerMsg);
            g.setFont(font(17));
            int w2 = g.getFontMetrics().stringWidth(centerSub);
            int w = Math.max(w1, w2) + 60, bx = (VW - w) / 2, by = 170;
            g.setColor(new Color(20, 14, 6, a * 200 / 255));
            g.fillRoundRect(bx, by, w, 84, 14, 14);
            g.setColor(new Color(255, 205, 70, a));
            g.drawRoundRect(bx, by, w, 84, 14, 14);
            g.setFont(fontB(28));
            g.drawString(centerMsg, (VW - w1) / 2, by + 38);
            g.setFont(font(17));
            g.setColor(new Color(235, 225, 200, a));
            g.drawString(centerSub, (VW - w2) / 2, by + 68);
        }
        if (radioTimer > 0 && state == State.PLAY) {
            int a = (int) (255 * Math.min(1, radioTimer / 0.6));
            g.setFont(fontB(15));
            int w = g.getFontMetrics().stringWidth(radioMsg) + 60, bx = 12, by = 58;
            g.setColor(new Color(10, 25, 20, a * 210 / 255));
            g.fillRoundRect(bx, by, w, 34, 10, 10);
            g.setColor(new Color(120, 255, 170, a));
            g.drawRoundRect(bx, by, w, 34, 10, 10);
            // 小天线图标
            g.setStroke(new BasicStroke(2f));
            g.drawLine(bx + 20, by + 28, bx + 20, by + 14);
            g.drawArc(bx + 13, by + 8, 14, 12, 30, 120);
            g.drawArc(bx + 9, by + 4, 22, 18, 30, 120);
            g.setStroke(new BasicStroke(1f));
            g.drawString(radioMsg, bx + 42, by + 23);
        }
        if (tipTimer > 0 && state == State.PLAY) {
            int a = (int) (255 * Math.min(1, tipTimer / 0.6));
            g.setFont(font(15));
            String t = "提示:" + tipMsg;
            int w = g.getFontMetrics().stringWidth(t) + 32, bx = (VW - w) / 2, by = VH - 68;
            g.setColor(new Color(0, 0, 0, a * 190 / 255));
            g.fillRoundRect(bx, by, w, 34, 10, 10);
            g.setColor(new Color(150, 210, 255, a));
            g.drawRoundRect(bx, by, w, 34, 10, 10);
            g.setColor(new Color(235, 245, 255, a));
            g.drawString(t, bx + 16, by + 23);
        }
        // 货舱满了:常驻提醒
        if (state == State.PLAY && p.cargoCount() >= p.cargoMax() && p.depthRows() > 0.5) {
            String t = "货舱满了!再挖到的矿会丢掉 —— 回地面卖矿吧";
            g.setFont(fontB(17));
            int w = g.getFontMetrics().stringWidth(t) + 28;
            int bx = (VW - w) / 2, by = 122;
            g.setColor(new Color(0, 0, 0, 170));
            g.fillRoundRect(bx, by, w, 32, 10, 10);
            g.setColor(new Color(255, 170, 60));
            g.drawRoundRect(bx, by, w, 32, 10, 10);
            g.drawString(t, bx + 14, by + 22);
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
                List<Integer> kinds = new ArrayList<>();
                for (int i : Mineral.BY_VALUE) if (p.cargo[i] > 0) kinds.add(i);
                int cols = kinds.size() > 12 ? 3 : kinds.size() > 6 ? 2 : 1;
                int rows = (kinds.size() + cols - 1) / cols;
                int colW = cols == 3 ? 202 : 305, lh = cols == 1 ? 24 : 22;
                g.setFont(font(cols == 3 ? 13 : cols == 2 ? 14 : 15));
                for (int k = 0; k < kinds.size(); k++) {
                    int i = kinds.get(k);
                    Mineral mi = Mineral.ALL[i];
                    int cx = x + 30 + (k / rows) * colW, cy = ly + (k % rows) * lh;
                    g.drawImage(tex.ore[Textures.band(World.SURFACE + mi.peakDepth)][i][0], cx - 2, cy - 15, 18, 18, null);
                    g.setColor(Color.WHITE);
                    g.drawString(mi.name, cx + 22, cy);
                    g.drawString("×" + p.cargo[i], cx + (cols == 1 ? 148 : cols == 2 ? 100 : 82), cy);
                    if (cols == 1) g.drawString("单价 $" + fmtMoney(mi.value), cx + 228, cy);
                    g.setColor(new Color(255, 214, 70));
                    g.drawString("$" + fmtMoney((long) mi.value * p.cargo[i]), cx + (cols == 1 ? 398 : cols == 2 ? 160 : 116), cy);
                }
                ly += rows * lh + 10;
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

    static final String[] HELP_TITLES = {"操作", "怎么玩", "矿石价目表", "危险", "商店与升级", "宝藏与奖励"};

    /** 说明页内容;"#"开头的行是小标题 */
    String[] helpLines(int page) {
        switch (page) {
            case 0:
                return new String[]{
                    "#开车",
                    "← →  (或 A D)    左右行驶",
                    "↑    (或 W)      喷气往上飞(很费油)",
                    "↓    (或 S)      向下钻",
                    "往下掉的时候按住 ↑  可以快速刹车(能减轻摔伤)",
                    "贴着墙按住 ← / →  横着钻(不能往上钻)",
                    "#地面上",
                    "停在商店门口按 回车(或 E)  进店",
                    "#道具(先在道具店买)",
                    "F 备用油桶    R 修理包    X 炸药    T 紧急传送",
                    "#其它",
                    "Esc  暂停 / 存档 / 看说明        M  静音        F11  全屏",
                    "窗口可以拉大、最大化;切到别的窗口时游戏会自动暂停",
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
                    "150 米以下有地热,越深越费油;右边的竖条小地图显示你在哪、挖过哪里",
                    "地底最深处藏着 6 颗「熔岩之心」—— 全部卖掉就通关!",
                };
            case 3:
                return new String[]{
                    "#燃油耗尽 或 车身归零 → 车会爆炸!",
                    "货全部丢失,扣现金 25% 救援费;新车送回地面,油加满、车修好",
                    "货舱里的熔岩之心和宝藏不会毁掉,会回到地底深处",
                    "#岩石(灰色方块)",
                    "钻不动:绕开、用炸药炸,或把钻头升到钻石级",
                    "#熔岩(橙红色)",
                    "钻进去会被烧伤,越深伤害越高;「散热器」可以减伤",
                    "#瓦斯(像普通泥土,细看有淡绿色斑点)",
                    "一挖就爆炸,扣车身并炸开周围的土;「探测器」能提前标出来",
                    "#摔落",
                    "掉落超过 4 格会摔伤(10 格扣 1.5、20 格扣 4);快落地时按 ↑ 刹车能减轻",
                    "#地热(150 米以下)",
                    "越深越热、越费油(最深处约 3.5 倍);深度旁显示温度,「散热器」能减轻",
                    "#地震落石(300 米以下)",
                    "不时会地震:头顶掉灰就是碎石要落下来了,赶紧挪开",
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
        else if (helpPage == 5) drawTreasureHelp(g, x + 36, ly);
        else {
            String[] lines = helpLines(helpPage);
            int heads = 0;
            for (String line : lines) if (line.startsWith("#")) heads++;
            int lh = Math.min(32, (384 - heads * 4) / lines.length); // 行多时压缩行距,免得超出面板
            for (String line : lines) {
                if (line.startsWith("#")) {
                    ly += 4;
                    g.setFont(fontB(17));
                    g.setColor(new Color(255, 200, 80));
                    g.drawString(line.substring(1), x + 36, ly);
                } else {
                    g.setFont(font(16));
                    g.setColor(Color.WHITE);
                    g.drawString(line, x + 52, ly);
                }
                ly += lh;
            }
        }
        g.setFont(font(13));
        g.setColor(new Color(150, 150, 150));
        g.drawString("← → 翻页 (" + (helpPage + 1) + "/" + HELP_TITLES.length + ")      Esc / 回车 返回", x + 28, y + h - 20);
    }

    void drawMineralTable(Graphics2D g, int x, int y) {
        List<Integer> list = new ArrayList<>();
        for (int i : Mineral.BY_VALUE) if (!Mineral.isTreasure(i)) list.add(i); // 宝藏另有一页
        int n = list.size(), rows = (n + 2) / 3;
        y -= 22;
        for (int k = 0; k < n; k++) {
            int i = list.get(k);
            Mineral m = Mineral.ALL[i];
            int cx = x + (k / rows) * 226, cy = y + (k % rows) * 40;
            int b = Textures.band(World.SURFACE + Math.max(1, m.peakDepth));
            g.drawImage(tex.ore[b][i][0], cx, cy, 34, 34, null);
            g.setFont(fontB(15));
            g.setColor(Color.WHITE);
            g.drawString(m.name, cx + 42, cy + 15);
            g.setColor(new Color(255, 214, 70));
            g.drawString("$" + fmtMoney(m.value), cx + 122, cy + 15);
            g.setFont(font(12));
            g.setColor(new Color(190, 190, 190));
            String d = i == Mineral.HEART ? "最底部,全图仅 " + World.HEARTS + " 颗" : "常见于约 " + Math.round(m.peakDepth * METERS_PER_ROW) + " 米";
            g.drawString(d, cx + 42, cy + 32);
        }
    }

    /** 说明书「宝藏与奖励」页:四种宝藏和深度里程碑 */
    void drawTreasureHelp(Graphics2D g, int x, int y) {
        g.setFont(fontB(17));
        g.setColor(new Color(255, 200, 80));
        g.drawString("地下宝藏(全图数量有限,挖到一个少一个;爆炸丢了会回到地底)", x, y);
        y += 16;
        for (int k = 0; k < Mineral.TREASURES; k++) {
            int m = Mineral.FIRST_V4 + k;
            Mineral mi = Mineral.ALL[m];
            int cy = y + k * 52;
            g.drawImage(tex.ore[Textures.band(World.SURFACE + mi.peakDepth)][m][0], x + 10, cy, 44, 44, null);
            g.setFont(fontB(16));
            g.setColor(Color.WHITE);
            g.drawString(mi.name, x + 66, cy + 19);
            g.setColor(new Color(255, 214, 70));
            g.drawString("$" + fmtMoney(mi.value), x + 166, cy + 19);
            g.setFont(font(14));
            g.setColor(new Color(190, 190, 190));
            g.drawString(String.format("约 %d~%d 米,全图 %d 个", Math.round(mi.minDepth * METERS_PER_ROW / 50) * 50,
                Math.round(mi.peakDepth * METERS_PER_ROW / 50) * 50, Mineral.TREASURE_COUNT[k]), x + 270, cy + 19);
            g.setColor(new Color(220, 210, 180));
            g.drawString("「" + Mineral.TREASURE_TEXT[k] + "」", x + 66, cy + 39);
        }
        y += Mineral.TREASURES * 52 + 22;
        g.setFont(fontB(17));
        g.setColor(new Color(255, 200, 80));
        g.drawString("深度里程碑(第一次到达时,总部发奖金)", x, y);
        y += 28;
        g.setFont(font(15));
        for (int i = 0; i < MILESTONE_M.length; i++) {
            boolean got = (p.milestones & (1 << i)) != 0;
            int cx = x + 16 + (i % 3) * 220, cy = y + (i / 3) * 26;
            g.setColor(got ? new Color(120, 255, 170) : Color.WHITE);
            g.drawString(MILESTONE_M[i] + " 米   $" + fmtMoney(MILESTONE_BONUS[i]) + (got ? "  (已领)" : ""), cx, cy);
        }
    }

    void drawShopHelp(Graphics2D g, int x, int y) {
        String[] shops = {
            "加油站:加油 $" + (int) FUEL_PRICE + "/升,修车 $" + (int) REPAIR_PRICE + "/点;没钱时可领免费救济油",
            "矿石收购站:把货舱里的矿全部卖掉",
            "改装厂:升级下面 " + Upgrades.KINDS + " 种部件,每种 7 级,越往后越贵",
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
        g.drawString("最深纪录 " + Math.round(p.maxDepth * METERS_PER_ROW) + " 米    熔岩之心 " + p.hearts + " / " + heartGoal(), x + 30, y + 76);
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

    void drawEnding(Graphics2D g) {
        int x = 190, y = 110, w = 580, h = 420;
        panel(g, x, y, w, h);
        for (int i = 0; i < 6; i++) {
            double a = time * 0.8 + i * Math.PI / 3;
            Art.star((Graphics2D) g, x + w / 2.0 + Math.cos(a) * 240, y + 60 + Math.sin(a) * 30, 6 + 2 * Math.sin(time * 3 + i), new Color(255, 210, 80));
        }
        g.setFont(fontB(40));
        String t = "通关!";
        g.setPaint(new GradientPaint(0, y + 40, new Color(255, 235, 130), 0, y + 90, new Color(255, 100, 20)));
        g.drawString(t, x + (w - g.getFontMetrics().stringWidth(t)) / 2, y + 88);
        g.setFont(font(18));
        g.setColor(Color.WHITE);
        String sub = "你把全部 " + heartGoal() + " 颗熔岩之心都挖了出来!";
        g.drawString(sub, x + (w - g.getFontMetrics().stringWidth(sub)) / 2, y + 128);
        String[] lines = {
            "游戏用时      " + fmtTime(p.playTime),
            "最深纪录      " + Math.round(p.maxDepth * METERS_PER_ROW) + " 米",
            "累计卖矿收入  $" + fmtMoney(p.totalEarned),
            "爆炸次数      " + p.deaths + " 次",
        };
        g.setFont(font(17));
        for (int i = 0; i < lines.length; i++) {
            g.setColor(new Color(230, 230, 230));
            g.drawString(lines[i], x + 150, y + 190 + i * 36);
        }
        g.setFont(fontB(16));
        g.setColor(new Color(255, 200, 80));
        String k = "按 回车 继续自由挖矿";
        g.drawString(k, x + (w - g.getFontMetrics().stringWidth(k)) / 2, y + h - 30);
    }

    static String fmtTime(double sec) {
        long s = (long) sec;
        return String.format("%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
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
        int sum = c.getRed() + c.getGreen() + c.getBlue();
        if (sum < 150) return new Color(200, 200, 210);
        if (sum < 380) return new Color(Math.min(255, c.getRed() + 80), Math.min(255, c.getGreen() + 80), Math.min(255, c.getBlue() + 80));
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
        if (!repeat && k == KeyEvent.VK_M) {
            Sfx.muted = !Sfx.muted;
            String t = Sfx.muted ? "已静音(M 恢复)" : "声音已打开";
            if (state == State.PLAY) addText(p.x + Player.W / 2.0, p.y - 8, t, new Color(220, 220, 220));
            else msg(t, new Color(220, 220, 220));
            return;
        }
        if (!repeat && k == KeyEvent.VK_F11) {
            if (fullscreenToggler != null) fullscreenToggler.run();
            return;
        }
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
            case ENDING:
                if (ok && !repeat) {
                    state = State.PLAY;
                    save();
                }
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
