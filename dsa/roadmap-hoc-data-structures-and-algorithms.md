# Roadmap học Data Structures and Algorithms

> Lộ trình 18 tuần, khoảng 10–14 giờ mỗi tuần. Mục tiêu là xây nền tảng DSA đủ chắc để giải quyết bài toán backend, coding interview và tối ưu chương trình. Có thể dùng Java, C++, Python hoặc Go; nên chọn một ngôn ngữ và dùng nhất quán.

## 1. Mục tiêu đầu ra

Sau lộ trình, bạn có thể:

- Phân tích time complexity và space complexity bằng Big-O.
- Chọn cấu trúc dữ liệu dựa trên thao tác cần tối ưu.
- Cài đặt và sử dụng array, linked list, stack, queue, hash table, tree, heap và graph.
- Nhận diện pattern: two pointers, sliding window, prefix sum, binary search, recursion, backtracking, BFS/DFS, greedy và dynamic programming.
- Viết lời giải rõ ràng, kiểm tra edge case và chứng minh tính đúng đắn.
- Tối ưu từ brute force sang solution có complexity phù hợp.
- Đọc constraint để ước lượng complexity chấp nhận được.
- Giải bài dễ và trung bình độc lập; tiếp cận bài khó bằng cách phân rã.
- Trình bày solution trong coding interview.
- Xây một thư viện DSA nhỏ và bộ test cho các cấu trúc dữ liệu chính.

## 2. Nguyên tắc học DSA

1. Học concept rồi tự cài đặt, sau đó mới dùng thư viện chuẩn.
2. Mỗi bài phải phân tích complexity trước khi code.
3. Không xem lời giải quá sớm; dành 20–30 phút tự thử.
4. Khi xem lời giải, đóng lại và tự viết lại từ đầu.
5. Ghi lại pattern, không chỉ ghi đáp án.
6. Luôn test empty input, một phần tử, duplicate, giá trị âm, dữ liệu lớn và input đã sắp xếp.
7. Ôn lại bài theo chu kỳ: sau 1 ngày, 1 tuần và 1 tháng.
8. Ưu tiên hiểu invariant và proof hơn việc thuộc template.

## 3. Chuẩn bị trước khi học

### Kiến thức lập trình

- Biến, kiểu dữ liệu, function/method.
- Condition, loop và recursion cơ bản.
- Array/string và thao tác index.
- Class/object, reference/pointer ở mức ngôn ngữ đang dùng.
- Input/output, exception và unit test.
- Git và debugger.

### Công cụ

- IDE có debugger.
- REPL hoặc playground của ngôn ngữ.
- Test framework.
- Một nền tảng luyện bài như LeetCode, HackerRank, Codeforces hoặc NeetCode.
- File ghi chú pattern và complexity.

### Cấu trúc repository

~~~text
dsa-learning/
├── complexity/
├── arrays-strings/
├── linked-list/
├── stack-queue/
├── hashing/
├── sorting-searching/
├── trees/
├── heaps/
├── graphs/
├── greedy/
├── backtracking/
├── dynamic-programming/
├── advanced/
├── tests/
└── notes/
~~~

## 4. Big-O và cách đọc constraint

## Tuần 1 — Complexity, recursion và problem-solving foundation

### Học

- Time complexity và space complexity.
- Big-O, Big-Theta, Big-Omega ở mức trực quan.
- Best case, average case, worst case.
- Constant, logarithmic, linear, linearithmic, quadratic, exponential.
- Amortized analysis.
- Recursion tree và recurrence.
- Stack frame, base case và recursive case.
- Invariant và proof of correctness.
- Brute force, optimize, trade-off và constraints.

### Bảng ước lượng nhanh

| Constraint n | Complexity thường chấp nhận |
|---:|---|
| n ≤ 10 | O(n!) hoặc backtracking nhỏ |
| n ≤ 20 | O(2^n) có thể chấp nhận |
| n ≤ 100 | O(n^3) tùy giới hạn |
| n ≤ 1.000 | O(n^2) có thể chấp nhận |
| n ≤ 100.000 | O(n log n) hoặc O(n) |
| n ≥ 1.000.000 | Gần như cần O(n) hoặc tốt hơn |

