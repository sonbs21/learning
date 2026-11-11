# Lộ trình & Kiến thức Java Core

## Lịch trình tổng quan (6 tuần)

| Tuần | Chủ đề chính | Trọng tâm |
|---|---|---|
| 1 | Nền tảng ngôn ngữ | Cú pháp, kiểu dữ liệu, OOP 4 tính chất |
| 2 | Collections & Generics | List/Set/Map, Generics, Comparable/Comparator |
| 3 | Exception, I/O & String | Xử lý lỗi, đọc/ghi file, String pool |
| 4 | Java Memory Model & JVM | Stack/Heap, GC, class loading |
| 5 | Multithreading & Concurrency | Thread, Executor, lock, concurrent collections |
| 6 | Java 8+ Modern Features | Lambda, Stream API, Optional |

**Gợi ý luyện tập:** Mỗi tuần làm 2-3 bài coding nhỏ áp dụng đúng chủ đề (ví dụ tuần 5: viết chương trình đa luồng đếm số bằng AtomicInteger vs synchronized). Cuối mỗi tuần tự giải thích lại bằng lời để chuẩn bị cho việc lên slide đào tạo.

---

## 1. Nền tảng ngôn ngữ (Syntax & Basics)
- Cấu trúc chương trình Java, JDK/JRE/JVM là gì, biên dịch và chạy (`javac`, `java`)
- Kiểu dữ liệu nguyên thủy (byte, short, int, long, float, double, char, boolean)
- Wrapper class, autoboxing/unboxing, cache của Integer (-128 → 127)
- Ép kiểu (casting): implicit vs explicit, widening vs narrowing
- Toán tử: arithmetic, relational, logical, bitwise, ternary, instanceof
- Cấu trúc điều khiển: if-else, switch (kể cả switch expression, pattern matching Java 17+)
- Vòng lặp: for, while, do-while, enhanced for, break/continue với label
- Mảng (array): 1 chiều, đa chiều, Arrays utility class

## 2. Hướng đối tượng (OOP)
- 4 tính chất: Encapsulation, Inheritance, Polymorphism, Abstraction
- Class, Object, Constructor (default, overloaded, constructor chaining `this()`/`super()`)
- Method overloading vs overriding
- `static` vs instance (member, method, block, nested class)
- `final` với biến, method, class
- Abstract class vs Interface (default method, static method, private method trong interface từ Java 9)
- Nested class: static nested, inner class, local class, anonymous class
- Enum: cách dùng nâng cao (enum với method, constructor, implement interface)
- Object class: `equals()`, `hashCode()`, `toString()`, `clone()`, quy tắc contract giữa equals/hashCode
- `instanceof` và pattern matching

## 3. Collections Framework
- Kiến trúc tổng thể: Collection, List, Set, Queue, Map (Map không extends Collection)
- List: ArrayList, LinkedList, Vector — cấu trúc bên trong, độ phức tạp O()
- Set: HashSet, LinkedHashSet, TreeSet — cơ chế hash, cây đỏ-đen
- Map: HashMap (cơ chế bucket, treeify khi collision từ Java 8), LinkedHashMap, TreeMap, Hashtable, ConcurrentHashMap
- Queue/Deque: PriorityQueue, ArrayDeque, LinkedList làm Queue
- Comparable vs Comparator, `Collections.sort()`, `Comparator.comparing()`
- Iterator, ListIterator, fail-fast vs fail-safe
- Collections utility class (sort, reverse, unmodifiableList, synchronizedList)
- Immutable collections (`List.of()`, `Collections.unmodifiableList`)

## 4. Generics
- Generic class, generic method
- Bounded type parameter (`<T extends Number>`)
- Wildcard: `<?>`, `<? extends T>`, `<? super T>`
- Type erasure và giới hạn của generics tại runtime
- Generic array (tại sao không tạo được `new T[]`)

## 5. Exception Handling
- Phân cấp: Throwable → Error / Exception → Checked / Unchecked (RuntimeException)
- try-catch-finally, thứ tự thực thi, finally với return
- try-with-resources, AutoCloseable
- Multi-catch (`catch (A | B e)`)
- Custom exception (checked vs unchecked)
- Exception chaining (`getCause()`)
- Best practice: không nuốt exception, không dùng exception cho control flow

