public class Login {
    public static void main(String[] args) {
        String role = args[0];                     // dữ liệu từ bên ngoài
        System.out.println("role == \"admin\"      : " + (role == "admin"));
        System.out.println("role.equals(\"admin\") : " + role.equals("admin"));
    }
}
