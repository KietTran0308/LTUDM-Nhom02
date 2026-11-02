package bus;

import java.sql.SQLException;
import java.util.List;

import dao.CategoryDAO;
import dto.CategoryDTO;

public class CategoryBUS {
    private final CategoryDAO categoryDAO;

    public CategoryBUS() {
        this.categoryDAO = new CategoryDAO();
    }

    public List<CategoryDTO> getAllCategories() throws SQLException {
        return categoryDAO.getAllCategories();
    }

    public CategoryDTO getCategoryById(long id) throws SQLException {
        if (id <= 0) {
            throw new IllegalArgumentException("ID danh mục không hợp lệ");
        }
        CategoryDTO category = categoryDAO.getCategoryById(id);
        if (category == null) {
            throw new IllegalArgumentException("Không tìm thấy danh mục với ID: " + id);
        }
        return category;
    }

    public CategoryDTO createCategory(CategoryDTO category) throws SQLException {
        if (category == null) {
            throw new IllegalArgumentException("Dữ liệu danh mục không được để trống");
        }
        String name = category.getName() == null ? "" : category.getName().trim();
        String slug = category.getSlug() == null ? "" : category.getSlug().trim();

        if (name.isEmpty()) throw new IllegalArgumentException("Tên danh mục không được để trống");
        if (slug.isEmpty()) throw new IllegalArgumentException("Slug không được để trống");
        if (categoryDAO.existsByName(name)) throw new IllegalArgumentException("Tên danh mục đã tồn tại");
        if (categoryDAO.existsBySlug(slug)) throw new IllegalArgumentException("Slug đã tồn tại");

        category.setName(name);
        category.setSlug(slug);
        return categoryDAO.createCategory(category);
    }

    public CategoryDTO updateCategory(CategoryDTO category) throws SQLException {
        if (category == null) {
            throw new IllegalArgumentException("Dữ liệu danh mục không được để trống");
        }
        if (category.getId() == null || category.getId() <= 0) {
            throw new IllegalArgumentException("ID danh mục không hợp lệ");
        }

        CategoryDTO oldCategory = categoryDAO.getCategoryById(category.getId());
        if (oldCategory == null) {
            throw new IllegalArgumentException("Không tìm thấy danh mục với ID: " + category.getId());
        }

        if (category.getName() != null) {
            String name = category.getName().trim();
            if (name.isEmpty()) {
                throw new IllegalArgumentException("Tên danh mục không được để trống");
            }
            if (categoryDAO.existsByNameExceptId(name, category.getId())) {
                throw new IllegalArgumentException("Tên danh mục đã tồn tại");
            }
            category.setName(name);
        } else {
            category.setName(oldCategory.getName());
        }

        if (category.getSlug() != null) {
            String slug = category.getSlug().trim();
            if (slug.isEmpty()) {
                throw new IllegalArgumentException("Slug không được để trống");
            }
            if (categoryDAO.existsBySlugExceptId(slug, category.getId())) {
                throw new IllegalArgumentException("Slug đã tồn tại");
            }
            category.setSlug(slug);
        } else {
            category.setSlug(oldCategory.getSlug());
        }

        if (category.getDescription() == null) {
            category.setDescription(oldCategory.getDescription());
        }
        return categoryDAO.updateCategory(category);
    }

    public boolean deleteCategory(long id) throws SQLException {
        if (id <= 0) throw new IllegalArgumentException("ID không hợp lệ");
        if (categoryDAO.getCategoryById(id) == null) {
            throw new IllegalArgumentException("Không tìm thấy danh mục");
        }
        if (!categoryDAO.deleteCategory(id)) {
            throw new IllegalArgumentException("Danh mục đang được món ăn sử dụng nên không thể xóa");
        }
        return true;
    }
}