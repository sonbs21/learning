public class DaHinh {
    static class DongVat { String keu() { return "..."; } }
    static class Cho extends DongVat { @Override String keu() { return "Gâu gâu"; } }
    static class Meo extends DongVat { @Override String keu() { return "Meo meo"; } }

    public static void main(String[] args) {
        DongVat[] chuong = { new DongVat(), new Cho(), new Meo() };
        for (DongVat d : chuong) {
            System.out.println(d.getClass().getSimpleName() + ": " + d.keu());
        }
    }
}
