package bus;

import dao.BillDAO;
import dao.CategoryDAO;
import dao.PointDAO;
import dao.UserDAO;
import dao.VoucherDAO;
import dto.BillDTO;
import dto.CategoryDTO;
import dto.FoodDTO;
import dto.PointDTO;
import dto.PointTransactionDTO;
import dto.UserDTO;
import dto.VoucherDTO;
import dto.dbEnums;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Pattern;

public class AdminBUS {

    private static final Pattern PHONE_PATTERN = Pattern.compile("0\\d{9}");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern VOUCHER_CODE_PATTERN = Pattern.compile("(?i)[A-Z0-9_-]{3,50}");

    private final FoodBUS foodBUS = new FoodBUS();
    private final UserBUS userBUS = new UserBUS();
    private final AuthBUS authBUS = new AuthBUS();

    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final BillDAO billDAO = new BillDAO();
    private final UserDAO userDAO = new UserDAO();
    private final PointDAO pointDAO = new PointDAO();
    private final VoucherDAO voucherDAO = new VoucherDAO();

    public record RevenueReport(List<BillDTO> bills, JSONObject summary) { }

    // ==========================================================
    // --- 1. NGHIỆP VỤ QUẢN LÝ THỨC ĂN & DANH MỤC ---
    // ==========================================================
    public List<FoodDTO> getAllFoods() throws SQLException {
        return foodBUS.getAllFoodsForAdmin();
    }

    public List<CategoryDTO> getAllCategories() {
        return categoryDAO.getAllCategories();
    }

    public boolean addNewFood(String sku, Long categoryId, String name,
                              BigDecimal price, String coverUrl, boolean isPublished) throws SQLException {
        FoodDTO food = new FoodDTO();
        food.setSku(sku);
        food.setCategoryId(categoryId);
        food.setName(name);
        food.setDescription("Món ngon chuẩn vị");
        food.setPrice(price);
        food.setIsPublished(isPublished);
        food.setCoverImageUrl(coverUrl);
        foodBUS.createFood(food);
        return true;
    }

    public boolean updateFood(Long id, String name, BigDecimal price,
                              Long categoryId, boolean isPublished) throws SQLException {
        FoodDTO food = foodBUS.getFoodById(id);
        if (food == null) {
            throw new IllegalArgumentException("Không tìm thấy món ăn cần cập nhật");
        }
        food.setName(name == null ? "" : name.trim());
        food.setPrice(price);
        food.setCategoryId(categoryId);
        food.setIsPublished(isPublished);
        foodBUS.updateFood(food);
        return true;
    }

    public boolean deleteFood(Long id) throws SQLException {
        return foodBUS.deleteFood(id);
    }

    // ==========================================================
    // --- 2. NGHIỆP VỤ HÓA ĐƠN & THỐNG KÊ DOANH THU ---
    // ==========================================================
    public List<BillDTO> getAllBills() {
        return billDAO.getAllBills();
    }

