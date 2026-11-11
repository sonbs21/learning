# Tuần 1: Complexity, recursion và nền tảng giải quyết vấn đề

## 1. Mục tiêu

Sau tuần này, bạn có thể:

- Ước lượng time complexity và space complexity của một đoạn code.
- Phân biệt trực quan Big-O, Big-Theta và Big-Omega.
- Nhận diện best case, average case và worst case.
- Hiểu các mức complexity phổ biến: `O(1)`, `O(log n)`, `O(n)`, `O(n log n)`, `O(n²)`, `O(2ⁿ)` và `O(n!)`.
- Viết recursion đúng với base case, recursive case và tiến trình tiến gần base case.
- Giải thích stack frame và space complexity của hàm đệ quy.
- Viết recurrence và hiểu recursion tree ở mức đủ dùng cho DSA.
- Dùng invariant và proof of correctness để giải thích vì sao thuật toán đúng.
- Đọc constraint trước khi chọn hướng giải.
- Chuyển một lời giải brute force thành lời giải tốt hơn bằng cách đánh đổi time và space.
- Trình bày lời giải theo một quy trình có thể lặp lại.

Thời lượng đề xuất: **10–14 giờ**, gồm lý thuyết, tự cài đặt, phân tích 20 đoạn code và làm bài tập.

---

## 2. Cách học trong tuần

Đừng bắt đầu bằng việc thuộc các công thức. Với mỗi bài toán, hãy thực hiện thứ tự sau:

1. Đọc đề và xác định input, output, constraint.
2. Viết một lời giải đơn giản nhất có thể.
3. Tìm invariant hoặc điều kiện luôn đúng trong quá trình chạy.
4. Phân tích time/space complexity trước khi tối ưu.
5. Tìm bottleneck của lời giải.
6. Tối ưu bằng cấu trúc dữ liệu hoặc pattern phù hợp.
7. Chứng minh ngắn gọn tính đúng đắn.
8. Viết code và test edge case.

Một lời giải tốt không chỉ là code chạy được. Nó còn phải trả lời được:

> Vì sao thuật toán đúng, chạy trong bao lâu, dùng thêm bao nhiêu bộ nhớ và có phù hợp với constraint không?

---

## 3. Complexity là gì?

Complexity mô tả tài nguyên mà thuật toán cần khi kích thước input tăng. Hai loại quan trọng nhất là:

- **Time complexity**: số lượng công việc hoặc bước tính toán tăng như thế nào theo input size.
- **Space complexity**: bộ nhớ tăng như thế nào theo input size.

Giả sử `n` là số phần tử của mảng. Ta thường không đếm chính xác từng phép cộng hay từng lệnh máy. Thay vào đó, ta quan tâm xu hướng tăng trưởng khi `n` rất lớn.

Ví dụ:

```java
int sum = 0;
for (int value : numbers) {
    sum += value;
}
```

Vòng lặp chạy một lần cho mỗi phần tử, nên time complexity là `O(n)`. Biến `sum` không phụ thuộc vào `n`, nên extra space là `O(1)`.

### 3.1. Input size không phải lúc nào cũng là `n`

Chọn đúng biến đo kích thước input rất quan trọng:

- Mảng có `n` phần tử: thường dùng `n`.
- Ma trận `r × c`: complexity thường là `O(r × c)`, không nhất thiết là `O(n²)`.
- String dài `m`: dùng `m`.
- Hai mảng dài `n` và `m`: giữ cả hai biến nếu chúng có vai trò khác nhau.
- Cây có `n` node và chiều cao `h`: một số thuật toán tốt hơn khi biểu diễn theo `h`.
- Đồ thị có `V` đỉnh và `E` cạnh: thường phân tích theo `O(V + E)`.

Không nên viết `O(n²)` cho mọi bài có hai vòng lặp. Nếu vòng ngoài chạy `r` lần và vòng trong chạy `c` lần thì complexity là `O(r × c)`.

---

## 4. Big-O, Big-Theta và Big-Omega

### 4.1. Big-O: cận trên

`O(f(n))` mô tả một cận trên về tốc độ tăng trưởng. Trong thực hành, Big-O thường được dùng để nói complexity tối đa hoặc worst-case.

Ví dụ, nếu một thuật toán có số bước là `3n + 10` thì:

```text
3n + 10 = O(n)
```

Ta bỏ hằng số và phần nhỏ hơn vì khi `n` rất lớn, `n` quyết định xu hướng chính.

### 4.2. Big-Omega: cận dưới

