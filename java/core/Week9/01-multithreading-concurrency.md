# Tuần 9: Multithreading & Concurrency

> Tài liệu tổng hợp kiến thức mục 9 trong lộ trình `java-core-roadmap.md`.

---

## 1. Thread: Tạo bằng extends Thread / implements Runnable / Callable

### Cách 1: extends Thread
```java
class MyThread extends Thread {
    @Override
    public void run() {
        System.out.println("Running in " + Thread.currentThread().getName());
    }
}

MyThread t = new MyThread();
t.start(); // KHÔNG gọi run() trực tiếp - start() mới tạo thread mới và gọi run() trên thread đó
```
**Nhược điểm**: Java chỉ hỗ trợ đơn kế thừa — nếu class đã `extends Thread`, không thể `extends` class nào khác nữa.

### Cách 2: implements Runnable (khuyến nghị hơn)
```java
class MyTask implements Runnable {
    @Override
    public void run() {
        System.out.println("Running in " + Thread.currentThread().getName());
    }
}

Thread t = new Thread(new MyTask());
t.start();

// Hoặc dùng lambda (Runnable là functional interface)
Thread t2 = new Thread(() -> System.out.println("Running via lambda"));
t2.start();
```
**Ưu điểm**: tách biệt "nhiệm vụ cần chạy" (`Runnable`) khỏi "cơ chế chạy nó" (`Thread`) — linh hoạt hơn, class vẫn có thể `extends` class khác, và **tái sử dụng được** cùng 1 task cho nhiều thread hoặc đưa vào `ExecutorService`.

### Cách 3: implements Callable (khi cần trả về giá trị hoặc ném checked exception)
```java
class MyCallableTask implements Callable<Integer> {
    @Override
    public Integer call() throws Exception { // khác Runnable.run(): CÓ return value, CÓ throws exception
        return 42;
    }
}

ExecutorService executor = Executors.newSingleThreadExecutor();
Future<Integer> future = executor.submit(new MyCallableTask());
Integer result = future.get(); // block chờ kết quả, có thể ném ExecutionException
executor.shutdown();
```

| | `Runnable` | `Callable<V>` |
|---|---|---|
| Method | `void run()` | `V call() throws Exception` |
| Trả về giá trị | Không | Có |
| Ném checked exception | Không | Có |
| Chạy trực tiếp bằng `Thread` | Có | Không (chỉ dùng được với `ExecutorService`) |

---

## 2. Thread Lifecycle

```
        new Thread()
             │
             ▼
          NEW ────────start()────────▶ RUNNABLE ◀──────────────┐
                                           │  ▲                 │
                          hết thời gian CPU│  │được cấp CPU lại │  synchronized lock
                          (do scheduler)   │  │                 │  được giải phóng
                                           ▼  │                 │
                                       (Runnable  <-------- BLOCKED
                                        cũng là trạng thái        (đang chờ
                                        "đang chạy" thực tế        vào synchronized
                                        trên CPU hoặc sẵn          block/method)
                                        sàng chờ được cấp CPU)
             │
             │ wait() / join() không timeout
             ▼
         WAITING ◀────────────────────────┐
             │  notify()/notifyAll()       │
             │  hoặc thread join xong      │
             ▼                             │
                                    wait(timeout) / sleep(ms) / join(ms)
                                            │
                                     TIMED_WAITING
             │
             │ run() kết thúc (bình thường hoặc do exception)
             ▼
        TERMINATED
```

| Trạng thái | Mô tả |
|---|---|
| **NEW** | Đã tạo object `Thread` nhưng chưa gọi `start()` |
| **RUNNABLE** | Đã gọi `start()` — đang chạy thực sự trên CPU HOẶC đang chờ được hệ điều hành cấp CPU (Java không phân biệt 2 trạng thái con này) |
| **BLOCKED** | Đang chờ để giành được 1 **intrinsic lock** (`synchronized`) mà thread khác đang giữ |
| **WAITING** | Đang chờ vô thời hạn cho đến khi thread khác gọi `notify()`/`notifyAll()` (sau `wait()`), hoặc chờ thread khác kết thúc (`join()` không timeout) |
| **TIMED_WAITING** | Giống `WAITING` nhưng có giới hạn thời gian chờ (`sleep(ms)`, `wait(ms)`, `join(ms)`) |
| **TERMINATED** | `run()` đã kết thúc (hoàn thành hoặc ném exception không bắt) |

