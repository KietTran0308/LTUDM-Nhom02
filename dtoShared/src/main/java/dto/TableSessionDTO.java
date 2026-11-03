package dto;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Anh xa table_sessions. Mot ban chi duoc co mot phien ACTIVE
 * (UNIQUE tren cot GENERATED active_table_id).
 *
 * Quan trong: accessTokenHash chi dung o server de DOI CHIEU ma phien,
 * khong gui ve client qua Java ObjectOutputStream.
 */
public class TableSessionDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long tableId; // NOT NULL, FK restaurant_tables.id
    private Long openedBy; // NULL hoac FK users.id
    private TableSessionStatus status; // NOT NULL, DEFAULT ACTIVE
    private transient String accessTokenHash; // CHAR(64) NOT NULL; SERVER-ONLY
    private LocalDateTime openedAt; // NOT NULL, DEFAULT CURRENT_TIMESTAMP(3)
    private LocalDateTime closedAt; // NULL neu dang ACTIVE
    private Long activeTableId; // GENERATED ALWAYS, READ ONLY trong SQL

    public TableSessionDTO() {}

    public TableSessionDTO(Long id, Long tableId, Long openedBy,
                           TableSessionStatus status, String accessTokenHash,
                           LocalDateTime openedAt, LocalDateTime closedAt,
                           Long activeTableId) {
        this.id = id;
        this.tableId = tableId;
        this.openedBy = openedBy;
        this.status = status;
        this.accessTokenHash = accessTokenHash;
        this.openedAt = openedAt;
        this.closedAt = closedAt;
        this.activeTableId = activeTableId;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTableId() { return tableId; }
    public void setTableId(Long tableId) { this.tableId = tableId; }

    public Long getOpenedBy() { return openedBy; }
    public void setOpenedBy(Long openedBy) { this.openedBy = openedBy; }

    public TableSessionStatus getStatus() { return status; }
    public void setStatus(TableSessionStatus status) { this.status = status; }

    /** Khong truyen gia tri nay qua SocketPacket cho client. */
    public String getAccessTokenHash() { return accessTokenHash; }
    public void setAccessTokenHash(String accessTokenHash) { this.accessTokenHash = accessTokenHash; }

    public LocalDateTime getOpenedAt() { return openedAt; }
    public void setOpenedAt(LocalDateTime openedAt) { this.openedAt = openedAt; }

    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }

    /** Chỉ đọc/ánh xạ từ ResultSet; KHÔNG đưa active_table_id vào INSERT/UPDATE. */
    public Long getActiveTableId() { return activeTableId; }
    public void setActiveTableId(Long activeTableId) { this.activeTableId = activeTableId; }
}
