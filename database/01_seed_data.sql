-- Chạy SAU 01_schema.sql. Dữ liệu nhà hàng lẩu, không ghi đè bản ghi đã có.
-- Chạy từng phiên, không chạy đồng thời nhiều bản seed.
-- =====================================================================
-- SEED DATA - NHÀ HÀNG LẨU TRUNG QUỐC / HỒNG KÔNG / ĐÀI LOAN
-- Tương thích: 01_schema.sql
-- CẢNH BÁO: XÓA TOÀN BỘ dữ liệu hiện có trước khi seed.
-- Nguyên tắc: stock_quantity >= tổng quantity_required của mọi recipe.
--
-- LƯU Ý SCHEMA CŨ: bảng table_sessions CHỈ tồn tại ở schema mới.
-- Khối TRUNCATE dùng dynamic SQL để bỏ qua nếu đang chạy trên DB cũ
-- (không có bảng table_sessions) — tránh lỗi và tránh orphan session.
-- =====================================================================
SET NAMES utf8mb4;
USE annhien_datmon;
SET FOREIGN_KEY_CHECKS = 0;

-- 0a. Dọn table_sessions (nếu bảng tồn tại). Đặt trước để tránh session
--     cũ trỏ tới users/restaurant_tables vừa bị xoá (orphan).
SET @ts_exists := (
    SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'table_sessions'
);
SET @sql_truncate_ts := IF(@ts_exists > 0,
    'TRUNCATE TABLE table_sessions',
    'DO 0');
PREPARE stmt FROM @sql_truncate_ts;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 0b. XÓA SẠCH DỮ LIỆU CŨ (ngược thứ tự khóa ngoại)
TRUNCATE TABLE bill;
TRUNCATE TABLE order_items;
TRUNCATE TABLE orders;
TRUNCATE TABLE cart_items;
TRUNCATE TABLE reviews;
TRUNCATE TABLE food_images;
TRUNCATE TABLE recipes;
TRUNCATE TABLE foods;
TRUNCATE TABLE categories;
TRUNCATE TABLE ingredients;
TRUNCATE TABLE vouchers;
TRUNCATE TABLE restaurant_tables;
TRUNCATE TABLE users;
TRUNCATE TABLE roles;

SET FOREIGN_KEY_CHECKS = 1;
START TRANSACTION;

-- =====================================================================
-- 1. ROLES (4 bản ghi)
-- =====================================================================
INSERT INTO roles (roleID, nameRole) VALUES
(1, 'ADMIN'), (2, 'STAFF'), (3, 'CUSTOMER'), (4, 'KITCHEN');