```java
Thread t = new Thread(() -> {
    try { Thread.sleep(1000); } catch (InterruptedException e) { }
});
System.out.println(t.getState()); // NEW
t.start();
System.out.println(t.getState()); // RUNNABLE (hoặc TIMED_WAITING nếu đang sleep)
t.join();
System.out.println(t.getState()); // TERMINATED
```

---

## 3. `synchronized`: Method-level, Block-level, Intrinsic Lock, Reentrant Lock

### Vấn đề Race Condition
```java
class Counter {
    int count = 0;
    void increment() { count++; } // KHÔNG an toàn - count++ thực chất là 3 bước: đọc, cộng, ghi
    // Nếu 2 thread cùng gọi increment() đồng thời, có thể mất mát cập nhật (lost update)
}
```

### Method-level synchronized
```java
class Counter {
    int count = 0;
    synchronized void increment() { // lock trên "this" (đối tượng hiện tại)
        count++;
    }
    static synchronized void staticMethod() { // lock trên Class object (Counter.class), không phải instance
        // ...
    }
}
```

### Block-level synchronized (linh hoạt hơn — chỉ khóa phần code thực sự cần)
```java
class Counter {
    private final Object lock = new Object(); // nên dùng 1 object riêng làm lock, tránh lộ lock ra ngoài
    int count = 0;

    void increment() {
        // code không cần đồng bộ ở đây (chạy song song bình thường)
        synchronized (lock) { // chỉ khóa đoạn code thực sự cần thiết -> hiệu năng tốt hơn
            count++;
        }
    }
}
```

### Intrinsic Lock (Monitor Lock)
Mỗi object trong Java đều có sẵn **1 lock ngầm định** (intrinsic lock/monitor lock) gắn liền với nó. `synchronized` sử dụng chính lock này — không cần tạo lock riêng như các ngôn ngữ khác.

### Reentrant (tái nhập được)
`synchronized` là **reentrant** — 1 thread đã giữ lock của 1 object có thể **tiếp tục vào thêm** các block/method `synchronized` khác trên **cùng object đó** mà không bị tự deadlock với chính mình.

```java
class Counter {
    synchronized void outer() {
        System.out.println("outer");
        inner(); // gọi method synchronized khác trên CÙNG object -> KHÔNG bị block
    }
    synchronized void inner() {
        System.out.println("inner"); // vẫn vào được vì thread hiện tại ĐÃ giữ lock rồi (reentrant)
    }
}
```

---

## 4. `volatile` Keyword, Happens-Before Relationship

### Vấn đề Visibility (Khả năng nhìn thấy giữa các thread)
Do CPU cache riêng của mỗi core, 1 thread ghi giá trị vào biến có thể **chưa được ghi ngay xuống Main Memory** — thread khác đọc biến đó từ cache riêng của nó có thể thấy giá trị **cũ**.

```java
class Flag {
    boolean running = true; // KHÔNG volatile

    void stop() { running = false; } // Thread A gọi

    void doWork() {
        while (running) { // Thread B đọc - có thể ĐỌC MÃI giá trị cũ (true) từ cache riêng,
            // không bao giờ thấy Thread A đã set false -> vòng lặp vô hạn!
        }
    }
}
```

### `volatile` giải quyết vấn đề Visibility
```java
class Flag {
    volatile boolean running = true; // đảm bảo MỌI thread đọc/ghi trực tiếp Main Memory, không qua cache riêng

    void stop() { running = false; }
    void doWork() {
        while (running) { /* ... */ } // luôn thấy giá trị MỚI NHẤT
    }
}
```

**Lưu ý quan trọng**: `volatile` **CHỈ đảm bảo visibility, KHÔNG đảm bảo atomicity**:
```java
volatile int count = 0;
void increment() { count++; } // VẪN không an toàn! count++ vẫn là 3 bước (đọc-cộng-ghi),
                                // volatile chỉ đảm bảo MỖI bước đọc/ghi riêng lẻ là thấy giá trị mới nhất,
                                // không đảm bảo TOÀN BỘ thao tác count++ là 1 thao tác nguyên tử (atomic)
// -> Vẫn cần synchronized hoặc AtomicInteger cho trường hợp này
```

