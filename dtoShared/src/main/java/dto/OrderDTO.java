package dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class OrderDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String orderCode;
    private Long parentOrderId;
    private Long userId, tableId, tableSessionId, voucherId;
    private dbEnums.OrderStatus status;
    private BigDecimal subtotal, discountAmount, totalAmount, checkoutTax;
    private String checkoutRevision, note;
    private String cancelReason;
    private LocalDateTime cancelledAt;
    private Long confirmedBy;
    private LocalDateTime createdAt, updatedAt;

    // Danh sách món đi kèm trong đơn để gửi trọn gói qua TCP Socket
    private List<OrderItemDTO> items = new ArrayList<>();
    private String tableName; // Tên bàn hiển thị trên màn hình Bếp

    public OrderDTO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOrderCode() { return orderCode; }
    public void setOrderCode(String orderCode) { this.orderCode = orderCode; }

    public Long getParentOrderId() { return parentOrderId; }
    public void setParentOrderId(Long parentOrderId) { this.parentOrderId = parentOrderId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getTableId() { return tableId; }
    public void setTableId(Long tableId) { this.tableId = tableId; }

    public Long getTableSessionId() { return tableSessionId; }
    public void setTableSessionId(Long tableSessionId) { this.tableSessionId = tableSessionId; }

    public Long getVoucherId() { return voucherId; }
    public void setVoucherId(Long voucherId) { this.voucherId = voucherId; }

    public dbEnums.OrderStatus getStatus() { return status; }
    public void setStatus(dbEnums.OrderStatus status) { this.status = status; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public BigDecimal getCheckoutTax() { return checkoutTax; }
    public void setCheckoutTax(BigDecimal checkoutTax) { this.checkoutTax = checkoutTax; }

    public BigDecimal getCheckoutTotal() { return totalAmount.add(checkoutTax); }

    public String getCheckoutRevision() { return checkoutRevision; }
    public void setCheckoutRevision(String checkoutRevision) { this.checkoutRevision = checkoutRevision; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }

    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }

    public Long getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(Long confirmedBy) { this.confirmedBy = confirmedBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public List<OrderItemDTO> getItems() { return items; }
    public void setItems(List<OrderItemDTO> items) { this.items = items; }

    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }

}
