import java.util.HashSet;
import java.util.Objects;

public class bai3 {
    public static void main(String[] args) {
        HashSet<Employee> setEmp = new HashSet<Employee>();
        Employee e1 = new Employee(1, "Son");
        Employee e2 = new Employee(2, "Nam");
        Employee e3 = new Employee(2, "Trung");
        setEmp.add(e1);
        setEmp.add(e2);
        setEmp.add(e3);

        System.out.println(setEmp.size());
    }
}

class Employee {
    int id;
    String name;

    

    public Employee(int id, String name) {
        this.id = id;
        this.name = name;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Employee)) return false;
        Employee other = (Employee) obj;
        return this.id == other.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    
}

/*
Khi comment 
  @Override
    public int hashCode() {
        return Objects.hash(id);
    }

HashSet dùng hashCode() để xác định bucket (ngăn chứa) cho mỗi phần tử trước.
equals() chỉ được gọi để so sánh giữa các phần tử nằm chung 1 bucket — đây là cơ chế tối ưu, không phải so sánh với toàn bộ tập hợp.
Khi không override hashCode(), Employee dùng lại Object.hashCode() mặc định — dựa trên identity (gần như luôn khác nhau giữa 2 object, bất kể id có giống nhau hay không).
Vì vậy e2 và e3 (cùng id=2) rơi vào 2 bucket khác nhau.
HashSet không bao giờ có lý do để đem e2 ra so sánh với e3 bằng equals() (vì "chúng ở 2 ngăn khác nhau") — nên dù equals() vẫn đúng, nó không bao giờ được gọi giữa 2 object này.
Nói ngắn gọn: equals() quyết định "2 object có bằng nhau không", còn hashCode() quyết định "2 object có bao giờ được đem ra so sánh bằng equals() hay không". Thiếu hashCode() đúng, equals() dù viết đúng đến đâu cũng không có cơ hội chạy giữa các object liên quan.
*/