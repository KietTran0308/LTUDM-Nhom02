package dto;

import java.io.Serializable;
import java.time.LocalDateTime;

public class PointDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long userId;
    private Integer currentPoints = 0;      // điểm hiện có
    private Integer totalEarnedPoints = 0;  // tổng điểm đã tích lũy, dùng để xét hạng
    private dbEnums.PointTier tier = dbEnums.PointTier.DONG;
    private LocalDateTime updatedAt;

    public PointDTO() {}

    public PointDTO(Long userId, Integer currentPoints, Integer totalEarnedPoints,
                    dbEnums.PointTier tier, LocalDateTime updatedAt) {
        this.userId = userId;
        this.currentPoints = currentPoints == null ? 0 : currentPoints;
        this.totalEarnedPoints = totalEarnedPoints == null ? 0 : totalEarnedPoints;
        this.tier = tier == null ? dbEnums.PointTier.DONG : tier;
        this.updatedAt = updatedAt;
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Integer getCurrentPoints() { return currentPoints; }
    public void setCurrentPoints(Integer currentPoints) { this.currentPoints = currentPoints == null ? 0 : currentPoints; }

    public Integer getTotalEarnedPoints() { return totalEarnedPoints; }
    public void setTotalEarnedPoints(Integer totalEarnedPoints) { this.totalEarnedPoints = totalEarnedPoints == null ? 0 : totalEarnedPoints; }

    public dbEnums.PointTier getTier() { return tier; }
    public void setTier(dbEnums.PointTier tier) { this.tier = tier == null ? dbEnums.PointTier.DONG : tier; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}