package dto;

public enum SocketAction{
    // 1. Xác thực
    LOGIN, LOGIN_STAFF,
    REGISTER,
    LOGOUT,
    REQUEST_PASSWORD_RESET,
    CONFIRM_PASSWORD_RESET,

    // 2. Tablet khách hàng
    GET_MENU_AND_CATEGORIES,
    ORDER_SUBMIT,
    GET_TABLE_TRACKING,
    CALL_STAFF,
    VIEW_SESSION_BILL,

    // 3. Bàn giao Tablet
    LIST_TABLES,
    ENTER_CUSTOMER_MODE,

    // 4. Bếp và sự kiện realtime
    GET_KITCHEN_QUEUE,
    UPDATE_ITEM_STATUS,
    ITEM_STATUS_CHANGED,
    ORDER_CREATED,
    ORDER_COMPLETED,
    SESSION_CLOSED,

    // 5. POS thu ngân
    GET_ALL_TABLES,
    OPEN_TABLE_SESSION,
    GET_TABLE_BILL_PREVIEW,
    MARK_ITEM_SERVED,
    PAY_TABLE_BILL,

    // 6. Admin - Thực đơn
    ADMIN_GET_FOODS,
    ADMIN_GET_CATEGORIES,
    ADMIN_ADD_FOOD,
    ADMIN_UPDATE_FOOD,
    ADMIN_DELETE_FOOD,

    // 8. Admin - Hóa đơn
    ADMIN_GET_BILLS,

    // 9. Admin - Tài khoản
    ADMIN_GET_ACCOUNTS,
    ADMIN_CREATE_ACCOUNT,
    ADMIN_UPDATE_ACCOUNT,
    ADMIN_LOCK_ACCOUNT,

    // 10. Tài khoản thành viên trên Tablet
    MEMBER_LOGIN,
    MEMBER_PROFILE,
    MEMBER_HISTORY,
    MEMBER_LOGOUT
}