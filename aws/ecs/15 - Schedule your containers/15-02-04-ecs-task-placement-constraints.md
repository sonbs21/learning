# Define which container instances Amazon ECS uses for tasks

> Nguồn: **Amazon ECS Developer Guide — Define which container instances Amazon ECS uses for tasks**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-placement-constraints.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng ý và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.

---

## Mục lục

1. [Placement constraint là gì](#1-placement-constraint-là-gì)
2. [Khai báo `placementConstraints`](#2-khai-báo-placementconstraints)
3. [Hai loại constraint](#3-hai-loại-constraint)
4. [Thuộc tính của container instance](#4-thuộc-tính-của-container-instance)
   - [4.1 Thuộc tính có sẵn (built-in)](#41-thuộc-tính-có-sẵn-built-in)
   - [4.2 Thuộc tính tuỳ chọn (optional)](#42-thuộc-tính-tuỳ-chọn-optional)
   - [4.3 Thuộc tính tuỳ chỉnh (custom)](#43-thuộc-tính-tuỳ-chỉnh-custom)
5. [Liên hệ với các file trước](#5-liên-hệ-với-các-file-trước)
6. [Tóm tắt](#6-tóm-tắt)

---

## 1. Placement constraint là gì

**📘 Theo docs AWS**

- Placement constraint là **quy tắc về container instance** mà ECS dùng để quyết định task **có được phép chạy** trên instance đó không.
- Phải có **ít nhất một** container instance khớp constraint. Không instance nào khớp thì task **ở lại `PENDING`**.
- Khi tạo service mới hoặc cập nhật service, bạn chỉ định được constraint cho task của service.

---

## 2. Khai báo `placementConstraints`

**📘 Theo docs AWS**

- Constraint được khai báo trong **service definition**, **task definition** hoặc **task**, bằng tham số `placementConstraints`.
- Mỗi phần tử có hai trường:
  - **`expression`** — biểu thức định nghĩa constraint;
  - **`type`** — loại constraint.

```json
"placementConstraints": [
  { "type": "<LOẠI_CONSTRAINT>", "expression": "<BIỂU_THỨC>" }
]
```

---

## 3. Hai loại constraint

**📘 Theo docs AWS**

| `type` | Ý nghĩa | Khai báo được khi |
|---|---|---|
| **`distinctInstance`** | Đặt **mỗi task đang hoạt động** lên **một container instance khác nhau**. | `RunTask`, `CreateService` |
| **`memberOf`** | Đặt task lên container instance **thoả một biểu thức**. | `RunTask`, `CreateService`, `RegisterTaskDefinition` (task definition mới hoặc revision mới), `UpdateService` |

Về **`distinctInstance`**:
- ECS dựa vào **desired status** của task khi đặt. Ví dụ: task cũ có desired status `STOPPED` (dù last status chưa phải `STOPPED`) thì task mới vẫn có thể được đặt **lên cùng instance**. Vì vậy có lúc thấy **2 task có last status `RUNNING`** trên cùng một instance.

> **Important (docs):** Ai cần **cô lập mạnh** giữa các task thì AWS khuyến nghị dùng **Fargate** — Fargate chạy mỗi task trong môi trường **ảo hoá phần cứng**, không chia sẻ network interface, ephemeral storage, CPU hay memory với task khác.

Về **`memberOf`**:
- Viết biểu thức bằng **cluster query language** để xác định các instance mà ECS được đặt task lên. Biểu thức là cách **nhóm instance theo thuộc tính**, đặt trong trường `expression`.

**💡 Giải thích thêm**

- `distinctInstance` **không khai báo được trong task definition** (chỉ ở `RunTask`/`CreateService`), còn `memberOf` thì được.
- Hiện tượng "2 task `RUNNING` trên cùng instance" liên quan trực tiếp tới file *Task lifecycle*: task cũ đã có `desiredStatus = STOPPED` nhưng `lastStatus` vẫn đang `RUNNING`/`DEACTIVATING`/`STOPPING` — ECS coi nó như đã đi, nên đặt task mới vào. Hay gặp lúc deploy.

Ví dụ minh hoạ (giá trị do mình đặt; cú pháp chi tiết của cluster query language nằm ở trang *Create expressions…*):

```json
"placementConstraints": [
  { "type": "distinctInstance" },
  { "type": "memberOf", "expression": "attribute:ecs.instance-type =~ t3.*" }
]
```

---

## 4. Thuộc tính của container instance

**📘 Theo docs AWS**

Bạn có thể gắn **metadata tuỳ chỉnh** lên container instance, gọi là **attribute**. Mỗi attribute có **tên** và **giá trị dạng chuỗi (tuỳ chọn)**. Dùng attribute có sẵn của ECS hoặc tự định nghĩa.

### 4.1 Thuộc tính có sẵn (built-in)

**📘 Theo docs AWS**

ECS tự gắn các thuộc tính sau:

| Thuộc tính | Ý nghĩa | Ví dụ giá trị (theo docs) |
|---|---|---|
| `ecs.ami-id` | ID AMI dùng để launch instance | `ami-1234abcd` |
| `ecs.availability-zone` | AZ của instance | `us-east-1a` |
| `ecs.instance-type` | Instance type | `g2.2xlarge` |
| `ecs.os-type` | Hệ điều hành | `linux`, `windows` |
| `ecs.os-family` | Phiên bản hệ điều hành | Linux: `LINUX`; Windows: dạng `WINDOWS_SERVER_<release>_<FULL hoặc CORE>` — `WINDOWS_SERVER_2022_FULL`, `WINDOWS_SERVER_2022_CORE`, `WINDOWS_SERVER_20H2_CORE`, `WINDOWS_SERVER_2019_FULL`, `WINDOWS_SERVER_2019_CORE`, `WINDOWS_SERVER_2016_FULL` |
| `ecs.cpu-architecture` | Kiến trúc CPU | `x86_64`, `arm64` |
| `ecs.vpc-id` | VPC instance được launch vào | `vpc-1234abcd` |
| `ecs.subnet-id` | Subnet instance đang dùng | `subnet-1234abcd` |

Về **`ecs.os-family`** và Windows:
- Quan trọng với Windows container (kể cả trên Fargate), vì **phiên bản OS của container Windows phải khớp với host**. Khác phiên bản thì container **không khởi động**. (Xem *Windows container version compatibility* của Microsoft — <https://learn.microsoft.com/en-us/virtualization/windowscontainers/deploy-containers/version-compatibility?tabs=windows-server-2022%2Cwindows-11>.)
- Cluster chạy nhiều phiên bản Windows thì dùng constraint `memberOf` với điều kiện `attribute:ecs.os-family` bằng đúng phiên bản cần. (Xem *Retrieving Amazon ECS-optimized Windows AMI metadata* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/retrieve-ecs-optimized_windows_AMI.html>.)

> **Note (docs):** Managed Instances chỉ hỗ trợ tập con: `ecs.subnet-id`, `ecs.availability-zone`, `ecs.instance-type`, `ecs.cpu-architecture`.

### 4.2 Thuộc tính tuỳ chọn (optional)

**📘 Theo docs AWS**

ECS **có thể** gắn thêm:

| Thuộc tính | Có thì nghĩa là | Tài liệu |
|---|---|---|
| `ecs.awsvpc-trunk-id` | Instance có **trunk network interface** | <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/container-instance-eni.html> |
| `ecs.outpost-arn` | Chứa ARN của **AWS Outpost** | <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/using-outposts.html> |
| `ecs.capability.external` | Instance là **external instance** (ECS Anywhere) | <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ecs-anywhere.html> |

### 4.3 Thuộc tính tuỳ chỉnh (custom)

**📘 Theo docs AWS**

Bạn gắn được thuộc tính tuỳ chỉnh, ví dụ tên **`stack`**, giá trị **`prod`**. Quy tắc:

| | Độ dài | Ký tự cho phép |
|---|---|---|
| `name` | 1–128 | chữ hoa/thường, số, `-`, `_`, `/`, `\`, `.` |
| `value` | 1–128 | chữ hoa/thường, số, `-`, `_`, `.`, `@`, `/`, `\`, `:`, khoảng trắng; **không** có khoảng trắng ở đầu/cuối |

**💡 Giải thích thêm**

Ví dụ dùng thuộc tính tuỳ chỉnh (lệnh `put-attributes` là phần mình bổ sung, trang này không nêu cách gắn):

```bash
aws ecs put-attributes --cluster <CLUSTER> \
  --attributes name=stack,value=prod,targetType=container-instance,targetId=<CONTAINER_INSTANCE_ARN>
```

```json
"placementConstraints": [
  { "type": "memberOf", "expression": "attribute:stack == prod" }
]
```

Một số cách dùng thuộc tính có sẵn (gợi ý của mình):

| Mục đích | Thuộc tính |
|---|---|
| Chỉ chạy trên GPU instance | `ecs.instance-type` |
| Chỉ chạy trên ARM | `ecs.cpu-architecture` |
| Ghim vào AZ/subnet cụ thể | `ecs.availability-zone`, `ecs.subnet-id` |
| Windows đúng phiên bản | `ecs.os-family` |
| Chỉ chạy trên instance có trunk ENI (mật độ task `awsvpc` cao) | `ecs.awsvpc-trunk-id` |
| Chạy trên ECS Anywhere | `ecs.capability.external` |

---

## 5. Liên hệ với các file trước

**💡 Giải thích thêm**

| Nội dung | File liên quan / ghi chú |
|---|---|
| Constraint không thoả → `PENDING` | *How Amazon ECS places tasks*, *Task lifecycle* |
| `distinctInstance` dựa vào desired status | *Task lifecycle* (`lastStatus` vs `desiredStatus`) |
| Managed Instances chỉ 4 thuộc tính | *task definition parameters for Managed Instances*, *Managed Instances task definition differences* — **khớp** |
| Fargate không hỗ trợ constraint | *How Amazon ECS places tasks*, *Fargate task definition differences* |
| `memberOf` với task group | *Group related Amazon ECS tasks* |
| Trunk ENI | *task networking for Managed Instances* (ENI trunking) |
| External instance | *Container image pull behavior* (EC2 và external instance) |

**Chỗ không khớp:** danh sách giá trị Windows của `ecs.os-family` trên trang này **không có** `WINDOWS_SERVER_2025_*` và `WINDOWS_SERVER_2004_CORE`, trong khi `operatingSystemFamily` ở file *task definition parameters for Amazon EC2* có các giá trị đó.

**Về vị trí khai báo:** trang này nói constraint khai báo được trong service definition, task definition hoặc task — khớp với bảng: `memberOf` có `RegisterTaskDefinition`, còn `distinctInstance` thì không.

---

## 6. Tóm tắt

- **Placement constraint** = quy tắc về container instance quyết định task có được chạy ở đó; không instance nào khớp → task ở `PENDING`.
- Khai báo `placementConstraints` (`type` + `expression`) trong service, task definition hoặc khi chạy task.
- **`distinctInstance`**: mỗi task trên một instance khác (chỉ `RunTask`/`CreateService`); dựa vào desired status nên đôi khi thấy 2 task `RUNNING` trên cùng instance. Cần cô lập mạnh → dùng Fargate.
- **`memberOf`**: instance thoả biểu thức cluster query language (`RunTask`, `CreateService`, `RegisterTaskDefinition`, `UpdateService`).
- **Thuộc tính built-in**: `ecs.ami-id`, `ecs.availability-zone`, `ecs.instance-type`, `ecs.os-type`, `ecs.os-family`, `ecs.cpu-architecture`, `ecs.vpc-id`, `ecs.subnet-id`. Managed Instances chỉ hỗ trợ subnet-id, availability-zone, instance-type, cpu-architecture.
- Windows container phải khớp phiên bản OS với host → dùng `memberOf` với `ecs.os-family`.
- **Thuộc tính optional**: `ecs.awsvpc-trunk-id`, `ecs.outpost-arn`, `ecs.capability.external`.
- **Thuộc tính custom**: tên và giá trị 1–128 ký tự theo bộ ký tự cho phép (vd `stack=prod`).
