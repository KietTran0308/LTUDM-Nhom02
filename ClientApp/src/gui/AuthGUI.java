package gui;

import api.ImageSocketClient;
import api.SocketClient;
import bus.AuthBUS;
import components.CButton;
import components.CPasswordField;
import components.CTextField;
import components.CToast;
import dto.UserDTO;
import utils.Utils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.Locale;

import static java.awt.Color.GRAY;

public class AuthGUI extends JFrame {

    private static final String CARD_LOGIN = "LOGIN";
    private static final String CARD_FORGOT = "FORGOT";
    private static final String PLACEHOLDER_EMAIL = "name@bepnha.vn";

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel rightCardPanel = new JPanel(cardLayout);
    private final AuthBUS authBUS = new AuthBUS();
    private final Timer responseTimer;

    private CButton btnLogin;
    private CTextField txtLoginEmail;
    private CPasswordField txtLoginPassword;

    private CTextField txtForgotEmail;
    private CTextField txtForgotOtp;
    private CPasswordField txtForgotNewPassword;
    private CPasswordField txtForgotConfirmPassword;
    private CButton btnSendResetCode;
    private CButton btnConfirmReset;

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
        rightCardPanel.add(createForgotPasswordPanel(), CARD_FORGOT);
        add(rightCardPanel);

        loadPasswordIconsViaSocket();

        addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                if (btnLogin != null) btnLogin.requestFocusInWindow();
            }
        });

        responseTimer = new Timer(100, e -> {
            AuthBUS.Event event = authBUS.pollEvent();
            if (event != null) onAuthEvent(event);
        });
        responseTimer.start();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                responseTimer.stop();
            }
        });
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

        JLabel lblIcon = createSocketLogo("things/logo2.png", 50);
        lblIcon.setBorder(new EmptyBorder(0, 0, 0, 10));

        JPanel logoTextPanel = new JPanel();
        logoTextPanel.setLayout(new BoxLayout(logoTextPanel, BoxLayout.Y_AXIS));
        logoTextPanel.setOpaque(false);
        logoTextPanel.add(createLabel("Nhà hàng", "Inter", Font.BOLD, 18, new Color(140, 150, 145), Component.LEFT_ALIGNMENT));
        logoTextPanel.add(createLabel("AN NHIÊN", "Inter", Font.BOLD, 30, Color.WHITE, Component.LEFT_ALIGNMENT));

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

        centerContent.add(badgePanel);
        centerContent.add(Box.createVerticalStrut(25));
        centerContent.add(createLabel("Mỗi đơn món,", "Be Vietnam Pro Black", Font.BOLD, 46, Color.WHITE, Component.CENTER_ALIGNMENT));
        centerContent.add(createLabel("một trải nghiệm", "Be Vietnam Pro Black", Font.BOLD, 46, Color.WHITE, Component.CENTER_ALIGNMENT));
        centerContent.add(createLabel("trọn vẹn.", "Be Vietnam Pro Black", Font.BOLD, 46, new Color(201, 142, 84), Component.CENTER_ALIGNMENT));
        centerContent.add(Box.createVerticalStrut(20));

        centerWrapper.add(centerContent);
        leftPanel.add(centerWrapper, BorderLayout.CENTER);
        return leftPanel;
    }

    private JPanel createLoginPanel() {
        JPanel rightWrapper = new JPanel(new GridBagLayout());
        rightWrapper.setBackground(Color.WHITE);

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setOpaque(false);

        txtLoginEmail = new CTextField(PLACEHOLDER_EMAIL, 600, 50);
        txtLoginEmail.setAlignmentX(Component.LEFT_ALIGNMENT);

        txtLoginPassword = new CPasswordField("••••••••", 600, 50);
        txtLoginPassword.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel pnlOptions = new JPanel(new BorderLayout());
        pnlOptions.setBackground(Color.WHITE);
        pnlOptions.setMaximumSize(new Dimension(600, 50));
        pnlOptions.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblForgot = createLabel("Quên mật khẩu?", "Be Vietnam Pro Black", Font.BOLD, 14, Utils.TERRACOTTA, Component.LEFT_ALIGNMENT);
        lblForgot.setCursor(new Cursor(Cursor.HAND_CURSOR));
        lblForgot.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                String typed = txtLoginEmail.getText().trim();
                txtForgotEmail.setText(typed.equals(PLACEHOLDER_EMAIL) ? "" : typed);
                cardLayout.show(rightCardPanel, CARD_FORGOT);
            }
        });
        pnlOptions.add(lblForgot, BorderLayout.EAST);

        btnLogin = new CButton("Đăng nhập", 600, 50);
        btnLogin.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnLogin.addActionListener(e -> handleLogin());

        form.add(createTitleHeader("Chào mừng trở lại"));
        form.add(Box.createVerticalStrut(8));
        form.add(createLabel("Đăng nhập để tiếp tục vào hệ thống nhà hàng An Nhiên", "Inter", Font.PLAIN, 22, GRAY, Component.LEFT_ALIGNMENT));
        form.add(Box.createVerticalStrut(26));
        form.add(createSeparator());
        form.add(Box.createVerticalStrut(25));
        form.add(createLabel("Email hoặc số điện thoại", "Be Vietnam Pro Black", Font.BOLD, 15, Color.BLACK, Component.LEFT_ALIGNMENT));
        form.add(Box.createVerticalStrut(8));
        form.add(txtLoginEmail);
        form.add(Box.createVerticalStrut(20));
        form.add(createLabel("Mật khẩu", "Be Vietnam Pro Black", Font.BOLD, 15, Color.BLACK, Component.LEFT_ALIGNMENT));
        form.add(Box.createVerticalStrut(8));
        form.add(txtLoginPassword);
        form.add(Box.createVerticalStrut(15));
        form.add(pnlOptions);
        form.add(Box.createVerticalStrut(45));
        form.add(btnLogin);
        form.add(Box.createVerticalStrut(30));

        rightWrapper.add(form);
        return rightWrapper;
    }

    private JPanel createForgotPasswordPanel() {
        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setBackground(Color.WHITE);

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));

        txtForgotEmail = new CTextField("Email nhận mã", 600, 48);
        txtForgotEmail.setAlignmentX(Component.LEFT_ALIGNMENT);

        btnSendResetCode = new CButton("Gửi mã xác minh", 600, 48);
        btnSendResetCode.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnSendResetCode.addActionListener(e -> requestResetCode());

        txtForgotOtp = new CTextField("Nhập mã 6 chữ số", 600, 48);
        txtForgotOtp.setAlignmentX(Component.LEFT_ALIGNMENT);

        txtForgotNewPassword = new CPasswordField("Mật khẩu mới", 600, 48);
        txtForgotNewPassword.setAlignmentX(Component.LEFT_ALIGNMENT);

        txtForgotConfirmPassword = new CPasswordField("Nhập lại mật khẩu", 600, 48);
        txtForgotConfirmPassword.setAlignmentX(Component.LEFT_ALIGNMENT);

        btnConfirmReset = new CButton("Đặt lại mật khẩu", 600, 48);
        btnConfirmReset.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnConfirmReset.addActionListener(e -> confirmResetPassword());

        setForgotStepEnabled(false);

        form.add(createTitleHeader("Quên mật khẩu?"));
        form.add(Box.createVerticalStrut(12));
        form.add(createLabel("Nhập email đã đăng ký để nhận mã OTP qua email.", "Inter", Font.PLAIN, 22, GRAY, Component.LEFT_ALIGNMENT));
        form.add(Box.createVerticalStrut(24));
        form.add(createLabel("Email đã đăng ký", "Be Vietnam Pro Black", Font.BOLD, 15, Color.BLACK, Component.LEFT_ALIGNMENT));
        form.add(Box.createVerticalStrut(8));
        form.add(txtForgotEmail);
        form.add(Box.createVerticalStrut(12));
        form.add(btnSendResetCode);
        form.add(Box.createVerticalStrut(18));
        form.add(createLabel("Mã OTP 6 chữ số", "Be Vietnam Pro Black", Font.BOLD, 15, Color.BLACK, Component.LEFT_ALIGNMENT));
        form.add(Box.createVerticalStrut(8));
        form.add(txtForgotOtp);
        form.add(Box.createVerticalStrut(14));
        form.add(createLabel("Mật khẩu mới", "Be Vietnam Pro Black", Font.BOLD, 15, Color.BLACK, Component.LEFT_ALIGNMENT));
        form.add(Box.createVerticalStrut(8));
        form.add(txtForgotNewPassword);
        form.add(Box.createVerticalStrut(14));
        form.add(createLabel("Xác nhận mật khẩu mới", "Be Vietnam Pro Black", Font.BOLD, 15, Color.BLACK, Component.LEFT_ALIGNMENT));
        form.add(Box.createVerticalStrut(8));
        form.add(txtForgotConfirmPassword);
        form.add(Box.createVerticalStrut(22));
        form.add(btnConfirmReset);
        form.add(Box.createVerticalStrut(20));
        form.add(createSwitchAuthPanel("Đã nhớ mật khẩu? ", "Quay lại đăng nhập", CARD_LOGIN));

        wrapper.add(form);
        return wrapper;
    }

    private void setForgotStepEnabled(boolean enabled) {
        txtForgotOtp.setEnabled(enabled);
        txtForgotNewPassword.setEnabled(enabled);
        txtForgotConfirmPassword.setEnabled(enabled);
        btnConfirmReset.setEnabled(enabled);
    }

    private void requestResetCode() {
        String email = txtForgotEmail.getText().trim();
        if (!email.contains("@")) {
            CToast.warning(this, "Vui lòng nhập địa chỉ email hợp lệ.");
            return;
        }
        if (!authBUS.requestPasswordReset(email)) {
            CToast.error(this, "Không thể kết nối Server để gửi mã.");
            return;
        }
        btnSendResetCode.setEnabled(false);
    }

    private void confirmResetPassword() {
        String email = txtForgotEmail.getText().trim();
        String otp = txtForgotOtp.getText().trim();
        String password = new String(txtForgotNewPassword.getPassword());
        String confirm = new String(txtForgotConfirmPassword.getPassword());

        if (!otp.matches("[0-9]{6}")) {
            CToast.warning(this, "Mã OTP phải gồm đúng 6 chữ số.");
            return;
        }
        if (password.length() < 8 || password.length() > 72) {
            CToast.warning(this, "Mật khẩu mới phải từ 8 đến 72 ký tự.");
            return;
        }
        if (!password.equals(confirm)) {
            CToast.warning(this, "Mật khẩu xác nhận không trùng khớp.");
            return;
        }
        if (!authBUS.confirmPasswordReset(email, otp, password)) {
            CToast.error(this, "Không thể gửi yêu cầu đặt lại mật khẩu tới Server.");
            return;
        }
        btnConfirmReset.setEnabled(false);
    }

    private void handleLogin() {
        String account = txtLoginEmail.getText().trim();
        String pass = new String(txtLoginPassword.getPassword()).trim();

        if (account.isEmpty() || account.equals(PLACEHOLDER_EMAIL) || pass.isEmpty()) {
            CToast.show(this, "Vui lòng nhập Email/SĐT và Mật khẩu!", "Nhắc nhở", CToast.WARNING);
            return;
        }
        if (!authBUS.login(account, pass)) {
            CToast.show(this, "Không kết nối được Server.\nVui lòng kiểm tra ServerApp tại cổng 8888.", "Lỗi kết nối", CToast.ERROR);
        }
    }

    private void onAuthEvent(AuthBUS.Event event) {
        if (event == null) return;
        switch (event.type) {
            case RESET_CODE -> {
                btnSendResetCode.setEnabled(true);
                if (event.success) {
                    setForgotStepEnabled(true);
                    btnSendResetCode.setText("Gửi lại mã xác minh");
                    CToast.info(this, event.message);
                } else {
                    CToast.error(this, event.message);
                }
            }
            case RESET_PASSWORD -> {
                btnConfirmReset.setEnabled(true);
                if (event.success) {
                    CToast.success(this, "Đổi mật khẩu thành công. Hãy đăng nhập lại.");
                    txtLoginEmail.setText(txtForgotEmail.getText().trim());
                    txtLoginPassword.setText("");
                    txtForgotOtp.setText("");
                    txtForgotNewPassword.setText("");
                    txtForgotConfirmPassword.setText("");
                    setForgotStepEnabled(false);
                    cardLayout.show(rightCardPanel, CARD_LOGIN);
                } else {
                    CToast.warning(this, event.message);
                }
            }
            case LOGIN -> {
                if (!event.success) {
                    CToast.error(this, event.message);
                } else if (event.user == null || event.user.getRoleName() == null) {
                    CToast.error(this, "Server không trả tài khoản hoặc quyền hợp lệ.");
                } else {
                    openRoleScreen(event.user);
                }
            }
            default -> {
                if (!event.success) CToast.error(this, event.message);
            }
        }
    }

    private void openRoleScreen(UserDTO user) {
        String role = user.getRoleName().toUpperCase(Locale.ROOT);
        SocketClient session = authBUS.getSocketClient();
        responseTimer.stop();

        JFrame next = switch (role) {
            case "STAFF" -> new StaffGUI(user, session);
            case "KITCHEN" -> new KitchenGUI(user, session);
            default -> null;
        };

        if (next != null) {
            next.setVisible(true);
            dispose();
            return;
        }

        switch (role) {
            case "ADMIN" -> CToast.warning(this, "Quản trị viên đăng nhập trên ServerApp (máy trung tâm).");
            case "CUSTOMER" -> CToast.warning(this, "Khách gọi món bằng thiết bị đã được nhân viên bàn giao.");
            default -> CToast.error(this, "Vai trò không được hỗ trợ: " + role);
        }
        resetAuthenticatedLogin();
    }

    private void resetAuthenticatedLogin() {
        responseTimer.stop();
        authBUS.close();
        new AuthGUI().setVisible(true);
        dispose();
    }

    private JPanel createTitleHeader(String text) {
        JLabel lblTitle = createLabel(text, "Be Vietnam Pro Black", Font.BOLD, 40, Color.BLACK, Component.LEFT_ALIGNMENT);
        JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        wrapper.setOpaque(false);
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.add(lblTitle);
        return wrapper;
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

        JLabel lblPrompt = createLabel(promptText, "Be Vietnam Pro Black", Font.PLAIN, 14, GRAY, Component.LEFT_ALIGNMENT);
        JLabel lblLink = createLabel(linkText, "Be Vietnam Pro Black", Font.BOLD, 14, Utils.TERRACOTTA, Component.LEFT_ALIGNMENT);
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

    private void loadPasswordIconsViaSocket() {
        new SwingWorker<BufferedImage[], Void>() {
            @Override
            protected BufferedImage[] doInBackground() {
                return new BufferedImage[]{
                        ImageSocketClient.fetchImage("things/eye.png"),
                        ImageSocketClient.fetchImage("things/eye_off.png")
                };
            }

            @Override
            protected void done() {
                try {
                    BufferedImage[] images = get();
                    ImageIcon iconShow = images[0] == null ? null : new ImageIcon(
                            images[0].getScaledInstance(20, 20, Image.SCALE_SMOOTH));
                    ImageIcon iconHide = images[1] == null ? null : new ImageIcon(
                            images[1].getScaledInstance(20, 20, Image.SCALE_SMOOTH));

                    for (CPasswordField field : new CPasswordField[]{
                            txtLoginPassword, txtForgotNewPassword, txtForgotConfirmPassword}) {
                        if (field != null) field.setVisibilityIcons(iconShow, iconHide);
                    }
                } catch (Exception ex) {
                    System.err.println("[AUTH-ICON] Lỗi nhận icon mật khẩu qua TCP: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private JLabel createSocketLogo(String imageKey, int size) {
        JLabel label = new JLabel("AN", SwingConstants.CENTER);
        Dimension dimension = new Dimension(size, size);
        label.setPreferredSize(dimension);
        label.setMinimumSize(dimension);
        label.setMaximumSize(dimension);
        label.setForeground(Color.WHITE);
        label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(12, size / 3)));

        new SwingWorker<BufferedImage, Void>() {
            @Override
            protected BufferedImage doInBackground() {
                return ImageSocketClient.fetchImage(imageKey);
            }

            @Override
            protected void done() {
                try {
                    BufferedImage received = get();
                    if (received == null) return;
                    label.setIcon(new ImageIcon(received.getScaledInstance(size, size, Image.SCALE_SMOOTH)));
                    label.setText("");
                    if ("things/logo.png".equals(imageKey)) setIconImage(received);
                    label.revalidate();
                    label.repaint();
                } catch (Exception ex) {
                    System.err.println("[LOGO-TCP] Lỗi " + imageKey + ": " + ex.getMessage());
                }
            }
        }.execute();
        return label;
    }
}