package dto;

import java.io.Serializable;
import java.math.BigDecimal;

public class RecipeDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long foodsId;
    private Long ingredientId;
    private BigDecimal quantityRequired;

    // Thuộc tính phụ trợ hiển thị tên nguyên liệu & đơn vị
    private String ingredientName;
    private String unit;

    public RecipeDTO() {}

    public RecipeDTO(Long id, Long foodsId, Long ingredientId, BigDecimal quantityRequired) {
        this.id = id;
        this.foodsId = foodsId;
        this.ingredientId = ingredientId;
        this.quantityRequired = quantityRequired;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getFoodsId() { return foodsId; }
    public void setFoodsId(Long foodsId) { this.foodsId = foodsId; }

    public Long getIngredientId() { return ingredientId; }
    public void setIngredientId(Long ingredientId) { this.ingredientId = ingredientId; }

    public BigDecimal getQuantityRequired() { return quantityRequired; }
    public void setQuantityRequired(BigDecimal quantityRequired) { this.quantityRequired = quantityRequired; }

    public String getIngredientName() { return ingredientName; }
    public void setIngredientName(String ingredientName) { this.ingredientName = ingredientName; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
}