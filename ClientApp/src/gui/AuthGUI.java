package gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import gui.components.*;

public class AuthGUI extends JFrame {

    private CardLayout cardLayout;
    private JPanel rightCardPanel;

    private JCheckBox chkRemember;
    private CButton btnLogin, btnRegister;

    private bus.AuthBUS authBUS;

    private CTextField txtLoginEmail, txtRegFullName, txtRegEmail, txtRegPhone;
    private CPasswordField txtLoginPassword, txtRegPassword, txtRegConfirmPassword;

    public AuthGUI() {
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setTitle("Bếp Nhà Restaurant - Đăng nhập & Đăng ký");
        setLocationRelativeTo(null);
        setResizable(false);
        setLayout(new GridLayout(1, 2));

        add(createLeftPanel());

        cardLayout = new CardLayout();
        rightCardPanel = new JPanel(cardLayout);

        rightCardPanel.add(createLoginPanel(), "LOGIN");
        rightCardPanel.add(createRegisterPanel(), "REGISTER");

        add(rightCardPanel);

        this.addWindowFocusListener(new WindowAdapter() {
            public void windowGainedFocus(WindowEvent e) {
                if (btnLogin != null) btnLogin.requestFocusInWindow();
            }
        });

        authBUS = new bus.AuthBUS(this::onAuthServerResponse);
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

        JLabel lblIcon = new JLabel(new LogoIcon());
        lblIcon.setBorder(new EmptyBorder(0, 0, 0, 10));

        JPanel logoTextPanel = new JPanel();
        logoTextPanel.setLayout(new BoxLayout(logoTextPanel, BoxLayout.Y_AXIS));
        logoTextPanel.setOpaque(false);

        JLabel lblBrandName = new JLabel("Bếp Nhà");
        lblBrandName.setFont(getCustomFont("Inter", Font.BOLD, 18));
        lblBrandName.setForeground(Color.WHITE);
        lblBrandName.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel lblBrandSub = new JLabel("RESTAURANT");
        lblBrandSub.setFont(getCustomFont("Inter", Font.BOLD, 9));
        lblBrandSub.setForeground(new Color(140, 150, 145));
        lblBrandSub.setAlignmentX(Component.LEFT_ALIGNMENT);

        logoTextPanel.add(lblBrandName);
        logoTextPanel.add(lblBrandSub);
        logoPanel.add(lblIcon);
        logoPanel.add(logoTextPanel);
        leftPanel.add(logoPanel, BorderLayout.NORTH);

        JPanel centerWrapper = new JPanel(new GridBagLayout());
        centerWrapper.setOpaque(false);
        JPanel centerContent = new JPanel();
        centerContent.setLayout(new BoxLayout(centerContent, BoxLayout.Y_AXIS));
        centerContent.setOpaque(false);

        JPanel badgePanel = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 25, 25);
            }
        };
        badgePanel.setOpaque(false);
        badgePanel.setLayout(new FlowLayout(FlowLayout.CENTER, 0, 0));
        badgePanel.setMaximumSize(new Dimension(230, 28));
        badgePanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel lblBadge = new JLabel("VẬN HÀNH NHÀ HÀNG, ĐƠN GIẢN HƠN");
        lblBadge.setFont(getCustomFont("Inter", Font.BOLD, 10));
        lblBadge.setForeground(new Color(201, 142, 84));
        lblBadge.setBorder(new EmptyBorder(7, 15, 7, 15));
        badgePanel.add(lblBadge);

        JLabel lblTitle1 = new JLabel("Mỗi đơn món,");
        lblTitle1.setFont(getCustomFont("Be Vietnam Pro Black", Font.BOLD, 46));
        lblTitle1.setForeground(Color.WHITE);
        lblTitle1.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel lblTitle2 = new JLabel("một trải nghiệm");
        lblTitle2.setFont(getCustomFont("Be Vietnam Pro Black", Font.BOLD, 46));
        lblTitle2.setForeground(Color.WHITE);
        lblTitle2.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel lblTitle3 = new JLabel("trọn vẹn.");
        lblTitle3.setFont(getCustomFont("Be Vietnam Pro Black", Font.BOLD, 46));
        lblTitle3.setForeground(new Color(201, 142, 84));
        lblTitle3.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel lblDesc1 = new JLabel("Kết nối khách hàng, bếp và đội ngũ phục vụ trong một hệ");
        lblDesc1.setFont(getCustomFont("Inter", Font.PLAIN, 15));
        lblDesc1.setForeground(new Color(152, 161, 157));
        lblDesc1.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel lblDesc2 = new JLabel("thống thời gian thực.");
        lblDesc2.setFont(getCustomFont("Inter", Font.PLAIN, 15));
        lblDesc2.setForeground(new Color(152, 161, 157));
        lblDesc2.setAlignmentX(Component.CENTER_ALIGNMENT);

        centerContent.add(badgePanel);
        centerContent.add(Box.createVerticalStrut(25));
        centerContent.add(lblTitle1);
        centerContent.add(lblTitle2);
        centerContent.add(lblTitle3);
        centerContent.add(Box.createVerticalStrut(20));
        centerContent.add(lblDesc1);
        centerContent.add(Box.createVerticalStrut(5));
        centerContent.add(lblDesc2);

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

        JLabel lblTitle = new JLabel("Chào mừng trở lại");
        lblTitle.setFont(getCustomFont("Be Vietnam Pro Black", Font.BOLD, 40));
        JPanel titleWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        titleWrapper.setOpaque(false);
        titleWrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleWrapper.add(lblTitle);

        JLabel lblSubtitle = new JLabel("Đăng nhập để tiếp tục vào hệ thống Bếp Nhà");
        lblSubtitle.setFont(getCustomFont("Inter", Font.PLAIN, 22));
        lblSubtitle.setForeground(Color.GRAY);
        lblSubtitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblEmail = new JLabel("Email hoặc số điện thoại");
        lblEmail.setFont(getCustomFont("Be Vietnam Pro Black", Font.BOLD, 15));
        lblEmail.setAlignmentX(Component.LEFT_ALIGNMENT);

        txtLoginEmail = new CTextField("name@bepnha.vn", 470, 50);
        txtLoginEmail.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblPassword = new JLabel("Mật khẩu");
        lblPassword.setFont(getCustomFont("Be Vietnam Pro Black", Font.BOLD, 15));
        lblPassword.setAlignmentX(Component.LEFT_ALIGNMENT);

        txtLoginPassword = new CPasswordField("••••••••", 470, 50);
        txtLoginPassword.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel pnlOptions = new JPanel(new BorderLayout());
        pnlOptions.setBackground(Color.WHITE);
        pnlOptions.setMaximumSize(new Dimension(470, 50));
        pnlOptions.setAlignmentX(Component.LEFT_ALIGNMENT);

        chkRemember = new JCheckBox("Ghi nhớ đăng nhập");
        chkRemember.setBackground(Color.WHITE);
        chkRemember.setFont(getCustomFont("Be Vietnam Pro Black", Font.PLAIN, 14));
        chkRemember.setForeground(new Color(107, 114, 128));
        chkRemember.setFocusPainted(false);
        chkRemember.setIcon(new CCheckBox(false));
        chkRemember.setSelectedIcon(new CCheckBox(true));
        chkRemember.setSelected(true);

        JLabel lblForgot = new JLabel("Quên mật khẩu?");
        lblForgot.setFont(getCustomFont("Be Vietnam Pro Black", Font.BOLD, 14));
        lblForgot.setForeground(new Color(206, 93, 52));
        lblForgot.setCursor(new Cursor(Cursor.HAND_CURSOR));

        pnlOptions.add(chkRemember, BorderLayout.WEST);
        pnlOptions.add(lblForgot, BorderLayout.EAST);

        btnLogin = new CButton("Đăng nhập", 470, 50);
        btnLogin.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnLogin.addActionListener(e -> {
            String account = txtLoginEmail.getText().trim();
            String pass = new String(txtLoginPassword.getPassword()).trim();

            if (account.isEmpty() || account.equals("name@bepnha.vn") || pass.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Vui lòng nhập Email/SĐT và Mật khẩu!", "Nhắc nhở", JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (!authBUS.login(account, pass)) {
                // Chế độ Offline Demo nhanh nếu bạn chưa bật ServerApp
                if (account.toLowerCase().contains("staff")) {
                    new StaffGUI().setVisible(true);
                    this.dispose();
                } else {
                    new CustomerGUI().setVisible(true);
                    this.dispose();
                }
            }
        });

        JPanel pnlRegister = new JPanel();
        pnlRegister.setLayout(new FlowLayout(FlowLayout.CENTER, 0, 0));
        pnlRegister.setBackground(Color.WHITE);
        pnlRegister.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblNoAccount = new JLabel("Chưa có tài khoản? ");
        lblNoAccount.setFont(getCustomFont("Be Vietnam Pro Black", Font.PLAIN, 14));
        lblNoAccount.setForeground(Color.GRAY);

        JLabel lblRegLink = new JLabel("Đăng ký ngay");
        lblRegLink.setFont(getCustomFont("Be Vietnam Pro Black", Font.BOLD, 14));
        lblRegLink.setForeground(new Color(206, 93, 52));
        lblRegLink.setCursor(new Cursor(Cursor.HAND_CURSOR));

        lblRegLink.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                cardLayout.show(rightCardPanel, "REGISTER");
            }
        });

        pnlRegister.add(lblNoAccount);
        pnlRegister.add(lblRegLink);

        formContainer.add(titleWrapper);
        formContainer.add(Box.createVerticalStrut(8));
        formContainer.add(lblSubtitle);
        formContainer.add(Box.createVerticalStrut(26));

        JSeparator separator1 = new JSeparator(SwingConstants.HORIZONTAL);
        separator1.setSize(new Dimension(Integer.MAX_VALUE, 10));
        separator1.setForeground(new Color(23, 31, 26));
        formContainer.add(separator1);
        formContainer.add(Box.createVerticalStrut(25));

        formContainer.add(lblEmail);
        formContainer.add(Box.createVerticalStrut(8));
        formContainer.add(txtLoginEmail);
        formContainer.add(Box.createVerticalStrut(20));
        formContainer.add(lblPassword);
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

    // ================= 3. FUNCTION FORM ĐĂNG KÝ =================
    private JPanel createRegisterPanel() {
        JPanel rightWrapper = new JPanel(new GridBagLayout());
        rightWrapper.setBackground(Color.WHITE);

        JPanel formContainer = new JPanel();
        formContainer.setLayout(new BoxLayout(formContainer, BoxLayout.Y_AXIS));
        formContainer.setBackground(Color.WHITE);

        JLabel lblTitle = new JLabel("Đăng ký tài khoản");
        lblTitle.setFont(getCustomFont("Be Vietnam Pro Black", Font.BOLD, 40));
        JPanel titleWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        titleWrapper.setOpaque(false);
        titleWrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleWrapper.add(lblTitle);

        JLabel lblSubtitle = new JLabel("Tạo tài khoản để bắt đầu sử dụng các chức năng");
        lblSubtitle.setFont(getCustomFont("Inter", Font.PLAIN, 22));
        lblSubtitle.setForeground(Color.GRAY);
        lblSubtitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblFullName = new JLabel("Họ và tên");
        lblFullName.setFont(getCustomFont("Be Vietnam Pro Black", Font.BOLD, 15));
        lblFullName.setAlignmentX(Component.LEFT_ALIGNMENT);
        txtRegFullName = new CTextField("Nguyễn Minh Anh", 500, 50);
        txtRegFullName.setAlignmentX(Component.LEFT_ALIGNMENT); // Xóa thiết lập thừa

        JLabel lblEmail = new JLabel("Email");
        lblEmail.setFont(getCustomFont("Be Vietnam Pro Black", Font.BOLD, 15));
        lblEmail.setAlignmentX(Component.LEFT_ALIGNMENT);
        txtRegEmail = new CTextField("name@bepnha.vn", 500, 50);
        txtRegEmail.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblPhone = new JLabel("Số điện thoại");
        lblPhone.setFont(getCustomFont("Be Vietnam Pro Black", Font.BOLD, 15));
        lblPhone.setAlignmentX(Component.LEFT_ALIGNMENT);
        txtRegPhone = new CTextField("090 123 4567", 500, 50);
        txtRegPhone.setAlignmentX(Component.LEFT_ALIGNMENT); // Xóa thiết lập thừa

        JLabel lblPassword = new JLabel("Mật khẩu");
        lblPassword.setFont(getCustomFont("Be Vietnam Pro Black", Font.BOLD, 15));
        lblPassword.setAlignmentX(Component.LEFT_ALIGNMENT);
        txtRegPassword = new CPasswordField("••••••••", 500, 50);
        txtRegPassword.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblConfirmPassword = new JLabel("Xác nhận mật khẩu");
        lblConfirmPassword.setFont(getCustomFont("Be Vietnam Pro Black", Font.BOLD, 15));
        lblConfirmPassword.setAlignmentX(Component.LEFT_ALIGNMENT);
        txtRegConfirmPassword = new CPasswordField("••••••••", 500, 50);
        txtRegConfirmPassword.setAlignmentX(Component.LEFT_ALIGNMENT);

        btnRegister = new CButton("Đăng ký", 500, 50);
        btnRegister.setAlignmentX(Component.LEFT_ALIGNMENT); // Xóa thiết lập thừa
        btnRegister.addActionListener(e -> {
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
        });

        JPanel pnlLogin = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        pnlLogin.setBackground(Color.WHITE);
        pnlLogin.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblHasAccount = new JLabel("Đã có tài khoản? ");
        lblHasAccount.setFont(getCustomFont("Be Vietnam Pro Black", Font.PLAIN, 14));
        lblHasAccount.setForeground(Color.GRAY);

        JLabel lblLoginLink = new JLabel("Đăng nhập");
        lblLoginLink.setFont(getCustomFont("Be Vietnam Pro Black", Font.BOLD, 14));
        lblLoginLink.setForeground(new Color(206, 93, 52));
        lblLoginLink.setCursor(new Cursor(Cursor.HAND_CURSOR));

        lblLoginLink.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                cardLayout.show(rightCardPanel, "LOGIN");
            }
        });

        pnlLogin.add(lblHasAccount);
        pnlLogin.add(lblLoginLink);

        formContainer.add(titleWrapper);
        formContainer.add(Box.createVerticalStrut(8));
        formContainer.add(lblSubtitle);
        formContainer.add(Box.createVerticalStrut(35));

        JSeparator separator1 = new JSeparator(SwingConstants.HORIZONTAL);
        separator1.setSize(new Dimension(Integer.MAX_VALUE, 10));
        separator1.setForeground(new Color(23, 31, 26));
        formContainer.add(separator1);
        formContainer.add(Box.createVerticalStrut(25));

        formContainer.add(lblFullName);
        formContainer.add(Box.createVerticalStrut(5));
        formContainer.add(txtRegFullName);
        formContainer.add(Box.createVerticalStrut(15));
        formContainer.add(lblEmail);
        formContainer.add(Box.createVerticalStrut(5));
        formContainer.add(txtRegEmail);
        formContainer.add(Box.createVerticalStrut(15));
        formContainer.add(lblPhone);
        formContainer.add(Box.createVerticalStrut(5));
        formContainer.add(txtRegPhone);
        formContainer.add(Box.createVerticalStrut(15));
        formContainer.add(lblPassword);
        formContainer.add(Box.createVerticalStrut(5));
        formContainer.add(txtRegPassword);
        formContainer.add(Box.createVerticalStrut(15));
        formContainer.add(lblConfirmPassword);
        formContainer.add(Box.createVerticalStrut(5));
        formContainer.add(txtRegConfirmPassword);
        formContainer.add(Box.createVerticalStrut(25));
        formContainer.add(btnRegister);
        formContainer.add(Box.createVerticalStrut(25));
        formContainer.add(pnlLogin);

        rightWrapper.add(formContainer);
        return rightWrapper;
    }

    // ================= 4. COMPONENT LOGO =================

    // Class CustomPasswordField thừa thãi đã được XÓA BỎ HOÀN TOÀN

    class LogoIcon implements Icon {
        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(206, 93, 52));
            g2.fillRoundRect(x, y, 40, 40, 12, 12);
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("SansSerif", Font.BOLD, 22));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString("⌂", x + (40 - fm.stringWidth("⌂")) / 2, y + ((40 - fm.getHeight()) / 2) + fm.getAscent());
        }
        @Override public int getIconWidth() { return 40; }
        @Override public int getIconHeight() { return 40; }
    }

    private void onAuthServerResponse(dto.SocketPacket packet) {
        SwingUtilities.invokeLater(() -> {
            if (packet.getAction() == dto.SocketAction.LOGIN) {
                if (!packet.isSuccess()) {
                    JOptionPane.showMessageDialog(this, packet.getMessage(), "Đăng nhập thất bại", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                dto.UserDTO user = (dto.UserDTO) packet.getData();
                String role = user.getRoleName() != null ? user.getRoleName().toUpperCase() : "CUSTOMER";

                // Điều hướng mở màn hình theo đúng Role trong Database
                if (role.contains("STAFF") || role.contains("CASHIER") || role.contains("ADMIN")) {
                    new StaffGUI(user).setVisible(true);
                } else {
                    new CustomerGUI(1L, user.getId()).setVisible(true);
                }
                this.dispose();

            } else if (packet.getAction() == dto.SocketAction.REGISTER) {
                if (packet.isSuccess()) {
                    JOptionPane.showMessageDialog(this, packet.getMessage() + "\nVui lòng đăng nhập.", "Thành công", JOptionPane.INFORMATION_MESSAGE);
                    txtLoginEmail.setText(txtRegEmail.getText());
                    cardLayout.show(rightCardPanel, "LOGIN");
                } else {
                    JOptionPane.showMessageDialog(this, packet.getMessage(), "Đăng ký thất bại", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
    }

    private Font getCustomFont(String fontName, int style, int size) {
        return new Font(fontName, style, size);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new AuthGUI().setVisible(true));
    }
}