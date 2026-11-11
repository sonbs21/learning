public class bai6 {
    public static void main(String[] args) {
        for (Operation op : Operation.values()) {
            System.out.println(3 + " " + op + " " + 4 + " = " + op.apply(3, 4));
        }
    }
}

enum Operation {
    PLUS("+") {
        public double apply(double a, double b) {
            return a + b;
        }
    },
    MINUS("-") {
        public double apply(double a, double b) {
            return a - b;
        }
    },
    TIMES("*") {
        public double apply(double a, double b) {
            return a * b;
        }
    },
    DIVIDE("/") {
        public double apply(double a, double b) {
            if (b == 0)
                throw new ArithmeticException("Not divided by 0");
            return a / b;
        }
    };

    private final String symbol; // field riêng của enum

    Operation(String symbol) { // constructor của enum — LUÔN private/package-private, không cần viết "private"
                               // tường minh
        this.symbol = symbol;
    }

    @Override
    public String toString() {
        return symbol; // khi in "op" ra, sẽ hiện "+" thay vì "PLUS"
    }

    public abstract double apply(double a, double b);
}