-- =====================================================================
-- 2. USERS (45 bản ghi - tên người thật)
-- =====================================================================
INSERT INTO users (roleID, email, password_hash, full_name, phone, is_active) VALUES
-- ADMIN (2)
(1, 'nguyenminhtuan@annhien.vn', 'pbkdf2$210000$b8zQLUOXy5BDUARGobmp0g==$SG4HqRkxYQXRUHQuihM5f5x/7pR43dLvoKlHu/yT6Xc=', 'Nguyễn Minh Tuấn', '0901234567', TRUE),
(1, 'tranthuylinh@annhien.vn', 'pbkdf2$210000$b8zQLUOXy5BDUARGobmp0g==$SG4HqRkxYQXRUHQuihM5f5x/7pR43dLvoKlHu/yT6Xc=', 'Trần Thùy Linh', '0912345678', TRUE),
-- STAFF (8)
(2, 'levanhung@annhien.vn', 'pbkdf2$210000$9Picb6rIeGCtn41gecSm6Q==$r8ri3BgY8TPAcsTJAGAno46A175uX0S0n4QxVtNFPBM=', 'Lê Văn Hùng', '0923456789', TRUE),
(2, 'phamthimaianh@annhien.vn', 'pbkdf2$210000$9Picb6rIeGCtn41gecSm6Q==$r8ri3BgY8TPAcsTJAGAno46A175uX0S0n4QxVtNFPBM=', 'Phạm Thị Mai Anh', '0934567890', TRUE),
(2, 'hoangvannam@annhien.vn', 'pbkdf2$210000$9Picb6rIeGCtn41gecSm6Q==$r8ri3BgY8TPAcsTJAGAno46A175uX0S0n4QxVtNFPBM=', 'Hoàng Văn Nam', '0945678901', TRUE),
(2, 'vuthihanh@annhien.vn', 'pbkdf2$210000$9Picb6rIeGCtn41gecSm6Q==$r8ri3BgY8TPAcsTJAGAno46A175uX0S0n4QxVtNFPBM=', 'Vũ Thị Hạnh', '0956789012', TRUE),
(2, 'dangquocbao@annhien.vn', 'pbkdf2$210000$9Picb6rIeGCtn41gecSm6Q==$r8ri3BgY8TPAcsTJAGAno46A175uX0S0n4QxVtNFPBM=', 'Đặng Quốc Bảo', '0967890123', TRUE),
(2, 'buithithuong@annhien.vn', 'pbkdf2$210000$9Picb6rIeGCtn41gecSm6Q==$r8ri3BgY8TPAcsTJAGAno46A175uX0S0n4QxVtNFPBM=', 'Bùi Thị Thương', '0978901234', TRUE),
(2, 'dovanphuc@annhien.vn', 'pbkdf2$210000$9Picb6rIeGCtn41gecSm6Q==$r8ri3BgY8TPAcsTJAGAno46A175uX0S0n4QxVtNFPBM=', 'Đỗ Văn Phúc', '0989012345', TRUE),
(2, 'hothilan@annhien.vn', 'pbkdf2$210000$9Picb6rIeGCtn41gecSm6Q==$r8ri3BgY8TPAcsTJAGAno46A175uX0S0n4QxVtNFPBM=', 'Hồ Thị Lan', '0990123456', TRUE),
-- KITCHEN (5)
(4, 'ngovanloc@annhien.vn', 'pbkdf2$210000$AS6igY91wCy+ayuiJQEfcg==$d+t/Qtc6SRCKRvvHeIO5cExMThK00K4tAvkRel5GWOY=', 'Ngô Văn Lộc', '0901111222', TRUE),
(4, 'duongthimai@annhien.vn', 'pbkdf2$210000$AS6igY91wCy+ayuiJQEfcg==$d+t/Qtc6SRCKRvvHeIO5cExMThK00K4tAvkRel5GWOY=', 'Dương Thị Mai', '0912222333', TRUE),
(4, 'lyvankhang@annhien.vn', 'pbkdf2$210000$AS6igY91wCy+ayuiJQEfcg==$d+t/Qtc6SRCKRvvHeIO5cExMThK00K4tAvkRel5GWOY=', 'Lý Văn Khang', '0923333444', TRUE),
(4, 'trinhthihuong@annhien.vn', 'pbkdf2$210000$AS6igY91wCy+ayuiJQEfcg==$d+t/Qtc6SRCKRvvHeIO5cExMThK00K4tAvkRel5GWOY=', 'Trịnh Thị Hương', '0934444555', TRUE),
(4, 'caovandat@annhien.vn', 'pbkdf2$210000$AS6igY91wCy+ayuiJQEfcg==$d+t/Qtc6SRCKRvvHeIO5cExMThK00K4tAvkRel5GWOY=', 'Cao Văn Đạt', '0945555666', TRUE),
-- CUSTOMER (30)
(3, 'nguyenhoanglong@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Nguyễn Hoàng Long', '0908111222', TRUE),
(3, 'tranthiminhthu@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Trần Thị Minh Thư', '0919222333', TRUE),
(3, 'phamquocviet@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Phạm Quốc Việt', '0920333444', TRUE),
(3, 'lethithanhtruc@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Lê Thị Thanh Trúc', '0931444555', TRUE),
(3, 'hoangminhkhoa@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Hoàng Minh Khoa', '0942555666', TRUE),
(3, 'vuthibaongoc@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Vũ Thị Bảo Ngọc', '0953666777', TRUE),
(3, 'dangtrungdung@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Đặng Trung Dũng', '0964777888', TRUE),
(3, 'buithithuha@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Bùi Thị Thu Hà', '0975888999', TRUE),
(3, 'dovietanh@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Đỗ Việt Anh', '0986999000', TRUE),
(3, 'hothimyla@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Hồ Thị Mỹ La', '0997000111', TRUE),
(3, 'ngovantai@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Ngô Văn Tài', '0908111333', TRUE),
(3, 'duongthiyen@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Dương Thị Yến', '0919222444', TRUE),
(3, 'lyminhquan@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Lý Minh Quân', '0920333555', TRUE),
(3, 'trinhthihong@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Trịnh Thị Hồng', '0931444666', TRUE),
(3, 'caominhtri@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Cao Minh Trí', '0942555777', TRUE),
(3, 'truongvankhanh@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Trương Văn Khánh', '0953666888', TRUE),
(3, 'phanthikimchi@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Phan Thị Kim Chi', '0964777999', TRUE),
(3, 'vohongson@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Võ Hồng Sơn', '0975888000', TRUE),
(3, 'dinhthuphuong@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Đinh Thu Phương', '0986999111', TRUE),
(3, 'lyvanthanh@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Lý Văn Thành', '0997000222', TRUE),
(3, 'tranquoctrung@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Trần Quốc Trung', '0901122334', TRUE),
(3, 'lygiabao@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Lý Gia Bảo', '0912233445', TRUE),
(3, 'hoangchihung@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Hoàng Chí Hùng', '0923344556', TRUE),
(3, 'ngothihuong@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Ngô Thị Hương', '0934455667', TRUE),
(3, 'dangvanphong@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Đặng Văn Phong', '0945566778', TRUE),
(3, 'buiminhhoang@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Bùi Minh Hoàng', '0956677889', TRUE),
(3, 'dothuhang@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Đỗ Thu Hằng', '0967788990', TRUE),
(3, 'hovannhan@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Hồ Văn Nhân', '0978899001', TRUE),
(3, 'ngothanhtung@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Ngô Thanh Tùng', '0989900112', TRUE),
(3, 'duongthuylinh@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Dương Thùy Linh', '0990011223', TRUE);

-- =====================================================================
-- 3. RESTAURANT_TABLES (40 bản ghi)
-- =====================================================================
INSERT INTO restaurant_tables (table_name, capacity, status) VALUES
('Bàn 01', 2, TRUE), ('Bàn 02', 2, TRUE), ('Bàn 03', 4, TRUE), ('Bàn 04', 4, TRUE),
('Bàn 05', 4, TRUE), ('Bàn 06', 4, TRUE), ('Bàn 07', 6, TRUE), ('Bàn 08', 6, TRUE),
('Bàn 09', 6, TRUE), ('Bàn 10', 6, TRUE), ('Bàn 11', 8, TRUE), ('Bàn 12', 8, TRUE),
('Bàn 13', 8, TRUE), ('Bàn 14', 8, TRUE), ('Bàn 15', 10, TRUE), ('Bàn 16', 10, TRUE),
('Bàn 17', 2, TRUE), ('Bàn 18', 2, TRUE), ('Bàn 19', 4, TRUE), ('Bàn 20', 4, TRUE),
('Bàn VIP 01', 8, TRUE), ('Bàn VIP 02', 8, TRUE), ('Bàn VIP 03', 10, TRUE),
('Bàn VIP 04', 10, TRUE), ('Bàn VIP 05', 12, TRUE), ('Bàn Sân Vườn 01', 6, TRUE),
('Bàn Sân Vườn 02', 6, TRUE), ('Bàn Sân Vườn 03', 6, TRUE), ('Bàn Sân Vườn 04', 8, TRUE),
('Bàn Tầng 2 - 01', 4, TRUE), ('Bàn Tầng 2 - 02', 4, TRUE), ('Bàn Tầng 2 - 03', 4, TRUE),
('Bàn Tầng 2 - 04', 6, TRUE), ('Bàn Tầng 2 - 05', 6, TRUE), ('Bàn Tầng 2 - 06', 6, TRUE),
('Bàn Tầng 3 - 01', 8, TRUE), ('Bàn Tầng 3 - 02', 8, TRUE), ('Bàn Tầng 3 - 03', 10, TRUE),
('Bàn Tầng 3 - 04', 10, TRUE), ('Bàn Tầng 3 - 05', 12, TRUE);

