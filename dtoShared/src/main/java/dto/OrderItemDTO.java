package dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class OrderItemDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long orderId;
    private Long foodsId;
    private String foodsName;
    private BigDecimal unitPrice;
    private Integer quantity;
    private BigDecimal lineTotal;
    private OrderStatus status; // Đồng bộ trạng thái từng món từ Bếp về Tablet
    private String note; // order_items.note VARCHAR(500), nullable
    private LocalDateTime updatedAt; // DATETIME(3) NOT NULL
    private LocalDateTime completedAt; // DATETIME(3), nullable

    public OrderItemDTO() {}

    public OrderItemDTO(Long id, Long orderId, Long foodsId, String foodsName,
                        BigDecimal unitPrice, Integer quantity, BigDecimal lineTotal, OrderStatus status) {
        this.id = id;
        this.orderId = orderId;
        this.foodsId = foodsId;
        this.foodsName = foodsName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.lineTotal = lineTotal;
        this.status = status;
    }

    public OrderItemDTO(Long id, Long orderId, Long foodsId, String foodsName,
                        BigDecimal unitPrice, Integer quantity, BigDecimal lineTotal,
                        OrderStatus status, String note, LocalDateTime updatedAt,
                        LocalDateTime completedAt) {
        this(id, orderId, foodsId, foodsName, unitPrice, quantity, lineTotal, status);
        this.note = note;
        this.updatedAt = updatedAt;
        this.completedAt = completedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public Long getFoodsId() { return foodsId; }
    public void setFoodsId(Long foodsId) { this.foodsId = foodsId; }

    public String getFoodsName() { return foodsName; }
    public void setFoodsName(String foodsName) { this.foodsName = foodsName; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public BigDecimal getLineTotal() { return lineTotal; }
    public void setLineTotal(BigDecimal lineTotal) { this.lineTotal = lineTotal; }

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}