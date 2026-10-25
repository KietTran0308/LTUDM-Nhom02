package dto;

import java.io.Serializable;

public enum SocketAction implements Serializable {
    // 1. Luồng khởi tạo & Thực đơn (Tablet Khách)
    REGISTER_TABLE,
    GET_MENU_AND_CATEGORIES,

    // 2. Luồng đặt món & Theo dõi đơn (Tablet Khách)
    PLACE_ORDER,
    GET_TABLE_TRACKING,

    // 3. Luồng Real-time Push từ Server Bếp về Tablet
    KITCHEN_UPDATE_STATUS,
    CALL_STAFF,

    // 4. LUỒNG NHÂN VIÊN THU NGÂN (STAFF POS) - MỚI BỔ SUNG
    GET_ALL_TABLES,          // Lấy sơ đồ tất cả các bàn (Trống / Có khách)
    GET_TABLE_BILL_PREVIEW,  // Xem tạm tính gộp tất cả các lượt gọi (đơn gốc + đơn con) của 1 bàn
    PAY_TABLE_BILL,          // Xác nhận thanh toán (Lưu bảng bill, chốt orders, mở trống bàn)
    TABLE_CLEARED_RESET,     // Server bắn tín hiệu về Tablet của bàn vừa thanh toán để xóa trắng màn hình

    LOGIN, REGISTER
}