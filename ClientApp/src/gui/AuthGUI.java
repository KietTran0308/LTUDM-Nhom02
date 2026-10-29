package gui;

import bus.AuthBUS;
import dto.SocketAction;
import dto.SocketPacket;
import dto.UserDTO;
import gui.components.*;
import utils.Utils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;

public class AuthGUI extends JFrame {

    private static final String CARD_LOGIN = "LOGIN";
    private static final String CARD_REGISTER = "REGISTER";
    private static final Color BRAND_ORANGE = new Color(206, 93, 52);

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel rightCardPanel = new JPanel(cardLayout);
    private final AuthBUS authBUS;

    private JCheckBox chkRemember;
    private CButton btnLogin, btnRegister;

    private CTextField txtLoginEmail, txtRegFullName, txtRegEmail, txtRegPhone;
    private CPasswordField txtLoginPassword, txtRegPassword, txtRegConfirmPassword;

    public AuthGUI() {
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setUndecorated(true);
        setSize(1360, 820);
        setLocationRelativeTo(null);
        setResizable(false);
        setLayout(new GridLayout(1, 2));

        add(createLeftPanel());

        rightCardPanel.add(createLoginPanel(), CARD_LOGIN);
        rightCardPanel.add(createRegisterPanel(), CARD_REGISTER);
        add(rightCardPanel);

        addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                if (btnLogin != null) {
                    btnLogin.requestFocusInWindow();
                }
            }
        });

        authBUS = new AuthBUS(this::onAuthServerResponse);
    }

    private JPanel createLeftPanel() {
        JPanel leftPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(29, 41, 34));
                g2.fillOval(getWidth() - 350, getHeight() / 2 - 450, 700, 700);
                g2.setColor(new Color(33, 45, 38));
                g2.fillOval(getWidth() - 250, getHeight() / 2 - 350, 500, 500);
                g2.setColor(new Color(24, 37, 30));
                g2.fillOval(getWidth() - 150, getHeight() / 2 - 250, 300, 300);
            }
        };
        leftPanel.setBackground(new Color(32, 43, 37));
        leftPanel.setBorder(new EmptyBorder(40, 40, 40, 40));

        JPanel logoPanel = new JPanel();
        logoPanel.setLayout(new BoxLayout(logoPanel, BoxLayout.X_AXIS));
        logoPanel.setOpaque(false);

        JLabel lblIcon = new JLabel(Utils.getScaledIcon("img/logo.png", 50, 50));
        lblIcon.setBorder(new EmptyBorder(0, 0, 0, 10));

        JLabel lblIcon2 = new JLabel(Utils.getScaledIcon("img/logo2.png", 50, 50));
        lblIcon2.setBorder(new EmptyBorder(0, 0, 0, 10));

        JPanel logoTextPanel = new JPanel();
        logoTextPanel.setLayout(new BoxLayout(logoTextPanel, BoxLayout.Y_AXIS));
        logoTextPanel.setOpaque(false);

        JLabel lblBrandName = createLabel("AN NHIÊN", "Inter", Font.BOLD, 30, Color.WHITE, Component.LEFT_ALIGNMENT);
        JLabel lblBrandSub = createLabel("Nhà hàng", "Inter", Font.BOLD, 18, new Color(140, 150, 145), Component.LEFT_ALIGNMENT);

        logoTextPanel.add(lblBrandSub);
        logoTextPanel.add(lblBrandName);
        logoPanel.add(lblIcon2);
        logoPanel.add(lblIcon);
        logoPanel.add(logoTextPanel);
        leftPanel.add(logoPanel, BorderLayout.NORTH);

        JPanel centerWrapper = new JPanel(new GridBagLayout());
        centerWrapper.setOpaque(false);

        JPanel centerContent = new JPanel();
        centerContent.setLayout(new BoxLayout(centerContent, BoxLayout.Y_AXIS));
        centerContent.setOpaque(false);

        JPanel badgePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 25, 25);
            }
        };
        badgePanel.setOpaque(false);
        badgePanel.setMaximumSize(new Dimension(230, 28));
        badgePanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel lblBadge = createLabel("VẬN HÀNH NHÀ HÀNG, ĐƠN GIẢN HƠN", "Inter", Font.BOLD, 10, new Color(201, 142, 84), Component.CENTER_ALIGNMENT);
        lblBadge.setBorder(new EmptyBorder(7, 15, 7, 15));
        badgePanel.add(lblBadge);

        Color descColor = new Color(152, 161, 157);
        centerContent.add(badgePanel);
        centerContent.add(Box.createVerticalStrut(25));
        centerContent.add(createLabel("Mỗi đơn món,", "Be Vietnam Pro Black", Font.BOLD, 46, Color.WHITE, Component.CENTER_ALIGNMENT));
        centerContent.add(createLabel("một trải nghiệm", "Be Vietnam Pro Black", Font.BOLD, 46, Color.WHITE, Component.CENTER_ALIGNMENT));
        centerContent.add(createLabel("trọn vẹn.", "Be Vietnam Pro Black", Font.BOLD, 46, new Color(201, 142, 84), Component.CENTER_ALIGNMENT));
        centerContent.add(Box.createVerticalStrut(20));
        centerContent.add(createLabel("Kết nối khách hàng, bếp và đội ngũ phục vụ trong một hệ", "Inter", Font.PLAIN, 15, descColor, Component.CENTER_ALIGNMENT));
        centerContent.add(Box.createVerticalStrut(5));
        centerContent.add(createLabel("thống thời gian thực.", "Inter", Font.PLAIN, 15, descColor, Component.CENTER_ALIGNMENT));

        centerWrapper.add(centerContent);
        leftPanel.add(centerWrapper, BorderLayout.CENTER);

        return leftPanel;
    }

    private JPanel createLoginPanel() {
        JPanel rightWrapper = new JPanel(new GridBagLayout());
        rightWrapper.setBackground(Color.WHITE);

        JPanel formContainer = new JPanel();
        formContainer.setLayout(new BoxLayout(formContainer, BoxLayout.Y_AXIS));
        formContainer.setOpaque(false);

        JPanel titleWrapper = createTitleHeader("Chào mừng trở lại");
        JLabel lblSubtitle = createLabel("Đăng nhập để tiếp tục vào hệ thống Bếp Nhà", "Inter", Font.PLAIN, 22, Color.GRAY, Component.LEFT_ALIGNMENT);

        txtLoginEmail = new CTextField("name@bepnha.vn", 470, 50);
        txtLoginEmail.setAlignmentX(Component.LEFT_ALIGNMENT);

        txtLoginPassword = new CPasswordField("••••••••", 470, 50);
        txtLoginPassword.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel pnlOptions = new JPanel(new BorderLayout());
        pnlOptions.setBackground(Color.WHITE);
        pnlOptions.setMaximumSize(new Dimension(470, 50));
        pnlOptions.setAlignmentX(Component.LEFT_ALIGNMENT);

        chkRemember = new JCheckBox("Ghi nhớ đăng nhập");
        chkRemember.setBackground(Color.WHITE);
        chkRemember.setFont(Utils.getCustomFont("Be Vietnam Pro Black", Font.PLAIN, 14));
        chkRemember.setForeground(new Color(107, 114, 128));
        chkRemember.setFocusPainted(false);
        chkRemember.setIcon(new CCheckBox(false));
        chkRemember.setSelectedIcon(new CCheckBox(true));
        chkRemember.setSelected(true);

        JLabel lblForgot = createLabel("Quên mật khẩu?", "Be Vietnam Pro Black", Font.BOLD, 14, BRAND_ORANGE, Component.LEFT_ALIGNMENT);
        lblForgot.setCursor(new Cursor(Cursor.HAND_CURSOR));

        pnlOptions.add(chkRemember, BorderLayout.WEST);
        pnlOptions.add(lblForgot, BorderLayout.EAST);

        btnLogin = new CButton("Đăng nhập", 470, 50);
        btnLogin.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnLogin.addActionListener(e -> handleLogin());

        JPanel pnlRegister = createSwitchAuthPanel("Chưa có tài khoản? ", "Đăng ký ngay", CARD_REGISTER);

        formContainer.add(titleWrapper);
        formContainer.add(Box.createVerticalStrut(8));
        formContainer.add(lblSubtitle);
        formContainer.add(Box.createVerticalStrut(26));
        formContainer.add(createSeparator());
        formContainer.add(Box.createVerticalStrut(25));
        formContainer.add(createFieldLabel("Email hoặc số điện thoại"));
        formContainer.add(Box.createVerticalStrut(8));
        formContainer.add(txtLoginEmail);
        formContainer.add(Box.createVerticalStrut(20));
        formContainer.add(createFieldLabel("Mật khẩu"));
        formContainer.add(Box.createVerticalStrut(8));
        formContainer.add(txtLoginPassword);
        formContainer.add(Box.createVerticalStrut(15));
        formContainer.add(pnlOptions);
        formContainer.add(Box.createVerticalStrut(35));
        formContainer.add(btnLogin);
        formContainer.add(Box.createVerticalStrut(30));
        formContainer.add(pnlRegister);

        rightWrapper.add(formContainer);
        return rightWrapper;
    }

    private JPanel createRegisterPanel() {
        JPanel rightWrapper = new JPanel(new GridBagLayout());
        rightWrapper.setBackground(Color.WHITE);

        JPanel formContainer = new JPanel();
        formContainer.setLayout(new BoxLayout(formContainer, BoxLayout.Y_AXIS));
        formContainer.setBackground(Color.WHITE);

        JPanel titleWrapper = createTitleHeader("Đăng ký tài khoản");
        JLabel lblSubtitle = createLabel("Tạo tài khoản để bắt đầu sử dụng các chức năng", "Inter", Font.PLAIN, 22, Color.GRAY, Component.LEFT_ALIGNMENT);

        txtRegFullName = new CTextField("Nguyễn Minh Anh", 500, 50);
        txtRegFullName.setAlignmentX(Component.LEFT_ALIGNMENT);

        txtRegEmail = new CTextField("name@bepnha.vn", 500, 50);
        txtRegEmail.setAlignmentX(Component.LEFT_ALIGNMENT);

        txtRegPhone = new CTextField("090 123 4567", 500, 50);
        txtRegPhone.setAlignmentX(Component.LEFT_ALIGNMENT);

        txtRegPassword = new CPasswordField("••••••••", 500, 50);
        txtRegPassword.setAlignmentX(Component.LEFT_ALIGNMENT);

        txtRegConfirmPassword = new CPasswordField("••••••••", 500, 50);
        txtRegConfirmPassword.setAlignmentX(Component.LEFT_ALIGNMENT);

        btnRegister = new CButton("Đăng ký", 500, 50);
        btnRegister.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnRegister.addActionListener(e -> handleRegister());

        JPanel pnlLogin = createSwitchAuthPanel("Đã có tài khoản? ", "Đăng nhập", CARD_LOGIN);

        formContainer.add(titleWrapper);
        formContainer.add(Box.createVerticalStrut(8));
        formContainer.add(lblSubtitle);
        formContainer.add(Box.createVerticalStrut(35));
        formContainer.add(createSeparator());
        formContainer.add(Box.createVerticalStrut(25));
        formContainer.add(createFieldLabel("Họ và tên"));
        formContainer.add(Box.createVerticalStrut(5));
        formContainer.add(txtRegFullName);
        formContainer.add(Box.createVerticalStrut(15));
        formContainer.add(createFieldLabel("Email"));
        formContainer.add(Box.createVerticalStrut(5));
        formContainer.add(txtRegEmail);
        formContainer.add(Box.createVerticalStrut(15));
        formContainer.add(createFieldLabel("Số điện thoại"));
        formContainer.add(Box.createVerticalStrut(5));
        formContainer.add(txtRegPhone);
        formContainer.add(Box.createVerticalStrut(15));
        formContainer.add(createFieldLabel("Mật khẩu"));
        formContainer.add(Box.createVerticalStrut(5));
        formContainer.add(txtRegPassword);
        formContainer.add(Box.createVerticalStrut(15));
        formContainer.add(createFieldLabel("Xác nhận mật khẩu"));
        formContainer.add(Box.createVerticalStrut(5));
        formContainer.add(txtRegConfirmPassword);
        formContainer.add(Box.createVerticalStrut(25));
        formContainer.add(btnRegister);
        formContainer.add(Box.createVerticalStrut(25));
        formContainer.add(pnlLogin);

        rightWrapper.add(formContainer);
        return rightWrapper;
    }

    private void handleLogin() {
        String account = txtLoginEmail.getText().trim();
        String pass = new String(txtLoginPassword.getPassword()).trim();

        if (account.isEmpty() || account.equals("name@bepnha.vn") || pass.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập Email/SĐT và Mật khẩu!", "Nhắc nhở", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (!authBUS.login(account, pass)) {
            if (account.toLowerCase().contains("staff")) {
                new StaffGUI().setVisible(true);
            } else {
                new CustomerGUI(1L, 1L).setVisible(true);
            }
            dispose();
        }
    }

    private void handleRegister() {
        String fullName = txtRegFullName.getText().trim();
        String email = txtRegEmail.getText().trim();
        String phone = txtRegPhone.getText().trim();
        String pass = new String(txtRegPassword.getPassword()).trim();
        String confirm = new String(txtRegConfirmPassword.getPassword()).trim();

        if (fullName.isEmpty() || email.isEmpty() || pass.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng điền đầy đủ thông tin bắt buộc!", "Lỗi", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!pass.equals(confirm)) {
            JOptionPane.showMessageDialog(this, "Mật khẩu xác nhận không khớp!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (!authBUS.register(fullName, email, phone, pass)) {
            JOptionPane.showMessageDialog(this, "Không kết nối được ServerApp (Port 8888)!", "Lỗi mạng", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onAuthServerResponse(SocketPacket packet) {
        SwingUtilities.invokeLater(() -> {
            if (packet.getAction() == SocketAction.LOGIN) {
                if (!packet.isSuccess()) {
                    JOptionPane.showMessageDialog(this, packet.getMessage(), "Đăng nhập thất bại", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                UserDTO user = (UserDTO) packet.getData();
                String role = user.getRoleName() != null ? user.getRoleName().toUpperCase() : "CUSTOMER";

                if (role.contains("STAFF") || role.contains("CASHIER") || role.contains("ADMIN")) {
                    new StaffGUI(user).setVisible(true);
                } else {
                    new CustomerGUI(1L, user.getId()).setVisible(true);
                }
                dispose();

            } else if (packet.getAction() == SocketAction.REGISTER) {
                if (packet.isSuccess()) {
                    JOptionPane.showMessageDialog(this, packet.getMessage() + "\nVui lòng đăng nhập.", "Thành công", JOptionPane.INFORMATION_MESSAGE);
                    txtLoginEmail.setText(txtRegEmail.getText());
                    cardLayout.show(rightCardPanel, CARD_LOGIN);
                } else {
                    JOptionPane.showMessageDialog(this, packet.getMessage(), "Đăng ký thất bại", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
    }

    private JPanel createTitleHeader(String text) {
        JLabel lblTitle = createLabel(text, "Be Vietnam Pro Black", Font.BOLD, 40, Color.BLACK, Component.LEFT_ALIGNMENT);
        JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        wrapper.setOpaque(false);
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.add(lblTitle);
        return wrapper;
    }

    private JLabel createFieldLabel(String text) {
        return createLabel(text, "Be Vietnam Pro Black", Font.BOLD, 15, Color.BLACK, Component.LEFT_ALIGNMENT);
    }

    private JLabel createLabel(String text, String fontName, int style, int size, Color color, float alignmentX) {
        JLabel label = new JLabel(text);
        label.setFont(Utils.getCustomFont(fontName, style, size));
        label.setForeground(color);
        label.setAlignmentX(alignmentX);
        return label;
    }

    private JSeparator createSeparator() {
        JSeparator separator = new JSeparator(SwingConstants.HORIZONTAL);
        separator.setSize(new Dimension(Integer.MAX_VALUE, 10));
        separator.setForeground(new Color(23, 31, 26));
        return separator;
    }

    private JPanel createSwitchAuthPanel(String promptText, String linkText, String targetCard) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        panel.setBackground(Color.WHITE);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblPrompt = createLabel(promptText, "Be Vietnam Pro Black", Font.PLAIN, 14, Color.GRAY, Component.LEFT_ALIGNMENT);
        JLabel lblLink = createLabel(linkText, "Be Vietnam Pro Black", Font.BOLD, 14, BRAND_ORANGE, Component.LEFT_ALIGNMENT);
        lblLink.setCursor(new Cursor(Cursor.HAND_CURSOR));
        lblLink.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                cardLayout.show(rightCardPanel, targetCard);
            }
        });

        panel.add(lblPrompt);
        panel.add(lblLink);
        return panel;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new AuthGUI().setVisible(true));
    }
}