-- =====================================================================
-- 4. CATEGORIES (36 bản ghi)
-- =====================================================================
INSERT INTO categories (name, slug, description) VALUES
('Nước lẩu', 'nuoc-lau', 'Các loại nước lẩu: mala, cà chua, nấm, Thái, collagen, ớt xanh'),
('Thịt bò & Cừu', 'thit-bo-cuu', 'Ba chỉ bò, bò Mỹ, bò Úc, thịt cừu cuộn'),
('Thịt heo & Gà', 'thit-heo-ga', 'Ba chỉ heo, thịt gà, lòng heo'),
('Hải sản', 'hai-san', 'Tôm, mực, cá, sò, bạch tuộc, tôm hùm'),
('Viên & Chả', 'vien-cha', 'Viên cá, viên bò, viên tôm, chả cá, xíu mại'),
('Đậu phụ & Đồ khô', 'dau-phu-do-kho', 'Đậu phụ non, đậu phụ chiên, đậu phụ da'),
('Rau & Nấm', 'rau-nam', 'Cải thảo, nấm kim châm, nấm đông cô, rau muống, cải xanh'),
('Mì & Bún', 'mi-bun', 'Mì tươi, mì udon, bún, miến, mì trứng'),
('Món khai vị Trung', 'khai-vi-trung', 'Há cảo, xíu mại, dimsum, bánh bao, gyoza'),
('Đồ uống', 'do-uong', 'Trà sữa, trà hoa quả, nước ngọt, bia, rượu'),
('Tráng miệng', 'trang-mieng', 'Pudding xoài, bánh trôi, kem, chè'),
('Nước chấm', 'nuoc-cham', 'Sốt mè, sốt sa tế, sốt tỏi, dầu hào'),
('Lẩu Đài Loan', 'lau-dai-loan', 'Đặc sản lẩu Đài Loan: lẩu sữa, lẩu Thái, lẩu dê'),
('Lẩu Hồng Kông', 'lau-hong-kong', 'Lẩu HK: lẩu hải sản, lẩu bò, lẩu cá'),
('Lẩu Trùng Khánh', 'lau-trung-khanh', 'Lẩu mala Trùng Khánh, lẩu 9 ngăn'),
('Lẩu Vịt', 'lau-vit', 'Lẩu vịt tiềm, vịt quay'),
('Lẩu Cá', 'lau-ca', 'Lẩu cá hồi, lẩu cá basa, lẩu cá tầm'),
('Lẩu Nấm', 'lau-nam', 'Lẩu nấm chay, lẩu nấm thập cẩm'),
('Lẩu Cua', 'lau-cua', 'Lẩu cua đồng, lẩu cua biển'),
('Lẩu Ếch', 'lau-ech', 'Lẩu ếch măng cay'),
('Bia & Rượu', 'bia-ruou', 'Bia Tiger, Heineken, rượu Mao Đài, rượu vang'),
('Trà sữa', 'tra-sua', 'Trà sữa trân châu, trà sữa matcha'),
('Trà hoa quả', 'tra-hoa-qua', 'Trà đào, trà vải, trà chanh dây'),
('Nước ép', 'nuoc-ep', 'Nước ép cam, dưa hấu, cà rốt'),
('Sinh tố', 'sinh-to', 'Sinh tố bơ, xoài, mãng cầu'),
('Đồ nướng', 'do-nuong', 'Thịt nướng, hải sản nướng, rau nướng'),
('Dimsum hấp', 'dimsum-hap', 'Há cảo, xíu mại, bánh bao hấp'),
('Dimsum chiên', 'dimsum-chien', 'Chả giò, bánh xếp chiên, há cảo chiên'),
('Món xào', 'mon-xao', 'Mì xào, cơm chiên, rau xào'),
('Cơm & Cháo', 'com-chao', 'Cơm chiên Dương Châu, cháo hải sản'),
('Bánh bao', 'banh-bao', 'Bánh bao kim sa, bánh bao nhân thịt'),
('Kem & Chè', 'kem-che', 'Kem vani, kem matcha, chè Thái'),
('Pudding', 'pudding', 'Pudding xoài, pudding trứng, pudding matcha'),
('Bánh trôi', 'banh-troi', 'Bánh trôi nước, bánh trôi nhân mè'),
('Sốt lẩu', 'sot-lau', 'Sốt mè, sốt sa tế, sốt nấm'),
('Topping lẩu', 'topping-lau', 'Topping thêm: trứng, phô mai, bắp');

-- =====================================================================
-- 5. INGREDIENTS (45 bản ghi)
--    stock_quantity đặt DƯ so với tổng nhu cầu recipes bên dưới.
-- =====================================================================
INSERT INTO ingredients (name, unit, stock_quantity, min_stock_level) VALUES
-- Cốt lẩu (phần)
('Cốt lẩu mala', 'phần', 500, 20),
('Cốt lẩu cà chua', 'phần', 400, 20),
('Cốt lẩu nấm', 'phần', 400, 20),
('Cốt lẩu Thái', 'phần', 350, 20),
('Cốt lẩu collagen', 'phần', 300, 15),
('Cốt lẩu ớt xanh', 'phần', 300, 15),
('Cốt lẩu sữa', 'phần', 250, 15),
-- Thịt (kg)
('Ba chỉ bò Mỹ', 'kg', 80, 10),
('Bò Úc cuộn', 'kg', 60, 10),
('Thịt cừu cuộn', 'kg', 50, 10),
('Ba chỉ heo', 'kg', 70, 10),
('Thịt gà', 'kg', 60, 10),
('Lòng heo', 'kg', 30, 5),
-- Hải sản (kg / con)
('Tôm sú', 'kg', 40, 5),
('Mực ống', 'kg', 35, 5),
('Cá hồi', 'kg', 30, 5),
('Bạch tuộc', 'kg', 25, 5),
('Sò điệp', 'kg', 20, 5),
('Tôm hùm', 'con', 50, 5),
-- Viên & chả (kg)
('Viên cá', 'kg', 50, 10),
('Viên bò', 'kg', 50, 10),
('Viên tôm', 'kg', 45, 10),
('Chả cá', 'kg', 40, 10),
('Xíu mại', 'kg', 35, 10),
-- Đậu phụ (kg)
('Đậu phụ non', 'kg', 40, 10),
('Đậu phụ chiên', 'kg', 35, 10),
('Đậu phụ da', 'kg', 30, 10),
-- Rau nấm (kg)
('Cải thảo', 'kg', 60, 15),
('Nấm kim châm', 'kg', 50, 10),
('Nấm đông cô', 'kg', 40, 10),
('Nấm linh chi', 'kg', 20, 5),
('Rau muống', 'kg', 45, 10),
('Cải xanh', 'kg', 45, 10),
('Bắp Mỹ', 'kg', 30, 10),
('Khoai tây', 'kg', 40, 10),
-- Mì (kg)
('Mì tươi', 'kg', 50, 10),
('Mì udon', 'kg', 40, 10),
('Bún tươi', 'kg', 40, 10),
('Miến', 'kg', 30, 10),
('Mì trứng', 'kg', 30, 10),
-- Khác
('Trà ô long', 'ly', 800, 50),
('Trà sữa', 'ly', 600, 50),
('Bia Tiger', 'lon', 500, 30),
('Nước suối', 'chai', 800, 50),
('Pudding xoài', 'phần', 200, 20);