### Thực hành

1. Phân tích complexity của 20 đoạn code.
2. Viết factorial, Fibonacci và binary exponentiation.
3. So sánh recursive Fibonacci với memoization.
4. Viết recurrence cho merge sort và binary search.
5. Tạo template giải bài gồm idea, invariant, complexity và edge cases.

### Tiêu chí hoàn thành

- Nhìn loop lồng nhau và nhận diện được complexity.
- Biết recursion dùng stack space.
- Đọc constraint trước khi chọn thuật toán.

## Tuần 2 — Array, string và prefix techniques

### Học

- Static array và dynamic array.
- Index access O(1), insert/delete giữa array O(n).
- In-place operation và extra space.
- String immutable/mutable.
- Frequency array.
- Prefix sum và difference array.
- Kadane algorithm cho maximum subarray.
- Matrix traversal, rotation và transpose.
- Coordinate/index mapping.

### Pattern

- Prefix sum.
- Difference array.
- In-place swap.
- Frequency counting.
- Running minimum/maximum.

### Bài tập

1. Two Sum bằng brute force rồi hash map.
2. Maximum subarray.
3. Product of array except self.
4. Merge sorted arrays.
5. Rotate array.
6. Longest common prefix.
7. Subarray sum equals k.
8. Range sum query.
9. Spiral matrix.
10. Set matrix zeroes.

### Tiêu chí hoàn thành

- Biết khi nào dùng extra array và khi nào cần in-place.
- Chuyển được bài range query sang prefix sum.
- Không lỗi index ở đầu/cuối array.

## Tuần 3 — Two pointers và sliding window

### Học

- Two pointers cùng chiều và ngược chiều.
- Fast/slow pointer.
- Sliding window fixed size.
- Sliding window variable size.
- Window invariant.
- Frequency map trong window.
- Khi nào window mở rộng/thu hẹp.
- Sorting trước khi dùng two pointers.

### Bài tập

1. Valid palindrome.
2. Two Sum II trên array đã sort.
3. Remove duplicates from sorted array.
4. Container with most water.
5. 3Sum.
6. Longest substring without repeating characters.
7. Longest repeating character replacement.
8. Minimum window substring.
9. Permutation in string.
10. Maximum sum subarray có kích thước k.

### Tiêu chí hoàn thành

- Viết được invariant “window luôn thỏa điều kiện nào”.
- Phân biệt fixed window và variable window.
- Không dùng nested loop dư thừa khi có thể dùng two pointers.

## Tuần 4 — Linked list

### Học

- Singly linked list và doubly linked list.
- Node, head, tail và size.
- Insert/delete đầu, cuối, giữa.
- Dummy/sentinel node.
- Fast/slow pointer.
- Reverse list iterative và recursive.
- Cycle detection bằng Floyd algorithm.
- Merge sorted linked lists.
- Reordering và partition linked list.
- Deep copy node có random pointer.

### Bài tập

1. Reverse linked list.
2. Merge two sorted lists.
3. Linked list cycle.
4. Remove nth node from end.
5. Middle of linked list.
6. Palindrome linked list.
7. Reorder list.
8. Add two numbers.
9. Copy list with random pointer.
10. Reverse nodes in k-group.

### Tiêu chí hoàn thành

- Vẽ pointer trước khi sửa link.
- Xử lý được empty list, một node và cycle.
- Biết khi nào dummy node làm code đơn giản hơn.

## Tuần 5 — Stack, queue và monotonic structures

### Học

- Stack: LIFO.
- Queue: FIFO.
- Deque.
- Array implementation và linked-list implementation.
- Monotonic increasing/decreasing stack.
- Parentheses matching.
- Expression parsing ở mức cơ bản.
- Queue trong BFS.
- Stack mô phỏng recursion.

### Bài tập

1. Valid parentheses.
2. Min stack.
3. Evaluate reverse Polish notation.
4. Daily temperatures.
5. Next greater element.
6. Largest rectangle in histogram.
7. Implement queue using stacks.
8. Implement stack using queues.
9. Sliding window maximum.
10. Basic calculator.

