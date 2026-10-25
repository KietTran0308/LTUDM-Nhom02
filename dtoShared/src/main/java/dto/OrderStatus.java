package dto;

// Dùng chung cho cả bảng orders và order_items để đồng bộ trạng thái Bếp <-> Bàn khách
public enum OrderStatus {
    PENDING,    // Chờ xác nhận
    RECEIVED,   // Bếp đã nhận
    COOKING,    // Đang chế biến
    COMPLETED,  // Đã nấu xong (chờ nhân viên bưng)
    SERVED,     // Đã lên món tại bàn
    CANCELLED   // Đã hủy
}