-- =====================================================================
-- 6. FOODS (55 bản ghi - thực đơn lẩu Trung/HK/Đài)
-- =====================================================================
INSERT INTO foods (sku, category_id, name, slug, description, price, is_published, is_deleted) VALUES
-- Nước lẩu (7)
('SP-MALA',      (SELECT id FROM categories WHERE slug='nuoc-lau'), 'Nước lẩu mala Tứ Xuyên (nồi 1 ngăn)', 'nuoc-lau-mala',      'Cốt lẩu mala cay tê đặc trưng Tứ Xuyên', 129000, TRUE, FALSE),
('SP-TOMATO',    (SELECT id FROM categories WHERE slug='nuoc-lau'), 'Nước lẩu cà chua (nồi 1 ngăn)',        'nuoc-lau-ca-chua',   'Cốt lẩu cà chua thanh ngọt', 99000, TRUE, FALSE),
('SP-MUSHROOM',  (SELECT id FROM categories WHERE slug='nuoc-lau'), 'Nước lẩu nấm (nồi 1 ngăn)',            'nuoc-lau-nam',       'Cốt lẩu nấm thập cẩm thanh đạm', 119000, TRUE, FALSE),
('SP-THAI',      (SELECT id FROM categories WHERE slug='nuoc-lau'), 'Nước lẩu Thái chua cay (nồi 1 ngăn)',  'nuoc-lau-thai',      'Cốt lẩu Thái chua cay', 109000, TRUE, FALSE),
('SP-COLLAGEN',  (SELECT id FROM categories WHERE slug='nuoc-lau'), 'Nước lẩu collagen (nồi 1 ngăn)',       'nuoc-lau-collagen',  'Cốt lẩu collagen bổ dưỡng', 139000, TRUE, FALSE),
('SP-GREENCHILI',(SELECT id FROM categories WHERE slug='nuoc-lau'), 'Nước lẩu ớt xanh (nồi 1 ngăn)',        'nuoc-lau-ot-xanh',   'Cốt lẩu ớt xanh thanh cay', 119000, TRUE, FALSE),
('SP-MILK',      (SELECT id FROM categories WHERE slug='nuoc-lau'), 'Nước lẩu sữa Đài Loan (nồi 1 ngăn)',   'nuoc-lau-sua',       'Cốt lẩu sữa kiểu Đài Loan', 129000, TRUE, FALSE),
-- Thịt bò & Cừu (6)
('SP-BEEF-ROLL', (SELECT id FROM categories WHERE slug='thit-bo-cuu'), 'Ba chỉ bò Mỹ cuộn (đĩa 150g)',  'ba-chi-bo-my',  'Ba chỉ bò Mỹ thái lát cuộn', 89000, TRUE, FALSE),
('SP-BEEF-AUS',  (SELECT id FROM categories WHERE slug='thit-bo-cuu'), 'Bò Úc cuộn (đĩa 150g)',         'bo-uc-cuon',    'Bò Úc thái lát cuộn', 99000, TRUE, FALSE),
('SP-LAMB',      (SELECT id FROM categories WHERE slug='thit-bo-cuu'), 'Thịt cừu cuộn (đĩa 150g)',      'thit-cuu-cuon', 'Thịt cừu thái lát cuộn', 109000, TRUE, FALSE),
('SP-BEEF-TENDER',(SELECT id FROM categories WHERE slug='thit-bo-cuu'),'Bò phi lê thăn nội (đĩa 150g)','bo-phi-le',     'Bò phi lê thăn nội', 129000, TRUE, FALSE),
('SP-BEEF-BRISKET',(SELECT id FROM categories WHERE slug='thit-bo-cuu'),'Bò gầu (đĩa 150g)',           'bo-gau',        'Bò gầu thái lát', 99000, TRUE, FALSE),
('SP-BEEF-TONGUE',(SELECT id FROM categories WHERE slug='thit-bo-cuu'),'Lưỡi bò (đĩa 150g)',           'luoi-bo',       'Lưỡi bò thái lát', 89000, TRUE, FALSE),
-- Thịt heo & Gà (5)
('SP-PORK-BELLY',(SELECT id FROM categories WHERE slug='thit-heo-ga'), 'Ba chỉ heo (đĩa 150g)',        'ba-chi-heo',    'Ba chỉ heo thái lát', 69000, TRUE, FALSE),
('SP-CHICKEN',   (SELECT id FROM categories WHERE slug='thit-heo-ga'), 'Thịt gà (đĩa 200g)',           'thit-ga',       'Thịt gà tươi thái miếng', 59000, TRUE, FALSE),
('SP-PORK-INT',  (SELECT id FROM categories WHERE slug='thit-heo-ga'), 'Lòng heo (đĩa 150g)',          'long-heo',      'Lòng heo làm sạch', 69000, TRUE, FALSE),
('SP-PORK-TROT', (SELECT id FROM categories WHERE slug='thit-heo-ga'), 'Chân giò heo (đĩa 200g)',      'chan-gio-heo',  'Chân giò heo hầm', 79000, TRUE, FALSE),
('SP-CHICKEN-FEET',(SELECT id FROM categories WHERE slug='thit-heo-ga'),'Chân gà rút xương (đĩa 150g)','chan-ga',       'Chân gà rút xương', 49000, TRUE, FALSE),
-- Hải sản (6)
('SP-SHRIMP',    (SELECT id FROM categories WHERE slug='hai-san'), 'Tôm sú (đĩa 200g)',           'tom-su',        'Tôm sú tươi sống', 119000, TRUE, FALSE),
('SP-SQUID',     (SELECT id FROM categories WHERE slug='hai-san'), 'Mực ống (đĩa 150g)',          'muc-ong',       'Mực ống tươi', 99000, TRUE, FALSE),
('SP-SALMON',    (SELECT id FROM categories WHERE slug='hai-san'), 'Cá hồi (đĩa 150g)',           'ca-hoi',        'Cá hồi Na Uy', 139000, TRUE, FALSE),
('SP-OCTOPUS',   (SELECT id FROM categories WHERE slug='hai-san'), 'Bạch tuộc (đĩa 150g)',        'bach-tuoc',     'Bạch tuộc tươi', 109000, TRUE, FALSE),
('SP-SCALLOP',   (SELECT id FROM categories WHERE slug='hai-san'), 'Sò điệp (đĩa 150g)',          'so-diep',       'Sò điệp tươi', 129000, TRUE, FALSE),
('SP-LOBSTER',   (SELECT id FROM categories WHERE slug='hai-san'), 'Tôm hùm (1 con ~500g)',       'tom-hum',       'Tôm hùm sống', 490000, TRUE, FALSE),
-- Viên & Chả (5)
('SP-FISHBALL',  (SELECT id FROM categories WHERE slug='vien-cha'), 'Viên cá (đĩa 150g)',          'vien-ca',       'Viên cá dai giòn', 45000, TRUE, FALSE),
('SP-BEEFBALL',  (SELECT id FROM categories WHERE slug='vien-cha'), 'Viên bò (đĩa 150g)',          'vien-bo',       'Viên bò đậm vị', 49000, TRUE, FALSE),
('SP-SHRIMPBALL',(SELECT id FROM categories WHERE slug='vien-cha'), 'Viên tôm (đĩa 150g)',         'vien-tom',      'Viên tôm tươi', 59000, TRUE, FALSE),
('SP-FISHPASTE', (SELECT id FROM categories WHERE slug='vien-cha'), 'Chả cá thác lác (đĩa 150g)',   'cha-ca',        'Chả cá thác lác', 55000, TRUE, FALSE),
('SP-SIUMAI',    (SELECT id FROM categories WHERE slug='vien-cha'), 'Xíu mại (đĩa 6 viên)',        'xiu-mai',       'Xíu mại hấp', 39000, TRUE, FALSE),
-- Đậu phụ & Đồ khô (4)
('SP-TOFU-SOFT', (SELECT id FROM categories WHERE slug='dau-phu-do-kho'), 'Đậu phụ non (đĩa 200g)',   'dau-phu-non',   'Đậu phụ non mềm', 25000, TRUE, FALSE),
('SP-TOFU-FRIED',(SELECT id FROM categories WHERE slug='dau-phu-do-kho'), 'Đậu phụ chiên (đĩa 200g)', 'dau-phu-chien', 'Đậu phụ chiên vàng', 29000, TRUE, FALSE),
('SP-TOFU-SKIN', (SELECT id FROM categories WHERE slug='dau-phu-do-kho'), 'Đậu phụ da (đĩa 150g)',    'dau-phu-da',    'Đậu phụ da dai', 35000, TRUE, FALSE),
('SP-TOFU-PUFF', (SELECT id FROM categories WHERE slug='dau-phu-do-kho'), 'Đậu phụ phồng (đĩa 150g)', 'dau-phu-phong', 'Đậu phụ phồng giòn', 29000, TRUE, FALSE),
-- Rau & Nấm (6)
('SP-CABBAGE',   (SELECT id FROM categories WHERE slug='rau-nam'), 'Cải thảo (đĩa 200g)',          'cai-thao',      'Cải thảo tươi', 25000, TRUE, FALSE),
('SP-ENOKI',     (SELECT id FROM categories WHERE slug='rau-nam'), 'Nấm kim châm (đĩa 150g)',      'nam-kim-cham',  'Nấm kim châm tươi', 29000, TRUE, FALSE),
('SP-SHITAKE',   (SELECT id FROM categories WHERE slug='rau-nam'), 'Nấm đông cô (đĩa 150g)',       'nam-dong-co',   'Nấm đông cô tươi', 35000, TRUE, FALSE),
('SP-SPINACH',   (SELECT id FROM categories WHERE slug='rau-nam'), 'Rau muống (đĩa 200g)',         'rau-muong',     'Rau muống tươi', 19000, TRUE, FALSE),
('SP-BOKCHOY',   (SELECT id FROM categories WHERE slug='rau-nam'), 'Cải xanh (đĩa 200g)',          'cai-xanh',      'Cải xanh tươi', 19000, TRUE, FALSE),
('SP-CORN',      (SELECT id FROM categories WHERE slug='rau-nam'), 'Bắp Mỹ (đĩa 200g)',            'bap-my',        'Bắp Mỹ ngọt', 25000, TRUE, FALSE),
-- Mì & Bún (5)
('SP-NOODLE',    (SELECT id FROM categories WHERE slug='mi-bun'), 'Mì tươi (phần 150g)',             'mi-tuoi',       'Mì tươi', 19000, TRUE, FALSE),
('SP-UDON',      (SELECT id FROM categories WHERE slug='mi-bun'), 'Mì udon (phần 150g)',             'mi-udon',       'Mì udon Nhật', 25000, TRUE, FALSE),
('SP-VERMICELLI',(SELECT id FROM categories WHERE slug='mi-bun'), 'Bún tươi (phần 150g)',            'bun-tuoi',      'Bún tươi', 19000, TRUE, FALSE),
('SP-RICENOODLE',(SELECT id FROM categories WHERE slug='mi-bun'), 'Miến (phần 100g)',                'mien',          'Miến dong', 19000, TRUE, FALSE),
('SP-EGGNOODLE', (SELECT id FROM categories WHERE slug='mi-bun'), 'Mì trứng (phần 150g)',            'mi-trung',      'Mì trứng', 22000, TRUE, FALSE),
-- Khai vị Trung (4)
('SP-HARGOW',    (SELECT id FROM categories WHERE slug='khai-vi-trung'), 'Há cảo tôm (6 cái)',      'ha-cao',        'Há cảo tôm hấp', 45000, TRUE, FALSE),
('SP-GYOZA',     (SELECT id FROM categories WHERE slug='khai-vi-trung'), 'Sủi cảo chiên (6 cái)',   'sui-cao',       'Sủi cảo chiên giòn', 45000, TRUE, FALSE),
('SP-BAOBUN',    (SELECT id FROM categories WHERE slug='khai-vi-trung'), 'Bánh bao kim sa (3 cái)', 'banh-bao-kim-sa','Bánh bao kim sa', 39000, TRUE, FALSE),
('SP-DIMSUM',    (SELECT id FROM categories WHERE slug='khai-vi-trung'), 'Dimsum thập cẩm (6 cái)', 'dimsum',        'Dimsum thập cẩm', 55000, TRUE, FALSE),
-- Đồ uống (5)
('SP-OLONG',     (SELECT id FROM categories WHERE slug='do-uong'), 'Trà ô long (ly)',                 'tra-o-long',    'Trà ô long nóng/lạnh', 19000, TRUE, FALSE),
('SP-MILKTEA',   (SELECT id FROM categories WHERE slug='do-uong'), 'Trà sữa trân châu (ly)',          'tra-sua',       'Trà sữa trân châu', 39000, TRUE, FALSE),
('SP-BEER',      (SELECT id FROM categories WHERE slug='do-uong'), 'Bia Tiger (lon)',                 'bia-tiger',     'Bia Tiger lon', 25000, TRUE, FALSE),
('SP-WATER',     (SELECT id FROM categories WHERE slug='do-uong'), 'Nước suối (chai)',                'nuoc-suoi',     'Nước suối tinh khiết', 10000, TRUE, FALSE),
('SP-PEACH-TEA', (SELECT id FROM categories WHERE slug='do-uong'), 'Trà đào cam sả (ly)',             'tra-dao',       'Trà đào cam sả', 35000, TRUE, FALSE),
-- Tráng miệng (4)
('SP-MANGO-PUD', (SELECT id FROM categories WHERE slug='trang-mieng'), 'Pudding xoài (phần)',         'pudding-xoai',  'Pudding xoài tươi', 29000, TRUE, FALSE),
('SP-TANGYUAN',  (SELECT id FROM categories WHERE slug='trang-mieng'), 'Bánh trôi nước (chén)',        'banh-troi',     'Bánh trôi nước', 25000, TRUE, FALSE),
('SP-ICE-CREAM', (SELECT id FROM categories WHERE slug='trang-mieng'), 'Kem matcha (phần)',            'kem-matcha',    'Kem matcha Nhật', 29000, TRUE, FALSE),
('SP-CHE',       (SELECT id FROM categories WHERE slug='trang-mieng'), 'Chè Thái (chén)',              'che-thai',      'Chè Thái', 25000, TRUE, FALSE);

