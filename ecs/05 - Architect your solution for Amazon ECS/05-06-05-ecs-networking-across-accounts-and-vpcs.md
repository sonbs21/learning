# Best practices for networking Amazon ECS services across AWS accounts and VPCs

> Nguồn: **Amazon ECS Developer Guide — Best practices for networking Amazon ECS services across AWS accounts and VPCs**
> <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/networking-connecting-services-crossaccount.html>
>
> **Cách đọc file này**
> - Khối **📘 Theo docs AWS**: **bản dịch** sang tiếng Việt, đi đúng thứ tự và đúng nội dung của trang gốc, không thêm, không bớt ý.
> - Khối **💡 Giải thích thêm**: chỉ giải thích lại những gì **chính trang này** đã nói, cho dễ hiểu hơn. Không đưa thông tin từ trang khác hay ví dụ tự đặt.
> - Tên dịch vụ giữ nguyên tiếng Anh.
> - Tài liệu AWS được phát hành theo giấy phép **Creative Commons Attribution-ShareAlike 4.0**; bản dịch này ghi rõ nguồn ở trên.

---

## Nội dung trang

**📘 Theo docs AWS**

Nếu bạn thuộc một tổ chức có nhiều team và bộ phận, có lẽ bạn deploy service một cách độc lập vào các VPC riêng bên trong một AWS account dùng chung, hoặc vào các VPC gắn với nhiều AWS account riêng lẻ. Dù deploy service theo cách nào, chúng tôi khuyến nghị bạn **bổ sung** các thành phần mạng để giúp định tuyến traffic giữa các VPC. Để làm vậy, có thể dùng một số dịch vụ AWS để bổ sung cho các thành phần mạng hiện có.

- **AWS Transit Gateway** — Bạn nên cân nhắc dịch vụ mạng này **đầu tiên**. Dịch vụ này đóng vai trò **trung tâm (central hub)** để định tuyến kết nối giữa các Amazon VPC, AWS account và mạng on-premises. Thông tin thêm, xem *What is a transit gateway?* (<https://docs.aws.amazon.com/vpc/latest/tgw/what-is-transit-gateway.html>) trong *Amazon VPC Transit Gateways Guide*.
- **Amazon VPC and VPN support** — Bạn có thể dùng dịch vụ này để tạo kết nối **site-to-site VPN**, nối mạng on-premises với VPC của bạn. Thông tin thêm, xem *What is AWS Site-to-Site VPN?* (<https://docs.aws.amazon.com/vpn/latest/s2svpn/VPC_VPN.html>) trong *AWS Site-to-Site VPN User Guide*.
- **Amazon VPC** — Bạn có thể dùng **Amazon VPC peering** để kết nối nhiều VPC, trong cùng account hoặc giữa các account. Thông tin thêm, xem *What is VPC peering?* (<https://docs.aws.amazon.com/vpc/latest/peering/what-is-vpc-peering.html>) trong *Amazon VPC Peering Guide*.
- **Shared VPCs** — Bạn có thể dùng một VPC và các VPC subnet trên nhiều AWS account. Thông tin thêm, xem *Working with shared VPCs* (<https://docs.aws.amazon.com/vpc/latest/userguide/vpc-sharing.html>) trong *Amazon VPC User Guide*.

**💡 Giải thích thêm**

Hai bối cảnh trang mô tả:

| Bối cảnh | Mô tả |
|---|---|
| Một account dùng chung | Mỗi team một VPC riêng, cùng nằm trong một AWS account |
| Nhiều account | Mỗi VPC gắn với một AWS account riêng |

Trong cả hai trường hợp, trang khuyến nghị **bổ sung** thành phần mạng để định tuyến traffic giữa các VPC. Bốn lựa chọn:

| Dịch vụ | Kết nối giữa | Ghi chú theo trang |
|---|---|---|
| **AWS Transit Gateway** | VPC ↔ VPC, account ↔ account, on-premises | **Cân nhắc đầu tiên**; là trung tâm định tuyến |
| **Amazon VPC and VPN support** (Site-to-Site VPN) | On-premises ↔ VPC | Kết nối site-to-site VPN |
| **Amazon VPC peering** | VPC ↔ VPC (cùng account hoặc khác account) | — |
| **Shared VPCs** | Nhiều account dùng chung một VPC và subnet | — |

```
                 AWS Transit Gateway (hub)
              ┌──────────┼──────────┐
           VPC team A  VPC team B  on-premises
           (account 1) (account 2)

VPC peering:     VPC A ◄────────► VPC B
Site-to-Site VPN: on-premises ◄──► VPC
Shared VPC:      account 1, account 2 cùng dùng một VPC / subnet
```
