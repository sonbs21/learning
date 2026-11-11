# AWS Organizations và Service Control Policies (SCP) chi tiết

> Chuyên đề này mở rộng phần security trong `tuan-11-security-nang-cao-data-protection.md`. Mục tiêu là giúp bạn hiểu cách tổ chức môi trường AWS nhiều account, cách SCP giới hạn quyền, cách policy được kế thừa và cách thiết kế guardrail mà không vô tình khóa hệ thống.

## 1. Bắt đầu bằng một bài toán thực tế

Một công ty có các môi trường sau:

- production chứa dữ liệu khách hàng;
- development cho lập trình viên thử nghiệm;
- security chứa log và công cụ giám sát;
- shared services chứa DNS, network và CI/CD;
- sandbox cho phép thử dịch vụ mới nhưng phải giới hạn chi phí và Region.

Nếu tất cả nằm trong một AWS account, ranh giới quyền rất dễ trở nên rối:

~~~text
Một AWS account
├── Production
├── Development
├── Security logs
├── Shared network
└── Sandbox
~~~

Một lỗi IAM, một credential bị lộ hoặc một thao tác nhầm có thể ảnh hưởng nhiều môi trường. AWS Organizations cho phép tách thành nhiều account và quản lý tập trung:

~~~text
Organization root
├── Security OU
│   ├── Log Archive account
│   └── Security Tooling account
├── Infrastructure OU
│   ├── Network account
│   └── Shared Services account
├── Workloads OU
│   ├── Production OU
│   │   ├── App-A-Prod account
│   │   └── App-B-Prod account
│   └── NonProduction OU
│       ├── App-A-Dev account
│       └── App-A-Test account
└── Sandbox OU
    └── Developer Sandbox accounts
~~~

Sau đó ta đặt guardrail:

- Production không được tắt CloudTrail;
- Log Archive không được xóa log;
- Sandbox chỉ dùng một số Region;
- member account không được tự rời organization;
- không principal nào trong workload account được xóa role bảo mật trung tâm.

SCP là một công cụ để thực hiện các guardrail này.

---

## 2. Vì sao cần nhiều AWS account?

AWS account không chỉ là nơi gom tài nguyên và hóa đơn. Nó là một **security boundary**, **billing boundary**, **quota boundary** và **blast-radius boundary**.

Tách account giúp:

- cô lập production khỏi development;
- tách log bảo mật khỏi workload tạo ra log;
- giới hạn thiệt hại khi credential bị lộ;
- phân bổ chi phí theo sản phẩm hoặc đội nhóm;
- tách service quota giữa workload;
- áp policy chung theo nhóm account;
- đáp ứng yêu cầu compliance và data residency;
- đóng/mở rộng workload mà không làm IAM trong một account quá phức tạp.

### 2.1. Account khác IAM user

Một AWS account là container cấp cao, có root user, IAM resources, billing và resource riêng. IAM user chỉ là một identity nằm bên trong account.

Không tạo một IAM user cho mỗi phòng ban rồi gọi đó là “multi-account”. Muốn có ranh giới tài nguyên và blast radius thực sự, cần account riêng.

### 2.2. Khi nào nên tách account?

Tách account khi có khác biệt đáng kể về:

- môi trường: prod, staging, dev;
- data classification;
- owner và vòng đời workload;
- compliance;
- network boundary;
- quyền quản trị;
- budget và cost attribution;
- blast radius.

Không nhất thiết tạo account cho từng microservice nhỏ. Account strategy phải đủ chi tiết để cô lập rủi ro nhưng không tạo overhead vô ích.

---

## 3. AWS Organizations là gì?

AWS Organizations là dịch vụ quản lý tập trung nhiều AWS account. Nó cung cấp:

- tạo hoặc mời account vào organization;
- xếp account vào cây Root/OU;
- consolidated billing;
- policy quản trị tập trung;
- tích hợp trusted access với các dịch vụ AWS;
- delegated administrator;
- quan sát và quản trị trên quy mô nhiều account.

### 3.1. Hai feature set

| Feature set | Khả năng |
|---|---|
| Consolidated billing only | gom hóa đơn và chia sẻ một số lợi ích billing |
| All features | billing cùng policy, trusted access, service integration và quản trị nâng cao |

Muốn dùng SCP phải bật **All features**. Organization mới mặc định dùng All features. Chuyển từ consolidated billing sang All features là quá trình một chiều; không chuyển ngược về billing-only sau khi hoàn tất.

### 3.2. Organization không tự tạo least privilege

Organizations cung cấp khung quản trị. Nó không tự biết workload nào được dùng dịch vụ gì. Bạn vẫn phải thiết kế:

- IAM Identity Center và permission sets;
- workload IAM roles;
- resource policies;
- SCP/RCP;
- logging, alert và quy trình exception.

---

## 4. Các thành phần trong cây Organization

### 4.1. Management account

Management account tạo và quản lý organization, billing, policy và một số service integration. Đây là account có đặc quyền rất lớn.

Nguyên tắc quan trọng:

- không triển khai workload thông thường tại đây;
- giới hạn số người có quyền truy cập;
- dùng MFA và federation;
- chỉ dùng cho tác vụ bắt buộc phải thực hiện từ management account;
- delegate dịch vụ sang account chuyên trách khi dịch vụ hỗ trợ;
- giám sát mọi thay đổi Organizations.

