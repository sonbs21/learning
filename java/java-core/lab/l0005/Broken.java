public class Broken {
    public static void main(String[] args) {
        for (int i = 1; i <= 2; i++) {
            try {
                System.out.println("Lần " + i + ": " + Config.URL);
            } catch (Throwable e) {
                System.out.println("Lần " + i + ": " + e);
            }
        }
    }
}

class Config {
    static String URL = loadUrl();
    static String loadUrl() {
        throw new IllegalStateException("thiếu biến môi trường DB_URL");
    }
}
