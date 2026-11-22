package bus;

import api.SocketClient;
import dto.*;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

public final class AdminBUS {

    private static final long REQUEST_TIMEOUT_MS = 7000L;
    private final SocketClient socket;

    public AdminBUS(SocketClient socket) {
        if (socket == null || !socket.isConnected()) {
            throw new IllegalArgumentException("Chưa kết nối máy chủ quản trị");
        }
        this.socket = socket;
    }

    // ==========================================================
    // --- 0. ĐĂNG NHẬP QUẢN TRỊ ---
    // ==========================================================
    public static UserDTO loginAdmin(SocketClient socket, String account, String password) {
        if (socket == null) {
            throw new IllegalArgumentException("Socket không hợp lệ");
        }
        if (account == null || account.isBlank() || password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng nhập tài khoản và mật khẩu");
        }
        if (!socket.isConnected() && !socket.connect(null)) {
            throw new IllegalStateException("Không kết nối được Server");
        }

        SocketPacket response = socket.requestAndWait(
                new SocketPacket(SocketAction.LOGIN, null, account.trim(), password),
                REQUEST_TIMEOUT_MS
        );

        if (!(response.getData() instanceof UserDTO admin)) {
            throw new IllegalStateException("Server trả về tài khoản không hợp lệ");
        }
        if (!"ADMIN".equalsIgnoreCase(admin.getRoleName())) {
            throw new SecurityException("Tài khoản không có quyền ADMIN");
        }
        return admin;
    }

    // ==========================================================
    // --- 1. NGHIỆP VỤ THỰC ĐƠN & DANH MỤC ---
    // ==========================================================
    public List<FoodDTO> getAllFoods() {
        return castList(request(SocketAction.ADMIN_GET_FOODS, null).getData());
    }

    public List<CategoryDTO> getAllCategories() {
        return castList(request(SocketAction.ADMIN_GET_CATEGORIES, null).getData());
    }

    public boolean addNewFood(String sku, Long categoryId, String name,
                              BigDecimal price, String coverUrl, boolean isPublished) {
        if (sku == null || sku.isBlank()) throw new IllegalArgumentException("Mã SKU không được để trống");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Tên món không được để trống");
        if (price == null || price.signum() < 0) throw new IllegalArgumentException("Giá bán không hợp lệ");

        FoodDTO food = new FoodDTO();
        food.setSku(sku.trim());
        food.setCategoryId(categoryId);
        food.setName(name.trim());
        food.setPrice(price);
        food.setCoverImageUrl(coverUrl == null ? null : coverUrl.trim());
        food.setIsPublished(isPublished);

        request(SocketAction.ADMIN_ADD_FOOD, food);
        return true;
    }

    public boolean updateFood(Long id, String name, BigDecimal price,
                              Long categoryId, boolean isPublished) {
        requireValidId(id, "ID món ăn không hợp lệ");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Tên món không được để trống");
        if (price == null || price.signum() < 0) throw new IllegalArgumentException("Giá bán không hợp lệ");

        FoodDTO food = new FoodDTO();
        food.setId(id);
        food.setName(name.trim());
        food.setPrice(price);
        food.setCategoryId(categoryId);
        food.setIsPublished(isPublished);

        request(SocketAction.ADMIN_UPDATE_FOOD, food);
        return true;
    }

    public boolean deleteFood(Long id) {
        requireValidId(id, "ID món ăn không hợp lệ");
        request(SocketAction.ADMIN_DELETE_FOOD, id);
        return true;
    }

    // ==========================================================
    // --- 2. NGHIỆP VỤ HÓA ĐƠN & BÁO CÁO DOANH THU ---
    // ==========================================================
    public record RevenueReport(List<BillDTO> bills, JSONObject summary) {
    }

    public List<BillDTO> getAllBills() {
        return castList(request(SocketAction.ADMIN_GET_BILLS, null).getData());
    }

    public RevenueReport getRevenueReport(LocalDate from, LocalDate to,
                                          String billNumber, String paymentMethod) {
        if ((from == null) != (to == null)) {
            throw new IllegalArgumentException("Vui lòng chọn đầy đủ cả ngày bắt đầu và ngày kết thúc");
        }
        if (from != null && to.isBefore(from)) {
            throw new IllegalArgumentException("Ngày kết thúc không được nhỏ hơn ngày bắt đầu");
        }

        JSONObject filters = new JSONObject()
                .put("billNumber", billNumber == null ? "" : billNumber.trim())
                .put("paymentMethod", paymentMethod == null ? "ALL" : paymentMethod.trim());
        if (from != null) {
            filters.put("from", from.toString()).put("to", to.toString());
        }

        SocketPacket packet = request(SocketAction.ADMIN_GET_BILLS, filters);
        if (!(packet.getExtraData() instanceof JSONObject summary)) {
            throw new IllegalStateException("Server chưa trả dữ liệu thống kê");
        }
        return new RevenueReport(castList(packet.getData()), summary);
    }

    // ==========================================================
    // --- 3. NGHIỆP VỤ QUẢN LÝ TÀI KHOẢN ---
    // ==========================================================
    public List<UserDTO> getAllAccounts() {
        return castList(request(SocketAction.ADMIN_GET_ACCOUNTS, null).getData());
    }

