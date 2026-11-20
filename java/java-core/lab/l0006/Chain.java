public class Chain {
    static class SanPham {
        String ten; int gia; int soLuong;

        SanPham(String ten, int gia, int soLuong) {           // constructor "đầy đủ"
            this.ten = ten; this.gia = gia; this.soLuong = soLuong;
            System.out.println("  constructor 3 tham số chạy");
        }
        SanPham(String ten, int gia) {
            this(ten, gia, 1);                                  // gọi constructor kia, soLuong mặc định 1
            System.out.println("  constructor 2 tham số chạy");
        }
    }

    public static void main(String[] args) {
        SanPham sp = new SanPham("bút", 5000);
        System.out.println(sp.ten + " / " + sp.gia + " / " + sp.soLuong);
    }
}
