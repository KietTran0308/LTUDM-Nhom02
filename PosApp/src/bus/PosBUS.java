package bus;

import api.SocketClient;
import dto.BillDTO;
import dto.OrderDTO;
import dto.SocketAction;
import dto.SocketPacket;
import dto.TableDTO;
import dto.UserDTO;
import dto.dbEnums;

import dto.PointDTO;
import java.util.HashMap;
import java.util.Map;

import java.util.ArrayList;
import java.util.List;

public final class PosBUS {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_CUSTOMER = "CUSTOMER";
    private static final long REQUEST_TIMEOUT_MS = 5000L;
    private static final long LOGIN_TIMEOUT_MS = 7000L;

    private final SocketClient socket;
    private final UserDTO operator;

    public PosBUS(UserDTO operator, SocketClient socket) {
        if (operator == null || socket == null || !socket.isConnected()
                || !ROLE_ADMIN.equalsIgnoreCase(operator.getRoleName())) {
            throw new SecurityException("POS chỉ dành cho ADMIN có phiên kết nối hợp lệ");
        }
        this.operator = operator;
        this.socket = socket;
    }

    public static PosBUS loginPos(String account, String password) {
        if (account == null || account.isBlank() || password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng nhập tài khoản và mật khẩu");
        }

        SocketClient connection = new SocketClient();
        try {
            if (!connection.connect(null)) {
                throw new IllegalStateException("Không kết nối được Server");
            }

            SocketPacket response = connection.requestAndWait(
                    new SocketPacket(SocketAction.LOGIN, null, account.trim(), password),
                    LOGIN_TIMEOUT_MS
            );

            if (!(response.getData() instanceof UserDTO user)) {
                throw new IllegalStateException("Phản hồi đăng nhập không hợp lệ");
            }

            PosBUS pos = new PosBUS(user, connection);
            pos.listTables(); // Xác minh quyền truy cập POS phía Server
            return pos;
        } catch (RuntimeException ex) {
            safeLogoutAndClose(connection);
            throw ex;
        }
    }

    public UserDTO getOperator() {
        return operator;
    }

    @SuppressWarnings("unchecked")
    public List<TableDTO> listTables() {
        return (List<TableDTO>) request(SocketAction.GET_ALL_TABLES).getData();
    }

    public long openTable(long tableId) {
        return (Long) request(SocketAction.OPEN_TABLE_SESSION, tableId, null, null).getData();
    }

    public void createCustomerAccount(String fullName, String phone, String email) {
        UserDTO customer = new UserDTO();
        customer.setFullName(fullName);
        customer.setPhone(phone);
        customer.setEmail(email);
        customer.setRoleName(ROLE_CUSTOMER);
        request(SocketAction.ADMIN_CREATE_ACCOUNT, null, customer, null);
    }

    public OrderDTO preview(long tableId) {
        return (OrderDTO) request(SocketAction.GET_TABLE_BILL_PREVIEW, tableId, null, null).getData();
    }

    public List<UserDTO> listCustomers() {
        Object accountData = request(SocketAction.ADMIN_GET_ACCOUNTS).getData();

        Map<Long, Integer> pointsByUserId = new HashMap<>();
        try {
            Object pointData = request(SocketAction.ADMIN_GET_POINTS).getData();
            if (pointData instanceof List<?> pointList) {
                for (Object item : pointList) {
                    if (item instanceof PointDTO p && p.getUserId() != null) {
                        pointsByUserId.put(p.getUserId(), p.getCurrentPoints());
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // Nếu chưa có dữ liệu điểm thì mặc định 0 điểm, không làm gián đoạn tải khách hàng
        }

        List<UserDTO> customers = new ArrayList<>();
        if (accountData instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof UserDTO user
                        && ROLE_CUSTOMER.equalsIgnoreCase(user.getRoleName())
                        && Boolean.TRUE.equals(user.getIsActive())) {
                    int pts = user.getId() != null
                            ? pointsByUserId.getOrDefault(user.getId(), 0)
                            : 0;
                    user.setCurrentPoints(pts);
                    customers.add(user);
                }
            }
        }
        return customers;
    }

    public OrderDTO applyVoucher(long tableId, String voucherCode) {
        String normalizedCode = voucherCode == null ? "" : voucherCode.trim();
        return (OrderDTO) request(SocketAction.APPLY_TABLE_VOUCHER, tableId, normalizedCode, null).getData();
    }

    public void pay(long tableId, OrderDTO preview, dbEnums.PaymentMethod method, UserDTO customer) {
        if (preview == null || preview.getTableSessionId() == null || preview.getCheckoutRevision() == null) {
            throw new IllegalArgumentException("Dữ liệu hóa đơn chưa sẵn sàng, vui lòng tải lại bàn");
        }
        if (method == null) {
            throw new IllegalArgumentException("Vui lòng chọn phương thức thanh toán");
        }
        BillDTO bill = new BillDTO();
        bill.setTableSessionId(preview.getTableSessionId());
        bill.setPaymentMethod(method);
        if (customer != null) {
            bill.setUserId(customer.getId());
        }
        request(SocketAction.PAY_TABLE_BILL, tableId, bill, preview.getCheckoutRevision());
    }

    public void close() {
        safeLogoutAndClose(socket);
    }

    private SocketPacket request(SocketAction action) {
        return request(action, null, null, null);
    }

    private SocketPacket request(SocketAction action, Long tableId, Object data, Object extra) {
        return socket.requestAndWait(new SocketPacket(action, tableId, data, extra), REQUEST_TIMEOUT_MS);
    }

    private static void safeLogoutAndClose(SocketClient client) {
        try {
            client.logout();
        } catch (Exception ignored) { }
        client.close();
    }
}