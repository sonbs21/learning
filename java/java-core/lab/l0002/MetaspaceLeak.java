import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

public class MetaspaceLeak {
    static List<Object> giuLai = new ArrayList<>();   // giữ mãi → không class nào được dọn

    public static void main(String[] args) {
        int i = 0;
        try {
            while (true) {
                // Mỗi ClassLoader mới → JVM phải sinh thêm MỘT CLASS proxy mới
                ClassLoader loader = new URLClassLoader(new URL[0], MetaspaceLeak.class.getClassLoader());
                Object proxy = Proxy.newProxyInstance(loader, new Class<?>[]{ Runnable.class }, (p, m, a) -> null);
                giuLai.add(proxy);
                i++;
                if (i % 2000 == 0) System.out.println("Đã sinh " + i + " class");
            }
        } catch (OutOfMemoryError e) {
            giuLai = null;
            System.out.println("Sập sau " + i + " class: " + e);
        }
    }
}
