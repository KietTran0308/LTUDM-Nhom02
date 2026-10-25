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
            case LOGIN:
                data = root.optString("data", "");          // emailOrPhone
                extraData = root.optString("extraData", ""); // password
                break;

            case REGISTER:
                if (root.has("data") && !root.isNull("data")) {
                    data = jsonToUser(root.getJSONObject("data"));
                }
                break;

            case PLACE_ORDER:
                if (root.has("data") && !root.isNull("data")) {
                    data = jsonToOrder(root.getJSONObject("data"));
                }
                if (root.has("extraData") && !root.isNull("extraData")) {
                    extraData = root.getString("extraData"); // voucherCode
                }
                break;

            case CALL_STAFF:
                data = root.optString("data", "");
                break;

            case PAY_TABLE_BILL:
                if (root.has("data") && !root.isNull("data")) {
                    data = jsonToBill(root.getJSONObject("data"));
                }
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
                case LOGIN:
                case REGISTER:
                    if (data instanceof UserDTO) {
                        root.put("data", userToJson((UserDTO) data));
                    }
                    break;

                case GET_MENU_AND_CATEGORIES:
                    if (data instanceof List<?>) {
                        root.put("data", foodListToJsonArray((List<FoodDTO>) data));
                    }
                    if (extraData instanceof List<?>) {
                        root.put("extraData", categoryListToJsonArray((List<CategoryDTO>) extraData));
                    }
                    break;

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

                case KITCHEN_UPDATE_STATUS:
                    if (data != null) root.put("data", data); // Long orderItemId
                    if (extraData instanceof OrderStatus) {
                        root.put("extraData", ((OrderStatus) extraData).name());
                    }
                    break;

                case GET_ALL_TABLES:
                case PAY_TABLE_BILL:
                    if (data instanceof List<?>) {
                        root.put("data", tableListToJsonArray((List<TableDTO>) data));
                    }
                    break;

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
        if (o.getSubtotal() != null) obj.put("subtotal", o.getSubtotal());
        if (o.getDiscountAmount() != null) obj.put("discountAmount", o.getDiscountAmount());
        if (o.getTotalAmount() != null) obj.put("totalAmount", o.getTotalAmount());
        if (o.getNote() != null) obj.put("note", o.getNote());
        if (o.getItems() != null) {
            obj.put("items", orderItemListToJsonArray(o.getItems()));
        }
        return obj;
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
                arr.put(obj);
            }
        }
        return arr;
    }

    private static BillDTO jsonToBill(JSONObject obj) {
        BillDTO b = new BillDTO();
        if (obj.has("orderId") && !obj.isNull("orderId")) b.setOrderId(obj.getLong("orderId"));
        b.setTotalAmount(obj.optBigDecimal("totalAmount", BigDecimal.ZERO));
        b.setDiscount(obj.optBigDecimal("discount", BigDecimal.ZERO));
        b.setTax(obj.optBigDecimal("tax", BigDecimal.ZERO));
        if (obj.has("paymentMethod") && !obj.isNull("paymentMethod")) {
            b.setPaymentMethod(PaymentMethod.valueOf(obj.getString("paymentMethod")));
        }
        return b;
    }
}