    public RevenueReport getRevenueReport(JSONObject filters) throws SQLException {
        JSONObject safeFilters = filters == null ? new JSONObject() : filters;
        LocalDate from = null;
        LocalDate to = null;

        if (safeFilters.has("from") || safeFilters.has("to")) {
            try {
                from = LocalDate.parse(safeFilters.optString("from"));
                to = LocalDate.parse(safeFilters.optString("to"));
            } catch (DateTimeParseException ex) {
                throw new IllegalArgumentException("Ngày thống kê phải có dạng yyyy-MM-dd");
            }
            if (from.isAfter(to) || from.getYear() < 1000 || to.getYear() > 9999) {
                throw new IllegalArgumentException("Khoảng ngày thống kê không hợp lệ");
            }
        }

        String method = safeFilters.optString("paymentMethod", "").trim();
        dbEnums.PaymentMethod payment = (method.isEmpty() || "ALL".equalsIgnoreCase(method))
                ? null
                : dbEnums.PaymentMethod.valueOf(method.toUpperCase(Locale.ROOT));

        BillDAO.ReportRows rows = billDAO.getReportRows(
                from, to, payment, safeFilters.optString("billNumber", "").trim()
        );

        List<BillDTO> paidBills = rows.bills().stream()
                .filter(b -> b.getPaymentStatus() == dbEnums.PaymentStatus.PAID && b.getPaidAt() != null)
                .toList();

        TreeMap<LocalDate, BigDecimal> revenueByDay = new TreeMap<>();
        for (BillDTO bill : paidBills) {
            revenueByDay.merge(bill.getPaidAt().toLocalDate(), bill.getTotalAmount(), BigDecimal::add);
        }

        JSONArray days = new JSONArray();
        revenueByDay.forEach((day, total) ->
                days.put(new JSONObject().put("day", day.toString()).put("total", total))
        );

        JSONArray foods = new JSONArray();
        for (BillDAO.TopFood food : rows.topFoods()) {
            foods.put(new JSONObject().put("name", food.name()).put("quantity", food.quantity()));
        }

        JSONObject summary = new JSONObject()
                .put("revenue", calculateTotalRevenue(paidBills))
                .put("discount", calculateTotalDiscount(paidBills))
                .put("tax", calculateTotalTax(paidBills))
                .put("paidBillCount", paidBills.size())
                .put("days", days)
                .put("topFoods", foods);

        return new RevenueReport(rows.bills(), summary);
    }

    public BigDecimal calculateTotalRevenue(List<BillDTO> bills) {
        return sumBy(bills, BillDTO::getTotalAmount);
    }

    public BigDecimal calculateTotalDiscount(List<BillDTO> bills) {
        return sumBy(bills, BillDTO::getDiscount);
    }

    public BigDecimal calculateTotalTax(List<BillDTO> bills) {
        return sumBy(bills, BillDTO::getTax);
    }

    // ==========================================================
    // --- 3. NGHIỆP VỤ QUẢN LÝ TÀI KHOẢN ---
    // ==========================================================
    public List<UserDTO> getAllAccounts() throws SQLException {
        List<UserDTO> users = userBUS.getAllUsers();
        users.forEach(u -> u.setPasswordHash(null));
        return users;
    }

    public boolean createNewAccount(String fullName, String phone, String email,
                                    Long roleId, String roleName) throws Exception {
        validateContactFields(fullName, phone, email, true);

        String cleanPhone = phone.trim();
        UserDTO newUser = new UserDTO();
        newUser.setFullName(fullName.trim());
        newUser.setPhone(cleanPhone);
        newUser.setEmail(email.trim());
        newUser.setRoleID(roleId);
        newUser.setRoleName(roleName);
        newUser.setIsActive(true);
        newUser.setPasswordHash(cleanPhone);

        authBUS.registerWithRole(newUser, roleName);
        return true;
    }

    public boolean updateAccount(Long id, String fullName, String phone,
                                 String email, String roleName) throws SQLException {
        UserDTO user = userBUS.getUserById(id);
        if (user == null) {
            throw new IllegalArgumentException("Không tìm thấy tài khoản");
        }
        validateContactFields(fullName, phone, email, false);

        Long roleId = userDAO.findRoleIdByName(roleName);
        if (roleId == null) {
            throw new IllegalArgumentException("Vai trò không tồn tại");
        }

        user.setFullName(fullName.trim());
        user.setPhone(phone.trim());
        user.setEmail(email.trim());
        user.setRoleID(roleId);
        return userBUS.updateUser(user);
    }

    public boolean lockAccount(Long id) throws SQLException {
        return userBUS.deactivateUser(id);
    }

    /** Khóa (active=false) hoặc mở khóa (active=true) theo trạng thái đích do client yêu cầu. */
    public boolean setAccountActive(Long accountId, Long adminId, boolean active) throws SQLException {
        requireValidId(accountId, "ID tài khoản không hợp lệ");
        if (!active && accountId.equals(adminId)) {
            throw new SecurityException("Không thể tự khóa tài khoản đang đăng nhập");
        }
        return userBUS.setAccountActive(accountId, active);
    }

    public boolean toggleAccountActive(Long accountId, Long adminId) throws SQLException {
        requireValidId(accountId, "ID tài khoản không hợp lệ");
        if (accountId.equals(adminId)) {
            throw new SecurityException("Không thể tự khóa tài khoản đang đăng nhập");
        }
        return userBUS.toggleAccountActive(accountId);
    }