### Tiêu chí hoàn thành

- Nhận diện được bài “next greater/smaller” cần monotonic stack.
- Phân biệt queue dùng cho BFS và stack dùng cho DFS/recursion.
- Phân tích được amortized O(1) của một số cấu trúc.

## Tuần 6 — Hash table, set và frequency pattern

### Học

- Hash function, bucket và collision.
- Separate chaining và open addressing.
- Load factor và rehash.
- HashMap, HashSet và frequency map.
- Key mutability và hash contract.
- Trade-off time/space của hashing.
- Coordinate compression ở mức cơ bản.
- Prefix hash và duplicate detection.

### Bài tập

1. Two Sum.
2. Contains duplicate.
3. Group anagrams.
4. Top K frequent elements.
5. Longest consecutive sequence.
6. Subarray sum equals k.
7. Isomorphic strings.
8. Word pattern.
9. LRU cache.
10. Insert/delete/getRandom O(1).

### Tiêu chí hoàn thành

- Biết khi nào hash map biến O(n^2) thành O(n).
- Không dùng object key mutable làm key nếu hash contract không ổn định.
- Biết collision làm worst case xấu hơn average case.

## Tuần 7 — Sorting và binary search

### Học sorting

- Stability, in-place và comparison sort.
- Bubble, selection, insertion sort để hiểu nền tảng.
- Merge sort.
- Quick sort, pivot và worst case.
- Heap sort ở mức nhận biết.
- Counting sort, radix sort khi domain giới hạn.
- Comparator và sort object.

### Học binary search

- Binary search exact match.
- Lower bound và upper bound.
- Search trong answer space.
- First/last occurrence.
- Rotated sorted array.
- Monotonic predicate.

### Bài tập

1. Implement merge sort.
2. Implement quick sort.
3. Binary search cơ bản.
4. Search insert position.
5. First and last position.
6. Search rotated sorted array.
7. Find minimum in rotated array.
8. Koko eating bananas.
9. Capacity to ship packages.
10. Median of two sorted arrays ở mức thử thách.

### Tiêu chí hoàn thành

- Viết binary search không infinite loop.
- Nhận diện được predicate đơn điệu.
- Biết khi nào sort O(n log n) là đủ và khi nào cần linear solution.

## Tuần 8 — Recursion, backtracking và divide-and-conquer

### Học

- Decision tree.
- State, choice, constraint và undo.
- Backtracking template.
- Pruning.
- Permutation, combination và subset.
- Divide-and-conquer.
- Memoization trong recursion.
- Stack overflow và iterative alternative.

### Template tư duy

~~~text
backtrack(state):
    if state là solution:
        record solution
        return

    for choice hợp lệ:
        apply choice
        backtrack(state mới)
        undo choice
~~~

### Bài tập

1. Subsets.
2. Permutations.
3. Combination sum.
4. Letter combinations of a phone number.
5. Generate parentheses.
6. N-Queens.
7. Word search.
8. Palindrome partitioning.
9. Sudoku solver.
10. Merge sort bằng divide-and-conquer.

### Tiêu chí hoàn thành

- Xác định rõ state và choice trước khi code.
- Có pruning để tránh nhánh không thể thành công.
- Phân biệt backtracking với dynamic programming.

## Tuần 9 — Binary tree và binary search tree

### Học

- Tree terminology: root, leaf, height, depth, subtree.
- Binary tree và n-ary tree.
- Preorder, inorder, postorder, level-order.
- Recursive và iterative traversal.
- BST invariant.
- Search/insert/delete trong BST.
- Balanced tree và skewed tree.
- Height, diameter và path.
- Lowest common ancestor.

### Bài tập

1. Maximum depth.
2. Same tree.
3. Invert binary tree.
4. Binary tree level order traversal.
5. Validate BST.
6. Kth smallest in BST.
7. Lowest common ancestor.
8. Diameter of binary tree.
9. Binary tree right side view.
10. Serialize and deserialize binary tree.

### Tiêu chí hoàn thành

- Viết được cả recursive và iterative traversal.
- Không nhầm subtree với toàn bộ tree khi validate BST.
- Hiểu BST mất lợi thế nếu bị skewed.