Lý do đặc biệt quan trọng: **SCP không giới hạn principal trong management account**. Đưa production workload vào đây làm mất một lớp guardrail quan trọng.

### 4.2. Member account

Mọi account còn lại trong organization là member account. SCP có thể giới hạn IAM user, IAM role và root user của các account này.

Account có thể được:

- tạo trực tiếp trong Organizations;
- mời từ account đã tồn tại;
- di chuyển giữa các OU;
- chỉ nằm trực tiếp dưới một parent tại một thời điểm.

### 4.3. Root của Organization

Root là container cao nhất của cây, không phải root user. Mỗi organization có một root.

Policy gắn vào root ảnh hưởng toàn bộ OU và member account bên dưới. Vì phạm vi rất rộng, thay đổi tại root có blast radius lớn nhất.

### 4.4. Organizational Unit — OU

OU là nhóm logic chứa account hoặc OU con. OU không chứa tài nguyên trực tiếp và không phải AWS account.

Nên thiết kế OU theo **nhóm control chung**, không nhất thiết sao chép sơ đồ phòng ban. Hai team khác nhau nhưng cùng guardrail production có thể nằm trong cùng Production OU.

Ví dụ:

~~~text
Workloads OU
├── Production OU      -> policy production nghiêm ngặt
└── NonProduction OU   -> cho phép thử nghiệm rộng hơn
~~~

Khi di chuyển account sang OU khác, tập policy kế thừa thay đổi. Đây là thay đổi security thực sự, không chỉ là sắp xếp giao diện.

### 4.5. Delegated administrator

Management account có thể chỉ định member account làm delegated administrator cho dịch vụ hỗ trợ như GuardDuty, Security Hub, Macie hoặc CloudFormation StackSets.

Lợi ích:

- giảm việc đăng nhập management account;
- phân tách nhiệm vụ;
- tập trung security tooling ở Security account;
- cho đội chuyên trách quản trị dịch vụ toàn organization.

Delegated admin vẫn là member account. Principal thông thường trong account đó vẫn chịu SCP, trừ các ngoại lệ cụ thể như service-linked role.

---

## 5. Gợi ý cấu trúc OU

Một baseline thực tế:

~~~text
Root
├── Security
│   ├── Log Archive
│   └── Security Tooling
├── Infrastructure
│   ├── Network
│   └── Shared Services
├── Workloads
│   ├── Production
│   └── NonProduction
├── Sandbox
├── Policy Staging
├── Transitional
└── Suspended
~~~

### 5.1. Security OU

Chứa log archive, audit và security tooling. Workload account không nên có quyền sửa/xóa log trung tâm.

### 5.2. Infrastructure OU

Chứa network hub, DNS, shared directory, CI/CD hoặc shared services. Quyền thay đổi tài nguyên cốt lõi nên hẹp hơn workload account.

### 5.3. Workloads OU

Tách Production và NonProduction vì control khác nhau. Production thường có Region restriction, bảo vệ logging/backup và change control nghiêm ngặt hơn.

### 5.4. Sandbox OU

Cho phép thử nghiệm nhưng nên:

- không nối vào production network;
- giới hạn Region/dịch vụ đắt tiền;
- đặt budget và expiration workflow;
- cấm thay đổi security baseline;
- không chứa dữ liệu production.

### 5.5. Policy Staging OU

Dùng account đại diện để thử SCP trước khi rollout. Đây là hàng rào chống việc một policy sai khóa hàng trăm account.

### 5.6. Suspended OU

Chứa account đang chờ đóng hoặc không còn hoạt động. Có thể gắn SCP deny gần như toàn bộ action, đồng thời giữ đường quản trị cần thiết.

---

## 6. Policy trong AWS Organizations

Organizations có nhiều loại policy. Không nên gọi tất cả là SCP.

| Policy | Mục đích chính |
|---|---|
| SCP | đặt trần quyền cho principal trong member account |
| RCP | đặt trần quyền trên resource được hỗ trợ trong organization |
| Tag policy | chuẩn hóa key/value và kiểm tra tagging |
| Backup policy | quản lý backup plan toàn organization |
| Declarative policy | cấu hình baseline cho tính năng dịch vụ được hỗ trợ |
| AI services opt-out policy | quản lý lựa chọn dùng dữ liệu cho AI services |
| Security Hub policy | cấu hình Security Hub tập trung |

SCP là **authorization policy**. Một số policy khác là **management/declarative policy**, có cơ chế kế thừa khác. Không áp công thức tính SCP cho mọi Organizations policy.

---

## 7. SCP là gì?

Service Control Policy đặt ra **maximum available permissions** cho principal trong member account.

Hãy hình dung:

~~~text
IAM policy: nhân viên được cấp những quyền nào?
SCP: organization cho phép account có thể cấp tối đa đến đâu?
~~~

Nếu IAM Administrator cấp `ec2:*` nhưng SCP deny `ec2:TerminateInstances`, principal vẫn không terminate được instance.

### 7.1. SCP không cấp quyền

Đây là quy tắc quan trọng nhất.

`FullAWSAccess` trong SCP không biến user thành administrator. Nó chỉ nói Organizations không thu hẹp quyền. Principal vẫn cần IAM identity policy hoặc resource policy cấp quyền.

~~~text
SCP Allow + IAM không Allow  -> Deny
IAM Allow + SCP không Allow  -> Deny
IAM Allow + SCP Allow        -> có thể Allow, nếu không có Deny khác
~~~

### 7.2. SCP áp dụng cho ai?

