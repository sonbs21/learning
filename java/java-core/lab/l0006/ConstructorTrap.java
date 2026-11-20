public class ConstructorTrap {
    static class Cha {
        Cha() {
            System.out.println("Cha(): gọi gioiThieu() → " + gioiThieu());
        }
        String gioiThieu() { return "tôi là Cha"; }
    }
    static class Con extends Cha {
        private final String ten = layTen();
        Con() {
            super();
            System.out.println("Con(): gọi gioiThieu() → " + gioiThieu());
        }
        static String layTen() { return "Bình"; }
        @Override
        String gioiThieu() { return "tôi là Con, tên " + ten; }
    }

    public static void main(String[] args) {
        new Con();
    }
}
