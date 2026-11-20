public class Indy {
    public static void main(String[] args) {
        Runnable anDanh = new Runnable() { public void run() { System.out.println("ẩn danh"); } };
        Runnable lambda = () -> System.out.println("lambda");
        anDanh.run();
        lambda.run();
        System.out.println("Class của ẩn danh: " + anDanh.getClass().getName());
        System.out.println("Class của lambda:  " + lambda.getClass().getName());
    }
}