-- =====================================================================
-- 7. FOOD_IMAGES (55 bản ghi - 1 ảnh cover mỗi món)
-- =====================================================================
INSERT INTO food_images (foods_id, url, is_cover, sort_order)
SELECT id, CONCAT('https://cdn.annhien.vn/foods/', slug, '.jpg'), TRUE, 1 FROM foods;

-- =====================================================================
-- 8. RECIPES (công thức) — mỗi món ăn cần các nguyên liệu tương ứng
--    Tổng nhu cầu mỗi nguyên liệu KHÔNG vượt stock_quantity ở mục 5.
-- =====================================================================
INSERT INTO recipes (foods_id, ingredient_id, quantity_required)
SELECT f.id, i.id, q.qty FROM foods f JOIN ingredients i JOIN (
  -- Nước lẩu
  SELECT 'SP-MALA' AS sku, 'Cốt lẩu mala' AS ing, 1 AS qty UNION ALL
  SELECT 'SP-TOMATO', 'Cốt lẩu cà chua', 1 UNION ALL
  SELECT 'SP-MUSHROOM', 'Cốt lẩu nấm', 1 UNION ALL
  SELECT 'SP-THAI', 'Cốt lẩu Thái', 1 UNION ALL
  SELECT 'SP-COLLAGEN', 'Cốt lẩu collagen', 1 UNION ALL
  SELECT 'SP-GREENCHILI', 'Cốt lẩu ớt xanh', 1 UNION ALL
  SELECT 'SP-MILK', 'Cốt lẩu sữa', 1 UNION ALL
  -- Thịt bò & cừu
  SELECT 'SP-BEEF-ROLL', 'Ba chỉ bò Mỹ', 0.15 UNION ALL
  SELECT 'SP-BEEF-AUS', 'Bò Úc cuộn', 0.15 UNION ALL
  SELECT 'SP-LAMB', 'Thịt cừu cuộn', 0.15 UNION ALL
  SELECT 'SP-BEEF-TENDER', 'Ba chỉ bò Mỹ', 0.15 UNION ALL
  SELECT 'SP-BEEF-BRISKET', 'Ba chỉ bò Mỹ', 0.15 UNION ALL
  SELECT 'SP-BEEF-TONGUE', 'Ba chỉ bò Mỹ', 0.15 UNION ALL
  -- Thịt heo & gà
  SELECT 'SP-PORK-BELLY', 'Ba chỉ heo', 0.15 UNION ALL
  SELECT 'SP-CHICKEN', 'Thịt gà', 0.2 UNION ALL
  SELECT 'SP-PORK-INT', 'Lòng heo', 0.15 UNION ALL
  SELECT 'SP-PORK-TROT', 'Ba chỉ heo', 0.2 UNION ALL
  SELECT 'SP-CHICKEN-FEET', 'Thịt gà', 0.15 UNION ALL
  -- Hải sản
  SELECT 'SP-SHRIMP', 'Tôm sú', 0.2 UNION ALL
  SELECT 'SP-SQUID', 'Mực ống', 0.15 UNION ALL
  SELECT 'SP-SALMON', 'Cá hồi', 0.15 UNION ALL
  SELECT 'SP-OCTOPUS', 'Bạch tuộc', 0.15 UNION ALL
  SELECT 'SP-SCALLOP', 'Sò điệp', 0.15 UNION ALL
  SELECT 'SP-LOBSTER', 'Tôm hùm', 1 UNION ALL
  -- Viên & chả
  SELECT 'SP-FISHBALL', 'Viên cá', 0.15 UNION ALL
  SELECT 'SP-BEEFBALL', 'Viên bò', 0.15 UNION ALL
  SELECT 'SP-SHRIMPBALL', 'Viên tôm', 0.15 UNION ALL
  SELECT 'SP-FISHPASTE', 'Chả cá', 0.15 UNION ALL
  SELECT 'SP-SIUMAI', 'Xíu mại', 0.15 UNION ALL
  -- Đậu phụ
  SELECT 'SP-TOFU-SOFT', 'Đậu phụ non', 0.2 UNION ALL
  SELECT 'SP-TOFU-FRIED', 'Đậu phụ chiên', 0.2 UNION ALL
  SELECT 'SP-TOFU-SKIN', 'Đậu phụ da', 0.15 UNION ALL
  SELECT 'SP-TOFU-PUFF', 'Đậu phụ chiên', 0.15 UNION ALL
  -- Rau & nấm
  SELECT 'SP-CABBAGE', 'Cải thảo', 0.2 UNION ALL
  SELECT 'SP-ENOKI', 'Nấm kim châm', 0.15 UNION ALL
  SELECT 'SP-SHITAKE', 'Nấm đông cô', 0.15 UNION ALL
  SELECT 'SP-SPINACH', 'Rau muống', 0.2 UNION ALL
  SELECT 'SP-BOKCHOY', 'Cải xanh', 0.2 UNION ALL
  SELECT 'SP-CORN', 'Bắp Mỹ', 0.2 UNION ALL
  -- Mì & bún
  SELECT 'SP-NOODLE', 'Mì tươi', 0.15 UNION ALL
  SELECT 'SP-UDON', 'Mì udon', 0.15 UNION ALL
  SELECT 'SP-VERMICELLI', 'Bún tươi', 0.15 UNION ALL
  SELECT 'SP-RICENOODLE', 'Miến', 0.1 UNION ALL
  SELECT 'SP-EGGNOODLE', 'Mì trứng', 0.15 UNION ALL
  -- Khai vị Trung
  SELECT 'SP-HARGOW', 'Tôm sú', 0.05 UNION ALL
  SELECT 'SP-GYOZA', 'Ba chỉ heo', 0.05 UNION ALL
  SELECT 'SP-BAOBUN', 'Ba chỉ heo', 0.05 UNION ALL
  SELECT 'SP-DIMSUM', 'Tôm sú', 0.05 UNION ALL
  -- Đồ uống
  SELECT 'SP-OLONG', 'Trà ô long', 1 UNION ALL
  SELECT 'SP-MILKTEA', 'Trà sữa', 1 UNION ALL
  SELECT 'SP-BEER', 'Bia Tiger', 1 UNION ALL
  SELECT 'SP-WATER', 'Nước suối', 1 UNION ALL
  SELECT 'SP-PEACH-TEA', 'Trà ô long', 1 UNION ALL
  -- Tráng miệng
  SELECT 'SP-MANGO-PUD', 'Pudding xoài', 1 UNION ALL
  SELECT 'SP-TANGYUAN', 'Pudding xoài', 1 UNION ALL
  SELECT 'SP-ICE-CREAM', 'Pudding xoài', 1 UNION ALL
  SELECT 'SP-CHE', 'Pudding xoài', 1
) AS q ON q.sku = f.sku AND q.ing = i.name;

