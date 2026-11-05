package api;

import dto.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Đặt tại: ServerApp -> package api
 * Chuyên giải mã Request (Client -> Server) và đóng gói Response (Server -> Client)
 */
public class SocketJsonMapper {

    // =========================================================================
    // 1. CHIỀU SERVER NHẬN YÊU CẦU TỪ CLIENT (JSON -> REQUEST PACKET)
    // =========================================================================
    public static SocketPacket fromJsonString(String rawJson) {
        JSONObject root = new JSONObject(rawJson);

        SocketAction action = SocketAction.valueOf(root.getString("action"));
        Long tableId = root.has("tableId") && !root.isNull("tableId") ? root.getLong("tableId") : null;

        Object data = null;
        Object extraData = null;

        switch (action) {
            case REQUEST_PASSWORD_RESET:
                data = root.optString("data", "");
                break;

            case CONFIRM_PASSWORD_RESET:
                if (root.has("data") && !root.isNull("data")) {
                    JSONObject obj = root.getJSONObject("data");
                    java.util.Map<String, String> values = new java.util.HashMap<>();
                    values.put("email", obj.optString("email", ""));
                    values.put("otp", obj.optString("otp", ""));
                    values.put("newPassword", obj.optString("newPassword", ""));
                    data = values;
                }
                break;

            case LOGIN_STAFF:
            case LOGIN:
                data = root.optString("data", "");          // emailOrPhone
                extraData = root.optString("extraData", ""); // password
                break;

            case REGISTER:
                if (root.has("data") && !root.isNull("data")) {
                    data = jsonToUser(root.getJSONObject("data"));
                }
                break;

            case ORDER_SUBMIT:
            case PLACE_ORDER:
                if (root.has("data") && !root.isNull("data")) {
                    data = jsonToOrder(root.getJSONObject("data"));
                }
                if (root.has("extraData") && !root.isNull("extraData")) {
                    extraData = root.getString("extraData"); // voucherCode
                }
                break;

            case ENTER_CUSTOMER_MODE:
                if (root.has("data") && !root.isNull("data")) data = root.getLong("data");
                break;
            case REGISTER_TABLE:
                data = root.optString("data", ""); // staff pairing code
                break;

            case UPDATE_ITEM_STATUS:
            case MARK_ITEM_SERVED:
            case SET_KITCHEN_ITEM_STATUS:
                if (root.has("data") && !root.isNull("data")) data = root.getLong("data");
                if (root.has("extraData") && !root.isNull("extraData"))
                    extraData = OrderStatus.valueOf(root.getString("extraData"));
                break;

            case CALL_STAFF:
                data = root.optString("data", "");
                break;

            case PAY_TABLE_BILL:
                if (root.has("data") && !root.isNull("data"))
                    data = jsonToBill(root.getJSONObject("data"));
                break;

            case ADMIN_ADD_FOOD:
            case ADMIN_UPDATE_FOOD:
                if (root.has("data") && !root.isNull("data"))
                    data = jsonToFood(root.getJSONObject("data"));
                break;
            case ADMIN_ADD_INGREDIENT:
            case ADMIN_RESTOCK_INGREDIENT:
                if (root.has("data") && !root.isNull("data"))
                    data = jsonToIngredient(root.getJSONObject("data"));
                break;
            case ADMIN_DELETE_FOOD:
                if (root.has("data") && !root.isNull("data")) data = root.getLong("data");
                break;

            default:
                break;
        }

        return new SocketPacket(action, tableId, data, extraData);
    }

