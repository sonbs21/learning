public class ThisZero {
    int ten = 1;
    class KhongDungNgoai { int x() { return 42; } }       // không đụng tới object ngoài
    class CoDungNgoai    { int x() { return ten; } }      // đọc field của object ngoài
}