-- =====================================================================
-- 9. VOUCHERS (36 bản ghi)
-- =====================================================================
INSERT INTO vouchers (code, name, description, discount_type, discount_value, min_order_amount, max_discount_amount, start_at, end_at, usage_limit, used_count, is_active) VALUES
('WELCOME10',  'Chào mừng khách mới',       'Giảm 10% cho đơn đầu tiên',        'PERCENT', 10, 200000, 50000, NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 365 DAY, 500, 0, TRUE),
('LAU100K',    'Giảm 100K đơn lẩu',         'Giảm 100K cho đơn từ 800K',        'FIXED',   100000, 800000, 100000, NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 180 DAY, 200, 0, TRUE),
('MALA15',     'Ưu đãi lẩu mala',           'Giảm 15% cho đơn lẩu mala',        'PERCENT', 15, 500000, 100000, NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 90 DAY, 300, 0, TRUE),
('CUOITUAN20', 'Cuối tuần vui vẻ',           'Giảm 20% đơn từ 1 triệu',          'PERCENT', 20, 1000000, 200000, NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 60 DAY, 100, 0, TRUE),
('SINHNHAT50', 'Sinh nhật giảm 50%',         'Giảm 50% cho khách sinh nhật',     'PERCENT', 50, 300000, 150000, NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 30 DAY, 50, 0, TRUE),
('VIP200K',    'Ưu đãi khách VIP',           'Giảm 200K cho khách VIP',          'FIXED',   200000, 1500000, 200000, NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 365 DAY, 50, 0, TRUE),
('LAUNHAM',    'Ưu đãi lẩu nấm',             'Giảm 10% cho đơn lẩu nấm',         'PERCENT', 10, 300000, 50000, NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 120 DAY, 200, 0, TRUE),
('HAISAN12',   'Ưu đãi hải sản',             'Giảm 12% cho đơn hải sản',         'PERCENT', 12, 500000, 80000, NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 120 DAY, 150, 0, TRUE),
('BIAFREE',    'Tặng bia',                   'Giảm giá tương đương 1 lon bia',   'FIXED',   25000, 300000, 25000, NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 90 DAY, 300, 0, TRUE),
('TRUAVANG',   'Trưa vắng khách',            'Giảm 10% khung 11h-14h',           'PERCENT', 10, 200000, 50000, NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 365 DAY, 1000, 0, TRUE),
('COMBO4',     'Combo 4 người',              'Giảm 80K cho bàn 4 người',         'FIXED',   80000, 1000000, 80000, NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 180 DAY, 300, 0, TRUE),
('COMBO6',     'Combo 6 người',              'Giảm 150K cho bàn 6 người',        'FIXED',   150000, 1800000, 150000, NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 180 DAY, 200, 0, TRUE),
('COMBO8',     'Combo 8 người',              'Giảm 200K cho bàn 8 người',        'FIXED',   200000, 2500000, 200000, NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 180 DAY, 100, 0, TRUE),
('TAKEAWAY10', 'Mang về giảm 10%',           'Giảm 10% cho đơn mang về',         'PERCENT', 10, 200000, 30000, NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 365 DAY, 500, 0, TRUE),
('DELIVERY15', 'Giao hàng giảm 15K',         'Giảm 15K phí giao hàng',           'FIXED',   15000, 300000, 15000, NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 180 DAY, 500, 0, TRUE),
('APP20',      'Ưu đãi đặt qua app',         'Giảm 20K khi đặt qua app',         'FIXED',   20000, 250000, 20000, NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 365 DAY, 1000, 0, TRUE),
('FREESHIP',   'Miễn phí giao hàng',         'Miễn phí ship đơn từ 500K',        'FIXED',   30000, 500000, 30000, NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 180 DAY, 300, 0, TRUE),
('TET2027',    'Ưu đãi Tết',                 'Giảm 25% dịp Tết',                 'PERCENT', 25, 500000, 200000, NOW() + INTERVAL 60 DAY, NOW() + INTERVAL 90 DAY, 500, 0, TRUE),
('TRUNGTHU',   'Ưu đãi Trung Thu',           'Giảm 20% dịp Trung Thu',           'PERCENT', 20, 400000, 150000, NOW() + INTERVAL 30 DAY, NOW() + INTERVAL 60 DAY, 300, 0, TRUE),
('NOEL2026',   'Ưu đãi Giáng Sinh',          'Giảm 20% dịp Noel',                'PERCENT', 20, 400000, 150000, NOW() + INTERVAL 30 DAY, NOW() + INTERVAL 55 DAY, 300, 0, TRUE),
('NEWYEAR',    'Ưu đãi Tết Dương Lịch',      'Giảm 20% dịp năm mới',             'PERCENT', 20, 400000, 150000, NOW() + INTERVAL 60 DAY, NOW() + INTERVAL 70 DAY, 300, 0, TRUE),
('KHAIVI10',   'Ưu đãi khai vị',             'Giảm 10% món khai vị',             'PERCENT', 10, 150000, 30000, NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 120 DAY, 300, 0, TRUE),
('TRANGMIENG', 'Tặng tráng miệng',           'Giảm 25K khi gọi tráng miệng',     'FIXED',   25000, 200000, 25000, NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 120 DAY, 500, 0, TRUE),
('DOUONG20',   'Ưu đãi đồ uống',             'Giảm 20% đồ uống',                 'PERCENT', 20, 100000, 30000, NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 120 DAY, 500, 0, TRUE),
('DIMSUM15',   'Ưu đãi dimsum',              'Giảm 15% dimsum',                  'PERCENT', 15, 200000, 50000, NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 120 DAY, 300, 0, TRUE),
('REVIEW50',   'Khách đánh giá 5 sao',       'Giảm 50K cho khách review 5 sao',  'FIXED',   50000, 300000, 50000, NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 365 DAY, 1000, 0, TRUE),
('BIRTHDAY',   'Sinh nhật khách hàng',       'Giảm 30% trong tháng sinh nhật',   'PERCENT', 30, 300000, 200000, NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 365 DAY, 500, 0, TRUE),
('LOYAL100',   'Khách thân thiết',           'Giảm 100K cho khách thân thiết',   'FIXED',   100000, 500000, 100000, NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 365 DAY, 300, 0, TRUE),
('FIRSTBUY',   'Lần đầu mua',                'Giảm 10% lần đầu',                 'PERCENT', 10, 150000, 30000, NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 365 DAY, 1000, 0, TRUE),
('GROUP10',    'Đoàn đông giảm 10%',         'Giảm 10% cho đoàn 10 người',       'PERCENT', 10, 2000000, 200000, NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 180 DAY, 200, 0, TRUE),
('GROUP15',    'Đoàn đông giảm 15%',         'Giảm 15% cho đoàn 15 người',       'PERCENT', 15, 3000000, 300000, NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 180 DAY, 100, 0, TRUE),
('WEEKDAY10',  'Ngày thường giảm 10%',       'Giảm 10% từ T2-T5',                'PERCENT', 10, 200000, 50000, NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 365 DAY, 1000, 0, TRUE),
('WEEKEND5',   'Cuối tuần giảm 5%',          'Giảm 5% T7-CN',                    'PERCENT', 5, 200000, 30000, NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 365 DAY, 1000, 0, TRUE),
('STAFFTREAT', 'Nhân viên chiêu đãi',        'Giảm 30% cho nhân viên',           'PERCENT', 30, 100000, 100000, NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 365 DAY, 1000, 0, TRUE),
('NEWSTORE',   'Khai trương chi nhánh mới',  'Giảm 25% khai trương',             'PERCENT', 25, 300000, 150000, NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 30 DAY, 500, 0, TRUE),
('FLASHSALE',  'Flash sale 30 phút',         'Giảm 20% flash sale',              'PERCENT', 20, 200000, 100000, NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 30 DAY, 100, 0, TRUE);

-- =====================================================================
-- 10. TABLE_SESSIONS (36 bản ghi - session đã đóng cho 36 bàn đầu)
--     Dùng CLOSED + access_token_hash random để không đụng UNIQUE(active_table_id)
--     và UNIQUE(access_token_hash).
-- =====================================================================
INSERT INTO table_sessions (table_id, opened_by, status, access_token_hash, opened_at, closed_at)
SELECT
    t.id,
    3,                                  -- staff đầu tiên
    'CLOSED',
    SHA2(CONCAT('session-', t.id, '-', UUID()), 256),
    NOW(3) - INTERVAL (t.id) HOUR,
    NOW(3) - INTERVAL (t.id) HOUR + INTERVAL 2 HOUR
FROM restaurant_tables t
WHERE t.id <= 36;

COMMIT;

-- Thống kê dữ liệu mẫu đã đổ vào
-- role: 4
-- users: 45
-- restaurant_tables: 40
-- categories: 36
-- ingredients: 45
-- foods: 57
-- food_images: 57
-- recipes: 57
-- vouchers: 36
-- table_sessions: 36