import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Cme {
    public static void main(String[] args) {
        List<String> a = new ArrayList<>(List.of("trà", "cà phê", "sữa", "nước cam"));
        try {
            for (String s : a) if (s.equals("trà")) a.remove(s);       // xoá trong lúc duyệt
        } catch (Exception e) { System.out.println("Xoá phần tử đầu: " + e); }

        List<String> b = new ArrayList<>(List.of("trà", "cà phê", "sữa", "nước cam"));
        List<String> daDuyet = new ArrayList<>();
        for (String s : b) { daDuyet.add(s); if (s.equals("sữa")) b.remove(s); }   // xoá phần tử ÁP CHÓT
        System.out.println("Xoá áp chót: không lỗi! đã duyệt " + daDuyet + ", list còn " + b);

        List<String> c = new ArrayList<>(List.of("trà", "cà phê", "sữa", "nước cam"));
        for (Iterator<String> it = c.iterator(); it.hasNext(); ) if (it.next().startsWith("c")) it.remove();
        System.out.println("iterator.remove(): " + c);

        List<String> d = new ArrayList<>(List.of("trà", "cà phê", "sữa", "nước cam"));
        d.removeIf(s -> s.length() <= 3);
        System.out.println("removeIf:          " + d);
    }
}
