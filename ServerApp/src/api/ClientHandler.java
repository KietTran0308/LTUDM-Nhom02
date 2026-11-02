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

    private void requireCurrentTable(Long requestedTableId) throws Exception {
        requireRole("CUSTOMER");
        if (pairedSessionId == null || currentTableId == null ||
                !currentTableId.equals(requestedTableId) ||
                !TableDAO.isActiveSession(currentTableId, pairedSessionId)) {
            throw new SecurityException("Thiết bị chưa xác minh bàn hoặc phiên bàn đã kết thúc");
        }
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
                case OPEN_TABLE_SESSION: {
                    requireRole("STAFF", "ADMIN");
                    if (req.getTableId() == null || req.getTableId() <= 0) {
                        throw new IllegalArgumentException("Hãy chọn bàn hợp lệ");
                    }
                    String code = TableDAO.openOrIssuePairingCode(req.getTableId(), authenticatedUser.getId());
                    SocketPacket opened = new SocketPacket(SocketAction.OPEN_TABLE_SESSION, true,
                            "Mã thiết lập thiết bị dành cho nhân viên", code, null);
                    opened.setTableId(req.getTableId());
                    sendPacket(opened);
                    break;
                }

                case REGISTER_TABLE: {
                    requireRole("CUSTOMER");
                    if (failedPairAttempts >= 5) throw new SecurityException("Đã vượt số lần ghép bàn; vui lòng đăng nhập lại");
                    Long id = req.getTableId();
                    Long session = (id == null) ? null : TableDAO.verifyActiveSession(id, (String) req.getData());
                    if (session == null) {
                        failedPairAttempts++;
                        sendPacket(new SocketPacket(SocketAction.REGISTER_TABLE, false,
                                "Mã ghép không hợp lệ hoặc bàn chưa được mở; báo nhân viên để kiểm tra", null, null));
                        break;
                    }
                    SocketServer.removeConnection(this); // Prevent stale bindings on a reused connection.
                    currentTableId = id;
                    pairedSessionId = session;
                    failedPairAttempts = 0;
                    SocketServer.registerTableSession(id, this);
                    sendPacket(new SocketPacket(SocketAction.REGISTER_TABLE, true,
                            "Đã xác minh thiết bị cho bàn #" + id, null, null));
                    break;
                }

                case GET_MENU_AND_CATEGORIES: {
                    requireCurrentTable(req.getTableId());
                    List<FoodDTO> foods = foodDAO.getPublishedFoodsForTablet();
                    List<CategoryDTO> categories = categoryDAO.getAllCategories();
                    // data = List<FoodDTO>, extraData = List<CategoryDTO>
                    sendPacket(new SocketPacket(
                            SocketAction.GET_MENU_AND_CATEGORIES, true,
                            "Tải thực đơn thành công", foods, categories
                    ));
                    break;
                }

                case PLACE_ORDER: {
                    requireCurrentTable(req.getTableId());
                    OrderDTO incomingOrder = (OrderDTO) req.getData();
                    String voucherCode = (String) req.getExtraData();
                    if (incomingOrder == null) throw new IllegalArgumentException("Đơn không hợp lệ");
                    incomingOrder.setTableId(currentTableId);
                    incomingOrder.setTableSessionId(pairedSessionId);
                    incomingOrder.setUserId(authenticatedUser.getId());

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
                            SocketAction.PLACE_ORDER, true,
                            "Đặt món thành công! Mã đơn: " + savedOrder.getOrderCode(),
                            savedOrder, updatedTrackingList
                    ));

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
                    requireRole("STAFF", "ADMIN");
                    List<TableDTO> tables = serverOrderBUS.getAllRestaurantTables();
                    sendPacket(new SocketPacket(
                            SocketAction.GET_ALL_TABLES, true, "Tải sơ đồ bàn thành công", tables, null
                    ));
                    break;
                }

                case GET_TABLE_BILL_PREVIEW: {
                    requireRole("STAFF", "ADMIN");
                    OrderDTO mergedOrder = serverOrderBUS.getTableBillPreview(req.getTableId());
                    if (mergedOrder == null) {
                        sendPacket(new SocketPacket(
                                SocketAction.GET_TABLE_BILL_PREVIEW, false,
                                "Bàn số " + req.getTableId() + " hiện chưa có đơn hàng nào cần thanh toán!", null, null
                        ));
                    } else {
                        sendPacket(new SocketPacket(
                                SocketAction.GET_TABLE_BILL_PREVIEW, true,
                                "Đã gộp toàn bộ các lượt gọi món của Bàn #" + req.getTableId(), mergedOrder, null
                        ));
                    }
                    break;
                }

                case PAY_TABLE_BILL: {
                    requireRole("STAFF", "ADMIN");
                    BillDTO bill = (BillDTO) req.getData();
                    Long targetTableId = req.getTableId();

                    boolean ok = serverOrderBUS.processTablePayment(bill, targetTableId);
                    if (ok) {
                        // Bắn tín hiệu về Tablet của bàn vừa thanh toán để xóa danh sách món cũ
                        SocketServer.notifyTableCleared(targetTableId);

                        // Trả lại danh sách bàn mới nhất cho Thu ngân
                        List<TableDTO> updatedTables = serverOrderBUS.getAllRestaurantTables();
                        sendPacket(new SocketPacket(
                                SocketAction.PAY_TABLE_BILL, true,
                                "Thanh toán thành công cho Bàn #" + targetTableId + "! Đã xuất hóa đơn & mở trống bàn.",
                                updatedTables, null
                        ));
                    } else {
                        sendPacket(new SocketPacket(
                                SocketAction.PAY_TABLE_BILL, false,
                                "Thanh toán thất bại (Đơn hàng đã được thanh toán hoặc lỗi CSDL)!", null, null
                        ));
                    }
                    break;
                }

                case GET_KITCHEN_QUEUE: {
                    requireRole("KITCHEN", "ADMIN");
                    sendPacket(new SocketPacket(SocketAction.GET_KITCHEN_QUEUE, true,
                            "Đã tải danh sách bếp", orderDAO.getKitchenQueue(), null));
                    break;
                }

                case SET_KITCHEN_ITEM_STATUS: {
                    requireRole("KITCHEN", "ADMIN");
                    Long itemId = (Long) req.getData();
                    OrderStatus newStatus = (OrderStatus) req.getExtraData();
                    if (itemId == null || newStatus == null ||
                            (newStatus != OrderStatus.RECEIVED && newStatus != OrderStatus.COOKING &&
                                    newStatus != OrderStatus.COMPLETED)) {
                        throw new IllegalArgumentException("Món hoặc trạng thái không hợp lệ");
                    }
                    Long table = orderDAO.getTableIdByOrderItem(itemId);
                    if (table == null || !orderDAO.updateOrderItemStatus(itemId, newStatus)) {
                        throw new IllegalStateException("Không thể cập nhật trạng thái món");
                    }
                    sendPacket(new SocketPacket(SocketAction.SET_KITCHEN_ITEM_STATUS, true,
                            "Đã cập nhật trạng thái món", itemId, newStatus));
                    SocketServer.pushStatusToTable(table, itemId, newStatus);
                    SocketServer.broadcastKitchenQueue(orderDAO.getKitchenQueue());
                    break;
                }

                case LOGOUT: {
                    SocketServer.removeConnection(this);
                    authenticatedUser = null;
                    currentTableId = null;
                    pairedSessionId = null;
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

                case LOGIN: {
                    String emailOrPhone = (String) req.getData();
                    String password = (String) req.getExtraData();
                    UserDTO loggedUser = authBUS.login(emailOrPhone, password);
                    SocketServer.removeConnection(this);
                    authenticatedUser = loggedUser;
                    currentTableId = null;
                    pairedSessionId = null;
                    failedPairAttempts = 0;
                    if (hasRole("KITCHEN", "ADMIN")) SocketServer.registerKitchen(this);
                    if (hasRole("STAFF", "ADMIN")) SocketServer.registerStaff(this);
                    sendPacket(new SocketPacket(
                            SocketAction.LOGIN, true,
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
        } catch (IOException ignored) {}
    }
}