    // =========================================================================
    // 2. CHIỀU SERVER ĐÓNG GÓI TRẢ VỀ CLIENT (RESPONSE PACKET -> JSON)
    // =========================================================================
    @SuppressWarnings("unchecked")
    public static String toJsonString(SocketPacket packet) {
        JSONObject root = new JSONObject();
        root.put("action", packet.getAction().name());
        root.put("success", packet.isSuccess());
        if (packet.getMessage() != null) root.put("message", packet.getMessage());
        if (packet.getTableId() != null) root.put("tableId", packet.getTableId());

        Object data = packet.getData();
        Object extraData = packet.getExtraData();

        if (packet.isSuccess()) {
            switch (packet.getAction()) {
                case LOGIN_STAFF:
                case LOGIN:
                case REGISTER:
                    if (data instanceof UserDTO) {
                        root.put("data", userToJson((UserDTO) data));
                    }
                    if (packet.getAction() != SocketAction.REGISTER && extraData instanceof Boolean)
                        root.put("extraData", extraData);
                    break;

                case GET_MENU_AND_CATEGORIES:
                    if (data instanceof List<?>) {
                        root.put("data", foodListToJsonArray((List<FoodDTO>) data));
                    }
                    if (extraData instanceof List<?>) {
                        root.put("extraData", categoryListToJsonArray((List<CategoryDTO>) extraData));
                    }
                    break;

                case ORDER_SUBMIT:
                case ORDER_CREATED:
                case PLACE_ORDER:
                    if (data instanceof OrderDTO) {
                        root.put("data", orderToJson((OrderDTO) data));
                    }
                    if (extraData instanceof List<?>) {
                        root.put("extraData", orderItemListToJsonArray((List<OrderItemDTO>) extraData));
                    }
                    break;

                case GET_TABLE_TRACKING:
                    if (data instanceof List<?>) {
                        root.put("data", orderItemListToJsonArray((List<OrderItemDTO>) data));
                    }
                    break;

                case ITEM_STATUS_CHANGED:
                case KITCHEN_UPDATE_STATUS:
                    if (data != null) root.put("data", data); // Long orderItemId
                    if (extraData instanceof OrderStatus) {
                        root.put("extraData", ((OrderStatus) extraData).name());
                    }
                    break;

                case ENTER_CUSTOMER_MODE:
                case ORDER_COMPLETED:
                case SESSION_CLOSED:
                    if (data != null) root.put("data", data);
                    break;

                case OPEN_TABLE_SESSION:
                    if (data instanceof Long) root.put("data", data);
                    break;

                case GET_KITCHEN_QUEUE:
                    if (data instanceof List<?>) root.put("data", orderListToJson((List<OrderDTO>) data));
                    break;

                case UPDATE_ITEM_STATUS:
                case MARK_ITEM_SERVED:
                case SET_KITCHEN_ITEM_STATUS:
                    if (data != null) root.put("data", data);
                    if (extraData instanceof OrderStatus) root.put("extraData", ((OrderStatus) extraData).name());
                    break;

                case LIST_TABLES:
                case GET_ALL_TABLES:
                case PAY_TABLE_BILL:
                    if (data instanceof List<?>) {
                        root.put("data", tableListToJsonArray((List<TableDTO>) data));
                    }
                    break;

                case ADMIN_GET_FOODS:
                    if (data instanceof List<?>) root.put("data", foodListToJsonArray((List<FoodDTO>) data));
                    break;
                case ADMIN_GET_CATEGORIES:
                    if (data instanceof List<?>) root.put("data", categoryListToJsonArray((List<CategoryDTO>) data));
                    break;
                case ADMIN_GET_INGREDIENTS:
                    if (data instanceof List<?>) root.put("data", ingredientListToJsonArray((List<IngredientDTO>) data));
                    break;
                case ADMIN_GET_BILLS:
                    if (data instanceof List<?>) root.put("data", billListToJsonArray((List<BillDTO>) data));
                    break;

                case VIEW_SESSION_BILL:
                case GET_TABLE_BILL_PREVIEW:
                    if (data instanceof OrderDTO) {
                        root.put("data", orderToJson((OrderDTO) data));
                    }
                    break;

                default:
                    break;
            }
        }
        return root.toString();
    }

    // --- Các hàm Helper của Server ---
    private static UserDTO jsonToUser(JSONObject obj) {
        UserDTO u = new UserDTO();
        u.setEmail(obj.optString("email", ""));
        u.setPasswordHash(obj.optString("passwordHash", ""));
        u.setFullName(obj.optString("fullName", ""));
        u.setPhone(obj.optString("phone", ""));
        return u;
    }

    private static JSONObject userToJson(UserDTO u) {
        JSONObject obj = new JSONObject();
        if (u.getId() != null) obj.put("id", u.getId());
        if (u.getRoleID() != null) obj.put("roleID", u.getRoleID());
        obj.put("email", u.getEmail());
        obj.put("fullName", u.getFullName());
        obj.put("phone", u.getPhone());
        if (u.getRoleName() != null) obj.put("roleName", u.getRoleName());
        return obj;
    }