### Happens-Before Relationship
Là tập hợp quy tắc trong **Java Memory Model (JMM)** đảm bảo thứ tự quan sát được của các thao tác bộ nhớ giữa nhiều thread — nếu hành động A "happens-before" hành động B, thì kết quả của A **chắc chắn nhìn thấy được** bởi B.

Một số quy tắc happens-before quan trọng:
- **Program order rule**: trong 1 thread, các câu lệnh chạy theo đúng thứ tự viết trong code.
- **Monitor lock rule**: `unlock()` 1 monitor (thoát khỏi `synchronized`) happens-before `lock()` monitor đó của thread khác (vào `synchronized` sau đó).
- **Volatile variable rule**: ghi vào biến `volatile` happens-before mọi lần đọc biến đó sau này (từ bất kỳ thread nào).
- **Thread start rule**: `Thread.start()` happens-before mọi hành động bên trong `run()` của thread mới.
- **Thread join rule**: mọi hành động trong `run()` happens-before đoạn code sau `Thread.join()` thành công.

> Đây chính là nền tảng lý thuyết giải thích **vì sao** `synchronized` và `volatile` giải quyết được vấn đề visibility — không chỉ là "quy tắc thực hành" mà có cơ sở hình thức hóa rõ ràng trong JMM.

---

## 5. Deadlock, Livelock, Starvation

### Deadlock — 2 (hoặc nhiều) thread chờ nhau vô thời hạn
```java
Object lockA = new Object();
Object lockB = new Object();

// Thread 1
synchronized (lockA) {
    // ...
    synchronized (lockB) { /* ... */ } // chờ lockB
}

// Thread 2 (chạy đồng thời)
synchronized (lockB) {
    // ...
    synchronized (lockA) { /* ... */ } // chờ lockA
}
// Nếu Thread 1 giữ lockA và chờ lockB, ĐỒNG THỜI Thread 2 giữ lockB và chờ lockA
// -> CẢ 2 THREAD BỊ KẸT MÃI MÃI (deadlock)
```
**4 điều kiện cần để deadlock xảy ra** (Coffman conditions): Mutual Exclusion (lock độc quyền), Hold and Wait (giữ lock này chờ lock khác), No Preemption (không thể cưỡng chế lấy lại lock), Circular Wait (chờ vòng tròn).

**Cách phòng tránh**: luôn **quy định thứ tự lock cố định** (VD: luôn lock theo thứ tự alphabet của tên object, hoặc theo ID tăng dần) cho toàn bộ codebase — phá vỡ điều kiện "Circular Wait".
```java
// SỬA: cả 2 thread đều lock theo thứ tự CỐ ĐỊNH: lockA trước, lockB sau
synchronized (lockA) {
    synchronized (lockB) { /* ... */ }
}
```

### Livelock — thread không bị "kẹt cứng" nhưng cũng không tiến triển được
2 (hoặc nhiều) thread liên tục **phản ứng lẫn nhau** để "nhường đường", nhưng vì cả 2 luôn nhường cùng lúc nên không ai thực sự hoàn thành công việc (giống 2 người đi ngược chiều trong hành lang hẹp, cùng né sang 1 bên, rồi cùng né lại — cứ lặp lại mãi).

### Starvation — 1 thread không bao giờ được cấp tài nguyên/CPU
Xảy ra khi 1 thread có độ ưu tiên thấp liên tục bị các thread có độ ưu tiên cao hơn "chen ngang", khiến nó không bao giờ được thực thi (dù về lý thuyết không hề bị deadlock).

**Cách phòng tránh chung**: dùng cấu trúc dữ liệu công bằng (`ReentrantLock` với `fairness = true`), tránh giữ lock quá lâu, tránh thiết kế ưu tiên tuyệt đối.

---

## 6. `wait()`/`notify()`/`notifyAll()` — Vì sao phải gọi trong synchronized block

```java
class SharedBuffer {
    private final Object lock = new Object();
    private int data;
    private boolean available = false;

    void produce(int value) {
        synchronized (lock) {
            while (available) { // dùng while, KHÔNG dùng if (đề phòng "spurious wakeup")
                try { lock.wait(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            data = value;
            available = true;
            lock.notifyAll(); // đánh thức thread đang wait() trên CÙNG lock này
        }
    }

    int consume() {
        synchronized (lock) {
            while (!available) {
                try { lock.wait(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            available = false;
            lock.notifyAll();
            return data;
        }
    }
}
```

