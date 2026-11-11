# AWS SAA-C03 — VPC Practice Set

## Bộ 20 câu tập trung Amazon VPC

**Thời gian gợi ý:** 40 phút  
**Hình thức:** Multiple choice và multiple response  
**Quy ước:** Câu ghi “Chọn HAI” hoặc “Chọn BA” phải chọn đủ và không chọn thừa.

Đây là bộ luyện tập tự biên soạn, tập trung vào VPC networking và security; không phải câu hỏi thật của AWS.

## Câu hỏi

### Câu 1

Một EC2 trong private subnet cần tải bản cập nhật từ Internet. Instance không được có public IPv4 address. Kiến trúc nào phù hợp nhất?

A. Gắn Internet Gateway trực tiếp vào private subnet  
B. Đặt NAT Gateway trong public subnet và route `0.0.0.0/0` của private subnet tới NAT Gateway  
C. Gắn Elastic IP trực tiếp cho EC2 nhưng không cần route table  
D. Dùng VPC peering tới một VPC có Internet Gateway

### Câu 2

Một subnet được xem là public trong VPC khi nào?

A. Subnet có route mặc định tới Internet Gateway và resource có public IPv4 hoặc Elastic IP  
B. Subnet nằm trong Availability Zone đầu tiên  
C. Subnet có security group cho phép inbound từ Internet  
D. Subnet có NAT Gateway

### Câu 3 — Chọn HAI

Một công ty muốn kiểm soát traffic tới EC2 ở subnet application. Hai phát biểu nào đúng về security group và network ACL?

A. Security group là stateless và cần rule chiều về cho mọi response  
B. Security group là stateful và có thể tham chiếu security group khác làm nguồn  
C. Network ACL chỉ áp dụng cho một ENI cụ thể  
D. Network ACL là stateless và áp dụng ở cấp subnet  
E. Network ACL luôn được đánh giá sau security group

### Câu 4

EC2 trong private subnet chỉ cần truy cập Amazon S3. Công ty muốn traffic không đi qua NAT Gateway và không phát sinh chi phí xử lý dữ liệu NAT. Giải pháp nào phù hợp nhất?

A. Internet Gateway  
B. Interface endpoint cho mọi subnet nhưng không cập nhật route table  
C. S3 gateway VPC endpoint và associate endpoint với route table của private subnet  
D. Public IPv4 address cho EC2

### Câu 5

Một ứng dụng chạy ở hai AZ và dùng NAT Gateway để outbound Internet. Ứng dụng phải tiếp tục hoạt động khi một AZ lỗi, đồng thời hạn chế traffic liên AZ. Thiết kế nào phù hợp nhất?

A. Một NAT Gateway duy nhất ở AZ bất kỳ, tất cả private subnet dùng chung  
B. NAT Instance trong một public subnet  
C. Đưa toàn bộ EC2 vào public subnet  
D. Tạo NAT Gateway ở mỗi AZ và route private subnet của AZ đó tới NAT Gateway cùng AZ

### Câu 6

VPC A và VPC B được kết nối bằng VPC peering. Ứng dụng ở VPC A cần truy cập subnet ở VPC B. Cấu hình nào bắt buộc?

A. Thêm route tới CIDR của VPC B trong route table của subnet nguồn và route ngược lại trong VPC B; security group/NACL cũng phải cho phép  
B. Chỉ tạo peering connection, route table tự cập nhật  
C. Đặt NAT Gateway ở cả hai VPC  
D. Dùng Internet Gateway để peering hoạt động

### Câu 7

Một tổ chức có 30 VPC với CIDR không overlap và muốn kết nối chúng với nhau cùng mạng on-premises mà không tạo full-mesh peering. Giải pháp nào phù hợp nhất?

A. Tạo VPC peering giữa mọi cặp VPC  
B. Dùng NAT Gateway làm transit router  
C. Dùng AWS Transit Gateway, attach các VPC và kết nối VPN hoặc Direct Connect  
D. Dùng Internet Gateway chung cho tất cả VPC

### Câu 8 — Chọn HAI

Một công ty cần kết nối mạng on-premises với VPC qua kết nối private. Họ muốn vừa có đường truyền mã hóa triển khai nhanh, vừa có lựa chọn băng thông ổn định cho traffic lớn. Hai lựa chọn nào phù hợp?

A. AWS Site-to-Site VPN  
B. Internet Gateway  
C. VPC peering  
D. AWS Direct Connect  
E. NAT Gateway

### Câu 9

Một RDS database chỉ được truy cập từ application tier trong cùng VPC. Database không được nhận kết nối trực tiếp từ Internet. Thiết kế nào phù hợp nhất?

A. Đặt RDS trong public subnet và giới hạn bằng password  
B. Đặt RDS trong private subnets, tắt public access và cho DB security group nhận traffic từ application security group  
C. Mở port database từ `0.0.0.0/0` rồi dùng NACL để bảo vệ  
D. Đặt RDS sau Internet Gateway

### Câu 10

Một EC2 có public IPv4 address nhưng không truy cập được Internet. Security group cho phép outbound toàn bộ. Nguyên nhân nào có khả năng nhất?

A. EC2 không có IAM role  
B. Route table của subnet không có route `0.0.0.0/0` tới Internet Gateway hoặc Internet Gateway chưa attach vào VPC  
C. EC2 nằm trong Availability Zone thứ hai  
D. VPC có quá ít security group

### Câu 11

