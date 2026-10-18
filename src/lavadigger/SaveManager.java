package lavadigger;

import java.io.*;
import java.util.Base64;
import java.util.Properties;

/** 存档:保存在 用户目录/.lava-digger/save.properties */
final class SaveManager {
    static boolean enabled = true;

    static File file() {
        return new File(System.getProperty("user.home"), ".lava-digger" + File.separator + "save.properties");
    }

    static boolean exists() {
        return enabled && file().isFile();
    }

    static void save(World w, Player p) {
        if (!enabled) return;
        Properties pr = new Properties();
        pr.setProperty("version", "1");
        pr.setProperty("seed", Long.toString(w.seed));
        pr.setProperty("tiles", Base64.getEncoder().encodeToString(w.tiles));
        pr.setProperty("money", Integer.toString(p.money));
        pr.setProperty("fuel", Double.toString(p.fuel));
        pr.setProperty("hull", Double.toString(p.hull));
        pr.setProperty("x", Double.toString(p.x));
        pr.setProperty("y", Double.toString(p.y));
        pr.setProperty("cargo", join(p.cargo));
        pr.setProperty("level", join(p.level));
        pr.setProperty("items", join(p.items));
        pr.setProperty("maxDepth", Integer.toString(p.maxDepth));
        pr.setProperty("hearts", Integer.toString(p.hearts));
        File f = file();
        f.getParentFile().mkdirs();
        File tmp = new File(f.getPath() + ".tmp");
        try (OutputStream out = new FileOutputStream(tmp)) {
            pr.store(out, "lava digger save");
        } catch (IOException e) {
            System.err.println("存档失败: " + e);
            return;
        }
        if (f.exists() && !f.delete()) System.err.println("无法覆盖旧存档");
        if (!tmp.renameTo(f)) System.err.println("存档重命名失败");
    }

    static boolean load(World w, Player p) {
        if (!exists()) return false;
        Properties pr = new Properties();
        try (InputStream in = new FileInputStream(file())) {
            pr.load(in);
            byte[] tiles = Base64.getDecoder().decode(pr.getProperty("tiles"));
            if (tiles.length != World.COLS * World.ROWS) return false;
            w.tiles = tiles;
            w.seed = Long.parseLong(pr.getProperty("seed"));
            p.money = Integer.parseInt(pr.getProperty("money"));
            split(pr.getProperty("cargo"), p.cargo);
            split(pr.getProperty("level"), p.level);
            split(pr.getProperty("items"), p.items);
            for (int i = 0; i < p.level.length; i++) p.level[i] = Math.min(p.level[i], Upgrades.TIERS - 1);
            p.fuel = Math.min(Double.parseDouble(pr.getProperty("fuel")), p.maxFuel());
            p.hull = Math.min(Double.parseDouble(pr.getProperty("hull")), p.maxHull());
            p.x = Double.parseDouble(pr.getProperty("x"));
            p.y = Double.parseDouble(pr.getProperty("y"));
            p.maxDepth = Integer.parseInt(pr.getProperty("maxDepth", "0"));
            p.hearts = Integer.parseInt(pr.getProperty("hearts", "0"));
            p.vx = p.vy = 0;
            p.digging = false;
            return true;
        } catch (Exception e) {
            System.err.println("读档失败: " + e);
            return false;
        }
    }

    private static String join(int[] a) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(a[i]);
        }
        return sb.toString();
    }

    private static void split(String s, int[] out) {
        if (s == null || s.isEmpty()) return;
        String[] parts = s.split(",");
        for (int i = 0; i < out.length && i < parts.length; i++) {
            out[i] = Math.max(0, Integer.parseInt(parts[i].trim()));
        }
    }
}
