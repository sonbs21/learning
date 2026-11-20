public class StaticHide {
    static class Cha { static String ten() { return "Cha"; }  String chao() { return "Cha"; } }
    static class Con extends Cha { static String ten() { return "Con"; }  @Override String chao() { return "Con"; } }

    public static void main(String[] args) {
        Cha x = new Con();
        System.out.println("x.chao() = " + x.chao());   // method thường: object thật
        System.out.println("x.ten()  = " + x.ten());    // static: kiểu khai báo
    }
}
