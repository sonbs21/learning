import java.util.HashMap;

public class Collision {
    public static void main(String[] args) throws Exception {
        System.out.println("\"Aa\".hashCode() = " + "Aa".hashCode());
        System.out.println("\"BB\".hashCode() = " + "BB".hashCode());

        HashMap<String, String> map = new HashMap<>();
        map.put("Aa", "giá trị của Aa");
        map.put("BB", "giá trị của BB");

        Peek.show(map);
        System.out.println("get(\"Aa\") = " + map.get("Aa"));
        System.out.println("get(\"BB\") = " + map.get("BB"));
    }
}
