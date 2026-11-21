package api;

import bus.*;
import dto.*;
import org.json.JSONObject;
import security.AESGCMUtil;
import security.RSAKeyExchange;
import security.RSAKeyManager;

import javax.crypto.SecretKey;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private DataOutputStream out;
    private DataInputStream in;
    private SecretKey sessionKey;
    private Long currentTableId;
    private final RSAKeyManager rsaKeyManager;

    private static final int MAX_PACKET_SIZE = 1024 * 1024;

    private final FoodBUS foodBUS = new FoodBUS();
    private final OrderBUS orderBUS = new OrderBUS();
    private final AuthBUS authBUS = new AuthBUS();
    private final AdminBUS adminBUS = new AdminBUS();

    private UserDTO authenticatedUser;
    private boolean customerMode;
    // LOGIN trên máy POS/Admin; khác với LOGIN_STAFF trên iPad phục vụ.
    private boolean workstationLogin;
    private Long pairedSessionId;
    private int failedPairAttempts;

    public ClientHandler(Socket socket, RSAKeyManager rsaKeyManager) {
        this.socket = socket;
        this.rsaKeyManager = rsaKeyManager;
    }

    private void writeFrame(DataOutputStream output, byte[] data) throws IOException {
        if (data == null || data.length <= 0 || data.length > MAX_PACKET_SIZE) {
            throw new IOException("Kích thước gói tin gửi đi không hợp lệ");
        }
        output.writeInt(data.length);
        output.write(data);
        output.flush();
    }

    private byte[] readFrame(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length <= 0 || length > MAX_PACKET_SIZE) {
            throw new IOException("Kích thước gói tin không hợp lệ");
        }
        byte[] data = new byte[length];
        input.readFully(data);
        return data;
    }

    private void writeFrame(DataOutputStream output, String data) throws IOException {
        writeFrame(output, data.getBytes(StandardCharsets.UTF_8));
    }

    private String readFrameAsString(DataInputStream input) throws IOException {
        return new String(readFrame(input), StandardCharsets.UTF_8);
    }

    private boolean hasRole(String... roles) {
        if (authenticatedUser == null || authenticatedUser.getRoleName() == null) return false;
        for (String role : roles) {
            if (role.equalsIgnoreCase(authenticatedUser.getRoleName())) return true;
        }
        return false;
    }

    private void requireRole(String... roles) {
        if (!hasRole(roles)) throw new SecurityException("Không có quyền thực hiện thao tác này");
    }

    private void checkFood(FoodDTO f, boolean update) {
        if (f == null || (update && (f.getId() == null || f.getId() <= 0))
                || (!update && (f.getSku() == null || f.getSku().isBlank()))
                || f.getName() == null || f.getName().isBlank()
                || f.getPrice() == null || f.getPrice().signum() < 0
                || f.getCategoryId() == null || f.getCategoryId() <= 0)
            throw new IllegalArgumentException("Thông tin món ăn không hợp lệ");
    }

    private void requireWorkstationRole(String... roles) {
        requireRole(roles);
        if (!workstationLogin || customerMode)
            throw new SecurityException("Hãy đăng nhập từ ứng dụng POS/Admin, không phải iPad phục vụ");
        String ips = System.getenv("WORKSTATION_ALLOWED_IPS");
        if (ips != null && !ips.isBlank()) {
            String remote = socket.getInetAddress().getHostAddress();
            boolean allowed = java.util.Arrays.stream(ips.split(","))
                    .map(String::trim).anyMatch(remote::equals);
            if (!allowed) throw new SecurityException("Thiết bị này chưa được cấp phép POS/Admin");
        }
    }

    private void requireCurrentTable(Long requestedTableId) throws Exception {
        if (!customerMode || pairedSessionId == null || currentTableId == null ||
                (requestedTableId != null && !currentTableId.equals(requestedTableId)) ||
                !orderBUS.isActiveTableSession(currentTableId, pairedSessionId)) {
            throw new SecurityException("Thiet bi chua vao CUSTOMER MODE hoac phien da dong");
        }
    }

    private SocketPacket ok(SocketAction action, String message, Object data, Object extra) {
        return new SocketPacket(action, true, message, data, extra);
    }

    @Override
    public void run() {
        try {
            in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

            socket.setSoTimeout(5000);

            String publicKeyString = RSAKeyExchange.publicKeyToString(rsaKeyManager.getPublicKey());
            writeFrame(out, publicKeyString);

            String encryptedAESKey = readFrameAsString(in);
            sessionKey = RSAKeyExchange.decryptAESKey(encryptedAESKey, rsaKeyManager.getPrivateKey());

            writeFrame(out, AESGCMUtil.encrypt("OK".getBytes(StandardCharsets.US_ASCII), sessionKey));
            System.out.println("[Server] Trao đổi khóa AES thành công!");

            socket.setSoTimeout(0);

            while (!socket.isClosed()) {
                byte[] encryptedData = readFrame(in);
                byte[] decryptedData = AESGCMUtil.decrypt(encryptedData, sessionKey);
                String rawJson = new String(decryptedData, StandardCharsets.UTF_8);

                if (rawJson.startsWith("IMG/2 ")) {
                    ImageSocketServer.sendImage(rawJson.substring(6), out, sessionKey);
                    break;
                }

                SocketPacket request = SocketJsonMapper.fromJsonString(rawJson);
                System.out.println("[Server] Nhận action: " + request.getAction()
                        + " | peer=" + socket.getRemoteSocketAddress());

                handleClientRequest(request);
            }

        } catch (Exception e) {
            System.err.println("[ClientHandler] Kết nối " + socket.getRemoteSocketAddress()
                    + " đã đóng: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            e.printStackTrace();
        } finally {
            SocketServer.removeConnection(this);
            closeResources();
        }
    }

    private void handleClientRequest(SocketPacket req) {
        try {
            switch (req.getAction()) {
                case LIST_TABLES: {
                    requireRole("STAFF");
                    List<TableDTO> tables = orderBUS.getAllRestaurantTables();
                    System.out.println("[Server] LIST_TABLES: trả " + tables.size()
                            + " bàn cho nhân viên #" + authenticatedUser.getId());
                    sendPacket(ok(SocketAction.LIST_TABLES, "Danh sach ban va phien dang hoat dong",
                            tables, null));
                    break;
                }
                case ENTER_CUSTOMER_MODE: {
                    requireRole("STAFF");
                    if (!(req.getData() instanceof Long) || req.getTableId() == null) {
                        throw new IllegalArgumentException("Can tableId va sessionId");
                    }
                    long sid = (Long) req.getData();
                    long tid = req.getTableId();
                    if (!orderBUS.sessionBelongsToTable(tid, sid)) {
                        throw new SecurityException("Ban chua duoc POS mo phien ACTIVE");
                    }
                    SocketServer.removeConnection(this);
                    authenticatedUser = null;
                    customerMode = true;
                    workstationLogin = false;
                    currentTableId = tid;
                    pairedSessionId = sid;
                    SocketServer.registerSession(sid, this);
                    SocketPacket result = ok(SocketAction.ENTER_CUSTOMER_MODE,
                            "Da ban giao thiet bi cho khach", sid, null);
                    result.setTableId(tid);
                    sendPacket(result);
                    break;
                }

                case GET_MENU_AND_CATEGORIES: {
                    requireCurrentTable(req.getTableId());
                    try {
                        List<FoodDTO> foods = foodBUS.getPublishedFoodsForTablet();
                        List<CategoryDTO> categories = foodBUS.getAllCategories();
                        System.out.println("[MENU] Ban=" + currentTableId + ", session=" + pairedSessionId
                                + ", mon_dang_ban=" + foods.size() + ", danh_muc=" + categories.size());
                        sendPacket(new SocketPacket(
                                SocketAction.GET_MENU_AND_CATEGORIES, true,
                                foods.isEmpty() ? "Không có món đang mở bán" : "Tải thực đơn thành công",
                                foods, categories
                        ));
                    } catch (Exception menuError) {
                        System.err.println("[MENU] Tai thuc don that bai, ban=" + currentTableId);
                        menuError.printStackTrace();
                        sendPacket(new SocketPacket(SocketAction.GET_MENU_AND_CATEGORIES, false,
                                "Lỗi tải thực đơn tại Server. Kiểm tra MySQL/DB_URL và bảng foods, categories, food_images.",
                                null, null));
                    }
                    break;
                }

                case ORDER_SUBMIT: {
                    requireCurrentTable(req.getTableId());
                    OrderDTO incomingOrder = (OrderDTO) req.getData();
                    String voucherCode = (String) req.getExtraData();
                    if (incomingOrder == null) throw new IllegalArgumentException("Đơn không hợp lệ");
                    incomingOrder.setTableId(currentTableId);
                    incomingOrder.setTableSessionId(pairedSessionId);
                    incomingOrder.setUserId(null);

                    Long targetTableId = req.getTableId() != null ? req.getTableId() : this.currentTableId;
                    if (incomingOrder.getTableId() == null) {
                        incomingOrder.setTableId(targetTableId);
                    }

                    OrderDTO savedOrder = orderBUS.processIncomingTableOrder(incomingOrder, voucherCode);
                    List<OrderItemDTO> updatedTrackingList = orderBUS.getTableTrackingItems(targetTableId);

                    sendPacket(new SocketPacket(
                            req.getAction(), true,
                            "Đặt món thành công! Mã đơn: " + savedOrder.getOrderCode(),
                            savedOrder, updatedTrackingList
                    ));

                    SocketServer.broadcastOrderCreated(savedOrder);
                    SocketServer.broadcastKitchenQueue(orderBUS.getKitchenQueue());
                    break;
                }

                case GET_TABLE_TRACKING: {
                    requireCurrentTable(req.getTableId());
                    List<OrderItemDTO> trackedItems = orderBUS.getTableTrackingItems(req.getTableId());
                    sendPacket(new SocketPacket(
                            SocketAction.GET_TABLE_TRACKING, true,
                            "Đồng bộ trạng thái đơn thành công", trackedItems, null
                    ));
                    break;
                }

                case VIEW_SESSION_BILL: {
                    requireCurrentTable(req.getTableId());
                    OrderDTO preview = orderBUS.getTableBillPreview(currentTableId);
                    if (preview != null && !pairedSessionId.equals(preview.getTableSessionId())) {
                        throw new SecurityException("Hoa don khong thuoc phien hien tai");
                    }
                    sendPacket(ok(SocketAction.VIEW_SESSION_BILL,
                            "Tam tinh toan bo luot goi mon", preview, null));
                    break;
                }

                case CALL_STAFF: {
                    requireCurrentTable(req.getTableId());
                    SocketServer.notifyStaff(currentTableId, String.valueOf(req.getData()));
                    sendPacket(new SocketPacket(
                            SocketAction.CALL_STAFF, true,
                            "Nhân viên đã nhận được yêu cầu hỗ trợ của Bàn số " + req.getTableId(), null, null
                    ));
                    break;
                }

                case GET_ALL_TABLES: {
                    requireWorkstationRole("STAFF", "ADMIN");
                    PosBUS pos = new PosBUS(authenticatedUser);
                    sendPacket(ok(
                            SocketAction.GET_ALL_TABLES,
                            "Đã tải danh sách bàn",
                            pos.listTables(),
                            null));
                    break;
                }

                case OPEN_TABLE_SESSION: {
                    requireWorkstationRole("STAFF", "ADMIN");
                    Long tableId = req.getTableId();
                    if (tableId == null || tableId <= 0) {
                        throw new IllegalArgumentException("Mã bàn không hợp lệ");
                    }
                    PosBUS pos = new PosBUS(authenticatedUser);
                    long sessionId = pos.openTable(tableId);
                    sendPacket(ok(
                            SocketAction.OPEN_TABLE_SESSION,
                            "Đã mở phiên bàn",
                            sessionId,
                            null));
                    break;
                }

                case GET_TABLE_BILL_PREVIEW: {
                    requireWorkstationRole("STAFF", "ADMIN");
                    Long tableId = req.getTableId();
                    if (tableId == null || tableId <= 0) {
                        throw new IllegalArgumentException("Mã bàn không hợp lệ");
                    }
                    PosBUS pos = new PosBUS(authenticatedUser);
                    sendPacket(ok(
                            SocketAction.GET_TABLE_BILL_PREVIEW,
                            "Đã tải hóa đơn tạm tính",
                            pos.preview(tableId),
                            null));
                    break;
                }

                case MARK_ITEM_SERVED:
                    throw new IllegalStateException("Hãy xác nhận đã phục vụ trên màn hình bếp.");

                case PAY_TABLE_BILL: {
                    requireWorkstationRole("STAFF", "ADMIN");
                    Long tableId = req.getTableId();
                    BillDTO bill = (BillDTO) req.getData();

                    if (tableId == null || tableId <= 0 ||
                            bill == null ||
                            bill.getTableSessionId() == null ||
                            bill.getTableSessionId() <= 0 ||
                            bill.getPaymentMethod() == null) {
                        throw new IllegalArgumentException("Thông tin thanh toán không hợp lệ");
                    }

                    PosBUS pos = new PosBUS(authenticatedUser);
                    pos.pay(tableId, bill, (String) req.getExtraData());

                    sendPacket(ok(
                            SocketAction.PAY_TABLE_BILL,
                            "Thanh toán và đóng phiên thành công",
                            null,
                            null));
                    break;
                }

                case GET_KITCHEN_QUEUE: {
                    requireRole("KITCHEN");
                    sendPacket(new SocketPacket(SocketAction.GET_KITCHEN_QUEUE, true,
                            "Đã tải danh sách bếp", orderBUS.getKitchenQueue(), null));
                    break;
                }

                case UPDATE_ITEM_STATUS: {
                    requireRole("KITCHEN");
                    Long itemId = (Long) req.getData();
                    dbEnums.OrderStatus next = (dbEnums.OrderStatus) req.getExtraData();
                    if (itemId == null || itemId <= 0 || (next != dbEnums.OrderStatus.RECEIVED &&
                            next != dbEnums.OrderStatus.COOKING && next != dbEnums.OrderStatus.COMPLETED &&
                            next != dbEnums.OrderStatus.SERVED)) {
                        throw new IllegalArgumentException("Mon hoac trang thai khong hop le");
                    }
                    Long table = orderBUS.getTableIdByOrderItem(itemId);
                    if (table == null || !orderBUS.changeDishStatusByKitchen(itemId, next)) {
                        throw new IllegalStateException("Trang thai mon khong cho phep chuyen sang " + next);
                    }
                    OrderDTO updatedOrder = orderBUS.getOrderByItem(itemId);
                    sendPacket(ok(req.getAction(), "Da doi trang thai mon", itemId, next));
                    if (updatedOrder != null && updatedOrder.getTableSessionId() != null) {
                        SocketServer.broadcastItemStatus(updatedOrder.getTableSessionId(), itemId, next);
                    }
                    if (next == dbEnums.OrderStatus.COMPLETED) {
                        OrderDTO finished = orderBUS.getCompletedOrderIfReady(itemId);
                        if (finished != null) SocketServer.broadcastOrderCompleted(finished);
                    }
                    SocketServer.broadcastKitchenQueue(orderBUS.getKitchenQueue());
                    break;
                }

                case ADMIN_GET_FOODS: {
                    requireWorkstationRole("ADMIN");
                    sendPacket(ok(req.getAction(), "Danh sách món", adminBUS.getAllFoods(), null));
                    break;
                }
                case ADMIN_GET_CATEGORIES: {
                    requireWorkstationRole("ADMIN");
                    sendPacket(ok(req.getAction(), "Danh mục", adminBUS.getAllCategories(), null));
                    break;
                }
                case ADMIN_ADD_FOOD: {
                    requireWorkstationRole("ADMIN");
                    FoodDTO f = (FoodDTO) req.getData();
                    checkFood(f, false);
                    if (!adminBUS.addNewFood(f.getSku(), f.getCategoryId(), f.getName(),
                            f.getPrice(), f.getCoverImageUrl(), Boolean.TRUE.equals(f.getIsPublished())))
                        throw new IllegalStateException("Không thêm được món (SKU có thể đã tồn tại)");
                    sendPacket(ok(req.getAction(), "Đã thêm món", null, null));
                    break;
                }
                case ADMIN_UPDATE_FOOD: {
                    requireWorkstationRole("ADMIN");
                    FoodDTO f = (FoodDTO) req.getData();
                    checkFood(f, true);
                    if (!adminBUS.updateFood(f.getId(), f.getName(), f.getPrice(),
                            f.getCategoryId(), Boolean.TRUE.equals(f.getIsPublished())))
                        throw new IllegalStateException("Không cập nhật được món");
                    sendPacket(ok(req.getAction(), "Đã cập nhật món", null, null));
                    break;
                }
                case ADMIN_DELETE_FOOD: {
                    requireWorkstationRole("ADMIN");
                    Long id = (Long) req.getData();
                    if (id == null || id <= 0) throw new IllegalArgumentException("ID món không hợp lệ");
                    if (!adminBUS.deleteFood(id)) throw new IllegalStateException("Không ẩn được món");
                    sendPacket(ok(req.getAction(), "Đã ẩn món", null, null));
                    break;
                }

                case ADMIN_GET_BILLS: {
                    requireWorkstationRole("ADMIN");
                    if (req.getData() == null) {
                        sendPacket(ok(req.getAction(), "Danh sách hóa đơn", adminBUS.getAllBills(), null));
                    } else if (req.getData() instanceof JSONObject filters) {
                        AdminBUS.RevenueReport report = adminBUS.getRevenueReport(filters);
                        sendPacket(ok(req.getAction(), "Thống kê doanh thu", report.bills(), report.summary()));
                    } else {
                        throw new IllegalArgumentException("Bộ lọc thống kê không hợp lệ");
                    }
                    break;
                }
                case ADMIN_GET_ACCOUNTS: {
                    requireWorkstationRole("ADMIN");
                    sendPacket(ok(req.getAction(), "Danh sách tài khoản", adminBUS.getAllAccounts(), null));
                    break;
                }

                case ADMIN_CREATE_ACCOUNT: {
                    requireWorkstationRole("ADMIN");
                    UserDTO newAcc = (UserDTO) req.getData();
                    if (newAcc == null) throw new IllegalArgumentException("Dữ liệu tài khoản không hợp lệ");

                    boolean success = adminBUS.createNewAccount(
                            newAcc.getFullName(),
                            newAcc.getPhone(),
                            newAcc.getEmail(),
                            newAcc.getRoleID(),
                            newAcc.getRoleName()
                    );

                    if (!success) throw new IllegalStateException("Không thể cấp tài khoản mới");
                    sendPacket(ok(req.getAction(), "Đã cấp tài khoản thành công", null, null));
                    break;
                }

                case ADMIN_UPDATE_ACCOUNT: {
                    requireWorkstationRole("ADMIN");
                    UserDTO updAcc = (UserDTO) req.getData();
                    if (updAcc == null || updAcc.getId() == null)
                        throw new IllegalArgumentException("Dữ liệu không hợp lệ");

                    boolean success = adminBUS.updateAccount(
                            updAcc.getId(),
                            updAcc.getFullName(),
                            updAcc.getPhone(),
                            updAcc.getEmail(),
                            updAcc.getRoleName()
                    );

                    if (!success) throw new IllegalStateException("Cập nhật thông tin thất bại");
                    sendPacket(ok(req.getAction(), "Đã cập nhật tài khoản", null, null));
                    break;
                }

                case ADMIN_LOCK_ACCOUNT: {
                    requireWorkstationRole("ADMIN");
                    Long accId = (Long) req.getData();
                    if (accId == null || accId <= 0) throw new IllegalArgumentException("ID tài khoản không hợp lệ");

                    String mode = req.getExtraData() == null ? "" : String.valueOf(req.getExtraData());
                    boolean active;
                    if ("UNLOCK".equalsIgnoreCase(mode)) active = true;
                    else if ("LOCK".equalsIgnoreCase(mode)) active = false;
                    else throw new IllegalArgumentException("Thiếu trạng thái khóa/mở khóa");
                    boolean success = adminBUS.setAccountActive(accId, authenticatedUser.getId(), active);
                    if (!success) throw new IllegalStateException("Thao tác khóa/mở khóa thất bại");

                    sendPacket(ok(req.getAction(), "Đã thay đổi trạng thái tài khoản", null, null));
                    break;
                }

                case LOGOUT: {
                    SocketServer.removeConnection(this);
                    authenticatedUser = null;
                    currentTableId = null;
                    pairedSessionId = null;
                    customerMode = false;
                    workstationLogin = false;
                    failedPairAttempts = 0;
                    sendPacket(new SocketPacket(SocketAction.LOGOUT, true, "Đã đăng xuất", null, null));
                    break;
                }

                case REQUEST_PASSWORD_RESET: {
                    try {
                        authBUS.requestPasswordReset((String) req.getData());
                        sendPacket(new SocketPacket(
                                SocketAction.REQUEST_PASSWORD_RESET, true,
                                "Nếu email đã đăng ký, mã xác minh sẽ được gửi tới hộp thư. Vui lòng kiểm tra cả Spam.",
                                null, null));
                    } catch (IllegalArgumentException ex) {
                        sendPacket(new SocketPacket(SocketAction.REQUEST_PASSWORD_RESET, false,
                                ex.getMessage(), null, null));
                    } catch (IllegalStateException ex) {
                        System.err.println("Gửi OTP thất bại: " + ex.getClass().getSimpleName());
                        if ("Server chưa cấu hình SMTP".equals(ex.getMessage())) {
                            sendPacket(new SocketPacket(SocketAction.REQUEST_PASSWORD_RESET, false,
                                    "Server chưa cấu hình gửi email OTP. Vui lòng liên hệ quản trị.", null, null));
                        } else {
                            sendPacket(new SocketPacket(SocketAction.REQUEST_PASSWORD_RESET, true,
                                    "Nếu email đã đăng ký, bạn sẽ nhận mã xác minh. Hãy kiểm tra Spam hoặc thử lại sau.",
                                    null, null));
                        }
                    } catch (Exception ex) {
                        System.err.println("Gửi OTP thất bại: " + ex.getClass().getSimpleName());
                        sendPacket(new SocketPacket(SocketAction.REQUEST_PASSWORD_RESET, true,
                                "Nếu email đã đăng ký, bạn sẽ nhận mã xác minh. Hãy kiểm tra Spam hoặc thử lại sau.",
                                null, null));
                    }
                    break;
                }

                case CONFIRM_PASSWORD_RESET: {
                    try {
                        if (!(req.getData() instanceof java.util.Map<?, ?> body)) {
                            throw new IllegalArgumentException("Dữ liệu đặt lại mật khẩu không hợp lệ.");
                        }
                        boolean reset = authBUS.confirmPasswordReset(
                                (String) body.get("email"), (String) body.get("otp"),
                                (String) body.get("newPassword"));
                        sendPacket(new SocketPacket(SocketAction.CONFIRM_PASSWORD_RESET, reset,
                                reset ? "Đổi mật khẩu thành công. Bạn có thể đăng nhập bằng mật khẩu mới."
                                        : "Mã xác minh không đúng, đã hết hạn hoặc đã vượt số lần thử.",
                                null, null));
                    } catch (IllegalArgumentException ex) {
                        sendPacket(new SocketPacket(SocketAction.CONFIRM_PASSWORD_RESET, false,
                                ex.getMessage(), null, null));
                    } catch (Exception ex) {
                        System.err.println("Đặt lại mật khẩu lỗi: " + ex.getClass().getSimpleName());
                        sendPacket(new SocketPacket(SocketAction.CONFIRM_PASSWORD_RESET, false,
                                "Chưa thể đặt lại mật khẩu. Vui lòng thử lại sau.", null, null));
                    }
                    break;
                }

                case LOGIN_STAFF:
                case LOGIN: {
                    String emailOrPhone = (String) req.getData();
                    String password = (String) req.getExtraData();
                    if (customerMode) throw new SecurityException("Phien CUSTOMER da khoa; can tao ket noi moi");
                    long loginStarted = System.nanoTime();
                    System.out.println("[LOGIN] Bắt đầu xác thực | peer="
                            + socket.getRemoteSocketAddress());
                    UserDTO loggedUser;
                    try {
                        loggedUser = authBUS.login(emailOrPhone, password);
                    } finally {
                        long elapsedMs = (System.nanoTime() - loginStarted) / 1_000_000L;
                        System.out.println("[LOGIN] Kết thúc xác thực sau " + elapsedMs + "ms");
                    }
                    if (req.getAction() == SocketAction.LOGIN_STAFF &&
                            !("STAFF".equalsIgnoreCase(loggedUser.getRoleName()) ||
                                    "KITCHEN".equalsIgnoreCase(loggedUser.getRoleName()))) {
                        throw new SecurityException("Chi tai khoan nhan vien moi duoc dang nhap ipad/POS/bep");
                    }
                    SocketServer.removeConnection(this);
                    authenticatedUser = loggedUser;
                    workstationLogin = req.getAction() == SocketAction.LOGIN;
                    customerMode = false;
                    currentTableId = null;
                    pairedSessionId = null;
                    failedPairAttempts = 0;
                    if (hasRole("KITCHEN")) SocketServer.registerKitchen(this);
                    if (hasRole("STAFF")) SocketServer.registerStaff(this);
                    sendPacket(new SocketPacket(
                            req.getAction(), true,
                            "Xin chào " + loggedUser.getFullName() + " (" + loggedUser.getRoleName() + ")",
                            loggedUser, null
                    ));
                    break;
                }

                case REGISTER: {
                    UserDTO regUser = (UserDTO) req.getData();
                    UserDTO createdUser = authBUS.register(regUser);
                    sendPacket(new SocketPacket(
                            SocketAction.REGISTER, true,
                            "Đăng ký tài khoản thành công cho " + createdUser.getFullName() + "!",
                            createdUser, null
                    ));
                    break;
                }

                default:
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendPacket(new SocketPacket(
                    req.getAction(), false, "Yêu cầu không hợp lệ hoặc không đủ quyền: " + e.getMessage(), null, null
            ));
        }
    }

    public synchronized void sendPacket(SocketPacket packet) {
        try {
            if (out == null || sessionKey == null || socket.isClosed()) {
                return;
            }
            String json = SocketJsonMapper.toJsonString(packet);
            byte[] plainData = json.getBytes(StandardCharsets.UTF_8);
            byte[] encryptedData = AESGCMUtil.encrypt(plainData, sessionKey);
            writeFrame(out, encryptedData);
            System.out.println("[Server -> Client] action=" + packet.getAction()
                    + " success=" + packet.isSuccess());
        } catch (Exception e) {
            System.err.println("[ClientHandler] Lỗi gửi action=" + packet.getAction()
                    + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void closeResources() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("[ClientHandler] Lỗi đóng socket: " + e.getMessage());
        } finally {
            sessionKey = null;
            in = null;
            out = null;
        }
    }
}