## Tuần 10 — Heap, priority queue và trie

### Học heap

- Min heap và max heap.
- Heap property và array representation.
- Sift up, sift down.
- Build heap O(n).
- Insert/extract/peek.
- Priority queue.
- Top K và streaming data.

### Học trie

- Prefix tree.
- Insert/search/startsWith.
- Word dictionary và autocomplete.
- Memory trade-off của trie.

### Bài tập

1. Kth largest element.
2. Top K frequent words.
3. Merge K sorted lists.
4. Find median from data stream.
5. Task scheduler.
6. K closest points.
7. Implement heap.
8. Implement trie.
9. Word dictionary.
10. Word search II ở mức thử thách.

### Tiêu chí hoàn thành

- Chọn heap khi cần top/min/max động.
- Biết build heap khác với insert từng phần tử.
- Chọn trie khi truy vấn prefix quan trọng.

## Tuần 11 — Graph representation và traversal

### Học

- Directed/undirected graph.
- Weighted/unweighted graph.
- Adjacency list, adjacency matrix và edge list.
- BFS và DFS.
- Visited set.
- Connected components.
- Cycle detection.
- Bipartite graph.
- Grid như một graph.
- Recursive DFS và iterative DFS.

### Bài tập

1. Number of islands.
2. Clone graph.
3. Max area of island.
4. Flood fill.
5. Rotting oranges.
6. Course schedule.
7. Graph valid tree.
8. Number of connected components.
9. Is graph bipartite?
10. Pacific Atlantic water flow.

### Tiêu chí hoàn thành

- Chọn adjacency list cho graph thưa.
- Không quên mark visited đúng thời điểm.
- Phân biệt traversal graph với traversal tree.

## Tuần 12 — Shortest path, topological sort và union-find

### Học

- Topological sort bằng DFS và Kahn BFS.
- DAG và dependency ordering.
- Dijkstra cho cạnh không âm.
- Bellman-Ford ở mức nhận biết.
- 0-1 BFS ở mức mở rộng.
- Floyd-Warshall ở mức khái niệm.
- Minimum spanning tree.
- Union-Find/Disjoint Set Union.
- Path compression và union by rank/size.

### Bài tập

1. Course schedule II.
2. Alien dictionary ở mức thử thách.
3. Network delay time.
4. Cheapest flights within K stops.
5. Path with minimum effort.
6. Min cost to connect all points.
7. Number of provinces.
8. Redundant connection.
9. Accounts merge.
10. Kruskal/Prim implementation.

### Tiêu chí hoàn thành

- Không dùng Dijkstra cho cạnh âm.
- Nhận diện dependency graph cần topological sort.
- Biết Union-Find phù hợp với dynamic connectivity.

## Tuần 13 — Greedy algorithms

### Học

- Greedy choice và exchange argument.
- Khi greedy đúng và khi không đúng.
- Sort theo tiêu chí phù hợp.
- Interval scheduling.
- Sweep line.
- Heap trong greedy.
- Two heaps ở mức mở rộng.
- Local optimum so với global optimum.

### Bài tập

1. Assign cookies.
2. Jump game.
3. Jump game II.
4. Gas station.
5. Partition labels.
6. Non-overlapping intervals.
7. Merge intervals.
8. Meeting rooms.
9. Minimum arrows to burst balloons.
10. Task scheduler.

### Tiêu chí hoàn thành

- Viết được lý do tại sao greedy choice không làm mất optimum.
- Nhận diện interval sorting theo end time.
- Không dùng greedy chỉ vì code ngắn.

## Tuần 14 — Dynamic programming căn bản

### Học

- Optimal substructure và overlapping subproblems.
- State definition.
- Transition.
- Base case.
- Top-down memoization.
- Bottom-up tabulation.
- Space optimization.
- 1D và 2D DP.
- Knapsack pattern.
- Decision DP và counting DP.

### Framework 5 bước

1. Xác định state biểu diễn điều gì.
2. Viết recurrence từ state trước đó.
3. Xác định base case.
4. Chọn thứ tự tính.
5. Tối ưu space nếu cần.

