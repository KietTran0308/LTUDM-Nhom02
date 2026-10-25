package bus;

import dao.KdsOrderDAO;
import dto.*;

import java.sql.SQLException;
import java.util.*;

/**
 * Tầng nghiệp vụ điều phối đơn từ RestaurantKDS, chạy trong ServerApp.
 */
public class KdsOrderBUS {

    private final KdsOrderDAO dal = new KdsOrderDAO();
    private final ServerOrderBUS original = new ServerOrderBUS();

    //public List<KitchenTaskDTO> getKitchenQueue() throws SQLException {
      //  return dal.getKitchenQueue();
    //}
/**
    public List<OrderDTO> getOrders(String status) throws SQLException {
        return dal.getOrders(status);
    }
**/
    public OrderDTO getOrder(long id) throws SQLException {
        return dal.getOrder(id);
    }

    public OrderDTO activeOrderOfTable(long tableId) throws SQLException {
        return dal.getActiveOrder(tableId);
    }

    public OrderDTO placeOrder(OrderDTO order, String voucherCode) throws Exception {
        return original.processIncomingTableOrder(order, voucherCode);
    }

    public OrderDTO createOrder(OrderDTO order) throws SQLException {
        return dal.createOrder(order);
    }

    public void updateOrder(long id, String note, OrderType type) throws SQLException {
        dal.updateNote(id, note, type);
    }

    public void updateOrderStatus(long id, OrderStatus status) throws SQLException {
        dal.updateStatus(id, status);
    }

    public void updateOrderItemStatus(long id, long itemId, OrderStatus status) throws SQLException {
        dal.updateItemStatus(id, itemId, status);
    }

    public void changeTable(long id, long tableId) throws SQLException {
        dal.changeTable(id, tableId);
    }

    public void cancelOrder(long id, String reason) throws SQLException {
        dal.cancelOrder(id, reason);
    }

    public void mergeOrders(long mainId, long sourceId) throws SQLException {
        dal.mergeOrders(mainId, sourceId);
    }

    public void addItems(long orderId, Map<Long, Integer> items) throws SQLException {
        dal.addItems(orderId, items);
    }

    public void applyVoucher(long orderId, String code) throws SQLException {
        dal.applyVoucher(orderId, code);
    }

    public void removeVoucher(long orderId) throws SQLException {
        dal.applyVoucher(orderId, null);
    }
}
