package cuahang;

public class SanPham {
    public String ten = "Cà phê";     // ai cũng thấy
    protected String moTa = "Rang xay";   // cùng package + class con
    int tonKho = 50;                   // (không ghi gì) chỉ cùng package
    private int giaVon = 18000;        // chỉ trong class SanPham

    public boolean reHon(SanPham khac) {
        return this.giaVon < khac.giaVon;   // đọc private của object KHÁC: được!
    }
}
