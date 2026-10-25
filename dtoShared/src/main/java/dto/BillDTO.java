package dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class BillDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long orderId;
    private BigDecimal totalAmount;
    private BigDecimal discount;
    private BigDecimal tax;
    private PaymentMethod paymentMethod;
    private LocalDateTime paidAt;

    public BillDTO() {}

    public BillDTO(Long id, Long orderId, BigDecimal totalAmount, BigDecimal discount,
                   BigDecimal tax, PaymentMethod paymentMethod, LocalDateTime paidAt) {
        this.id = id;
        this.orderId = orderId;
        this.totalAmount = totalAmount;
        this.discount = discount;
        this.tax = tax;
        this.paymentMethod = paymentMethod;
        this.paidAt = paidAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

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
}