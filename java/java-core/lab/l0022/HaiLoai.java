public class HaiLoai {
    String tenQuan = "Cà phê Sáng";

    static class Nested {                       // static nested: không gắn với object HaiLoai nào
        String chao() { return "Nested: xin chào"; }
    }

    class Inner {                               // inner: luôn gắn với MỘT object HaiLoai
        String chao() { return "Inner: chào từ " + tenQuan; }   // đọc field của object ngoài
    }

    public static void main(String[] args) {
        HaiLoai.Nested n = new HaiLoai.Nested();        // tạo thẳng, không cần object ngoài
        System.out.println(n.chao());

        HaiLoai quan = new HaiLoai();
        HaiLoai.Inner i = quan.new Inner();              // phải có object ngoài mới tạo được
        System.out.println(i.chao());

        quan.tenQuan = "Cà phê Tối";
        System.out.println(i.chao());                    // thấy thay đổi: nó giữ tham chiếu tới quan

        System.out.println("Constructor của Inner:  " + java.util.Arrays.toString(Inner.class.getDeclaredConstructors()));
        System.out.println("Constructor của Nested: " + java.util.Arrays.toString(Nested.class.getDeclaredConstructors()));
    }
}
