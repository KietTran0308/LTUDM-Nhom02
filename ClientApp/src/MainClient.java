import gui.AuthGUI;

import javax.swing.*;

public class MainClient {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new AuthGUI().setVisible(true));
    }
}
