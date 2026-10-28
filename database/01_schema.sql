-- LTUDM: cấu trúc tương thích ngày 28/10/2026.
-- Không xóa database/bảng/dữ liệu. IF NOT EXISTS không nâng cấp bảng đã có.
-- Nếu đang có schema khác bản gốc: sao lưu và đối chiếu trước, không coi đây là migration.
SET NAMES utf8mb4;
CREATE DATABASE IF NOT EXISTS annhien_datmon
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE annhien_datmon;

CREATE TABLE IF NOT EXISTS roles (
    roleID          BIGINT AUTO_INCREMENT PRIMARY KEY,
    nameRole        VARCHAR(255) NOT NULL UNIQUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS categories (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(100) NOT NULL UNIQUE,
    slug            VARCHAR(120) NOT NULL UNIQUE,
    description     VARCHAR(500),
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS restaurant_tables (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    table_name   VARCHAR(200) NOT NULL,
    capacity     INT          NOT NULL,
    status       BOOLEAN      NOT NULL DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci; 

CREATE TABLE IF NOT EXISTS vouchers (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    code                VARCHAR(50) NOT NULL UNIQUE,
    name                VARCHAR(255),
    description         VARCHAR(500),
    discount_type       ENUM('PERCENT', 'FIXED') NOT NULL, -- Tối ưu bằng ENUM
    discount_value      DECIMAL(15,2) NOT NULL,
    min_order_amount    DECIMAL(15,2) DEFAULT 0,
    max_discount_amount DECIMAL(15,2),
    start_at            DATETIME(3) NOT NULL,
    end_at              DATETIME(3) NOT NULL,
    usage_limit         INT,
    used_count          INT DEFAULT 0,
    is_active           BOOLEAN DEFAULT TRUE,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ingredients (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    unit            VARCHAR(50) NOT NULL, 
    stock_quantity  DECIMAL(10, 2) NOT NULL DEFAULT 0, 
    min_stock_level DECIMAL(10, 2) NOT NULL DEFAULT 0, 
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS users (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    roleID          BIGINT NOT NULL, 
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(150) NOT NULL,
    phone           VARCHAR(20),
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_users_roles FOREIGN KEY (roleID) REFERENCES roles(roleID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS foods (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    sku             VARCHAR(50)  NOT NULL UNIQUE,
    category_id     BIGINT       NULL,
    name            VARCHAR(200) NOT NULL,
    slug            VARCHAR(220) NOT NULL UNIQUE,
    description     TEXT,
    price           DECIMAL(14, 2) NOT NULL,
    is_published    BOOLEAN      NOT NULL DEFAULT TRUE, 
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    INDEX idx_foods_published (is_published, is_deleted),
    INDEX idx_foods_price (price),
    CONSTRAINT fk_food_category FOREIGN KEY (category_id) REFERENCES categories(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS recipes (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    foods_id            BIGINT NOT NULL,
    ingredient_id       BIGINT NOT NULL,
    quantity_required   DECIMAL(10, 2) NOT NULL, 
    CONSTRAINT fk_recipes_foods FOREIGN KEY (foods_id) REFERENCES foods(id) ON DELETE CASCADE,
    CONSTRAINT fk_recipes_ingredients FOREIGN KEY (ingredient_id) REFERENCES ingredients(id) ON DELETE CASCADE,
    CONSTRAINT uq_food_ingredient UNIQUE (foods_id, ingredient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS food_images (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    foods_id        BIGINT       NOT NULL,
    url             VARCHAR(500) NOT NULL,
    is_cover        BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order      INT          NOT NULL DEFAULT 0,
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_foods_images_foods FOREIGN KEY (foods_id) REFERENCES foods(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS reviews (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    foods_id        BIGINT NOT NULL,
    rating          INT NOT NULL CHECK (rating >= 1 AND rating <= 5),
    comment         TEXT,
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_reviews_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_reviews_food FOREIGN KEY (foods_id) REFERENCES foods(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cart_items (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    foods_id        BIGINT       NOT NULL,
    quantity        INT          NOT NULL DEFAULT 1,
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_cart_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_foods FOREIGN KEY (foods_id) REFERENCES foods(id),
    CONSTRAINT uq_cart_user_foods UNIQUE (user_id, foods_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS orders (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_code       VARCHAR(30)  NOT NULL UNIQUE,
    parent_order_id  BIGINT       NULL,      
    user_id          BIGINT       NOT NULL,  
    table_id         BIGINT       NULL,      
    voucher_id       BIGINT       NULL,      
    order_type       ENUM('DINE_IN', 'TAKEAWAY', 'DELIVERY') NOT NULL DEFAULT 'DINE_IN', -- Tối ưu
    status           ENUM('PENDING', 'RECEIVED', 'COOKING', 'COMPLETED', 'SERVED', 'CANCELLED') NOT NULL DEFAULT 'PENDING', -- Tối ưu theo yêu cầu đề
    subtotal         DECIMAL(14, 2) NOT NULL,
    shipping_fee     DECIMAL(14, 2) NOT NULL DEFAULT 0,
    discount_amount  DECIMAL(14, 2) NOT NULL DEFAULT 0,
    total_amount     DECIMAL(14, 2) NOT NULL,
    receiver_name    VARCHAR(150) NULL,      
    receiver_phone   VARCHAR(20)  NULL,      
    shipping_address VARCHAR(500) NULL,      
    note             VARCHAR(500),
    cancel_reason    VARCHAR(255) NULL,      
    cancelled_at     DATETIME(3)  NULL,
    confirmed_by     BIGINT       NULL,      
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    INDEX idx_orders_table_active (table_id, status, parent_order_id),
    INDEX idx_orders_status (status),
    CONSTRAINT fk_orders_parent FOREIGN KEY (parent_order_id) REFERENCES orders(id),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_orders_admin FOREIGN KEY (confirmed_by) REFERENCES users(id),
    CONSTRAINT fk_orders_table FOREIGN KEY (table_id) REFERENCES restaurant_tables(id),
    CONSTRAINT fk_orders_voucher FOREIGN KEY (voucher_id) REFERENCES vouchers(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS order_items (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id      BIGINT       NOT NULL,
    foods_id      BIGINT       NOT NULL,
    foods_name    VARCHAR(200) NOT NULL,
    unit_price    DECIMAL(14, 2) NOT NULL,
    quantity      INT          NOT NULL,
    line_total    DECIMAL(14, 2) NOT NULL,
    status        ENUM('PENDING', 'RECEIVED', 'COOKING', 'COMPLETED', 'SERVED', 'CANCELLED') NOT NULL DEFAULT 'PENDING', -- Tối ưu để đồng bộ trạng thái từng món
    CONSTRAINT fk_oi_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_oi_foods FOREIGN KEY (foods_id) REFERENCES foods(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS bill (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id       BIGINT NOT NULL UNIQUE,
    total_amount   DECIMAL(14, 2) NOT NULL,
    discount       DECIMAL(14, 2) NOT NULL DEFAULT 0,
    tax            DECIMAL(14, 2) NOT NULL DEFAULT 0,
    payment_method ENUM('CASH', 'CREDIT_CARD', 'E_WALLET', 'BANK_TRANSFER') NOT NULL DEFAULT 'CASH', -- Tối ưu
    paid_at        DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_bill_order FOREIGN KEY (order_id) REFERENCES orders(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


