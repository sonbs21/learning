// Bài 2 — chạy bằng: java lessons/code/L0002Equality.java
// Đoán từng dòng TRƯỚC khi chạy.
public class L0002Equality {

    static class Person {
        String name;
        Person(String name) { this.name = name; }
        // Cố ý KHÔNG override equals => dùng equals của Object (so sánh ==)
    }

    public static void main(String[] args) {
        String a = "hi";
        String b = "hi";
        String c = new String("hi");
        System.out.println("1. a == b          : " + (a == b));
        System.out.println("2. a == c          : " + (a == c));
        System.out.println("3. a.equals(c)     : " + a.equals(c));

        String d = "h" + "i";              // constant expression => tính lúc compile
        System.out.println("4. a == d          : " + (a == d));

        String h = "h";
        String e = h + "i";                // h không phải hằng => nối lúc runtime
        System.out.println("5. a == e          : " + (a == e));
        System.out.println("6. a == e.intern() : " + (a == e.intern()));

        final String fh = "h";             // final + khởi tạo bằng hằng => constant variable
        String f = fh + "i";
        System.out.println("7. a == f          : " + (a == f));

        Integer x = 127, y = 127;
        Integer m = 128, n = 128;
        System.out.println("8. 127 == 127      : " + (x == y));
        System.out.println("9. 128 == 128      : " + (m == n));
        System.out.println("10. m.equals(n)    : " + m.equals(n));

        Person p1 = new Person("An"), p2 = new Person("An");
        System.out.println("11. p1.equals(p2)  : " + p1.equals(p2));
    }
}
