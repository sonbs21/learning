# Creating a capacity provider for Amazon ECS Managed Instances

> Nguồn: **Amazon ECS Developer Guide — Creating a capacity provider for Amazon ECS Managed Instances**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/create-capacity-provider-managed-instances.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: diễn đạt lại bằng tiếng Việt, bám sát từng bước và đúng thứ tự của trang gốc, không thêm thông tin ngoài trang. Đây không phải bản dịch từng chữ; khi cần câu chữ hoặc ví dụ JSON chính xác, đọc link gốc ở trên.
> - Khối **💡 Giải thích thêm**: phần giải thích để dễ hiểu hơn, chỉ làm rõ những gì docs đã nói.
> - Ví dụ JSON / CLI dùng placeholder (`<...>`); tên trường giữ đúng như docs. Tên nút trên console giữ tiếng Anh (in đậm).

---

## Mục lục

0. [Tổng quan](#0-tổng-quan)
1. [Điều kiện tiên quyết](#1-điều-kiện-tiên-quyết)
2. [Tạo capacity provider bằng console](#2-tạo-capacity-provider-bằng-console)
3. [Tạo capacity provider bằng AWS CLI](#3-tạo-capacity-provider-bằng-aws-cli)
4. [Cấu trúc JSON đầy đủ (ghép từ các trang)](#4-cấu-trúc-json-đầy-đủ-ghép-từ-các-trang)
5. [Bước tiếp theo](#5-bước-tiếp-theo)
6. [Tóm tắt](#6-tóm-tắt)

---

## 0. Tổng quan

**📘 Theo docs AWS**

- Managed Instances dùng **capacity provider** để quản lý compute capacity.
- Tạo capacity provider **không** có `instanceRequirements` → ECS tự chọn instance **general-purpose tối ưu chi phí nhất**. (Xem *general-purpose instance types* — <https://aws.amazon.com/ec2/instance-types/general-purpose/>.)
- Tạo **với** `instanceRequirements` → khai báo thuộc tính instance như instance type, hãng CPU, loại accelerator…
- Custom capacity provider dùng **chọn instance theo thuộc tính (attribute-based)**: diễn đạt yêu cầu bằng tập thuộc tính, được **tự chuyển** thành mọi EC2 instance type khớp → đơn giản việc tạo và duy trì cấu hình instance type. (Xem *Amazon EC2 Fleet attribute-based instance type selection* — <https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-fleet-attribute-based-instance-type-selection.html>.)

**💡 Giải thích thêm**

"Attribute-based instance type selection" là cơ chế chung của EC2 Fleet / Auto Scaling — Managed Instances dùng lại cơ chế đó. Liên hệ file *Managed Instances instance types* (các nhóm thuộc tính) và *Instance selection best practices* (nên ưu tiên chọn theo thuộc tính).

---

## 1. Điều kiện tiên quyết

**📘 Theo docs AWS**

- Quyết định **loại giám sát** (basic / detailed). (Xem *Detailed monitoring for Amazon ECS Managed Instances* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/monitoring-managed-instances.html#detailed-monitoring-managed-instances>.)
- Có **cluster** sẵn hoặc dự định tạo. (Xem *Creating a cluster for Amazon ECS Managed Instances* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/create-cluster-managed-instances.html>.)
- Có IAM role cho Managed Instances:

| Role | Vai trò | Tài liệu |
|---|---|---|
| **Infrastructure role** | Cho ECS gọi dịch vụ AWS thay bạn để quản lý hạ tầng | <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/infrastructure_IAM_role.html> |
| **Instance profile** | Cấp quyền cho ECS container agent và Docker daemon trên managed instance | <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/managed-instances-instance-profile.html> |

- Hiểu cách chọn instance: *Instance selection best practices* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/managed-instances-instance-selection-best-practices.html>.

---

## 2. Tạo capacity provider bằng console

**📘 Theo docs AWS**

1. Mở console tại <https://console.aws.amazon.com/ecs/v2>.
2. Chọn **Region**.
3. Thanh bên → **Clusters**.
4. Chọn tên cluster.
5. Tab **Infrastructure**.
6. Mục **Capacity providers** → **Create capacity provider**.
7. **Capacity provider configuration**:
   - **Capacity provider name**: tên duy nhất.
   - **Capacity provider type**: **Amazon ECS Managed Instances**.
8. **Instance configuration**:
   - **Instance profile**: instance profile role đã tạo cho Managed Instances.
   - **Infrastructure role**: infrastructure role đã tạo cho Managed Instances.
9. **Instance requirements** — kết hợp tuỳ ý các thuộc tính:

| Thuộc tính | Ví dụ (theo docs) |
|---|---|
| **vCPU count** | `4`, hoặc dải `8-16` |
| **Memory (MiB)** | `8192`, hoặc dải `16384-32768` |
| **Instance types** | `m5.large,m5.xlarge,c5.large` |
| **CPU manufacturers** | `intel`, `amd`, `amazon-web-services` |
| **Accelerator types** | `gpu`, `fpga`, `inference` |
| **Accelerator count** | `1`, hoặc dải `2-4` |

10. **Advanced configuration** — giám sát:
    - **Basic**: CloudWatch gửi metric **status check**;
    - **Detailed**: CloudWatch gửi **mọi** metric.
11. (Tuỳ chọn) **Tags**: chọn **Propagate tags from** → **Capacity provider** để lan tag sang tài nguyên được quản lý (vd instance launch từ capacity provider). **Add tag** → nhập **Key**, **Value**.
12. **Create**.

**💡 Giải thích thêm**

- Capacity provider được tạo **trong cluster** (bước 4–6) — khớp ý "chỉ dùng trong cluster đã chỉ định" ở file *Migrate from Fargate*.
- Mô tả mức **Basic** ở trang này ("chỉ gửi status check") gọn hơn trang *Managed Instances capacity providers* ("phần lớn metric 5 phút, status check 1 phút"). Hai trang cùng ý: basic có metric thưa hơn, detailed có mọi metric 1 phút.
- Hãng CPU `amazon-web-services` = **Graviton** (ARM) → image phải có bản ARM64 (file *Managed Instances instance types*).

---

## 3. Tạo capacity provider bằng AWS CLI

**📘 Theo docs AWS**

- Dùng **phiên bản AWS CLI mới nhất** (<https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html>).

1. Chạy `create-capacity-provider` với file JSON:

```bash
aws ecs create-capacity-provider --cli-input-json file://capacity-provider-definition.json
```

Ví dụ JSON của docs khai báo **yêu cầu instance cơ bản**, **kích thước storage** và **bật tag propagation**. Cấu trúc các trường:

| Trường | Vị trí | Giá trị trong ví dụ docs |
|---|---|---|
| `name` | gốc | tên capacity provider |
| `cluster` | gốc | tên cluster |
| `tags` | gốc | một tag `version=test` |
| `managedInstancesProvider.infrastructureRoleArn` | | ARN infrastructure role |
| `managedInstancesProvider.instanceLaunchTemplate.ec2InstanceProfileArn` | | ARN instance profile |
| `…instanceLaunchTemplate.instanceRequirements.vCpuCount` | | min **4**, max **8** |
| `…instanceLaunchTemplate.instanceRequirements.memoryMiB` | | min **8192**, max **16384** |
| `…instanceLaunchTemplate.networkConfiguration` | | 2 subnet, 1 security group |
| `…instanceLaunchTemplate.storageConfiguration.storageSizeGiB` | | **100** |
| `…instanceLaunchTemplate.monitoring` | | **`basic`** |
| `managedInstancesProvider.propagateTags` | | **`CAPACITY_PROVIDER`** |

Dạng tương tự (placeholder):

```json
{
  "name": "<CAPACITY_PROVIDER_NAME>",
  "cluster": "<CLUSTER_NAME>",
  "tags": [{ "key": "<KEY>", "value": "<VALUE>" }],
  "managedInstancesProvider": {
    "infrastructureRoleArn": "<INFRA_ROLE_ARN>",
    "instanceLaunchTemplate": {
      "ec2InstanceProfileArn": "<INSTANCE_PROFILE_ARN>",
      "instanceRequirements": {
        "vCpuCount": { "min": 4, "max": 8 },
        "memoryMiB": { "min": 8192, "max": 16384 }
      },
      "networkConfiguration": { "subnets": ["<SUBNET_1>", "<SUBNET_2>"], "securityGroups": ["<SG_ID>"] },
      "storageConfiguration": { "storageSizeGiB": 100 },
      "monitoring": "basic"
    },
    "propagateTags": "CAPACITY_PROVIDER"
  }
}
```

2. Kiểm tra đã tạo thành công:

```bash
aws ecs describe-capacity-providers --capacity-providers <CAPACITY_PROVIDER_NAME>
```

**💡 Giải thích thêm**

Trang này làm rõ một số vị trí trường mà các file trước chỉ đoán:
- `instanceRequirements` nằm **trong** `instanceLaunchTemplate` (khớp cách mình viết lại ở file *Updating a cluster*).
- `monitoring` nằm trong `instanceLaunchTemplate`, giá trị `basic` (hoặc detailed).
- **`propagateTags` nằm trong `managedInstancesProvider`**, còn `tags` ở **gốc** của request. Ở file *Managed Instances capacity providers* mình đã đoán cờ CLI `--propagate-tags` ở mức gốc — theo ví dụ này, cách chắc chắn là đặt `propagateTags` bên trong `managedInstancesProvider` như trên.

Dùng `--cli-input-json` tiện cho IaC đơn giản và review trong Git — gợi ý của mình.

---

## 4. Cấu trúc JSON đầy đủ (ghép từ các trang)

**💡 Giải thích thêm**

Gom mọi trường đã thấy trong loạt file Managed Instances (vị trí lấy theo ví dụ của các trang; trường nào trang chưa nêu vị trí thì ghi chú):

```json
{
  "name": "<NAME>",
  "cluster": "<CLUSTER>",
  "tags": [ ... ],
  "managedInstancesProvider": {
    "infrastructureRoleArn": "<...>",
    "propagateTags": "CAPACITY_PROVIDER",
    "autoRepairConfiguration": { "actionsStatus": "ENABLED" },          // file GPU auto repair
    "instanceLaunchTemplate": {
      "ec2InstanceProfileArn": "<...>",
      "networkConfiguration": { "subnets": [...], "securityGroups": [...] },
      "storageConfiguration": { "storageSizeGiB": 80 },                 // 30–16.384, mặc định 80
      "localStorageConfiguration": { "useLocalStorage": true },        // file Architect MI
      "instanceRequirements": { ... },                                  // vCpuCount, memoryMiB, localStorage…
      "monitoring": "basic",
      "fipsEnabled": false                                              // file Security (GovCloud)
    }
  }
}
```

Các trường như `capacityOptionType` (Spot/Reserved), `allowedInstanceTypes` / `excludedInstanceTypes`, ScaleInAfter, cài đặt capability đặc quyền — các trang đã nhắc tên nhưng **chưa** cho ví dụ vị trí; cần tra API reference (`CreateCapacityProvider`) trước khi dùng. JSON có chú thích `//` ở trên chỉ để đọc, không chạy trực tiếp được.

---

## 5. Bước tiếp theo

**📘 Theo docs AWS**

Sau khi tạo capacity provider, dùng nó khi tạo service hoặc chạy task:
- **Service**: *Creating an Amazon ECS rolling update deployment* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/create-service-console-v2.html>
- **Standalone task**: *Running an application as an Amazon ECS task* — <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/standalone-task-create.html>

**💡 Giải thích thêm**

Khi thay cấu hình về sau, áp dụng file *Best practices for updating capacity providers*: tạo capacity provider **mới** (vd `-v2`), chuyển từng service, rồi mới xoá cái cũ.

---

## 6. Tóm tắt

- Không có `instanceRequirements` → ECS chọn instance general-purpose rẻ nhất; có → chọn theo thuộc tính (attribute-based, như EC2 Fleet).
- **Tiên quyết**: chọn loại giám sát, có cluster, infrastructure role + instance profile, hiểu cách chọn instance.
- **Console**: Clusters → cluster → **Infrastructure** → **Create capacity provider** → tên + loại **Amazon ECS Managed Instances** → instance profile + infrastructure role → instance requirements (vCPU, memory, instance types, CPU manufacturers `intel`/`amd`/`amazon-web-services`, accelerator types `gpu`/`fpga`/`inference`, accelerator count) → monitoring **Basic**/**Detailed** → tag + **Propagate tags from: Capacity provider** → **Create**.
- **CLI**: `create-capacity-provider --cli-input-json` với `name`, `cluster`, `tags`, `managedInstancesProvider` (`infrastructureRoleArn`, `instanceLaunchTemplate` {`ec2InstanceProfileArn`, `instanceRequirements`, `networkConfiguration`, `storageConfiguration`, `monitoring`}, `propagateTags`); kiểm tra bằng `describe-capacity-providers`.
- **Tiếp theo**: dùng capacity provider cho service hoặc standalone task.