`Ω(f(n))` mô tả một cận dưới. Nếu thuật toán luôn phải đọc ít nhất một lần qua `n` phần tử, nó có lower bound `Ω(n)`.

Ví dụ, muốn xác nhận một giá trị không xuất hiện trong mảng chưa sắp xếp, về nguyên tắc phải kiểm tra toàn bộ mảng. Bài toán này có lower bound `Ω(n)` trong mô hình truy cập thông thường.

### 4.3. Big-Theta: cận chặt

`Θ(f(n))` nghĩa là tốc độ tăng trưởng vừa có cận trên vừa có cận dưới là `f(n)`.

Một vòng lặp luôn chạy đúng `n` lần có complexity `Θ(n)`. Khi nói “complexity là `O(n)`”, ta thường không phân biệt chi tiết giữa `O(n)` và `Θ(n)` trong giao tiếp hàng ngày, nhưng nên hiểu sự khác nhau.

### 4.4. Bỏ hằng số và số hạng nhỏ hơn

```text
5n + 20       → O(n)
2n² + 7n + 3  → O(n²)
n + log n      → O(n)
```

Quy tắc này không có nghĩa hằng số không quan trọng trong production. Một thuật toán `O(n)` có hằng số rất lớn vẫn có thể chậm. Big-O chỉ là mô hình tăng trưởng, không phải benchmark thực tế.

---

## 5. Các mức complexity thường gặp

Từ thường tốt hơn đến thường tệ hơn khi `n` tăng:

| Complexity | Tên gọi | Ví dụ điển hình |
|---:|---|---|
| `O(1)` | Constant | Truy cập `array[index]`, lấy phần tử đầu stack |
| `O(log n)` | Logarithmic | Binary search |
| `O(n)` | Linear | Duyệt mảng một lần |
| `O(n log n)` | Linearithmic | Merge sort, heap sort |
| `O(n²)` | Quadratic | So sánh mọi cặp phần tử |
| `O(n³)` | Cubic | Một số bài toán trên mọi bộ ba |
| `O(2ⁿ)` | Exponential | Duyệt mọi subset bằng recursion |
| `O(n!)` | Factorial | Duyệt mọi permutation |

Với `n` lớn, khác biệt giữa các mức này rất đáng kể:

```text
O(1)       không đổi theo n
O(log n)   tăng rất chậm
O(n)       tăng tỷ lệ thuận
O(n log n) gần tuyến tính
O(n²)      tăng nhanh
O(2ⁿ)      chỉ phù hợp với n nhỏ
O(n!)      chỉ phù hợp với n rất nhỏ
```

### 5.1. `O(1)` không có nghĩa là chạy trong một đơn vị thời gian

`O(1)` nghĩa là số bước không phụ thuộc vào `n`, không có nghĩa mọi thao tác đều nhanh như nhau. Một thao tác gọi service ngoài, cấp phát cấu trúc lớn cố định hoặc tính hash phức tạp vẫn có thể tốn thời gian đáng kể.

### 5.2. Logarithm trong thuật toán

Nếu mỗi bước giảm bài toán còn một nửa, số bước là `log₂ n`:

```text
n → n/2 → n/4 → n/8 → ... → 1
```

Cơ số của logarithm thường bị bỏ qua trong Big-O vì:

```text
log₂ n = log₁₀ n / log₁₀ 2 = Θ(log n)
```

### 5.3. Quy tắc cộng và nhân

Hai đoạn chạy tuần tự:

```java
for (int value : numbers) { // O(n)
    process(value);
}

for (int value : numbers) { // O(n)
    validate(value);
}
```

Tổng là `O(n + n) = O(n)`.

Vòng lặp lồng nhau:

```java
for (int i = 0; i < n; i++) {
    for (int j = 0; j < n; j++) {
        process(i, j);
    }
}
```

Vòng trong chạy `n` lần cho mỗi một trong `n` lần của vòng ngoài: `O(n × n) = O(n²)`.

### 5.4. Vòng lặp không phải lúc nào cũng là `O(n)`

```java
for (int i = 1; i < n; i *= 2) {
    process(i);
}
```

`i` nhân đôi sau mỗi vòng nên chỉ chạy khoảng `log₂ n` lần: `O(log n)`.

```java
for (int i = n; i > 0; i /= 2) {
    process(i);
}
```

Đây cũng là `O(log n)`.

```java
for (int i = 0; i < n; i++) {
    for (int j = 0; j < i; j++) {
        process(i, j);
    }
}
```

Số lần chạy là `0 + 1 + 2 + ... + (n - 1) = n(n - 1)/2`, nên là `O(n²)`, dù vòng trong không chạy đúng `n` lần ở mọi lượt.

