
import javax.swing.*;
import java.awt.*;

import api.SocketServer;

public class Main {

    public static void main(String[] args) {
        SocketServer server = new SocketServer();
        server.startServer();

        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("AN NHIEN | May chu trung tam");
            frame.setSize(650, 220);
            frame.setLocationRelativeTo(null);
            frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            frame.setLayout(new BorderLayout(12, 12));
            JLabel status = new JLabel("SERVER DANG CHAY | TCP: 8888 (JSON + anh nhi phan)", SwingConstants.CENTER);
            status.setFont(new Font("SansSerif", Font.BOLD, 16));
            frame.add(status, BorderLayout.CENTER);
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 12));
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