### Vì sao BẮT BUỘC gọi `wait()`/`notify()` bên trong `synchronized` block trên CÙNG object?
- `wait()` cần **giải phóng lock hiện tại 1 cách an toàn** trước khi thread đi ngủ (để thread khác có thể vào `synchronized` và gọi `notify()`) — điều này chỉ có thể thực hiện đúng nếu thread đang **thực sự giữ lock đó**.
- Nếu gọi `wait()` mà không giữ lock (không trong `synchronized`), JVM sẽ ném `IllegalMonitorStateException` ngay lập tức.
- **Race condition kinh điển nếu không đồng bộ đúng**: nếu kiểm tra điều kiện (`if (available)`) và gọi `wait()` không nằm trong cùng 1 khối `synchronized` liên tục, có thể xảy ra tình huống: thread A kiểm tra điều kiện (chưa sẵn sàng) → trước khi A kịp `wait()`, thread B đã set điều kiện và gọi `notify()` → tín hiệu `notify()` đó **bị "mất"** (không có ai đang wait() để nhận) → A vẫn `wait()` sau đó và **ngủ mãi mãi (missed signal)**.

### Vì sao dùng `while` thay vì `if` khi kiểm tra điều kiện trước `wait()`?
- **Spurious wakeup**: JVM cho phép thread bị đánh thức khỏi `wait()` mà **không có `notify()` nào thực sự xảy ra** (hiếm nhưng được đặc tả cho phép) — dùng `while` để kiểm tra lại điều kiện sau khi thức dậy, đảm bảo đúng logic dù bị đánh thức "giả".
- Ngoài ra, `notifyAll()` đánh thức **tất cả** thread đang chờ, nhưng chỉ 1 trong số đó thực sự "giành" được điều kiện phù hợp — các thread còn lại phải quay về `wait()` tiếp, nên bắt buộc phải kiểm tra lại điều kiện bằng vòng lặp `while`, không phải chỉ 1 lần bằng `if`.

---

## 7. `java.util.concurrent`

### 7.1. Executor, ExecutorService, ThreadPoolExecutor, ScheduledExecutorService

```java
ExecutorService executor = Executors.newFixedThreadPool(4); // pool 4 thread cố định

executor.execute(() -> System.out.println("Task 1")); // Runnable, không cần kết quả
Future<Integer> future = executor.submit(() -> 42);     // Callable, có kết quả trả về

executor.shutdown(); // không nhận task mới, đợi task hiện có hoàn thành
executor.shutdownNow(); // cố gắng dừng ngay lập tức (interrupt các thread đang chạy)
```

**Các loại Executor có sẵn:**
| Factory method | Đặc điểm |
|---|---|
| `newFixedThreadPool(n)` | Pool cố định n thread, task thừa xếp hàng đợi (`LinkedBlockingQueue` không giới hạn) |
| `newCachedThreadPool()` | Tạo thread mới khi cần, tái sử dụng thread rảnh, tự hủy thread rảnh quá 60s — dễ tạo QUÁ NHIỀU thread nếu không kiểm soát |
| `newSingleThreadExecutor()` | Chỉ 1 thread, task chạy tuần tự, đảm bảo thứ tự |
| `newScheduledThreadPool(n)` | Hỗ trợ chạy task định kỳ hoặc trễ (`schedule`, `scheduleAtFixedRate`) |

**`ThreadPoolExecutor`** — lớp nền tảng thực sự đứng sau các factory method trên, cho phép cấu hình chi tiết:
```java
ThreadPoolExecutor executor = new ThreadPoolExecutor(
    2,                          // corePoolSize - số thread tối thiểu luôn duy trì
    4,                          // maximumPoolSize - số thread tối đa
    60, TimeUnit.SECONDS,       // thời gian thread "thừa" (ngoài core) được giữ trước khi bị hủy
    new LinkedBlockingQueue<>(100), // hàng đợi chứa task khi core đã bận (xem lại Week3/05)
    new ThreadPoolExecutor.CallerRunsPolicy() // policy khi queue đầy VÀ đã đạt max thread
);
```

