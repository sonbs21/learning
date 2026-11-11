// Bài 5 — đọc bytecode bằng javap.
// Biên dịch rồi xem bytecode (chạy từ thư mục java-core/):
//   javac -d /tmp/l5 lessons/code/L0005Bytecode.java
//   javap -c -p -cp /tmp/l5 L0005Bytecode
public class L0005Bytecode {

    static class Person {
        String name;
        Person(String name) { this.name = name; }
    }

    static int add(int a, int b) {
        int sum = a + b;
        return sum;
    }

    static void rename(Person p) {
        p.name = "Bình";
    }

    public static void main(String[] args) {
        int x = 10;
        int y = add(x, 5);
        Person an = new Person("An");
        rename(an);
        String s = "hi" == "hi" ? "same" : "diff";
        String t = an.name + y;
        System.out.println(s + " " + t);
    }
}