SCP áp dụng cho:

- IAM users trong member account;
- IAM roles và assumed-role sessions trong member account;
- root user của member account.

SCP không áp dụng cho:

- user/role trong management account;
- service-linked roles.

Service-linked role do AWS service quản lý để service hoạt động thay khách hàng. Không nên trông chờ SCP chặn hành vi của service-linked role.

### 7.3. SCP không có `Principal`

SCP được gắn theo Root/OU/account nên không dùng phần tử `Principal` như bucket policy. Nếu cần exception theo role, thường dùng condition với global condition key như `aws:PrincipalArn`, sau khi kiểm tra chính xác request context.

---

## 8. Công thức tính quyền hiệu dụng

Một mô hình đơn giản:

~~~text
Effective permission
  = quyền được IAM/resource policy cấp
  ∩ giới hạn của permissions boundary
  ∩ giới hạn SCP trên toàn đường kế thừa
  ∩ giới hạn RCP nếu áp dụng
  - mọi explicit Deny
~~~

Thực tế resource-based policy và session principal có các quy tắc chi tiết hơn, nhưng trong SAA-C03 cần nhớ: **SCP không grant và explicit Deny thắng**.

### 8.1. Đường kế thừa

Giả sử Account A nằm ở:

~~~text
Root -> Workloads OU -> Production OU -> Account A
~~~

Để một action được phép trong mô hình allow-list, action đó phải được SCP cho phép ở mọi cấp trên đường đi:

~~~text
Root ALLOW
  AND Workloads ALLOW
  AND Production ALLOW
  AND Account A ALLOW
~~~

Thiếu Allow ở một cấp là bị chặn.

### 8.2. Nhiều SCP tại cùng một cấp

Các Allow trong nhiều SCP cùng gắn tại một node được tổng hợp. Nhưng một explicit Deny trong bất kỳ SCP áp dụng nào vẫn thắng.

Ví dụ tại Production OU:

- SCP A cho phép EC2;
- SCP B cho phép S3;
- SCP C deny xóa CloudTrail.

Node đó có thể cho EC2 và S3 đi qua, nhưng không action nào ở cấp thấp hơn override được deny CloudTrail.

### 8.3. Deny kế thừa xuống dưới

Nếu Root deny `organizations:LeaveOrganization`, một SCP gắn vào account với Allow action đó không mở lại được.

~~~text
Root: Deny LeaveOrganization
           ↓
OU: Allow *              -> không override
           ↓
Account: Allow *         -> không override
           ↓
Kết quả: Deny
~~~

---

## 9. `FullAWSAccess` thực sự làm gì?

Khi bật SCP, AWS tự gắn SCP do AWS quản lý tên `FullAWSAccess`:

~~~json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": "*",
    "Resource": "*"
  }]
}
~~~

Nó giữ behavior hiện tại: SCP không chặn gì. IAM vẫn quyết định quyền cụ thể.

Nếu xóa `FullAWSAccess` ở một cấp mà không có SCP Allow thay thế, các account bên dưới có thể mất gần như toàn bộ quyền do không còn Allow tại cấp đó.

`FullAWSAccess` không thể override một SCP Deny khác cùng cấp hoặc cấp cha.

---

## 10. Hai chiến lược thiết kế SCP

### 10.1. Deny-list strategy

Giữ `FullAWSAccess`, sau đó thêm Deny cho hành vi nguy hiểm:

- cấm rời organization;
- cấm tắt CloudTrail/Config/GuardDuty;
- cấm dùng Region không phê duyệt;
- cấm xóa backup;
- cấm sửa role trung tâm.

Ưu điểm:

- ít làm vỡ workload hiện hữu;
- dễ bắt đầu;
- dịch vụ AWS mới mặc định không bị chặn.

Nhược điểm:

- phải biết hành vi nào cần deny;
- service/API mới có thể nằm ngoài danh sách chặn;
- không phải allow-list compliance nghiêm ngặt.

### 10.2. Allow-list strategy

Chỉ Allow dịch vụ/action được phê duyệt ở mỗi cấp. Mọi thứ khác bị implicit deny.

Ưu điểm:

- kiểm soát chặt;
- phù hợp môi trường regulated hoặc sandbox giới hạn.

Nhược điểm:

- dễ phá dependency;
- AWS service thường gọi nhiều service phụ trợ;
- mỗi dịch vụ mới cần thay policy;
- troubleshooting và vận hành phức tạp hơn.

### 10.3. Cách chọn

Phần lớn tổ chức bắt đầu bằng deny-list guardrail, quan sát Service Last Accessed/CloudTrail, thử policy trong Policy Staging OU rồi mới thu hẹp. Allow-list phù hợp khi có requirement rõ và đội vận hành đủ trưởng thành.

---

## 11. Cú pháp SCP hiện hành

SCP dùng JSON gần giống IAM policy. Các phần tử quan trọng gồm:

- `Version`;
- `Statement`;
- `Sid`;
- `Effect`;
- `Action` hoặc `NotAction`;
- `Resource` hoặc `NotResource`;
- `Condition`.

Tài liệu cũ có thể nói SCP chỉ hỗ trợ một phần cú pháp IAM. Cú pháp hiện hành hỗ trợ `Action`, `NotAction`, `Resource`, `NotResource` và `Condition` cho cả Allow/Deny theo tài liệu AWS mới. Vẫn phải xác minh action có hỗ trợ resource-level permission và condition key tương ứng hay không.