---

## 6. Best case, average case và worst case

Xét linear search tìm `target` trong mảng:

```java
static int linearSearch(int[] numbers, int target) {
    for (int i = 0; i < numbers.length; i++) {
        if (numbers[i] == target) {
            return i;
        }
    }
    return -1;
}
```

- **Best case**: target ở vị trí đầu → `O(1)`.
- **Worst case**: target ở cuối hoặc không tồn tại → `O(n)`.
- **Average case**: phụ thuộc vào giả định phân bố vị trí của target; nếu các vị trí có khả năng như nhau thì khoảng `n/2` phép kiểm tra, vẫn là `O(n)`.

Khi đề không nói rõ, thường phân tích worst-case vì đó là cam kết an toàn của thuật toán. Tuy nhiên, đừng gọi mọi thứ là worst-case một cách máy móc; hãy nói rõ điều kiện đầu vào tạo ra trường hợp đó.

---

## 7. Space complexity và auxiliary space

Có hai cách dùng từ phổ biến:

- **Total space**: toàn bộ bộ nhớ bao gồm input.
- **Auxiliary space / extra space**: bộ nhớ thêm ngoài input.

Trong phỏng vấn, khi nói space complexity, cần nói rõ bạn đang tính cách nào.

```java
static int sum(int[] numbers) {
    int result = 0;
    for (int number : numbers) {
        result += number;
    }
    return result;
}
```

Hàm chỉ dùng vài biến phụ nên extra space là `O(1)`. Nếu tạo mảng copy:

```java
int[] copy = Arrays.copyOf(numbers, numbers.length);
```

extra space là `O(n)`.

### 7.1. In-place không đồng nghĩa với không dùng bộ nhớ

Một thuật toán in-place thường chỉ dùng `O(1)` extra heap space, nhưng vẫn có thể dùng call stack, temporary variable hoặc recursion stack. Khi phân tích, hãy tính cả những phần này.

### 7.2. Space-time trade-off

Ta thường dùng thêm bộ nhớ để giảm thời gian:

- Linear search: `O(n)` time, `O(1)` extra space.
- Dùng `HashSet` để kiểm tra duplicate: trung bình `O(n)` time, `O(n)` extra space.

Không có lựa chọn “tốt nhất” trong mọi bối cảnh. Constraint về memory, latency, throughput và độ đơn giản của code đều ảnh hưởng quyết định.

---

## 8. Amortized analysis

Amortized analysis tính chi phí trung bình trên một chuỗi thao tác, không phải average-case theo xác suất input.

Ví dụ dynamic array:

1. Khi còn chỗ, thêm phần tử tốn `O(1)`.
2. Khi đầy, tạo mảng lớn hơn và copy toàn bộ phần tử, một lần tốn `O(n)`.
3. Việc resize không xảy ra ở mọi lần thêm. Nếu capacity tăng theo hệ số, tổng số phần tử được copy qua nhiều lần resize là một cấp số nhân.

Vì vậy, `append` có:

- Worst-case của **một thao tác**: `O(n)`.
- Amortized complexity của **mỗi thao tác**: `O(1)`.

Đừng nhầm amortized `O(1)` với worst-case `O(1)`. Một thao tác cụ thể vẫn có thể đột ngột tốn `O(n)`.

Ví dụ tương tự thường gặp là stack hỗ trợ `push`, `pop`, hoặc cấu trúc dùng hai stack để mô phỏng queue. Cần xem tổng chi phí của cả chuỗi thao tác.

---

## 9. Recursion: tư duy đệ quy

Recursion là khi một hàm gọi lại chính nó trên một input nhỏ hơn hoặc đơn giản hơn.

Một hàm đệ quy đúng thường có ba thành phần:

1. **Base case**: trường hợp dừng, không gọi đệ quy nữa.
2. **Recursive case**: chia bài toán thành bài toán nhỏ hơn.
3. **Progress**: mỗi lần gọi phải tiến gần base case.

### 9.1. Factorial

Định nghĩa:

```text
0! = 1
n! = n × (n - 1)! với n > 0
```

Code Java:

```java
static long factorial(int n) {
    if (n < 0) {
        throw new IllegalArgumentException("n must be non-negative");
    }
    if (n == 0) {              // base case
        return 1;
    }
    return n * factorial(n - 1); // recursive case và progress
}
```

Trace với `factorial(4)`:

```text
factorial(4)
└── 4 * factorial(3)
    └── 3 * factorial(2)
        └── 2 * factorial(1)
            └── 1 * factorial(0)
                └── 1
```