    public boolean createNewAccount(String fullName, String phone, String email,
                                    Long roleId, String roleName) {
        if (fullName == null || fullName.isBlank()) throw new IllegalArgumentException("Họ tên không được để trống");
        if (email == null || email.isBlank()) throw new IllegalArgumentException("Email không được để trống");

        UserDTO user = new UserDTO();
        user.setFullName(fullName.trim());
        user.setPhone(phone == null ? null : phone.trim());
        user.setEmail(email.trim());
        user.setRoleID(roleId);
        user.setRoleName(roleName);

        request(SocketAction.ADMIN_CREATE_ACCOUNT, user);
        return true;
    }

    public boolean updateAccount(Long id, String fullName, String phone,
                                 String email, String roleName) {
        requireValidId(id, "ID tài khoản không hợp lệ");
        if (fullName == null || fullName.isBlank()) throw new IllegalArgumentException("Họ tên không được để trống");
        if (email == null || email.isBlank()) throw new IllegalArgumentException("Email không được để trống");

        UserDTO user = new UserDTO();
        user.setId(id);
        user.setFullName(fullName.trim());
        user.setPhone(phone == null ? null : phone.trim());
        user.setEmail(email.trim());
        user.setRoleName(roleName);

        request(SocketAction.ADMIN_UPDATE_ACCOUNT, user);
        return true;
    }

    /**
     * Đặt trạng thái đích: active=true là mở khóa, active=false là khóa.
     */
    public boolean setAccountActive(Long id, boolean active) {
        requireValidId(id, "ID tài khoản không hợp lệ");
        request(SocketAction.ADMIN_LOCK_ACCOUNT, id, active ? "UNLOCK" : "LOCK");
        return true;
    }

    // ==========================================================
    // --- 4. NGHIỆP VỤ QUẢN LÝ ĐIỂM THÀNH VIÊN ---
    // ==========================================================
    public List<PointDTO> getAllMemberPoints() {
        return castList(request(SocketAction.ADMIN_GET_POINTS, null).getData());
    }

    public List<PointTransactionDTO> getPointHistory(Long userId) {
        requireValidId(userId, "ID thành viên không hợp lệ");
        return castList(request(SocketAction.ADMIN_GET_POINT_HISTORY, userId).getData());
    }

    // ==========================================================
    // --- 5. NGHIỆP VỤ QUẢN LÝ VOUCHER ---
    // ==========================================================
    public List<VoucherDTO> getAllVouchers() {
        return castList(request(SocketAction.ADMIN_GET_VOUCHERS, null).getData());
    }

    public boolean addNewVoucher(VoucherDTO voucher) {
        validateVoucher(voucher, false);
        request(SocketAction.ADMIN_ADD_VOUCHER, voucher);
        return true;
    }

    public boolean updateVoucher(VoucherDTO voucher) {
        validateVoucher(voucher, true);
        request(SocketAction.ADMIN_UPDATE_VOUCHER, voucher);
        return true;
    }

    public boolean setVoucherActive(Long id, boolean active) {
        requireValidId(id, "ID voucher không hợp lệ");
        request(SocketAction.ADMIN_TOGGLE_VOUCHER, id, active ? "ACTIVATE" : "DEACTIVATE");
        return true;
    }

    // ==========================================================
    // --- HELPER NỘI BỘ ---
    // ==========================================================
    private SocketPacket request(SocketAction action, Object data) {
        return request(action, data, null);
    }

    private SocketPacket request(SocketAction action, Object data, Object extraData) {
        return socket.requestAndWait(
                new SocketPacket(action, null, data, extraData),
                REQUEST_TIMEOUT_MS
        );
    }

    private static void requireValidId(Long id, String message) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> castList(Object data) {
        return data instanceof List<?> list ? (List<T>) list : Collections.emptyList();
    }

    private static void validateVoucher(VoucherDTO v, boolean isUpdate) {
        if (v == null) throw new IllegalArgumentException("Dữ liệu voucher trống");
        if (isUpdate) requireValidId(v.getId(), "Thiếu ID voucher cần cập nhật");
        if (v.getCode() == null || v.getCode().isBlank()) {
            throw new IllegalArgumentException("Mã voucher không được để trống");
        }
        if (v.getName() == null || v.getName().isBlank()) {
            throw new IllegalArgumentException("Tên chương trình voucher không được để trống");
        }
        if (v.getDiscountType() == null) {
            throw new IllegalArgumentException("Vui lòng chọn loại giảm giá");
        }
        if (v.getDiscountValue() == null || v.getDiscountValue().signum() <= 0) {
            throw new IllegalArgumentException("Giá trị giảm phải lớn hơn 0");
        }
        if (v.getDiscountType() == dbEnums.DiscountType.PERCENT
                && v.getDiscountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Giảm theo phần trăm không được vượt quá 100%");
        }
        if (v.getMinOrderAmount() != null && v.getMinOrderAmount().signum() < 0) {
            throw new IllegalArgumentException("Đơn tối thiểu không được âm");
        }
        if (v.getMaxDiscountAmount() != null && v.getMaxDiscountAmount().signum() < 0) {
            throw new IllegalArgumentException("Mức giảm tối đa không được âm");
        }
        if (v.getUsageLimit() != null && v.getUsageLimit() <= 0) {
            throw new IllegalArgumentException("Giới hạn lượt dùng phải lớn hơn 0");
        }
        if (v.getStartAt() == null || v.getEndAt() == null || !v.getEndAt().isAfter(v.getStartAt())) {
            throw new IllegalArgumentException("Thời gian kết thúc phải sau thời gian bắt đầu");
        }
    }
}