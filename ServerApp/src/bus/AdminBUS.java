package bus;

import dao.BillDAO;
import dao.CategoryDAO;
import dao.IngredientDAO;
import dto.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AdminBUS {
    private final FoodBUS foodBUS = new FoodBUS();
    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final IngredientDAO ingredientDAO = new IngredientDAO();
    private final BillDAO billDAO = new BillDAO();
    private final UserBUS userBUS = new UserBUS();
    private final AuthBUS authBUS = new AuthBUS();

    // NGHIỆP VỤ QUẢN LÝ THỨC ĂN
    public List<FoodDTO> getAllFoods() throws SQLException {
        return foodBUS.getAllFoodsForAdmin();
    }

    public List<CategoryDTO> getAllCategories() {
        return categoryDAO.getAllCategories();
    }

    public boolean addNewFood(String sku, Long categoryId, String name, BigDecimal price, String coverUrl, boolean isPublished) throws SQLException {
        FoodDTO food = new FoodDTO();
        food.setSku(sku);
        food.setCategoryId(categoryId);
        food.setName(name);
        food.setSlug(generateSlug(name) + "-" + (System.currentTimeMillis() % 10000));
        food.setDescription("Món ngon chuẩn vị");
        food.setPrice(price);
        food.setIsPublished(isPublished);
        food.setCoverImageUrl(coverUrl);
        foodBUS.createFood(food);
        return true;
    }

    public boolean updateFood(Long id, String name, BigDecimal price, Long categoryId, boolean isPublished) throws SQLException {
        FoodDTO food = foodBUS.getFoodById(id);

        food.setName(name);
        food.setPrice(price);
        food.setCategoryId(categoryId);
        food.setIsPublished(isPublished);

        foodBUS.updateFood(food);
        return true;
    }

    public boolean deleteFood(Long id) throws SQLException {
        return foodBUS.deleteFood(id);
    }

    // NGHIỆP VỤ THỐNG KẾ
    public List<BillDTO> getAllBills() {
        return billDAO.getAllBills();
    }

    public record RevenueReport(List<BillDTO> bills, JSONObject summary) {}

    public RevenueReport getRevenueReport(JSONObject filters) throws SQLException {
        LocalDate from = null;
        LocalDate to = null;
        if (filters.has("from") || filters.has("to")) {
            try {
                from = LocalDate.parse(filters.optString("from"));
                to = LocalDate.parse(filters.optString("to"));
            } catch (DateTimeParseException ex) {
                throw new IllegalArgumentException("Ngày thống kê phải có dạng yyyy-MM-dd");
            }
            if (from.isAfter(to) || from.getYear() < 1000 || to.getYear() > 9999) {
                throw new IllegalArgumentException("Khoảng ngày thống kê không hợp lệ");
            }
        }

        String method = filters.optString("paymentMethod");
        dbEnums.PaymentMethod payment = method.isEmpty() ? null : dbEnums.PaymentMethod.valueOf(method);
        BillDAO.ReportRows rows = billDAO.getReportRows(from, to, payment, filters.optString("billNumber"));
        List<BillDTO> paidBills = rows.bills().stream()
                .filter(bill -> bill.getPaymentStatus() == dbEnums.PaymentStatus.PAID && bill.getPaidAt() != null)
                .toList();

        TreeMap<LocalDate, BigDecimal> revenueByDay = new TreeMap<>();
        for (BillDTO bill : paidBills) {
            revenueByDay.merge(bill.getPaidAt().toLocalDate(), bill.getTotalAmount(), BigDecimal::add);
        }
        JSONArray days = new JSONArray();
        revenueByDay.forEach((day, total) -> days.put(new JSONObject()
                .put("day", day.toString()).put("total", total)));

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
        return bills.stream()
                .map(BillDTO::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal calculateTotalDiscount(List<BillDTO> bills) {
        return bills.stream()
                .map(BillDTO::getDiscount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal calculateTotalTax(List<BillDTO> bills) {
        return bills.stream()
                .map(BillDTO::getTax)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // --- 4. NGHIỆP VỤ QUẢN LÝ TÀI KHOẢN (HOÀN THIỆN) ---

    public List<UserDTO> getAllAccounts() throws SQLException {
        List<UserDTO> users = userBUS.getAllUsers();
        users.forEach(u -> u.setPasswordHash(null)); // không gửi mã băm mật khẩu về client
        return users;
    }

    // Nghiệp vụ Thêm tài khoản mới (sử dụng AuthBUS)
    public boolean createNewAccount(String fullName, String phone, String email, Long roleId, String roleName) throws Exception {
        // Kiểm tra dữ liệu ở Server, không chỉ dựa vào GUI
        if (fullName == null || fullName.isBlank())
            throw new IllegalArgumentException("Họ tên không được để trống");
        if (phone == null || !phone.trim().matches("0\\d{9}"))
            throw new IllegalArgumentException("Số điện thoại không hợp lệ (VD: 0901234567)");
        if (email != null && !email.isBlank()
                && !email.trim().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))
            throw new IllegalArgumentException("Email không hợp lệ");
        fullName = fullName.trim();
        phone = phone.trim();

        UserDTO newUser = new UserDTO();
        newUser.setFullName(fullName);
        newUser.setPhone(phone);

        newUser.setEmail(email != null && !email.isBlank() ? email.trim() : phone + "@bepnha.com");
        newUser.setRoleName(roleName);
        newUser.setIsActive(true);
        newUser.setPasswordHash(phone);

        authBUS.registerWithRole(newUser, roleName); // role do server tra theo tên, roleId từ client bị bỏ qua
        return true;
    }

    // Nghiệp vụ Cập nhật tài khoản

    public boolean updateAccount(Long id, String fullName, String phone, String email, String roleName) throws SQLException {
        UserDTO user = userBUS.getUserById(id);

        if (user == null) {
            throw new IllegalArgumentException("Không tìm thấy tài khoản");
        }

        user.setFullName(fullName);
        user.setPhone(phone);
        user.setEmail(email != null && !email.isEmpty()
                ? email : user.getEmail());

        Long roleId = new dao.UserDAO().findRoleIdByName(roleName);

        if (roleId == null) {
            throw new IllegalArgumentException("Vai trò không tồn tại");
        }

        user.setRoleID(roleId);

        return userBUS.updateUser(user);
    }


    // Nghiệp vụ Khóa/Mở khóa tài khoản
    public boolean lockAccount(Long id) throws SQLException {
        return userBUS.deactivateUser(id);
    }


    // Hàm tiện ích chuyển tên tiếng Việt có dấu thành slug chuẩn
    private String generateSlug(String input) {
        String temp = Normalizer.normalize(input, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(temp).replaceAll("")
                .toLowerCase()
                .replaceAll("đ", "d")
                .replaceAll("[^a-z0-9\\s-]", "")
                .trim()
                .replaceAll("\\s+", "-");
    }

    /** Khóa (active=false) hoặc mở khóa (active=true) theo trạng thái đích do client yêu cầu. */
    public boolean setAccountActive(
            Long accountId, Long adminId, boolean active)
            throws SQLException {

        System.out.println("SERVER ADMINBUS:");
        System.out.println("accountId = " + accountId);
        System.out.println("adminId = " + adminId);
        System.out.println("active = " + active);

        if (accountId == null || accountId <= 0)
            throw new IllegalArgumentException("ID không hợp lệ");

        if (!active && accountId.equals(adminId))
            throw new SecurityException(
                    "Không thể tự khóa tài khoản đang đăng nhập");

        boolean result = userBUS.setAccountActive(accountId, active);

        System.out.println("Kết quả UserBUS = " + result);

        return result;
    }
    public boolean toggleAccountActive(
            Long accountId, Long adminId) throws SQLException {

        if (accountId == null || accountId <= 0)
            throw new IllegalArgumentException("ID không hợp lệ");

        if (accountId.equals(adminId))
            throw new SecurityException(
                    "Không thể tự khóa tài khoản đang đăng nhập");

        return userBUS.toggleAccountActive(accountId);
    }
}