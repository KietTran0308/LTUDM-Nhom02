package api;

import bus.*;
import dao.*;
import dto.*;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Long currentTableId;

    private final FoodDAO foodDAO = new FoodDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final ServerOrderBUS serverOrderBUS = new ServerOrderBUS();
    private final AuthBUS authBUS = new AuthBUS();
    private UserDTO authenticatedUser;
    private boolean customerMode;
    // LOGIN trên máy POS/Admin; khác với LOGIN_STAFF trên iPad phục vụ.
    // Đây là ranh giới giao thức, không thay thế việc cấp phép thiết bị ở mạng thật.
    private boolean workstationLogin;
    private Long pairedSessionId;
    private int failedPairAttempts;
    private final OrderDAO orderDAO = new OrderDAO();

    public ClientHandler(Socket socket) {
        this.socket = socket;
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

    /** POS socket chỉ dành cho STAFF/ADMIN đã xác thực trên chính kết nối này. */
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
        // Tùy chọn tăng cường: ví dụ WORKSTATION_ALLOWED_IPS=192.168.1.10,192.168.1.11
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
                !TableDAO.isActiveSession(currentTableId, pairedSessionId)) {
            throw new SecurityException("Thiet bi chua vao CUSTOMER MODE hoac phien da dong");
        }
    }

    private SocketPacket ok(SocketAction action, String message, Object data, Object extra) {
        return new SocketPacket(action, true, message, data, extra);
    }

    @Override
    public void run() {
        try {
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String rawJsonLine;

            while (!socket.isClosed() && (rawJsonLine = in.readLine()) != null) {
                // Không log raw JSON: LOGIN/đặt lại mật khẩu chứa thông tin bí mật.
                SocketPacket request = SocketJsonMapper.fromJsonString(rawJsonLine);
                System.out.println("[Server] Nhận action: " + request.getAction());
                handleClientRequest(request);
            }
        } catch (Exception e) {
            System.out.println("[ClientHandler - JSON] Kết nối bàn " + currentTableId + " đã đóng: " + e.getMessage());
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
                    List<TableDTO> tables = TableDAO.getAllTables();
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
                    if (!TableDAO.sessionBelongsToTable(tid, sid)) {
                        throw new SecurityException("Ban chua duoc POS mo phien ACTIVE");
                    }
                    // Ha quyen ben Server: khong giu UserDTO role STAFF tren socket.
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
                case REGISTER_TABLE: {
                    // Giao thuc ma ghep cu da bo: khong the tu gan ban bang ma/token.
                    throw new SecurityException("Hay dung ENTER_CUSTOMER_MODE tu nhan vien");
                }

                case GET_MENU_AND_CATEGORIES: {
                    requireCurrentTable(req.getTableId());
                    try {
                        List<FoodDTO> foods = foodDAO.getPublishedFoodsForTablet();
                        List<CategoryDTO> categories = categoryDAO.getAllCategories();
                        System.out.println("[MENU] Ban=" + currentTableId + ", session=" + pairedSessionId
                                + ", mon_dang_ban=" + foods.size() + ", danh_muc=" + categories.size());
                        sendPacket(new SocketPacket(
                                SocketAction.GET_MENU_AND_CATEGORIES, true,
                                foods.isEmpty() ? "Không có món đang mở bán" : "Tải thực đơn thành công",
                                foods, categories
                        ));
                    } catch (Exception menuError) {
                        // Log SQL tai Server; khong tra thong tin ket noi DB ve Client.
                        System.err.println("[MENU] Tai thuc don that bai, ban=" + currentTableId);
                        menuError.printStackTrace();
                        sendPacket(new SocketPacket(SocketAction.GET_MENU_AND_CATEGORIES, false,
                                "Lỗi tải thực đơn tại Server. Kiểm tra MySQL/DB_URL và bảng foods, categories, food_images.",
                                null, null));
                    }
                    break;
                }

                case ORDER_SUBMIT:
                case PLACE_ORDER: {
                    requireCurrentTable(req.getTableId());
                    OrderDTO incomingOrder = (OrderDTO) req.getData();
                    String voucherCode = (String) req.getExtraData();
                    if (incomingOrder == null) throw new IllegalArgumentException("Đơn không hợp lệ");
                    incomingOrder.setTableId(currentTableId);
                    incomingOrder.setTableSessionId(pairedSessionId);
                    incomingOrder.setUserId(null); // Khach khong can tai khoan.

                    // Đồng bộ tableId giữa gói tin SocketPacket, phiên kết nối hiện tại và OrderDTO
                    Long targetTableId = req.getTableId() != null ? req.getTableId() : this.currentTableId;
                    if (targetTableId == null && incomingOrder != null) {
                        targetTableId = incomingOrder.getTableId();
                    }
                    if (incomingOrder != null && incomingOrder.getTableId() == null) {
                        incomingOrder.setTableId(targetTableId);
                    }

                    // Gọi xuống ServerOrderBUS để kiểm tra đơn gốc parent_order_id, tính tiền, lưu DB & trừ kho
                    OrderDTO savedOrder = serverOrderBUS.processIncomingTableOrder(incomingOrder, voucherCode);

                    // Lấy lại toàn bộ danh sách món đang theo dõi của bàn này trả về cho Tablet
                    List<OrderItemDTO> updatedTrackingList = serverOrderBUS.getTableTrackingItems(targetTableId);

                    sendPacket(new SocketPacket(
                            req.getAction(), true,
                            "Đặt món thành công! Mã đơn: " + savedOrder.getOrderCode(),
                            savedOrder, updatedTrackingList
                    ));

                    SocketServer.broadcastOrderCreated(savedOrder);
                    SocketServer.broadcastKitchenQueue(orderDAO.getKitchenQueue());
                    break;
                }

                case GET_TABLE_TRACKING: {
                    requireCurrentTable(req.getTableId());
                    List<OrderItemDTO> trackedItems = serverOrderBUS.getTableTrackingItems(req.getTableId());
                    sendPacket(new SocketPacket(
                            SocketAction.GET_TABLE_TRACKING, true,
                            "Đồng bộ trạng thái đơn thành công", trackedItems, null
                    ));
                    break;
                }

                case VIEW_SESSION_BILL: {
                    requireCurrentTable(req.getTableId());
                    OrderDTO preview = serverOrderBUS.getTableBillPreview(currentTableId);
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
                        throw new IllegalArgumentException(
                                "Mã bàn không hợp lệ");
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
                        throw new IllegalArgumentException(
                                "Mã bàn không hợp lệ");
                    }

                    PosBUS pos = new PosBUS(authenticatedUser);

                    sendPacket(ok(
                            SocketAction.GET_TABLE_BILL_PREVIEW,
                            "Đã tải hóa đơn tạm tính",
                            pos.preview(tableId),
                            null));
                    break;
                }

                case MARK_ITEM_SERVED: {
                    requireWorkstationRole("STAFF", "ADMIN");

                    Long itemId = (Long) req.getData();
                    if (itemId == null || itemId <= 0) {
                        throw new IllegalArgumentException(
                                "Mã chi tiết món không hợp lệ");
                    }

                    PosBUS pos = new PosBUS(authenticatedUser);
                    pos.markServed(itemId);

                    sendPacket(ok(
                            SocketAction.MARK_ITEM_SERVED,
                            "Đã xác nhận phục vụ món",
                            itemId,
                            OrderStatus.SERVED));
                    break;
                }

                case PAY_TABLE_BILL: {
                    requireWorkstationRole("STAFF", "ADMIN");

                    Long tableId = req.getTableId();
                    BillDTO bill = (BillDTO) req.getData();

                    if (tableId == null || tableId <= 0 ||
                            bill == null ||
                            bill.getTableSessionId() == null ||
                            bill.getPaymentMethod() == null) {
                        throw new IllegalArgumentException(
                                "Thông tin thanh toán không hợp lệ");
                    }

                    PosBUS pos = new PosBUS(authenticatedUser);

                    pos.pay(
                            tableId,
                            bill.getTableSessionId(),
                            bill.getPaymentMethod());

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
                            "Đã tải danh sách bếp", orderDAO.getKitchenQueue(), null));
                    break;
                }

                case UPDATE_ITEM_STATUS:
                case SET_KITCHEN_ITEM_STATUS: {
                    requireRole("KITCHEN");
                    Long itemId = (Long) req.getData();
                    OrderStatus next = (OrderStatus) req.getExtraData();
                    if (itemId == null || next == null || (next != OrderStatus.RECEIVED &&
                            next != OrderStatus.COOKING && next != OrderStatus.COMPLETED)) {
                        throw new IllegalArgumentException("Mon hoac trang thai khong hop le");
                    }
                    Long table = orderDAO.getTableIdByOrderItem(itemId);
                    if (table == null || !orderDAO.updateOrderItemStatus(itemId, next)) {
                        throw new IllegalStateException("Trang thai mon khong cho phep chuyen sang " + next);
                    }
                    OrderDTO updatedOrder = orderDAO.getOrderById(orderDAO.getOrderIdByItem(itemId));
                    sendPacket(ok(req.getAction(), "Da doi trang thai mon", itemId, next));
                    if (updatedOrder != null && updatedOrder.getTableSessionId() != null) {
                        SocketServer.broadcastItemStatus(updatedOrder.getTableSessionId(), itemId, next);
                    }
                    if (next == OrderStatus.COMPLETED) {
                        OrderDTO finished = orderDAO.completedOrderIfReady(itemId);
                        if (finished != null) SocketServer.broadcastOrderCompleted(finished);
                    }
                    SocketServer.broadcastKitchenQueue(orderDAO.getKitchenQueue());
                    break;
                }

                case ADMIN_GET_FOODS: {
                    requireWorkstationRole("ADMIN");
                    sendPacket(ok(req.getAction(), "Danh sách món", new AdminBUS().getAllFoods(), null));
                    break;
                }
                case ADMIN_GET_CATEGORIES: {
                    requireWorkstationRole("ADMIN");
                    sendPacket(ok(req.getAction(), "Danh mục", new AdminBUS().getAllCategories(), null));
                    break;
                }
                case ADMIN_ADD_FOOD: {
                    requireWorkstationRole("ADMIN");
                    FoodDTO f = (FoodDTO) req.getData();
                    checkFood(f, false);
                    if (!new AdminBUS().addNewFood(f.getSku(), f.getCategoryId(), f.getName(),
                            f.getPrice(), f.getCoverImageUrl(), Boolean.TRUE.equals(f.getIsPublished())))
                        throw new IllegalStateException("Không thêm được món (SKU có thể đã tồn tại)");
                    sendPacket(ok(req.getAction(), "Đã thêm món", null, null));
                    break;
                }
                case ADMIN_UPDATE_FOOD: {
                    requireWorkstationRole("ADMIN");
                    FoodDTO f = (FoodDTO) req.getData();
                    checkFood(f, true);
                    if (!new AdminBUS().updateFood(f.getId(), f.getName(), f.getPrice(),
                            f.getCategoryId(), Boolean.TRUE.equals(f.getIsPublished())))
                        throw new IllegalStateException("Không cập nhật được món");
                    sendPacket(ok(req.getAction(), "Đã cập nhật món", null, null));
                    break;
                }
                case ADMIN_DELETE_FOOD: {
                    requireWorkstationRole("ADMIN");
                    Long id = (Long) req.getData();
                    if (id == null || id <= 0) throw new IllegalArgumentException("ID món không hợp lệ");
                    if (!new AdminBUS().deleteFood(id)) throw new IllegalStateException("Không ẩn được món");
                    sendPacket(ok(req.getAction(), "Đã ẩn món", null, null));
                    break;
                }
                case ADMIN_GET_INGREDIENTS: {
                    requireWorkstationRole("ADMIN");
                    sendPacket(ok(req.getAction(), "Danh sách nguyên liệu",
                            new AdminBUS().getAllIngredients(), null));
                    break;
                }
                case ADMIN_ADD_INGREDIENT: {
                    requireWorkstationRole("ADMIN");
                    IngredientDTO i = (IngredientDTO) req.getData();
                    if (i == null || i.getName() == null || i.getName().isBlank()
                            || i.getUnit() == null || i.getUnit().isBlank()
                            || i.getStockQuantity() == null || i.getMinStockLevel() == null
                            || i.getStockQuantity().signum() < 0 || i.getMinStockLevel().signum() < 0)
                        throw new IllegalArgumentException("Thông tin nguyên liệu không hợp lệ");
                    if (!new AdminBUS().addIngredient(i.getName(), i.getUnit(),
                            i.getStockQuantity(), i.getMinStockLevel()))
                        throw new IllegalStateException("Không thêm được nguyên liệu");
                    sendPacket(ok(req.getAction(), "Đã thêm nguyên liệu", null, null));
                    break;
                }
                case ADMIN_RESTOCK_INGREDIENT: {
                    requireWorkstationRole("ADMIN");
                    IngredientDTO i = (IngredientDTO) req.getData();
                    if (i == null || i.getId() == null || i.getId() <= 0 || i.getStockQuantity() == null
                            || i.getStockQuantity().signum() <= 0)
                        throw new IllegalArgumentException("Số lượng nhập kho không hợp lệ");
                    if (!new AdminBUS().restockIngredient(i.getId(), i.getStockQuantity()))
                        throw new IllegalStateException("Không thể nhập kho");
                    sendPacket(ok(req.getAction(), "Đã nhập kho", null, null));
                    break;
                }
                case ADMIN_GET_BILLS: {
                    requireWorkstationRole("ADMIN");
                    sendPacket(ok(req.getAction(), "Danh sách hóa đơn", new AdminBUS().getAllBills(), null));
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
                    // Không yêu cầu đăng nhập vì người dùng đang quên mật khẩu.
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
                        // SMTP chưa cấu hình: cùng một lỗi cho mọi email, không lộ tài khoản tồn tại.
                        if ("Server chưa cấu hình SMTP".equals(ex.getMessage())) {
                            sendPacket(new SocketPacket(SocketAction.REQUEST_PASSWORD_RESET, false,
                                    "Server chưa cấu hình gửi email OTP. Vui lòng liên hệ quản trị.", null, null));
                        } else {
                            sendPacket(new SocketPacket(SocketAction.REQUEST_PASSWORD_RESET, true,
                                    "Nếu email đã đăng ký, bạn sẽ nhận mã xác minh. Hãy kiểm tra Spam hoặc thử lại sau.",
                                    null, null));
                        }
                    } catch (Exception ex) {
                        // Không tiết lộ email có tồn tại hay chi tiết SMTP/DB cho Client.
                        System.err.println("Gửi OTP thất bại: " + ex.getClass().getSimpleName());
                        sendPacket(new SocketPacket(SocketAction.REQUEST_PASSWORD_RESET, true,
                                "Nếu email đã đăng ký, bạn sẽ nhận mã xác minh. Hãy kiểm tra Spam hoặc thử lại sau.",
                                null, null));
                    }
                    break;
                }

                case CONFIRM_PASSWORD_RESET: {
                    try {
                        if (!(req.getData() instanceof java.util.Map<?, ?>)) {
                            throw new IllegalArgumentException("Dữ liệu đặt lại mật khẩu không hợp lệ.");
                        }
                        java.util.Map<?, ?> body = (java.util.Map<?, ?>) req.getData();
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
                    UserDTO loggedUser = authBUS.login(emailOrPhone, password);
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

    /**
     * Đóng gói SocketPacket thành chuỗi JSON bằng org.json và gửi về Client
     */
    public synchronized void sendPacket(SocketPacket packet) {
        if (out != null && !socket.isClosed()) {
            String jsonResponse = SocketJsonMapper.toJsonString(packet);
            System.out.println("[Server -> Client] action=" + packet.getAction());
            out.println(jsonResponse); // Gửi 1 dòng JSON kết thúc bằng \n
        }
    }

    private void closeResources() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {
        }
    }
}