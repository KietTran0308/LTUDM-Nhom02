package api;

import dto.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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
            case LOGIN:
                if (data != null) root.put("data", data.toString());           // emailOrPhone
                if (extraData != null) root.put("extraData", extraData.toString()); // password
                break;

            case REGISTER:
                if (data instanceof UserDTO) {
                    root.put("data", userToJson((UserDTO) data));
                }
                break;

            case PLACE_ORDER:
                if (data instanceof OrderDTO) {
                    root.put("data", orderToJson((OrderDTO) data));
                }
                if (extraData != null) {
                    root.put("extraData", extraData.toString()); // voucherCode
                }
                break;

            case CALL_STAFF:
                if (data != null) root.put("data", data.toString()); // Nội dung gọi nhân viên
                break;

            case PAY_TABLE_BILL:
                if (data instanceof BillDTO) {
                    root.put("data", billToJson((BillDTO) data));
                }
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
                case LOGIN:
                case REGISTER:
                    if (root.has("data") && !root.isNull("data")) {
                        data = jsonToUser(root.getJSONObject("data"));
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

                case KITCHEN_UPDATE_STATUS:
                    if (root.has("data") && !root.isNull("data")) {
                        data = root.getLong("data"); // orderItemId
                    }
                    if (root.has("extraData") && !root.isNull("extraData")) {
                        extraData = OrderStatus.valueOf(root.getString("extraData"));
                    }
                    break;

                case GET_ALL_TABLES:
                case PAY_TABLE_BILL:
                    if (root.has("data") && !root.isNull("data")) {
                        data = jsonArrayToTableList(root.getJSONArray("data"));
                    }
                    break;

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
            list.add(new TableDTO(
                    obj.getLong("id"),
                    obj.optString("tableName", ""),
                    obj.optInt("capacity", 4),
                    obj.optBoolean("status", true)
            ));
        }
        return list;
    }

    private static JSONObject billToJson(BillDTO b) {
        JSONObject obj = new JSONObject();
        if (b.getOrderId() != null) obj.put("orderId", b.getOrderId());
        obj.put("totalAmount", b.getTotalAmount());
        obj.put("discount", b.getDiscount());
        obj.put("tax", b.getTax());
        if (b.getPaymentMethod() != null) obj.put("paymentMethod", b.getPaymentMethod().name());
        return obj;
    }
}