## 6. String & Text Processing
- String immutability, String pool (constant pool)
- String vs StringBuilder vs StringBuffer (thread-safety)
- Các method quan trọng: substring, split, replace, format, intern()
- StringBuilder internal (mảng char/byte, capacity growth)
- Regular Expression (Pattern, Matcher)
- Text block (Java 15+)

## 7. I/O & NIO
- InputStream/OutputStream (byte stream), Reader/Writer (character stream)
- BufferedReader/BufferedWriter, FileReader/FileWriter
- Serialization: Serializable, transient, serialVersionUID
- NIO: Path, Files, Paths (Java 7+)
- NIO.2 channel & buffer (khái niệm cơ bản)

## 8. JVM & Memory Management
- Kiến trúc JVM: Class Loader Subsystem, Runtime Data Area, Execution Engine
- Vùng nhớ: Stack, Heap (Young Gen: Eden + Survivor, Old Gen), Metaspace (thay PermGen từ Java 8), PC Register, Native Method Stack
- Garbage Collection: thuật toán (Mark-Sweep-Compact), các loại GC (Serial, Parallel, CMS, G1, ZGC)
- GC roots, khi nào object eligible for GC
- ClassLoader: Bootstrap, Extension/Platform, Application, cơ chế delegation model
- Memory leak thường gặp: static collection, listener chưa unregister, ThreadLocal không remove
- `System.gc()`, finalize() (deprecated), phantom reference

## 9. Multithreading & Concurrency
- Thread: tạo bằng extends Thread / implements Runnable / Callable
- Thread lifecycle: New, Runnable, Blocked, Waiting, Timed Waiting, Terminated
- `synchronized`: method-level, block-level, intrinsic lock, reentrant lock
- `volatile` keyword, happens-before relationship
- Deadlock, livelock, starvation — nguyên nhân và cách phòng tránh
- wait()/notify()/notifyAll(), tại sao phải gọi trong synchronized block
- java.util.concurrent:
  - Executor, ExecutorService, ThreadPoolExecutor, ScheduledExecutorService
  - Future, Callable, CompletableFuture (thenApply, thenCombine, allOf)
  - Lock API: ReentrantLock, ReadWriteLock
  - Atomic classes: AtomicInteger, AtomicLong, AtomicReference
  - Concurrent collections: ConcurrentHashMap, CopyOnWriteArrayList, BlockingQueue
  - Synchronizer: CountDownLatch, CyclicBarrier, Semaphore
- ThreadLocal và ứng dụng

## 10. Java 8+ Modern Features
- Lambda expression, functional interface (`@FunctionalInterface`)
- Built-in functional interfaces: Function, BiFunction, Predicate, Supplier, Consumer
- Method reference: static, instance, constructor reference
- Stream API: map, filter, reduce, collect, flatMap, sorted, distinct
- Stream đặc điểm: lazy evaluation, intermediate vs terminal operation
- Collectors: toList, groupingBy, partitioningBy, joining, summarizing
- Optional: of, ofNullable, orElse, orElseGet, map, ifPresent — tránh lạm dụng
- Date/Time API mới: LocalDate, LocalDateTime, ZonedDateTime, Duration, Period
- Interface default/static method

## 11. Tính năng Java mới hơn (9-21, tùy nhu cầu đào tạo)
- var (local variable type inference, Java 10)
- Record (Java 16+) — immutable data carrier
- Sealed class (Java 17+)
- Pattern matching cho instanceof và switch
- Text block
- Module system (Java 9 — java.base, module-info.java) — mức khái niệm

## 12. Kiến thức bổ trợ khác
- Reflection API (Class, Method, Field — dùng để hiểu cách Spring hoạt động)
- Annotation: built-in (@Override, @Deprecated, @FunctionalInterface) và custom annotation
- Java Memory Model (JMM) — nguyên lý cơ bản cho concurrency
- equals/hashCode contract khi dùng object làm key trong HashMap
- Design pattern liên quan tới JDK (Singleton qua enum, Builder, Factory)
