import java.util.ArrayList;
import java.util.List;

public class GenericInfo {
    List<String> danhSach;                               // khai báo field có generic

    public static void main(String[] args) throws Exception {
        System.out.println(GenericInfo.class.getDeclaredField("danhSach").getGenericType());
        Object vo = new ArrayList<String>() {};          // class con ẩn danh "đóng băng" <String>
        System.out.println(vo.getClass().getGenericSuperclass());
        System.out.println(new ArrayList<String>().getClass().getGenericSuperclass());
    }
}