Có `n + 1` stack frame nên time complexity là `O(n)` và auxiliary space là `O(n)`.

Lưu ý: `long` cũng có giới hạn. `factorial(21)` đã vượt quá giá trị `long` có thể biểu diễn. Complexity không đảm bảo không bị overflow.

### 9.2. Stack frame

Mỗi lần gọi hàm, runtime lưu một stack frame chứa thông tin như:

- Tham số và biến local.
- Địa chỉ để quay về sau khi hàm con kết thúc.
- Trạng thái tạm của phép tính.

Với:

```java
factorial(4)
```

stack sẽ chứa `factorial(4)`, `factorial(3)`, ..., đến `factorial(0)`. Sau khi base case trả về, các frame được gỡ theo thứ tự ngược lại.

Recursion quá sâu có thể gây `StackOverflowError`, ngay cả khi logic toán học là đúng.

### 9.3. Tổng từ 1 đến n

```java
static long sumTo(int n) {
    if (n <= 0) {
        return 0;
    }
    return n + sumTo(n - 1);
}
```

Time `O(n)`, auxiliary space `O(n)`. Phiên bản loop có cùng time nhưng `O(1)` extra space:

```java
static long sumToIterative(int n) {
    long sum = 0;
    for (int value = 1; value <= n; value++) {
        sum += value;
    }
    return sum;
}
```

Recursion làm code gần với định nghĩa toán học hơn, nhưng không phải lúc nào cũng tối ưu về memory.

---

## 10. Recursion trên mảng và string

Khi recursion xử lý một đoạn mảng, nên truyền biên `left` và `right` thay vì tạo subarray mới. Tạo subarray ở mỗi bước có thể làm tăng time hoặc space ngoài ý muốn.

### 10.1. Kiểm tra palindrome

Invariant: tại mỗi lần gọi, các cặp ký tự bên ngoài đoạn `[left, right]` đã được xác nhận bằng nhau.

```java
static boolean isPalindrome(String text, int left, int right) {
    if (left >= right) {
        return true;
    }
    if (text.charAt(left) != text.charAt(right)) {
        return false;
    }
    return isPalindrome(text, left + 1, right - 1);
}

static boolean isPalindrome(String text) {
    return isPalindrome(text, 0, text.length() - 1);
}
```

Mỗi lần bỏ hai ký tự, nên time `O(n)` và stack space `O(n)` theo số frame (chính xác hơn là `O(n/2)`, vẫn là `O(n)`).

### 10.2. Tìm phần tử lớn nhất

```java
static int max(int[] numbers, int index) {
    if (index == numbers.length - 1) {
        return numbers[index];
    }
    int maxOfRest = max(numbers, index + 1);
    return Math.max(numbers[index], maxOfRest);
}
```

Điều kiện cần quy ước mảng không rỗng. Nếu API cho phép mảng rỗng, phải validate hoặc trả về kiểu biểu diễn được “không có kết quả”, chẳng hạn `OptionalInt`.

---

## 11. Recursion tree và recurrence

Recurrence mô tả thời gian của một hàm đệ quy bằng thời gian của các lời gọi nhỏ hơn.

### 11.1. Binary search

Binary search bỏ một nửa không gian tìm kiếm sau mỗi bước:

```text
T(n) = T(n/2) + O(1)
T(n) = O(log n)
```

Điều kiện quan trọng: dữ liệu phải có thứ tự và mỗi bước phải loại bỏ được một nửa ứng viên.

Phiên bản iterative tránh dùng recursion stack:

```java
static int binarySearch(int[] sorted, int target) {
    int left = 0;
    int right = sorted.length - 1;

    while (left <= right) {
        int middle = left + (right - left) / 2;
        if (sorted[middle] == target) {
            return middle;
        }
        if (sorted[middle] < target) {
            left = middle + 1;
        } else {
            right = middle - 1;
        }
    }
    return -1;
}
```

Cách tính `left + (right - left) / 2` tránh một số trường hợp overflow khi `left + right` vượt quá giới hạn integer.

### 11.2. Merge sort

Merge sort chia mảng thành hai nửa, sắp xếp mỗi nửa rồi merge:

```text
T(n) = 2T(n/2) + O(n)
T(n) = O(n log n)
```

Giải thích bằng recursion tree:

- Tầng 0: merge `n` phần tử → tổng công việc `O(n)`.
- Tầng 1: hai lần merge, mỗi lần trên khoảng `n/2` → tổng vẫn `O(n)`.
- Mỗi tầng có tổng công việc `O(n)`.
- Có `log n` tầng.

