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

    // Lấy món đang mở bán cho Tablet
    public List<FoodDTO> getPublishedFoodsForTablet()
            throws SQLException {

        return foodDAO.getPublishedFoodsForTablet();
    }

    // Danh muc di cung thuc don cho tablet; API khong goi CategoryDAO truc tiep.
    public List<CategoryDTO> getAllCategories() {
        return categoryDAO.getAllCategories();
    }

    // Lấy tất cả món chưa xóa cho Admin
    public List<FoodDTO> getAllFoodsForAdmin()
            throws SQLException {

        return foodDAO.getAllFoodsForAdmin();
    }

    // Lấy món theo ID
    public FoodDTO getFoodById(Long foodId)
            throws SQLException {

        validateId(foodId);

        FoodDTO food = foodDAO.getFoodById(foodId);

        if (food == null) {
            throw new IllegalArgumentException(
                    "Không tìm thấy món ăn với ID: " + foodId
            );
        }

        return food;
    }

    // Lấy món đang mở bán theo danh mục
    public List<FoodDTO> getPublishedFoodsByCategoryId(Long categoryId)
            throws SQLException {

        validateCategoryId(categoryId);

        return foodDAO.getPublishedFoodsByCategoryId(categoryId);
    }

    // Lấy tất cả món theo danh mục cho Admin
    public List<FoodDTO> getFoodsByCategoryId(Long categoryId)
            throws SQLException {

        validateCategoryId(categoryId);

        return foodDAO.getFoodsByCategoryId(categoryId);
    }

    // ================= CREATE =================

    // Thêm món ăn
    public FoodDTO createFood(FoodDTO food)
            throws SQLException {

        if (food == null) {
            throw new IllegalArgumentException(
                    "Dữ liệu món ăn không được để trống."
            );
        }

        validateFoodData(food);

        // Chuẩn hóa dữ liệu
        food.setSku(food.getSku().trim());
        food.setName(food.getName().trim());
        food.setSlug(food.getSlug().trim());

        if (food.getDescription() != null) {
            food.setDescription(food.getDescription().trim());
        }

        if (food.getCoverImageUrl() != null) {
            food.setCoverImageUrl(food.getCoverImageUrl().trim());
        }

        // Kiểm tra Category nếu có
        validateCategory(food.getCategoryId());

        // Kiểm tra trùng SKU
        if (foodDAO.existsBySku(food.getSku())) {
            throw new IllegalArgumentException(
                    "SKU món ăn đã tồn tại."
            );
        }

        // Kiểm tra trùng tên
        if (foodDAO.existsByName(food.getName())) {
            throw new IllegalArgumentException(
                    "Tên món ăn đã tồn tại."
            );
        }

        // Kiểm tra trùng slug
        if (foodDAO.existsBySlug(food.getSlug())) {
            throw new IllegalArgumentException(
                    "Slug món ăn đã tồn tại."
            );
        }

        // Mặc định mở bán nếu chưa truyền trạng thái
        if (food.getIsPublished() == null) {
            food.setIsPublished(true);
        }

        if (!foodDAO.insertFoodWithCover(food)) {
            throw new SQLException("Không thể tạo món ăn mới");
        }
        FoodDTO saved = foodDAO.getFoodById(food.getId());
        if (saved == null) throw new SQLException("Không tìm thấy món vừa thêm");
        return saved;
    }

    // ================= UPDATE =================

    // Cập nhật thông tin món ăn
    public FoodDTO updateFood(FoodDTO food)
            throws SQLException {

        if (food == null) {
            throw new IllegalArgumentException(
                    "Dữ liệu món ăn không được để trống."
            );
        }

        validateId(food.getId());

        // Kiểm tra món có tồn tại không
        FoodDTO oldFood = foodDAO.getFoodById(food.getId());

        if (oldFood == null) {
            throw new IllegalArgumentException(
                    "Không tìm thấy món ăn với ID: " + food.getId()
            );
        }

        validateFoodData(food);

        // Chuẩn hóa dữ liệu
        food.setSku(food.getSku().trim());
        food.setName(food.getName().trim());
        food.setSlug(food.getSlug().trim());

        if (food.getDescription() != null) {
            food.setDescription(food.getDescription().trim());
        }
        if (food.getCoverImageUrl() != null) {
            food.setCoverImageUrl(food.getCoverImageUrl().trim());
        }

        // Kiểm tra Category mới nếu có
        validateCategory(food.getCategoryId());

        // Kiểm tra SKU trùng với món khác
        if (foodDAO.existsBySkuExceptId(
                food.getSku(), food.getId())) {

            throw new IllegalArgumentException(
                    "SKU đã được sử dụng bởi món ăn khác."
            );
        }

        // Kiểm tra tên trùng với món khác
        if (foodDAO.existsByNameExceptId(
                food.getName(), food.getId())) {

            throw new IllegalArgumentException(
                    "Tên món ăn đã được sử dụng."
            );
        }

        // Kiểm tra slug trùng với món khác
        if (foodDAO.existsBySlugExceptId(
                food.getSlug(), food.getId())) {

            throw new IllegalArgumentException(
                    "Slug đã được sử dụng."
            );
        }

        // Nếu không truyền trạng thái thì giữ trạng thái cũ
        if (food.getIsPublished() == null) {
            food.setIsPublished(oldFood.getIsPublished());
        }

        if (!foodDAO.updateFoodBasicInfo(food)) {
            throw new SQLException("Không thể cập nhật món ăn");
        }
        FoodDTO saved = foodDAO.getFoodById(food.getId());
        if (saved == null) throw new SQLException("Không tìm thấy món vừa cập nhật");
        return saved;
    }

    // ================= DELETE =================

    // Xóa mềm món ăn
    public boolean deleteFood(Long foodId)
            throws SQLException {

        validateId(foodId);

        FoodDTO food = foodDAO.getFoodById(foodId);

        if (food == null) {
            throw new IllegalArgumentException(
                    "Không tìm thấy món ăn với ID: " + foodId
            );
        }

        return foodDAO.softDeleteFood(foodId);
    }

    // ================= PUBLISH STATUS =================

    // Ẩn hoặc hiện món ăn
    public boolean updatePublishedStatus(
            Long foodId, boolean isPublished)
            throws SQLException {

        validateId(foodId);

        FoodDTO food = foodDAO.getFoodById(foodId);

        if (food == null) {
            throw new IllegalArgumentException(
                    "Không tìm thấy món ăn với ID: " + foodId
            );
        }

        return foodDAO.updatePublishedStatus(
                foodId, isPublished
        );
    }

    // ================= VALIDATION =================

    // Kiểm tra thông tin món ăn
    private void validateFoodData(FoodDTO food) {

        String sku = food.getSku() == null
                ? "" : food.getSku().trim();

        String name = food.getName() == null
                ? "" : food.getName().trim();

        String slug = food.getSlug() == null
                ? "" : food.getSlug().trim();

        if (sku.isEmpty()) {
            throw new IllegalArgumentException(
                    "SKU món ăn không được để trống."
            );
        }

        if (name.isEmpty()) {
            throw new IllegalArgumentException(
                    "Tên món ăn không được để trống."
            );
        }

        if (slug.isEmpty()) {
            throw new IllegalArgumentException(
                    "Slug món ăn không được để trống."
            );
        }

        if (food.getPrice() == null ||
                food.getPrice().compareTo(BigDecimal.ZERO) <= 0) {

            throw new IllegalArgumentException(
                    "Giá món ăn phải lớn hơn 0."
            );
        }
    }

    // Kiểm tra ID món ăn
    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                    "ID món ăn không hợp lệ."
            );
        }
    }

    // Kiểm tra Category
    private void validateCategory(Long categoryId)
            throws SQLException {

        // Database cho phép category_id NULL
        if (categoryId == null) {
            return;
        }

        validateCategoryId(categoryId);

        CategoryDTO category =
                categoryDAO.getCategoryById(categoryId);

        if (category == null) {
            throw new IllegalArgumentException(
                    "Không tìm thấy danh mục với ID: " + categoryId
            );
        }
    }

    private void validateCategoryId(Long categoryId) {
        if (categoryId == null || categoryId <= 0) {
            throw new IllegalArgumentException(
                    "ID danh mục không hợp lệ."
            );
        }
    }
}
