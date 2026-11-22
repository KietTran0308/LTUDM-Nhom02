package bus;

import dao.CategoryDAO;
import dao.FoodDAO;
import dto.CategoryDTO;
import dto.FoodDTO;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

public class FoodBUS {

    private final FoodDAO foodDAO;
    private final CategoryDAO categoryDAO;

    public FoodBUS() {
        this.foodDAO = new FoodDAO();
        this.categoryDAO = new CategoryDAO();
    }

    // ================= GET DATA =================

    public List<FoodDTO> getPublishedFoodsForTablet() throws SQLException {
        return foodDAO.getPublishedFoodsForTablet();
    }

    public List<CategoryDTO> getAllCategories() {
        return categoryDAO.getAllCategories();
    }

    public List<FoodDTO> getAllFoodsForAdmin() throws SQLException {
        return foodDAO.getAllFoodsForAdmin();
    }

    public FoodDTO getFoodById(Long foodId) throws SQLException {
        return requireExistingFood(foodId);
    }

    public List<FoodDTO> getPublishedFoodsByCategoryId(Long categoryId) throws SQLException {
        validateCategoryId(categoryId);
        return foodDAO.getPublishedFoodsByCategoryId(categoryId);
    }

    public List<FoodDTO> getFoodsByCategoryId(Long categoryId) throws SQLException {
        validateCategoryId(categoryId);
        return foodDAO.getFoodsByCategoryId(categoryId);
    }

    // ================= CREATE =================

    public FoodDTO createFood(FoodDTO food) throws SQLException {
        requireNonNullFood(food);
        validateFoodData(food);
        normalizeFoodFields(food);
        validateCategory(food.getCategoryId());

        if (foodDAO.existsBySku(food.getSku())) {
            throw new IllegalArgumentException("SKU món ăn đã tồn tại.");
        }
        if (foodDAO.existsByName(food.getName())) {
            throw new IllegalArgumentException("Tên món ăn đã tồn tại.");
        }
        if (food.getIsPublished() == null) {
            food.setIsPublished(true);
        }
        if (!foodDAO.insertFoodWithCover(food)) {
            throw new SQLException("Không thể tạo món ăn mới");
        }

        FoodDTO saved = foodDAO.getFoodById(food.getId());
        if (saved == null) {
            throw new SQLException("Không tìm thấy món vừa thêm");
        }
        return saved;
    }

    // ================= UPDATE =================

    public FoodDTO updateFood(FoodDTO food) throws SQLException {
        requireNonNullFood(food);
        FoodDTO oldFood = requireExistingFood(food.getId());
        validateFoodData(food);
        normalizeFoodFields(food);
        validateCategory(food.getCategoryId());

        if (foodDAO.existsBySkuExceptId(food.getSku(), food.getId())) {
            throw new IllegalArgumentException("SKU đã được sử dụng bởi món ăn khác.");
        }
        if (foodDAO.existsByNameExceptId(food.getName(), food.getId())) {
            throw new IllegalArgumentException("Tên món ăn đã được sử dụng.");
        }
        if (food.getIsPublished() == null) {
            food.setIsPublished(oldFood.getIsPublished());
        }
        if (!foodDAO.updateFoodBasicInfo(food)) {
            throw new SQLException("Không thể cập nhật món ăn");
        }

        FoodDTO saved = foodDAO.getFoodById(food.getId());
        if (saved == null) {
            throw new SQLException("Không tìm thấy món vừa cập nhật");
        }
        return saved;
    }

    // ================= DELETE & PUBLISH =================

    public boolean deleteFood(Long foodId) throws SQLException {
        requireExistingFood(foodId);
        return foodDAO.softDeleteFood(foodId);
    }

    public boolean updatePublishedStatus(Long foodId, boolean isPublished) throws SQLException {
        requireExistingFood(foodId);
        return foodDAO.updatePublishedStatus(foodId, isPublished);
    }

    // ================= VALIDATION & HELPERS =================

    private static void requireNonNullFood(FoodDTO food) {
        if (food == null) {
            throw new IllegalArgumentException("Dữ liệu món ăn không được để trống.");
        }
    }

    private FoodDTO requireExistingFood(Long foodId) throws SQLException {
        validateId(foodId);
        FoodDTO food = foodDAO.getFoodById(foodId);
        if (food == null) {
            throw new IllegalArgumentException("Không tìm thấy món ăn với ID: " + foodId);
        }
        return food;
    }

    private static void normalizeFoodFields(FoodDTO food) {
        food.setSku(food.getSku().trim());
        food.setName(food.getName().trim());
        if (food.getDescription() != null) {
            food.setDescription(food.getDescription().trim());
        }
        if (food.getCoverImageUrl() != null) {
            food.setCoverImageUrl(food.getCoverImageUrl().trim());
        }
    }

    private static void validateFoodData(FoodDTO food) {
        String sku = food.getSku() == null ? "" : food.getSku().trim();
        String name = food.getName() == null ? "" : food.getName().trim();

        if (sku.isEmpty()) {
            throw new IllegalArgumentException("SKU món ăn không được để trống.");
        }
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Tên món ăn không được để trống.");
        }
        if (food.getPrice() == null || food.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Giá món ăn phải lớn hơn 0.");
        }
    }

    private static void validateId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("ID món ăn không hợp lệ.");
        }
    }

    private void validateCategory(Long categoryId) throws SQLException {
        // Database cho phép category_id NULL
        if (categoryId == null) return;
        validateCategoryId(categoryId);
        if (categoryDAO.getCategoryById(categoryId) == null) {
            throw new IllegalArgumentException("Không tìm thấy danh mục với ID: " + categoryId);
        }
    }

    private static void validateCategoryId(Long categoryId) {
        if (categoryId == null || categoryId <= 0) {
            throw new IllegalArgumentException("ID danh mục không hợp lệ.");
        }
    }
}