Tổng là `O(n log n)`. Merge sort thường cần `O(n)` auxiliary space cho mảng tạm.

### 11.3. Fibonacci và cây gọi đệ quy

Phiên bản ngây thơ:

```java
static long fibonacciSlow(int n) {
    if (n <= 1) {
        return n;
    }
    return fibonacciSlow(n - 1) + fibonacciSlow(n - 2);
}
```

Nó tính lại cùng một subproblem nhiều lần:

```text
fib(5)
├── fib(4)
│   ├── fib(3)
│   └── fib(2)
└── fib(3)
    ├── fib(2)
    └── fib(1)
```

Số node của cây tăng theo cấp số nhân, nên time complexity là `O(2ⁿ)` ở mức ước lượng. Stack depth là `O(n)`.

Memoization lưu kết quả đã tính:

```java
static long fibonacciMemo(int n, long[] memo) {
    if (n <= 1) {
        return n;
    }
    if (memo[n] != -1) {
        return memo[n];
    }
    memo[n] = fibonacciMemo(n - 1, memo)
            + fibonacciMemo(n - 2, memo);
    return memo[n];
}
```

Có `n` subproblem, mỗi subproblem tính một lần:

- Time: `O(n)`.
- Memo array: `O(n)`.
- Recursion stack: `O(n)`.
- Tổng auxiliary space: `O(n)`.

Bottom-up dynamic programming còn có thể giảm stack space xuống `O(1)` nếu chỉ cần hai giá trị trước đó.

---

## 12. Khi nào dùng recursion?

Recursion phù hợp khi cấu trúc bài toán có tính đệ quy tự nhiên:

- Cây và filesystem: mỗi subtree là một bài toán nhỏ hơn.
- Divide and conquer: merge sort, quicksort, binary search.
- Backtracking: thử một lựa chọn rồi quay lui.
- DFS.
- Các định nghĩa toán học hoặc grammar.

Nên cân nhắc loop hoặc explicit stack khi:

- Độ sâu có thể rất lớn.
- Môi trường giới hạn call stack.
- Không cần biểu diễn tự nhiên bằng recursion.
- Muốn kiểm soát memory rõ ràng.

Recursion không tự động làm thuật toán nhanh hơn. Nó là một cách biểu diễn lời giải; complexity phụ thuộc vào số subproblem, kích thước subproblem và công việc mỗi lời gọi.

---

## 13. Invariant và proof of correctness

### 13.1. Invariant là gì?

Invariant là một mệnh đề luôn đúng ở một thời điểm xác định trong vòng lặp hoặc recursion.

Ví dụ tìm giá trị nhỏ nhất trong mảng:

```java
static int minimum(int[] numbers) {
    if (numbers.length == 0) {
        throw new IllegalArgumentException("numbers must not be empty");
    }

    int best = numbers[0];
    for (int index = 1; index < numbers.length; index++) {
        best = Math.min(best, numbers[index]);
    }
    return best;
}
```

Invariant sau khi xử lý đến `index`:

> `best` là giá trị nhỏ nhất trong đoạn `numbers[0..index]`.

### 13.2. Ba phần của loop proof

1. **Initialization**: invariant đúng trước vòng lặp hoặc sau lần khởi tạo.
2. **Maintenance**: nếu invariant đúng trước một vòng, nó vẫn đúng sau vòng đó.
3. **Termination**: khi vòng lặp kết thúc, invariant kết hợp với điều kiện dừng cho ra kết quả cần tìm.

Với `minimum`:

- Initialization: `best = numbers[0]`, đúng cho đoạn chỉ có phần tử đầu.
- Maintenance: so sánh `best` với phần tử mới, nên `best` vẫn là minimum của đoạn đã duyệt.
- Termination: đã duyệt toàn bộ mảng, nên `best` là minimum của toàn mảng.

### 13.3. Proof cho recursion

Với hàm palindrome:

> Giả thuyết: hàm trả về đúng việc đoạn `text[left..right]` có phải palindrome hay không.

- Base case `left >= right`: đoạn rỗng hoặc một ký tự luôn là palindrome.
- Nếu hai đầu khác nhau: chắc chắn không phải palindrome.
- Nếu hai đầu giống nhau: kết quả phụ thuộc chính xác vào đoạn nhỏ hơn `left + 1..right - 1`, được xử lý bởi lời gọi đệ quy.

Đây là cách dùng induction: đúng ở base case, và mỗi bước giả sử lời gọi nhỏ hơn đúng để suy ra lời gọi hiện tại đúng.

