public class Main {
    public static void main(String[] args) {
        OrderService real  = new OrderServiceImpl();
        OrderService proxy = TxProxy.wrap(real, OrderService.class);

        System.out.println("real  là class: " + real.getClass().getName());
        System.out.println("proxy là class: " + proxy.getClass().getName());

        System.out.println("\n1) proxy.save(\"sách\")");
        proxy.save("sách");

        System.out.println("\n2) real.save(\"bút\")   -- gọi thẳng object thật");
        real.save("bút");

        System.out.println("\n3) proxy.placeOrder(\"vở\")   -- bên trong gọi this.save()");
        proxy.placeOrder("vở");
    }
}
