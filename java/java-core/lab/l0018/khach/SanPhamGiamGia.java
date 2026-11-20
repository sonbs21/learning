package khach;

import cuahang.SanPham;

public class SanPhamGiamGia extends SanPham {
    String nhan() { return ten + " (" + moTa + ") giảm 10%"; }   // protected: class con thấy

    public static void main(String[] args) {
        System.out.println(new SanPhamGiamGia().nhan());
    }
}
