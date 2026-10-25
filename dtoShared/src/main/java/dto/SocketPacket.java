package dto;

import java.io.Serializable;

public class SocketPacket implements Serializable {
    private static final long serialVersionUID = 1L;

    private SocketAction action;
    private boolean success;
    private String message;
    private Long tableId;       // Định danh bàn gửi hoặc nhận
    private Object data;        // Chứa OrderDTO, List<FoodDTO>, List<OrderItemDTO>...
    private Object extraData;   // Chứa dữ liệu phụ (VD: List<CategoryDTO> hoặc mã Voucher)

    public SocketPacket() {}

    public SocketPacket(SocketAction action, Long tableId, Object data, Object extraData) {
        this.action = action;
        this.tableId = tableId;
        this.data = data;
        this.extraData = extraData;
    }

    public SocketPacket(SocketAction action, boolean success, String message, Object data, Object extraData) {
        this.action = action;
        this.success = success;
        this.message = message;
        this.data = data;
        this.extraData = extraData;
    }

    public SocketAction getAction() { return action; }
    public void setAction(SocketAction action) { this.action = action; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Long getTableId() { return tableId; }
    public void setTableId(Long tableId) { this.tableId = tableId; }

    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }

    public Object getExtraData() { return extraData; }
    public void setExtraData(Object extraData) { this.extraData = extraData; }
}