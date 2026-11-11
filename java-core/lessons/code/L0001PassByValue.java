// Bài 1 — chạy bằng: java lessons/code/L0001PassByValue.java
// Đoán output TRƯỚC khi chạy, rồi so sánh.
public class L0001PassByValue {

    static class Person {
        String name;
        Person(String name) { this.name = name; }
    }

    static void inc(int x) { x = x + 1; }

    static void rename(Person p) { p.name = "Bình"; }

    static void replace(Person p) { p = new Person("Bình"); }

    static void swap(Person x, Person y) { Person t = x; x = y; y = t; }

    static void tricky(Person p) {
        p.name = "Chi";
        p = new Person("Dũng");
        p.name = "Em";
    }

    public static void main(String[] args) {
        int a = 10;
        inc(a);
        System.out.println("Q1 inc:     " + a);

        Person an = new Person("An");
        rename(an);
        System.out.println("Q2 rename:  " + an.name);

        Person an2 = new Person("An");
        replace(an2);
        System.out.println("Q3 replace: " + an2.name);

        Person x = new Person("An"), y = new Person("Bình");
        swap(x, y);
        System.out.println("Q4 swap:    " + x.name + " " + y.name);

        Person an3 = new Person("An");
        tricky(an3);
        System.out.println("Q5 tricky:  " + an3.name);

        // Hai biến cùng giữ một giá trị tham chiếu => cùng một object
        Person p = new Person("An");
        Person q = p;
        q.name = "Giang";
        System.out.println("p == q: " + (p == q) + ", p.name = " + p.name);
    }
}
