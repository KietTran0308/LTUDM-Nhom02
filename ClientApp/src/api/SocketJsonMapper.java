package api;

import dto.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Đặt tại: ClientApp -> package api
 * Chuyên đóng gói Request (Client -> Server) và giải mã Response (Server -> Client)
 */
public class SocketJsonMapper {

    // =========================================================================
    // 1. CHIỀU CLIENT GỬI LÊN SERVER (REQUEST -> JSON)
    // =========================================================================
    public static String toJsonString(SocketPacket packet) {
        JSONObject root = new JSONObject();
        root.put("action", packet.getAction().name());
        if (packet.getTableId() != null) root.put("tableId", packet.getTableId());

        Object data = packet.getData();
        Object extraData = packet.getExtraData();

        switch (packet.getAction()) {
            case REQUEST_PASSWORD_RESET:
                if (data != null) root.put("data", data.toString());
                break;

            case CONFIRM_PASSWORD_RESET:
                if (data instanceof Map<?, ?>) {
                    Map<?, ?> payload = (Map<?, ?>) data;
                    JSONObject body = new JSONObject();
                    body.put("email", payload.get("email"));
                    body.put("otp", payload.get("otp"));
                    body.put("newPassword", payload.get("newPassword"));
                    root.put("data", body);
                }
                break;

            case LOGIN_STAFF:
            case LOGIN:
                if (data != null) root.put("data", data.toString());           // emailOrPhone
                if (extraData != null) root.put("extraData", extraData.toString()); // password
                break;

            case REGISTER:
                if (data instanceof UserDTO) {
                    root.put("data", userToJson((UserDTO) data));
                }
                break;

            case ORDER_SUBMIT:
            case PLACE_ORDER:
                if (data instanceof OrderDTO) {
                    root.put("data", orderToJson((OrderDTO) data));
                }
                if (extraData != null) {
                    root.put("extraData", extraData.toString()); // voucherCode
                }
                break;

            case ENTER_CUSTOMER_MODE:
                if (data instanceof Long) root.put("data", data);
                break;

            case REGISTER_TABLE:
                if (data != null) root.put("data", data.toString()); // session token
                break;

            case UPDATE_ITEM_STATUS:
            case MARK_ITEM_SERVED:
            case SET_KITCHEN_ITEM_STATUS:
                if (data != null) root.put("data", data);  // itemId
                if (extraData instanceof OrderStatus) root.put("extraData", ((OrderStatus) extraData).name());
                break;

            case CALL_STAFF:
                if (data != null) root.put("data", data.toString()); // Nội dung gọi nhân viên
                break;

            case PAY_TABLE_BILL:
                if (data instanceof BillDTO) root.put("data", billToJson((BillDTO) data));
                break;

            case ADMIN_ADD_FOOD:
            case ADMIN_UPDATE_FOOD:
                if (data instanceof FoodDTO) root.put("data", foodToJson((FoodDTO) data));
                break;
            case ADMIN_ADD_INGREDIENT:
            case ADMIN_RESTOCK_INGREDIENT:
                if (data instanceof IngredientDTO) root.put("data", ingredientToJson((IngredientDTO) data));
                break;
            case ADMIN_DELETE_FOOD:
                if (data instanceof Long) root.put("data", data);
                break;

            default:
                // REGISTER_TABLE, GET_MENU_AND_CATEGORIES, GET_TABLE_TRACKING,
                // GET_ALL_TABLES, GET_TABLE_BILL_PREVIEW chỉ cần action và tableId
                break;
        }
        return root.toString();
    }

