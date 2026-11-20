public class AbstractDemo {
    static abstract class Hinh {
        final String ten;                         // abstract class được có field (trạng thái)
        Hinh(String ten) { this.ten = ten; }      // ...và constructor
        abstract double dienTich();               // chưa làm: để class con làm
        String moTa() { return ten + ": " + dienTich(); }   // làm sẵn, dùng chung
    }
    static class HinhVuong extends Hinh {
        final double canh;
        HinhVuong(double canh) { super("Hình vuông"); this.canh = canh; }
        @Override double dienTich() { return canh * canh; }
    }
    static class HinhTron extends Hinh {
        final double r;
        HinhTron(double r) { super("Hình tròn"); this.r = r; }
        @Override double dienTich() { return Math.round(Math.PI * r * r * 100) / 100.0; }
    }
    public static void main(String[] args) {
        Hinh[] ds = { new HinhVuong(3), new HinhTron(1) };
        for (Hinh h : ds) System.out.println(h.moTa());
    }
}
