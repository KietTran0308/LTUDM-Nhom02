import api.ImageSocketServer;
import api.SocketServer;

import javax.swing.*;
import java.awt.*;

public class Main {

    public static void main(String[] args) {
        SocketServer server = new SocketServer();
        server.startServer();

        ImageSocketServer.start(8889);

        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("AN NHIEN | May chu trung tam");
            frame.setSize(650, 220);
            frame.setLocationRelativeTo(null);
            frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            frame.setLayout(new BorderLayout(12, 12));
            JLabel status = new JLabel("SERVER DANG CHAY | TCP: 8888 | Anh: 8889", SwingConstants.CENTER);
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
