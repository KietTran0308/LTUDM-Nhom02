package api;

import dto.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SocketJsonMapper {

    // 1. SERVER NHẬN PACKET JSON TỪ CLIENT
    public static SocketPacket fromJsonString(String rawJson) {
        JSONObject root = new JSONObject(rawJson);

        SocketAction action = SocketAction.valueOf(root.getString("action"));
        Long tableId = readLong(root, "tableId");

        Object data = null;
        Object extraData = null;

        switch (action) {
            case REQUEST_PASSWORD_RESET, APPLY_TABLE_VOUCHER -> data = root.optString("data", "");

            case CONFIRM_PASSWORD_RESET -> {
                if (hasNonNull(root, "data")) {
                    JSONObject obj = root.getJSONObject("data");
                    Map<String, String> values = new HashMap<>();
                    values.put("email", obj.optString("email", ""));
                    values.put("otp", obj.optString("otp", ""));
                    values.put("newPassword", obj.optString("newPassword", ""));
                    data = values;
                }
            }

            case LOGIN_STAFF, LOGIN -> {
                data = root.optString("data", "");
                extraData = root.optString("extraData", "");
            }

            case REGISTER, ADMIN_CREATE_ACCOUNT, ADMIN_UPDATE_ACCOUNT -> {
                if (hasNonNull(root, "data")) data = jsonToUser(root.getJSONObject("data"));
            }

            case ORDER_SUBMIT -> {
                if (hasNonNull(root, "data")) data = jsonToOrder(root.getJSONObject("data"));
                if (hasNonNull(root, "extraData")) extraData = root.getString("extraData");
            }

            case ENTER_CUSTOMER_MODE, ADMIN_DELETE_FOOD, ADMIN_GET_POINT_HISTORY -> data = readLong(root, "data");

            case UPDATE_ITEM_STATUS -> {
                data = readLong(root, "data");
                extraData = readEnum(root, "extraData", dbEnums.OrderStatus.class);
            }

            case PAY_TABLE_BILL -> {
                if (hasNonNull(root, "data")) data = jsonToBill(root.getJSONObject("data"));
                extraData = root.optString("extraData", "");
            }

            case ADMIN_GET_BILLS -> {
                if (hasNonNull(root, "data") && root.get("data") instanceof JSONObject filter) {
                    data = filter;
                }
            }

            case ADMIN_ADD_FOOD, ADMIN_UPDATE_FOOD -> {
                if (hasNonNull(root, "data")) data = jsonToFood(root.getJSONObject("data"));
            }

            case ADMIN_LOCK_ACCOUNT, ADMIN_TOGGLE_VOUCHER -> {
                data = readLong(root, "data");
                if (hasNonNull(root, "extraData")) extraData = root.getString("extraData");
            }

            case ADMIN_ADD_VOUCHER, ADMIN_UPDATE_VOUCHER -> {
                if (hasNonNull(root, "data")) data = jsonToVoucher(root.getJSONObject("data"));
            }

            default -> {
            }
        }

        return new SocketPacket(action, tableId, data, extraData);
    }

    // 2. SERVER ĐÓNG GÓI TRẢ VỀ CLIENT (RESPONSE PACKET -> JSON)
    @SuppressWarnings("unchecked")
    public static String toJsonString(SocketPacket packet) {
        JSONObject root = new JSONObject();
        root.put("action", packet.getAction().name());
        root.put("success", packet.isSuccess());
        putIfNotNull(root, "message", packet.getMessage());
        putIfNotNull(root, "tableId", packet.getTableId());

        Object data = packet.getData();
        Object extraData = packet.getExtraData();

        if (packet.isSuccess()) {
            switch (packet.getAction()) {
                case LOGIN_STAFF, LOGIN, REGISTER -> {
                    if (data instanceof UserDTO user) root.put("data", userToJson(user));
                    if (packet.getAction() != SocketAction.REGISTER && extraData instanceof Boolean) {
                        root.put("extraData", extraData);
                    }
                }

                case GET_MENU_AND_CATEGORIES -> {
                    if (data instanceof List<?> list) {
                        root.put("data", foodListToJsonArray((List<FoodDTO>) list));
                    }
                    if (extraData instanceof List<?> list) {
                        root.put("extraData", categoryListToJsonArray((List<CategoryDTO>) list));
                    }
                }

                case ORDER_SUBMIT, ORDER_CREATED -> {
                    if (data instanceof OrderDTO order) root.put("data", orderToJson(order));
                    if (extraData instanceof List<?> list) {
                        root.put("extraData", orderItemListToJsonArray((List<OrderItemDTO>) list));
                    }
                }

                case GET_TABLE_TRACKING -> {
                    if (data instanceof List<?> list) {
                        root.put("data", orderItemListToJsonArray((List<OrderItemDTO>) list));
                    }
                }

                case ITEM_STATUS_CHANGED, UPDATE_ITEM_STATUS -> {
                    putIfNotNull(root, "data", data);
                    if (extraData instanceof dbEnums.OrderStatus status) {
                        root.put("extraData", status.name());
                    }
                }

                case ENTER_CUSTOMER_MODE, ORDER_COMPLETED, SESSION_CLOSED -> putIfNotNull(root, "data", data);

                case OPEN_TABLE_SESSION -> {
                    if (data instanceof Long sessionId) root.put("data", sessionId);
                }

                case GET_KITCHEN_QUEUE -> {
                    if (data instanceof List<?> list) root.put("data", orderListToJson((List<OrderDTO>) list));
                }

                case LIST_TABLES, GET_ALL_TABLES -> {
                    if (data instanceof List<?> list) {
                        root.put("data", tableListToJsonArray((List<TableDTO>) list));
                    }
                }

                case ADMIN_GET_FOODS -> {
                    if (data instanceof List<?> list) root.put("data", foodListToJsonArray((List<FoodDTO>) list));
                }

                case ADMIN_GET_CATEGORIES -> {
                    if (data instanceof List<?> list) {
                        root.put("data", categoryListToJsonArray((List<CategoryDTO>) list));
                    }
                }

                case ADMIN_GET_BILLS -> {
                    if (data instanceof List<?> list) root.put("data", billListToJsonArray((List<BillDTO>) list));
                    if (extraData instanceof JSONObject summary) root.put("extraData", summary);
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

                case ADMIN_GET_POINTS -> {
                    if (data instanceof List<?> list) root.put("data", pointListToJsonArray((List<PointDTO>) list));
                }

                case ADMIN_GET_POINT_HISTORY -> {
                    if (data instanceof List<?> list) {
                        root.put("data", pointTransactionListToJsonArray((List<PointTransactionDTO>) list));
                    }
                }

                case VIEW_SESSION_BILL, GET_TABLE_BILL_PREVIEW, APPLY_TABLE_VOUCHER -> {
                    if (data instanceof OrderDTO order) root.put("data", orderToJson(order));
                }

                case ADMIN_GET_VOUCHERS -> {
                    if (data instanceof List<?> list) {
                        root.put("data", voucherListToJsonArray((List<VoucherDTO>) list));
                    }
                }

                default -> {
                }
            }
        }
        return root.toString();
    }

    // --- Deserialization helpers ---

    private static UserDTO jsonToUser(JSONObject obj) {
        UserDTO u = new UserDTO();
        u.setId(readLong(obj, "id"));
        u.setRoleID(readLong(obj, "roleID"));
        if (hasNonNull(obj, "roleName")) u.setRoleName(obj.getString("roleName"));
        u.setEmail(obj.optString("email", ""));
        u.setPasswordHash(obj.optString("passwordHash", ""));
        u.setFullName(obj.optString("fullName", ""));
        u.setPhone(obj.optString("phone", ""));
        return u;
    }

    private static OrderDTO jsonToOrder(JSONObject obj) {
        OrderDTO o = new OrderDTO();
        o.setUserId(readLong(obj, "userId"));
        o.setTableId(readLong(obj, "tableId"));
        o.setStatus(readEnum(obj, "status", dbEnums.OrderStatus.class));
        o.setNote(obj.optString("note", ""));

        List<OrderItemDTO> items = new ArrayList<>();
        if (hasNonNull(obj, "items")) {
            JSONArray arr = obj.getJSONArray("items");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject itemObj = arr.getJSONObject(i);
                OrderItemDTO item = new OrderItemDTO(
                        null,
                        null,
                        itemObj.getLong("foodsId"),
                        itemObj.optString("foodsName", ""),
                        itemObj.optBigDecimal("unitPrice", BigDecimal.ZERO),
                        itemObj.optInt("quantity", 1),
                        itemObj.optBigDecimal("lineTotal", BigDecimal.ZERO),
                        dbEnums.OrderStatus.PENDING
                );
                item.setNote(itemObj.optString("note", ""));
                items.add(item);
            }
        }
        o.setItems(items);
        return o;
    }

    private static BillDTO jsonToBill(JSONObject obj) {
        BillDTO b = new BillDTO();
        b.setTableSessionId(readLong(obj, "tableSessionId"));
        b.setUserId(readLong(obj, "userId"));
        b.setTotalAmount(obj.optBigDecimal("totalAmount", BigDecimal.ZERO));
        b.setDiscount(obj.optBigDecimal("discount", BigDecimal.ZERO));
        b.setTax(obj.optBigDecimal("tax", BigDecimal.ZERO));
        b.setPointsUsed(obj.optInt("pointsUsed", 0));
        b.setPaymentMethod(readEnum(obj, "paymentMethod", dbEnums.PaymentMethod.class));
        return b;
    }

    private static FoodDTO jsonToFood(JSONObject obj) {
        FoodDTO f = new FoodDTO();
        f.setId(readLong(obj, "id"));
        f.setCategoryId(readLong(obj, "categoryId"));
        f.setSku(obj.optString("sku", ""));
        f.setName(obj.optString("name", ""));
        f.setCoverImageUrl(obj.optString("coverImageUrl", ""));
        f.setPrice(obj.optBigDecimal("price", BigDecimal.ZERO));
        f.setIsPublished(obj.optBoolean("isPublished", true));
        return f;
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
        return v;
    }

    // --- Serialization helpers ---

    private static JSONObject userToJson(UserDTO u) {
        JSONObject obj = new JSONObject();
        putIfNotNull(obj, "id", u.getId());
        putIfNotNull(obj, "roleID", u.getRoleID());
        obj.put("email", u.getEmail());
        obj.put("fullName", u.getFullName());
        obj.put("phone", u.getPhone());
        putIfNotNull(obj, "roleName", u.getRoleName());
        putIfNotNull(obj, "currentPoints", u.getCurrentPoints());
        putIfNotNull(obj, "isActive", u.getIsActive());
        return obj;
    }

    private static JSONObject orderToJson(OrderDTO o) {
        JSONObject obj = new JSONObject();
        putIfNotNull(obj, "id", o.getId());
        putIfNotNull(obj, "orderCode", o.getOrderCode());
        putIfNotNull(obj, "parentOrderId", o.getParentOrderId());
        putIfNotNull(obj, "tableId", o.getTableId());
        putIfNotNull(obj, "tableSessionId", o.getTableSessionId());
        if (o.getCreatedAt() != null) obj.put("createdAt", o.getCreatedAt().toString());
        putIfNotNull(obj, "tableName", o.getTableName());
        putIfNotNull(obj, "subtotal", o.getSubtotal());
        putIfNotNull(obj, "discountAmount", o.getDiscountAmount());
        putIfNotNull(obj, "totalAmount", o.getTotalAmount());
        putIfNotNull(obj, "checkoutTax", o.getCheckoutTax());
        putIfNotNull(obj, "checkoutRevision", o.getCheckoutRevision());
        putIfNotNull(obj, "note", o.getNote());
        if (o.getItems() != null) obj.put("items", orderItemListToJsonArray(o.getItems()));
        return obj;
    }

    private static JSONArray orderListToJson(List<OrderDTO> list) {
        JSONArray arr = new JSONArray();
        for (OrderDTO order : list) arr.put(orderToJson(order));
        return arr;
    }

    private static JSONArray orderItemListToJsonArray(List<OrderItemDTO> list) {
        JSONArray arr = new JSONArray();
        if (list == null) return arr;
        for (OrderItemDTO item : list) {
            JSONObject obj = new JSONObject();
            putIfNotNull(obj, "id", item.getId());
            putIfNotNull(obj, "orderId", item.getOrderId());
            putIfNotNull(obj, "foodsId", item.getFoodsId());
            obj.put("foodsName", item.getFoodsName());
            obj.put("unitPrice", item.getUnitPrice());
            obj.put("quantity", item.getQuantity());
            obj.put("lineTotal", item.getLineTotal());
            if (item.getStatus() != null) obj.put("status", item.getStatus().name());
            putIfNotNull(obj, "note", item.getNote());
            if (item.getUpdatedAt() != null) obj.put("updatedAt", item.getUpdatedAt().toString());
            if (item.getCompletedAt() != null) obj.put("completedAt", item.getCompletedAt().toString());
            arr.put(obj);
        }
        return arr;
    }

    private static JSONArray foodListToJsonArray(List<FoodDTO> list) {
        JSONArray arr = new JSONArray();
        if (list == null) return arr;
        for (FoodDTO f : list) {
            JSONObject obj = new JSONObject();
            obj.put("id", f.getId());
            obj.put("sku", f.getSku());
            putIfNotNull(obj, "categoryId", f.getCategoryId());
            obj.put("name", f.getName());
            obj.put("description", f.getDescription());
            obj.put("price", f.getPrice());
            obj.put("isPublished", f.getIsPublished());
            obj.put("categoryName", f.getCategoryName());
            obj.put("coverImageUrl", f.getCoverImageUrl());
            arr.put(obj);
        }
        return arr;
    }

    private static JSONArray categoryListToJsonArray(List<CategoryDTO> list) {
        JSONArray arr = new JSONArray();
        if (list == null) return arr;
        for (CategoryDTO c : list) {
            JSONObject obj = new JSONObject();
            obj.put("id", c.getId());
            obj.put("name", c.getName());
            arr.put(obj);
        }
        return arr;
    }

    private static JSONArray tableListToJsonArray(List<TableDTO> list) {
        JSONArray arr = new JSONArray();
        if (list == null) return arr;
        for (TableDTO t : list) {
            JSONObject obj = new JSONObject();
            obj.put("id", t.getId());
            obj.put("tableName", t.getTableName());
            obj.put("capacity", t.getCapacity());
            obj.put("floorNumber", t.getFloorNumber());
            obj.put("status", t.getStatus());
            putIfNotNull(obj, "activeSessionId", t.getActiveSessionId());
            arr.put(obj);
        }
        return arr;
    }

    private static JSONArray pointListToJsonArray(List<PointDTO> list) {
        JSONArray arr = new JSONArray();
        if (list == null) return arr;
        for (PointDTO p : list) {
            JSONObject obj = new JSONObject();
            obj.put("userId", p.getUserId());
            obj.put("currentPoints", p.getCurrentPoints());
            obj.put("totalEarnedPoints", p.getTotalEarnedPoints());
            if (p.getTier() != null) obj.put("tier", p.getTier().name());
            if (p.getUpdatedAt() != null) obj.put("updatedAt", p.getUpdatedAt().toString());
            arr.put(obj);
        }
        return arr;
    }

    private static JSONArray pointTransactionListToJsonArray(List<PointTransactionDTO> list) {
        JSONArray arr = new JSONArray();
        if (list == null) return arr;
        for (PointTransactionDTO t : list) {
            JSONObject obj = new JSONObject();
            obj.put("id", t.getId());
            obj.put("userId", t.getUserId());
            putIfNotNull(obj, "billId", t.getBillId());
            if (t.getType() != null) obj.put("type", t.getType().name());
            obj.put("pointsChange", t.getPointsChange());
            putIfNotNull(obj, "note", t.getNote());
            if (t.getCreatedAt() != null) obj.put("createdAt", t.getCreatedAt().toString());
            arr.put(obj);
        }
        return arr;
    }

    private static JSONArray billListToJsonArray(List<BillDTO> list) {
        JSONArray result = new JSONArray();
        for (BillDTO b : list) {
            JSONObject obj = new JSONObject();
            putIfNotNull(obj, "id", b.getId());
            putIfNotNull(obj, "tableSessionId", b.getTableSessionId());
            putIfNotNull(obj, "userId", b.getUserId());
            putIfNotNull(obj, "pointsUsed", b.getPointsUsed());
            obj.put("totalAmount", b.getTotalAmount());
            obj.put("discount", b.getDiscount());
            obj.put("tax", b.getTax());
            if (b.getPaymentMethod() != null) obj.put("paymentMethod", b.getPaymentMethod().name());
            if (b.getPaymentStatus() != null) obj.put("paymentStatus", b.getPaymentStatus().name());
            if (b.getPaidAt() != null) obj.put("paidAt", b.getPaidAt().toString());
            if (b.getCreatedAt() != null) obj.put("createdAt", b.getCreatedAt().toString());
            result.put(obj);
        }
        return result;
    }

    private static JSONArray voucherListToJsonArray(List<VoucherDTO> list) {
        JSONArray arr = new JSONArray();
        if (list == null) return arr;
        for (VoucherDTO v : list) {
            JSONObject obj = new JSONObject();
            putIfNotNull(obj, "id", v.getId());
            obj.put("code", v.getCode());
            putIfNotNull(obj, "name", v.getName());
            putIfNotNull(obj, "description", v.getDescription());
            if (v.getDiscountType() != null) obj.put("discountType", v.getDiscountType().name());
            obj.put("discountValue", v.getDiscountValue());
            obj.put("minOrderAmount", v.getMinOrderAmount() != null ? v.getMinOrderAmount() : BigDecimal.ZERO);
            putIfNotNull(obj, "maxDiscountAmount", v.getMaxDiscountAmount());
            if (v.getStartAt() != null) obj.put("startAt", v.getStartAt().toString());
            if (v.getEndAt() != null) obj.put("endAt", v.getEndAt().toString());
            putIfNotNull(obj, "usageLimit", v.getUsageLimit());
            obj.put("usedCount", v.getUsedCount() != null ? v.getUsedCount() : 0);
            obj.put("isActive", Boolean.TRUE.equals(v.getIsActive()));
            if (v.getCreatedAt() != null) obj.put("createdAt", v.getCreatedAt().toString());
            if (v.getUpdatedAt() != null) obj.put("updatedAt", v.getUpdatedAt().toString());
            arr.put(obj);
        }
        return arr;
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

    private static LocalDateTime readDateTime(JSONObject obj, String key) {
        return hasNonNull(obj, key) ? LocalDateTime.parse(obj.getString(key)) : null;
    }

    private static <E extends Enum<E>> E readEnum(JSONObject obj, String key, Class<E> enumType) {
        return hasNonNull(obj, key) ? Enum.valueOf(enumType, obj.getString(key)) : null;
    }
}