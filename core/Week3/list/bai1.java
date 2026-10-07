package core.Week3.list;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class bai1 {
    public static void main(String[] args) throws Exception {
        List<Integer> list = new ArrayList<>();

        // Lấy field private "elementData" của class ArrayList
        Field field = ArrayList.class.getDeclaredField("elementData");
        field.setAccessible(true); // bắt buộc, vì field này là private

        for (int i = 0; i < 20; i++) {
            list.add(i);

            // field.get(list) trả về Object -> ép kiểu về Object[] để đọc .length
            Object[] elementData = (Object[]) field.get(list);
            int capacity = elementData.length;

            System.out.printf("add() lần %2d -> size=%2d, capacity=%2d%n",
                    i + 1, list.size(), capacity);
        }
    }
}