Một private subnet cần truy cập S3 và DynamoDB với chi phí thấp nhất, không cần NAT Gateway. Lựa chọn nào phù hợp nhất?

A. Gateway VPC endpoints cho S3 và DynamoDB, kèm association với route tables cần dùng  
B. NAT Gateway trong mỗi private subnet  
C. Internet Gateway và public IP cho mọi instance  
D. VPC peering tới AWS account chứa S3

### Câu 12 — Chọn HAI

Một workload trong VPC dùng IPv6 và chỉ cần kết nối outbound tới Internet. Workload không được nhận kết nối inbound khởi tạo từ Internet. Hai thành phần nào phù hợp?

A. Internet Gateway với route `::/0`  
B. NAT Gateway bắt buộc cho IPv6  
C. Egress-only Internet Gateway với route `::/0`  
D. Public IPv4 bắt buộc cho mọi instance  
E. Security group chỉ cho phép outbound cần thiết

### Câu 13

Đội vận hành muốn điều tra vì sao traffic tới EC2 bị chấp nhận hoặc từ chối, nhưng không cần ghi payload của packet. Giải pháp nào phù hợp nhất?

A. VPC Flow Logs gửi metadata flow tới CloudWatch Logs hoặc S3  
B. CloudTrail data events cho toàn bộ packet  
C. S3 access logs  
D. Route 53 Resolver query logs

### Câu 14

Một EC2 không kết nối được tới database trong cùng VPC. Team đã xác nhận database đang chạy. Cách điều tra nào có phạm vi phù hợp nhất?

A. Chỉ kiểm tra IAM policy của EC2  
B. Kiểm tra route table hai subnet, security group hai phía, network ACL và trạng thái subnet/VPC connectivity  
C. Tăng kích thước EC2  
D. Tạo thêm Internet Gateway dù traffic nội bộ

### Câu 15

Một ứng dụng trong VPC cần phân giải tên `db.internal.example.com` thành private IP của database. Tên này chỉ được dùng trong VPC và không được public. Giải pháp nào phù hợp nhất?

A. Public hosted zone trên Route 53  
B. Ghi IP database vào file `/etc/hosts` trên từng EC2  
C. Dùng CloudFront distribution  
D. Route 53 private hosted zone associate với VPC và bật DNS support/hostnames phù hợp

### Câu 16 — Chọn HAI

Một team cung cấp dịch vụ nội bộ trong VPC Provider. VPC Consumer ở account khác cần truy cập private mà không được phép mở full network connectivity tới toàn bộ Provider VPC. Hai thành phần nào phù hợp?

A. Public Application Load Balancer  
B. Endpoint service dựa trên Network Load Balancer ở Provider  
C. Internet Gateway ở Consumer  
D. VPC peering bắt buộc  
E. Interface VPC endpoint ở Consumer

### Câu 17

Một network ACL custom được gắn cho subnet application. Sau khi cho phép inbound TCP 443 từ client, response từ application bị timeout. Nguyên nhân thường gặp nào cần kiểm tra?

A. NACL là stateful nên không cần rule outbound  
B. Security group không thể cho phép HTTPS  
C. NACL là stateless nên cần cho phép cả chiều outbound và ephemeral return ports phù hợp  
D. Route table không bao giờ ảnh hưởng response traffic

### Câu 18

VPC A và VPC B có các CIDR bị overlap. Team muốn tạo VPC peering để workload hai VPC giao tiếp. Giải pháp nào phù hợp nhất?

A. Thiết kế lại hoặc mở rộng CIDR để các VPC không overlap trước khi dùng peering hoặc Transit Gateway  
B. Dùng NAT Gateway để tự xử lý CIDR overlap cho mọi traffic  
C. Thêm route cụ thể hơn để override overlap  
D. Gắn thêm Internet Gateway vào một VPC

### Câu 19 — Chọn HAI

Một tổ chức muốn quản lý network hub tập trung cho nhiều account, nhưng vẫn phân tách route giữa production và development. Hai biện pháp nào phù hợp?

A. Full-mesh VPC peering giữa tất cả account  
B. AWS Transit Gateway dùng các route table riêng để segment traffic  
C. Dùng một Internet Gateway làm hub  
D. Chia sẻ Transit Gateway qua AWS RAM và attach các VPC cần thiết  
E. NAT Gateway làm thiết bị định tuyến trung tâm

### Câu 20

Một website public cần Application Load Balancer nhận traffic Internet, trong khi EC2 backend không được public trực tiếp. Kiến trúc nào phù hợp nhất?

A. ALB và EC2 đều ở private subnet, không cần route tới Internet  
B. EC2 ở public subnet, ALB ở private subnet  
C. Một EC2 public duy nhất làm reverse proxy  
D. ALB ở một public subnet duy nhất và EC2 trong cùng subnet  
E. ALB ở public subnets trên nhiều AZ, EC2 trong private subnets, security group EC2 chỉ cho phép traffic từ security group của ALB

## Phiếu trả lời

```
01 __  02 __  03 __  04 __  05 __
06 __  07 __  08 __  09 __  10 __
11 __  12 __  13 __  14 __  15 __
16 __  17 __  18 __  19 __  20 __
```

Nguồn tham khảo chính: [Amazon VPC User Guide](https://docs.aws.amazon.com/vpc/latest/userguide/) và [AWS SAA-C03 Exam Guide](https://docs.aws.amazon.com/pdfs/aws-certification/latest/solutions-architect-associate-03/solutions-architect-associate-03.pdf).