---

## 14. Problem-solving foundation

### Bước 1: Làm rõ đề bài

Hỏi hoặc tự xác định:

- Input có thể rỗng không?
- Có duplicate không?
- Giá trị có âm không?
- Input đã được sort chưa?
- Có cần giữ nguyên input không?
- Output cần index, giá trị, số lượng hay boolean?
- Có yêu cầu thứ tự kết quả không?
- Constraint lớn nhất là bao nhiêu?

Không nên code trước khi biết các giả định này.

### Bước 2: Đọc constraint

Bảng ước lượng nhanh theo roadmap:

| Constraint | Complexity thường có thể cân nhắc |
|---:|---|
| `n ≤ 10` | `O(n!)`, backtracking nhỏ |
| `n ≤ 20` | `O(2ⁿ)` có thể chấp nhận |
| `n ≤ 100` | `O(n³)` tùy giới hạn thời gian |
| `n ≤ 1.000` | `O(n²)` thường có thể chấp nhận |
| `n ≤ 100.000` | `O(n log n)` hoặc `O(n)` |
| `n ≥ 1.000.000` | Gần như cần `O(n)` hoặc tốt hơn |

Đây chỉ là heuristic. Thời gian thực tế còn phụ thuộc ngôn ngữ, hằng số, số test case, memory và giới hạn của nền tảng.

Ví dụ, nếu `n = 100.000`:

- `O(n²)` có khoảng 10 tỷ cặp, thường quá chậm.
- `O(n log n)` có khoảng 1,7 triệu đơn vị công việc theo mô hình đơn giản.
- `O(n)` thường là hướng phù hợp.

### Bước 3: Viết brute force trước

Brute force giúp:

- Xác nhận cách hiểu đề.
- Có baseline để kiểm tra lời giải tối ưu.
- Nhìn thấy bottleneck.
- Tránh tối ưu sai bài toán.

Brute force không phải code “vô dụng”. Nó chỉ không nhất thiết là lời giải cuối cùng.

### Bước 4: Tìm bottleneck

Hỏi:

- Có tính lại cùng một thứ không?
- Có tìm kiếm trong một vòng lặp không?
- Có đang tạo bản copy không cần thiết không?
- Dữ liệu có thể sort để giảm tìm kiếm không?
- Có thể lưu kết quả bằng map/array không?
- Có thể loại bỏ một nửa không gian ở mỗi bước không?

### Bước 5: Xác định trade-off

Ví dụ Two Sum:

Brute force thử mọi cặp:

```java
static int[] twoSumBruteForce(int[] numbers, int target) {
    for (int left = 0; left < numbers.length; left++) {
        for (int right = left + 1; right < numbers.length; right++) {
            if (numbers[left] + numbers[right] == target) {
                return new int[]{left, right};
            }
        }
    }
    return new int[0];
}
```

Complexity: `O(n²)` time, `O(1)` extra space.

Dùng map để lưu giá trị đã gặp:

```java
static int[] twoSum(int[] numbers, int target) {
    Map<Integer, Integer> indexByValue = new HashMap<>();

    for (int index = 0; index < numbers.length; index++) {
        int needed = target - numbers[index];
        Integer previousIndex = indexByValue.get(needed);
        if (previousIndex != null) {
            return new int[]{previousIndex, index};
        }
        indexByValue.put(numbers[index], index);
    }
    return new int[0];
}
```

Complexity trung bình: `O(n)` time, `O(n)` extra space.

Invariant:

> Trước khi xử lý `numbers[index]`, map chứa index của các giá trị trong đoạn đã duyệt trước đó.

Khi tìm thấy `needed`, ta biết cặp đó gồm một phần tử trước đó và phần tử hiện tại.

### Bước 6: Test theo nhóm edge case

Luôn kiểm tra tối thiểu:

- Input rỗng.
- Một phần tử.
- Hai phần tử.
- Duplicate.
- Giá trị âm hoặc bằng 0.
- Tất cả phần tử giống nhau.
- Không có đáp án.
- Đáp án ở đầu, ở cuối.
- Input đã sort và sort ngược.
- Giá trị lớn có thể gây overflow.
- Kích thước lớn để kiểm tra complexity.

---

## 15. Mẫu trình bày lời giải

Khi giải bài hoặc trình bày trong interview, dùng template sau:

```text
1. Hiểu đề:
   - Input:
   - Output:
   - Assumptions:

2. Ý tưởng:
   - Quan sát chính:
   - Cấu trúc dữ liệu/pattern:

3. Invariant hoặc proof:
   - Điều gì luôn đúng trong loop/recursion?

4. Pseudocode:

5. Complexity:
   - Time:
   - Extra space:

6. Edge cases:

7. Code và test:
```