### 11.1. `Action`

~~~json
"Action": [
  "cloudtrail:StopLogging",
  "cloudtrail:DeleteTrail"
]
~~~

### 11.2. `NotAction`

Trong một Deny, `NotAction` nghĩa là deny mọi action **ngoại trừ** danh sách. Nó rất mạnh và dễ tạo blast radius.

~~~json
{
  "Effect": "Deny",
  "NotAction": ["iam:*", "organizations:*"],
  "Resource": "*"
}
~~~

Đoạn trên không đơn giản là “allow IAM và Organizations”. Nó deny mọi action khác trong phạm vi statement; IAM/Organizations vẫn cần được policy khác cấp quyền.

### 11.3. `Resource`

Không phải mọi API hỗ trợ resource-level ARN. Với action không hỗ trợ, thường phải dùng `"Resource": "*"`. Luôn tra Service Authorization Reference.

### 11.4. `Condition`

Condition giúp giới hạn theo Region, principal ARN, organization ID, resource tag, request tag hoặc network context khi API cung cấp condition key đó.

Không phải condition key nào cũng có mặt trong mọi request. Dùng toán tử `IfExists`, `Null` và phủ định sai có thể mở hoặc chặn ngoài dự kiến.

---

## 12. Ví dụ 1 — Cấm member account rời Organization hoặc tự đóng

~~~json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Sid": "PreventLeavingOrClosingAccount",
    "Effect": "Deny",
    "Action": [
      "organizations:LeaveOrganization",
      "account:CloseAccount"
    ],
    "Resource": "*"
  }]
}
~~~

Gắn ở Root để member account không làm mất governance/billing ngoài quy trình trung tâm.

Organization tạo qua AWS Console sau ngày 10/07/2026 có thể nhận control mặc định này; organization cũ hoặc tạo qua API/IaC cần kiểm tra và triển khai chủ động.

---

## 13. Ví dụ 2 — Bảo vệ CloudTrail và AWS Config

~~~json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Sid": "ProtectAuditServices",
    "Effect": "Deny",
    "Action": [
      "cloudtrail:StopLogging",
      "cloudtrail:DeleteTrail",
      "cloudtrail:UpdateTrail",
      "config:DeleteConfigurationRecorder",
      "config:DeleteDeliveryChannel",
      "config:StopConfigurationRecorder"
    ],
    "Resource": "*"
  }]
}
~~~

Đây chỉ là ví dụ học tập. Production có thể cần exception cho security automation role và phải bảo vệ thêm S3 log bucket, KMS key, EventBridge rule hoặc organization trail.

Nếu deny `UpdateTrail` tuyệt đối, chính đội security cũng không cập nhật được từ member account. Tốt hơn là chỉ cho phép thay đổi qua role trung tâm đã kiểm soát.

---

## 14. Ví dụ 3 — Chỉ cho security admin sửa role quan trọng

~~~json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Sid": "ProtectCentralSecurityRole",
    "Effect": "Deny",
    "Action": [
      "iam:DeleteRole",
      "iam:UpdateAssumeRolePolicy",
      "iam:PutRolePolicy",
      "iam:DeleteRolePolicy",
      "iam:AttachRolePolicy",
      "iam:DetachRolePolicy"
    ],
    "Resource": "arn:aws:iam::*:role/CentralSecurityRole",
    "Condition": {
      "ArnNotLike": {
        "aws:PrincipalArn": "arn:aws:iam::*:role/OrganizationSecurityAdmin"
      }
    }
  }]
}
~~~

Ý nghĩa: mọi principal bị deny sửa `CentralSecurityRole`, ngoại trừ request có principal ARN khớp role quản trị.

Trước khi dùng cần kiểm tra:

- tên/path role thực tế;
- ARN của assumed role được biểu diễn thế nào trong global condition key;
- CloudFormation/StackSets dùng role nào;
- service-linked role có nằm ngoài phạm vi SCP;
- security admin role có được bảo vệ khỏi việc bị assume trái phép không.

Exception trong Deny là phần dễ sai nhất của SCP. Test bằng account staging và CloudTrail request context.

---

## 15. Ví dụ 4 — Giới hạn Region

Yêu cầu: workload chỉ hoạt động tại Singapore và Tokyo.

~~~json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Sid": "DenyOutsideApprovedRegions",
    "Effect": "Deny",
    "NotAction": [
      "iam:*",
      "organizations:*",
      "route53:*",
      "cloudfront:*",
      "support:*",
      "budgets:*"
    ],
    "Resource": "*",
    "Condition": {
      "StringNotEquals": {
        "aws:RequestedRegion": [
          "ap-southeast-1",
          "ap-northeast-1"
        ]
      }
    }
  }]
}
~~~

### 15.1. Vì sao cần `NotAction`?

Một số dịch vụ global có endpoint/control plane gắn với một Region nhất định hoặc không hoạt động như regional service. Nếu deny mọi action dựa vào `aws:RequestedRegion`, IAM, Route 53, CloudFront hoặc billing/support có thể bị phá.

Danh sách trên chỉ minh họa, không phải danh sách global service hoàn chỉnh. Phải xây danh sách exception theo dịch vụ thực tế và cập nhật khi AWS thay đổi.

### 15.2. Region restriction không bảo đảm data residency tuyệt đối

Nó giới hạn API request theo context Region, nhưng không thay thế:

