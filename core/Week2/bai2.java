public class bai2 {
    public static void main(String[] args) {
        Vehicle c = new Car( "Lexus", 6);
        System.out.println(c.toString());

        Car c1 = new Car("Lexus");
        System.out.println(c1.toString());
    }
}

class Vehicle {
    String brand;
    int wheels;

    Vehicle(String brand, int wheels) {
        this.brand = brand;
        this.wheels = wheels;
    }

    public String toString() {
        return "The vehicle is " + this.brand + " brand has " + this.wheels + " wheels";
    }
}

class Car extends Vehicle {
    int doors;

    Car(String brand, int doors) {
        super(brand, 4);       // constructor "đầy đủ" — gọi super, hardcode 4 bánh
    }
    
    Car(String brand) {
        this(brand, 4);        // constructor rút gọn — CHAIN qua constructor trên, mặc định 4 cửa
    }
    @Override
    public String toString() {
        return "This car is " + this.brand + " brand has " + this.doors + " doors," + this.wheels + " wheels";
    }

}

// super phải nằm đầu để khởi tạo và truyền value của Car và class cha là
// Vehicle


public class Giaibai2 {
    public static void main(String[] args) {
        GiaiCar c1 = new GiaiCar("Lexus", 6);  // constructor đầy đủ: 6 cửa, wheels hardcode = 4
        System.out.println(c1);

        GiaiCar c2 = new GiaiCar("Toyota");        // constructor rút gọn: mặc định 4 cửa, chain qua this()
        System.out.println(c2);
    }
}

class GiaiVehicle {
    String brand;
    int wheels;

    GiaiVehicle(String brand, int wheels) {
        this.brand = brand;
        this.wheels = wheels;
    }

    @Override
    public String toString() {
        return "The vehicle is " + brand + " brand, has " + wheels + " wheels";
    }
}

class GiaiCar extends GiaiVehicle {
    int doors;

    // Constructor "đầy đủ" — logic thật nằm ở đây, hardcode wheels = 4 vì "xe hơi luôn 4 bánh"
    GiaiCar(String brand, int doors) {
        super(brand, 4);
        this.doors = doors; // BẮT BUỘC — nếu thiếu dòng này, doors sẽ luôn = 0
    }

    // Constructor "rút gọn" — KHÔNG lặp lại logic, chỉ ủy quyền qua this(), mặc định 4 cửa
    GiaiCar(String brand) {
        this(brand, 4);
    }

    @Override
    public String toString() {
        return "This car is " + brand + " brand, has " + doors + " doors, " + wheels + " wheels";
    }
}

/*
 * Thí nghiệm bắt buộc của đề bài (mục 4): thử đặt super(...) KHÔNG PHẢI dòng đầu tiên.
 *
 * Car(String brand, int doors) {
 *     this.doors = doors;   // đặt TRƯỚC super() -> LỖI BIÊN DỊCH
 *     super(brand, 4);
 * }
 *
 * Trình biên dịch báo lỗi:
 *   error: call to super must be first statement in constructor
 *
 * Giải thích: Java bắt buộc object cha (Vehicle) phải được khởi tạo XONG HOÀN TOÀN
 * trước khi phần khởi tạo riêng của object con (Car) được phép chạy. Nếu cho phép
 * đặt code khác trước super(), đoạn code đó có thể vô tình đọc/ghi lên các field kế
 * thừa từ Vehicle (brand, wheels) khi chúng CHƯA TỪNG được khởi tạo -> object rơi vào
 * trạng thái không nhất quán ngay từ lúc "chưa sinh ra". Bắt super() đứng đầu đảm bảo:
 * phần của cha luôn được xây xong trước, con mới được phép xây tiếp phần của mình.
 */