Ví dụ phần complexity không nên viết chung chung là “tối ưu”. Hãy viết cụ thể:

```text
Mỗi phần tử được đưa vào map và đọc từ map tối đa một lần, nên time trung bình là O(n).
Map có thể chứa tối đa n phần tử, nên extra space là O(n).
```

---

## 16. Các lỗi phổ biến

### 16.1. Đếm số vòng lặp nhưng bỏ qua công việc bên trong

Nếu thân vòng lặp có một hàm `O(n)`, vòng lặp `n` lần có thể thành `O(n²)`. Luôn phân tích toàn bộ call chain ở mức cần thiết.

### 16.2. Gọi `contains` trên List trong vòng lặp

`ArrayList.contains` là `O(n)`. Đặt nó trong vòng lặp `n` lần sẽ thành `O(n²)`. Nếu không cần thứ tự và phù hợp về semantics, `HashSet` thường cho lookup trung bình `O(1)`.

### 16.3. Cho rằng mọi HashMap operation luôn là `O(1)`

`HashMap` thường được phân tích là `O(1)` trung bình, nhưng không phải guarantee tuyệt đối cho mọi tình huống. Cần tính collision, hash function, resize và chất lượng key.

### 16.4. Quên recursion stack

Một hàm đệ quy có thể không tạo collection nào nhưng vẫn dùng `O(n)` stack space.

### 16.5. Base case sai hoặc thiếu progress

Các dấu hiệu:

- Gọi lại với cùng input.
- Input không tiến gần base case.
- Base case không bao phủ input hợp lệ.
- Base case trả về giá trị không phù hợp với phép kết hợp.

### 16.6. Overflow trong phép tính trung gian

`int a + int b` có thể overflow trước khi gán vào `long`. Nếu cần, ép kiểu trước:

```java
long sum = (long) a + b;
```

### 16.7. Dùng subarray trong recursion mà không tính chi phí copy

Tạo mảng con ở mỗi lần gọi có thể biến một lời giải tưởng là `O(n log n)` thành tốn thêm `O(n log n)` space allocation hoặc nhiều chi phí copy. Truyền index hoặc dùng buffer chung khi phù hợp.

---

## 17. Bài tập thực hành

### Nhóm A — Phân tích complexity

Với mỗi đoạn code, hãy viết time complexity và extra space, sau đó giải thích ngắn gọn.

#### Bài 1

```java
for (int i = 0; i < n; i++) {
    System.out.println(i);
}
```

#### Bài 2

```java
for (int i = 1; i < n; i *= 2) {
    System.out.println(i);
}
```

#### Bài 3

```java
for (int i = 0; i < n; i++) {
    for (int j = 0; j < n; j++) {
        if (i == j) {
            System.out.println(i);
        }
    }
}
```

#### Bài 4

```java
for (int i = 0; i < n; i++) {
    for (int j = i; j < n; j++) {
        process(i, j);
    }
}
```

#### Bài 5

```java
int value = n;
while (value > 1) {
    value /= 2;
}
```

#### Bài 6

```java
for (int i = 0; i < n; i++) {
    for (int j = 0; j < m; j++) {
        process(i, j);
    }
}
```

#### Bài 7

```java
for (int i = 0; i < n; i++) {
    for (int j = 0; j < i; j++) {
        for (int k = 0; k < 10; k++) {
            process(i, j, k);
        }
    }
}
```

#### Bài 8

```java
int[] copy = Arrays.copyOf(numbers, numbers.length);
for (int value : copy) {
    process(value);
}
```

### Nhóm B — Recursion

1. Viết `power(base, exponent)` bằng recursion cho exponent không âm. Phân tích complexity của phiên bản nhân lặp và phiên bản binary exponentiation.
2. Viết hàm đệ quy đếm số chữ số của số nguyên dương.
3. Viết hàm đảo ngược một string bằng recursion.
4. Viết hàm kiểm tra mảng có tăng không giảm hay không.
5. Viết `binarySearch` recursive và iterative; so sánh space complexity.
6. Viết Fibonacci slow, memoized và bottom-up; đo số lần gọi hàm.
7. Viết hàm tính tổng các phần tử trong mảng bằng recursion trên range `[left, right]`.
8. Vẽ recursion tree cho `fibonacciSlow(5)`.

### Nhóm C — Problem solving

