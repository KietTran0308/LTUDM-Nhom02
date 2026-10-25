package bus;

import dao.KdsManagementDAO;
import dao.UserDAO;
import dto.*;

import java.sql.SQLException;
import java.text.Normalizer;
import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;

/**
 * Nghiệp vụ quản lý từ RestaurantKDS: không Spring, không JPA/REST.
 */
public class KdsManagementBUS {
    private final KdsManagementDAO dal = new KdsManagementDAO();

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private static String slug(String name) {
        return Normalizer.normalize(name.toLowerCase(), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replace('đ', 'd').replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    public List<CategoryDTO> categories() {
        return dal.categories();
    }

    public void saveCategory(CategoryDTO c) throws SQLException {
        require(c.getName() != null && !c.getName().isBlank(), "Tên danh mục bắt buộc");
        if (c.getSlug() == null || c.getSlug().isBlank()) c.setSlug(slug(c.getName()));
        if (c.getId() == null) dal.addCategory(c);
        else dal.updateCategory(c);
    }

    public void deleteCategory(long id) throws SQLException {
        dal.deleteCategory(id);
    }

    public List<TableDTO> tables() {
        return dal.tables();
    }

    public void saveTable(TableDTO t) throws SQLException {
        require(t.getTableName() != null && !t.getTableName().isBlank() && t.getCapacity() != null && t.getCapacity() > 0, "Tên và sức chứa bàn không hợp lệ");
        if (t.getStatus() == null) t.setStatus(true);
        if (t.getId() == null) dal.addTable(t);
        else dal.updateTable(t);
    }

    public void deleteTable(long id) throws SQLException {
        dal.deleteTable(id);
    }

    public List<FoodDTO> searchFoods(String key, Long categoryId) {
        return dal.foods(key, categoryId);
    }

    public List<UserDTO> users() throws SQLException {
        return dal.users();
    }

    public void saveUser(UserDTO u, String newPassword) throws SQLException {
        require(u.getEmail() != null && u.getEmail().contains("@") && u.getFullName() != null && !u.getFullName().isBlank(), "Email hoặc họ tên không hợp lệ");
        if (u.getId() == null) {
            require(newPassword != null && newPassword.length() >= 8, "Mật khẩu mới cần ít nhất 8 ký tự");
            u.setPasswordHash(PasswordUtil.encode(newPassword));
            new UserDAO().registerUser(u);
        } else dal.updateUser(u);
    }

    public void disableUser(long id) throws SQLException {
        dal.disableUser(id);
    }

    public void changePassword(long id, String oldPass, String newPass) throws SQLException {
        require(newPass != null && newPass.length() >= 8, "Mật khẩu mới cần ít nhất 8 ký tự");
        require(PasswordUtil.matches(oldPass, dal.getPasswordHash(id)), "Mật khẩu hiện tại không đúng");
        dal.changePassword(id, PasswordUtil.encode(newPass));
    }

    /**
     * Quản trị tạo mật khẩu ngẫu nhiên; không đặt lại về mật khẩu mặc định 123456.
     */
    public String resetPassword(long id) throws SQLException {
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        dal.changePassword(id, PasswordUtil.encode(random));
        return random;
    }

    public List<VoucherDTO> vouchers() throws SQLException {
        return dal.vouchers();
    }

    public void saveVoucher(VoucherDTO v) throws SQLException {
        require(v.getCode() != null && !v.getCode().isBlank(), "Mã voucher không được trống");
        require(v.getDiscountType() != null && v.getDiscountValue() != null && v.getDiscountValue().compareTo(BigDecimal.ZERO) > 0, "Kiểu và giá trị giảm không hợp lệ");
        require(v.getStartAt() != null && v.getEndAt() != null && v.getEndAt().isAfter(v.getStartAt()), "Thời hạn voucher không hợp lệ");
        if (v.getMinOrderAmount() == null) v.setMinOrderAmount(BigDecimal.ZERO);
        if (v.getIsActive() == null) v.setIsActive(true);
        if (v.getId() == null) dal.addVoucher(v);
        else dal.updateVoucher(v);
    }

    public void disableVoucher(long id) throws SQLException {
        dal.disableVoucher(id);
    }
}
