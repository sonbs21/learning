import java.lang.reflect.Field;
import java.util.ArrayList;

public class Grow {
    public static void main(String[] args) throws Exception {
        ArrayList<Integer> list = new ArrayList<>();
        Field f = ArrayList.class.getDeclaredField("elementData");   // "mở hộp" xem mảng bên trong
        f.setAccessible(true);

        int lastCapacity = -1;
        for (int i = 0; i <= 50; i++) {
            int capacity = ((Object[]) f.get(list)).length;
            if (capacity != lastCapacity) {
                System.out.println("size = " + i + "  →  mảng bên trong dài " + capacity);
                lastCapacity = capacity;
            }
            list.add(i);
        }
    }
}
