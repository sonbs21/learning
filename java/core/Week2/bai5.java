public class bai5 {
    public static void main(String[] args) {
        Animal a = new Dog();
        a.makeSound(); // (1)
        Dog d = new Dog();
        Animal ref = d;
        d.feed(d); // gọi hàm feed nào?
        ref.feed(d);
    }
}

class Animal {
    void makeSound() {
        System.out.println("Sound Animal");
    }

    void feed(Animal a) {
        System.out.println("Animal:"+a);
    }
}

class Dog extends Animal {

    @Override
    void makeSound() {
        System.err.println("Sound Dog");
    }

    void feed(Dog d) {
        System.out.println("Dog:"+d);
    }

}

/*
Vì makeSound() là override lớp con dùng lại của lớp cha
Trong khi feed() chỉ giống tên thôi
*/