**`ScheduledExecutorService`** — chạy task định kỳ/trễ:
```java
ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
scheduler.schedule(() -> System.out.println("Run once after 5s"), 5, TimeUnit.SECONDS);
scheduler.scheduleAtFixedRate(() -> System.out.println("Every 2s"), 0, 2, TimeUnit.SECONDS);
```

### 7.2. Future, Callable, CompletableFuture

```java
// Future - kết quả bất đồng bộ CƠ BẢN, chỉ có get() (blocking) và cancel()
Future<Integer> future = executor.submit(() -> 42);
Integer result = future.get(); // BLOCK cho đến khi có kết quả

// CompletableFuture (Java 8+) - mạnh mẽ hơn nhiều, hỗ trợ CHAINING không cần block
CompletableFuture<Integer> cf = CompletableFuture.supplyAsync(() -> 10)
    .thenApply(x -> x * 2)         // biến đổi kết quả, chạy khi bước trước xong (10 -> 20)
    .thenApply(x -> x + 1);         // (20 -> 21)

cf.thenAccept(result -> System.out.println("Result: " + result)); // dùng kết quả, không trả về gì

// Kết hợp 2 CompletableFuture độc lập
CompletableFuture<Integer> cf1 = CompletableFuture.supplyAsync(() -> 10);
CompletableFuture<Integer> cf2 = CompletableFuture.supplyAsync(() -> 20);
CompletableFuture<Integer> combined = cf1.thenCombine(cf2, (a, b) -> a + b); // 30

// Chờ NHIỀU CompletableFuture hoàn thành cùng lúc
CompletableFuture<Void> all = CompletableFuture.allOf(cf1, cf2);
all.join(); // block đến khi TẤT CẢ hoàn thành
```

| | `Future` | `CompletableFuture` |
|---|---|---|
| Lấy kết quả | Chỉ `get()` (blocking) | `get()`, hoặc callback không-blocking (`thenApply`, `thenAccept`...) |
| Kết hợp nhiều task | Không hỗ trợ trực tiếp | `thenCombine`, `allOf`, `anyOf` |
| Xử lý exception | Phải tự bắt `ExecutionException` khi `get()` | `exceptionally()`, `handle()` |

### 7.3. Lock API: ReentrantLock, ReadWriteLock

**`ReentrantLock`** — thay thế linh hoạt hơn cho `synchronized`:
```java
ReentrantLock lock = new ReentrantLock();
lock.lock();
try {
    // critical section
} finally {
    lock.unlock(); // BẮT BUỘC unlock trong finally, khác synchronized (tự động unlock)
}

// Ưu điểm so với synchronized:
lock.tryLock();                          // thử lock, KHÔNG block nếu đang bị giữ, trả về false ngay
lock.tryLock(1, TimeUnit.SECONDS);       // thử lock có timeout
lock.lockInterruptibly();                 // có thể bị interrupt trong lúc đang chờ lock

ReentrantLock fairLock = new ReentrantLock(true); // fair lock - cấp lock theo đúng thứ tự chờ (FIFO), tránh starvation
```

**`ReadWriteLock`** — tách riêng lock cho đọc và ghi, cho phép **nhiều reader đọc đồng thời**, nhưng **writer thì độc quyền** (không ai được đọc/ghi cùng lúc với writer):
```java
ReadWriteLock rwLock = new ReentrantReadWriteLock();

void read() {
    rwLock.readLock().lock();
    try { /* đọc dữ liệu - nhiều thread đọc CÙNG LÚC được */ }
    finally { rwLock.readLock().unlock(); }
}

void write() {
    rwLock.writeLock().lock();
    try { /* ghi dữ liệu - ĐỘC QUYỀN, không ai khác được đọc/ghi cùng lúc */ }
    finally { rwLock.writeLock().unlock(); }
}
```
> Phù hợp cho dữ liệu **đọc nhiều, ghi ít** — hiệu năng tốt hơn `synchronized` (vốn độc quyền cả đọc lẫn ghi) trong trường hợp này.

### 7.4. Atomic Classes: AtomicInteger, AtomicLong, AtomicReference

Cung cấp thao tác **atomic (nguyên tử)** trên 1 biến duy nhất mà **không cần dùng lock** — dựa trên chỉ thị CPU **CAS (Compare-And-Swap)**.

