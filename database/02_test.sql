USE annhien_datmon;

-- 1) Món nào có tổng nhu cầu nguyên liệu > tồn kho? (kết quả phải RỖNG)
SELECT r.ingredient_id, i.name, i.stock_quantity, SUM(r.quantity_required) AS total_needed
FROM recipes r
JOIN ingredients i ON i.id = r.ingredient_id
GROUP BY r.ingredient_id, i.name, i.stock_quantity
HAVING total_needed > i.stock_quantity;

-- 2) Món nào chưa có công thức? (nên là các món đồ uống/tráng miệng nếu có, hoặc rỗng)
SELECT f.sku, f.name FROM foods f
LEFT JOIN recipes r ON r.foods_id = f.id
WHERE r.id IS NULL;

-- 3) Đếm số bản ghi từng bảng
SELECT 'roles' AS t, COUNT(*) c FROM roles
UNION ALL SELECT 'users', COUNT(*) FROM users
UNION ALL SELECT 'restaurant_tables', COUNT(*) FROM restaurant_tables
UNION ALL SELECT 'categories', COUNT(*) FROM categories
UNION ALL SELECT 'ingredients', COUNT(*) FROM ingredients
UNION ALL SELECT 'foods', COUNT(*) FROM foods
UNION ALL SELECT 'food_images', COUNT(*) FROM food_images
UNION ALL SELECT 'recipes', COUNT(*) FROM recipes
UNION ALL SELECT 'vouchers', COUNT(*) FROM vouchers;

-- Trả kết quả là 0 thì nghĩa là mọi nguyên liệu đều đủ tồn kho cho tất cả công thức