1. Tìm phần tử lớn thứ hai trong mảng, xử lý duplicate rõ ràng.
2. Kiểm tra mảng có duplicate: viết brute force, sort và HashSet.
3. Tìm số lần xuất hiện của mỗi giá trị.
4. Tìm cặp có tổng bằng target; viết ít nhất hai hướng giải.
5. Tìm maximum và minimum trong một lần duyệt.
6. Với mảng đã sort, tìm vị trí đầu tiên và cuối cùng của target.
7. Tính tổng `1 + 2 + ... + n` bằng loop, recursion và công thức; nêu trade-off.
8. Cho constraint `n ≤ 20`, thiết kế thuật toán sinh mọi subset.

Trước khi xem đáp án, nên dành ít nhất 20–30 phút cho mỗi bài và ghi lại:

```text
- Ý tưởng ban đầu
- Complexity dự kiến
- Edge cases
- Điều gì làm mình bị kẹt
```

---

## 18. Đáp án nhanh cho nhóm A

| Bài | Time | Extra space | Lý do chính |
|---:|---:|---:|---|
| 1 | `O(n)` | `O(1)` | Một vòng lặp tuyến tính |
| 2 | `O(log n)` | `O(1)` | Biến tăng gấp đôi |
| 3 | `O(n²)` | `O(1)` | Hai vòng lặp độc lập |
| 4 | `O(n²)` | `O(1)` | Tổng `n + (n-1) + ... + 1` |
| 5 | `O(log n)` | `O(1)` | Mỗi bước chia đôi |
| 6 | `O(nm)` | `O(1)` | Ma trận `n × m` |
| 7 | `O(n²)` | `O(1)` | Vòng `k` cố định 10, không tạo thêm bậc |
| 8 | `O(n)` | `O(n)` | Copy mảng dài n và duyệt copy |

Ở bài 7, tổng số lần `process` là `10 × (0 + 1 + ... + n - 1)`, nên vẫn là `O(n²)`, không phải `O(n³)` vì vòng trong cùng chỉ chạy 10 lần.

---

## 19. Mini project tuần 1

Tạo một package Java nhỏ, ví dụ `dsa.week1`, gồm:

```text
src/
└── dsa/week1/
    ├── ComplexityExamples.java
    ├── RecursionExamples.java
    ├── SearchExamples.java
    └── TwoSum.java
test/
└── dsa/week1/
    ├── RecursionExamplesTest.java
    ├── SearchExamplesTest.java
    └── TwoSumTest.java
```

Yêu cầu:

- Mỗi public method có JavaDoc ngắn nói rõ input assumptions.
- Mỗi method có comment complexity.
- Có test cho empty input, một phần tử, duplicate, negative value và input lớn.
- Có một test brute force dùng làm oracle cho lời giải tối ưu trên input nhỏ ngẫu nhiên.
- Không dùng recursion nếu độ sâu có thể phụ thuộc tuyến tính vào input lớn mà không có lý do rõ ràng.

Ví dụ comment nên cụ thể:

```java
// Time: O(n) average, O(n) worst-case tùy hành vi hash table.
// Extra space: O(n).
```

---

## 20. Checklist hoàn thành tuần

- [ ] Tôi giải thích được vì sao `3n² + 2n + 1` là `O(n²)`.
- [ ] Tôi phân biệt được time complexity và extra space.
- [ ] Tôi nhận ra loop nhân/chia biến là `O(log n)`.
- [ ] Tôi không nhầm hai vòng lặp tuần tự với hai vòng lặp lồng nhau.
- [ ] Tôi giải thích được best, average và worst case của linear search.
- [ ] Tôi phân biệt worst-case của một thao tác với amortized complexity.
- [ ] Tôi viết được recursion có base case và progress rõ ràng.
- [ ] Tôi tính được recursion stack space.
- [ ] Tôi viết được recurrence cho binary search và merge sort.
- [ ] Tôi giải thích được vì sao Fibonacci ngây thơ bị tính lặp.
- [ ] Tôi viết được invariant cho ít nhất hai vòng lặp.
- [ ] Tôi biết đọc constraint trước khi chọn `O(n²)`, `O(n log n)` hoặc `O(2ⁿ)`.
- [ ] Tôi trình bày lời giải bằng idea, proof, complexity và edge cases.
- [ ] Tôi hoàn thành ít nhất 20 bài phân tích code và các bài thực hành chính.

Nếu còn thiếu mục nào, hãy ôn lại đúng mục đó trước khi chuyển sang Tuần 2. Nền tảng complexity và invariant sẽ được dùng trong mọi cấu trúc dữ liệu và pattern về sau.
