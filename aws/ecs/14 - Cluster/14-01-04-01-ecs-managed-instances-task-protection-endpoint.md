# Amazon ECS task scale-in protection endpoint

> Nguồn: **Amazon ECS Developer Guide — Amazon ECS task scale-in protection endpoint**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/managed-instance-task-scale-in-protection-endpoint.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng ý và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ hoặc ví dụ JSON chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.
> - Tên trường, mã lỗi giữ nguyên như docs; các ví dụ response được tóm thành bảng trường.

---

## Mục lục

0. [Endpoint là gì](#0-endpoint-là-gì)
1. [Tham số request](#1-tham-số-request)
2. [Ví dụ đặt bảo vệ](#2-ví-dụ-đặt-bảo-vệ)
3. [Tham số response](#3-tham-số-response)
4. [Response khi thất bại (`failure`)](#4-response-khi-thất-bại-failure)
5. [Response khi có lỗi (`error`)](#5-response-khi-có-lỗi-error)
6. [Mẫu code xử lý trong ứng dụng](#6-mẫu-code-xử-lý-trong-ứng-dụng)
7. [Tóm tắt](#7-tóm-tắt)

---

## 0. Endpoint là gì

**📘 Theo docs AWS**

- ECS container agent **tự chèn** biến môi trường **`ECS_AGENT_URI`** vào container của task, để container gọi được **API endpoint của agent**.
- AWS khuyến nghị dùng endpoint của agent cho task **tự xác định** được lúc nào cần bảo vệ.
- Khi container bắt đầu xử lý việc, đặt thuộc tính bảo vệ qua đường dẫn **`$ECS_AGENT_URI/task-protection/v1/state`** từ **bên trong container**.

| Phương thức | Ý nghĩa |
|---|---|
| **PUT** `…/task-protection/v1/state` | **Đặt** trạng thái bảo vệ |
| **GET** `…/task-protection/v1/state` | **Đọc** trạng thái bảo vệ hiện tại |

**💡 Giải thích thêm**

Endpoint này là cơ chế thứ nhất ("endpoint agent") ở file *Safely stop Amazon ECS workloads…*. Vì gọi qua agent, ứng dụng **không cần** AWS SDK hay tự ký request — agent dùng **task role** để gọi ECS API (nên task role cần `ecs:GetTaskProtection`, `ecs:UpdateTaskProtection`).

---

## 1. Tham số request

**📘 Theo docs AWS**

| Tham số | Kiểu | Bắt buộc | Ý nghĩa |
|---|---|---|---|
| **`ProtectionEnabled`** | Boolean | **Có** | `true` = đánh dấu task được bảo vệ; `false` = gỡ bảo vệ, task có thể bị dừng |
| **`ExpiresInMinutes`** | Integer | Không | Số phút được bảo vệ: tối thiểu **1**, tối đa **2880** (48 giờ). Trong thời gian này task **không** bị dừng bởi scale-in của **service Auto Scaling** hay **deployment**. Hết hạn thì `protectionEnabled` tự về `false`. **Không khai báo** → mặc định **120 phút** (2 giờ) |

---

## 2. Ví dụ đặt bảo vệ

**📘 Theo docs AWS**

Docs đưa ba ví dụ `curl` dùng **PUT** với header `Content-Type: application/json`:

| Ví dụ | Body |
|---|---|
| Bảo vệ với thời hạn **mặc định** (2 giờ) | `{"ProtectionEnabled":true}` |
| Bảo vệ **60 phút** | `{"ProtectionEnabled":true,"ExpiresInMinutes":60}` |
| Bảo vệ **24 giờ** | `{"ProtectionEnabled":true,"ExpiresInMinutes":1440}` |

Dạng lệnh:

```bash
curl --request PUT --header 'Content-Type: application/json' \
  "${ECS_AGENT_URI}/task-protection/v1/state" \
  --data '{"ProtectionEnabled":true,"ExpiresInMinutes":60}'
```

PUT trả về object **`protection`** gồm `ExpirationDate`, `ProtectionEnabled`, `TaskArn` (xem mục 3).

**💡 Giải thích thêm**

Gỡ bảo vệ (body suy ra từ định nghĩa tham số ở mục 1):

```bash
curl --request PUT --header 'Content-Type: application/json' \
  "${ECS_AGENT_URI}/task-protection/v1/state" \
  --data '{"ProtectionEnabled":false}'
```

Lưu ý: docs viết tên trường request là **`ProtectionEnabled`** / **`ExpiresInMinutes`** (chữ P, E viết hoa), nhưng trong mô tả lại nhắc `protectionEnabled` / `expiresInMinutes` (chữ thường đầu). Ví dụ `curl` chính thức dùng chữ hoa — nên theo ví dụ.

---

## 3. Tham số response

**📘 Theo docs AWS**

Khi thành công, response JSON có object **`protection`**:

| Trường | Ý nghĩa |
|---|---|
| **`ExpirationDate`** | Thời điểm hết hạn bảo vệ (docs gọi là **epoch time**). Task không được bảo vệ → **null** |
| **`ProtectionEnabled`** | `true` nếu đang bảo vệ, ngược lại `false` |
| **`TaskArn`** | ARN đầy đủ của task chứa container |

Đọc trạng thái:

```bash
curl --request GET "${ECS_AGENT_URI}/task-protection/v1/state"
```

**💡 Giải thích thêm**

Docs mô tả `ExpirationDate` là **epoch time**, nhưng cả hai ví dụ response lại cho giá trị dạng **ISO 8601** (vd `2023-12-20T21:57:44Z`, có ví dụ kèm mili giây `.837Z`). Khi parse, nên xử lý được **chuỗi thời gian ISO 8601** (có hoặc không có phần mili giây).

---

## 4. Response khi thất bại (`failure`)

**📘 Theo docs AWS**

Khi có **thất bại**, response có object **`failure`**:

| Trường | Ý nghĩa |
|---|---|
| **`Arn`** | ARN đầy đủ của task |
| **`Detail`** | Chi tiết liên quan tới thất bại |
| **`Reason`** | Lý do thất bại |

Ví dụ docs: task **không được bảo vệ** → `Reason` = **`TASK_NOT_VALID`**, `Detail` = `null`.

**💡 Giải thích thêm**

`TASK_NOT_VALID` có thể gặp khi task **không thuộc service** — vì protection chỉ hỗ trợ task của service (file *Safely stop…*). Đây là suy luận; docs chỉ nói ví dụ là "task không được bảo vệ".

---

## 5. Response khi có lỗi (`error`)

**📘 Theo docs AWS**

Khi có **exception**, response gồm:

| Trường | Ý nghĩa |
|---|---|
| **`requestID`** | AWS request ID của lời gọi ECS API gây exception |
| **`Arn`** | ARN đầy đủ của task hoặc service |
| **`Code`** | Mã lỗi |
| **`Message`** | Thông điệp lỗi |

> **Note (docs):** Gặp **`RequestError`** hoặc **`RequestTimeout`** thì nhiều khả năng là **vấn đề mạng**. Thử dùng **VPC endpoint cho Amazon ECS**.

Các lỗi docs đưa ví dụ:

| `Code` | Khi nào | Có `requestID`? |
|---|---|---|
| **`AccessDeniedException`** | Task role **không có quyền** (ví dụ docs: thiếu `ecs:GetTaskProtection`) | Có |
| **`RequestCanceled`** | Agent **không nhận được phản hồi** từ ECS endpoint (vd lỗi mạng, control plane ECS gặp sự cố); thông điệp kiểu "timed out calling … Task Protection API" | Không |
| **`ThrottlingException`** | Agent bị ECS **giới hạn tốc độ** gọi API ("Rate exceeded") | Có |

**💡 Giải thích thêm**

| Lỗi | Cách xử lý (gợi ý của mình) |
|---|---|
| `AccessDeniedException` | Thêm `ecs:GetTaskProtection`, `ecs:UpdateTaskProtection` vào **task role** (không phải execution role) |
| `RequestCanceled`, `RequestError`, `RequestTimeout` | Kiểm tra đường ra mạng tới ECS API; trong private subnet thì thêm **interface VPC endpoint cho ECS** (file *task networking for Fargate* có nhắc PrivateLink cho ECS API) |
| `ThrottlingException` | Giảm tần suất gọi; với job dài, gia hạn bảo vệ thưa hơn (vd mỗi 30 phút thay vì mỗi phút) |

---

## 6. Mẫu code xử lý trong ứng dụng

**💡 Giải thích thêm**

Mẫu Python (của mình, chỉ minh hoạ) cho worker xử lý message: bật bảo vệ trước khi xử lý, tắt khi xong, kiểm tra các dạng response ở mục 3–5.

```python
import os, json, urllib.request

URL = f"{os.environ['ECS_AGENT_URI']}/task-protection/v1/state"

def set_protection(enabled: bool, minutes: int | None = None) -> dict:
    body = {"ProtectionEnabled": enabled}
    if enabled and minutes:
        body["ExpiresInMinutes"] = minutes          # 1..2880
    req = urllib.request.Request(
        URL, data=json.dumps(body).encode(), method="PUT",
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=10) as resp:
        data = json.loads(resp.read())
    if "error" in data:
        raise RuntimeError(f"{data['error'].get('Code')}: {data['error'].get('Message')}")
    if "failure" in data:
        raise RuntimeError(f"failure: {data['failure'].get('Reason')}")
    return data["protection"]

def handle(message):
    set_protection(True, minutes=60)
    try:
        process(message)                            # công việc thật
    finally:
        set_protection(False)
```

Lưu ý thực tế: agent có retry sẵn (file *Safely stop…*), nhưng ứng dụng vẫn nên quyết định làm gì nếu **không bật được** bảo vệ (vd không lấy message mới, hoặc xử lý mà chấp nhận rủi ro).

---

## 7. Tóm tắt

- Agent chèn **`ECS_AGENT_URI`** vào container; endpoint **`$ECS_AGENT_URI/task-protection/v1/state`**: **PUT** để đặt, **GET** để đọc.
- **Request**: `ProtectionEnabled` (Boolean, bắt buộc), `ExpiresInMinutes` (Integer, 1–2880, mặc định 120). Hết hạn → tự về `false`.
- **Response thành công**: `protection` { `ExpirationDate` (null nếu không bảo vệ), `ProtectionEnabled`, `TaskArn` }.
- **Thất bại**: `failure` { `Arn`, `Detail`, `Reason` } — vd `TASK_NOT_VALID`.
- **Lỗi**: `requestID` + `error` { `Arn`, `Code`, `Message` } — vd `AccessDeniedException`, `RequestCanceled` (mạng / control plane), `ThrottlingException`. `RequestError` / `RequestTimeout` → kiểm tra mạng, cân nhắc VPC endpoint cho ECS.