### Bài tập

1. Climbing stairs.
2. House robber.
3. House robber II.
4. Coin change.
5. Decode ways.
6. Longest increasing subsequence.
7. Partition equal subset sum.
8. Unique paths.
9. Minimum path sum.
10. Word break.

### Tiêu chí hoàn thành

- Không bắt đầu bằng việc tạo bảng DP mù quáng.
- Giải thích được ý nghĩa từng ô.
- Chuyển được top-down sang bottom-up.

## Tuần 15 — Dynamic programming nâng cao

### Học

- 0/1 knapsack và unbounded knapsack.
- Subsequence DP.
- String DP.
- Interval DP ở mức nhận biết.
- Tree DP ở mức nhận biết.
- State machine DP.
- Bitmask DP ở mức mở rộng.
- DP kết hợp binary search.
- Tối ưu bằng prefix sum, monotonic queue hoặc bitset ở mức nâng cao.

### Bài tập

1. Longest common subsequence.
2. Edit distance.
3. Distinct subsequences.
4. Target sum.
5. Best time to buy/sell stock with states.
6. Burst balloons.
7. Matrix chain multiplication.
8. Palindrome partitioning II.
9. Regular expression matching.
10. Longest valid parentheses bằng DP hoặc stack.

### Tiêu chí hoàn thành

- Nhận diện được state nhiều chiều.
- Biết giới hạn state để tránh complexity quá lớn.
- Có thể so sánh DP với greedy hoặc graph shortest path.

## Tuần 16 — Advanced structures và range queries

### Học

- Fenwick tree/Binary Indexed Tree.
- Segment tree.
- Sparse table ở mức nhận biết.
- Monotonic queue.
- Ordered set/map.
- Interval tree ở mức khái niệm.
- Bit manipulation và bitmask.
- XOR properties.
- Bloom filter ở mức hệ thống.
- LRU/LFU cache design.

### Bài tập

1. Range sum query mutable.
2. Count of smaller numbers after self.
3. Sliding window maximum bằng deque.
4. Single number bằng XOR.
5. Counting bits.
6. Reverse bits.
7. Maximum XOR pair.
8. Implement LRU cache.
9. Implement LFU cache ở mức thử thách.
10. Dynamic range minimum query.

### Tiêu chí hoàn thành

- Biết khi nào prefix sum không đủ vì có update động.
- Chọn Fenwick cho point update/range query đơn giản.
- Chọn segment tree cho query/update linh hoạt hơn.

## Tuần 17 — Problem patterns và mixed practice

### Pattern cần tổng hợp

- Frequency map.
- Two pointers.
- Sliding window.
- Prefix sum.
- Binary search on answer.
- Fast/slow pointer.
- Monotonic stack.
- Heap top K.
- Interval sort.
- BFS shortest path.
- DFS connected components.
- Topological sort.
- Union-Find.
- Greedy.
- 1D/2D DP.
- Backtracking.
- Bit manipulation.

### Cách luyện

1. Chọn bài ngẫu nhiên, không nhìn tag.
2. Đọc constraint và tự dự đoán complexity.
3. Viết brute force cho input nhỏ để tạo oracle.
4. Tìm pattern.
5. Code và test.
6. Review complexity và edge case.
7. Viết lại solution tối ưu sau một ngày.

### Tiêu chí hoàn thành

- Giải ổn định bài easy trong 15–20 phút.
- Giải bài medium quen pattern trong 30–40 phút.
- Biết nói rõ khi chưa biết solution và cách tiếp cận.

## Tuần 18 — Mock interview và project thư viện DSA

### Project

Tạo một thư viện DSA nhỏ gồm:

- Dynamic array.
- Linked list.
- Stack/queue/deque.
- Hash map đơn giản.
- Min/max heap.
- BST.
- Graph adjacency list.
- BFS/DFS/Dijkstra.
- Union-Find.
- Sorting và binary search.

### Yêu cầu

- API rõ ràng.
- Unit test cho happy path và edge case.
- Complexity document.
- Benchmark cơ bản.
- README có ví dụ sử dụng.
- Không dùng thư viện built-in cho phần cài đặt lõi.

