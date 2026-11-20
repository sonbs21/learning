import java.lang.reflect.Field;
import java.util.HashMap;

// Công cụ nhỏ để "nhìn vào trong" HashMap (dùng reflection, cần --add-opens)
public class Peek {
    static Object[] table(HashMap<?, ?> map) throws Exception {
        Field f = HashMap.class.getDeclaredField("table");
        f.setAccessible(true);
        return (Object[]) f.get(map);
    }

    // In từng ngăn (bucket) đang có dữ liệu
    static void show(HashMap<?, ?> map) throws Exception {
        Object[] tab = table(map);
        System.out.println("Số ngăn: " + tab.length);
        for (int i = 0; i < tab.length; i++) {
            if (tab[i] == null) continue;
            StringBuilder sb = new StringBuilder("  ngăn " + i + ": ");
            for (Object node = tab[i]; node != null; node = next(node)) {
                sb.append(key(node)).append(" → ");
            }
            System.out.println(sb.append("hết"));
        }
    }

    static Object key(Object node) throws Exception { return field(node, "key"); }
    static Object next(Object node) throws Exception { return field(node, "next"); }

    static Object field(Object node, String name) throws Exception {
        Class<?> c = node.getClass();
        while (c != null) {
            try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f.get(node); }
            catch (NoSuchFieldException e) { c = c.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }
}
