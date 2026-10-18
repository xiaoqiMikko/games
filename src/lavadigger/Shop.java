package lavadigger;

import java.awt.Color;

/** 地面上的商店 */
final class Shop {
    static final int FUEL = 0, SELL = 1, UPGRADE = 2, ITEMS = 3;
    static final String[] NAMES = {"加油站", "矿石收购站", "改装厂", "道具店"};
    static final int[] COL = {2, 8, 32, 39};
    static final int[] WIDTH = {4, 4, 5, 4};
    static final Color[] COLORS = {
        new Color(196, 62, 50), new Color(55, 115, 190), new Color(95, 98, 110), new Color(140, 80, 170)
    };

    /** 出生/重生在加油站门口:水泥地挖不动,不会掉进自己挖的坑 */
    static final int SPAWN_COL = 4;
    /** 新手开挖区(这几列前几层保证是普通泥土) */
    static final int START_DIG_COL = 22;

    static boolean isUnderShop(int c) {
        for (int i = 0; i < COL.length; i++) {
            if (c >= COL[i] && c < COL[i] + WIDTH[i]) return true;
        }
        return false;
    }

    /** 车中心点所在的商店,没有返回 -1 */
    static int at(double centerX) {
        for (int i = 0; i < COL.length; i++) {
            double l = COL[i] * World.T, r = (COL[i] + WIDTH[i]) * World.T;
            if (centerX >= l && centerX < r) return i;
        }
        return -1;
    }
}
