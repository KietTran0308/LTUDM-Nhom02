package dto;

import java.text.Normalizer;

public interface dbEnums {

    enum DiscountType {
        PERCENT, FIXED
    }

    enum OrderStatus {
        PENDING, RECEIVED, COOKING, COMPLETED, SERVED, CANCELLED
    }

    enum PaymentMethod {
        CASH, CREDIT_CARD, E_WALLET, BANK_TRANSFER
    }

    enum PaymentStatus {
        UNPAID, PAID, CANCELLED
    }

    enum PointTransactionType {
        EARN, REDEEM, ADJUST
    }

    enum TableSessionStatus {
        ACTIVE, CLOSED
    }

    enum PointTier {
        DONG("ĐỒNG"),
        BAC("BẠC"),
        VANG("VÀNG"),
        KIM_CUONG("KIM CƯƠNG");

        private final String dbValue;

        PointTier(String dbValue) {
            this.dbValue = dbValue;
        }

        public String getDbValue() {
            return dbValue;
        }

        public static PointTier fromDbValue(String value) {
            if (value == null || value.isBlank()) return DONG;
            String wanted = normalize(value);
            for (PointTier tier : values()) {
                if (normalize(tier.dbValue).equals(wanted)) return tier;
            }
            throw new IllegalArgumentException("Hạng thành viên không hợp lệ: " + value);
        }

        private static String normalize(String s) {
            return Normalizer.normalize(s.trim(), Normalizer.Form.NFC).toUpperCase();
        }

        @Override
        public String toString() {
            return dbValue;
        }
    }
}