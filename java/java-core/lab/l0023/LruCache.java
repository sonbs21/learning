import java.util.LinkedHashMap;
import java.util.Map;

public class LruCache {
    public static void main(String[] args) {
        Map<String, Integer> themVao = new LinkedHashMap<>();
        themVao.put("c", 3); themVao.put("a", 1); themVao.put("b", 2);
        System.out.println("Thứ tự thêm vào: " + themVao);

        // accessOrder = true: mỗi lần get/put, phần tử được chuyển xuống cuối
        Map<String, Integer> cache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) {
                return size() > 3;                       // quá 3 phần tử: bỏ phần tử lâu nhất chưa dùng
            }
        };
        cache.put("u1", 1); cache.put("u2", 2); cache.put("u3", 3);
        System.out.println("Cache:            " + cache);
        cache.get("u1");
        System.out.println("Sau get(u1):      " + cache);
        cache.put("u4", 4);
        System.out.println("Sau put(u4):      " + cache);
    }
}
