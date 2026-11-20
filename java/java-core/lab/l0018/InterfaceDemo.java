import java.lang.reflect.Modifier;

public class InterfaceDemo {
    interface CoTheBay {
        int SO_CANH = 2;                          // trông như field thường...
        void bay();                               // không có thân: class phải tự làm
        default String gioiThieu() { return "Tôi bay được"; }   // Java 8+: có thân sẵn
    }
    interface CoTheBoi { void boi(); }

    static class Vit implements CoTheBay, CoTheBoi {          // một class, nhiều interface
        @Override public void bay() { System.out.println("Vịt vỗ cánh bay"); }
        @Override public void boi() { System.out.println("Vịt bơi"); }
    }

    public static void main(String[] args) throws Exception {
        Vit v = new Vit();
        v.bay();
        v.boi();
        System.out.println(v.gioiThieu());
        CoTheBay b = v;                           // nhìn Vit qua "hợp đồng" CoTheBay
        b.bay();
        System.out.println("SO_CANH là: " + Modifier.toString(CoTheBay.class.getField("SO_CANH").getModifiers()));
        System.out.println("bay() là:   " + Modifier.toString(CoTheBay.class.getMethod("bay").getModifiers()));
    }
}
