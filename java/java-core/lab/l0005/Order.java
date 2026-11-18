public class Order {
    public static void main(String[] args) {
        System.out.println("--- new Child() lần 1");
        new Child();
        System.out.println("--- new Child() lần 2");
        new Child();
    }
}

class Parent {
    static { System.out.println("A. Parent static"); }
    Parent()  { System.out.println("C. Parent constructor"); }
}

class Child extends Parent {
    static { System.out.println("B. Child static"); }
    Child()   { System.out.println("D. Child constructor"); }
}
