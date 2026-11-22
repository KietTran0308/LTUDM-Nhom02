package api;

import bus.*;
import bus.AuthBUS;
import bus.FoodBUS;
import bus.OrderBUS;
import bus.PosBUS;
import dto.BillDTO;
import dto.CategoryDTO;
import dto.FoodDTO;
import dto.OrderDTO;
import dto.OrderItemDTO;
import dto.PointDTO;
import dto.PointTransactionDTO;
import dto.SocketAction;
import dto.SocketPacket;
import dto.TableDTO;
import dto.UserDTO;
import dto.VoucherDTO;
import dto.dbEnums;
import org.json.JSONObject;
import security.AESGCMUtil;
import security.RSAKeyExchange;
import security.RSAKeyManager;

import javax.crypto.SecretKey;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ClientHandler implements Runnable {

    private static final int MAX_PACKET_SIZE = 1024 * 1024;
    private static final int HANDSHAKE_TIMEOUT_MS = 5000;

    private final Socket socket;
    private final RSAKeyManager rsaKeyManager;
    private DataOutputStream out;
    private DataInputStream in;
    private SecretKey sessionKey;

    private final FoodBUS foodBUS = new FoodBUS();
    private final OrderBUS orderBUS = new OrderBUS();
    private final AuthBUS authBUS = new AuthBUS();
    private final AdminBUS adminBUS = new AdminBUS();

    private UserDTO authenticatedUser;
    private Long currentTableId;
    private Long pairedSessionId;
    private boolean customerMode;

    private boolean workstationLogin;

    public ClientHandler(Socket socket, RSAKeyManager rsaKeyManager) {
        this.socket = socket;
        this.rsaKeyManager = rsaKeyManager;
    }

    @Override
    public void run() {
        try {
            in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

            socket.setSoTimeout(HANDSHAKE_TIMEOUT_MS);

            String publicKeyString = RSAKeyExchange.publicKeyToString(rsaKeyManager.getPublicKey());
            writeFrame(out, publicKeyString);

            String encryptedAESKey = readFrameAsString(in);
            sessionKey = RSAKeyExchange.decryptAESKey(encryptedAESKey, rsaKeyManager.getPrivateKey());

            writeFrame(out, AESGCMUtil.encrypt("OK".getBytes(StandardCharsets.US_ASCII), sessionKey));
            System.out.println("[Server] Trao đổi khóa AES thành công!");

            socket.setSoTimeout(5 * 60 * 1000);

            while (!socket.isClosed()) {
                byte[] encryptedData = readFrame(in);
                byte[] decryptedData = AESGCMUtil.decrypt(encryptedData, sessionKey);
                String rawJson = new String(decryptedData, StandardCharsets.UTF_8);

                if (rawJson.startsWith("IMG/2 ")) {
                    ImageSocketServer.sendImage(rawJson.substring(6), out, sessionKey);
                    continue;
                }

                SocketPacket request = SocketJsonMapper.fromJsonString(rawJson);
                System.out.println("[Server] Nhận action: " + request.getAction()
                        + " | peer=" + socket.getRemoteSocketAddress());

                handleClientRequest(request);
            }
        } catch (java.io.EOFException | java.net.SocketException e) {
            System.out.println("[ClientHandler] Client " + socket.getRemoteSocketAddress() + " đã ngắt kết nối.");
        } catch (Exception e) {
            System.err.println("[ClientHandler] Lỗi bất thường tại " + socket.getRemoteSocketAddress()
                    + ": " + e.getClass().getSimpleName() + ": " + e.getMessage());
            e.printStackTrace();
        } finally {
            SocketServer.removeConnection(this);
            closeResources();
        }
    }

    private void handleClientRequest(SocketPacket req) {
        try {
            switch (req.getAction()) {
                case LIST_TABLES -> {
                    requireRole("STAFF");
                    List<TableDTO> tables = orderBUS.getAllRestaurantTables();
                    System.out.println("[Server] LIST_TABLES: trả " + tables.size()
                            + " bàn cho nhân viên #" + authenticatedUser.getId());
                    sendPacket(ok(SocketAction.LIST_TABLES, "Danh sách bàn và phiên đang hoạt động", tables, null));
                }

                case ENTER_CUSTOMER_MODE -> {
                    requireRole("STAFF");
                    if (!(req.getData() instanceof Long sid) || req.getTableId() == null) {
                        throw new IllegalArgumentException("Cần tableId và sessionId");
                    }
                    long tid = req.getTableId();
                    if (!orderBUS.sessionBelongsToTable(tid, sid)) {
                        throw new SecurityException("Bàn chưa được POS mở phiên ACTIVE");
                    }

                    if (!SocketServer.tryClaimSession(sid, this)) {
                        throw new IllegalStateException("Bàn này đã được bàn giao cho một thiết bị iPad khác!");
                    }
                    SocketServer.releaseStaffRoles(this);


                    authenticatedUser = null;
                    customerMode = true;
                    workstationLogin = false;
                    currentTableId = tid;
                    pairedSessionId = sid;

                    SocketPacket result = ok(SocketAction.ENTER_CUSTOMER_MODE,
                            "Đã bàn giao thiết bị cho khách", sid, null);
                    result.setTableId(tid);
                    sendPacket(result);
                    SocketServer.broadcastTablesChanged();
                }

                case GET_MENU_AND_CATEGORIES -> {
                    requireCurrentTable(req.getTableId());
                    try {
                        List<FoodDTO> foods = foodBUS.getPublishedFoodsForTablet();
                        List<CategoryDTO> categories = foodBUS.getAllCategories();
                        System.out.println("[MENU] Bàn=" + currentTableId + ", session=" + pairedSessionId
                                + ", món_đang_bán=" + foods.size() + ", danh_mục=" + categories.size());
                        sendPacket(new SocketPacket(
                                SocketAction.GET_MENU_AND_CATEGORIES, true,
                                foods.isEmpty() ? "Không có món đang mở bán" : "Tải thực đơn thành công",
                                foods, categories
                        ));
                    } catch (Exception menuError) {
                        System.err.println("[MENU] Tải thực đơn thất bại, bàn=" + currentTableId);
                        menuError.printStackTrace();
                        sendPacket(new SocketPacket(SocketAction.GET_MENU_AND_CATEGORIES, false,
                                "Lỗi tải thực đơn tại Server. Kiểm tra MySQL/DB_URL và bảng foods, categories, food_images.",
                                null, null));
                    }
                }

                case ORDER_SUBMIT -> {
                    requireCurrentTable(req.getTableId());
                    if (!(req.getData() instanceof OrderDTO incomingOrder)) {
                        throw new IllegalArgumentException("Đơn không hợp lệ");
                    }
                    String voucherCode = (String) req.getExtraData();
                    Long targetTableId = req.getTableId() != null ? req.getTableId() : currentTableId;

                    OrderDTO savedOrder;
                    List<OrderItemDTO> updatedTrackingList;
                    synchronized (SocketServer.getSessionLock(pairedSessionId)) {
                        // Kiểm tra lại trong lock phòng trường hợp POS vừa thanh toán xong
                        requireCurrentTable(targetTableId);
                        incomingOrder.setTableId(currentTableId != null ? currentTableId : targetTableId);
                        incomingOrder.setTableSessionId(pairedSessionId);
                        incomingOrder.setUserId(null);

                        savedOrder = orderBUS.processIncomingTableOrder(incomingOrder, voucherCode);
                        updatedTrackingList = orderBUS.getTableTrackingItems(targetTableId);
                    }

                    sendPacket(new SocketPacket(
                            req.getAction(), true,
                            "Đặt món thành công! Mã đơn: " + savedOrder.getOrderCode(),
                            savedOrder, updatedTrackingList
                    ));

                    SocketServer.broadcastOrderCreated(savedOrder);
                    SocketServer.broadcastKitchenQueue(orderBUS.getKitchenQueue());
                    SocketServer.broadcastTablesChanged();
                }

                case GET_TABLE_TRACKING -> {
                    requireCurrentTable(req.getTableId());
                    List<OrderItemDTO> trackedItems = orderBUS.getTableTrackingItems(req.getTableId());
                    sendPacket(new SocketPacket(
                            SocketAction.GET_TABLE_TRACKING, true,
                            "Đồng bộ trạng thái đơn thành công", trackedItems, null
                    ));
                }

                case VIEW_SESSION_BILL -> {
                    requireCurrentTable(req.getTableId());
                    OrderDTO preview = orderBUS.getTableBillPreview(currentTableId);
                    if (preview != null && !pairedSessionId.equals(preview.getTableSessionId())) {
                        throw new SecurityException("Hóa đơn không thuộc phiên hiện tại");
                    }
                    sendPacket(ok(SocketAction.VIEW_SESSION_BILL, "Tạm tính toàn bộ lượt gọi món", preview, null));
                }

                case GET_ALL_TABLES -> {
                    requireWorkstationRole("ADMIN");
                    PosBUS pos = new PosBUS(authenticatedUser);
                    sendPacket(ok(SocketAction.GET_ALL_TABLES, "Đã tải danh sách bàn", pos.listTables(), null));
                }

                case OPEN_TABLE_SESSION -> {
                    requireWorkstationRole("ADMIN");
                    long tableId = requirePositiveId(req.getTableId(), "Mã bàn không hợp lệ");
                    PosBUS pos = new PosBUS(authenticatedUser);
                    Long newSessionId = pos.openTable(tableId);
                    sendPacket(ok(SocketAction.OPEN_TABLE_SESSION, "Đã mở phiên bàn", newSessionId, null));
                    SocketServer.broadcastTablesChanged();
                }

                case GET_TABLE_BILL_PREVIEW -> {
                    requireWorkstationRole("ADMIN");
                    long tableId = requirePositiveId(req.getTableId(), "Mã bàn không hợp lệ");
                    PosBUS pos = new PosBUS(authenticatedUser);
                    sendPacket(ok(SocketAction.GET_TABLE_BILL_PREVIEW, "Đã tải hóa đơn tạm tính",
                            pos.preview(tableId), null));
                }

                case APPLY_TABLE_VOUCHER -> {
                    requireWorkstationRole("ADMIN");
                    long tableId = requirePositiveId(req.getTableId(), "Mã bàn không hợp lệ");
                    String voucherCode = req.getData() == null ? "" : req.getData().toString().trim();
                    PosBUS pos = new PosBUS(authenticatedUser);
                    OrderDTO updatedPreview = pos.applyVoucher(tableId, voucherCode);
                    String msg = voucherCode.isEmpty()
                            ? "Đã gỡ mã giảm giá"
                            : "Đã áp dụng mã " + voucherCode.toUpperCase();
                    sendPacket(ok(SocketAction.APPLY_TABLE_VOUCHER, msg, updatedPreview, null));
                }

                case MARK_ITEM_SERVED ->
                        throw new IllegalStateException("Hãy xác nhận đã phục vụ trên màn hình bếp.");

                case PAY_TABLE_BILL -> {
                    requireWorkstationRole("ADMIN");
                    long tableId = requirePositiveId(req.getTableId(), "Thông tin thanh toán không hợp lệ");
                    if (!(req.getData() instanceof BillDTO bill)
                            || bill.getTableSessionId() == null || bill.getTableSessionId() <= 0
                            || bill.getPaymentMethod() == null) {
                        throw new IllegalArgumentException("Thông tin thanh toán không hợp lệ");
                    }
                    long sessionId = bill.getTableSessionId();
                    synchronized (SocketServer.getSessionLock(sessionId)) {
                        PosBUS pos = new PosBUS(authenticatedUser);
                        pos.pay(tableId, bill, (String) req.getExtraData());
                        SocketServer.notifySessionClosed(sessionId, tableId);
                        SocketServer.broadcastKitchenQueue(orderBUS.getKitchenQueue());
                    }
                    sendPacket(ok(SocketAction.PAY_TABLE_BILL, "Thanh toán và đóng phiên thành công", null, null));
                    SocketServer.notifySessionClosed(sessionId, tableId);
                }

                case GET_KITCHEN_QUEUE -> {
                    requireRole("KITCHEN");
                    sendPacket(new SocketPacket(SocketAction.GET_KITCHEN_QUEUE, true,
                            "Đã tải danh sách bếp", orderBUS.getKitchenQueue(), null));
                }

                case UPDATE_ITEM_STATUS -> {
                    requireRole("KITCHEN");
                    Long itemId = (Long) req.getData();
                    dbEnums.OrderStatus next = (dbEnums.OrderStatus) req.getExtraData();
                    if (itemId == null || itemId <= 0 || (next != dbEnums.OrderStatus.RECEIVED
                            && next != dbEnums.OrderStatus.COOKING
                            && next != dbEnums.OrderStatus.COMPLETED
                            && next != dbEnums.OrderStatus.SERVED)) {
                        throw new IllegalArgumentException("Món hoặc trạng thái không hợp lệ");
                    }
                    Long table = orderBUS.getTableIdByOrderItem(itemId);
                    if (table == null || !orderBUS.changeDishStatusByKitchen(itemId, next)) {
                        throw new IllegalStateException("Trạng thái món không cho phép chuyển sang " + next);
                    }
                    OrderDTO updatedOrder = orderBUS.getOrderByItem(itemId);
                    sendPacket(ok(req.getAction(), "Đã đổi trạng thái món", itemId, next));

                    if (updatedOrder != null && updatedOrder.getTableSessionId() != null) {
                        SocketServer.broadcastItemStatus(updatedOrder.getTableSessionId(), itemId, next);
                    }
                    if (next == dbEnums.OrderStatus.COMPLETED) {
                        OrderDTO finished = orderBUS.getCompletedOrderIfReady(itemId);
                        if (finished != null) SocketServer.broadcastOrderCompleted(finished);
                    }
                    SocketServer.broadcastKitchenQueue(orderBUS.getKitchenQueue());
                }

                case ADMIN_GET_FOODS -> {
                    requireWorkstationRole("ADMIN");
                    sendPacket(ok(req.getAction(), "Danh sách món", adminBUS.getAllFoods(), null));
                }

                case ADMIN_GET_CATEGORIES -> {
                    requireWorkstationRole("ADMIN");
                    sendPacket(ok(req.getAction(), "Danh mục", adminBUS.getAllCategories(), null));
                }

                case ADMIN_ADD_FOOD -> {
                    requireWorkstationRole("ADMIN");
                    FoodDTO f = (FoodDTO) req.getData();
                    checkFood(f, false);
                    if (!adminBUS.addNewFood(f.getSku(), f.getCategoryId(), f.getName(),
                            f.getPrice(), f.getCoverImageUrl(), Boolean.TRUE.equals(f.getIsPublished()))) {
                        throw new IllegalStateException("Không thêm được món (SKU có thể đã tồn tại)");
                    }
                    sendPacket(ok(req.getAction(), "Đã thêm món", null, null));
                    SocketServer.broadcastMenuChanged();
                }

                case ADMIN_UPDATE_FOOD -> {
                    requireWorkstationRole("ADMIN");
                    FoodDTO f = (FoodDTO) req.getData();
                    checkFood(f, true);
                    if (!adminBUS.updateFood(f.getId(), f.getName(), f.getPrice(),
                            f.getCategoryId(), Boolean.TRUE.equals(f.getIsPublished()))) {
                        throw new IllegalStateException("Không cập nhật được món");
                    }
                    sendPacket(ok(req.getAction(), "Đã cập nhật món", null, null));
                    SocketServer.broadcastMenuChanged();
                }

                case ADMIN_DELETE_FOOD -> {
                    requireWorkstationRole("ADMIN");
                    long id = requirePositiveId((Long) req.getData(), "ID món không hợp lệ");
                    if (!adminBUS.deleteFood(id)) throw new IllegalStateException("Không ẩn được món");
                    sendPacket(ok(req.getAction(), "Đã ẩn món", null, null));
                    SocketServer.broadcastMenuChanged();
                }

                case ADMIN_GET_BILLS -> {
                    requireWorkstationRole("ADMIN");
                    if (req.getData() == null) {
                        sendPacket(ok(req.getAction(), "Danh sách hóa đơn", adminBUS.getAllBills(), null));
                    } else if (req.getData() instanceof JSONObject filters) {
                        AdminBUS.RevenueReport report = adminBUS.getRevenueReport(filters);
                        sendPacket(ok(req.getAction(), "Thống kê doanh thu", report.bills(), report.summary()));
                    } else {
                        throw new IllegalArgumentException("Bộ lọc thống kê không hợp lệ");
                    }
                }

                case ADMIN_GET_ACCOUNTS -> {
                    requireWorkstationRole("ADMIN");
                    sendPacket(ok(req.getAction(), "Danh sách tài khoản", adminBUS.getAllAccounts(), null));
                }

                case ADMIN_GET_POINTS -> {
                    requireWorkstationRole("ADMIN");
                    List<PointDTO> points = adminBUS.getAllMemberPoints();
                    sendPacket(ok(req.getAction(), "Danh sách điểm thành viên", points, null));
                }

                case ADMIN_GET_POINT_HISTORY -> {
                    requireWorkstationRole("ADMIN");
                    if (!(req.getData() instanceof Long userId) || userId <= 0) {
                        throw new IllegalArgumentException("ID thành viên không hợp lệ");
                    }
                    List<PointTransactionDTO> history = adminBUS.getPointHistory(userId);
                    sendPacket(ok(req.getAction(), "Lịch sử giao dịch điểm", history, null));
                }

                case ADMIN_CREATE_ACCOUNT -> {
                    requireWorkstationRole("ADMIN");
                    if (!(req.getData() instanceof UserDTO newAcc)) {
                        throw new IllegalArgumentException("Dữ liệu tài khoản không hợp lệ");
                    }
                    if (!adminBUS.createNewAccount(newAcc.getFullName(), newAcc.getPhone(),
                            newAcc.getEmail(), newAcc.getRoleID(), newAcc.getRoleName())) {
                        throw new IllegalStateException("Không thể cấp tài khoản mới");
                    }
                    sendPacket(ok(req.getAction(), "Đã cấp tài khoản thành công", null, null));
                }

                case ADMIN_UPDATE_ACCOUNT -> {
                    requireWorkstationRole("ADMIN");
                    if (!(req.getData() instanceof UserDTO updAcc) || updAcc.getId() == null) {
                        throw new IllegalArgumentException("Dữ liệu không hợp lệ");
                    }
                    if (!adminBUS.updateAccount(updAcc.getId(), updAcc.getFullName(),
                            updAcc.getPhone(), updAcc.getEmail(), updAcc.getRoleName())) {
                        throw new IllegalStateException("Cập nhật thông tin thất bại");
                    }
                    sendPacket(ok(req.getAction(), "Đã cập nhật tài khoản", null, null));
                }

                case ADMIN_LOCK_ACCOUNT -> {
                    requireWorkstationRole("ADMIN");
                    long accId = requirePositiveId((Long) req.getData(), "ID tài khoản không hợp lệ");
                    boolean active = parseToggleMode(req.getExtraData(), "UNLOCK", "LOCK",
                            "Thiếu trạng thái khóa/mở khóa");
                    if (!adminBUS.setAccountActive(accId, authenticatedUser.getId(), active)) {
                        throw new IllegalStateException("Thao tác khóa/mở khóa thất bại");
                    }
                    sendPacket(ok(req.getAction(), "Đã thay đổi trạng thái tài khoản", null, null));
                }

                case LOGOUT -> {
                    SocketServer.removeConnection(this);
                    authenticatedUser = null;
                    currentTableId = null;
                    pairedSessionId = null;
                    customerMode = false;
                    workstationLogin = false;
                    sendPacket(new SocketPacket(SocketAction.LOGOUT, true, "Đã đăng xuất", null, null));
                }

                case REQUEST_PASSWORD_RESET -> handleRequestPasswordReset((String) req.getData());

                case CONFIRM_PASSWORD_RESET -> handleConfirmPasswordReset(req.getData());

                case LOGIN_STAFF, LOGIN -> handleLogin(req);

                case REGISTER -> {
                    UserDTO createdUser = authBUS.register((UserDTO) req.getData());
                    sendPacket(new SocketPacket(
                            SocketAction.REGISTER, true,
                            "Đăng ký tài khoản thành công cho " + createdUser.getFullName() + "!",
                            createdUser, null
                    ));
                }

                case ADMIN_GET_VOUCHERS -> {
                    requireWorkstationRole("ADMIN");
                    sendPacket(ok(req.getAction(), "Danh sách voucher", adminBUS.getAllVouchers(), null));
                }

                case ADMIN_ADD_VOUCHER -> {
                    requireWorkstationRole("ADMIN");
                    if (!adminBUS.addNewVoucher((VoucherDTO) req.getData())) {
                        throw new IllegalStateException("Không thể tạo voucher mới");
                    }
                    sendPacket(ok(req.getAction(), "Đã tạo voucher mới", null, null));
                }

                case ADMIN_UPDATE_VOUCHER -> {
                    requireWorkstationRole("ADMIN");
                    if (!adminBUS.updateVoucher((VoucherDTO) req.getData())) {
                        throw new IllegalStateException("Không thể cập nhật voucher");
                    }
                    sendPacket(ok(req.getAction(), "Đã cập nhật voucher", null, null));
                }

                case ADMIN_TOGGLE_VOUCHER -> {
                    requireWorkstationRole("ADMIN");
                    long voucherId = requirePositiveId((Long) req.getData(), "ID voucher không hợp lệ");
                    boolean active = parseToggleMode(req.getExtraData(), "ACTIVATE", "DEACTIVATE",
                            "Thiếu trạng thái kích hoạt/tạm ngưng");
                    if (!adminBUS.setVoucherActive(voucherId, active)) {
                        throw new IllegalStateException("Thao tác thay đổi trạng thái voucher thất bại");
                    }
                    sendPacket(ok(req.getAction(), "Đã thay đổi trạng thái voucher", null, null));
                }

                default -> { }
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendPacket(new SocketPacket(
                    req.getAction(), false,
                    "Yêu cầu không hợp lệ hoặc không đủ quyền: " + e.getMessage(),
                    null, null
            ));
        }
    }

    private void handleLogin(SocketPacket req) throws Exception {
        if (customerMode) {
            throw new SecurityException("Phiên CUSTOMER đã khóa; cần tạo kết nối mới");
        }
        String emailOrPhone = (String) req.getData();
        String password = (String) req.getExtraData();

        long loginStarted = System.nanoTime();
        System.out.println("[LOGIN] Bắt đầu xác thực | peer=" + socket.getRemoteSocketAddress());
        UserDTO loggedUser;
        try {
            loggedUser = authBUS.login(emailOrPhone, password);
        } finally {
            long elapsedMs = (System.nanoTime() - loginStarted) / 1_000_000L;
            System.out.println("[LOGIN] Kết thúc xác thực sau " + elapsedMs + "ms");
        }

        if (req.getAction() == SocketAction.LOGIN_STAFF
                && !("STAFF".equalsIgnoreCase(loggedUser.getRoleName())
                || "KITCHEN".equalsIgnoreCase(loggedUser.getRoleName()))) {
            throw new SecurityException("Chỉ tài khoản nhân viên mới được đăng nhập iPad/POS/bếp");
        }

        SocketServer.removeConnection(this);
        authenticatedUser = loggedUser;
        workstationLogin = req.getAction() == SocketAction.LOGIN;
        customerMode = false;
        currentTableId = null;
        pairedSessionId = null;

        if (hasRole("KITCHEN")) SocketServer.registerKitchen(this);
        if (hasRole("STAFF")) SocketServer.registerStaff(this);
        if (workstationLogin && hasRole("ADMIN", "STAFF")) SocketServer.registerPos(this);

        sendPacket(new SocketPacket(
                req.getAction(), true,
                "Xin chào " + loggedUser.getFullName() + " (" + loggedUser.getRoleName() + ")",
                loggedUser, null
        ));
    }

    private void handleRequestPasswordReset(String email) {
        try {
            authBUS.requestPasswordReset(email);
            sendPacket(new SocketPacket(
                    SocketAction.REQUEST_PASSWORD_RESET, true,
                    "Nếu email đã đăng ký, mã xác minh sẽ được gửi tới hộp thư. Vui lòng kiểm tra cả Spam.",
                    null, null));
        } catch (IllegalArgumentException ex) {
            sendPacket(new SocketPacket(SocketAction.REQUEST_PASSWORD_RESET, false, ex.getMessage(), null, null));
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
    }

    private void handleConfirmPasswordReset(Object rawData) {
        try {
            if (!(rawData instanceof Map<?, ?> body)) {
                throw new IllegalArgumentException("Dữ liệu đặt lại mật khẩu không hợp lệ.");
            }
            boolean reset = authBUS.confirmPasswordReset(
                    (String) body.get("email"),
                    (String) body.get("otp"),
                    (String) body.get("newPassword"));
            sendPacket(new SocketPacket(SocketAction.CONFIRM_PASSWORD_RESET, reset,
                    reset ? "Đổi mật khẩu thành công. Bạn có thể đăng nhập bằng mật khẩu mới."
                            : "Mã xác minh không đúng, đã hết hạn hoặc đã vượt số lần thử.",
                    null, null));
        } catch (IllegalArgumentException ex) {
            sendPacket(new SocketPacket(SocketAction.CONFIRM_PASSWORD_RESET, false, ex.getMessage(), null, null));
        } catch (Exception ex) {
            System.err.println("Đặt lại mật khẩu lỗi: " + ex.getClass().getSimpleName());
            sendPacket(new SocketPacket(SocketAction.CONFIRM_PASSWORD_RESET, false,
                    "Chưa thể đặt lại mật khẩu. Vui lòng thử lại sau.", null, null));
        }
    }

    public synchronized void sendPacket(SocketPacket packet) {
        try {
            if (out == null || sessionKey == null || socket.isClosed()) return;
            String json = SocketJsonMapper.toJsonString(packet);
            byte[] encryptedData = AESGCMUtil.encrypt(json.getBytes(StandardCharsets.UTF_8), sessionKey);
            writeFrame(out, encryptedData);
            System.out.println("[Server -> Client] action=" + packet.getAction() + " success=" + packet.isSuccess());
        } catch (Exception e) {
            System.err.println("[ClientHandler] Lỗi gửi action=" + packet.getAction() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    // --- Validation & Frame helpers ---

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

    private void requireWorkstationRole(String... roles) {
        requireRole(roles);
        if (!workstationLogin || customerMode) {
            throw new SecurityException("Hãy đăng nhập từ ứng dụng POS/Admin, không phải iPad phục vụ");
        }
        String ips = System.getenv("WORKSTATION_ALLOWED_IPS");
        if (ips != null && !ips.isBlank()) {
            String remote = socket.getInetAddress().getHostAddress();
            boolean allowed = Arrays.stream(ips.split(",")).map(String::trim).anyMatch(remote::equals);
            if (!allowed) throw new SecurityException("Thiết bị này chưa được cấp phép POS/Admin");
        }
    }

    private void requireCurrentTable(Long requestedTableId) throws Exception {
        if (!customerMode || pairedSessionId == null || currentTableId == null
                || (requestedTableId != null && !currentTableId.equals(requestedTableId))
                || !orderBUS.isActiveTableSession(currentTableId, pairedSessionId)) {
            throw new SecurityException("Thiết bị chưa vào CUSTOMER MODE hoặc phiên đã đóng");
        }
    }

    private static long requirePositiveId(Long id, String errorMessage) {
        if (id == null || id <= 0) throw new IllegalArgumentException(errorMessage);
        return id;
    }

    private static boolean parseToggleMode(Object rawMode, String enableToken, String disableToken, String errorMessage) {
        String mode = rawMode == null ? "" : String.valueOf(rawMode);
        if (enableToken.equalsIgnoreCase(mode)) return true;
        if (disableToken.equalsIgnoreCase(mode)) return false;
        throw new IllegalArgumentException(errorMessage);
    }

    private static void checkFood(FoodDTO f, boolean update) {
        if (f == null || (update && (f.getId() == null || f.getId() <= 0))
                || (!update && (f.getSku() == null || f.getSku().isBlank()))
                || f.getName() == null || f.getName().isBlank()
                || f.getPrice() == null || f.getPrice().signum() < 0
                || f.getCategoryId() == null || f.getCategoryId() <= 0) {
            throw new IllegalArgumentException("Thông tin món ăn không hợp lệ");
        }
    }

    private static SocketPacket ok(SocketAction action, String message, Object data, Object extra) {
        return new SocketPacket(action, true, message, data, extra);
    }

    private static void writeFrame(DataOutputStream output, byte[] data) throws IOException {
        if (data == null || data.length == 0 || data.length > MAX_PACKET_SIZE) {
            throw new IOException("Kích thước gói tin gửi đi không hợp lệ");
        }
        output.writeInt(data.length);
        output.write(data);
        output.flush();
    }

    private static void writeFrame(DataOutputStream output, String data) throws IOException {
        writeFrame(output, data.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] readFrame(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length <= 0 || length > MAX_PACKET_SIZE) {
            throw new IOException("Kích thước gói tin không hợp lệ");
        }
        byte[] data = new byte[length];
        input.readFully(data);
        return data;
    }

    private static String readFrameAsString(DataInputStream input) throws IOException {
        return new String(readFrame(input), StandardCharsets.UTF_8);
    }

    public boolean isAliveConnection() {
        return socket != null && !socket.isClosed() && socket.isConnected();
    }

    public void resetCustomerMode() {
        this.customerMode = false;
        this.currentTableId = null;
        this.pairedSessionId = null;
    }

    private void closeResources() {
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("[ClientHandler] Lỗi đóng socket: " + e.getMessage());
        } finally {
            sessionKey = null;
            in = null;
            out = null;
        }
    }
}