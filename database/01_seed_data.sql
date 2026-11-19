-- phpMyAdmin SQL Dump
-- version 5.2.1
-- https://www.phpmyadmin.net/
--
-- Host: 127.0.0.1
-- Generation Time: Nov 08, 2026 at 09:03 PM
-- Server version: 10.4.32-MariaDB
-- PHP Version: 8.2.12
USE annhien_datmon;

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Database: `annhien_datmon`
--

-- --------------------------------------------------------

--
-- Table structure for table `bill`
--

CREATE TABLE `bill` (
  `id` bigint(20) NOT NULL,
  `table_session_id` bigint(20) NOT NULL,
  `total_amount` decimal(14,2) NOT NULL,
  `discount` decimal(14,2) NOT NULL DEFAULT 0.00,
  `tax` decimal(14,2) NOT NULL DEFAULT 0.00,
  `payment_method` enum('CASH','CREDIT_CARD','E_WALLET','BANK_TRANSFER') DEFAULT NULL,
  `paid_at` datetime(3) DEFAULT NULL,
  `payment_status` enum('UNPAID','PAID','CANCELLED') NOT NULL DEFAULT 'UNPAID',
  `created_at` datetime(3) NOT NULL DEFAULT current_timestamp(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `bill`
--

INSERT INTO `bill` (`id`, `table_session_id`, `total_amount`, `discount`, `tax`, `payment_method`, `paid_at`, `payment_status`, `created_at`) VALUES
(1, 65, 460080.00, 0.00, 34080.00, 'CASH', '2026-11-03 01:58:03.936', 'PAID', '2026-11-03 01:58:03.936'),
(2, 64, 500040.00, 0.00, 37040.00, 'CASH', '2026-11-09 02:31:43.070', 'PAID', '2026-11-09 02:31:43.070');

-- --------------------------------------------------------

--
-- Table structure for table `categories`
--

CREATE TABLE `categories` (
  `id` bigint(20) NOT NULL,
  `name` varchar(100) NOT NULL,
  `slug` varchar(120) NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT current_timestamp(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `categories`
--

INSERT INTO `categories` (`id`, `name`, `slug`, `description`, `created_at`) VALUES
(1, 'Nước lẩu', 'nuoc-lau', 'Các loại nước lẩu: mala, cà chua, nấm, Thái, collagen, ớt xanh', '2026-11-01 13:28:33.441'),
(2, 'Thịt bò & Cừu', 'thit-bo-cuu', 'Ba chỉ bò, bò Mỹ, bò Úc, thịt cừu cuộn', '2026-11-01 13:28:33.441'),
(3, 'Thịt heo & Gà', 'thit-heo-ga', 'Ba chỉ heo, thịt gà, lòng heo', '2026-11-01 13:28:33.441'),
(4, 'Hải sản', 'hai-san', 'Tôm, mực, cá, sò, bạch tuộc, tôm hùm', '2026-11-01 13:28:33.441'),
(5, 'Viên & Chả', 'vien-cha', 'Viên cá, viên bò, viên tôm, chả cá, xíu mại', '2026-11-01 13:28:33.441'),
(6, 'Đậu phụ & Đồ khô', 'dau-phu-do-kho', 'Đậu phụ non, đậu phụ chiên, đậu phụ da', '2026-11-01 13:28:33.441'),
(7, 'Rau & Nấm', 'rau-nam', 'Cải thảo, nấm kim châm, nấm đông cô, rau muống, cải xanh', '2026-11-01 13:28:33.441'),
(8, 'Mì & Bún', 'mi-bun', 'Mì tươi, mì udon, bún, miến, mì trứng', '2026-11-01 13:28:33.441'),
(9, 'Món khai vị Trung', 'khai-vi-trung', 'Há cảo, xíu mại, dimsum, bánh bao, gyoza', '2026-11-01 13:28:33.441'),
(10, 'Đồ uống', 'do-uong', 'Trà sữa, trà hoa quả, nước ngọt, bia, rượu', '2026-11-01 13:28:33.441'),
(11, 'Tráng miệng', 'trang-mieng', 'Pudding xoài, bánh trôi, kem, chè', '2026-11-01 13:28:33.441');

-- --------------------------------------------------------

--
-- Table structure for table `foods`
--

CREATE TABLE `foods` (
  `id` bigint(20) NOT NULL,
  `sku` varchar(50) NOT NULL,
  `category_id` bigint(20) DEFAULT NULL,
  `name` varchar(200) NOT NULL,
  `slug` varchar(220) NOT NULL,
  `description` text DEFAULT NULL,
  `price` decimal(14,2) NOT NULL,
  `is_published` tinyint(1) NOT NULL DEFAULT 1,
  `is_deleted` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` datetime(3) NOT NULL DEFAULT current_timestamp(3),
  `updated_at` datetime(3) NOT NULL DEFAULT current_timestamp(3) ON UPDATE current_timestamp(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `foods`
--

INSERT INTO `foods` (`id`, `sku`, `category_id`, `name`, `slug`, `description`, `price`, `is_published`, `is_deleted`, `created_at`, `updated_at`) VALUES
(1, 'SP-MALA', 1, 'Nước lẩu mala Tứ Xuyên (nồi 1 ngăn)', 'nuoc-lau-mala', 'Cốt lẩu mala cay tê đặc trưng Tứ Xuyên', 129000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(2, 'SP-TOMATO', 1, 'Nước lẩu cà chua (nồi 1 ngăn)', 'nuoc-lau-ca-chua', 'Cốt lẩu cà chua thanh ngọt', 99000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(3, 'SP-MUSHROOM', 1, 'Nước lẩu nấm (nồi 1 ngăn)', 'nuoc-lau-nam', 'Cốt lẩu nấm thập cẩm thanh đạm', 119000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(4, 'SP-THAI', 1, 'Nước lẩu Thái chua cay (nồi 1 ngăn)', 'nuoc-lau-thai', 'Cốt lẩu Thái chua cay', 109000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(5, 'SP-COLLAGEN', 1, 'Nước lẩu collagen (nồi 1 ngăn)', 'nuoc-lau-collagen', 'Cốt lẩu collagen bổ dưỡng', 139000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(6, 'SP-GREENCHILI', 1, 'Nước lẩu ớt xanh (nồi 1 ngăn)', 'nuoc-lau-ot-xanh', 'Cốt lẩu ớt xanh thanh cay', 119000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(7, 'SP-MILK', 1, 'Nước lẩu sữa Đài Loan (nồi 1 ngăn)', 'nuoc-lau-sua', 'Cốt lẩu sữa kiểu Đài Loan', 129000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(8, 'SP-BEEF-ROLL', 2, 'Ba chỉ bò Mỹ cuộn (đĩa 150g)', 'ba-chi-bo-my', 'Ba chỉ bò Mỹ thái lát cuộn', 89000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(9, 'SP-BEEF-AUS', 2, 'Bò Úc cuộn (đĩa 150g)', 'bo-uc-cuon', 'Bò Úc thái lát cuộn', 99000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(10, 'SP-LAMB', 2, 'Thịt cừu cuộn (đĩa 150g)', 'thit-cuu-cuon', 'Thịt cừu thái lát cuộn', 109000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(11, 'SP-BEEF-TENDER', 2, 'Bò phi lê thăn nội (đĩa 150g)', 'bo-phi-le', 'Bò phi lê thăn nội', 129000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(12, 'SP-BEEF-BRISKET', 2, 'Bò gầu (đĩa 150g)', 'bo-gau', 'Bò gầu thái lát', 99000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(13, 'SP-BEEF-TONGUE', 2, 'Lưỡi bò (đĩa 150g)', 'luoi-bo', 'Lưỡi bò thái lát', 89000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(14, 'SP-PORK-BELLY', 3, 'Ba chỉ heo (đĩa 150g)', 'ba-chi-heo', 'Ba chỉ heo thái lát', 69000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(15, 'SP-CHICKEN', 3, 'Thịt gà (đĩa 200g)', 'thit-ga', 'Thịt gà tươi thái miếng', 59000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(16, 'SP-PORK-INT', 3, 'Lòng heo (đĩa 150g)', 'long-heo', 'Lòng heo làm sạch', 69000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(17, 'SP-PORK-TROT', 3, 'Chân giò heo (đĩa 200g)', 'chan-gio-heo', 'Chân giò heo hầm', 79000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(18, 'SP-CHICKEN-FEET', 3, 'Chân gà rút xương (đĩa 150g)', 'chan-ga', 'Chân gà rút xương', 49000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(19, 'SP-SHRIMP', 4, 'Tôm sú (đĩa 200g)', 'tom-su', 'Tôm sú tươi sống', 119000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(20, 'SP-SQUID', 4, 'Mực ống (đĩa 150g)', 'muc-ong', 'Mực ống tươi', 99000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(21, 'SP-SALMON', 4, 'Cá hồi (đĩa 150g)', 'ca-hoi', 'Cá hồi Na Uy', 139000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(22, 'SP-OCTOPUS', 4, 'Bạch tuộc (đĩa 150g)', 'bach-tuoc', 'Bạch tuộc tươi', 109000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(23, 'SP-SCALLOP', 4, 'Sò điệp (đĩa 150g)', 'so-diep', 'Sò điệp tươi', 129000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(24, 'SP-LOBSTER', 4, 'Tôm hùm (1 con ~500g)', 'tom-hum', 'Tôm hùm sống', 490000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(25, 'SP-FISHBALL', 5, 'Viên cá (đĩa 150g)', 'vien-ca', 'Viên cá dai giòn', 45000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(26, 'SP-BEEFBALL', 5, 'Viên bò (đĩa 150g)', 'vien-bo', 'Viên bò đậm vị', 49000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(27, 'SP-SHRIMPBALL', 5, 'Viên tôm (đĩa 150g)', 'vien-tom', 'Viên tôm tươi', 59000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(28, 'SP-FISHPASTE', 5, 'Chả cá thác lác (đĩa 150g)', 'cha-ca', 'Chả cá thác lác', 55000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(29, 'SP-SIUMAI', 5, 'Xíu mại (đĩa 6 viên)', 'xiu-mai', 'Xíu mại hấp', 39000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(30, 'SP-TOFU-SOFT', 6, 'Đậu phụ non (đĩa 200g)', 'dau-phu-non', 'Đậu phụ non mềm', 25000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(31, 'SP-TOFU-FRIED', 6, 'Đậu phụ chiên (đĩa 200g)', 'dau-phu-chien', 'Đậu phụ chiên vàng', 29000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(32, 'SP-TOFU-SKIN', 6, 'Đậu phụ da (đĩa 150g)', 'dau-phu-da', 'Đậu phụ da dai', 35000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(33, 'SP-TOFU-PUFF', 6, 'Đậu phụ phồng (đĩa 150g)', 'dau-phu-phong', 'Đậu phụ phồng giòn', 29000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(34, 'SP-CABBAGE', 7, 'Cải thảo (đĩa 200g)', 'cai-thao', 'Cải thảo tươi', 25000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(35, 'SP-ENOKI', 7, 'Nấm kim châm (đĩa 150g)', 'nam-kim-cham', 'Nấm kim châm tươi', 29000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(36, 'SP-SHITAKE', 7, 'Nấm đông cô (đĩa 150g)', 'nam-dong-co', 'Nấm đông cô tươi', 35000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(37, 'SP-SPINACH', 7, 'Rau muống (đĩa 200g)', 'rau-muong', 'Rau muống tươi', 19000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(38, 'SP-BOKCHOY', 7, 'Cải xanh (đĩa 200g)', 'cai-xanh', 'Cải xanh tươi', 19000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(39, 'SP-CORN', 7, 'Bắp Mỹ (đĩa 200g)', 'bap-my', 'Bắp Mỹ ngọt', 25000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(40, 'SP-NOODLE', 8, 'Mì tươi (phần 150g)', 'mi-tuoi', 'Mì tươi', 19000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(41, 'SP-UDON', 8, 'Mì udon (phần 150g)', 'mi-udon', 'Mì udon Nhật', 25000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(42, 'SP-VERMICELLI', 8, 'Bún tươi (phần 150g)', 'bun-tuoi', 'Bún tươi', 19000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(43, 'SP-RICENOODLE', 8, 'Miến (phần 100g)', 'mien', 'Miến dong', 19000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(44, 'SP-EGGNOODLE', 8, 'Mì trứng (phần 150g)', 'mi-trung', 'Mì trứng', 22000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(45, 'SP-HARGOW', 9, 'Há cảo tôm (6 cái)', 'ha-cao', 'Há cảo tôm hấp', 45000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(46, 'SP-GYOZA', 9, 'Sủi cảo chiên (6 cái)', 'sui-cao', 'Sủi cảo chiên giòn', 45000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(47, 'SP-BAOBUN', 9, 'Bánh bao kim sa (3 cái)', 'banh-bao-kim-sa', 'Bánh bao kim sa', 39000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(48, 'SP-DIMSUM', 9, 'Dimsum thập cẩm (6 cái)', 'dimsum', 'Dimsum thập cẩm', 55000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(49, 'SP-OLONG', 10, 'Trà ô long (ly)', 'tra-o-long', 'Trà ô long nóng/lạnh', 19000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(50, 'SP-MILKTEA', 10, 'Trà sữa trân châu (ly)', 'tra-sua', 'Trà sữa trân châu', 39000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(51, 'SP-BEER', 10, 'Bia Tiger (lon)', 'bia-tiger', 'Bia Tiger lon', 25000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(52, 'SP-WATER', 10, 'Nước suối (chai)', 'nuoc-suoi', 'Nước suối tinh khiết', 10000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(53, 'SP-PEACH-TEA', 10, 'Trà đào cam sả (ly)', 'tra-dao', 'Trà đào cam sả', 35000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(54, 'SP-MANGO-PUD', 11, 'Pudding xoài (phần)', 'pudding-xoai', 'Pudding xoài tươi', 29000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(55, 'SP-TANGYUAN', 11, 'Bánh trôi nước (chén)', 'banh-troi', 'Bánh trôi nước', 25000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(56, 'SP-ICE-CREAM', 11, 'Kem matcha (phần)', 'kem-matcha', 'Kem matcha Nhật', 29000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498'),
(57, 'SP-CHE', 11, 'Chè Thái (chén)', 'che-thai', 'Chè Thái', 25000.00, 1, 0, '2026-11-01 13:28:33.498', '2026-11-01 13:28:33.498');

-- --------------------------------------------------------

--
-- Table structure for table `food_images`
--

CREATE TABLE `food_images` (
  `id` bigint(20) NOT NULL,
  `foods_id` bigint(20) NOT NULL,
  `url` varchar(500) NOT NULL,
  `is_cover` tinyint(1) NOT NULL DEFAULT 0,
  `sort_order` int(11) NOT NULL DEFAULT 0,
  `created_at` datetime(3) NOT NULL DEFAULT current_timestamp(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `food_images`
--

INSERT INTO `food_images` (`id`, `foods_id`, `url`, `is_cover`, `sort_order`, `created_at`) VALUES
(2, 8, 'https://cdn.annhien.vn/foods/ba-chi-bo-my.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(3, 14, 'https://cdn.annhien.vn/foods/ba-chi-heo.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(4, 22, 'https://cdn.annhien.vn/foods/bach-tuoc.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(5, 47, 'https://cdn.annhien.vn/foods/banh-bao-kim-sa.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(6, 55, 'https://cdn.annhien.vn/foods/banh-troi.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(7, 39, 'https://cdn.annhien.vn/foods/bap-my.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(8, 51, 'https://cdn.annhien.vn/foods/bia-tiger.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(9, 12, 'https://cdn.annhien.vn/foods/bo-gau.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(10, 11, 'https://cdn.annhien.vn/foods/bo-phi-le.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(11, 9, 'https://cdn.annhien.vn/foods/bo-uc-cuon.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(12, 42, 'https://cdn.annhien.vn/foods/bun-tuoi.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(13, 21, 'https://cdn.annhien.vn/foods/ca-hoi.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(14, 34, 'https://cdn.annhien.vn/foods/cai-thao.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(15, 38, 'https://cdn.annhien.vn/foods/cai-xanh.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(16, 28, 'https://cdn.annhien.vn/foods/cha-ca.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(17, 18, 'https://cdn.annhien.vn/foods/chan-ga.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(18, 17, 'https://cdn.annhien.vn/foods/chan-gio-heo.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(19, 57, 'https://cdn.annhien.vn/foods/che-thai.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(20, 31, 'https://cdn.annhien.vn/foods/dau-phu-chien.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(21, 32, 'https://cdn.annhien.vn/foods/dau-phu-da.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(22, 30, 'https://cdn.annhien.vn/foods/dau-phu-non.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(23, 33, 'https://cdn.annhien.vn/foods/dau-phu-phong.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(24, 48, 'https://cdn.annhien.vn/foods/dimsum.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(25, 45, 'https://cdn.annhien.vn/foods/ha-cao.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(26, 56, 'https://cdn.annhien.vn/foods/kem-matcha.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(27, 16, 'https://cdn.annhien.vn/foods/long-heo.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(28, 13, 'https://cdn.annhien.vn/foods/luoi-bo.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(29, 44, 'https://cdn.annhien.vn/foods/mi-trung.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(30, 40, 'https://cdn.annhien.vn/foods/mi-tuoi.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(31, 41, 'https://cdn.annhien.vn/foods/mi-udon.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(32, 43, 'https://cdn.annhien.vn/foods/mien.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(33, 20, 'https://cdn.annhien.vn/foods/muc-ong.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(34, 36, 'https://cdn.annhien.vn/foods/nam-dong-co.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(35, 35, 'https://cdn.annhien.vn/foods/nam-kim-cham.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(36, 2, 'https://cdn.annhien.vn/foods/nuoc-lau-ca-chua.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(37, 5, 'https://cdn.annhien.vn/foods/nuoc-lau-collagen.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(38, 1, 'https://cdn.annhien.vn/foods/nuoc-lau-mala.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(39, 3, 'https://cdn.annhien.vn/foods/nuoc-lau-nam.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(40, 6, 'https://cdn.annhien.vn/foods/nuoc-lau-ot-xanh.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(41, 7, 'https://cdn.annhien.vn/foods/nuoc-lau-sua.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(42, 4, 'https://cdn.annhien.vn/foods/nuoc-lau-thai.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(43, 52, 'https://cdn.annhien.vn/foods/nuoc-suoi.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(44, 54, 'https://cdn.annhien.vn/foods/pudding-xoai.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(45, 37, 'https://cdn.annhien.vn/foods/rau-muong.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(46, 23, 'https://cdn.annhien.vn/foods/so-diep.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(47, 46, 'https://cdn.annhien.vn/foods/sui-cao.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(48, 10, 'https://cdn.annhien.vn/foods/thit-cuu-cuon.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(49, 15, 'https://cdn.annhien.vn/foods/thit-ga.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(50, 24, 'https://cdn.annhien.vn/foods/tom-hum.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(51, 19, 'https://cdn.annhien.vn/foods/tom-su.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(52, 53, 'https://cdn.annhien.vn/foods/tra-dao.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(53, 49, 'https://cdn.annhien.vn/foods/tra-o-long.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(54, 50, 'https://cdn.annhien.vn/foods/tra-sua.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(55, 26, 'https://cdn.annhien.vn/foods/vien-bo.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(56, 25, 'https://cdn.annhien.vn/foods/vien-ca.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(57, 27, 'https://cdn.annhien.vn/foods/vien-tom.jpg', 1, 1, '2026-11-01 13:28:33.672'),
(58, 29, 'https://cdn.annhien.vn/foods/xiu-mai.jpg', 1, 1, '2026-11-01 13:28:33.672');

-- --------------------------------------------------------

--
-- Table structure for table `ingredients`
--

CREATE TABLE `ingredients` (
  `id` bigint(20) NOT NULL,
  `name` varchar(200) NOT NULL,
  `unit` varchar(50) NOT NULL,
  `stock_quantity` decimal(10,2) NOT NULL DEFAULT 0.00,
  `min_stock_level` decimal(10,2) NOT NULL DEFAULT 0.00,
  `created_at` datetime(3) NOT NULL DEFAULT current_timestamp(3),
  `updated_at` datetime(3) NOT NULL DEFAULT current_timestamp(3) ON UPDATE current_timestamp(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `ingredients`
--

INSERT INTO `ingredients` (`id`, `name`, `unit`, `stock_quantity`, `min_stock_level`, `created_at`, `updated_at`) VALUES
(1, 'Cốt lẩu mala', 'phần', 498.00, 20.00, '2026-11-01 13:28:33.475', '2026-11-08 23:44:07.968'),
(2, 'Cốt lẩu cà chua', 'phần', 398.00, 20.00, '2026-11-01 13:28:33.475', '2026-11-08 23:44:07.970'),
(3, 'Cốt lẩu nấm', 'phần', 398.00, 20.00, '2026-11-01 13:28:33.475', '2026-11-06 00:39:27.454'),
(4, 'Cốt lẩu Thái', 'phần', 350.00, 20.00, '2026-11-01 13:28:33.475', '2026-11-01 13:28:33.475'),
(5, 'Cốt lẩu collagen', 'phần', 300.00, 15.00, '2026-11-01 13:28:33.475', '2026-11-01 13:28:33.475'),
(6, 'Cốt lẩu ớt xanh', 'phần', 300.00, 15.00, '2026-11-01 13:28:33.475', '2026-11-01 13:28:33.475'),
(7, 'Cốt lẩu sữa', 'phần', 250.00, 15.00, '2026-11-01 13:28:33.475', '2026-11-01 13:28:33.475'),
(8, 'Ba chỉ bò Mỹ', 'kg', 80.00, 10.00, '2026-11-01 13:28:33.475', '2026-11-01 13:28:33.475'),
(9, 'Bò Úc cuộn', 'kg', 60.00, 10.00, '2026-11-01 13:28:33.475', '2026-11-01 13:28:33.475'),
(10, 'Thịt cừu cuộn', 'kg', 50.00, 10.00, '2026-11-01 13:28:33.475', '2026-11-01 13:28:33.475'),
(11, 'Ba chỉ heo', 'kg', 69.80, 10.00, '2026-11-01 13:28:33.475', '2026-11-03 01:57:25.454'),
(12, 'Thịt gà', 'kg', 60.00, 10.00, '2026-11-01 13:28:33.475', '2026-11-01 13:28:33.475'),
(13, 'Lòng heo', 'kg', 30.00, 5.00, '2026-11-01 13:28:33.475', '2026-11-01 13:28:33.475'),
(14, 'Tôm sú', 'kg', 40.00, 5.00, '2026-11-01 13:28:33.475', '2026-11-01 13:28:33.475'),
(15, 'Mực ống', 'kg', 35.00, 5.00, '2026-11-01 13:28:33.475', '2026-11-01 13:28:33.475'),
(16, 'Cá hồi', 'kg', 30.00, 5.00, '2026-11-01 13:28:33.475', '2026-11-01 13:28:33.475'),
(17, 'Bạch tuộc', 'kg', 25.00, 5.00, '2026-11-01 13:28:33.475', '2026-11-01 13:28:33.475'),
(18, 'Sò điệp', 'kg', 20.00, 5.00, '2026-11-01 13:28:33.475', '2026-11-01 13:28:33.475'),
(19, 'Tôm hùm', 'con', 50.00, 5.00, '2026-11-01 13:28:33.475', '2026-11-01 13:28:33.475'),
(20, 'Viên cá', 'kg', 50.00, 10.00, '2026-11-01 13:28:33.475', '2026-11-01 13:28:33.475'),
(21, 'Viên bò', 'kg', 50.00, 10.00, '2026-11-01 13:28:33.475', '2026-11-01 13:28:33.475'),
(22, 'Viên tôm', 'kg', 45.00, 10.00, '2026-11-01 13:28:33.475', '2026-11-01 13:28:33.475'),
(23, 'Chả cá', 'kg', 40.00, 10.00, '2026-11-01 13:28:33.475', '2026-11-01 13:28:33.475'),
(24, 'Xíu mại', 'kg', 35.00, 10.00, '2026-11-01 13:28:33.475', '2026-11-01 13:28:33.475'),
(25, 'Đậu phụ non', 'kg', 40.00, 10.00, '2026-11-01 13:28:33.475', '2026-11-01 13:28:33.475'),
(26, 'Đậu phụ chiên', 'kg', 34.20, 10.00, '2026-11-01 13:28:33.475', '2026-11-09 02:24:06.669'),
(27, 'Đậu phụ da', 'kg', 30.00, 10.00, '2026-11-01 13:28:33.475', '2026-11-01 13:28:33.475'),
(28, 'Cải thảo', 'kg', 60.00, 15.00, '2026-11-01 13:28:33.475', '2026-11-01 13:28:33.475'),
(29, 'Nấm kim châm', 'kg', 50.00, 10.00, '2026-11-01 13:28:33.475', '2026-11-01 13:28:33.475'),
(30, 'Nấm đông cô', 'kg', 40.00, 10.00, '2026-11-01 13:28:33.475', '2026-11-01 13:28:33.475'),
(31, 'Nấm linh chi', 'kg', 20.00, 5.00, '2026-11-01 13:28:33.475', '2026-11-01 13:28:33.475'),
(32, 'Rau muống', 'kg', 45.00, 10.00, '2026-11-01 13:28:33.475', '2026-11-01 13:28:33.475'),
(33, 'Cải xanh', 'kg', 45.00, 10.00, '2026-11-01 13:28:33.475', '2026-11-01 13:28:33.475'),
(34, 'Bắp Mỹ', 'kg', 30.00, 10.00, '2026-11-01 13:28:33.475', '2026-11-01 13:28:33.475'),
(35, 'Khoai tây', 'kg', 40.00, 10.00, '2026-11-01 13:28:33.475', '2026-11-01 13:28:33.475'),
(36, 'Mì tươi', 'kg', 50.00, 10.00, '2026-11-01 13:28:33.475', '2026-11-01 13:28:33.475'),
(37, 'Mì udon', 'kg', 40.00, 10.00, '2026-11-01 13:28:33.475', '2026-11-01 13:28:33.475'),
(38, 'Bún tươi', 'kg', 40.00, 10.00, '2026-11-01 13:28:33.475', '2026-11-01 13:28:33.475'),
(39, 'Miến', 'kg', 30.00, 10.00, '2026-11-01 13:28:33.475', '2026-11-01 13:28:33.475'),
(40, 'Mì trứng', 'kg', 30.00, 10.00, '2026-11-01 13:28:33.475', '2026-11-01 13:28:33.475'),
(41, 'Trà ô long', 'ly', 800.00, 50.00, '2026-11-01 13:28:33.475', '2026-11-01 13:28:33.475'),
(42, 'Trà sữa', 'ly', 600.00, 50.00, '2026-11-01 13:28:33.475', '2026-11-01 13:28:33.475'),
(43, 'Bia Tiger', 'lon', 500.00, 30.00, '2026-11-01 13:28:33.475', '2026-11-01 13:28:33.475'),
(44, 'Nước suối', 'chai', 800.00, 50.00, '2026-11-01 13:28:33.475', '2026-11-01 13:28:33.475'),
(45, 'Pudding xoài', 'phần', 200.00, 20.00, '2026-11-01 13:28:33.475', '2026-11-01 13:28:33.475');

-- --------------------------------------------------------

--
-- Table structure for table `orders`
--

CREATE TABLE `orders` (
  `id` bigint(20) NOT NULL,
  `order_code` varchar(30) NOT NULL,
  `parent_order_id` bigint(20) DEFAULT NULL,
  `user_id` bigint(20) DEFAULT NULL,
  `table_id` bigint(20) DEFAULT NULL,
  `table_session_id` bigint(20) DEFAULT NULL,
  `voucher_id` bigint(20) DEFAULT NULL,
  `order_type` enum('DINE_IN','TAKEAWAY','DELIVERY') NOT NULL DEFAULT 'DINE_IN',
  `status` enum('PENDING','RECEIVED','COOKING','COMPLETED','SERVED','CANCELLED') NOT NULL DEFAULT 'PENDING',
  `subtotal` decimal(14,2) NOT NULL,
  `shipping_fee` decimal(14,2) NOT NULL DEFAULT 0.00,
  `discount_amount` decimal(14,2) NOT NULL DEFAULT 0.00,
  `total_amount` decimal(14,2) NOT NULL,
  `receiver_name` varchar(150) DEFAULT NULL,
  `receiver_phone` varchar(20) DEFAULT NULL,
  `shipping_address` varchar(500) DEFAULT NULL,
  `note` varchar(500) DEFAULT NULL,
  `cancel_reason` varchar(255) DEFAULT NULL,
  `cancelled_at` datetime(3) DEFAULT NULL,
  `confirmed_by` bigint(20) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT current_timestamp(3),
  `updated_at` datetime(3) NOT NULL DEFAULT current_timestamp(3) ON UPDATE current_timestamp(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `orders`
--

INSERT INTO `orders` (`id`, `order_code`, `parent_order_id`, `user_id`, `table_id`, `table_session_id`, `voucher_id`, `order_type`, `status`, `subtotal`, `shipping_fee`, `discount_amount`, `total_amount`, `receiver_name`, `receiver_phone`, `shipping_address`, `note`, `cancel_reason`, `cancelled_at`, `confirmed_by`, `created_at`, `updated_at`) VALUES
(1, 'ORD7F8BF5A1D26E44D9', NULL, NULL, 1, 65, NULL, 'DINE_IN', 'COMPLETED', 347000.00, 0.00, 0.00, 347000.00, NULL, NULL, NULL, '', NULL, NULL, NULL, '2026-11-03 01:55:55.535', '2026-11-03 01:58:03.940'),
(2, 'ORDABBDE8C287F248FC', 1, NULL, 1, 65, NULL, 'DINE_IN', 'COMPLETED', 79000.00, 0.00, 0.00, 79000.00, NULL, NULL, NULL, '', NULL, NULL, NULL, '2026-11-03 01:57:25.459', '2026-11-03 01:58:03.940'),
(3, 'ORD2830AD1450794BE0', NULL, NULL, 2, 64, NULL, 'DINE_IN', 'COMPLETED', 119000.00, 0.00, 0.00, 119000.00, NULL, NULL, NULL, '', NULL, NULL, NULL, '2026-11-06 00:39:27.461', '2026-11-09 02:31:43.072'),
(4, 'ORDD9A006E7E17A4317', 3, NULL, 2, 64, NULL, 'DINE_IN', 'COMPLETED', 228000.00, 0.00, 0.00, 228000.00, NULL, NULL, NULL, '', NULL, NULL, NULL, '2026-11-08 23:44:07.983', '2026-11-09 02:31:43.072'),
(5, 'ORD9B1ADBD016A44454', 3, NULL, 2, 64, NULL, 'DINE_IN', 'COMPLETED', 116000.00, 0.00, 0.00, 116000.00, NULL, NULL, NULL, 'Đậu phụ chiên (đĩa 200g) (x4): tét', NULL, NULL, NULL, '2026-11-09 02:24:06.681', '2026-11-09 02:31:43.072');

-- --------------------------------------------------------

--
-- Table structure for table `order_items`
--

CREATE TABLE `order_items` (
  `id` bigint(20) NOT NULL,
  `order_id` bigint(20) NOT NULL,
  `foods_id` bigint(20) NOT NULL,
  `foods_name` varchar(200) NOT NULL,
  `unit_price` decimal(14,2) NOT NULL,
  `quantity` int(11) NOT NULL,
  `line_total` decimal(14,2) NOT NULL,
  `status` enum('PENDING','RECEIVED','COOKING','COMPLETED','SERVED','CANCELLED') NOT NULL DEFAULT 'PENDING',
  `note` varchar(500) DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT current_timestamp(3) ON UPDATE current_timestamp(3),
  `completed_at` datetime(3) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `order_items`
--

INSERT INTO `order_items` (`id`, `order_id`, `foods_id`, `foods_name`, `unit_price`, `quantity`, `line_total`, `status`, `note`, `updated_at`, `completed_at`) VALUES
(1, 1, 1, 'Nước lẩu mala Tứ Xuyên (nồi 1 ngăn)', 129000.00, 1, 129000.00, 'SERVED', NULL, '2026-11-03 01:58:03.940', '2026-11-03 01:58:03.940'),
(2, 1, 2, 'Nước lẩu cà chua (nồi 1 ngăn)', 99000.00, 1, 99000.00, 'SERVED', NULL, '2026-11-03 01:58:03.940', '2026-11-03 01:58:03.940'),
(3, 1, 3, 'Nước lẩu nấm (nồi 1 ngăn)', 119000.00, 1, 119000.00, 'SERVED', NULL, '2026-11-03 01:58:03.940', '2026-11-03 01:58:03.940'),
(4, 2, 17, 'Chân giò heo (đĩa 200g)', 79000.00, 1, 79000.00, 'SERVED', NULL, '2026-11-03 01:58:03.940', '2026-11-03 01:58:03.940'),
(5, 3, 3, 'Nước lẩu nấm (nồi 1 ngăn)', 119000.00, 1, 119000.00, 'SERVED', NULL, '2026-11-09 00:09:21.756', '2026-11-06 00:39:54.528'),
(6, 4, 1, 'Nước lẩu mala Tứ Xuyên (nồi 1 ngăn)', 129000.00, 1, 129000.00, 'SERVED', '', '2026-11-09 00:09:56.575', '2026-11-08 23:44:43.857'),
(7, 4, 2, 'Nước lẩu cà chua (nồi 1 ngăn)', 99000.00, 1, 99000.00, 'SERVED', '', '2026-11-09 00:10:03.283', '2026-11-08 23:44:59.166'),
(8, 5, 31, 'Đậu phụ chiên (đĩa 200g)', 29000.00, 4, 116000.00, 'COMPLETED', 'tét', '2026-11-09 02:24:29.744', '2026-11-09 02:24:29.744');

-- --------------------------------------------------------

--
-- Table structure for table `recipes`
--

CREATE TABLE `recipes` (
  `id` bigint(20) NOT NULL,
  `foods_id` bigint(20) NOT NULL,
  `ingredient_id` bigint(20) NOT NULL,
  `quantity_required` decimal(10,2) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `recipes`
--

INSERT INTO `recipes` (`id`, `foods_id`, `ingredient_id`, `quantity_required`) VALUES
(1, 1, 1, 1.00),
(2, 2, 2, 1.00),
(3, 3, 3, 1.00),
(4, 4, 4, 1.00),
(5, 5, 5, 1.00),
(6, 6, 6, 1.00),
(7, 7, 7, 1.00),
(8, 13, 8, 0.15),
(9, 12, 8, 0.15),
(10, 11, 8, 0.15),
(11, 8, 8, 0.15),
(12, 9, 9, 0.15),
(13, 10, 10, 0.15),
(14, 47, 11, 0.05),
(15, 46, 11, 0.05),
(16, 17, 11, 0.20),
(17, 14, 11, 0.15),
(18, 18, 12, 0.15),
(19, 15, 12, 0.20),
(20, 16, 13, 0.15),
(21, 48, 14, 0.05),
(22, 45, 14, 0.05),
(23, 19, 14, 0.20),
(24, 20, 15, 0.15),
(25, 21, 16, 0.15),
(26, 22, 17, 0.15),
(27, 23, 18, 0.15),
(28, 24, 19, 1.00),
(29, 25, 20, 0.15),
(30, 26, 21, 0.15),
(31, 27, 22, 0.15),
(32, 28, 23, 0.15),
(33, 29, 24, 0.15),
(34, 30, 25, 0.20),
(35, 33, 26, 0.15),
(36, 31, 26, 0.20),
(37, 32, 27, 0.15),
(38, 34, 28, 0.20),
(39, 35, 29, 0.15),
(40, 36, 30, 0.15),
(41, 37, 32, 0.20),
(42, 38, 33, 0.20),
(43, 39, 34, 0.20),
(44, 40, 36, 0.15),
(45, 41, 37, 0.15),
(46, 42, 38, 0.15),
(47, 43, 39, 0.10),
(48, 44, 40, 0.15),
(49, 53, 41, 1.00),
(50, 49, 41, 1.00),
(51, 50, 42, 1.00),
(52, 51, 43, 1.00),
(53, 52, 44, 1.00),
(54, 57, 45, 1.00),
(55, 56, 45, 1.00),
(56, 55, 45, 1.00),
(57, 54, 45, 1.00);

-- --------------------------------------------------------

--
-- Table structure for table `reviews`
--

CREATE TABLE `reviews` (
  `id` bigint(20) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  `foods_id` bigint(20) NOT NULL,
  `rating` int(11) NOT NULL CHECK (`rating` >= 1 and `rating` <= 5),
  `comment` text DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT current_timestamp(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `roles`
--

CREATE TABLE `roles` (
  `roleID` bigint(20) NOT NULL,
  `nameRole` varchar(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `roles`
--

INSERT INTO `roles` (`roleID`, `nameRole`) VALUES
(1, 'ADMIN'),
(3, 'CUSTOMER'),
(4, 'KITCHEN'),
(2, 'STAFF');

-- --------------------------------------------------------

--
-- Table structure for table `tables`
--

CREATE TABLE `tables` (
  `id` bigint(20) NOT NULL,
  `table_name` varchar(200) NOT NULL,
  `capacity` int(11) NOT NULL,
  `floor_number` int(11) NOT NULL DEFAULT 1,
  `status` tinyint(1) NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `tables`
--

INSERT INTO `tables` (`id`, `table_name`, `capacity`, `floor_number`, `status`) VALUES
(1, 'Bàn 01', 2, 1, 0),
(2, 'Bàn 02', 2, 1, 1),
(3, 'Bàn 03', 4, 1, 1),
(4, 'Bàn 04', 4, 1, 1),
(5, 'Bàn 05', 4, 1, 1),
(6, 'Bàn 06', 4, 1, 1),
(7, 'Bàn 07', 6, 1, 1),
(8, 'Bàn 08', 6, 1, 1),
(9, 'Bàn 09', 6, 1, 1),
(10, 'Bàn 10', 6, 1, 1),
(11, 'Bàn 11', 8, 1, 1),
(12, 'Bàn 12', 8, 1, 1),
(13, 'Bàn 13', 8, 1, 1),
(14, 'Bàn 14', 8, 1, 1),
(15, 'Bàn 15', 10, 1, 1),
(16, 'Bàn 16', 10, 1, 1),
(17, 'Bàn 17', 2, 1, 1),
(18, 'Bàn 18', 2, 1, 1),
(19, 'Bàn 19', 4, 1, 1),
(20, 'Bàn 20', 4, 1, 1),
(21, 'Bàn VIP 01', 8, 1, 1),
(22, 'Bàn VIP 02', 8, 1, 1),
(23, 'Bàn VIP 03', 10, 1, 1),
(24, 'Bàn VIP 04', 10, 1, 1),
(25, 'Bàn VIP 05', 12, 1, 1),
(26, 'Bàn Sân Vườn 01', 6, 1, 1),
(27, 'Bàn Sân Vườn 02', 6, 1, 1),
(28, 'Bàn Sân Vườn 03', 6, 1, 1),
(29, 'Bàn Sân Vườn 04', 8, 1, 1),
(30, 'Bàn Tầng 2 - 01', 4, 2, 1),
(31, 'Bàn Tầng 2 - 02', 4, 2, 1),
(32, 'Bàn Tầng 2 - 03', 4, 2, 1),
(33, 'Bàn Tầng 2 - 04', 6, 2, 1),
(34, 'Bàn Tầng 2 - 05', 6, 2, 1),
(35, 'Bàn Tầng 2 - 06', 6, 2, 1),
(36, 'Bàn Tầng 3 - 01', 8, 3, 1),
(37, 'Bàn Tầng 3 - 02', 8, 3, 1),
(38, 'Bàn Tầng 3 - 03', 10, 3, 1),
(39, 'Bàn Tầng 3 - 04', 10, 3, 1),
(40, 'Bàn Tầng 3 - 05', 12, 3, 1);

-- --------------------------------------------------------

--
-- Table structure for table `table_sessions`
--

CREATE TABLE `table_sessions` (
  `id` bigint(20) NOT NULL,
  `table_id` bigint(20) NOT NULL,
  `opened_by` bigint(20) DEFAULT NULL,
  `status` enum('ACTIVE','CLOSED') NOT NULL DEFAULT 'ACTIVE',
  `access_token_hash` char(64) NOT NULL,
  `opened_at` datetime(3) NOT NULL DEFAULT current_timestamp(3),
  `closed_at` datetime(3) DEFAULT NULL,
  `active_table_id` bigint(20) GENERATED ALWAYS AS (case when `status` = 'ACTIVE' then `table_id` else NULL end) STORED
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `table_sessions`
--

INSERT INTO `table_sessions` (`id`, `table_id`, `opened_by`, `status`, `access_token_hash`, `opened_at`, `closed_at`) VALUES
(1, 1, 3, 'CLOSED', '774340be2bf3be55b40693dc6968f7d85271540f933a3642c26cd7ac72aa6eac', '2026-11-01 12:28:33.841', '2026-11-01 14:28:33.841'),
(2, 2, 3, 'CLOSED', '44610ce3f53243edfdc51641a433ebcbe3a0086125fd5c99e31f03b923531ea7', '2026-11-01 11:28:33.841', '2026-11-01 13:28:33.841'),
(3, 3, 3, 'CLOSED', '51f345b5b66972613c27152780d2d29926090d60e3ae6033b5b737d426d50296', '2026-11-01 10:28:33.841', '2026-11-01 12:28:33.841'),
(4, 4, 3, 'CLOSED', '25749c1baa89072d74966396f298e7b94fd9c16dc3ceece62109dafa08c91d5e', '2026-11-01 09:28:33.841', '2026-11-01 11:28:33.841'),
(5, 5, 3, 'CLOSED', '797770ce0f3be6503c814e42d2f3369f5bf87f2690a83ed5183920a704288d7e', '2026-11-01 08:28:33.841', '2026-11-01 10:28:33.841'),
(6, 6, 3, 'CLOSED', '4194fd061d29991776bb83aeafe9e94dcf39a346e1c4ebfcb00e796c85a209da', '2026-11-01 07:28:33.841', '2026-11-01 09:28:33.841'),
(7, 7, 3, 'CLOSED', 'd176977db361c5ed573e8c385838a9234d6ce77ea3315e6719e87ec663bd3501', '2026-11-01 06:28:33.841', '2026-11-01 08:28:33.841'),
(8, 8, 3, 'CLOSED', 'd23fdff3c961853b37b6a64f783c100fac5c120efb93333902b355916583c770', '2026-11-01 05:28:33.841', '2026-11-01 07:28:33.841'),
(9, 9, 3, 'CLOSED', '0814adc9215f064409caf3aeaee006158e8d24c0904590f644ff51256391ff08', '2026-11-01 04:28:33.841', '2026-11-01 06:28:33.841'),
(10, 10, 3, 'CLOSED', 'd9ffd0e3f495f973459c4f596424f26aa8338c45d32f098f07475a9dafeb3d54', '2026-11-01 03:28:33.841', '2026-11-01 05:28:33.841'),
(11, 11, 3, 'CLOSED', '2556cc042fee50735c7a7d3a9f2ea35392121886205f45993000ecd503c13e33', '2026-11-01 02:28:33.841', '2026-11-01 04:28:33.841'),
(12, 12, 3, 'CLOSED', '2d46bd374c05d4637c2afb3cae6033c8c8c220c03903d51e68a4c6c09d614c89', '2026-11-01 01:28:33.841', '2026-11-01 03:28:33.841'),
(13, 13, 3, 'CLOSED', 'df2f41c72c72a24a0ce7c7d58dd18c85134dc967cac3a867886512a5ecf38d26', '2026-11-01 00:28:33.841', '2026-11-01 02:28:33.841'),
(14, 14, 3, 'CLOSED', 'fd52163f0c62e0804eefa3499ca02b689025359243658e261ff3c9963c3740b8', '2026-10-31 23:28:33.841', '2026-11-01 01:28:33.841'),
(15, 15, 3, 'CLOSED', 'd603d3d07a21f89ec20d4c63ccc9976013ee6cb69565f8d061c299aa82264a9e', '2026-10-31 22:28:33.841', '2026-11-01 00:28:33.841'),
(16, 16, 3, 'CLOSED', 'b3bb51154e600f06f4f5a82c1b02c36d0586f9985b3127fe2445eacf7467b6e3', '2026-10-31 21:28:33.841', '2026-10-31 23:28:33.841'),
(17, 17, 3, 'CLOSED', 'f00a8592957c5afd56834b0d375c89d5de27be2658988e7f3a25295da6311f9d', '2026-10-31 20:28:33.841', '2026-10-31 22:28:33.841'),
(18, 18, 3, 'CLOSED', '6db77a8338474855e93613e5d4906d3555c458d731333509cab06b3a3eb7302b', '2026-10-31 19:28:33.841', '2026-10-31 21:28:33.841'),
(19, 19, 3, 'CLOSED', '53f23fee53f829db2579e63a7b508777d6bcf917dc00e92a19f6188b98e7b03f', '2026-10-31 18:28:33.841', '2026-10-31 20:28:33.841'),
(20, 20, 3, 'CLOSED', '39b2a5e03000b58adc96d0f06883371b2c84b717e91ded486220e67cfa4f332c', '2026-10-31 17:28:33.841', '2026-10-31 19:28:33.841'),
(21, 26, 3, 'CLOSED', 'c68a6a4c47915691d6a6f51672af1e52dacccc589fd62fff3aee4aa7e8aa401c', '2026-10-31 11:28:33.841', '2026-10-31 13:28:33.841'),
(22, 27, 3, 'CLOSED', '451b9d70250ef1cc5bdb22452b5152823db2b066c3db311119a20532c83208b7', '2026-10-31 10:28:33.841', '2026-10-31 12:28:33.841'),
(23, 28, 3, 'CLOSED', 'a003b44761a117ca894c6c02d7dd02fb1c72bd84c239036f3f47e50b2dd36049', '2026-10-31 09:28:33.841', '2026-10-31 11:28:33.841'),
(24, 29, 3, 'CLOSED', '5fe9dae283b64b1b30befe2301274d34de286a79151456b8c53a6351fd352a4f', '2026-10-31 08:28:33.841', '2026-10-31 10:28:33.841'),
(25, 30, 3, 'CLOSED', '12f94f2204fc2876d58dbec61421f15dd7d27441fbd95104778a87036ecde988', '2026-10-31 07:28:33.841', '2026-10-31 09:28:33.841'),
(26, 31, 3, 'CLOSED', 'ac79d32df7eac9ec8e83b3557538ff215a1c618fe7ea6bcf3082dc322f88b6c3', '2026-10-31 06:28:33.841', '2026-10-31 08:28:33.841'),
(27, 32, 3, 'CLOSED', '41628a6c21f0a849411428c719ffc004ceb4f3d6e8bdabf346fcc04829177913', '2026-10-31 05:28:33.841', '2026-10-31 07:28:33.841'),
(28, 33, 3, 'CLOSED', '8a2dcd0b715cfb5b1b3f343c3ad667cd7dcc30246a968ddb745164ea9bd7a8bf', '2026-10-31 04:28:33.841', '2026-10-31 06:28:33.841'),
(29, 34, 3, 'CLOSED', '90f3ec1a571746c82dd00b71204cee80ad142559b9bcf945b05b6f3b3a45443f', '2026-10-31 03:28:33.841', '2026-10-31 05:28:33.841'),
(30, 35, 3, 'CLOSED', '680d4b91f3a85d5213686e0c10f5b6e20c60870afa4f8ffe8da1de556a4a1a34', '2026-10-31 02:28:33.841', '2026-10-31 04:28:33.841'),
(31, 36, 3, 'CLOSED', '21e261a200c588b63cc31cdf46dfd5526b579ebc5e861cf8cc8efe72547faa5f', '2026-10-31 01:28:33.841', '2026-10-31 03:28:33.841'),
(32, 21, 3, 'CLOSED', '7c84749305ebdbe6a14a8b1bdfd611b977e6fd154ed6d0dad13b3b569f0e196d', '2026-10-31 16:28:33.841', '2026-10-31 18:28:33.841'),
(33, 22, 3, 'CLOSED', '6868149c8de66d28ecf4e4dd6cdd9154afea747f7ef2f206c75c35db481276eb', '2026-10-31 15:28:33.841', '2026-10-31 17:28:33.841'),
(34, 23, 3, 'CLOSED', 'a0e42e656b32974f7c1354037f0a2172c6a6729597bfd684f462902f63f4ed8d', '2026-10-31 14:28:33.841', '2026-10-31 16:28:33.841'),
(35, 24, 3, 'CLOSED', '098d616a52d1b15b5e2f873e9e8ee1586deba3295e527bb6a6b24332f3340544', '2026-10-31 13:28:33.841', '2026-10-31 15:28:33.841'),
(36, 25, 3, 'CLOSED', 'e1c6517589d982d71fe331f5d3c95a99ce3231f96aee9b214398106678d2fc1c', '2026-10-31 12:28:33.841', '2026-10-31 14:28:33.841'),
(64, 2, 1, 'CLOSED', '0f1dfdf13ebb3ef2b26536e4e71aac954080efae3b152997221f685309b14fe5', '2026-11-02 18:52:05.400', '2026-11-09 02:31:43.073'),
(65, 1, 1, 'CLOSED', '0892b41e6a84f5afb7126039cf5c4760277dbc5cda6cbc1d21bb756ac80b95e8', '2026-11-02 18:52:11.901', '2026-11-03 01:58:03.943'),
(66, 1, 46, 'ACTIVE', 'e8cf250903f923e7cb0fcf20aebac187b8e6dec5e79e4afb948269a1999e7cfb', '2026-11-09 02:34:42.271', NULL);

-- --------------------------------------------------------

--
-- Table structure for table `users`
--

CREATE TABLE `users` (
  `id` bigint(20) NOT NULL,
  `roleID` bigint(20) NOT NULL,
  `email` varchar(255) NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `full_name` varchar(150) NOT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `is_active` tinyint(1) NOT NULL DEFAULT 1,
  `created_at` datetime(3) NOT NULL DEFAULT current_timestamp(3),
  `updated_at` datetime(3) NOT NULL DEFAULT current_timestamp(3) ON UPDATE current_timestamp(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `users`
--

INSERT INTO `users` (`id`, `roleID`, `email`, `password_hash`, `full_name`, `phone`, `is_active`, `created_at`, `updated_at`) VALUES
(1, 4, 'tuankiet21950@gmail.com', 'pbkdf2$210000$sQ+575GIEC5cAjTKsrcWLQ==$iDrodCe7s59koUf2Jo0nH5yTa9mt7KNqznVV1iuurHg=', 'Trần Giang Tuấn Kiệt', '0906628992', 1, '2026-11-01 13:28:33.352', '2026-11-09 02:23:21.215'),
(2, 1, 'tranthuylinh@annhien.vn', 'pbkdf2$210000$b8zQLUOXy5BDUARGobmp0g==$SG4HqRkxYQXRUHQuihM5f5x/7pR43dLvoKlHu/yT6Xc=', 'Trần Thùy Linh', '0912345678', 1, '2026-11-01 13:28:33.352', '2026-11-01 13:28:33.352'),
(3, 2, 'levanhung@annhien.vn', 'pbkdf2$210000$9Picb6rIeGCtn41gecSm6Q==$r8ri3BgY8TPAcsTJAGAno46A175uX0S0n4QxVtNFPBM=', 'Lê Văn Hùng', '0923456789', 1, '2026-11-01 13:28:33.352', '2026-11-01 13:28:33.352'),
(4, 2, 'phamthimaianh@annhien.vn', 'pbkdf2$210000$9Picb6rIeGCtn41gecSm6Q==$r8ri3BgY8TPAcsTJAGAno46A175uX0S0n4QxVtNFPBM=', 'Phạm Thị Mai Anh', '0934567890', 1, '2026-11-01 13:28:33.352', '2026-11-01 13:28:33.352'),
(5, 2, 'hoangvannam@annhien.vn', 'pbkdf2$210000$9Picb6rIeGCtn41gecSm6Q==$r8ri3BgY8TPAcsTJAGAno46A175uX0S0n4QxVtNFPBM=', 'Hoàng Văn Nam', '0945678901', 1, '2026-11-01 13:28:33.352', '2026-11-01 13:28:33.352'),
(6, 2, 'vuthihanh@annhien.vn', 'pbkdf2$210000$9Picb6rIeGCtn41gecSm6Q==$r8ri3BgY8TPAcsTJAGAno46A175uX0S0n4QxVtNFPBM=', 'Vũ Thị Hạnh', '0956789012', 1, '2026-11-01 13:28:33.352', '2026-11-01 13:28:33.352'),
(7, 2, 'dangquocbao@annhien.vn', 'pbkdf2$210000$9Picb6rIeGCtn41gecSm6Q==$r8ri3BgY8TPAcsTJAGAno46A175uX0S0n4QxVtNFPBM=', 'Đặng Quốc Bảo', '0967890123', 1, '2026-11-01 13:28:33.352', '2026-11-01 13:28:33.352'),
(8, 2, 'buithithuong@annhien.vn', 'pbkdf2$210000$9Picb6rIeGCtn41gecSm6Q==$r8ri3BgY8TPAcsTJAGAno46A175uX0S0n4QxVtNFPBM=', 'Bùi Thị Thương', '0978901234', 1, '2026-11-01 13:28:33.352', '2026-11-01 13:28:33.352'),
(9, 2, 'dovanphuc@annhien.vn', 'pbkdf2$210000$9Picb6rIeGCtn41gecSm6Q==$r8ri3BgY8TPAcsTJAGAno46A175uX0S0n4QxVtNFPBM=', 'Đỗ Văn Phúc', '0989012345', 1, '2026-11-01 13:28:33.352', '2026-11-01 13:28:33.352'),
(10, 2, 'hothilan@annhien.vn', 'pbkdf2$210000$9Picb6rIeGCtn41gecSm6Q==$r8ri3BgY8TPAcsTJAGAno46A175uX0S0n4QxVtNFPBM=', 'Hồ Thị Lan', '0990123456', 1, '2026-11-01 13:28:33.352', '2026-11-01 13:28:33.352'),
(11, 4, 'ngovanloc@annhien.vn', 'pbkdf2$210000$AS6igY91wCy+ayuiJQEfcg==$d+t/Qtc6SRCKRvvHeIO5cExMThK00K4tAvkRel5GWOY=', 'Ngô Văn Lộc', '0901111222', 1, '2026-11-01 13:28:33.352', '2026-11-01 13:28:33.352'),
(12, 4, 'duongthimai@annhien.vn', 'pbkdf2$210000$AS6igY91wCy+ayuiJQEfcg==$d+t/Qtc6SRCKRvvHeIO5cExMThK00K4tAvkRel5GWOY=', 'Dương Thị Mai', '0912222333', 1, '2026-11-01 13:28:33.352', '2026-11-01 13:28:33.352'),
(13, 4, 'lyvankhang@annhien.vn', 'pbkdf2$210000$AS6igY91wCy+ayuiJQEfcg==$d+t/Qtc6SRCKRvvHeIO5cExMThK00K4tAvkRel5GWOY=', 'Lý Văn Khang', '0923333444', 1, '2026-11-01 13:28:33.352', '2026-11-01 13:28:33.352'),
(14, 4, 'trinhthihuong@annhien.vn', 'pbkdf2$210000$AS6igY91wCy+ayuiJQEfcg==$d+t/Qtc6SRCKRvvHeIO5cExMThK00K4tAvkRel5GWOY=', 'Trịnh Thị Hương', '0934444555', 1, '2026-11-01 13:28:33.352', '2026-11-01 13:28:33.352'),
(15, 4, 'caovandat@annhien.vn', 'pbkdf2$210000$AS6igY91wCy+ayuiJQEfcg==$d+t/Qtc6SRCKRvvHeIO5cExMThK00K4tAvkRel5GWOY=', 'Cao Văn Đạt', '0945555666', 1, '2026-11-01 13:28:33.352', '2026-11-01 13:28:33.352'),
(16, 3, 'nguyenhoanglong@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Nguyễn Hoàng Long', '0908111222', 1, '2026-11-01 13:28:33.352', '2026-11-01 13:28:33.352'),
(17, 3, 'tranthiminhthu@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Trần Thị Minh Thư', '0919222333', 1, '2026-11-01 13:28:33.352', '2026-11-01 13:28:33.352'),
(18, 3, 'phamquocviet@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Phạm Quốc Việt', '0920333444', 1, '2026-11-01 13:28:33.352', '2026-11-01 13:28:33.352'),
(19, 3, 'lethithanhtruc@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Lê Thị Thanh Trúc', '0931444555', 1, '2026-11-01 13:28:33.352', '2026-11-01 13:28:33.352'),
(20, 3, 'hoangminhkhoa@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Hoàng Minh Khoa', '0942555666', 1, '2026-11-01 13:28:33.352', '2026-11-01 13:28:33.352'),
(21, 3, 'vuthibaongoc@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Vũ Thị Bảo Ngọc', '0953666777', 1, '2026-11-01 13:28:33.352', '2026-11-01 13:28:33.352'),
(22, 3, 'dangtrungdung@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Đặng Trung Dũng', '0964777888', 1, '2026-11-01 13:28:33.352', '2026-11-01 13:28:33.352'),
(23, 3, 'buithithuha@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Bùi Thị Thu Hà', '0975888999', 1, '2026-11-01 13:28:33.352', '2026-11-01 13:28:33.352'),
(24, 3, 'dovietanh@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Đỗ Việt Anh', '0986999000', 1, '2026-11-01 13:28:33.352', '2026-11-01 13:28:33.352'),
(25, 3, 'hothimyla@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Hồ Thị Mỹ La', '0997000111', 1, '2026-11-01 13:28:33.352', '2026-11-01 13:28:33.352'),
(26, 3, 'ngovantai@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Ngô Văn Tài', '0908111333', 1, '2026-11-01 13:28:33.352', '2026-11-01 13:28:33.352'),
(27, 3, 'duongthiyen@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Dương Thị Yến', '0919222444', 1, '2026-11-01 13:28:33.352', '2026-11-01 13:28:33.352'),
(28, 3, 'lyminhquan@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Lý Minh Quân', '0920333555', 1, '2026-11-01 13:28:33.352', '2026-11-01 13:28:33.352'),
(29, 3, 'trinhthihong@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Trịnh Thị Hồng', '0931444666', 1, '2026-11-01 13:28:33.352', '2026-11-01 13:28:33.352'),
(30, 3, 'caominhtri@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Cao Minh Trí', '0942555777', 1, '2026-11-01 13:28:33.352', '2026-11-01 13:28:33.352'),
(31, 3, 'truongvankhanh@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Trương Văn Khánh', '0953666888', 1, '2026-11-01 13:28:33.352', '2026-11-01 13:28:33.352'),
(32, 3, 'phanthikimchi@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Phan Thị Kim Chi', '0964777999', 1, '2026-11-01 13:28:33.352', '2026-11-01 13:28:33.352'),
(33, 3, 'vohongson@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Võ Hồng Sơn', '0975888000', 1, '2026-11-01 13:28:33.352', '2026-11-01 13:28:33.352'),
(34, 3, 'dinhthuphuong@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Đinh Thu Phương', '0986999111', 1, '2026-11-01 13:28:33.352', '2026-11-01 13:28:33.352'),
(35, 3, 'lyvanthanh@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Lý Văn Thành', '0997000222', 1, '2026-11-01 13:28:33.352', '2026-11-01 13:28:33.352'),
(36, 3, 'tranquoctrung@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Trần Quốc Trung', '0901122334', 1, '2026-11-01 13:28:33.352', '2026-11-01 13:28:33.352'),
(37, 3, 'lygiabao@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Lý Gia Bảo', '0912233445', 1, '2026-11-01 13:28:33.352', '2026-11-01 13:28:33.352'),
(38, 3, 'hoangchihung@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Hoàng Chí Hùng', '0923344556', 1, '2026-11-01 13:28:33.352', '2026-11-01 13:28:33.352'),
(39, 3, 'ngothihuong@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Ngô Thị Hương', '0934455667', 1, '2026-11-01 13:28:33.352', '2026-11-01 13:28:33.352'),
(40, 3, 'dangvanphong@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Đặng Văn Phong', '0945566778', 1, '2026-11-01 13:28:33.352', '2026-11-01 13:28:33.352'),
(41, 3, 'buiminhhoang@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Bùi Minh Hoàng', '0956677889', 1, '2026-11-01 13:28:33.352', '2026-11-01 13:28:33.352'),
(42, 3, 'dothuhang@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Đỗ Thu Hằng', '0967788990', 1, '2026-11-01 13:28:33.352', '2026-11-01 13:28:33.352'),
(43, 3, 'hovannhan@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Hồ Văn Nhân', '0978899001', 1, '2026-11-01 13:28:33.352', '2026-11-01 13:28:33.352'),
(44, 3, 'ngothanhtung@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Ngô Thanh Tùng', '0989900112', 1, '2026-11-01 13:28:33.352', '2026-11-01 13:28:33.352'),
(45, 3, 'duongthuylinh@gmail.com', 'pbkdf2$210000$Ti4jKzNIqM+ObvSW2s8veg==$nWHf+Lz2iNNkhJf1zv3qkRl9cfB4JfeQIwUAWIbXAqQ=', 'Dương Thùy Linh', '0990011223', 1, '2026-11-01 13:28:33.352', '2026-11-01 13:28:33.352'),
(46, 1, 'annhien_admin@gmail.com', 'pbkdf2$210000$ZQLaL6QSjY4Y8lQi9LXOfA==$hzZJmRcYNPRXaaaP69FzOiT1HqBTXNBFQBYNtVo3Lk4=', 'Quản trị viên thử nghiệm', NULL, 1, '2026-11-09 02:29:35.427', '2026-11-09 02:29:35.427'),
(47, 2, 'annhien_staff@gmail.com', 'pbkdf2$210000$tTtsUqAJzVfM452/PlfmtQ==$fzYXcUgOAQ7PPkk2G7xoyuMduMAvM0s7Jya3vfAxEeQ=', 'Nhân viên phục vụ thử nghiệm', NULL, 1, '2026-11-09 02:29:35.461', '2026-11-09 02:29:35.461'),
(48, 4, 'annhien_kitchen@gmail.com', 'pbkdf2$210000$e2zlOYEXnknujQV1q2oWmA==$kewztsBO9XfpyNdBkRjy+fwn9OScMUb6Pm4freJH858=', 'Nhân viên bếp thử nghiệm', NULL, 1, '2026-11-09 02:29:35.482', '2026-11-09 02:29:35.482'),
(49, 3, 'annhien_customer@gmail.com', 'pbkdf2$210000$arzmKkdkTpe5raPAsnVumA==$rMbmMYMWdEJ7KPkCzuxeFmw+H/h7iKlopTr9L2QmPWw=', 'Khách hàng thử nghiệm', NULL, 1, '2026-11-09 02:29:35.509', '2026-11-09 02:29:35.509');

-- --------------------------------------------------------

--
-- Table structure for table `vouchers`
--

CREATE TABLE `vouchers` (
  `id` bigint(20) NOT NULL,
  `code` varchar(50) NOT NULL,
  `name` varchar(255) DEFAULT NULL,
  `description` varchar(500) DEFAULT NULL,
  `discount_type` enum('PERCENT','FIXED') NOT NULL,
  `discount_value` decimal(15,2) NOT NULL,
  `min_order_amount` decimal(15,2) DEFAULT 0.00,
  `max_discount_amount` decimal(15,2) DEFAULT NULL,
  `start_at` datetime(3) NOT NULL,
  `end_at` datetime(3) NOT NULL,
  `usage_limit` int(11) DEFAULT NULL,
  `used_count` int(11) DEFAULT 0,
  `is_active` tinyint(1) DEFAULT 1,
  `created_at` datetime(3) NOT NULL DEFAULT current_timestamp(3),
  `updated_at` datetime(3) NOT NULL DEFAULT current_timestamp(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `vouchers`
--

INSERT INTO `vouchers` (`id`, `code`, `name`, `description`, `discount_type`, `discount_value`, `min_order_amount`, `max_discount_amount`, `start_at`, `end_at`, `usage_limit`, `used_count`, `is_active`, `created_at`, `updated_at`) VALUES
(1, 'WELCOME10', 'Chào mừng khách mới', 'Giảm 10% cho đơn đầu tiên', 'PERCENT', 10.00, 200000.00, 50000.00, '2026-10-31 13:28:33.000', '2027-11-01 13:28:33.000', 500, 0, 1, '2026-11-01 13:28:33.761', '2026-11-01 13:28:33.761'),
(2, 'LAU100K', 'Giảm 100K đơn lẩu', 'Giảm 100K cho đơn từ 800K', 'FIXED', 100000.00, 800000.00, 100000.00, '2026-10-31 13:28:33.000', '2027-04-30 13:28:33.000', 200, 0, 1, '2026-11-01 13:28:33.761', '2026-11-01 13:28:33.761'),
(3, 'MALA15', 'Ưu đãi lẩu mala', 'Giảm 15% cho đơn lẩu mala', 'PERCENT', 15.00, 500000.00, 100000.00, '2026-10-31 13:28:33.000', '2027-01-30 13:28:33.000', 300, 0, 1, '2026-11-01 13:28:33.761', '2026-11-01 13:28:33.761'),
(4, 'CUOITUAN20', 'Cuối tuần vui vẻ', 'Giảm 20% đơn từ 1 triệu', 'PERCENT', 20.00, 1000000.00, 200000.00, '2026-10-31 13:28:33.000', '2026-12-31 13:28:33.000', 100, 0, 1, '2026-11-01 13:28:33.761', '2026-11-01 13:28:33.761'),
(5, 'SINHNHAT50', 'Sinh nhật giảm 50%', 'Giảm 50% cho khách sinh nhật', 'PERCENT', 50.00, 300000.00, 150000.00, '2026-10-31 13:28:33.000', '2026-12-01 13:28:33.000', 50, 0, 1, '2026-11-01 13:28:33.761', '2026-11-01 13:28:33.761'),
(6, 'VIP200K', 'Ưu đãi khách VIP', 'Giảm 200K cho khách VIP', 'FIXED', 200000.00, 1500000.00, 200000.00, '2026-10-31 13:28:33.000', '2027-11-01 13:28:33.000', 50, 0, 1, '2026-11-01 13:28:33.761', '2026-11-01 13:28:33.761'),
(7, 'LAUNHAM', 'Ưu đãi lẩu nấm', 'Giảm 10% cho đơn lẩu nấm', 'PERCENT', 10.00, 300000.00, 50000.00, '2026-10-31 13:28:33.000', '2027-03-01 13:28:33.000', 200, 0, 1, '2026-11-01 13:28:33.761', '2026-11-01 13:28:33.761'),
(8, 'HAISAN12', 'Ưu đãi hải sản', 'Giảm 12% cho đơn hải sản', 'PERCENT', 12.00, 500000.00, 80000.00, '2026-10-31 13:28:33.000', '2027-03-01 13:28:33.000', 150, 0, 1, '2026-11-01 13:28:33.761', '2026-11-01 13:28:33.761'),
(9, 'BIAFREE', 'Tặng bia', 'Giảm giá tương đương 1 lon bia', 'FIXED', 25000.00, 300000.00, 25000.00, '2026-10-31 13:28:33.000', '2027-01-30 13:28:33.000', 300, 0, 1, '2026-11-01 13:28:33.761', '2026-11-01 13:28:33.761'),
(10, 'TRUAVANG', 'Trưa vắng khách', 'Giảm 10% khung 11h-14h', 'PERCENT', 10.00, 200000.00, 50000.00, '2026-10-31 13:28:33.000', '2027-11-01 13:28:33.000', 1000, 0, 1, '2026-11-01 13:28:33.761', '2026-11-01 13:28:33.761'),
(11, 'COMBO4', 'Combo 4 người', 'Giảm 80K cho bàn 4 người', 'FIXED', 80000.00, 1000000.00, 80000.00, '2026-10-31 13:28:33.000', '2027-04-30 13:28:33.000', 300, 0, 1, '2026-11-01 13:28:33.761', '2026-11-01 13:28:33.761'),
(12, 'COMBO6', 'Combo 6 người', 'Giảm 150K cho bàn 6 người', 'FIXED', 150000.00, 1800000.00, 150000.00, '2026-10-31 13:28:33.000', '2027-04-30 13:28:33.000', 200, 0, 1, '2026-11-01 13:28:33.761', '2026-11-01 13:28:33.761'),
(13, 'COMBO8', 'Combo 8 người', 'Giảm 200K cho bàn 8 người', 'FIXED', 200000.00, 2500000.00, 200000.00, '2026-10-31 13:28:33.000', '2027-04-30 13:28:33.000', 100, 0, 1, '2026-11-01 13:28:33.761', '2026-11-01 13:28:33.761'),
(14, 'TAKEAWAY10', 'Mang về giảm 10%', 'Giảm 10% cho đơn mang về', 'PERCENT', 10.00, 200000.00, 30000.00, '2026-10-31 13:28:33.000', '2027-11-01 13:28:33.000', 500, 0, 1, '2026-11-01 13:28:33.761', '2026-11-01 13:28:33.761'),
(15, 'DELIVERY15', 'Giao hàng giảm 15K', 'Giảm 15K phí giao hàng', 'FIXED', 15000.00, 300000.00, 15000.00, '2026-10-31 13:28:33.000', '2027-04-30 13:28:33.000', 500, 0, 1, '2026-11-01 13:28:33.761', '2026-11-01 13:28:33.761'),
(16, 'APP20', 'Ưu đãi đặt qua app', 'Giảm 20K khi đặt qua app', 'FIXED', 20000.00, 250000.00, 20000.00, '2026-10-31 13:28:33.000', '2027-11-01 13:28:33.000', 1000, 0, 1, '2026-11-01 13:28:33.761', '2026-11-01 13:28:33.761'),
(17, 'FREESHIP', 'Miễn phí giao hàng', 'Miễn phí ship đơn từ 500K', 'FIXED', 30000.00, 500000.00, 30000.00, '2026-10-31 13:28:33.000', '2027-04-30 13:28:33.000', 300, 0, 1, '2026-11-01 13:28:33.761', '2026-11-01 13:28:33.761'),
(18, 'TET2027', 'Ưu đãi Tết', 'Giảm 25% dịp Tết', 'PERCENT', 25.00, 500000.00, 200000.00, '2026-12-31 13:28:33.000', '2027-01-30 13:28:33.000', 500, 0, 1, '2026-11-01 13:28:33.761', '2026-11-01 13:28:33.761'),
(19, 'TRUNGTHU', 'Ưu đãi Trung Thu', 'Giảm 20% dịp Trung Thu', 'PERCENT', 20.00, 400000.00, 150000.00, '2026-12-01 13:28:33.000', '2026-12-31 13:28:33.000', 300, 0, 1, '2026-11-01 13:28:33.761', '2026-11-01 13:28:33.761'),
(20, 'NOEL2026', 'Ưu đãi Giáng Sinh', 'Giảm 20% dịp Noel', 'PERCENT', 20.00, 400000.00, 150000.00, '2026-12-01 13:28:33.000', '2026-12-26 13:28:33.000', 300, 0, 1, '2026-11-01 13:28:33.761', '2026-11-01 13:28:33.761'),
(21, 'NEWYEAR', 'Ưu đãi Tết Dương Lịch', 'Giảm 20% dịp năm mới', 'PERCENT', 20.00, 400000.00, 150000.00, '2026-12-31 13:28:33.000', '2027-01-10 13:28:33.000', 300, 0, 1, '2026-11-01 13:28:33.761', '2026-11-01 13:28:33.761'),
(22, 'KHAIVI10', 'Ưu đãi khai vị', 'Giảm 10% món khai vị', 'PERCENT', 10.00, 150000.00, 30000.00, '2026-10-31 13:28:33.000', '2027-03-01 13:28:33.000', 300, 0, 1, '2026-11-01 13:28:33.761', '2026-11-01 13:28:33.761'),
(23, 'TRANGMIENG', 'Tặng tráng miệng', 'Giảm 25K khi gọi tráng miệng', 'FIXED', 25000.00, 200000.00, 25000.00, '2026-10-31 13:28:33.000', '2027-03-01 13:28:33.000', 500, 0, 1, '2026-11-01 13:28:33.761', '2026-11-01 13:28:33.761'),
(24, 'DOUONG20', 'Ưu đãi đồ uống', 'Giảm 20% đồ uống', 'PERCENT', 20.00, 100000.00, 30000.00, '2026-10-31 13:28:33.000', '2027-03-01 13:28:33.000', 500, 0, 1, '2026-11-01 13:28:33.761', '2026-11-01 13:28:33.761'),
(25, 'DIMSUM15', 'Ưu đãi dimsum', 'Giảm 15% dimsum', 'PERCENT', 15.00, 200000.00, 50000.00, '2026-10-31 13:28:33.000', '2027-03-01 13:28:33.000', 300, 0, 1, '2026-11-01 13:28:33.761', '2026-11-01 13:28:33.761'),
(26, 'REVIEW50', 'Khách đánh giá 5 sao', 'Giảm 50K cho khách review 5 sao', 'FIXED', 50000.00, 300000.00, 50000.00, '2026-10-31 13:28:33.000', '2027-11-01 13:28:33.000', 1000, 0, 1, '2026-11-01 13:28:33.761', '2026-11-01 13:28:33.761'),
(27, 'BIRTHDAY', 'Sinh nhật khách hàng', 'Giảm 30% trong tháng sinh nhật', 'PERCENT', 30.00, 300000.00, 200000.00, '2026-10-31 13:28:33.000', '2027-11-01 13:28:33.000', 500, 0, 1, '2026-11-01 13:28:33.761', '2026-11-01 13:28:33.761'),
(28, 'LOYAL100', 'Khách thân thiết', 'Giảm 100K cho khách thân thiết', 'FIXED', 100000.00, 500000.00, 100000.00, '2026-10-31 13:28:33.000', '2027-11-01 13:28:33.000', 300, 0, 1, '2026-11-01 13:28:33.761', '2026-11-01 13:28:33.761'),
(29, 'FIRSTBUY', 'Lần đầu mua', 'Giảm 10% lần đầu', 'PERCENT', 10.00, 150000.00, 30000.00, '2026-10-31 13:28:33.000', '2027-11-01 13:28:33.000', 1000, 0, 1, '2026-11-01 13:28:33.761', '2026-11-01 13:28:33.761'),
(30, 'GROUP10', 'Đoàn đông giảm 10%', 'Giảm 10% cho đoàn 10 người', 'PERCENT', 10.00, 2000000.00, 200000.00, '2026-10-31 13:28:33.000', '2027-04-30 13:28:33.000', 200, 0, 1, '2026-11-01 13:28:33.761', '2026-11-01 13:28:33.761'),
(31, 'GROUP15', 'Đoàn đông giảm 15%', 'Giảm 15% cho đoàn 15 người', 'PERCENT', 15.00, 3000000.00, 300000.00, '2026-10-31 13:28:33.000', '2027-04-30 13:28:33.000', 100, 0, 1, '2026-11-01 13:28:33.761', '2026-11-01 13:28:33.761'),
(32, 'WEEKDAY10', 'Ngày thường giảm 10%', 'Giảm 10% từ T2-T5', 'PERCENT', 10.00, 200000.00, 50000.00, '2026-10-31 13:28:33.000', '2027-11-01 13:28:33.000', 1000, 0, 1, '2026-11-01 13:28:33.761', '2026-11-01 13:28:33.761'),
(33, 'WEEKEND5', 'Cuối tuần giảm 5%', 'Giảm 5% T7-CN', 'PERCENT', 5.00, 200000.00, 30000.00, '2026-10-31 13:28:33.000', '2027-11-01 13:28:33.000', 1000, 0, 1, '2026-11-01 13:28:33.761', '2026-11-01 13:28:33.761'),
(34, 'STAFFTREAT', 'Nhân viên chiêu đãi', 'Giảm 30% cho nhân viên', 'PERCENT', 30.00, 100000.00, 100000.00, '2026-10-31 13:28:33.000', '2027-11-01 13:28:33.000', 1000, 0, 1, '2026-11-01 13:28:33.761', '2026-11-01 13:28:33.761'),
(35, 'NEWSTORE', 'Khai trương chi nhánh mới', 'Giảm 25% khai trương', 'PERCENT', 25.00, 300000.00, 150000.00, '2026-10-31 13:28:33.000', '2026-12-01 13:28:33.000', 500, 0, 1, '2026-11-01 13:28:33.761', '2026-11-01 13:28:33.761'),
(36, 'FLASHSALE', 'Flash sale 30 phút', 'Giảm 20% flash sale', 'PERCENT', 20.00, 200000.00, 100000.00, '2026-10-31 13:28:33.000', '2026-12-01 13:28:33.000', 100, 0, 1, '2026-11-01 13:28:33.761', '2026-11-01 13:28:33.761');

--
-- Indexes for dumped tables
--

--
-- Indexes for table `bill`
--
ALTER TABLE `bill`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uq_bill_session` (`table_session_id`),
  ADD KEY `idx_bill_payment_date` (`payment_status`,`paid_at`);

--
-- Indexes for table `categories`
--
ALTER TABLE `categories`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `name` (`name`),
  ADD UNIQUE KEY `slug` (`slug`);

--
-- Indexes for table `foods`
--
ALTER TABLE `foods`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `sku` (`sku`),
  ADD UNIQUE KEY `slug` (`slug`),
  ADD KEY `idx_foods_category` (`category_id`),
  ADD KEY `idx_foods_price` (`price`),
  ADD KEY `idx_foods_published` (`is_published`,`is_deleted`);

--
-- Indexes for table `food_images`
--
ALTER TABLE `food_images`
  ADD PRIMARY KEY (`id`),
  ADD KEY `fk_foods_images_foods` (`foods_id`);

--
-- Indexes for table `ingredients`
--
ALTER TABLE `ingredients`
  ADD PRIMARY KEY (`id`);

--
-- Indexes for table `orders`
--
ALTER TABLE `orders`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `order_code` (`order_code`),
  ADD KEY `fk_orders_parent` (`parent_order_id`),
  ADD KEY `fk_orders_admin` (`confirmed_by`),
  ADD KEY `fk_orders_table` (`table_id`),
  ADD KEY `fk_orders_voucher` (`voucher_id`),
  ADD KEY `idx_orders_user` (`user_id`),
  ADD KEY `idx_orders_status_created` (`status`,`created_at`),
  ADD KEY `idx_orders_created_at` (`created_at`),
  ADD KEY `idx_order_session` (`table_session_id`);

--
-- Indexes for table `order_items`
--
ALTER TABLE `order_items`
  ADD PRIMARY KEY (`id`),
  ADD KEY `fk_oi_order` (`order_id`),
  ADD KEY `fk_oi_foods` (`foods_id`),
  ADD KEY `idx_order_items_status` (`status`);

--
-- Indexes for table `recipes`
--
ALTER TABLE `recipes`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uq_food_ingredient` (`foods_id`,`ingredient_id`),
  ADD KEY `fk_recipes_ingredients` (`ingredient_id`);

--
-- Indexes for table `reviews`
--
ALTER TABLE `reviews`
  ADD PRIMARY KEY (`id`),
  ADD KEY `fk_reviews_user` (`user_id`),
  ADD KEY `fk_reviews_food` (`foods_id`);

--
-- Indexes for table `roles`
--
ALTER TABLE `roles`
  ADD PRIMARY KEY (`roleID`),
  ADD UNIQUE KEY `nameRole` (`nameRole`);

--
-- Indexes for table `tables`
--
ALTER TABLE `tables`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uq_table_name` (`table_name`),
  ADD KEY `idx_tables_floor_number` (`floor_number`,`id`);

--
-- Indexes for table `table_sessions`
--
ALTER TABLE `table_sessions`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uq_session_token` (`access_token_hash`),
  ADD UNIQUE KEY `uq_active_table` (`active_table_id`),
  ADD KEY `idx_session_table` (`table_id`,`opened_at`),
  ADD KEY `fk_session_employee` (`opened_by`);

--
-- Indexes for table `users`
--
ALTER TABLE `users`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `email` (`email`),
  ADD KEY `fk_users_roles` (`roleID`);

--
-- Indexes for table `vouchers`
--
ALTER TABLE `vouchers`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `code` (`code`);

--
-- AUTO_INCREMENT for dumped tables
--

--
-- AUTO_INCREMENT for table `bill`
--
ALTER TABLE `bill`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=3;

--
-- AUTO_INCREMENT for table `categories`
--
ALTER TABLE `categories`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=37;

--
-- AUTO_INCREMENT for table `foods`
--
ALTER TABLE `foods`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=58;

--
-- AUTO_INCREMENT for table `food_images`
--
ALTER TABLE `food_images`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=65;

--
-- AUTO_INCREMENT for table `ingredients`
--
ALTER TABLE `ingredients`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=46;

--
-- AUTO_INCREMENT for table `orders`
--
ALTER TABLE `orders`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=6;

--
-- AUTO_INCREMENT for table `order_items`
--
ALTER TABLE `order_items`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=9;

--
-- AUTO_INCREMENT for table `recipes`
--
ALTER TABLE `recipes`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=64;

--
-- AUTO_INCREMENT for table `reviews`
--
ALTER TABLE `reviews`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `roles`
--
ALTER TABLE `roles`
  MODIFY `roleID` bigint(20) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=5;

--
-- AUTO_INCREMENT for table `tables`
--
ALTER TABLE `tables`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=41;

--
-- AUTO_INCREMENT for table `table_sessions`
--
ALTER TABLE `table_sessions`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=67;

--
-- AUTO_INCREMENT for table `users`
--
ALTER TABLE `users`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=50;

--
-- AUTO_INCREMENT for table `vouchers`
--
ALTER TABLE `vouchers`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=37;

--
-- Constraints for dumped tables
--

--
-- Constraints for table `bill`
--
ALTER TABLE `bill`
  ADD CONSTRAINT `fk_bill_session` FOREIGN KEY (`table_session_id`) REFERENCES `table_sessions` (`id`);

--
-- Constraints for table `foods`
--
ALTER TABLE `foods`
  ADD CONSTRAINT `fk_food_category` FOREIGN KEY (`category_id`) REFERENCES `categories` (`id`);

--
-- Constraints for table `food_images`
--
ALTER TABLE `food_images`
  ADD CONSTRAINT `fk_foods_images_foods` FOREIGN KEY (`foods_id`) REFERENCES `foods` (`id`) ON DELETE CASCADE;

--
-- Constraints for table `orders`
--
ALTER TABLE `orders`
  ADD CONSTRAINT `fk_order_session` FOREIGN KEY (`table_session_id`) REFERENCES `table_sessions` (`id`),
  ADD CONSTRAINT `fk_orders_admin` FOREIGN KEY (`confirmed_by`) REFERENCES `users` (`id`),
  ADD CONSTRAINT `fk_orders_parent` FOREIGN KEY (`parent_order_id`) REFERENCES `orders` (`id`),
  ADD CONSTRAINT `fk_orders_table` FOREIGN KEY (`table_id`) REFERENCES `tables` (`id`),
  ADD CONSTRAINT `fk_orders_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  ADD CONSTRAINT `fk_orders_voucher` FOREIGN KEY (`voucher_id`) REFERENCES `vouchers` (`id`);

--
-- Constraints for table `order_items`
--
ALTER TABLE `order_items`
  ADD CONSTRAINT `fk_oi_foods` FOREIGN KEY (`foods_id`) REFERENCES `foods` (`id`),
  ADD CONSTRAINT `fk_oi_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`) ON DELETE CASCADE;

--
-- Constraints for table `recipes`
--
ALTER TABLE `recipes`
  ADD CONSTRAINT `fk_recipes_foods` FOREIGN KEY (`foods_id`) REFERENCES `foods` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `fk_recipes_ingredients` FOREIGN KEY (`ingredient_id`) REFERENCES `ingredients` (`id`) ON DELETE CASCADE;

--
-- Constraints for table `reviews`
--
ALTER TABLE `reviews`
  ADD CONSTRAINT `fk_reviews_food` FOREIGN KEY (`foods_id`) REFERENCES `foods` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `fk_reviews_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;

--
-- Constraints for table `table_sessions`
--
ALTER TABLE `table_sessions`
  ADD CONSTRAINT `fk_session_employee` FOREIGN KEY (`opened_by`) REFERENCES `users` (`id`),
  ADD CONSTRAINT `fk_session_table` FOREIGN KEY (`table_id`) REFERENCES `tables` (`id`);

--
-- Constraints for table `users`
--
ALTER TABLE `users`
  ADD CONSTRAINT `fk_users_roles` FOREIGN KEY (`roleID`) REFERENCES `roles` (`roleID`);
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
