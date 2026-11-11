// Bài 4 — nhìn vào bên trong HashMap bằng reflection.
// Chạy bằng (cần mở module java.util cho reflection):
//   java --add-opens java.base/java.util=ALL-UNNAMED lessons/code/L0004HashMapInside.java
import java.lang.reflect.Field;
import java.util.HashMap;

public class L0004HashMapInside {

    // Chép y nguyên từ HashMap.hash() trong OpenJDK 21
    static int spread(Object key) {
        int h;
        return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
    }

    static int tableLength(HashMap<?, ?> map) throws Exception {
        Field f = HashMap.class.getDeclaredField("table");
        f.setAccessible(true);
        Object[] table = (Object[]) f.get(map);
        return table == null ? 0 : table.length;
    }

    public static void main(String[] args) throws Exception {
        // 1. Key được phân vào bucket nào?
        int n = 16;
        for (String key : new String[]{"An", "Bình", "Chi", "Aa", "BB"}) {
            int h = spread(key);
            System.out.printf("key=%-5s hashCode=%-8d bucket=(n-1)&hash=%d%n", key, key.hashCode(), (n - 1) & h);
        }

        // 2. Bảng được tạo khi nào, và resize khi nào?
        HashMap<Integer, String> map = new HashMap<>();
        System.out.println("sau new HashMap()   : table.length = " + tableLength(map));
        for (int i = 1; i <= 25; i++) {
            int before = tableLength(map);
            map.put(i, "v" + i);
            int after = tableLength(map);
            if (before != after) {
                System.out.println("put thứ " + i + "            : table.length " + before + " -> " + after);
            }
        }
    }
}
