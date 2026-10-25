package dto;

import java.io.Serializable;

public class MonAnDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private int maMon;
    private String tenMon;
    private String danhMuc; // Lẩu, Thịt bò, Hải sản, Rau nấm...
    private double gia;
    private String hinhAnh; // Đường dẫn ảnh hoặc tên file ảnh

    public MonAnDTO(int maMon, String tenMon, String danhMuc, double gia, String hinhAnh) {
        this.maMon = maMon;
        this.tenMon = tenMon;
        this.danhMuc = danhMuc;
        this.gia = gia;
        this.hinhAnh = hinhAnh;
    }

    public int getMaMon() { return maMon; }
    public String getTenMon() { return tenMon; }
    public String getDanhMuc() { return danhMuc; }
    public double getGia() { return gia; }
    public String getHinhAnh() { return hinhAnh; }
}