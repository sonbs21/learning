public class Shadow {
    static class NhanVien {
        String ten;
        void setTenSai(String ten) { ten = ten; }        // quên this
        void setTenDung(String ten) { this.ten = ten; }  // có this
    }

    public static void main(String[] args) {
        NhanVien a = new NhanVien();
        a.setTenSai("An");
        System.out.println("Sau setTenSai : ten = " + a.ten);
        a.setTenDung("An");
        System.out.println("Sau setTenDung: ten = " + a.ten);
    }
}
