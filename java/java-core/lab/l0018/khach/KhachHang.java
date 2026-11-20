package khach;

import cuahang.SanPham;

public class KhachHang {
    public static void main(String[] args) {
        SanPham sp = new SanPham();
        System.out.println(sp.ten);       // public: được
        System.out.println(sp.moTa);      // protected
        System.out.println(sp.tonKho);    // package-private
        System.out.println(sp.giaVon);    // private
    }
}
