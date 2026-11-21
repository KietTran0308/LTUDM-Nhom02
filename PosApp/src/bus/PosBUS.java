package bus;

import api.SocketClient;
import dto.*;

import java.util.List;

public final class PosBUS {
    private final SocketClient socket;
    private final UserDTO operator;

    public PosBUS(UserDTO operator, SocketClient socket) {
        if (operator == null || socket == null || !socket.isConnected()
                || !"ADMIN".equalsIgnoreCase(operator.getRoleName())) {
            throw new SecurityException(
                    "POS chỉ dành cho ADMIN có phiên kết nối hợp lệ");
        }

        this.operator = operator;
        this.socket = socket;
    }

    public UserDTO getOperator() {
        return operator;
    }

    private SocketPacket request(
            SocketAction action,
            Long tableId,
            Object data,
            Object extra) {

        return socket.requestAndWait(
                new SocketPacket(action, tableId, data, extra),
                5000);
    }

    @SuppressWarnings("unchecked")
    public List<TableDTO> listTables() {
        return (List<TableDTO>) request(
                SocketAction.GET_ALL_TABLES,
                null, null, null).getData();
    }


    public static PosBUS loginPos(
            String account, String password) {

        if (account == null || account.isBlank()
                || password == null || password.isEmpty()) {
            throw new IllegalArgumentException(
                    "Vui lòng nhập tài khoản và mật khẩu");
        }

        SocketClient connection = new SocketClient();

        try {
            // Kết nối TCP theo ServerConfig
            if (!connection.connect(null)) {
                throw new IllegalStateException(
                        "Không kết nối được Server");
            }

            // Gửi thông tin đăng nhập
            SocketPacket response = connection.requestAndWait(
                    new SocketPacket(
                            SocketAction.LOGIN,
                            null,
                            account.trim(),
                            password
                    ),
                    7000
            );

            if (!(response.getData() instanceof UserDTO user)) {
                throw new IllegalStateException(
                        "Phản hồi đăng nhập không hợp lệ");
            }

            // Constructor PosBUS kiểm tra role ADMIN
            PosBUS pos = new PosBUS(user, connection);

            // Yêu cầu Server kiểm tra quyền truy cập POS
            pos.listTables();

            return pos;

        } catch (RuntimeException ex) {
            connection.close();
            throw ex;
        }
    }

    public long openTable(long tableId) {
        return (Long) request(
                SocketAction.OPEN_TABLE_SESSION,
                tableId, null, null).getData();
    }

    public void createCustomerAccount(String fullName, String phone, String email) {
        UserDTO customer = new UserDTO();
        customer.setFullName(fullName);
        customer.setPhone(phone);
        customer.setEmail(email);
        customer.setRoleName("CUSTOMER");
        request(SocketAction.ADMIN_CREATE_ACCOUNT, null, customer, null);
    }

    public OrderDTO preview(long tableId) {
        return (OrderDTO) request(
                SocketAction.GET_TABLE_BILL_PREVIEW,
                tableId, null, null).getData();
    }

    @SuppressWarnings("unchecked")
    public List<UserDTO> listCustomers() {
        Object data = request(SocketAction.ADMIN_GET_ACCOUNTS, null, null, null).getData();
        List<UserDTO> customers = new java.util.ArrayList<>();
        if (data instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof UserDTO u && "CUSTOMER".equalsIgnoreCase(u.getRoleName())) {
                    customers.add(u);
                }
            }
        }
        return customers;
    }

    public void pay(long tableId, OrderDTO preview, dbEnums.PaymentMethod method, UserDTO customer) {
        BillDTO bill = new BillDTO();
        bill.setTableSessionId(preview.getTableSessionId());
        bill.setPaymentMethod(method);
        if (customer != null) {
            bill.setUserId(customer.getId());
        }
        request(SocketAction.PAY_TABLE_BILL,
                tableId, bill, preview.getCheckoutRevision());
    }

    public void close() {
        socket.logout();
        socket.close();
    }
}
