class Cha { Cha(String ten) { } }
class Con extends Cha {
    Con() {
        System.out.println("chạy trước super");
        super("Bình");
    }
}