- kiểm tra dịch vụ global;
- replication policy;
- cross-Region backup;
- CloudFront/Route 53 behavior;
- third-party transfer;
- application-level data flow;
- compliance review.

---

## 16. Ví dụ 5 — Cấm root user của member account thực hiện API

~~~json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Sid": "DenyMemberAccountRootUser",
    "Effect": "Deny",
    "Action": "*",
    "Resource": "*",
    "Condition": {
      "ArnLike": {
        "aws:PrincipalArn": "arn:aws:iam::*:root"
      }
    }
  }]
}
~~~

SCP áp dụng cho root user của member account, nên mẫu này chặn phần lớn hành động của root. Tuy nhiên một số tác vụ root-only và cơ chế quản lý root access tập trung cần được thiết kế trước. Không triển khai mù quáng nếu chưa có break-glass và quy trình privileged task.

SCP không bảo vệ root user của management account.

---

## 17. Ví dụ 6 — Chỉ cho phép một tập dịch vụ trong Sandbox

~~~json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Sid": "AllowApprovedSandboxServices",
    "Effect": "Allow",
    "Action": [
      "ec2:*",
      "s3:*",
      "lambda:*",
      "dynamodb:*",
      "cloudwatch:*",
      "logs:*",
      "iam:Get*",
      "iam:List*",
      "sts:GetCallerIdentity"
    ],
    "Resource": "*"
  }]
}
~~~

Nếu dùng allow-list, phải bỏ `FullAWSAccess` tại Sandbox OU và bảo đảm Root cùng các OU cha vẫn Allow các action này. Ví dụ trên chưa chắc đủ để CloudFormation, Lambda execution role, networking hoặc service integration hoạt động.

Đây là lý do allow-list cần phân tích dependency và test sâu.

---

## 18. SCP, IAM policy và permissions boundary

| Cơ chế | Scope | Có cấp quyền? | Mục đích |
|---|---|---:|---|
| SCP | Root/OU/member account | Không | trần quyền đa account |
| IAM identity policy | user/role | Có | cấp quyền cho identity |
| Resource policy | resource | Có thể | cấp/giới hạn quyền trên resource |
| Permissions boundary | user/role | Không | trần quyền cho identity cụ thể |
| Session policy | role session | Không | thu hẹp quyền session |

Ví dụ:

- SCP cho phép tối đa EC2 và S3;
- permissions boundary không cho role tạo IAM user;
- IAM policy chỉ cấp đọc một S3 prefix.

Kết quả role chỉ đọc prefix đó. Các lớp “trần quyền” không cộng thêm quyền.

---

## 19. SCP và resource-based policy

Resource policy như S3 bucket policy hoặc KMS key policy có thể cấp cross-account access. SCP vẫn là một phần của policy evaluation cho principal/member account và không nên được coi là không liên quan.

Tình huống cần kiểm tra:

- principal thuộc account nào;
- resource thuộc account nào;
- request dùng IAM role ARN hay role session principal;
- SCP nào áp vào account principal;
- RCP nào áp vào resource;
- có explicit Deny trong resource policy không;
- KMS key policy có cho phép không.

Đừng dùng câu đơn giản “bucket policy luôn thắng IAM” hoặc “SCP luôn chặn mọi resource policy”. IAM evaluation có nuance theo loại principal và policy. Trong bài thi, bám requirement và nhớ explicit Deny thắng.

---

## 20. SCP và service-linked role

SCP không giới hạn service-linked role. Ví dụ một AWS service có role dạng:

~~~text
arn:aws:iam::123456789012:role/aws-service-role/...
~~~

Lý do là service cần role định nghĩa sẵn để tích hợp và hoạt động thay khách hàng.

Hệ quả:

- deny một API trong SCP chưa chắc ngăn AWS service thực hiện API qua service-linked role;
- phải hiểu control plane của dịch vụ;
- có thể cần tắt trusted access, cấu hình dịch vụ, resource policy hoặc control bổ sung;
- không nên cố sửa service-linked role như role thông thường.

---

## 21. Trusted access và service integration

Khi bật trusted access, một AWS service được phép tích hợp với Organizations và có thể làm việc trên nhiều account, tạo service-linked role hoặc đọc cấu trúc organization theo quyền được định nghĩa.

Ví dụ dịch vụ thường tích hợp:

- CloudTrail organization trail;
- GuardDuty;
- Security Hub;
- AWS Config;
- Backup;
- IAM Access Analyzer;
- Firewall Manager;
- CloudFormation StackSets.

Chỉ bật trusted access cho dịch vụ có owner và use case rõ. Khi tắt, phải đọc tác động; tài nguyên hoặc delegated admin không phải lúc nào tự dọn theo cách bạn mong đợi.

---

## 22. AWS Control Tower liên quan thế nào?

AWS Control Tower xây landing zone và governance dựa trên Organizations cùng nhiều dịch vụ khác.

Control Tower controls có các hành vi:

- **preventive:** ngăn action, thường triển khai bằng SCP hoặc RCP;
- **detective:** phát hiện non-compliance, thường dựa trên AWS Config;
- **proactive:** kiểm tra resource trước khi provision, thường dùng CloudFormation hooks.

SCP là policy primitive. Control Tower cung cấp control catalog, landing zone và quy trình quản trị cao hơn. Dùng Control Tower không có nghĩa là không cần hiểu Organizations/SCP; sửa custom SCP vẫn có thể xung đột control do Control Tower quản lý.

---

## 23. Quota cần nhớ

