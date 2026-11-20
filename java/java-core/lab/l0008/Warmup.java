public class Warmup {
    // Một việc tính toán nhỏ: cộng dồn rồi trộn số
    static long work() {
        long sum = 0;
        for (int i = 0; i < 20_000; i++) {
            sum += i % 7 * 3 + (i ^ 5);
        }
        return sum;
    }

    public static void main(String[] args) {
        long keep = 0;
        for (int round = 1; round <= 3000; round++) {
            long start = System.nanoTime();
            keep += work();
            long us = (System.nanoTime() - start) / 1_000;
            if (round <= 5 || round % 500 == 0) {
                System.out.printf("Lần gọi %4d: %5d µs%n", round, us);
            }
        }
        System.out.println("(kết quả: " + keep + ")");
    }
}
