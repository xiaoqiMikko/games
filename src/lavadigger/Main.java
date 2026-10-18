package lavadigger;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;

/** 入口:java -jar LavaDigger.jar */
public final class Main {
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
            // 切走窗口时松开所有按键,免得车一直往前开
            f.addWindowFocusListener(new WindowFocusListener() {
                @Override
                public void windowGainedFocus(WindowEvent e) {
                    gp.requestFocusInWindow();
                }

                @Override
                public void windowLostFocus(WindowEvent e) {
                    gp.releaseAllKeys();
                }
            });
            f.setVisible(true);
            gp.requestFocusInWindow();
            gp.start();
        });
    }
}