```java
AtomicInteger counter = new AtomicInteger(0);

counter.incrementAndGet();          // ++count, atomic, không cần synchronized
counter.get();
counter.compareAndSet(5, 10);       // nếu giá trị hiện tại == 5, đổi thành 10 (atomic), trả về true/false
counter.updateAndGet(x -> x * 2);   // cập nhật bằng hàm tùy ý, atomic
```

**Cơ chế CAS (Compare-And-Swap):**
```
CAS(memoryLocation, expectedValue, newValue):
  if (giá trị hiện tại tại memoryLocation == expectedValue) {
      ghi newValue vào memoryLocation
      return true // thành công
  } else {
      return false // giá trị đã bị thread khác thay đổi, THẤT BẠI -> thường sẽ THỬ LẠI (retry loop)
  }
// Đây là 1 chỉ thị PHẦN CỨNG (CPU instruction) chạy NGUYÊN TỬ, không cần lock hệ điều hành
```

**So sánh với `synchronized`**: CAS là "lock-free" — không có thread nào bị **block** chờ, chỉ có thể phải **retry** (thử lại) nếu thất bại → hiệu năng tốt hơn đáng kể trong tình huống tranh chấp (contention) thấp đến vừa phải, vì tránh được chi phí context-switch của việc block/unblock thread.

