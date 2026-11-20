public class Trap {
    static class DongVat { String keu() { return "..."; } }
    static class Cho extends DongVat { @Override String keu() { return "Gâu gâu"; } }

    static String loai(DongVat d) { return "loai(DongVat)"; }
    static String loai(Cho c)     { return "loai(Cho)"; }

    public static void main(String[] args) {
        DongVat a = new Cho();        // khai báo DongVat, object thật là Cho
        System.out.println(a.keu());  // override: hỏi object thật
        System.out.println(loai(a));  // overload: nhìn kiểu khai báo
    }
}