### Mock interview

- 4 buổi, mỗi buổi 45–60 phút.
- 5 phút làm rõ đề.
- 10 phút nêu brute force.
- 15–25 phút tối ưu và code.
- 5 phút test.
- 5 phút complexity và review.

### Tiêu chí hoàn thành

- Trình bày solution mạch lạc.
- Viết code không cần sửa nhiều syntax.
- Nêu được trade-off và cách cải thiện.

## 5. Các cấu trúc dữ liệu cần thuộc bản chất

| Cấu trúc | Truy cập | Tìm kiếm | Chèn/xóa | Khi nên dùng |
|---|---:|---:|---:|---|
| Array | O(1) | O(n) | O(n) giữa | Dữ liệu liên tục, truy cập index |
| Dynamic array | O(1) amortized | O(n) | O(n) giữa | Danh sách thay đổi cuối |
| Linked list | O(n) | O(n) | O(1) khi có node | Chèn/xóa theo node |
| Stack | O(1) top | O(n) | O(1) | LIFO, undo, DFS |
| Queue | O(1) ends | O(n) | O(1) | FIFO, BFS, jobs |
| Hash map | O(1) average | O(1) average | O(1) average | Lookup theo key |
| BST balanced | O(log n) | O(log n) | O(log n) | Ordered lookup |
| Heap | O(1) min/max | O(n) | O(log n) | Priority queue/top K |
| Trie | O(L) | O(L) | O(L) | Prefix/string dictionary |
| Graph | Tùy biểu diễn | Tùy thuật toán | Tùy biểu diễn | Quan hệ và dependency |

Worst case có thể khác average case, đặc biệt với hash table và cây không cân bằng.

## 6. Mẫu complexity cần nhớ

| Thuật toán | Time | Space |
|---|---:|---:|
| Array access | O(1) | O(1) |
| Linear search | O(n) | O(1) |
| Binary search | O(log n) | O(1) iterative |
| Hash lookup | O(1) average | O(n) |
| Merge sort | O(n log n) | O(n) |
| Quick sort | O(n log n) average | O(log n) average |
| BFS/DFS | O(V + E) | O(V) |
| Dijkstra heap | O((V + E) log V) | O(V) |
| Heap insert/extract | O(log n) | O(1) per operation |
| Union-Find | Gần O(1) amortized | O(V) |

## 7. Checklist giải một bài

### Bước 1 — Hiểu đề

- [ ] Input là gì?
- [ ] Output là gì?
- [ ] Có duplicate không?
- [ ] Có negative value/negative edge không?
- [ ] Ordering có quan trọng không?
- [ ] Constraint lớn nhất là bao nhiêu?

### Bước 2 — Tìm pattern

- [ ] Có cần frequency map không?
- [ ] Array đã sort chưa hoặc có nên sort không?
- [ ] Có subarray/substring liên tiếp không?
- [ ] Có dependency graph không?
- [ ] Có top K/min/max động không?
- [ ] Có state lặp lại không?

### Bước 3 — Thiết kế

- [ ] Brute force là gì?
- [ ] Vì sao brute force quá chậm?
- [ ] Invariant là gì?
- [ ] Data structure nào giữ invariant?
- [ ] Complexity có phù hợp constraint không?

### Bước 4 — Code và test

- [ ] Empty input.
- [ ] One element.
- [ ] All duplicate.
- [ ] Already sorted/reverse sorted.
- [ ] Min/max integer.
- [ ] Cycle hoặc disconnected graph.
- [ ] Overflow.

### Bước 5 — Review

- [ ] Time complexity.
- [ ] Space complexity.
- [ ] Có thể tối ưu space không?
- [ ] Có side effect không?
- [ ] Code có dễ giải thích không?

## 8. Lịch luyện tập hằng tuần

| Buổi | Thời lượng | Nội dung |
|---|---:|---|
| Buổi 1 | 90 phút | Học concept và tự cài đặt |
| Buổi 2 | 120 phút | 3–5 bài dễ |
| Buổi 3 | 120 phút | 2–3 bài trung bình |
| Buổi 4 | 90 phút | Review lỗi và viết lại |
| Buổi 5 | 60 phút | Ôn flashcard/complexity |

