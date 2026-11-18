import java.util.HashMap;

public class Buckets {
    public static void main(String[] args) throws Exception {
        HashMap<String, Integer> tuoi = new HashMap<>();
        tuoi.put("an", 20);
        tuoi.put("binh", 25);
        tuoi.put("chi", 30);
        tuoi.put("dung", 22);

        for (String k : tuoi.keySet()) {
            int h = k.hashCode();
            int spread = h ^ (h >>> 16);
            System.out.printf("%-5s hashCode=%-8d → ngăn %d%n", k, h, spread & 15);
        }
        Peek.show(tuoi);
    }
}