    // =========================================================================
    // 2. CHIỀU CLIENT NHẬN TỪ SERVER VỀ (JSON -> RESPONSE PACKET)
    // =========================================================================
    public static SocketPacket fromJsonString(String rawJson) {
        JSONObject root = new JSONObject(rawJson);

        SocketAction action = SocketAction.valueOf(root.getString("action"));
        boolean success = root.optBoolean("success", false);
        String message = root.optString("message", "");
        Long tableId = root.has("tableId") && !root.isNull("tableId") ? root.getLong("tableId") : null;

        Object data = null;
        Object extraData = null;

        if (success) {
            switch (action) {
                case LOGIN_STAFF:
                case LOGIN:
                case REGISTER:
                    if (root.has("data") && !root.isNull("data")) {
                        data = jsonToUser(root.getJSONObject("data"));
                    }
                    if ((action == SocketAction.LOGIN || action == SocketAction.LOGIN_STAFF)
                            && root.has("extraData") && !root.isNull("extraData")) {
                        extraData = root.optBoolean("extraData", false);
                    }
                    break;

                case GET_MENU_AND_CATEGORIES:
                    if (root.has("data") && !root.isNull("data")) {
                        data = jsonArrayToFoodList(root.getJSONArray("data"));
                    }
                    if (root.has("extraData") && !root.isNull("extraData")) {
                        extraData = jsonArrayToCategoryList(root.getJSONArray("extraData"));
                    }
                    break;

                case ORDER_SUBMIT:
                case ORDER_CREATED:
                case PLACE_ORDER:
                    if (root.has("data") && !root.isNull("data")) {
                        data = jsonToOrder(root.getJSONObject("data"));
                    }
                    if (root.has("extraData") && !root.isNull("extraData")) {
                        extraData = jsonArrayToOrderItemList(root.getJSONArray("extraData"));
                    }
                    break;

                case GET_TABLE_TRACKING:
                    if (root.has("data") && !root.isNull("data")) {
                        data = jsonArrayToOrderItemList(root.getJSONArray("data"));
                    }
                    break;

                case ITEM_STATUS_CHANGED:
                case KITCHEN_UPDATE_STATUS:
                    if (root.has("data") && !root.isNull("data")) {
                        data = root.getLong("data"); // orderItemId
                    }
                    if (root.has("extraData") && !root.isNull("extraData")) {
                        extraData = OrderStatus.valueOf(root.getString("extraData"));
                    }
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
                case ENTER_CUSTOMER_MODE:
                case ORDER_COMPLETED:
                case SESSION_CLOSED:
                    if (root.has("data") && !root.isNull("data")) data = root.getLong("data");
                    break;
                case OPEN_TABLE_SESSION:
                    if (root.has("data") && !root.isNull("data")) data = root.getLong("data");
                    break;
                case GET_KITCHEN_QUEUE:
                    data = new ArrayList<OrderDTO>();
                    if (root.has("data") && !root.isNull("data")) {
                        JSONArray queue = root.getJSONArray("data");
                        List<OrderDTO> orders = new ArrayList<>();
                        for (int i=0;i<queue.length();i++) orders.add(jsonToOrder(queue.getJSONObject(i)));
                        data = orders;
                    }
                    break;
                case LIST_TABLES:
                case GET_ALL_TABLES:
                case PAY_TABLE_BILL:
                    if (root.has("data") && !root.isNull("data")) {
                        data = jsonArrayToTableList(root.getJSONArray("data"));
                    }
                    break;

                case ADMIN_GET_FOODS:
                    if (root.has("data") && !root.isNull("data"))
                        data = jsonArrayToFoodList(root.getJSONArray("data"));
                    break;
                case ADMIN_GET_CATEGORIES:
                    if (root.has("data") && !root.isNull("data"))
                        data = jsonArrayToCategoryList(root.getJSONArray("data"));
                    break;
                case ADMIN_GET_INGREDIENTS:
                    if (root.has("data") && !root.isNull("data"))
                        data = jsonArrayToIngredientList(root.getJSONArray("data"));
                    break;
                case ADMIN_GET_BILLS:
                    if (root.has("data") && !root.isNull("data"))
                        data = jsonArrayToBillList(root.getJSONArray("data"));
                    break;

                case VIEW_SESSION_BILL:
                case GET_TABLE_BILL_PREVIEW:
                    if (root.has("data") && !root.isNull("data")) {
                        data = jsonToOrder(root.getJSONObject("data"));
                    }
                    break;

                default:
                    break;
            }
        }

        SocketPacket packet = new SocketPacket(action, success, message, data, extraData);
        packet.setTableId(tableId);
        return packet;
    }

