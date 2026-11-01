package bus;

import dao.CartDAO;
import dao.FoodDAO;
import dto.CartItemDTO;
import dto.FoodDTO;

import java.math.BigDecimal;
import java.util.List;

public class CartBUS {

    private final CartDAO cartDAO;
    private final FoodDAO foodDAO;

    public CartBUS() {
        this.cartDAO = new CartDAO();
        this.foodDAO = new FoodDAO();
    }

    /**
     * Lấy giỏ hàng của người dùng
     */
    public List<CartItemDTO> getCartByUserId(Long userId) {

        validateId(userId, "ID người dùng");

        return cartDAO.getCartByUserId(userId);
    }

    /**
     * Thêm món vào giỏ hàng
     */
    public boolean addItem(Long userId, Long foodId, int quantity) {

        validateId(userId, "ID người dùng");
        validateId(foodId, "ID món ăn");
        validateQuantity(quantity);

        // Kiểm tra món ăn tồn tại và đang được công khai
        FoodDTO food = foodDAO.getFoodById(foodId);

        if (food == null) {
            throw new IllegalArgumentException("Món ăn không tồn tại.");
        }

        if (Boolean.TRUE.equals(food.getIsDeleted())) {
            throw new IllegalArgumentException("Món ăn đã bị xóa.");
        }

        if (!Boolean.TRUE.equals(food.getIsPublished())) {
            throw new IllegalArgumentException("Món ăn hiện không được bán.");
        }

        return cartDAO.addItem(userId, foodId, quantity);
    }

    /**
     * Cập nhật số lượng món
     */
    public boolean updateQuantity(Long userId, Long foodId, int quantity) {

        validateId(userId, "ID người dùng");
        validateId(foodId, "ID món ăn");
        validateQuantity(quantity);

        return cartDAO.updateQuantity(userId, foodId, quantity);
    }

    /**
     * Xóa một món khỏi giỏ hàng
     */
    public boolean removeItem(Long userId, Long foodId) {

        validateId(userId, "ID người dùng");
        validateId(foodId, "ID món ăn");

        return cartDAO.removeItem(userId, foodId);
    }

    /**
     * Xóa toàn bộ giỏ hàng
     */
    public boolean clearCart(Long userId) {

        validateId(userId, "ID người dùng");

        return cartDAO.clearCart(userId);
    }

    /**
     * Tính tổng tiền giỏ hàng
     */
    public BigDecimal getCartTotal(Long userId) {

        validateId(userId, "ID người dùng");

        List<CartItemDTO> items = cartDAO.getCartByUserId(userId);

        BigDecimal total = BigDecimal.ZERO;

        for (CartItemDTO item : items) {
            total = total.add(item.getLineTotal());
        }

        return total;
    }

    // ================= VALIDATION =================

    private void validateId(Long id, String fieldName) {

        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                    fieldName + " không hợp lệ."
            );
        }
    }

    private void validateQuantity(int quantity) {

        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "Số lượng món ăn phải lớn hơn 0."
            );
        }
    }
}