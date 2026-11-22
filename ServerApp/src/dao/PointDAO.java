package dao;

import dto.PointDTO;
import dto.PointTransactionDTO;
import dto.dbEnums;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class PointDAO {

    /**
     * Lấy điểm của tất cả tài khoản CUSTOMER.
     * Thành viên chưa từng tích điểm vẫn xuất hiện với số điểm = 0.
     */
    public List<PointDTO> getAllMemberPoints() throws SQLException {
        List<PointDTO> list = new ArrayList<>();

        String sql = """
                SELECT
                    u.id AS user_id,
                    COALESCE(p.current_points, 0) AS current_points,
                    COALESCE(p.total_earned_points, 0) AS total_earned_points,
                    COALESCE(p.tier, 'ĐỒNG') AS tier,
                    p.updated_at
                FROM users u
                JOIN roles r ON r.roleID = u.roleID
                LEFT JOIN points p ON p.user_id = u.id
                WHERE r.nameRole = 'CUSTOMER'
                ORDER BY current_points DESC, u.id ASC
                """;

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Timestamp updatedAt = rs.getTimestamp("updated_at");

                PointDTO point = new PointDTO(
                        rs.getLong("user_id"),
                        rs.getInt("current_points"),
                        rs.getInt("total_earned_points"),
                        mapTier(rs.getString("tier")),
                        updatedAt == null ? null : updatedAt.toLocalDateTime()
                );

                list.add(point);
            }
        }

        return list;
    }

    /**
     * Lấy lịch sử giao dịch điểm của một thành viên.
     * Bao gồm EARN, REDEEM và ADJUST.
     */
    public List<PointTransactionDTO> getPointHistory(Long userId)
            throws SQLException {

        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("ID thành viên không hợp lệ");
        }

        List<PointTransactionDTO> list = new ArrayList<>();

        String sql = """
                SELECT
                    id,
                    user_id,
                    bill_id,
                    type,
                    points_change,
                    note,
                    created_at
                FROM point_transactions
                WHERE user_id = ?
                ORDER BY created_at DESC, id DESC
                """;

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, userId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long billValue = rs.getLong("bill_id");
                    Long billId = rs.wasNull() ? null : billValue;

                    Timestamp createdAt = rs.getTimestamp("created_at");

                    PointTransactionDTO transaction =
                            new PointTransactionDTO(
                                    rs.getLong("id"),
                                    rs.getLong("user_id"),
                                    billId,
                                    dbEnums.PointTransactionType.valueOf(
                                            rs.getString("type")
                                    ),
                                    rs.getInt("points_change"),
                                    rs.getString("note"),
                                    createdAt == null
                                            ? null
                                            : createdAt.toLocalDateTime()
                            );

                    list.add(transaction);
                }
            }
        }

        return list;
    }

    /**
     * Chuyển giá trị ENUM tiếng Việt trong MySQL
     * sang enum PointTier của Java.
     */
    private dbEnums.PointTier mapTier(String tier) {
        return switch (tier) {
            case "ĐỒNG" -> dbEnums.PointTier.DONG;
            case "BẠC" -> dbEnums.PointTier.BAC;
            case "VÀNG" -> dbEnums.PointTier.VANG;
            case "KIM CƯƠNG" -> dbEnums.PointTier.KIM_CUONG;
            default -> throw new IllegalArgumentException(
                    "Hạng thành viên không hợp lệ: " + tier
            );
        };
    }
}
