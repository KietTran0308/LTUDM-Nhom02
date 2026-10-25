package dto;

import java.io.Serializable;
import java.time.LocalDateTime;

public class ReviewDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private Long foodsId;
    private Integer rating; // 1 -> 5
    private String comment;
    private LocalDateTime createdAt;

    // Thuộc tính phụ trợ
    private String userFullName;

    public ReviewDTO() {}

    public ReviewDTO(Long id, Long userId, Long foodsId, Integer rating, String comment, LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.foodsId = foodsId;
        this.rating = rating;
        this.comment = comment;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getFoodsId() { return foodsId; }
    public void setFoodsId(Long foodsId) { this.foodsId = foodsId; }

    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getUserFullName() { return userFullName; }
    public void setUserFullName(String userFullName) { this.userFullName = userFullName; }
}