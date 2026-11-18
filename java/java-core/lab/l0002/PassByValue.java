public class PassByValue {
    static class Box { int value; Box(int v) { value = v; } }

    static void changeInt(int x)      { x = 99; }
    static void changeField(Box b)    { b.value = 99; }
    static void reassign(Box b)       { b = new Box(99); }
    static void swap(Box a, Box b)    { Box t = a; a = b; b = t; }

    public static void main(String[] args) {
        int n = 1;
        changeInt(n);
        System.out.println("1. n = " + n);

        Box box = new Box(1);
        changeField(box);
        System.out.println("2. box.value = " + box.value);

        Box box2 = new Box(1);
        reassign(box2);
        System.out.println("3. box2.value = " + box2.value);

        Box p = new Box(1), q = new Box(2);
        swap(p, q);
        System.out.println("4. p = " + p.value + ", q = " + q.value);
    }
}
