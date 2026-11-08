package bus;

import dao.*;
import dto.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AdminBUS {
    private final FoodBUS foodBUS = new FoodBUS();
    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final IngredientDAO ingredientDAO = new IngredientDAO();
    private final BillDAO billDAO = new BillDAO();
    private final UserBUS userBUS = new UserBUS(); 
    private final AuthBUS authBUS = new AuthBUS();
    
    // --- 1. NGHIỆP VỤ QUẢN LÝ MÓN ĂN ---
    
    public List<FoodDTO> getAllFoods() throws SQLException {
        // Lấy tất cả món chưa xóa cho Admin
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
        // Tạo slug cơ bản, FoodBUS sẽ lo việc trim và validate
        food.setSlug(generateSlug(name) + "-" + (System.currentTimeMillis() % 10000));
        food.setDescription("Món ngon chuẩn vị");
        food.setPrice(price);
        food.setIsPublished(isPublished);
        food.setCoverImageUrl(coverUrl);
        
        // Gọi createFood của FoodBUS để kiểm tra trùng SKU, Tên, Slug trước khi Insert
        foodBUS.createFood(food);
        return true;
    }

    public boolean updateFood(Long id, String name, BigDecimal price, Long categoryId, boolean isPublished) throws SQLException {
        // Vì GUI cập nhật không gửi SKU và Slug, ta phải lấy object cũ từ DB lên
        // để pass qua được hàm validateFoodData của FoodBUS
        FoodDTO food = foodBUS.getFoodById(id);
        
        // Cập nhật các trường mà AdminGUI gửi sang
        food.setName(name);
        food.setPrice(price);
        food.setCategoryId(categoryId);
        food.setIsPublished(isPublished);
        
        // Gọi updateFood của FoodBUS để kiểm tra trùng lặp và tính hợp lệ
        foodBUS.updateFood(food);
        return true;
    }

    public boolean deleteFood(Long id) throws SQLException {
        // Gọi deleteFood của FoodBUS để kiểm tra tồn tại trước khi soft delete
        return foodBUS.deleteFood(id);
    }


    // --- 2. NGHIỆP VỤ KHO NGUYÊN LIỆU & CẢNH BÁO ---
    public List<IngredientDTO> getAllIngredients() {
        return ingredientDAO.getAllIngredients();
    }

    /**
     * Lọc danh sách nguyên liệu có tồn kho chạm đáy (stock_quantity <= min_stock_level)
     */
    public List<IngredientDTO> getLowStockIngredients() {
        return ingredientDAO.getAllIngredients().stream()
                .filter(i -> i.getStockQuantity().compareTo(i.getMinStockLevel()) <= 0)
                .collect(Collectors.toList());
    }

    public boolean restockIngredient(Long ingredientId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return false;
        return ingredientDAO.restockIngredient(ingredientId, amount);
    }

    public boolean addIngredient(String name, String unit, BigDecimal initialStock, BigDecimal minLevel) {
        IngredientDTO ing = new IngredientDTO(null, name.trim(), unit.trim(), initialStock, minLevel, null, null);
        return ingredientDAO.insertIngredient(ing);
    }

    // --- 3. NGHIỆP VỤ THỐNG KÊ DOANH THU (BẢNG BILL) ---
    public List<BillDTO> getAllBills() {
        return billDAO.getAllBills();
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
        return userBUS.getAllUsers();
    }

    // Nghiệp vụ Thêm tài khoản mới (sử dụng AuthBUS)
    public boolean createNewAccount(String fullName, String phone, String email, Long roleId, String roleName) throws Exception {
        UserDTO newUser = new UserDTO();
        newUser.setFullName(fullName);
        newUser.setPhone(phone);
        // Nếu không có email, có thể tạo email giả định hoặc để trống tùy DB của bạn
        newUser.setEmail(email != null && !email.isEmpty() ? email : phone + "@bepnha.com"); 
        newUser.setRoleName(roleName);
        newUser.setIsActive(true);
        
        // Mật khẩu mặc định khi Admin cấp tài khoản (ví dụ: số điện thoại)
        // AuthBUS.register sẽ tự động băm (hash) mật khẩu này
        newUser.setPasswordHash(phone); 

        // Gọi hàm register của AuthBUS để kiểm tra trùng lặp và lưu DB
        authBUS.register(newUser);
        return true;
    }

    // Nghiệp vụ Cập nhật tài khoản
    public boolean updateAccount(Long id, String fullName, String phone, String email) throws SQLException {
        UserDTO user = userBUS.getUserById(id);
        
        user.setFullName(fullName);
        user.setPhone(phone);
        user.setEmail(email != null && !email.isEmpty() ? email : user.getEmail()); 
        
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
}