### 7.5. Concurrent Collections
Đã trình bày chi tiết ở Tuần 3: [ConcurrentHashMap](../Week3/04-map-chi-tiet.md#25-concurrenthashmap--thread-safe-hiệu-năng-cao), [CopyOnWriteArrayList](../Week3/02-list-chi-tiet.md#24-copyonwritearraylist--thread-safe-cho-môi-trường-đọc-nhiều-ghi-ít), [BlockingQueue](../Week3/05-queue-deque-chi-tiet.md#24-blockingqueue--queue-cho-đa-luồng-producer-consumer).

### 7.6. Synchronizer: CountDownLatch, CyclicBarrier, Semaphore

**`CountDownLatch`** — 1 thread (hoặc nhiều) chờ cho đến khi N sự kiện khác hoàn thành (dùng 1 lần, không reset được):
```java
CountDownLatch latch = new CountDownLatch(3); // chờ 3 sự kiện

for (int i = 0; i < 3; i++) {
    new Thread(() -> {
        // làm việc gì đó...
        latch.countDown(); // báo hiệu "1 việc đã xong", giảm counter đi 1
    }).start();
}

latch.await(); // block cho đến khi counter về 0 (cả 3 thread đã countDown)
System.out.println("Tất cả đã hoàn thành!");
```

**`CyclicBarrier`** — N thread chờ NHAU tại 1 điểm (rào chắn), khi đủ N thread đến thì tất cả cùng tiếp tục (có thể **tái sử dụng nhiều lần**, khác `CountDownLatch`):
```java
CyclicBarrier barrier = new CyclicBarrier(3, () -> System.out.println("Cả 3 đã đến điểm hẹn!"));

for (int i = 0; i < 3; i++) {
    new Thread(() -> {
        // giai đoạn 1...
        try { barrier.await(); } catch (Exception e) { } // chờ 2 thread còn lại đến
        // giai đoạn 2 (chỉ chạy khi CẢ 3 đã qua điểm hẹn)
    }).start();
}
```

**`Semaphore`** — giới hạn số lượng thread được truy cập đồng thời vào 1 tài nguyên (VD: connection pool giới hạn 5 kết nối cùng lúc):
```java
Semaphore semaphore = new Semaphore(3); // tối đa 3 thread được vào cùng lúc

void accessResource() {
    try {
        semaphore.acquire(); // xin 1 "permit" - block nếu đã hết permit (đã có 3 thread đang dùng)
        // sử dụng tài nguyên giới hạn...
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    } finally {
        semaphore.release(); // trả lại permit, cho phép thread khác vào
    }
}
```

| | Dùng 1 lần | Tái sử dụng | Mục đích |
|---|---|---|---|
| `CountDownLatch` | Có | Không (phải tạo mới) | Chờ N sự kiện hoàn thành trước khi tiếp tục |
| `CyclicBarrier` | - | Có (tự reset sau mỗi vòng) | N thread đợi NHAU tại 1 điểm mốc |
| `Semaphore` | - | Có | Giới hạn số lượng truy cập đồng thời vào tài nguyên |

---

## 8. ThreadLocal và Ứng dụng

Cho phép mỗi **thread có 1 bản sao riêng biệt** của cùng 1 biến — không chia sẻ giữa các thread, nên không cần đồng bộ hóa.

```java
class UserContext {
    private static final ThreadLocal<String> currentUser = new ThreadLocal<>();

    static void setUser(String user) { currentUser.set(user); }
    static String getUser() { return currentUser.get(); }
    static void clear() { currentUser.remove(); } // BẮT BUỘC gọi khi dùng trong thread pool (xem Week8)
}
```

**Ứng dụng thực tế phổ biến:**
- Lưu thông tin request hiện tại (user đăng nhập, request ID) trong web server — mỗi request được xử lý bởi 1 thread riêng từ pool, `ThreadLocal` giúp truy cập thông tin này ở bất kỳ đâu trong call stack mà không cần truyền tham số qua nhiều tầng method.
- `SimpleDateFormat` (không thread-safe) — dùng `ThreadLocal<SimpleDateFormat>` để mỗi thread có 1 instance riêng, tránh việc chia sẻ 1 instance giữa nhiều thread gây lỗi.
- Quản lý transaction/connection trong ORM framework (Hibernate `Session` gắn với thread hiện tại).

> **Cảnh báo memory leak**: đã phân tích chi tiết ở [Week8/01-jvm-memory-management.md](../Week8/01-jvm-memory-management.md#3-threadlocal-không-remove) — luôn gọi `remove()` trong `finally` khi dùng `ThreadLocal` trong môi trường thread pool.

---

## Câu hỏi tự kiểm tra
1. So sánh 3 cách tạo Thread (`extends Thread`, `implements Runnable`, `implements Callable`) — khi nào chọn cách nào?
2. Vẽ lại vòng đời Thread và giải thích khi nào 1 thread chuyển từ `RUNNABLE` sang `BLOCKED` (khác với `WAITING`).
3. Giải thích khái niệm "reentrant" của `synchronized` bằng ví dụ 1 method synchronized gọi method synchronized khác trên cùng object.
4. `volatile` giải quyết vấn đề gì (visibility) và KHÔNG giải quyết được vấn đề gì (atomicity)? Cho ví dụ minh họa.
5. Vì sao phải gọi `wait()` bên trong khối `synchronized`? Điều gì xảy ra nếu kiểm tra điều kiện bằng `if` thay vì `while` trước khi gọi `wait()`?
6. Trình bày 4 điều kiện Coffman dẫn đến deadlock, và giải thích cách "quy định thứ tự lock cố định" phá vỡ điều kiện nào.
7. So sánh `Future` và `CompletableFuture` — vì sao `CompletableFuture` linh hoạt hơn trong việc kết hợp nhiều task bất đồng bộ?
8. Giải thích cơ chế CAS (Compare-And-Swap) đằng sau các lớp Atomic — vì sao nó được coi là "lock-free"?
9. Phân biệt `CountDownLatch` và `CyclicBarrier` — điểm khác biệt quan trọng nhất là gì?

## Bài tập luyện tập
- Viết chương trình minh họa race condition với `count++` không đồng bộ, sau đó sửa bằng `synchronized` và bằng `AtomicInteger`, so sánh kết quả.
- Cài đặt Producer-Consumer bằng `wait()`/`notifyAll()` thủ công (không dùng `BlockingQueue`), sau đó so sánh với cách dùng `ArrayBlockingQueue` (Week3).
- Viết chương trình minh họa deadlock giữa 2 thread với 2 lock, sau đó sửa bằng cách quy định thứ tự lock cố định.
- Dùng `ExecutorService` + `CompletableFuture` để gọi song song 3 "API" giả lập (dùng `Thread.sleep`), kết hợp kết quả bằng `thenCombine`/`allOf`.
- Viết chương trình dùng `CountDownLatch` để đợi 5 thread worker hoàn thành trước khi in ra tổng kết quả, và dùng `Semaphore` để giới hạn tối đa 2 thread truy cập 1 "resource" giả lập cùng lúc.
