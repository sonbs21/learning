public class BuilderRace {
    static void chay(Appendable sb, String ten) throws Exception {
        Runnable viec = () -> {
            try { for (int i = 0; i < 100_000; i++) sb.append('x'); }
            catch (Exception e) { System.out.println("  " + ten + " lỗi: " + e); }
        };
        Thread a = new Thread(viec), b = new Thread(viec);
        a.start(); b.start(); a.join(); b.join();
        System.out.println(ten + ": dài " + ((CharSequence) sb).length() + " (đúng phải là 200000)");
    }

    public static void main(String[] args) throws Exception {
        chay(new StringBuilder(), "StringBuilder");
        chay(new StringBuffer(),  "StringBuffer ");
    }
}
