package dto;

import java.io.Serializable;
import java.time.LocalDateTime;

public class FoodImageDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long foodsId;
    private String url;
    private Boolean isCover;
    private Integer sortOrder;
    private LocalDateTime createdAt;

    public FoodImageDTO() {}

    public FoodImageDTO(Long id, Long foodsId, String url, Boolean isCover, Integer sortOrder, LocalDateTime createdAt) {
        this.id = id;
        this.foodsId = foodsId;
        this.url = url;
        this.isCover = isCover;
        this.sortOrder = sortOrder;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getFoodsId() { return foodsId; }
    public void setFoodsId(Long foodsId) { this.foodsId = foodsId; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public Boolean getIsCover() { return isCover; }
    public void setIsCover(Boolean isCover) { this.isCover = isCover; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}