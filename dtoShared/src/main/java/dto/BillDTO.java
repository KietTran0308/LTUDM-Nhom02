package dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Anh xa bang bill: moi table_session co toi da mot bill
 * (UNIQUE bill.table_session_id). KHONG co bill.order_id trong database.
 */
public class BillDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long tableSessionId; // NOT NULL, FK -> table_sessions.id, UNIQUE
    private BigDecimal totalAmount; // DECIMAL(14,2) NOT NULL
    private BigDecimal discount; // DECIMAL(14,2), DEFAULT 0
    private BigDecimal tax; // DECIMAL(14,2), DEFAULT 0
    private PaymentMethod paymentMethod; // NULL khi chua thanh toan
    private LocalDateTime paidAt; // NULL khi chua thanh toan
    private PaymentStatus paymentStatus; // NOT NULL, DEFAULT UNPAID
    private LocalDateTime createdAt; // NOT NULL, DEFAULT CURRENT_TIMESTAMP(3)

    public BillDTO() {}

    // Giữ constructor 7 tham số để hạn chế thay đổi code cũ.
    // Quan trọng: tham số thứ hai là tableSessionId, KHÔNG PHẢI orderId.
    public BillDTO(Long id, Long tableSessionId, BigDecimal totalAmount,
                   BigDecimal discount, BigDecimal tax,
                   PaymentMethod paymentMethod, LocalDateTime paidAt) {
        this.id = id;
        this.tableSessionId = tableSessionId;
        this.totalAmount = totalAmount;
        this.discount = discount;
        this.tax = tax;
        this.paymentMethod = paymentMethod;
        this.paidAt = paidAt;
    }

    public BillDTO(Long id, Long tableSessionId, BigDecimal totalAmount,
                   BigDecimal discount, BigDecimal tax, PaymentMethod paymentMethod,
                   LocalDateTime paidAt, PaymentStatus paymentStatus,
                   LocalDateTime createdAt) {
        this(id, tableSessionId, totalAmount, discount, tax, paymentMethod, paidAt);
        this.paymentStatus = paymentStatus;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTableSessionId() { return tableSessionId; }
    public void setTableSessionId(Long tableSessionId) { this.tableSessionId = tableSessionId; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public BigDecimal getDiscount() { return discount; }
    public void setDiscount(BigDecimal discount) { this.discount = discount; }

    public BigDecimal getTax() { return tax; }
    public void setTax(BigDecimal tax) { this.tax = tax; }

    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }

    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }

    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(PaymentStatus paymentStatus) { this.paymentStatus = paymentStatus; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
