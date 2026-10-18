package lavadigger;

import java.awt.*;
import java.awt.geom.*;
import java.util.Random;

/** 手绘风格的商店和矿车(全部用代码画) */
final class Art {
    private Art() {
    }

    static void aa(Graphics2D g, boolean on) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, on ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
    }

    static Color mix(Color a, Color b, double t) {
        return new Color((int) (a.getRed() + (b.getRed() - a.getRed()) * t), (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
            (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a)));
    }

    // ================================================================ 商店

    /** x = 商店左边缘, w = 宽度, ground = 地面的屏幕 y */
    static void shop(Graphics2D g, int type, int x, int w, int ground, double time, Font signFont) {
        aa(g, true);
        Stroke os = g.getStroke();
        int top;
        switch (type) {
            case Shop.FUEL: top = fuelStation(g, x, w, ground, time); break;
            case Shop.SELL: top = warehouse(g, x, w, ground, time); break;
            case Shop.UPGRADE: top = garage(g, x, w, ground, time); break;
            default: top = itemShop(g, x, w, ground, time); break;
        }
        sign(g, signFont, Shop.NAMES[type], x + w / 2, top - 6, Shop.COLORS[type], time);
        g.setStroke(os);
        aa(g, false);
    }

    static void sign(Graphics2D g, Font f, String text, int cx, int bottom, Color accent, double time) {
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        int w = fm.stringWidth(text) + 22, h = 24;
        int x = cx - w / 2, y = bottom - h;
        // 支架
        g.setColor(new Color(60, 55, 50));
        g.fillRect(x + 8, bottom - 2, 3, 8);
        g.fillRect(x + w - 11, bottom - 2, 3, 8);
        g.setPaint(new GradientPaint(0, y, new Color(52, 40, 32), 0, y + h, new Color(28, 22, 18)));
        g.fill(new RoundRectangle2D.Double(x, y, w, h, 8, 8));
        g.setStroke(new BasicStroke(2f));
        g.setColor(mix(accent, Color.WHITE, 0.35));
        g.draw(new RoundRectangle2D.Double(x + 1, y + 1, w - 2, h - 2, 8, 8));
        g.setStroke(new BasicStroke(1f));
        // 霓虹字:外发光 + 金色字
        g.setColor(new Color(255, 180, 60, 70));
        g.drawString(text, x + 11, y + 18);
        g.drawString(text, x + 12, y + 17);
        g.setColor(new Color(255, 222, 110));
        g.drawString(text, x + 11, y + 17);
        // 两颗小灯,交替闪
        for (int i = 0; i < 2; i++) {
            boolean on = ((int) (time * 2) + i) % 2 == 0;
            int bx = i == 0 ? x + 4 : x + w - 8;
            g.setColor(on ? new Color(255, 240, 150) : new Color(120, 100, 60));
            g.fill(new Ellipse2D.Double(bx, y - 3, 4, 4));
        }
    }

    static void window(Graphics2D g, double x, double y, double w, double h, double time, int seed) {
        g.setColor(new Color(60, 50, 45));
        g.fill(new Rectangle2D.Double(x - 2, y - 2, w + 4, h + 4));
        double flick = 0.9 + 0.1 * Math.sin(time * 3 + seed);
        g.setPaint(new GradientPaint((float) x, (float) y, new Color(255, 236, 170), (float) x, (float) (y + h),
            new Color((int) (255 * flick), (int) (190 * flick), 90)));
        g.fill(new Rectangle2D.Double(x, y, w, h));
        g.setColor(new Color(255, 255, 255, 120));
        g.draw(new Line2D.Double(x + 2, y + h - 3, x + w * 0.5, y + 2));
        g.setColor(new Color(60, 50, 45));
        g.draw(new Line2D.Double(x + w / 2, y, x + w / 2, y + h));
        g.draw(new Line2D.Double(x, y + h / 2, x + w, y + h / 2));
    }

    // ---- 加油站:顶棚 + 柱子 + 加油机 + 小便利店 ----
    static int fuelStation(Graphics2D g, int x, int w, int ground, double time) {
        Color red = new Color(205, 52, 44), redD = new Color(140, 30, 28);
        // 后面的小店
        int kx = x + 50, kw = w - 56, ky = ground - 60;
        g.setPaint(new GradientPaint(0, ky, new Color(240, 232, 214), 0, ground, new Color(205, 195, 178)));
        g.fillRect(kx, ky, kw, 60);
        g.setColor(red);
        g.fillRect(kx, ground - 10, kw, 10);
        g.setColor(new Color(170, 160, 145));
        g.fillRect(kx, ky, kw, 3);
        window(g, kx + 7, ky + 12, 22, 18, time, 1);
        // 玻璃门
        int dx = kx + kw - 27;
        g.setColor(new Color(70, 70, 75));
        g.fillRect(dx - 2, ground - 44, 24, 44);
        g.setPaint(new GradientPaint(dx, ground - 42, new Color(180, 225, 245), dx + 20, ground, new Color(90, 150, 190)));
        g.fillRect(dx, ground - 42, 20, 42);
        g.setColor(new Color(255, 255, 255, 140));
        g.draw(new Line2D.Double(dx + 3, ground - 6, dx + 12, ground - 38));
        // 柱子
        int ct = ground - 98;
        for (int px : new int[]{x + 6, x + w - 13}) {
            g.setPaint(new GradientPaint(px, 0, new Color(235, 235, 235), px + 7, 0, new Color(170, 170, 175)));
            g.fillRect(px, ct + 16, 7, ground - ct - 16);
            g.setColor(red);
            g.fillRect(px, ground - 24, 7, 5);
            g.fillRect(px, ground - 14, 7, 5);
        }
        // 顶棚
        g.setPaint(new GradientPaint(0, ct, new Color(235, 70, 60), 0, ct + 18, redD));
        g.fill(new RoundRectangle2D.Double(x - 8, ct, w + 16, 18, 4, 4));
        g.setColor(Color.WHITE);
        g.fillRect(x - 8, ct + 7, w + 16, 3);
        g.setColor(new Color(90, 20, 18));
        g.fillRect(x - 6, ct + 18, w + 12, 3);
        // 顶棚下的灯
        for (int lx = x + 18; lx < x + w - 10; lx += 26) {
            g.setColor(new Color(255, 250, 210));
            g.fillRect(lx, ct + 20, 10, 2);
            g.setPaint(new GradientPaint(0, ct + 22, new Color(255, 245, 190, 70), 0, ct + 60, new Color(255, 245, 190, 0)));
            g.fill(new Polygon(new int[]{lx, lx + 10, lx + 20, lx - 10}, new int[]{ct + 22, ct + 22, ct + 60, ct + 60}, 4));
        }
        // 加油机
        int px = x + 19, py = ground - 38;
        g.setColor(new Color(80, 80, 85));
        g.fillRect(px - 3, ground - 4, 26, 4);
        g.setPaint(new GradientPaint(px, 0, new Color(230, 70, 60), px + 20, 0, new Color(160, 35, 30)));
        g.fill(new RoundRectangle2D.Double(px, py, 20, 34, 5, 5));
        g.setColor(new Color(245, 245, 245));
        g.fill(new RoundRectangle2D.Double(px + 2, py - 4, 16, 6, 3, 3));
        g.setColor(new Color(20, 30, 25));
        g.fillRect(px + 3, py + 4, 14, 9);
        g.setColor(new Color(90, 255, 140));
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 8));
        g.drawString(((int) (time * 2) % 2 == 0) ? "$2" : "2L", px + 4, py + 12);
        g.setColor(new Color(255, 220, 90));
        g.fillRect(px + 4, py + 17, 5, 3);
        g.fillRect(px + 11, py + 17, 5, 3);
        // 油枪和软管
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(30, 30, 32));
        g.draw(new CubicCurve2D.Double(px + 20, py + 10, px + 32, py + 14, px + 30, py + 34, px + 24, py + 24));
        g.setColor(new Color(60, 60, 64));
        g.fill(new RoundRectangle2D.Double(px + 20, py + 20, 6, 8, 2, 2));
        return ct;
    }

    // ---- 矿石收购站:蓝色仓库 + 金币标志 + 装满矿的小矿车 ----
    static int warehouse(Graphics2D g, int x, int w, int ground, double time) {
        int wy = ground - 78;
        g.setPaint(new GradientPaint(0, wy, new Color(80, 140, 210), 0, ground, new Color(45, 90, 150)));
        g.fillRect(x + 4, wy, w - 8, 78);
        g.setColor(new Color(35, 70, 120, 150));
        for (int lx = x + 8; lx < x + w - 6; lx += 6) g.drawLine(lx, wy + 2, lx, ground - 2);
        // 人字屋顶
        int peak = wy - 26;
        Path2D.Double roof = new Path2D.Double();
        roof.moveTo(x - 6, wy + 4);
        roof.lineTo(x + w / 2.0, peak);
        roof.lineTo(x + w + 6, wy + 4);
        roof.lineTo(x + w + 6, wy + 9);
        roof.lineTo(x + w / 2.0, peak + 6);
        roof.lineTo(x - 6, wy + 9);
        roof.closePath();
        g.setColor(new Color(30, 55, 95));
        g.fill(roof);
        Path2D.Double gable = new Path2D.Double();
        gable.moveTo(x + 4, wy + 5);
        gable.lineTo(x + w / 2.0, peak + 7);
        gable.lineTo(x + w - 4, wy + 5);
        gable.closePath();
        g.setColor(new Color(60, 110, 175));
        g.fill(gable);
        // 金币标志
        double ccx = x + w / 2.0, ccy = wy + 10;
        g.setPaint(new RadialGradientPaint((float) (ccx - 3), (float) (ccy - 3), 12, new float[]{0f, 1f},
            new Color[]{new Color(255, 245, 170), new Color(220, 150, 20)}));
        g.fill(new Ellipse2D.Double(ccx - 10, ccy - 10, 20, 20));
        g.setColor(new Color(150, 95, 10));
        g.setStroke(new BasicStroke(1.5f));
        g.draw(new Ellipse2D.Double(ccx - 10, ccy - 10, 20, 20));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        g.drawString("$", (int) ccx - 4, (int) ccy + 5);
        // 推拉门
        int dx = x + w / 2 - 22, dw = 44, dh = 46;
        g.setColor(new Color(40, 30, 25));
        g.fillRect(dx, ground - dh, dw, dh);
        g.setPaint(new GradientPaint(0, ground - dh, new Color(255, 210, 120, 120), 0, ground, new Color(255, 160, 60, 40)));
        g.fillRect(dx + 2, ground - dh + 2, 12, dh - 2);
        g.setColor(new Color(150, 100, 55));
        g.fillRect(dx + 14, ground - dh, dw - 14, dh);
        g.setColor(new Color(110, 70, 35));
        g.setStroke(new BasicStroke(2.5f));
        g.draw(new Rectangle2D.Double(dx + 15, ground - dh + 1, dw - 16, dh - 2));
        g.draw(new Line2D.Double(dx + 15, ground - dh + 1, dx + dw - 1, ground - 1));
        g.draw(new Line2D.Double(dx + dw - 1, ground - dh + 1, dx + 15, ground - 1));
        g.setColor(new Color(70, 70, 75));
        g.fillRect(dx - 2, ground - dh - 4, dw + 4, 4);
        // 门口的小矿车,装满矿石
        mineCart(g, x + 6, ground, time);
        window(g, x + w - 30, wy + 26, 18, 14, time, 2);
        return peak;
    }

    static void mineCart(Graphics2D g, int x, int ground, double time) {
        int y = ground - 20;
        Color[] ores = {new Color(255, 205, 40), new Color(215, 220, 232), new Color(230, 30, 65), new Color(225, 125, 50),
            new Color(160, 240, 255), new Color(255, 205, 40)};
        for (int i = 0; i < ores.length; i++) {
            double ox = x + 4 + i * 3.6, oy = y - 2 - (i % 2) * 3;
            g.setColor(ores[i].darker());
            g.fill(new Ellipse2D.Double(ox - 0.5, oy - 0.5, 7, 7));
            g.setColor(ores[i]);
            g.fill(new Ellipse2D.Double(ox, oy, 6, 6));
            g.setColor(new Color(255, 255, 255, 200));
            g.fillRect((int) ox + 1, (int) oy + 1, 2, 1);
        }
        Path2D.Double body = new Path2D.Double();
        body.moveTo(x, y);
        body.lineTo(x + 28, y);
        body.lineTo(x + 25, y + 13);
        body.lineTo(x + 3, y + 13);
        body.closePath();
        g.setPaint(new GradientPaint(0, y, new Color(140, 110, 80), 0, y + 13, new Color(90, 65, 45)));
        g.fill(body);
        g.setColor(new Color(60, 45, 30));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(body);
        g.drawLine(x + 2, y + 4, x + 26, y + 4);
        for (int wx : new int[]{x + 7, x + 21}) {
            g.setColor(new Color(50, 50, 55));
            g.fill(new Ellipse2D.Double(wx - 4, ground - 8, 8, 8));
            g.setColor(new Color(150, 150, 160));
            g.fill(new Ellipse2D.Double(wx - 1.5, ground - 5.5, 3, 3));
        }
    }

    // ---- 改装厂:卷帘门车库 + 转动的齿轮 + 轮胎堆 ----
    static int garage(Graphics2D g, int x, int w, int ground, double time) {
        int wy = ground - 86;
        g.setPaint(new GradientPaint(0, wy, new Color(165, 168, 178), 0, ground, new Color(115, 118, 128)));
        g.fillRect(x + 2, wy, w - 4, 86);
        g.setColor(new Color(95, 98, 108));
        for (int lx = x + 34; lx < x + w; lx += 46) g.drawLine(lx, wy + 12, lx, ground);
        // 顶部警示条纹
        Shape oc = g.getClip();
        g.clip(new Rectangle2D.Double(x - 2, wy - 10, w + 4, 12));
        g.setColor(new Color(250, 200, 40));
        g.fillRect(x - 2, wy - 10, w + 4, 12);
        g.setColor(new Color(35, 35, 35));
        for (int sx = x - 20; sx < x + w + 10; sx += 14)
            g.fill(new Polygon(new int[]{sx, sx + 7, sx + 19, sx + 12}, new int[]{wy + 2, wy + 2, wy - 10, wy - 10}, 4));
        g.setClip(oc);
        g.setColor(new Color(70, 72, 80));
        g.fillRect(x - 2, wy + 2, w + 4, 3);
        // 卷帘门(升起一半,里面亮着灯)
        int dx = x + 38, dw = w - 76, dh = 58, dy = ground - dh;
        g.setColor(new Color(250, 200, 40));
        g.fillRect(dx - 5, dy - 5, dw + 10, dh + 5);
        g.setColor(new Color(35, 35, 35));
        for (int sy = dy - 5; sy < ground; sy += 8) {
            g.fillRect(dx - 5, sy, 5, 4);
            g.fillRect(dx + dw, sy + 4, 5, 4);
        }
        g.setPaint(new GradientPaint(0, dy, new Color(60, 45, 35), 0, ground, new Color(255, 170, 70)));
        g.fillRect(dx, dy, dw, dh);
        // 里面挂着的吊钩
        double sway = Math.sin(time * 1.5) * 2;
        g.setColor(new Color(40, 40, 40));
        g.draw(new Line2D.Double(dx + dw / 2.0, dy, dx + dw / 2.0 + sway, dy + 34));
        g.setStroke(new BasicStroke(2f));
        g.draw(new Arc2D.Double(dx + dw / 2.0 + sway - 4, dy + 32, 8, 8, 180, 270, Arc2D.OPEN));
        g.setStroke(new BasicStroke(1f));
        int shutter = (int) (dh * 0.45);
        g.setPaint(new GradientPaint(0, dy, new Color(215, 218, 225), 0, dy + shutter, new Color(170, 173, 182)));
        g.fillRect(dx, dy, dw, shutter);
        g.setColor(new Color(130, 133, 142));
        for (int sy = dy + 4; sy < dy + shutter; sy += 4) g.drawLine(dx, sy, dx + dw, sy);
        g.setColor(new Color(90, 92, 100));
        g.fillRect(dx, dy + shutter - 2, dw, 3);
        g.fillRect(dx + dw / 2 - 5, dy + shutter, 10, 2);
        // 齿轮
        gear(g, x + 20, wy + 30, 11, time * 0.8, new Color(250, 200, 40));
        gear(g, x + 32, wy + 46, 7, -time * 1.25 + 0.3, new Color(200, 205, 215));
        // 扳手
        g.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(210, 215, 225));
        int wx = x + w - 26, wy2 = wy + 28;
        g.draw(new Line2D.Double(wx - 8, wy2 + 10, wx + 6, wy2 - 4));
        g.setStroke(new BasicStroke(1f));
        g.fill(new Ellipse2D.Double(wx + 2, wy2 - 9, 9, 9));
        g.setColor(new Color(130, 133, 142));
        g.fill(new Rectangle2D.Double(wx + 5, wy2 - 10, 3, 5));
        // 轮胎堆
        for (int i = 0; i < 2; i++) {
            double ty = ground - 9 - i * 8;
            g.setColor(new Color(30, 30, 33));
            g.fill(new RoundRectangle2D.Double(x + w - 30, ty, 22, 9, 6, 6));
            g.setColor(new Color(75, 75, 80));
            g.fill(new RoundRectangle2D.Double(x + w - 26, ty + 3, 14, 3, 3, 3));
        }
        return wy - 10;
    }

    static void gear(Graphics2D g, double cx, double cy, double r, double ang, Color c) {
        Path2D.Double p = new Path2D.Double();
        int teeth = 8;
        for (int i = 0; i < teeth * 2; i++) {
            double a0 = ang + i * Math.PI / teeth, a1 = ang + (i + 1) * Math.PI / teeth;
            double rr = i % 2 == 0 ? r : r * 0.78;
            if (i == 0) p.moveTo(cx + Math.cos(a0) * rr, cy + Math.sin(a0) * rr);
            else p.lineTo(cx + Math.cos(a0) * rr, cy + Math.sin(a0) * rr);
            p.lineTo(cx + Math.cos(a1) * rr, cy + Math.sin(a1) * rr);
        }
        p.closePath();
        g.setColor(c.darker());
        g.translate(1, 1);
        g.fill(p);
        g.translate(-1, -1);
        g.setColor(c);
        g.fill(p);
        g.setColor(new Color(90, 92, 100));
        g.fill(new Ellipse2D.Double(cx - r * 0.35, cy - r * 0.35, r * 0.7, r * 0.7));
    }

    // ---- 道具店:砖墙 + 条纹遮阳篷 + 摆满道具的橱窗 + 闪烁的星星 ----
    static int itemShop(Graphics2D g, int x, int w, int ground, double time) {
        int wy = ground - 80;
        g.setColor(new Color(130, 75, 160));
        g.fillRect(x + 4, wy, w - 8, 80);
        g.setColor(new Color(105, 58, 132));
        for (int ry = 0; ry * 7 < 80; ry++) {
            int yy = wy + ry * 7;
            g.drawLine(x + 4, yy, x + w - 5, yy);
            for (int bx = x + 4 + (ry % 2) * 8; bx < x + w - 4; bx += 16) g.drawLine(bx, yy, bx, yy + 7);
        }
        // 檐口
        g.setColor(new Color(80, 40, 100));
        g.fillRect(x, wy - 6, w, 8);
        g.setColor(new Color(170, 120, 200));
        g.fillRect(x, wy - 6, w, 2);
        // 星星招牌
        double tw = 0.75 + 0.25 * Math.sin(time * 4);
        star(g, x + w / 2.0, wy + 14, 10 * tw + 2, new Color(255, 225, 90));
        // 遮阳篷
        int ay = ground - 58, ah = 14;
        for (int i = 0, sx = x - 2; sx < x + w + 2; sx += 12, i++) {
            g.setColor(i % 2 == 0 ? new Color(225, 90, 160) : new Color(250, 245, 250));
            g.fillRect(sx, ay, 12, ah);
            g.fill(new Arc2D.Double(sx, ay + ah - 6, 12, 12, 180, 180, Arc2D.PIE));
        }
        g.setColor(new Color(150, 50, 110));
        g.fillRect(x - 2, ay - 2, w + 4, 3);
        // 橱窗
        int wx = x + 10, ww = 50, wh = 30, wy2 = ground - 38;
        g.setColor(new Color(70, 45, 85));
        g.fillRect(wx - 2, wy2 - 2, ww + 4, wh + 4);
        g.setPaint(new GradientPaint(0, wy2, new Color(255, 245, 215), 0, wy2 + wh, new Color(240, 215, 170)));
        g.fillRect(wx, wy2, ww, wh);
        // 橱窗里的四种道具
        g.setPaint(new GradientPaint(wx + 3, 0, new Color(230, 60, 50), wx + 11, 0, new Color(150, 30, 25)));
        g.fill(new RoundRectangle2D.Double(wx + 3, wy2 + 12, 9, 15, 3, 3));        // 油桶
        g.setColor(new Color(255, 220, 90));
        g.fillRect(wx + 3, wy2 + 16, 9, 2);
        g.setColor(Color.WHITE);
        g.fill(new RoundRectangle2D.Double(wx + 15, wy2 + 15, 11, 11, 3, 3));     // 修理包
        g.setColor(new Color(40, 170, 80));
        g.fillRect(wx + 19, wy2 + 17, 3, 7);
        g.fillRect(wx + 17, wy2 + 19, 7, 3);
        g.setColor(new Color(200, 40, 35));                                     // 炸药
        for (int i = 0; i < 3; i++) g.fill(new RoundRectangle2D.Double(wx + 29 + i * 3.2, wy2 + 13, 3, 14, 2, 2));
        g.setColor(new Color(80, 60, 40));
        g.draw(new Line2D.Double(wx + 33, wy2 + 13, wx + 35, wy2 + 7));
        g.setColor(new Color(255, 200, 60));
        g.fill(new Ellipse2D.Double(wx + 34, wy2 + 5, 3, 3));
        double glow = 0.6 + 0.4 * Math.sin(time * 5);                          // 传送水晶
        g.setColor(new Color(120, 200, 255, (int) (110 * glow)));
        g.fill(new Ellipse2D.Double(wx + 39, wy2 + 12, 11, 11));
        g.setColor(new Color(150, 225, 255));
        g.fill(new Ellipse2D.Double(wx + 41.5, wy2 + 14.5, 6, 6));
        g.setColor(new Color(255, 255, 255, 150));
        g.draw(new Line2D.Double(wx + 2, wy2 + wh - 3, wx + 14, wy2 + 2));
        // 门
        int dx = x + w - 42;
        g.setColor(new Color(70, 45, 85));
        g.fillRect(dx - 2, ground - 44, 28, 44);
        g.setColor(new Color(110, 70, 45));
        g.fillRect(dx, ground - 42, 24, 42);
        g.setPaint(new GradientPaint(dx, ground - 40, new Color(255, 230, 160), dx, ground - 22, new Color(240, 190, 110)));
        g.fillRect(dx + 4, ground - 38, 16, 14);
        g.setColor(new Color(255, 215, 90));
        g.fill(new Ellipse2D.Double(dx + 18, ground - 22, 3, 3));
        return wy - 6;
    }

    static void star(Graphics2D g, double cx, double cy, double r, Color c) {
        Path2D.Double p = new Path2D.Double();
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 5, rr = i % 2 == 0 ? r : r * 0.45;
            if (i == 0) p.moveTo(cx + Math.cos(a) * rr, cy + Math.sin(a) * rr);
            else p.lineTo(cx + Math.cos(a) * rr, cy + Math.sin(a) * rr);
        }
        p.closePath();
        g.setColor(new Color(255, 230, 120, 70));
        g.fill(new Ellipse2D.Double(cx - r * 1.4, cy - r * 1.4, r * 2.8, r * 2.8));
        g.setColor(c);
        g.fill(p);
        g.setColor(c.darker());
        g.draw(p);
    }

    // ================================================================ 矿车

    /** (cx, cy) 为车的中心;车体约 26×24,钻头会伸出车身 */
    static void vehicle(Graphics2D g, double cx, double cy, int facing, boolean drillDown, boolean digging, boolean thrusting,
                        double wheelAngle, double time, Random rnd, boolean damaged) {
        aa(g, true);
        AffineTransform old = g.getTransform();
        Stroke os = g.getStroke();
        g.translate(cx, cy);
        g.scale(facing, 1);

        // 喷气火焰
        if (thrusting) {
            for (int fxp : new int[]{-8, 5}) {
                double len = 8 + rnd.nextDouble() * 8;
                Path2D.Double fl = new Path2D.Double();
                fl.moveTo(fxp - 3.5, 11);
                fl.quadTo(fxp - 2, 11 + len * 0.6, fxp, 11 + len);
                fl.quadTo(fxp + 2, 11 + len * 0.6, fxp + 3.5, 11);
                fl.closePath();
                g.setColor(new Color(255, 110, 20, 200));
                g.fill(fl);
                g.setColor(new Color(255, 220, 90));
                g.fill(new Ellipse2D.Double(fxp - 2, 11, 4, len * 0.55));
                g.setColor(new Color(255, 255, 230));
                g.fill(new Ellipse2D.Double(fxp - 1, 11, 2, len * 0.3));
            }
        }

        drill(g, drillDown, time * (digging ? 42 : 5));

        // 排气管
        g.setColor(new Color(90, 90, 96));
        g.fill(new RoundRectangle2D.Double(-15.5, -12, 3, 8, 1.5, 1.5));
        g.setColor(new Color(50, 50, 55));
        g.fill(new Rectangle2D.Double(-16, -13, 4, 2));

        // 履带
        RoundRectangle2D.Double tread = new RoundRectangle2D.Double(-14.5, 3, 27, 9.5, 9, 9);
        g.setColor(new Color(34, 34, 38));
        g.fill(tread);
        g.setColor(new Color(85, 85, 92));
        double off = ((wheelAngle * 3) % 4 + 4) % 4;
        for (double lx = -11 + off; lx < 10; lx += 4) {
            g.fill(new Rectangle2D.Double(lx, 3, 2, 1.4));
            g.fill(new Rectangle2D.Double(lx, 11, 2, 1.4));
        }
        for (double wx : new double[]{-9, -1, 7}) {
            g.setColor(new Color(120, 122, 130));
            g.fill(new Ellipse2D.Double(wx - 3.3, 4.4, 6.6, 6.6));
            g.setColor(new Color(70, 70, 78));
            g.fill(new Ellipse2D.Double(wx - 1.4, 6.3, 2.8, 2.8));
            g.setColor(new Color(170, 172, 180));
            g.draw(new Line2D.Double(wx + Math.cos(wheelAngle) * 2.6, 7.7 + Math.sin(wheelAngle) * 2.6,
                wx + Math.cos(wheelAngle) * 1.5, 7.7 + Math.sin(wheelAngle) * 1.5));
        }
        // 底盘
        g.setColor(new Color(70, 58, 48));
        g.fill(new Rectangle2D.Double(-13, 1.5, 24, 2.5));

        // 车身
        Path2D.Double body = new Path2D.Double();
        body.moveTo(-14, -5);
        body.quadTo(-14, -7, -12, -7);
        body.lineTo(9, -7);
        body.quadTo(12, -7, 13, -3);
        body.lineTo(13, 2);
        body.lineTo(-14, 2);
        body.closePath();
        g.setPaint(new GradientPaint(0, -7, new Color(255, 205, 80), 0, 2, new Color(222, 120, 18)));
        g.fill(body);
        // 尾部黄黑警示条
        Shape oc = g.getClip();
        g.clip(new Rectangle2D.Double(-14, -6, 5, 7.5));
        g.setColor(new Color(30, 30, 30));
        for (double sx = -20; sx < -6; sx += 3.5)
            g.fill(new Polygon(new int[]{(int) sx, (int) sx + 2, (int) sx + 8, (int) sx + 6}, new int[]{2, 2, -7, -7}, 4));
        g.setClip(oc);
        g.setColor(new Color(255, 240, 170));
        g.draw(new Line2D.Double(-11, -6.2, 9, -6.2));
        g.setColor(new Color(190, 60, 30));
        g.fill(new Rectangle2D.Double(-8, -1.2, 21, 1.8));
        g.setColor(new Color(150, 80, 15));
        for (double rx : new double[]{-5, 0, 5}) g.fill(new Ellipse2D.Double(rx - 0.8, -4.2, 1.6, 1.6));
        g.setStroke(new BasicStroke(0.8f));
        g.setColor(new Color(120, 60, 10));
        g.draw(body);

        // 驾驶舱
        RoundRectangle2D.Double cab = new RoundRectangle2D.Double(-12, -15, 13, 9.5, 4, 4);
        g.setPaint(new GradientPaint(0, -15, new Color(245, 180, 50), 0, -5, new Color(210, 120, 20)));
        g.fill(cab);
        g.setColor(new Color(120, 60, 10));
        g.draw(cab);
        RoundRectangle2D.Double glass = new RoundRectangle2D.Double(-10.3, -13.4, 9.6, 6, 2.5, 2.5);
        g.setPaint(new GradientPaint(0, -13.4f, new Color(200, 240, 255), 0, -7.4f, new Color(70, 140, 205)));
        g.fill(glass);
        g.setColor(new Color(255, 255, 255, 200));
        g.setStroke(new BasicStroke(1.1f));
        g.draw(new Line2D.Double(-8.5, -8.5, -5.5, -12.6));
        g.setColor(new Color(255, 255, 255, 110));
        g.draw(new Line2D.Double(-6, -8.2, -3.5, -12));
        // 车顶警示灯(钻探时闪)
        boolean beacon = digging && ((int) (time * 6)) % 2 == 0;
        g.setColor(new Color(90, 90, 96));
        g.fill(new Rectangle2D.Double(-7, -16.5, 4, 1.8));
        g.setColor(beacon ? new Color(255, 120, 40) : new Color(170, 80, 40));
        g.fill(new Ellipse2D.Double(-6.8, -18.6, 3.6, 3));
        if (beacon) {
            g.setColor(new Color(255, 140, 40, 90));
            g.fill(new Ellipse2D.Double(-10, -21.5, 10, 9));
        }
        // 车灯
        g.setColor(new Color(255, 250, 200, 90));
        g.fill(new Ellipse2D.Double(8.5, -6.5, 8, 6));
        g.setColor(new Color(255, 252, 215));
        g.fill(new Ellipse2D.Double(10, -5.5, 4, 3.2));

        // 车身受损时的黑斑
        if (damaged) {
            g.setColor(new Color(40, 30, 25, 160));
            g.fill(new Ellipse2D.Double(-6, -5, 4, 3));
            g.fill(new Ellipse2D.Double(4, -3, 3, 2.5));
            g.fill(new Ellipse2D.Double(-10, -12, 3, 2.5));
        }
        g.setStroke(os);
        g.setTransform(old);
        aa(g, false);
    }

    static void drill(Graphics2D g, boolean down, double spin) {
        Path2D.Double cone = new Path2D.Double();
        if (down) {
            g.setColor(new Color(80, 80, 88));
            g.fill(new RoundRectangle2D.Double(-6, 11, 12, 3, 2, 2));
            cone.moveTo(-6.5, 13.5);
            cone.lineTo(6.5, 13.5);
            cone.lineTo(0, 25);
        } else {
            g.setColor(new Color(80, 80, 88));
            g.fill(new RoundRectangle2D.Double(11, -5, 3, 10, 2, 2));
            cone.moveTo(13.5, -5);
            cone.lineTo(13.5, 6);
            cone.lineTo(24.5, 0.5);
        }
        cone.closePath();
        if (down) g.setPaint(new GradientPaint(-6.5f, 0, new Color(150, 152, 162), 6.5f, 0, new Color(240, 242, 250)));
        else g.setPaint(new GradientPaint(0, -5, new Color(245, 246, 252), 0, 6, new Color(130, 132, 142)));
        g.fill(cone);
        Shape oc = g.getClip();
        g.clip(cone);
        g.setStroke(new BasicStroke(1.3f));
        g.setColor(new Color(85, 85, 95));
        for (int k = 0; k < 5; k++) {
            double o = ((spin + k * 3.2) % 16);
            if (down) g.draw(new Line2D.Double(-8, 12 + o, 8, 9.5 + o));
            else g.draw(new Line2D.Double(12 + o, -6, 9.5 + o, 7));
        }
        g.setClip(oc);
        g.setStroke(new BasicStroke(0.8f));
        g.setColor(new Color(60, 60, 68));
        g.draw(cone);
        g.setColor(new Color(255, 255, 255, 220));
        if (down) g.fill(new Ellipse2D.Double(-1, 22.5, 2, 2));
        else g.fill(new Ellipse2D.Double(22, -0.5, 2, 2));
    }
}
