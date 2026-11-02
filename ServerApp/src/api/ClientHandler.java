package api;

import bus.*;
import dao.*;
import dto.*;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Long currentTableId;

    private final FoodDAO foodDAO = new FoodDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final ServerOrderBUS serverOrderBUS = new ServerOrderBUS();
    private final AuthBUS authBUS = new AuthBUS();
    private final Consumer<SocketPacket> onKitchenNotification;

    public ClientHandler(Socket socket, Consumer<SocketPacket> onKitchenNotification) {
        this.socket = socket;
        this.onKitchenNotification = onKitchenNotification;
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
            SocketServer.removeTableSession(currentTableId);
            closeResources();
        }
    }

    private void handleClientRequest(SocketPacket req) {
        try {
            switch (req.getAction()) {
                case REGISTER_TABLE: {
                    this.currentTableId = req.getTableId();
                    SocketServer.registerTableSession(this.currentTableId, this);
                    sendPacket(new SocketPacket(
                            SocketAction.REGISTER_TABLE, true,
                            "Kết nối máy chủ thành công cho Bàn số " + currentTableId, null, null
                    ));
                    break;
                }

                case GET_MENU_AND_CATEGORIES: {
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
                    OrderDTO incomingOrder = (OrderDTO) req.getData();
                    String voucherCode = (String) req.getExtraData();

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

                    // Báo lên màn hình Bếp / Admin ở Server có đơn hàng mới
                    if (onKitchenNotification != null) {
                        onKitchenNotification.accept(new SocketPacket(
                                SocketAction.PLACE_ORDER, targetTableId, savedOrder, null
                        ));
                    }
                    break;
                }

                case GET_TABLE_TRACKING: {
                    List<OrderItemDTO> trackedItems = serverOrderBUS.getTableTrackingItems(req.getTableId());
                    sendPacket(new SocketPacket(
                            SocketAction.GET_TABLE_TRACKING, true,
                            "Đồng bộ trạng thái đơn thành công", trackedItems, null
                    ));
                    break;
                }

                case CALL_STAFF: {
                    if (onKitchenNotification != null) {
                        onKitchenNotification.accept(req);
                    }
                    sendPacket(new SocketPacket(
                            SocketAction.CALL_STAFF, true,
                            "Nhân viên đã nhận được yêu cầu hỗ trợ của Bàn số " + req.getTableId(), null, null
                    ));
                    break;
                }

                case GET_ALL_TABLES: {
                    List<TableDTO> tables = serverOrderBUS.getAllRestaurantTables();
                    sendPacket(new SocketPacket(
                            SocketAction.GET_ALL_TABLES, true, "Tải sơ đồ bàn thành công", tables, null
                    ));
                    break;
                }

                case GET_TABLE_BILL_PREVIEW: {
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
                    req.getAction(), false, "Lỗi xử lý tại Server: " + e.getMessage(), null, null
            ));
        }
    }

    /**
     * Đóng gói SocketPacket thành chuỗi JSON bằng org.json và gửi về Client
     */
    public synchronized void sendPacket(SocketPacket packet) {
        if (out != null && !socket.isClosed()) {
            String jsonResponse = SocketJsonMapper.toJsonString(packet);
            System.out.println("[Server -> Client JSON]: " + jsonResponse);
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