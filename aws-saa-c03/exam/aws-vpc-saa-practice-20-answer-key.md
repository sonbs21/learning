# AWS SAA-C03 — VPC Practice Set: Answer Key

|Câu|ĐA|Giải thích chi tiết|
|---:|:---:|---|
|1|B|NAT Gateway trong public subnet có route tới Internet Gateway; private subnet route outbound tới NAT Gateway. EC2 không cần public IP, và route theo AZ giúp giảm phụ thuộc cũng như chi phí liên AZ.|
|2|A|Public subnet không phải do tên hoặc AZ quyết định. Subnet cần route mặc định tới Internet Gateway, và resource cần địa chỉ public để Internet Gateway có thể cung cấp đường truy cập Internet.|
|3|BD|Security group là stateful và có thể tham chiếu security group khác. Network ACL là stateless, áp dụng ở cấp subnet, nên phải cấu hình rõ cả inbound và outbound; phát biểu A và C mô tả ngược.|
|4|C|S3 gateway endpoint thêm route tới S3 trong route table và giữ traffic trên mạng AWS. Nó tránh NAT Gateway, không cần public IP, và phù hợp hơn interface endpoint cho S3 trong bài toán chi phí này.|
|5|D|NAT Gateway là AZ-scoped. Đặt một NAT Gateway mỗi AZ và route private subnet tới NAT cùng AZ giúp vẫn outbound được khi một AZ lỗi và tránh hairpin traffic qua AZ khác.|
|6|A|VPC peering không tự cập nhật route table và không cung cấp transitive routing. Hai phía phải có route tới CIDR của nhau, đồng thời security group và NACL phải cho phép traffic.|
|7|C|Transit Gateway là hub managed cho nhiều VPC, VPN và Direct Connect. Nó tránh full-mesh và cho phép quản lý propagation hoặc static routes qua Transit Gateway route tables.|
|8|AD|Site-to-Site VPN dùng Internet nhưng traffic được mã hóa và triển khai nhanh; Direct Connect cung cấp kết nối dedicated/private ổn định cho băng thông lớn. Internet Gateway, peering và NAT không đáp ứng đồng thời yêu cầu này.|
|9|B|RDS nên nằm trong private subnets và tắt public access. DB security group chỉ cho phép source là application security group, giảm phạm vi truy cập mà không phải mở CIDR Internet.|
|10|B|Public IP chỉ có tác dụng khi subnet có route tới Internet Gateway và Internet Gateway đã attach vào VPC. IAM role, AZ hoặc số lượng security group không quyết định đường đi Internet của packet.|
|11|A|Gateway endpoints cho S3 và DynamoDB dùng route table, không cần NAT Gateway và không có chi phí hourly kiểu interface endpoint. Đây là mô hình private và cost-effective cho đúng hai service này.|
|12|CE|IPv6 không dùng NAT Gateway cho outbound Internet theo cách IPv4 thường dùng. Egress-only Internet Gateway cho phép outbound và return traffic của kết nối đã khởi tạo, còn security group giới hạn nguồn outbound; nó không cho inbound connection mới.|
|13|A|VPC Flow Logs ghi metadata như source/destination, protocol, bytes và action accepted hoặc rejected, không ghi payload packet. Log có thể gửi tới CloudWatch Logs hoặc S3 để troubleshooting và audit.|
|14|B|Kết nối VPC phụ thuộc đồng thời vào route table, security group, NACL và trạng thái network resource. Kiểm tra toàn bộ data path giúp tìm đúng điểm chặn; IAM hoặc tăng size EC2 không sửa lỗi network routing.|
|15|D|Private hosted zone chỉ trả lời DNS cho VPC được associate, nên tên private không bị public. VPC DNS support và hostnames cần phù hợp để instance sử dụng AmazonProvidedDNS và phân giải tên.|
|16|BE|Provider tạo endpoint service phía sau Network Load Balancer; Consumer tạo interface endpoint để nhận ENI private trong subnet của mình. PrivateLink giới hạn kết nối tới service cụ thể, không mở toàn bộ route giữa hai VPC.|
|17|C|NACL không lưu trạng thái nên response packet phải khớp outbound rule và ephemeral port rule. Security group stateful không loại bỏ yêu cầu NACL, và route table vẫn có thể là một nguyên nhân khác nhưng không giải thích trực tiếp đặc điểm return-port của NACL.|
|18|A|VPC peering và Transit Gateway cần CIDR không overlap để route unambiguous. Cần thiết kế lại hoặc mở rộng địa chỉ trước; route cụ thể hơn, NAT hay Internet Gateway không giải quyết overlap tổng thể.|
|19|BD|Transit Gateway route tables có thể segment production và development, còn AWS RAM giúp chia sẻ Transit Gateway cho account khác trong tổ chức. Full-mesh, Internet Gateway và NAT không cung cấp mô hình hub-and-spoke private phù hợp.|
|20|E|ALB public ở nhiều AZ nhận traffic Internet, còn EC2 private không có đường inbound trực tiếp từ Internet. Security group của EC2 chỉ cho phép source là security group của ALB, tạo lớp bảo vệ và HA đúng mục tiêu.|

