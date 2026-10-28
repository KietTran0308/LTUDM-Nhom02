-- Chỉ đọc dữ liệu. Chạy sau 01 và 02.
USE annhien_datmon;
SELECT VERSION() AS mysql_version, DATABASE() AS database_name;
SELECT COUNT(*) AS table_count FROM information_schema.tables
WHERE table_schema=DATABASE() AND table_type='BASE TABLE'; -- 14 trên database mới
SELECT u.id,u.email,u.phone,r.nameRole,u.is_active,
       password_hash LIKE 'pbkdf2$210000$%' AS uses_pbkdf2
FROM users u JOIN roles r ON r.roleID=u.roleID ORDER BY u.id;
SELECT * FROM restaurant_tables ORDER BY id;
SELECT f.id,f.sku,f.name,c.name AS category_name,f.price,fi.url AS cover_url
FROM foods f LEFT JOIN categories c ON c.id=f.category_id
LEFT JOIN food_images fi ON fi.foods_id=f.id AND fi.is_cover=TRUE
WHERE f.is_published=TRUE AND f.is_deleted=FALSE ORDER BY f.id;
SELECT f.name AS food,i.name AS ingredient,r.quantity_required,i.unit,i.stock_quantity
FROM recipes r JOIN foods f ON f.id=r.foods_id JOIN ingredients i ON i.id=r.ingredient_id;
SELECT code,discount_type,discount_value,start_at,end_at FROM vouchers
WHERE is_active=TRUE AND NOW() BETWEEN start_at AND end_at;
-- Hai truy vấn dưới dự kiến không có dòng nào.
SELECT foods_id,COUNT(*) AS cover_count FROM food_images WHERE is_cover=TRUE
GROUP BY foods_id HAVING COUNT(*)>1;
SELECT oi.id FROM order_items oi WHERE oi.line_total <> oi.unit_price*oi.quantity;
-- Sau khi đặt món bằng ứng dụng, chạy lại các câu sau:
SELECT id,order_code,parent_order_id,user_id,table_id,status,subtotal,total_amount
FROM orders ORDER BY id DESC LIMIT 20;
SELECT id,order_id,foods_name,quantity,line_total,status FROM order_items ORDER BY id DESC LIMIT 30;
SELECT * FROM bill ORDER BY id DESC LIMIT 20;