Theo quota AWS Organizations hiện hành:

- mỗi Root, OU hoặc account phải có ít nhất một SCP khi loại policy này được bật;
- tối đa 10 SCP gắn trực tiếp trên mỗi Root, OU hoặc account;
- inherited SCP không tính vào quota gắn trực tiếp;
- kích thước tối đa một SCP là 10.240 ký tự;
- cây OU có thể lồng tối đa 5 cấp bên dưới Root;
- quota và tính năng có thể thay đổi, cần kiểm tra tài liệu trước production.

Whitespace cũng có thể tính vào kích thước khi gửi qua CLI/SDK; console có thể loại whitespace ngoài chuỗi. Nên tổ chức statement rõ ràng, tái sử dụng policy theo OU và tránh tạo SCP riêng cho mọi account.

---

## 24. Quy trình triển khai SCP an toàn

### Bước 1 — Viết requirement bằng ngôn ngữ nghiệp vụ

Ví dụ: “Chỉ đội security được tắt organization trail” rõ hơn “deny CloudTrail”. Xác định owner, exception và recovery trước JSON.

### Bước 2 — Liệt kê API thật sự

Một hành vi console có thể gọi nhiều API. Tra CloudTrail và Service Authorization Reference để biết action, resource và condition key.

### Bước 3 — Kiểm tra service dependency

CloudFormation, Auto Scaling, Backup và managed service có thể gọi IAM, KMS, EC2, S3 hoặc service khác. Đừng chỉ test thao tác chính.

### Bước 4 — Validate policy

Dùng IAM Access Analyzer policy validation và kiểm tra JSON/IaC. Validation cú pháp không chứng minh behavior đúng.

### Bước 5 — Test trong account riêng

Gắn vào một account trong Policy Staging OU. Test:

- hành vi phải bị deny;
- hành vi hợp lệ vẫn hoạt động;
- deployment pipeline;
- monitoring/backup;
- break-glass;
- service-linked role và automation.

### Bước 6 — Rollout dần

~~~text
Một test account
   -> Policy Staging OU
      -> NonProduction OU
         -> một Production account
            -> toàn Production OU
~~~

### Bước 7 — Quan sát

Theo dõi CloudTrail `AccessDenied`, deployment failure, support ticket và business metric. Mỗi SCP cần owner, version control, change ticket và rollback plan.

---

## 25. Break-glass và exception

### 25.1. SCP Deny không có “super admin” tự động

AdministratorAccess trong member account không vượt qua SCP. Nếu explicit Deny không có exception, break-glass role trong cùng account cũng bị deny.

### 25.2. Exception role

Có thể dùng `aws:PrincipalArn` để loại trừ organization security role khỏi một Deny. Nhưng exception rộng biến role thành đường bypass toàn hệ thống.

Guardrail cho exception role:

- trust policy rất hẹp;
- MFA và approval;
- session ngắn;
- CloudTrail/alert real-time;
- credential tạm thời;
- chỉ exception đúng action cần thiết;
- định kỳ test và review.

### 25.3. Management account không phải break-glass cho mọi API

Management account có thể thay/gỡ SCP, nhưng không mặc nhiên sở hữu tài nguyên hoặc IAM role trong member account. Cần có cross-account administration role hoặc cơ chế privileged access được chuẩn bị trước.

---

## 26. Troubleshooting `AccessDenied`

Khi một role có `AdministratorAccess` nhưng API vẫn bị deny, kiểm tra theo thứ tự:

1. Principal thật sự là ai? Xem `sts get-caller-identity` và CloudTrail.
2. Account có thuộc Organization không?
3. Account đang nằm ở Root/OU nào?
4. Liệt kê SCP gắn trực tiếp và kế thừa từ toàn đường đi.
5. Có explicit Deny khớp action/resource/condition không?
6. Trong allow-list strategy, có Allow ở **mọi cấp** không?
7. IAM policy hoặc resource policy có thực sự cấp quyền không?
8. Có permissions boundary/session policy không?
9. Có RCP, VPC endpoint policy hoặc KMS key policy liên quan không?
10. Request context như Region, principal ARN, tag có đúng giả định không?

### 26.1. Dấu hiệu lỗi từ Organizations

Error message hoặc CloudTrail có thể chỉ ra explicit deny trong SCP. Tuy nhiên không phải mọi service đều trả thông báo đẹp; cần đối chiếu action, account và policy tree.

### 26.2. Policy simulator có giới hạn

IAM Policy Simulator hữu ích nhưng không thay thế test end-to-end cho mọi loại policy/context. Conditional SCP, service-linked role, cross-account resource policy và managed service workflow cần test thực tế trong staging.

### 26.3. Tình huống mẫu

~~~text
Role có AdministratorAccess
  + Root có FullAWSAccess
  + Production OU deny ec2:TerminateInstances
  + Account có FullAWSAccess
  = ec2:TerminateInstances bị DENY
~~~

Không cần sửa IAM role; phải thay requirement/SCP hoặc dùng exception đã thiết kế.

---

## 27. Monitoring và audit Organizations

CloudTrail ghi các Organizations API như:

- `CreatePolicy`;
- `UpdatePolicy`;
- `AttachPolicy`/`DetachPolicy`;
- `MoveAccount`;
- `CreateAccount`;
- `RegisterDelegatedAdministrator`;
- `EnableAWSServiceAccess`;
- `LeaveOrganization`.

Nên alert khi:

- SCP bị sửa/gỡ;
- account bị chuyển OU;
- trusted access thay đổi;
- delegated administrator thay đổi;
- account tạo/đóng/rời organization;
- control bảo mật bị disable.

Lưu organization trail/log ở Log Archive account, bảo vệ bucket/KMS key và hạn chế chính workload account sửa logging.

---

## 28. Những sai lầm phổ biến

### 28.1. Dùng SCP như IAM policy

Gắn Allow S3 vào SCP rồi chờ user đọc được bucket. SCP không cấp quyền; vẫn cần IAM/bucket policy.

### 28.2. Xóa `FullAWSAccess` mà chưa có allow-list hoàn chỉnh

Kết quả có thể là implicit deny diện rộng. Luôn test trên account riêng.

### 28.3. Gắn policy thẳng Root ngay lần đầu

Sai một condition có thể ảnh hưởng mọi member account. Rollout từ scope nhỏ lên scope lớn.

### 28.4. Giới hạn Region nhưng quên global services

IAM, Route 53, CloudFront, Support hoặc billing workflow có thể lỗi. Xây và duy trì `NotAction` exception rõ ràng.

### 28.5. Tin rằng admin có thể override SCP

Không. IAM AdministratorAccess và member root user vẫn chịu SCP.

### 28.6. Nghĩ SCP áp vào management account

Không. Vì vậy management account không nên chạy workload.

### 28.7. Nghĩ SCP giới hạn service-linked role

Không. Phải dùng control ở lớp dịch vụ/resource/configuration phù hợp.

### 28.8. OU theo sơ đồ tổ chức nhân sự

Khi phòng ban đổi liên tục, policy tree khó quản lý. OU nên phản ánh control và lifecycle của workload.

### 28.9. Dùng Deny quá rộng nhưng exception theo tên role không chính xác

Assumed-role ARN, IAM role ARN và principal context có thể khác cách người viết tưởng tượng. Quan sát CloudTrail và test condition.

### 28.10. Không bảo vệ pipeline quản trị policy

Nếu ai cũng sửa IaC repo hoặc deployment role của Organizations, SCP không còn đáng tin. Governance cần bảo vệ cả source, approval và deployment credentials.

---

## 29. Bẫy SAA-C03

1. **SCP cấp quyền cho IAM user?** Không, chỉ giới hạn quyền tối đa.
2. **SCP áp dụng cho root user member account?** Có.
3. **SCP áp dụng cho management account?** Không.
4. **SCP áp dụng cho service-linked role?** Không.
5. **Allow ở account có vượt Deny ở OU không?** Không.
6. **Permissions boundary quản lý toàn OU?** Không, boundary gắn identity; SCP quản lý Root/OU/account.
7. **Muốn governance landing zone tự động?** Control Tower là đáp án thường gặp, xây trên Organizations.
8. **Muốn gom hóa đơn nhưng không cần policy?** Consolidated billing có thể đủ; SCP cần All features.
9. **Muốn tìm cấu hình vi phạm sau khi tạo?** AWS Config/detective control, không phải chỉ SCP.
10. **Muốn ngăn hành động trước khi xảy ra?** SCP/preventive control khi action hỗ trợ.
11. **Muốn giới hạn principal nội bộ?** SCP. Muốn giới hạn quyền tối đa trên resource hỗ trợ? Cân nhắc RCP.
12. **Account chuyển OU có đổi policy không?** Có, inheritance đổi theo parent mới.

---

## 30. Câu hỏi tự kiểm tra

### Câu 1

Một developer có IAM `AdministratorAccess` nhưng không thể tạo resource ở `us-east-1`. Production OU có SCP chỉ cho Singapore. Vì sao?

**Đáp án:** IAM admin không vượt qua SCP. Effective permission là giao của IAM và SCP; Region condition tạo explicit Deny.

### Câu 2

Gắn SCP `Allow s3:*` vào account nhưng user vẫn không gọi `ListBucket`. Vì sao?

**Đáp án:** SCP không grant. User/role vẫn cần IAM hoặc resource-based Allow phù hợp.

### Câu 3

Muốn giới hạn cả management account bằng SCP. Có làm được không?

**Đáp án:** Không. Hãy giữ workload khỏi management account, giới hạn quyền truy cập, giám sát và dùng các control khác.

### Câu 4

Một managed AWS service vẫn thực hiện action dù SCP deny. Nguyên nhân có thể là gì?

**Đáp án:** Service có thể dùng service-linked role, vốn không chịu SCP. Cần xem CloudTrail principal và cấu hình dịch vụ.

### Câu 5

Root Allow EC2, Production OU không Allow EC2, Account Allow EC2. Trong allow-list model, account dùng EC2 được không?

**Đáp án:** Không. Cần explicit Allow ở mọi cấp trên đường Root → OU → Account.

### Câu 6

Muốn ngăn member account tự rời organization. Control nào phù hợp?

**Đáp án:** SCP Deny `organizations:LeaveOrganization`, thường gắn ở Root.

### Câu 7

Muốn biết bucket vi phạm encryption sau khi đã được tạo. SCP có phải lựa chọn trực tiếp tốt nhất không?

**Đáp án:** AWS Config/detective control phù hợp để đánh giá configuration. SCP chỉ ngăn API khi có thể biểu diễn requirement bằng action/resource/condition.

### Câu 8

Muốn triển khai guardrail chuẩn, account vending và dashboard compliance. Dịch vụ nào phù hợp?