    private static OrderDTO jsonToOrder(JSONObject obj) {
        OrderDTO o = new OrderDTO();
        if (obj.has("userId") && !obj.isNull("userId")) o.setUserId(obj.getLong("userId"));
        if (obj.has("tableId") && !obj.isNull("tableId")) o.setTableId(obj.getLong("tableId"));
        if (obj.has("orderType") && !obj.isNull("orderType")) o.setOrderType(OrderType.valueOf(obj.getString("orderType")));
        if (obj.has("status") && !obj.isNull("status")) o.setStatus(OrderStatus.valueOf(obj.getString("status")));
        o.setNote(obj.optString("note", ""));

        List<OrderItemDTO> items = new ArrayList<>();
        if (obj.has("items") && !obj.isNull("items")) {
            JSONArray arr = obj.getJSONArray("items");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject itemObj = arr.getJSONObject(i);
                items.add(new OrderItemDTO(
                        null, null,
                        itemObj.getLong("foodsId"),
                        itemObj.optString("foodsName", ""),
                        itemObj.optBigDecimal("unitPrice", BigDecimal.ZERO),
                        itemObj.optInt("quantity", 1),
                        itemObj.optBigDecimal("lineTotal", BigDecimal.ZERO),
                        OrderStatus.PENDING
                ));
            }
        }
        o.setItems(items);
        return o;
    }

    private static JSONObject orderToJson(OrderDTO o) {
        JSONObject obj = new JSONObject();
        if (o.getId() != null) obj.put("id", o.getId());
        if (o.getOrderCode() != null) obj.put("orderCode", o.getOrderCode());
        if (o.getParentOrderId() != null) obj.put("parentOrderId", o.getParentOrderId());
        if (o.getTableId() != null) obj.put("tableId", o.getTableId());
        if (o.getTableSessionId() != null) obj.put("tableSessionId", o.getTableSessionId());
        if (o.getTableSessionId() != null) obj.put("tableSessionId", o.getTableSessionId());
        if (o.getSubtotal() != null) obj.put("subtotal", o.getSubtotal());
        if (o.getDiscountAmount() != null) obj.put("discountAmount", o.getDiscountAmount());
        if (o.getTotalAmount() != null) obj.put("totalAmount", o.getTotalAmount());
        if (o.getNote() != null) obj.put("note", o.getNote());
        if (o.getItems() != null) {
            obj.put("items", orderItemListToJsonArray(o.getItems()));
        }
        return obj;
    }

    private static JSONArray orderListToJson(List<OrderDTO> list) {
        JSONArray arr = new JSONArray();
        for (OrderDTO order : list) arr.put(orderToJson(order));
        return arr;
    }

    private static JSONArray orderItemListToJsonArray(List<OrderItemDTO> list) {
        JSONArray arr = new JSONArray();
        if (list != null) {
            for (OrderItemDTO item : list) {
                JSONObject obj = new JSONObject();
                if (item.getId() != null) obj.put("id", item.getId());
                if (item.getOrderId() != null) obj.put("orderId", item.getOrderId());
                if (item.getFoodsId() != null) obj.put("foodsId", item.getFoodsId());
                obj.put("foodsName", item.getFoodsName());
                obj.put("unitPrice", item.getUnitPrice());
                obj.put("quantity", item.getQuantity());
                obj.put("lineTotal", item.getLineTotal());
                if (item.getStatus() != null) obj.put("status", item.getStatus().name());
                arr.put(obj);
            }
        }
        return arr;
    }

    private static JSONArray foodListToJsonArray(List<FoodDTO> list) {
        JSONArray arr = new JSONArray();
        if (list != null) {
            for (FoodDTO f : list) {
                JSONObject obj = new JSONObject();
                obj.put("id", f.getId());
                obj.put("sku", f.getSku());
                if (f.getCategoryId() != null) obj.put("categoryId", f.getCategoryId());
                obj.put("name", f.getName());
                obj.put("slug", f.getSlug());
                obj.put("description", f.getDescription());
                obj.put("price", f.getPrice());
                obj.put("isPublished", f.getIsPublished());
                obj.put("categoryName", f.getCategoryName());
                obj.put("coverImageUrl", f.getCoverImageUrl());
                arr.put(obj);
            }
        }
        return arr;
    }

    private static JSONArray categoryListToJsonArray(List<CategoryDTO> list) {
        JSONArray arr = new JSONArray();
        if (list != null) {
            for (CategoryDTO c : list) {
                JSONObject obj = new JSONObject();
                obj.put("id", c.getId());
                obj.put("name", c.getName());
                obj.put("slug", c.getSlug());
                obj.put("description", c.getDescription());
                arr.put(obj);
            }
        }
        return arr;
    }

    private static JSONArray tableListToJsonArray(List<TableDTO> list) {
        JSONArray arr = new JSONArray();
        if (list != null) {
            for (TableDTO t : list) {
                JSONObject obj = new JSONObject();
                obj.put("id", t.getId());
                obj.put("tableName", t.getTableName());
                obj.put("capacity", t.getCapacity());
                obj.put("status", t.getStatus());
                if (t.getActiveSessionId() != null) obj.put("activeSessionId", t.getActiveSessionId());
                arr.put(obj);
            }
        }
        return arr;
    }

    private static BillDTO jsonToBill(JSONObject obj) {
        BillDTO b = new BillDTO();

        if (obj.has("tableSessionId") && !obj.isNull("tableSessionId")) {
            b.setTableSessionId(obj.getLong("tableSessionId"));
        }

        b.setTotalAmount(obj.optBigDecimal("totalAmount", BigDecimal.ZERO));
        b.setDiscount(obj.optBigDecimal("discount", BigDecimal.ZERO));
        b.setTax(obj.optBigDecimal("tax", BigDecimal.ZERO));

        if (obj.has("paymentMethod") && !obj.isNull("paymentMethod")) {
            b.setPaymentMethod(PaymentMethod.valueOf(obj.getString("paymentMethod")));
        }

        return b;
    }
    private static FoodDTO jsonToFood(JSONObject obj) {
        FoodDTO f = new FoodDTO();
        if (obj.has("id") && !obj.isNull("id")) f.setId(obj.getLong("id"));
        if (obj.has("categoryId") && !obj.isNull("categoryId")) f.setCategoryId(obj.getLong("categoryId"));
        f.setSku(obj.optString("sku", ""));
        f.setName(obj.optString("name", ""));
        f.setCoverImageUrl(obj.optString("coverImageUrl", ""));
        f.setPrice(obj.optBigDecimal("price", BigDecimal.ZERO));
        f.setIsPublished(obj.optBoolean("isPublished", true));
        return f;
    }

    private static IngredientDTO jsonToIngredient(JSONObject obj) {
        IngredientDTO i = new IngredientDTO();
        if (obj.has("id") && !obj.isNull("id")) i.setId(obj.getLong("id"));
        i.setName(obj.optString("name", ""));
        i.setUnit(obj.optString("unit", ""));
        i.setStockQuantity(obj.optBigDecimal("stockQuantity", BigDecimal.ZERO));
        i.setMinStockLevel(obj.optBigDecimal("minStockLevel", BigDecimal.ZERO));
        return i;
    }

    private static JSONArray ingredientListToJsonArray(List<IngredientDTO> list) {
        JSONArray result = new JSONArray();
        for (IngredientDTO i : list) {
            JSONObject obj = new JSONObject();
            if (i.getId() != null) obj.put("id", i.getId());
            obj.put("name", i.getName());
            obj.put("unit", i.getUnit());
            obj.put("stockQuantity", i.getStockQuantity());
            obj.put("minStockLevel", i.getMinStockLevel());
            result.put(obj);
        }
        return result;
    }

    private static JSONArray billListToJsonArray(List<BillDTO> list) {
        JSONArray result = new JSONArray();
        for (BillDTO b : list) {
            JSONObject obj = new JSONObject();
            if (b.getId() != null) obj.put("id", b.getId());
            if (b.getTableSessionId() != null) obj.put("tableSessionId", b.getTableSessionId());
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
}