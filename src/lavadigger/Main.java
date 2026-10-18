package lavadigger;

import javax.swing.*;
import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;

/** 入口:java -jar LavaDigger.jar */
public final class Main {
    private static boolean fullscreen;
    private static Rectangle windowBounds;
    private static long toggledAt;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("熔岩矿车");
            GamePanel gp = new GamePanel();
            f.setContentPane(gp);
            f.pack();
            f.setLocationRelativeTo(null);
            f.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            f.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    gp.saveOnExit();
                    System.exit(0);
                }
            });
            // 切走窗口时松开所有按键,正在玩就自动暂停
            f.addWindowFocusListener(new WindowFocusListener() {
                @Override
                public void windowGainedFocus(WindowEvent e) {
                    gp.requestFocusInWindow();
                }

                @Override
                public void windowLostFocus(WindowEvent e) {
                    // 切换全屏时窗口会短暂失去焦点,这时不算切走
                    if (System.nanoTime() - toggledAt < 800_000_000L) gp.releaseAllKeys();
                    else gp.onFocusLost();
                }
            });
            // F11:全屏 / 窗口
            gp.fullscreenToggler = () -> {
                toggledAt = System.nanoTime();
                f.dispose();
                if (!fullscreen) {
                    windowBounds = f.getBounds();
                    f.setUndecorated(true);
                    f.setExtendedState(Frame.MAXIMIZED_BOTH);
                } else {
                    f.setUndecorated(false);
                    f.setExtendedState(Frame.NORMAL);
                    if (windowBounds != null) f.setBounds(windowBounds);
                }
                fullscreen = !fullscreen;
                f.setVisible(true);
                gp.requestFocusInWindow();
                toggledAt = System.nanoTime();
            };
            f.setVisible(true);
            gp.requestFocusInWindow();
            gp.start();
        });
    }
}
