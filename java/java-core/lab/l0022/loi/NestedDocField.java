public class NestedDocField {
    String tenQuan = "Cà phê";
    static class Nested {
        String chao() { return tenQuan; }       // static nested đọc field của object ngoài?
    }
}
