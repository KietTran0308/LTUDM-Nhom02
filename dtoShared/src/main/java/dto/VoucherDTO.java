package dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class VoucherDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String code;               // UNIQUE
    private String name;               // nullable
    private String description;        // nullable
    private dbEnums.DiscountType discountType; // PERCENT hoặc FIXED
    private BigDecimal discountValue;  // % nếu PERCENT, số tiền nếu FIXED
    private BigDecimal minOrderAmount; // nullable, mặc định 0
    private BigDecimal maxDiscountAmount; // nullable, trần giảm cho loại PERCENT
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Integer usageLimit;        // nullable, NULL là không giới hạn
    private Integer usedCount;         // nullable, mặc định 0
    private Boolean isActive;          // nullable, mặc định true
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public VoucherDTO() {}

    public VoucherDTO(Long id, String code, String name, String description,
                      dbEnums.DiscountType discountType, BigDecimal discountValue,
                      BigDecimal minOrderAmount, BigDecimal maxDiscountAmount,
                      LocalDateTime startAt, LocalDateTime endAt,
                      Integer usageLimit, Integer usedCount, Boolean isActive,
                      LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.description = description;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.minOrderAmount = minOrderAmount;
        this.maxDiscountAmount = maxDiscountAmount;
        this.startAt = startAt;
        this.endAt = endAt;
        this.usageLimit = usageLimit;
        this.usedCount = usedCount;
        this.isActive = isActive;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public dbEnums.DiscountType getDiscountType() { return discountType; }
    public void setDiscountType(dbEnums.DiscountType discountType) { this.discountType = discountType; }

    public BigDecimal getDiscountValue() { return discountValue; }
    public void setDiscountValue(BigDecimal discountValue) { this.discountValue = discountValue; }

    public BigDecimal getMinOrderAmount() { return minOrderAmount; }
    public void setMinOrderAmount(BigDecimal minOrderAmount) { this.minOrderAmount = minOrderAmount; }

    public BigDecimal getMaxDiscountAmount() { return maxDiscountAmount; }
    public void setMaxDiscountAmount(BigDecimal maxDiscountAmount) { this.maxDiscountAmount = maxDiscountAmount; }

    public LocalDateTime getStartAt() { return startAt; }
    public void setStartAt(LocalDateTime startAt) { this.startAt = startAt; }

    public LocalDateTime getEndAt() { return endAt; }
    public void setEndAt(LocalDateTime endAt) { this.endAt = endAt; }

    public Integer getUsageLimit() { return usageLimit; }
    public void setUsageLimit(Integer usageLimit) { this.usageLimit = usageLimit; }

    public Integer getUsedCount() { return usedCount; }
    public void setUsedCount(Integer usedCount) { this.usedCount = usedCount; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}