### Số bài tham khảo

- Tuần nền tảng: 10–15 bài.
- Tuần pattern: 15–20 bài.
- Tuần DP/graph: 12–18 bài chất lượng.
- Tránh chạy theo số lượng; một bài hiểu sâu hơn năm bài chép đáp án.

## 9. Cách ghi chú một bài

~~~text
Problem:
Input/Output:
Constraints:
Brute force:
Key observation:
Pattern:
Invariant:
Algorithm:
Correctness:
Time:
Space:
Edge cases:
Mistake:
Review dates:
~~~

## 10. Những lỗi thường gặp

- Học thuộc template mà không hiểu invariant.
- Dùng recursion sâu gây stack overflow.
- Quên reset visited giữa các lần DFS.
- Mark graph node quá muộn tạo duplicate work.
- Binary search cập nhật left/right sai.
- Nhầm substring với subsequence.
- Dùng sliding window cho bài không có tính monotonic.
- Dùng greedy mà không chứng minh choice.
- Dùng Dijkstra với cạnh âm.
- Quên duplicate trong backtracking.
- Không sort trước interval greedy hoặc two pointers.
- Tạo DP state quá lớn.
- Không kiểm tra integer overflow.
- Chỉ test happy path.

## 11. Mục tiêu theo cấp độ

### Beginner

- Big-O, array, string, linked list, stack, queue.
- Hash map/set.
- Sorting và binary search.
- Recursion cơ bản.

### Intermediate

- Two pointers, sliding window, prefix sum.
- Tree traversal, BST, heap.
- Graph BFS/DFS, topological sort.
- Greedy và 1D/2D DP.

### Advanced

- Dijkstra, MST, Union-Find.
- Trie, monotonic structures.
- Segment tree/Fenwick.
- Advanced DP và bitmask.
- Thiết kế LRU/LFU và mock interview.

## 12. Nguồn luyện tập

- LeetCode hoặc NeetCode cho pattern interview.
- HackerRank cho syntax và bài nhập môn.
- Codeforces/AtCoder cho contest và tư duy thuật toán.
- CLRS hoặc Algorithm Design Manual để học lý thuyết sâu.
- VisuAlgo để xem animation cấu trúc dữ liệu.
- Documentation của ngôn ngữ để hiểu implementation của collection chuẩn.

Chọn một nguồn chính để theo lộ trình và một nguồn phụ để mở rộng; không cần làm đồng thời quá nhiều nền tảng.

## 13. Lộ trình rút gọn 8 tuần

### Tuần 1

- Big-O, recursion, array và string.

### Tuần 2

- Two pointers, sliding window, prefix sum.

### Tuần 3

- Linked list, stack, queue, monotonic stack.

### Tuần 4

- Hash map, sorting, binary search.

### Tuần 5

- Binary tree, BST, heap, trie.

### Tuần 6

- Graph BFS/DFS, topological sort, Union-Find.

### Tuần 7

- Greedy, backtracking và DP căn bản.

### Tuần 8

- DP nâng cao, mixed practice và mock interview.

## 14. Tiêu chí hoàn thành toàn bộ roadmap

- [ ] Tự cài đặt được các cấu trúc dữ liệu lõi.
- [ ] Phân tích complexity trước khi code.
- [ ] Giải được ít nhất 150–250 bài có review.
- [ ] Có ghi chú cho các pattern chính.
- [ ] Có unit test cho thư viện DSA cá nhân.
- [ ] Giải thích được correctness của các thuật toán chính.
- [ ] Làm được ít nhất 4 mock interview.
- [ ] Giải bài medium quen pattern trong 30–40 phút.
- [ ] Biết nhận ra khi cần đọc thêm thay vì đoán.

Mục tiêu của DSA không phải ghi nhớ hàng trăm lời giải. Mục tiêu là nhìn một bài toán mới, xác định được constraint, chọn đúng mô hình dữ liệu, tìm pattern phù hợp và chứng minh rằng solution của mình đúng và đủ nhanh.
