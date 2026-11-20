public class CayException {
    static void inDongHo(Class<?> c) {
        StringBuilder sb = new StringBuilder(c.getSimpleName());
        for (Class<?> p = c.getSuperclass(); p != null; p = p.getSuperclass()) sb.append(" → ").append(p.getSimpleName());
        System.out.println(sb);
    }
    public static void main(String[] args) {
        inDongHo(java.io.IOException.class);
        inDongHo(java.sql.SQLException.class);
        inDongHo(IllegalArgumentException.class);
        inDongHo(NullPointerException.class);
        inDongHo(OutOfMemoryError.class);
    }
}
