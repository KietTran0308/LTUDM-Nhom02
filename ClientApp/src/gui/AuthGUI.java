package gui;

import bus.AuthBUS;
import dto.SocketAction;
import dto.SocketPacket;
import dto.UserDTO;
import components.*;
import utils.Utils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.Locale;

import api.SocketClient;

public class AuthGUI extends JFrame {

    private static final String CARD_LOGIN = "LOGIN";
    private static final String CARD_REGISTER = "REGISTER";
    private static final String CARD_FORGOT = "FORGOT";
    // Shared ordering device: employee pairs once, guest enters ONLY own credentials.
    private Long pairedTableId;
    private String pairedTableCode;
    private JLabel pairedDeviceHint;
    private static final Color BRAND_ORANGE = new Color(206, 93, 52);

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel rightCardPanel = new JPanel(cardLayout);
    private final AuthBUS authBUS;

    private Timer responseTimer;

    private JCheckBox chkRemember;
    private CButton btnLogin, btnRegister;

    private CTextField txtLoginEmail, txtRegFullName, txtRegEmail, txtRegPhone;
    private CPasswordField txtLoginPassword, txtRegPassword, txtRegConfirmPassword;
    private CTextField txtForgotEmail, txtForgotOtp;
    private CPasswordField txtForgotNewPassword, txtForgotConfirmPassword;
    private CButton btnSendResetCode, btnConfirmReset;

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
        rightCardPanel.add(createForgotPasswordPanel(), CARD_FORGOT);
        add(rightCardPanel);

        addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                if (btnLogin != null) {
                    btnLogin.requestFocusInWindow();
                }
            }
        });

        authBUS = new AuthBUS();
        startResponseTimer();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                if (responseTimer != null) responseTimer.stop();
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
        lblForgot.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                txtForgotEmail.setText(txtLoginEmail.getText().trim().equals("name@bepnha.vn")
                        ? "" : txtLoginEmail.getText().trim());
                cardLayout.show(rightCardPanel, CARD_FORGOT);
            }
        });

        pnlOptions.add(chkRemember, BorderLayout.WEST);
        pnlOptions.add(lblForgot, BorderLayout.EAST);

        pairedDeviceHint = new JLabel("Thiết bị chưa được nhân viên liên kết với bàn.");
        pairedDeviceHint.setFont(new Font("SansSerif", Font.PLAIN, 13));
        pairedDeviceHint.setForeground(new Color(108, 114, 128));
        pairedDeviceHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton btnPairDevice = new JButton("Thiết lập bàn - dành cho nhân viên");
        btnPairDevice.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnPairDevice.addActionListener(e -> showEmployeePairingDialog());

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
        formContainer.add(Box.createVerticalStrut(10));
        formContainer.add(pairedDeviceHint);
        formContainer.add(Box.createVerticalStrut(8));
        formContainer.add(btnPairDevice);
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

    /**
     * Màn hình đặt lại mật khẩu, dùng cùng CardLayout để không mở JFrame khác.
     */
    private JPanel createForgotPasswordPanel() {
        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setBackground(Color.WHITE);
        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));

        form.add(createTitleHeader("Quên mật khẩu?"));
        form.add(Box.createVerticalStrut(12));
        JLabel help = createLabel("Nhập email đã đăng ký để nhận mã OTP qua email.",
                "Inter", Font.PLAIN, 15, Color.GRAY, Component.LEFT_ALIGNMENT);
        form.add(help);
        form.add(Box.createVerticalStrut(24));

        form.add(createFieldLabel("Email đã đăng ký"));
        form.add(Box.createVerticalStrut(8));
        txtForgotEmail = new CTextField("Email nhận mã", 470, 48);
        txtForgotEmail.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(txtForgotEmail);
        form.add(Box.createVerticalStrut(12));

        btnSendResetCode = new CButton("Gửi mã xác minh", 470, 48);
        btnSendResetCode.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnSendResetCode.addActionListener(e -> requestResetCode());
        form.add(btnSendResetCode);
        form.add(Box.createVerticalStrut(18));

        form.add(createFieldLabel("Mã OTP (6 chữ số)"));
        form.add(Box.createVerticalStrut(8));
        txtForgotOtp = new CTextField("Nhập mã 6 chữ số", 470, 48);
        txtForgotOtp.setAlignmentX(Component.LEFT_ALIGNMENT);
        txtForgotOtp.setEnabled(false);
        form.add(txtForgotOtp);
        form.add(Box.createVerticalStrut(14));

        form.add(createFieldLabel("Mật khẩu mới (8–72 ký tự)"));
        form.add(Box.createVerticalStrut(8));
        txtForgotNewPassword = new CPasswordField("Mật khẩu mới", 470, 48);
        txtForgotNewPassword.setAlignmentX(Component.LEFT_ALIGNMENT);
        txtForgotNewPassword.setEnabled(false);
        form.add(txtForgotNewPassword);
        form.add(Box.createVerticalStrut(14));

        form.add(createFieldLabel("Xác nhận mật khẩu mới"));
        form.add(Box.createVerticalStrut(8));
        txtForgotConfirmPassword = new CPasswordField("Nhập lại mật khẩu", 470, 48);
        txtForgotConfirmPassword.setAlignmentX(Component.LEFT_ALIGNMENT);
        txtForgotConfirmPassword.setEnabled(false);
        form.add(txtForgotConfirmPassword);
        form.add(Box.createVerticalStrut(22));

        btnConfirmReset = new CButton("Đặt lại mật khẩu", 470, 48);
        btnConfirmReset.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnConfirmReset.setEnabled(false);
        btnConfirmReset.addActionListener(e -> confirmResetPassword());
        form.add(btnConfirmReset);
        form.add(Box.createVerticalStrut(20));
        form.add(createSwitchAuthPanel("Đã nhớ mật khẩu? ", "Quay lại đăng nhập", CARD_LOGIN));
        wrapper.add(form);
        return wrapper;
    }

    private void requestResetCode() {
        String email = txtForgotEmail.getText().trim();
        if (email.isEmpty() || !email.contains("@")) {
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

        if (account.isEmpty() || account.equals("name@bepnha.vn") || pass.isEmpty()) {
            CToast.show(this, "Vui lòng nhập Email/SĐT và Mật khẩu!", "Nhắc nhở", CToast.WARNING);
            return;
        }

        if (!authBUS.login(account, pass)) {
            CToast.show(
                    this,
                    "Không kết nối được Server.\n"
                            + "Vui lòng kiểm tra ServerApp tại cổng 8888.",
                    "Lỗi kết nối",
                    CToast.ERROR
            );
            return;
        }
    }

    private void handleRegister() {
        String fullName = txtRegFullName.getText().trim();
        String email = txtRegEmail.getText().trim();
        String phone = txtRegPhone.getText().trim();
        String pass = new String(txtRegPassword.getPassword()).trim();
        String confirm = new String(txtRegConfirmPassword.getPassword()).trim();

        if (fullName.isEmpty() || email.isEmpty() || pass.isEmpty()) {
            CToast.show(this, "Vui lòng điền đầy đủ thông tin bắt buộc!", "Lỗi", CToast.WARNING);
            return;
        }
        if (!pass.equals(confirm)) {
            CToast.show(this, "Mật khẩu xác nhận không khớp!", "Lỗi", CToast.ERROR);
            return;
        }
        if (!authBUS.register(fullName, email, phone, pass)) {
            CToast.show(this, "Không kết nối được ServerApp (Port 8888)!", "Lỗi mạng", CToast.ERROR);
        }
    }

    private void onAuthServerResponse(SocketPacket packet) {
        if (packet.getAction() == SocketAction.REQUEST_PASSWORD_RESET) {
            btnSendResetCode.setEnabled(true);
            if (packet.isSuccess()) {
                txtForgotOtp.setEnabled(true);
                txtForgotNewPassword.setEnabled(true);
                txtForgotConfirmPassword.setEnabled(true);
                btnConfirmReset.setEnabled(true);
                btnSendResetCode.setText("Gửi lại mã (sau 60 giây)");
                CToast.info(this, packet.getMessage());
            } else {
                CToast.error(this, packet.getMessage());
            }
            return;
        }
        if (packet.getAction() == SocketAction.CONFIRM_PASSWORD_RESET) {
            btnConfirmReset.setEnabled(true);
            if (packet.isSuccess()) {
                CToast.success(this, "Đổi mật khẩu thành công. Hãy đăng nhập lại.");
                txtLoginEmail.setText(txtForgotEmail.getText().trim());
                txtLoginPassword.setText("");
                txtForgotOtp.setText("");
                txtForgotNewPassword.setText("");
                txtForgotConfirmPassword.setText("");
                txtForgotOtp.setEnabled(false);
                txtForgotNewPassword.setEnabled(false);
                txtForgotConfirmPassword.setEnabled(false);
                btnConfirmReset.setEnabled(false);
                cardLayout.show(rightCardPanel, CARD_LOGIN);
            } else {
                CToast.warning(this, packet.getMessage());
            }
            return;
        }
        if (packet.getAction() == SocketAction.REGISTER) {
            if (packet.isSuccess()) {
                CToast.success(this, "Đăng ký thành công. Vui lòng đăng nhập.");
                txtLoginEmail.setText(txtRegEmail.getText());
                cardLayout.show(rightCardPanel, CARD_LOGIN);
            } else {
                CToast.error(this, packet.getMessage());
            }
            return;
        }
        if (packet.getAction() != SocketAction.LOGIN) return;
        if (!packet.isSuccess()) {
            CToast.error(this, packet.getMessage());
            return;
        }
        if (!(packet.getData() instanceof UserDTO)) {
            CToast.error(this, "Server không trả thông tin người dùng hợp lệ.");
            return;
        }
        UserDTO user = (UserDTO) packet.getData();
        String role = user.getRoleName();
        if (role == null) {
            CToast.error(this, "Tài khoản chưa được phân quyền.");
            return;
        }
        SocketClient session = authBUS.getSocketClient();
        JFrame next;
        switch (role.toUpperCase(Locale.ROOT)) {
            case "STAFF":
            case "ADMIN":
                next = new StaffGUI(user, session);
                break;
            case "KITCHEN":
                next = new KitchenGUI(user, session);
                break;
            case "CUSTOMER":
                // The staff pairs the shared device before the customer logs in.
                if (pairedTableId == null || pairedTableCode == null) {
                    CToast.warning(this, "Nhờ nhân viên thiết lập bàn trên thiết bị trước khi đăng nhập.");
                    resetAuthenticatedLogin();
                    return;
                }
                next = new CustomerGUI(pairedTableId, user.getId(), session, pairedTableCode);
                pairedTableCode = null; // CustomerGUI already issued REGISTER_TABLE.
                pairedTableId = null;
                break;
            default:
                CToast.error(this, "Vai trò không được hỗ trợ: " + role);
                resetAuthenticatedLogin();
                return;
        }
        responseTimer.stop();
        next.setVisible(true);
        dispose();
    }

    /** Staff enters pairing data received from STAFF POS; customer never sees token. */
    private void showEmployeePairingDialog() {
        JTextField tableField = new JTextField(16);
        JPasswordField codeField = new JPasswordField(16);
        JPanel panel = new JPanel(new GridLayout(2, 2, 8, 8));
        panel.add(new JLabel("Mã bàn:"));
        panel.add(tableField);
        panel.add(new JLabel("Mã ghép từ StaffGUI:"));
        panel.add(codeField);
        int result = JOptionPane.showConfirmDialog(this, panel,
                "Thiết lập thiết bị dùng chung (nhân viên)",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;
        try {
            long id = Long.parseLong(tableField.getText().trim());
            String code = new String(codeField.getPassword()).trim();
            java.util.Arrays.fill(codeField.getPassword(), '\0');
            if (id <= 0 || !code.matches("[A-Za-z0-9-]{10,15}")) throw new IllegalArgumentException();
            pairedTableId = id;
            pairedTableCode = code;
            pairedDeviceHint.setText("Thiết bị đã chọn bàn #" + id + " — chờ Server xác minh sau đăng nhập.");
            CToast.info(this, "Đã nhập thông tin bàn. Mời khách tự đăng nhập.");
        } catch (IllegalArgumentException e) {
            CToast.warning(this, "Thông tin bàn/mã ghép không hợp lệ. Nhờ nhân viên kiểm tra.");
        }
    }

    private void resetAuthenticatedLogin() {
        if (responseTimer != null) responseTimer.stop();
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

    private void startResponseTimer() {
        responseTimer = new Timer(100, e -> {
            SocketPacket packet;

            while ((packet = authBUS.pollPacket()) != null) {
                handleServerResponse(packet);
            }
        });

        responseTimer.start();
    }

    private void handleServerResponse(SocketPacket packet) {
        onAuthServerResponse(packet);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new AuthGUI().setVisible(true));
    }
}