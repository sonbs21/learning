package cuahang;

public class KhoHang {
    public static void main(String[] args) {
        SanPham sp = new SanPham();
        System.out.println(sp.ten + " | " + sp.moTa + " | tồn " + sp.tonKho);
        System.out.println("rẻ hơn chính nó? " + sp.reHon(sp));
    }
}
