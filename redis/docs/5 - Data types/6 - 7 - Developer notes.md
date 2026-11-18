# Ghi chú cho developer (Developer notes)

> Nguồn: <https://redis.io/docs/latest/develop/data-types/json/developer/>

Phát triển Redis JSON bao gồm việc thiết lập môi trường phát triển (có thể dựa trên Linux hoặc macOS), build RedisJSON (module Redis cung cấp JSON), chạy các bài test và benchmark, và debug cả module JSON lẫn các bài test của nó.

## Clone git repository (Cloning the git repository)

Để clone module RedisJSON và các submodule của nó, hãy chạy:
```sh
git clone --recursive https://github.com/RedisJSON/RedisJSON.git
```
## Làm việc trong một môi trường cô lập (Working in an isolated environment)

Có nhiều lý do để dùng một môi trường cô lập cho việc phát triển, như giữ cho máy làm việc của bạn sạch sẽ và phát triển cho một bản phân phối Linux khác.

Bạn có thể dùng một máy ảo (virtual machine) làm môi trường phát triển cô lập. Để thiết lập, bạn có thể dùng [Vagrant](https://www.vagrantup.com) hoặc Docker.

Để thiết lập một máy ảo bằng Docker:

```
rejson=$(docker run -d -it -v $PWD:/build debian:bullseye bash)
docker exec -it $rejson bash
```
Sau đó chạy ```cd /build``` từ bên trong container.

Ở chế độ này, mọi thứ được cài đặt đều nằm trong phạm vi của Docker container.
Sau khi thoát khỏi container, bạn có thể khởi động lại nó bằng lệnh ```docker exec``` trước đó, hoặc lưu trạng thái của container thành một image và tiếp tục dùng sau:

```
docker commit $rejson redisjson1
docker stop $rejson
rejson=$(docker run -d -it -v $PWD:/build redisjson1 bash)
docker exec -it $rejson bash
```

Bạn có thể thay `debian:bullseye` bằng hệ điều hành bạn chọn. Nếu bạn dùng cùng hệ điều hành với máy host, bạn có thể chạy file binary RedisJSON trên máy host sau khi nó được build.

## Cài đặt các điều kiện tiên quyết (Installing prerequisites)

Để build và test RedisJSON, bạn cần cài đặt một số package, tùy thuộc vào hệ điều hành bên dưới. Hiện tại, chúng tôi hỗ trợ Ubuntu/Debian, CentOS, Fedora, và macOS.

Vào thư mục `RedisJSON` và chạy:

```sh
$ ./sbin/setup
```

**Lệnh này sẽ cài đặt nhiều package khác nhau lên hệ thống của bạn** bằng trình quản lý package gốc (native package manager) và pip. Nó sẽ tự gọi `sudo`, và yêu cầu cấp quyền.

Nếu bạn muốn tránh điều đó, bạn có thể:

* Xem lại `system-setup.py` và cài đặt các package thủ công,
* Dùng `system-setup.py --nop` để hiển thị các lệnh cài đặt mà không thực thi chúng,
* Dùng một môi trường cô lập như đã giải thích ở trên,
* Dùng một môi trường ảo Python (virtual environment), vì các bản cài đặt Python được biết là dễ gặp vấn đề khi không được dùng cô lập: `python -m virtualenv venv; . ./venv/bin/activate`

## Cài đặt Redis (Installing Redis)

Nói chung, tốt nhất là chạy phiên bản Redis mới nhất.

Nếu hệ điều hành của bạn có package Redis 6.x, bạn có thể cài nó bằng trình quản lý package của hệ điều hành.

Nếu không, bạn có thể gọi
```sh
$ ./deps/readies/bin/getredis
```

## Xem trợ giúp (Getting help)

```make help``` cung cấp một bản tóm tắt nhanh về các tính năng phát triển:

```
make setup         # install prerequisites

make build
  DEBUG=1          # build debug variant
  SAN=type         # build with LLVM sanitizer (type=address|memory|leak|thread)
  VALGRIND|VG=1    # build for testing with Valgrind
make clean         # remove binary files
  ALL=1            # remove binary directories

make all           # build all libraries and packages

make test          # run both cargo and python tests
make cargo_test    # run inbuilt rust unit tests
make pytest        # run flow tests using RLTest
  TEST=file:name     # run test matching `name` from `file`
  TEST_ARGS="..."    # RLTest arguments
  QUICK=1            # run only general tests
  GEN=1              # run general tests on a standalone Redis topology
  AOF=1              # run AOF persistency tests on a standalone Redis topology
  SLAVES=1           # run replication tests on standalone Redis topology
  CLUSTER=1          # run general tests on a Redis Open Source Cluster topology
  VALGRIND|VG=1      # run specified tests with Valgrind
  VERBOSE=1          # display more RLTest-related information

make pack          # build package (RAMP file)
make upload-artifacts   # copy snapshot packages to S3
  OSNICK=nick             # copy snapshots for specific OSNICK
make upload-release     # copy release packages to S3

common options for upload operations:
  STAGING=1             # copy to staging lab area (for validation)
  FORCE=1               # allow operation outside CI environment
  VERBOSE=1             # show more details
  NOP=1                 # do not copy, just print commands

make coverage      # perform coverage analysis
make show-cov      # show coverage analysis results (implies COV=1)
make upload-cov    # upload coverage analysis results to codecov.io (implies COV=1)

make docker        # build for specific Linux distribution
  OSNICK=nick        # Linux distribution to build for
  REDIS_VER=ver      # use Redis version `ver`
  TEST=1             # test after build
  PACK=1             # create packages
  ARTIFACTS=1        # copy artifacts from docker image
  PUBLISH=1          # publish (i.e. docker push) after build

make sanbox        # create container for CLang Sanitizer tests
```

## Build từ mã nguồn (Building from source)

Chạy ```make build``` để build RedisJSON.

Lưu ý:

* Các file binary được đặt trong `target/release/`, tùy theo nền tảng và biến thể build.

* RedisJSON dùng [Cargo](https://github.com/rust-lang/cargo) làm hệ thống build. ```make build``` sẽ gọi cả Cargo lẫn lệnh `make` tiếp theo cần thiết để hoàn tất việc build.

Dùng ```make clean``` để xóa các artifact đã build. ```make clean ALL=1``` sẽ xóa toàn bộ thư mục con bin.

## Chạy test (Running tests)

Có một số bộ unit test:
* Các test Rust, được tích hợp trong mã nguồn, chạy bằng ```make cargo_test```.
* Các test Python (chạy nhờ RLTest), nằm trong ```tests/pytests```, chạy bằng ```make pytest```.

Bạn có thể chạy tất cả các test bằng ```make test```.
Để chỉ chạy các test cụ thể, dùng tham số ```TEST```. Ví dụ, chạy ```make test TEST=regex```.

Bạn có thể chạy các test của module trên một Redis instance "nhúng" (embedded) dùng một lần, hoặc trên một instance
do bạn cung cấp. Để dùng chế độ "nhúng", bạn phải đưa file thực thi `redis-server` vào `PATH` của mình.

Bạn có thể thay thế việc khởi chạy server nhúng bằng cách chỉ định một cổng Redis thông qua biến môi trường
`REDIS_PORT`, ví dụ:

```bash
$ # use an existing local Redis instance for testing the module
$ REDIS_PORT=6379 make test
```

## Debug (Debugging)

Để đưa thông tin debug vào, bạn cần đặt biến môi trường [`DEBUG`](https://redis.io/docs/latest/commands/debug) trước khi biên dịch RedisJSON. Ví dụ, chạy `export DEBUG=1`.

Bạn có thể thêm breakpoint vào các test Python ở chế độ chạy một test (single-test mode). Để đặt một breakpoint, hãy gọi hàm ```BB()``` bên trong một test.