    // --- Các hàm Helper của Client ---
    private static JSONObject userToJson(UserDTO u) {
        JSONObject obj = new JSONObject();
        obj.put("email", u.getEmail());
        obj.put("passwordHash", u.getPasswordHash());
        obj.put("fullName", u.getFullName());
        obj.put("phone", u.getPhone());
        return obj;
    }

    private static UserDTO jsonToUser(JSONObject obj) {
        UserDTO u = new UserDTO();
        if (obj.has("id") && !obj.isNull("id")) u.setId(obj.getLong("id"));
        if (obj.has("roleID") && !obj.isNull("roleID")) u.setRoleID(obj.getLong("roleID"));
        u.setEmail(obj.optString("email", ""));
        u.setFullName(obj.optString("fullName", ""));
        u.setPhone(obj.optString("phone", ""));
        u.setRoleName(obj.optString("roleName", "CUSTOMER"));
        return u;
    }

    private static JSONObject orderToJson(OrderDTO o) {
        JSONObject obj = new JSONObject();
        if (o.getUserId() != null) obj.put("userId", o.getUserId());
        if (o.getTableId() != null) obj.put("tableId", o.getTableId());
        if (o.getTableSessionId() != null) obj.put("tableSessionId", o.getTableSessionId());
        if (o.getOrderType() != null) obj.put("orderType", o.getOrderType().name());
        if (o.getStatus() != null) obj.put("status", o.getStatus().name());
        if (o.getNote() != null) obj.put("note", o.getNote());

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
                arr.put(itemObj);
            }
        }
        obj.put("items", arr);
        return obj;
    }

    private static OrderDTO jsonToOrder(JSONObject obj) {
        OrderDTO o = new OrderDTO();
        if (obj.has("id") && !obj.isNull("id")) o.setId(obj.getLong("id"));
        o.setOrderCode(obj.optString("orderCode", ""));
        if (obj.has("parentOrderId") && !obj.isNull("parentOrderId")) o.setParentOrderId(obj.getLong("parentOrderId"));
        if (obj.has("tableId") && !obj.isNull("tableId")) o.setTableId(obj.getLong("tableId"));
        if (obj.has("tableSessionId") && !obj.isNull("tableSessionId")) o.setTableSessionId(obj.getLong("tableSessionId"));
        o.setSubtotal(obj.optBigDecimal("subtotal", BigDecimal.ZERO));
        o.setDiscountAmount(obj.optBigDecimal("discountAmount", BigDecimal.ZERO));
        o.setTotalAmount(obj.optBigDecimal("totalAmount", BigDecimal.ZERO));
        o.setNote(obj.optString("note", ""));
        if (obj.has("items") && !obj.isNull("items")) {
            o.setItems(jsonArrayToOrderItemList(obj.getJSONArray("items")));
        }
        return o;
    }

    private static List<OrderItemDTO> jsonArrayToOrderItemList(JSONArray arr) {
        List<OrderItemDTO> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            list.add(new OrderItemDTO(
                    obj.has("id") && !obj.isNull("id") ? obj.getLong("id") : null,
                    obj.has("orderId") && !obj.isNull("orderId") ? obj.getLong("orderId") : null,
                    obj.getLong("foodsId"),
                    obj.optString("foodsName", ""),
                    obj.optBigDecimal("unitPrice", BigDecimal.ZERO),
                    obj.optInt("quantity", 1),
                    obj.optBigDecimal("lineTotal", BigDecimal.ZERO),
                    OrderStatus.valueOf(obj.optString("status", "PENDING"))
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
            if (obj.has("categoryId") && !obj.isNull("categoryId")) f.setCategoryId(obj.getLong("categoryId"));
            f.setName(obj.optString("name", ""));
            f.setSlug(obj.optString("slug", ""));
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
            list.add(new CategoryDTO(
                    obj.getLong("id"),
                    obj.optString("name", ""),
                    obj.optString("slug", ""),
                    obj.optString("description", ""),
                    null
            ));
        }
        return list;
    }

    private static List<TableDTO> jsonArrayToTableList(JSONArray arr) {
        List<TableDTO> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            TableDTO t = new TableDTO(obj.getLong("id"), obj.optString("tableName", ""),
                    obj.optInt("capacity", 4), obj.optBoolean("status", true));
            if (obj.has("activeSessionId") && !obj.isNull("activeSessionId"))
                t.setActiveSessionId(obj.getLong("activeSessionId"));
            list.add(t);
        }
        return list;
    }

    private static JSONObject billToJson(BillDTO b) {
        JSONObject obj = new JSONObject();
        if (b.getTableSessionId() != null) obj.put("tableSessionId", b.getTableSessionId());
        obj.put("totalAmount", b.getTotalAmount());
        obj.put("discount", b.getDiscount());
        obj.put("tax", b.getTax());
        if (b.getPaymentMethod() != null) obj.put("paymentMethod", b.getPaymentMethod().name());
        return obj;
    }
    private static JSONObject foodToJson(FoodDTO f) {
        JSONObject obj = new JSONObject();
        if (f.getId() != null) obj.put("id", f.getId());
        if (f.getCategoryId() != null) obj.put("categoryId", f.getCategoryId());
        if (f.getSku() != null) obj.put("sku", f.getSku());
        if (f.getName() != null) obj.put("name", f.getName());
        if (f.getPrice() != null) obj.put("price", f.getPrice());
        if (f.getCoverImageUrl() != null) obj.put("coverImageUrl", f.getCoverImageUrl());
        if (f.getIsPublished() != null) obj.put("isPublished", f.getIsPublished());
        return obj;
    }

    private static JSONObject ingredientToJson(IngredientDTO i) {
        JSONObject obj = new JSONObject();
        if (i.getId() != null) obj.put("id", i.getId());
        if (i.getName() != null) obj.put("name", i.getName());
        if (i.getUnit() != null) obj.put("unit", i.getUnit());
        if (i.getStockQuantity() != null) obj.put("stockQuantity", i.getStockQuantity());
        if (i.getMinStockLevel() != null) obj.put("minStockLevel", i.getMinStockLevel());
        return obj;
    }

    private static List<IngredientDTO> jsonArrayToIngredientList(JSONArray arr) {
        List<IngredientDTO> result = new ArrayList<>();
        for (int index = 0; index < arr.length(); index++) {
            JSONObject obj = arr.getJSONObject(index);
            IngredientDTO i = new IngredientDTO();
            if (obj.has("id") && !obj.isNull("id")) i.setId(obj.getLong("id"));
            i.setName(obj.optString("name", ""));
            i.setUnit(obj.optString("unit", ""));
            i.setStockQuantity(obj.optBigDecimal("stockQuantity", BigDecimal.ZERO));
            i.setMinStockLevel(obj.optBigDecimal("minStockLevel", BigDecimal.ZERO));
            result.add(i);
        }
        return result;
    }

    private static List<BillDTO> jsonArrayToBillList(JSONArray arr) {
        List<BillDTO> result = new ArrayList<>();
        for (int index = 0; index < arr.length(); index++) {
            JSONObject obj = arr.getJSONObject(index);
            BillDTO b = new BillDTO();
            if (obj.has("id") && !obj.isNull("id")) b.setId(obj.getLong("id"));
            if (obj.has("tableSessionId") && !obj.isNull("tableSessionId"))
                b.setTableSessionId(obj.getLong("tableSessionId"));
            b.setTotalAmount(obj.optBigDecimal("totalAmount", BigDecimal.ZERO));
            b.setDiscount(obj.optBigDecimal("discount", BigDecimal.ZERO));
            b.setTax(obj.optBigDecimal("tax", BigDecimal.ZERO));
            if (obj.has("paymentMethod") && !obj.isNull("paymentMethod"))
                b.setPaymentMethod(PaymentMethod.valueOf(obj.getString("paymentMethod")));
            if (obj.has("paymentStatus") && !obj.isNull("paymentStatus"))
                b.setPaymentStatus(PaymentStatus.valueOf(obj.getString("paymentStatus")));
            if (obj.has("paidAt") && !obj.isNull("paidAt"))
                b.setPaidAt(java.time.LocalDateTime.parse(obj.getString("paidAt")));
            if (obj.has("createdAt") && !obj.isNull("createdAt"))
                b.setCreatedAt(java.time.LocalDateTime.parse(obj.getString("createdAt")));
            result.add(b);
        }
        return result;
    }
}