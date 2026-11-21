package dto;

import java.io.Serializable;
import java.time.LocalDateTime;

/** Ánh xạ bảng point_transactions (lịch sử cộng, trừ, điều chỉnh điểm). */
public class PointTransactionDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;          // FK points.user_id
    private Long billId;          // FK bill.id, NULL nếu không gắn với hóa đơn
    private dbEnums.PointTransactionType type;
    private Integer pointsChange; // dương là cộng, âm là trừ
    private String note;          // VARCHAR(255), nullable
    private LocalDateTime createdAt;

    public PointTransactionDTO() {}

    public PointTransactionDTO(Long id, Long userId, Long billId, dbEnums.PointTransactionType type,
                               Integer pointsChange, String note, LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.billId = billId;
        this.type = type;
        this.pointsChange = pointsChange;
        this.note = note;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getBillId() { return billId; }
    public void setBillId(Long billId) { this.billId = billId; }

    public dbEnums.PointTransactionType getType() { return type; }
    public void setType(dbEnums.PointTransactionType type) { this.type = type; }

    public Integer getPointsChange() { return pointsChange; }
    public void setPointsChange(Integer pointsChange) { this.pointsChange = pointsChange; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}