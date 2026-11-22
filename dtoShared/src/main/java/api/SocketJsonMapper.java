package api;

import dto.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SocketJsonMapper {

    public static String toJsonString(SocketPacket packet) {
        JSONObject root = new JSONObject();
        root.put("action", packet.getAction().name());
        putIfNotNull(root, "tableId", packet.getTableId());

        Object data = packet.getData();
        Object extraData = packet.getExtraData();

        switch (packet.getAction()) {
            case REQUEST_PASSWORD_RESET, APPLY_TABLE_VOUCHER -> {
                if (data != null) root.put("data", data.toString());
            }
            case CONFIRM_PASSWORD_RESET -> {
                if (data instanceof Map<?, ?> payload) {
                    JSONObject body = new JSONObject();
                    body.put("email", payload.get("email"));
                    body.put("otp", payload.get("otp"));
                    body.put("newPassword", payload.get("newPassword"));
                    root.put("data", body);
                }
            }
            case LOGIN_STAFF, LOGIN -> {
                if (data != null) root.put("data", data.toString());
                if (extraData != null) root.put("extraData", extraData.toString());
            }
            case ORDER_SUBMIT -> {
                if (data instanceof OrderDTO order) root.put("data", orderToJson(order));
                if (extraData != null) root.put("extraData", extraData.toString());
            }
            case ENTER_CUSTOMER_MODE, ADMIN_DELETE_FOOD, ADMIN_GET_POINT_HISTORY -> {
                if (data instanceof Long id) root.put("data", id);
            }
            case UPDATE_ITEM_STATUS -> {
                putIfNotNull(root, "data", data);
                if (extraData instanceof dbEnums.OrderStatus st) root.put("extraData", st.name());
            }
            case PAY_TABLE_BILL -> {
                if (data instanceof BillDTO bill) root.put("data", billToJson(bill));
                if (extraData instanceof String revision) root.put("extraData", revision);
            }
            case ADMIN_ADD_FOOD, ADMIN_UPDATE_FOOD -> {
                if (data instanceof FoodDTO food) root.put("data", foodToJson(food));
            }
            case ADMIN_GET_BILLS -> {
                if (data instanceof JSONObject filter) root.put("data", filter);
            }
            case ADMIN_GET_ACCOUNTS -> {
                if (data instanceof List<?> list) {
                    JSONArray arr = new JSONArray();
                    for (Object item : list) {
                        if (item instanceof UserDTO user) arr.put(userToJson(user));
                    }
                    root.put("data", arr);
                }
            }
            case ADMIN_CREATE_ACCOUNT, ADMIN_UPDATE_ACCOUNT, REGISTER -> {
                if (data instanceof UserDTO user) root.put("data", userToJson(user));
            }
            case ADMIN_LOCK_ACCOUNT, ADMIN_TOGGLE_VOUCHER -> {
                if (data instanceof Long id) root.put("data", id);
                if (extraData != null) root.put("extraData", extraData.toString());
            }
            case ADMIN_ADD_VOUCHER, ADMIN_UPDATE_VOUCHER -> {
                if (data instanceof VoucherDTO voucher) root.put("data", voucherToJson(voucher));
            }
            default -> {
            }
        }
        return root.toString();
    }

    public static SocketPacket fromJsonString(String rawJson) {
        JSONObject root = new JSONObject(rawJson);

        SocketAction action = SocketAction.valueOf(root.getString("action"));
        boolean success = root.optBoolean("success", false);
        String message = root.optString("message", "");
        Long tableId = readLong(root, "tableId");

        Object data = null;
        Object extraData = null;

        if (success) {
            switch (action) {
                case LOGIN_STAFF, LOGIN, REGISTER -> {
                    if (hasNonNull(root, "data")) data = jsonToUser(root.getJSONObject("data"));
                    if ((action == SocketAction.LOGIN || action == SocketAction.LOGIN_STAFF)
                            && hasNonNull(root, "extraData")) {
                        extraData = root.optBoolean("extraData", false);
                    }
                }
                case GET_MENU_AND_CATEGORIES -> {
                    if (hasNonNull(root, "data")) data = jsonArrayToFoodList(root.getJSONArray("data"));
                    if (hasNonNull(root, "extraData"))
                        extraData = jsonArrayToCategoryList(root.getJSONArray("extraData"));
                }
                case ORDER_SUBMIT, ORDER_CREATED -> {
                    if (hasNonNull(root, "data")) data = jsonToOrder(root.getJSONObject("data"));
                    if (hasNonNull(root, "extraData"))
                        extraData = jsonArrayToOrderItemList(root.getJSONArray("extraData"));
                }
                case GET_TABLE_TRACKING -> {
                    if (hasNonNull(root, "data")) data = jsonArrayToOrderItemList(root.getJSONArray("data"));
                }
                case ITEM_STATUS_CHANGED, UPDATE_ITEM_STATUS -> {
                    data = readLong(root, "data");
                    extraData = readEnum(root, "extraData", dbEnums.OrderStatus.class);
                }
                case ENTER_CUSTOMER_MODE, ORDER_COMPLETED, SESSION_CLOSED, OPEN_TABLE_SESSION ->
                        data = readLong(root, "data");
                case GET_KITCHEN_QUEUE -> {
                    List<OrderDTO> orders = new ArrayList<>();
                    if (hasNonNull(root, "data")) {
                        JSONArray queue = root.getJSONArray("data");
                        for (int i = 0; i < queue.length(); i++) orders.add(jsonToOrder(queue.getJSONObject(i)));
                    }
                    data = orders;
                }
                case LIST_TABLES, GET_ALL_TABLES, PAY_TABLE_BILL -> {
                    if (hasNonNull(root, "data")) {
                        JSONArray arr = root.getJSONArray("data");
                        List<TableDTO> tables = new ArrayList<>();
                        for (int i = 0; i < arr.length(); i++) tables.add(jsonToTable(arr.getJSONObject(i)));
                        data = tables;
                    }
                }
                case ADMIN_GET_FOODS -> {
                    if (hasNonNull(root, "data")) data = jsonArrayToFoodList(root.getJSONArray("data"));
                }
                case ADMIN_GET_CATEGORIES -> {
                    if (hasNonNull(root, "data")) data = jsonArrayToCategoryList(root.getJSONArray("data"));
                }
                case ADMIN_GET_BILLS -> {
                    extraData = root.optJSONObject("extraData");
                    if (hasNonNull(root, "data")) {
                        JSONArray arr = root.getJSONArray("data");
                        List<BillDTO> bills = new ArrayList<>();
                        for (int i = 0; i < arr.length(); i++) bills.add(jsonToBill(arr.getJSONObject(i)));
                        data = bills;
                    }
                }
                case VIEW_SESSION_BILL, GET_TABLE_BILL_PREVIEW, APPLY_TABLE_VOUCHER -> {
                    if (hasNonNull(root, "data")) data = jsonToOrder(root.getJSONObject("data"));
                }
                case ADMIN_GET_ACCOUNTS -> {
                    if (hasNonNull(root, "data")) {
                        JSONArray arr = root.getJSONArray("data");
                        List<UserDTO> users = new ArrayList<>();
                        for (int i = 0; i < arr.length(); i++) users.add(jsonToUser(arr.getJSONObject(i)));
                        data = users;
                    }
                }
                case ADMIN_GET_POINTS -> {
                    List<PointDTO> points = new ArrayList<>();
                    JSONArray arr = root.optJSONArray("data");
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) points.add(jsonToPoint(arr.getJSONObject(i)));
                    }
                    data = points;
                }
                case ADMIN_GET_POINT_HISTORY -> {
                    List<PointTransactionDTO> history = new ArrayList<>();
                    JSONArray arr = root.optJSONArray("data");
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            history.add(jsonToPointTransaction(arr.getJSONObject(i)));
                        }
                    }
                    data = history;
                }
                case ADMIN_GET_VOUCHERS -> {
                    if (hasNonNull(root, "data")) {
                        JSONArray arr = root.getJSONArray("data");
                        List<VoucherDTO> vouchers = new ArrayList<>();
                        for (int i = 0; i < arr.length(); i++) vouchers.add(jsonToVoucher(arr.getJSONObject(i)));
                        data = vouchers;
                    }
                }
                default -> {
                }
            }
        }

        SocketPacket packet = new SocketPacket(action, success, message, data, extraData);
        packet.setTableId(tableId);
        return packet;
    }

    // --- Serialization helpers ---

    private static JSONObject orderToJson(OrderDTO o) {
        JSONObject obj = new JSONObject();
        putIfNotNull(obj, "userId", o.getUserId());
        putIfNotNull(obj, "tableId", o.getTableId());
        putIfNotNull(obj, "tableSessionId", o.getTableSessionId());
        if (o.getStatus() != null) obj.put("status", o.getStatus().name());
        putIfNotNull(obj, "note", o.getNote());

        JSONArray arr = new JSONArray();
        if (o.getItems() != null) {
            for (OrderItemDTO item : o.getItems()) {
                JSONObject itemObj = new JSONObject();
                itemObj.put("foodsId", item.getFoodsId());
                itemObj.put("foodsName", item.getFoodsName());
                itemObj.put("unitPrice", item.getUnitPrice());
                itemObj.put("quantity", item.getQuantity());
                itemObj.put("lineTotal", item.getLineTotal());
                if (item.getStatus() != null) itemObj.put("status", item.getStatus().name());
                putIfNotNull(itemObj, "note", item.getNote());
                arr.put(itemObj);
            }
        }
        obj.put("items", arr);
        return obj;
    }

    private static JSONObject billToJson(BillDTO b) {
        JSONObject obj = new JSONObject();
        putIfNotNull(obj, "tableSessionId", b.getTableSessionId());
        putIfNotNull(obj, "userId", b.getUserId());
        obj.put("totalAmount", b.getTotalAmount());
        obj.put("discount", b.getDiscount());
        obj.put("tax", b.getTax());
        if (b.getPaymentMethod() != null) obj.put("paymentMethod", b.getPaymentMethod().name());
        return obj;
    }

    private static JSONObject foodToJson(FoodDTO f) {
        JSONObject obj = new JSONObject();
        putIfNotNull(obj, "id", f.getId());
        putIfNotNull(obj, "categoryId", f.getCategoryId());
        putIfNotNull(obj, "sku", f.getSku());
        putIfNotNull(obj, "name", f.getName());
        putIfNotNull(obj, "price", f.getPrice());
        putIfNotNull(obj, "coverImageUrl", f.getCoverImageUrl());
        putIfNotNull(obj, "isPublished", f.getIsPublished());
        return obj;
    }

    private static JSONObject userToJson(UserDTO u) {
        JSONObject obj = new JSONObject();
        putIfNotNull(obj, "id", u.getId());
        putIfNotNull(obj, "roleID", u.getRoleID());
        putIfNotNull(obj, "roleName", u.getRoleName());
        putIfNotNull(obj, "email", u.getEmail());
        putIfNotNull(obj, "passwordHash", u.getPasswordHash());
        putIfNotNull(obj, "fullName", u.getFullName());
        putIfNotNull(obj, "phone", u.getPhone());
        putIfNotNull(obj, "isActive", u.getIsActive());
        putIfNotNull(obj, "currentPoints", u.getCurrentPoints());
        return obj;
    }

    private static JSONObject voucherToJson(VoucherDTO v) {
        JSONObject obj = new JSONObject();
        putIfNotNull(obj, "id", v.getId());
        putIfNotNull(obj, "code", v.getCode());
        putIfNotNull(obj, "name", v.getName());
        putIfNotNull(obj, "description", v.getDescription());
        if (v.getDiscountType() != null) obj.put("discountType", v.getDiscountType().name());
        putIfNotNull(obj, "discountValue", v.getDiscountValue());
        putIfNotNull(obj, "minOrderAmount", v.getMinOrderAmount());
        putIfNotNull(obj, "maxDiscountAmount", v.getMaxDiscountAmount());
        if (v.getStartAt() != null) obj.put("startAt", v.getStartAt().toString());
        if (v.getEndAt() != null) obj.put("endAt", v.getEndAt().toString());
        putIfNotNull(obj, "usageLimit", v.getUsageLimit());
        putIfNotNull(obj, "usedCount", v.getUsedCount());
        putIfNotNull(obj, "isActive", v.getIsActive());
        return obj;
    }

    // --- Deserialization helpers ---

    private static UserDTO jsonToUser(JSONObject obj) {
        UserDTO u = new UserDTO();
        u.setId(readLong(obj, "id"));
        u.setRoleID(readLong(obj, "roleID"));
        u.setIsActive(readBoolean(obj, "isActive"));
        u.setEmail(obj.optString("email", ""));
        u.setFullName(obj.optString("fullName", ""));
        u.setPhone(obj.optString("phone", ""));
        u.setRoleName(obj.optString("roleName", "CUSTOMER"));
        u.setCurrentPoints(readInt(obj, "currentPoints"));
        return u;
    }

    private static TableDTO jsonToTable(JSONObject obj) {
        TableDTO t = new TableDTO(
                obj.getLong("id"),
                obj.optString("tableName", ""),
                obj.optInt("capacity", 4),
                obj.optBoolean("status", true)
        );
        t.setFloorNumber(obj.optInt("floorNumber", 1));
        t.setActiveSessionId(readLong(obj, "activeSessionId"));
        return t;
    }

    private static BillDTO jsonToBill(JSONObject obj) {
        BillDTO b = new BillDTO();
        b.setId(readLong(obj, "id"));
        b.setTableSessionId(readLong(obj, "tableSessionId"));
        b.setTotalAmount(obj.optBigDecimal("totalAmount", BigDecimal.ZERO));
        b.setDiscount(obj.optBigDecimal("discount", BigDecimal.ZERO));
        b.setTax(obj.optBigDecimal("tax", BigDecimal.ZERO));
        b.setPaymentMethod(readEnum(obj, "paymentMethod", dbEnums.PaymentMethod.class));
        b.setPaymentStatus(readEnum(obj, "paymentStatus", dbEnums.PaymentStatus.class));
        b.setPaidAt(readDateTime(obj, "paidAt"));
        b.setCreatedAt(readDateTime(obj, "createdAt"));
        return b;
    }

    private static PointDTO jsonToPoint(JSONObject o) {
        return new PointDTO(
                o.getLong("userId"),
                o.optInt("currentPoints", 0),
                o.optInt("totalEarnedPoints", 0),
                dbEnums.PointTier.valueOf(o.optString("tier", "DONG")),
                readDateTime(o, "updatedAt")
        );
    }

    private static PointTransactionDTO jsonToPointTransaction(JSONObject o) {
        return new PointTransactionDTO(
                o.getLong("id"),
                o.getLong("userId"),
                readLong(o, "billId"),
                dbEnums.PointTransactionType.valueOf(o.getString("type")),
                o.optInt("pointsChange", 0),
                o.optString("note", ""),
                readDateTime(o, "createdAt")
        );
    }

    private static OrderDTO jsonToOrder(JSONObject obj) {
        OrderDTO o = new OrderDTO();
        o.setId(readLong(obj, "id"));
        o.setTableName(obj.optString("tableName", ""));
        o.setOrderCode(obj.optString("orderCode", ""));
        o.setCreatedAt(readDateTime(obj, "createdAt"));
        o.setParentOrderId(readLong(obj, "parentOrderId"));
        o.setTableId(readLong(obj, "tableId"));
        o.setTableSessionId(readLong(obj, "tableSessionId"));
        o.setSubtotal(obj.optBigDecimal("subtotal", BigDecimal.ZERO));
        o.setDiscountAmount(obj.optBigDecimal("discountAmount", BigDecimal.ZERO));
        o.setTotalAmount(obj.optBigDecimal("totalAmount", BigDecimal.ZERO));
        o.setCheckoutTax(obj.optBigDecimal("checkoutTax", null));
        o.setCheckoutRevision(obj.optString("checkoutRevision", null));
        o.setNote(obj.optString("note", ""));
        if (hasNonNull(obj, "items")) {
            o.setItems(jsonArrayToOrderItemList(obj.getJSONArray("items")));
        }
        return o;
    }

    private static List<OrderItemDTO> jsonArrayToOrderItemList(JSONArray arr) {
        List<OrderItemDTO> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            list.add(new OrderItemDTO(
                    readLong(obj, "id"),
                    readLong(obj, "orderId"),
                    obj.getLong("foodsId"),
                    obj.optString("foodsName", ""),
                    obj.optBigDecimal("unitPrice", BigDecimal.ZERO),
                    obj.optInt("quantity", 1),
                    obj.optBigDecimal("lineTotal", BigDecimal.ZERO),
                    dbEnums.OrderStatus.valueOf(obj.optString("status", "PENDING")),
                    hasNonNull(obj, "note") ? obj.getString("note") : null,
                    readDateTime(obj, "updatedAt"),
                    readDateTime(obj, "completedAt")
            ));
        }
        return list;
    }

    private static List<FoodDTO> jsonArrayToFoodList(JSONArray arr) {
        List<FoodDTO> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            FoodDTO f = new FoodDTO();
            f.setId(obj.getLong("id"));
            f.setSku(obj.optString("sku", ""));
            f.setCategoryId(readLong(obj, "categoryId"));
            f.setName(obj.optString("name", ""));
            f.setDescription(obj.optString("description", ""));
            f.setPrice(obj.optBigDecimal("price", BigDecimal.ZERO));
            f.setIsPublished(obj.optBoolean("isPublished", true));
            f.setCategoryName(obj.optString("categoryName", ""));
            f.setCoverImageUrl(obj.optString("coverImageUrl", ""));
            list.add(f);
        }
        return list;
    }

    private static List<CategoryDTO> jsonArrayToCategoryList(JSONArray arr) {
        List<CategoryDTO> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            list.add(new CategoryDTO(obj.getLong("id"), obj.optString("name", ""), null));
        }
        return list;
    }

    private static VoucherDTO jsonToVoucher(JSONObject obj) {
        VoucherDTO v = new VoucherDTO();
        v.setId(readLong(obj, "id"));
        v.setCode(obj.optString("code", ""));
        v.setName(obj.optString("name", ""));
        v.setDescription(obj.optString("description", ""));
        v.setDiscountType(readEnum(obj, "discountType", dbEnums.DiscountType.class));
        v.setDiscountValue(obj.optBigDecimal("discountValue", BigDecimal.ZERO));
        v.setMinOrderAmount(obj.optBigDecimal("minOrderAmount", BigDecimal.ZERO));
        v.setMaxDiscountAmount(obj.optBigDecimal("maxDiscountAmount", null));
        v.setStartAt(readDateTime(obj, "startAt"));
        v.setEndAt(readDateTime(obj, "endAt"));
        v.setUsageLimit(readInt(obj, "usageLimit"));
        v.setUsedCount(obj.optInt("usedCount", 0));
        v.setIsActive(obj.optBoolean("isActive", true));
        v.setCreatedAt(readDateTime(obj, "createdAt"));
        v.setUpdatedAt(readDateTime(obj, "updatedAt"));
        return v;
    }

    // --- Primitive JSON utilities ---

    private static boolean hasNonNull(JSONObject obj, String key) {
        return obj.has(key) && !obj.isNull(key);
    }

    private static void putIfNotNull(JSONObject obj, String key, Object value) {
        if (value != null) obj.put(key, value);
    }

    private static Long readLong(JSONObject obj, String key) {
        return hasNonNull(obj, key) ? obj.getLong(key) : null;
    }

    private static Integer readInt(JSONObject obj, String key) {
        return hasNonNull(obj, key) ? obj.getInt(key) : null;
    }

    private static Boolean readBoolean(JSONObject obj, String key) {
        return hasNonNull(obj, key) ? obj.getBoolean(key) : null;
    }

    private static LocalDateTime readDateTime(JSONObject obj, String key) {
        return hasNonNull(obj, key) ? LocalDateTime.parse(obj.getString(key)) : null;
    }

    private static <E extends Enum<E>> E readEnum(JSONObject obj, String key, Class<E> enumType) {
        return hasNonNull(obj, key) ? Enum.valueOf(enumType, obj.getString(key)) : null;
    }
}