    // ==========================================================
    // --- 4. NGHIỆP VỤ QUẢN LÝ ĐIỂM THÀNH VIÊN ---
    // ==========================================================
    public List<PointDTO> getAllMemberPoints() throws SQLException {
        return pointDAO.getAllMemberPoints();
    }

    public List<PointTransactionDTO> getPointHistory(Long userId) throws SQLException {
        requireValidId(userId, "ID thành viên không hợp lệ");
        return pointDAO.getPointHistory(userId);
    }

    // ==========================================================
    // --- 5. NGHIỆP VỤ QUẢN LÝ VOUCHER ---
    // ==========================================================
    public List<VoucherDTO> getAllVouchers() throws SQLException {
        return voucherDAO.getAllVouchers();
    }

    public boolean addNewVoucher(VoucherDTO v) throws SQLException {
        prepareVoucher(v, false);
        if (voucherDAO.existsByCode(v.getCode())) {
            throw new IllegalArgumentException("Mã voucher '" + v.getCode() + "' đã tồn tại trên hệ thống.");
        }
        return voucherDAO.insertVoucher(v);
    }

    public boolean updateVoucher(VoucherDTO v) throws SQLException {
        prepareVoucher(v, true);
        return voucherDAO.updateVoucher(v);
    }

    public boolean setVoucherActive(Long id, boolean active) throws SQLException {
        requireValidId(id, "ID voucher không hợp lệ");
        return voucherDAO.setVoucherActive(id, active);
    }

    // ==========================================================
    // --- HELPER NỘI BỘ ---
    // ==========================================================
    private static void validateContactFields(String fullName, String phone, String email, boolean strictFormat) {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Họ tên không được để trống");
        }
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("Số điện thoại không được để trống");
        }
        if (strictFormat && !PHONE_PATTERN.matcher(phone.trim()).matches()) {
            throw new IllegalArgumentException("Số điện thoại không hợp lệ (VD: 0901234567)");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email không được để trống");
        }
        if (strictFormat && !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new IllegalArgumentException("Email không được để trống và phải đúng định dạng");
        }
    }

    private static BigDecimal sumBy(List<BillDTO> bills, Function<BillDTO, BigDecimal> extractor) {
        return bills.stream()
                .map(extractor)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static void requireValidId(Long id, String message) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void prepareVoucher(VoucherDTO v, boolean isUpdate) {
        if (v == null) throw new IllegalArgumentException("Dữ liệu voucher không được để trống");
        if (isUpdate) requireValidId(v.getId(), "ID voucher không hợp lệ");

        if (v.getCode() == null || !VOUCHER_CODE_PATTERN.matcher(v.getCode().trim()).matches()) {
            throw new IllegalArgumentException("Mã voucher chỉ gồm chữ, số, '_', '-' (3–50 ký tự)");
        }
        if (v.getDiscountType() == null) {
            throw new IllegalArgumentException("Loại giảm giá không hợp lệ");
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
        if (v.getMaxDiscountAmount() != null && v.getMaxDiscountAmount().signum() <= 0) {
            throw new IllegalArgumentException("Giảm tối đa phải lớn hơn 0");
        }
        if (v.getUsageLimit() != null && v.getUsageLimit() <= 0) {
            throw new IllegalArgumentException("Giới hạn lượt dùng phải lớn hơn 0");
        }
        if (v.getStartAt() == null || v.getEndAt() == null || !v.getEndAt().isAfter(v.getStartAt())) {
            throw new IllegalArgumentException("Thời gian kết thúc phải sau thời gian bắt đầu");
        }

        v.setCode(v.getCode().trim().toUpperCase(Locale.ROOT));
        if (v.getName() != null) v.setName(v.getName().trim());
        if (v.getDescription() != null) v.setDescription(v.getDescription().trim());
        if (v.getMinOrderAmount() == null) v.setMinOrderAmount(BigDecimal.ZERO);
        if (v.getIsActive() == null) v.setIsActive(true);
    }
}