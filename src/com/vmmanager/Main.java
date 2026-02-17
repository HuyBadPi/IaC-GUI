package com.vmmanager;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import com.vmmanager.ui.MainFrame;
import com.vmmanager.utils.LoggerUtil;

public class Main {
    private static MainFrame frame;
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    UIManager.setLookAndFeel(
                        UIManager.getSystemLookAndFeelClassName()
                    );
                    
                    frame = new MainFrame();
                    frame.setVisible(true);
                    
                    // Thêm shutdown hook để dọn dẹp
                    Runtime.getRuntime().addShutdownHook(new Thread() {
                        @Override
                        public void run() {
                            if (frame != null) {
                                frame.dispose();
                            }
                            LoggerUtil.info("Ứng dụng đã đóng");
                        }
                    });
                    
                } catch (Exception e) {
                    LoggerUtil.error("Lỗi khởi động ứng dụng", e);
                }
            }
        });
    }
}