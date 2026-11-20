public class BadBenchmark {
    static long work() {
        long sum = 0;
        for (int i = 0; i < 20_000; i++) {
            sum += i % 7 * 3 + (i ^ 5);
        }
        return sum;
    }

    public static void main(String[] args) {
        long start = System.nanoTime();
        long result = work();                       // chỉ đo đúng 1 lần
        long us = (System.nanoTime() - start) / 1_000;
        System.out.println("work() mất " + us + " µs  (kết quả " + result + ")");
    }
}