**Đáp án:** AWS Control Tower cùng Organizations; preventive controls có thể dùng SCP/RCP.

---

## 31. Lab thực hành

### Lab 1 — Quan sát inheritance

1. Tạo Policy Staging OU và một test account.
2. Giữ `FullAWSAccess` ở các cấp.
3. Gắn SCP deny một action ít rủi ro vào test account.
4. Assume role trong account và thử action.
5. Chuyển policy từ account lên OU.
6. Quan sát account kế thừa và CloudTrail event.
7. Gỡ policy theo rollback plan.

### Lab 2 — Region restriction

1. Viết SCP chỉ cho hai Region.
2. Lập danh sách global services cần exception.
3. Test CLI ở allowed và denied Region.
4. Test CloudFormation, IAM, STS, CloudFront và billing workflow liên quan.
5. Xem principal/Region trong CloudTrail.

### Lab 3 — Bảo vệ audit trail

1. Tạo organization trail trong lab organization.
2. Dùng SCP deny stop/delete với exception security admin.
3. Test bằng workload admin role.
4. Test bằng security role.
5. Bật alert khi có `AccessDenied` hoặc policy change.

> Không thử SCP lần đầu trên management/root production. Lab Organizations có thể phát sinh chi phí ở account/tài nguyên con; dọn tài nguyên và đóng account theo đúng quy trình.

---

## 32. Checklist thiết kế

### Organization

- [ ] Đã bật All features?
- [ ] Management account không chạy workload?
- [ ] Có MFA/federation và ít người truy cập management account?
- [ ] Dịch vụ được delegate sang security/infrastructure account khi có thể?
- [ ] OU được thiết kế theo control, không chỉ theo phòng ban?
- [ ] Có Policy Staging, Suspended và Transitional OU nếu cần?

### SCP

- [ ] Mỗi SCP có requirement, owner và exception rõ ràng?
- [ ] Hiểu SCP không cấp quyền?
- [ ] Đã xem toàn bộ đường inheritance?
- [ ] Có giữ `FullAWSAccess` nếu dùng deny-list?
- [ ] Region policy đã xử lý global services?
- [ ] Condition key tồn tại với các API mục tiêu?
- [ ] Service-linked role/dependency đã được test?
- [ ] Có break-glass và rollback plan?

### Vận hành

- [ ] Policy nằm trong version control/IaC?
- [ ] Có peer review và change approval?
- [ ] Rollout từ test account lên OU theo giai đoạn?
- [ ] CloudTrail ghi Organizations changes?
- [ ] Có alert khi policy/account/OU thay đổi?
- [ ] Định kỳ xem Service Last Accessed và loại exception cũ?

---

## 33. Cheat sheet

| Yêu cầu | Công cụ/khái niệm phù hợp |
|---|---|
| Quản lý nhiều AWS account | AWS Organizations |
| Gom hóa đơn | Consolidated billing |
| Dùng SCP/trusted access | All features |
| Nhóm account có cùng control | OU |
| Giới hạn quyền tối đa đa account | SCP |
| Giới hạn resource trong organization | RCP |
| Cấp quyền cho user/role | IAM identity policy |
| Trần quyền cho một role/user | Permissions boundary |
| Landing zone và guardrail quản trị | AWS Control Tower |
| Ngăn API trước khi xảy ra | Preventive control/SCP |
| Phát hiện cấu hình sai | AWS Config/detective control |
| SCP mặc định không thu hẹp quyền | FullAWSAccess |
| Deny ở parent | Không thể override ở child |
| Member root user | Chịu SCP |
| Management account principal | Không chịu SCP |
| Service-linked role | Không chịu SCP |
| Thử policy an toàn | Policy Staging OU |

---

## 34. Tài liệu AWS chính thức

- [AWS Organizations concepts](https://docs.aws.amazon.com/organizations/latest/userguide/orgs_getting-started_concepts.html)
- [Service control policies](https://docs.aws.amazon.com/organizations/latest/userguide/orgs_manage_policies_scps.html)
- [SCP evaluation](https://docs.aws.amazon.com/organizations/latest/userguide/orgs_manage_policies_scps_evaluation.html)
- [SCP syntax](https://docs.aws.amazon.com/organizations/latest/userguide/orgs_manage_policies_scps_syntax.html)
- [SCP examples](https://docs.aws.amazon.com/organizations/latest/userguide/orgs_manage_policies_scps_examples.html)
- [AWS Organizations quotas](https://docs.aws.amazon.com/organizations/latest/userguide/orgs_reference_limits.html)
- [Best practices for management account](https://docs.aws.amazon.com/organizations/latest/userguide/orgs_best-practices_mgmt-acct.html)
- [Best practices for OUs](https://docs.aws.amazon.com/organizations/latest/userguide/orgs_manage_ous_best_practices.html)
- [Using Organizations with other AWS services](https://docs.aws.amazon.com/organizations/latest/userguide/orgs_integrate_services.html)
- [IAM policy evaluation logic](https://docs.aws.amazon.com/IAM/latest/UserGuide/reference_policies_evaluation-logic.html)
- [AWS Control Tower controls](https://docs.aws.amazon.com/controltower/latest/userguide/controls.html)

> SCP có blast radius lớn và AWS tiếp tục mở rộng cú pháp, policy type và control mặc định. Trước khi áp dụng production, luôn kiểm tra tài liệu hiện hành, validate policy và rollout qua Policy Staging OU.
