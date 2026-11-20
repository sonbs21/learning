import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class BangPhiShip {
    public static void main(String[] args) {
        TreeMap<Integer, Integer> phi = new TreeMap<>();     // từ X gram trở lên → phí
        Map<Integer, Integer> hash = new HashMap<>();
        for (Map<Integer, Integer> m : java.util.List.of(phi, hash)) {   // put cùng thứ tự vào cả hai
            m.put(1000, 25000);
            m.put(0, 15000);
            m.put(5000, 50000);
            m.put(3000, 35000);
        }
        System.out.println("TreeMap duyệt: " + phi.keySet());
        System.out.println("HashMap duyệt: " + hash.keySet());

        for (int gram : new int[]{300, 1000, 2700, 9000}) {
            Map.Entry<Integer, Integer> bac = phi.floorEntry(gram);     // bậc lớn nhất <= gram
            System.out.println(gram + "g → bậc " + bac.getKey() + "g → phí " + bac.getValue());
        }
        System.out.println("Các bậc từ 1000g tới dưới 5000g: " + phi.subMap(1000, 5000));
    }
}
