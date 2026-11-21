package dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Ánh xạ bảng bill. total_amount ĐÃ GỒM thuế VAT (tax). */
public class BillDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long tableSessionId;
    private Long userId;            // bill.user_id, NULL nếu khách vãng lai
    private BigDecimal totalAmount;
    private BigDecimal discount;
    private Integer pointsUsed = 0; // bill.points_used, NOT NULL DEFAULT 0
    private BigDecimal tax;
    private dbEnums.PaymentMethod paymentMethod;
    private LocalDateTime paidAt;
    private dbEnums.PaymentStatus paymentStatus;
    private LocalDateTime createdAt;

    public BillDTO() {}

    public BillDTO(Long id, Long tableSessionId, BigDecimal totalAmount,
                   BigDecimal discount, BigDecimal tax,
                   dbEnums.PaymentMethod paymentMethod, LocalDateTime paidAt) {
        this.id = id;
        this.tableSessionId = tableSessionId;
        this.totalAmount = totalAmount;
        this.discount = discount;
        this.tax = tax;
        this.paymentMethod = paymentMethod;
        this.paidAt = paidAt;
    }

    public BillDTO(Long id, Long tableSessionId, BigDecimal totalAmount,
                   BigDecimal discount, BigDecimal tax, dbEnums.PaymentMethod paymentMethod,
                   LocalDateTime paidAt, dbEnums.PaymentStatus paymentStatus,
                   LocalDateTime createdAt) {
        this(id, tableSessionId, totalAmount, discount, tax, paymentMethod, paidAt);
        this.paymentStatus = paymentStatus;
        this.createdAt = createdAt;
    }

    /** Đầy đủ tất cả cột của bảng bill. */
    public BillDTO(Long id, Long tableSessionId, Long userId, BigDecimal totalAmount,
                   BigDecimal discount, Integer pointsUsed, BigDecimal tax,
                   dbEnums.PaymentMethod paymentMethod, LocalDateTime paidAt,
                   dbEnums.PaymentStatus paymentStatus, LocalDateTime createdAt) {
        this(id, tableSessionId, totalAmount, discount, tax, paymentMethod, paidAt,
                paymentStatus, createdAt);
        this.userId = userId;
        this.pointsUsed = pointsUsed == null ? 0 : pointsUsed;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTableSessionId() { return tableSessionId; }
    public void setTableSessionId(Long tableSessionId) { this.tableSessionId = tableSessionId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public BigDecimal getDiscount() { return discount; }
    public void setDiscount(BigDecimal discount) { this.discount = discount; }

    public Integer getPointsUsed() { return pointsUsed; }
    public void setPointsUsed(Integer pointsUsed) { this.pointsUsed = pointsUsed == null ? 0 : pointsUsed; }

    public BigDecimal getTax() { return tax; }
    public void setTax(BigDecimal tax) { this.tax = tax; }

    public dbEnums.PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(dbEnums.PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }

    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }

    public dbEnums.PaymentStatus getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(dbEnums.PaymentStatus paymentStatus) { this.paymentStatus = paymentStatus; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}