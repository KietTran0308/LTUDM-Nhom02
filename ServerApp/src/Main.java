import api.ImageSocketServer;
import api.SocketServer;
import gui.AdminGUI;
import gui.PosGUI;

import javax.swing.*;
import java.awt.*;

public class Main {

    public static void main(String[] args) {
        SocketServer server = new SocketServer();
        server.startServer();

        ImageSocketServer.start(8889);

        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("AN NHIEN | May chu trung tam");
            frame.setSize(650, 270);
            frame.setLocationRelativeTo(null);
            frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            frame.setLayout(new BorderLayout(12, 12));
            JLabel status = new JLabel("SERVER DANG CHAY | TCP: 8888 | Anh: 8889", SwingConstants.CENTER);
            status.setFont(new Font("SansSerif", Font.BOLD, 16));
            frame.add(status, BorderLayout.CENTER);
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 12));
            JButton pos = new JButton("Mở POS thu ngân (STAFF)");
            pos.addActionListener(e -> PosGUI.showPosLogin());
            buttons.add(pos);
            JButton admin = new JButton("Dang nhap quan tri ADMIN");
            admin.addActionListener(e -> AdminGUI.showAdminLogin());
            buttons.add(admin);
            JButton exit = new JButton("Tat may chu");
            exit.addActionListener(e -> {
                if (JOptionPane.showConfirmDialog(frame,
                        "Tat Server se ngat tat ca iPad. Tiep tuc?",
                        "Xac nhan", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                    server.stopServer();
                    System.exit(0);
                }
            });
            buttons.add(exit);
            frame.add(buttons, BorderLayout.SOUTH);
            frame.setVisible(true);
        });
    }
}
