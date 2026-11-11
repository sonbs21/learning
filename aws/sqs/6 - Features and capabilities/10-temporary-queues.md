# Temporary queue trong Amazon SQS (Amazon SQS temporary queues)

> Nguồn: [Amazon SQS temporary queues](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-temporary-queues.html)

Temporary queue (queue tạm thời) giúp bạn tiết kiệm thời gian phát triển và chi phí triển khai khi sử dụng các mẫu nhắn tin (message pattern) phổ biến như *request-response*. Bạn có thể sử dụng [Temporary Queue Client](https://github.com/awslabs/amazon-sqs-java-temporary-queues-client) để tạo các temporary queue có thông lượng cao, tiết kiệm chi phí, và do ứng dụng quản lý (application-managed).

Client ánh xạ (map) nhiều *temporary queue* — các queue do ứng dụng quản lý được tạo theo nhu cầu (on demand) cho một process cụ thể — vào một Amazon SQS queue duy nhất một cách tự động. Điều này cho phép ứng dụng của bạn thực hiện ít API call hơn và có thông lượng cao hơn khi lưu lượng tới mỗi temporary queue thấp. Khi một temporary queue không còn được sử dụng, client tự động dọn dẹp (clean up) temporary queue đó, ngay cả khi một số process sử dụng client không được tắt (shut down) một cách sạch sẽ.

Sau đây là các lợi ích của temporary queue:

- Chúng đóng vai trò là các kênh giao tiếp nhẹ (lightweight communication channel) cho các thread hoặc process cụ thể.
- Chúng có thể được tạo và xóa mà không phát sinh chi phí bổ sung.
- Chúng tương thích về API (API-compatible) với các Amazon SQS queue tĩnh (thông thường). Điều này có nghĩa là code hiện có dùng để gửi và nhận message có thể gửi message tới và nhận message từ các virtual queue.

## Virtual queue (Virtual queues)

*Virtual queue* là các cấu trúc dữ liệu cục bộ (local data structure) mà Temporary Queue Client tạo ra. Virtual queue cho phép bạn kết hợp nhiều đích (destination) có lưu lượng thấp vào một Amazon SQS queue duy nhất. Để biết các best practice, xem Avoid reusing the same message group ID with virtual queues.

> **Lưu ý (Note)**
>
> Việc tạo một virtual queue chỉ tạo ra các cấu trúc dữ liệu tạm thời để consumer nhận message. Vì một virtual queue không thực hiện API call nào tới Amazon SQS, virtual queue không phát sinh chi phí.
>
> Các quota TPS áp dụng cho tất cả các virtual queue trên một host queue duy nhất. Để biết thêm thông tin, xem [Amazon SQS message quotas](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/quotas-messages.html).

Class wrapper `AmazonSQSVirtualQueuesClient` bổ sung hỗ trợ cho các attribute liên quan đến virtual queue. Để tạo một virtual queue, bạn phải gọi API action `CreateQueue` sử dụng attribute `HostQueueURL`. Attribute này chỉ định queue hiện có sẽ làm host cho các virtual queue.

URL của một virtual queue có định dạng như sau.

```
https://sqs.us-east-2.amazonaws.com/123456789012/MyQueue#MyVirtualQueueName
```

Khi một producer gọi API action `SendMessage` hoặc `SendMessageBatch` trên một URL virtual queue, Temporary Queue Client thực hiện những việc sau:

1. Trích xuất tên virtual queue.
2. Đính kèm tên virtual queue như một message attribute bổ sung.
3. Gửi message tới host queue.

Trong khi producer gửi message, một background thread poll host queue và gửi các message nhận được tới các virtual queue tương ứng dựa theo các message attribute.

Trong khi consumer gọi API action `ReceiveMessage` trên một URL virtual queue, Temporary Queue Client chặn (block) lời gọi đó tại chỗ (locally) cho đến khi background thread gửi một message vào virtual queue. (Quy trình này tương tự như việc prefetch message trong [Buffered Asynchronous Client](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-client-side-buffering-request-batching.html): một API action duy nhất có thể cung cấp message cho tối đa 10 virtual queue.) Việc xóa một virtual queue sẽ loại bỏ mọi tài nguyên phía client mà không cần gọi tới bản thân Amazon SQS.

Class `AmazonSQSTemporaryQueuesClient` tự động biến tất cả các queue mà nó tạo ra thành temporary queue. Nó cũng tự động tạo các host queue với cùng các queue attribute, theo nhu cầu. Tên của các queue này có chung một tiền tố (prefix) có thể cấu hình được (mặc định là `__RequesterClientQueues__`) để nhận diện chúng là temporary queue. Điều này cho phép client hoạt động như một sự thay thế trực tiếp (drop-in replacement) giúp tối ưu hóa code hiện có vốn tạo và xóa queue. Client cũng bao gồm các interface `AmazonSQSRequester` và `AmazonSQSResponder` cho phép giao tiếp hai chiều giữa các queue.

## Mẫu nhắn tin request-response (virtual queue) (Request-response messaging pattern (virtual queues))

Use case phổ biến nhất của temporary queue là mẫu nhắn tin *request-response*, trong đó một requester tạo một *temporary queue* để nhận mỗi response message. Để tránh việc tạo một Amazon SQS queue cho mỗi response message, Temporary Queue Client cho phép bạn tạo và xóa nhiều temporary queue mà không cần thực hiện bất kỳ Amazon SQS API call nào. Để biết thêm thông tin, xem Implementing request-response systems.

Sơ đồ sau cho thấy một cấu hình phổ biến sử dụng mẫu này.

![Sơ đồ của mẫu request-response được sử dụng với Amazon SQS.](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/images/sqs-request-response-pattern.png)

## Kịch bản ví dụ: Xử lý một login request (Example scenario: Processing a login request)

Kịch bản ví dụ sau cho thấy cách bạn có thể sử dụng các interface `AmazonSQSRequester` và `AmazonSQSResponder` để xử lý login request của một người dùng.

### Phía client (On the client side)

```java
public class LoginClient {

    // Specify the Amazon SQS queue to which to send requests.
    private final String requestQueueUrl;

    // Use the AmazonSQSRequester interface to create
    // a temporary queue for each response.
    private final AmazonSQSRequester sqsRequester = 
            AmazonSQSRequesterClientBuilder.defaultClient();

    LoginClient(String requestQueueUrl) {
        this.requestQueueUrl = requestQueueUrl;
    }

    // Send a login request.
    public String login(String body) throws TimeoutException {
        SendMessageRequest request = new SendMessageRequest()
                .withMessageBody(body)
                .withQueueUrl(requestQueueUrl);

        // If no response is received, in 20 seconds,
        // trigger the TimeoutException.
        Message reply = sqsRequester.sendMessageAndGetResponse(request, 
                20, TimeUnit.SECONDS);

        return reply.getBody();
    }
}
```

Việc gửi một login request thực hiện những việc sau:

1. Tạo một temporary queue.
2. Đính kèm URL của temporary queue vào message như một attribute.
3. Gửi message.
4. Nhận một response từ temporary queue.
5. Xóa temporary queue.
6. Trả về response.

### Phía server (On the server side)

Ví dụ sau giả định rằng, khi khởi tạo (upon construction), một thread được tạo ra để poll queue và gọi method `handleLoginRequest()` cho mỗi message. Ngoài ra, `doLogin()` là một method được giả định.

```java
public class LoginServer {

    // Specify the Amazon SQS queue to poll for login requests.
    private final String requestQueueUrl;

    // Use the AmazonSQSResponder interface to take care
    // of sending responses to the correct response destination.
    private final AmazonSQSResponder sqsResponder = 
            AmazonSQSResponderClientBuilder.defaultClient();

    LoginServer(String requestQueueUrl) {
        this.requestQueueUrl = requestQueueUrl;
    }

    // Process login requests from the client.
    public void handleLoginRequest(Message message) {

        // Process the login and return a serialized result.
        String response = doLogin(message.getBody());

        // Extract the URL of the temporary queue from the message attribute
        // and send the response to the temporary queue.
        sqsResponder.sendResponseMessage(MessageContent.fromMessage(message), 
                new MessageContent(response));  
    }
}
```

## Dọn dẹp queue (Cleaning up queues)

Để đảm bảo Amazon SQS thu hồi mọi tài nguyên trong bộ nhớ (in-memory resource) được sử dụng bởi các virtual queue, khi ứng dụng của bạn không còn cần Temporary Queue Client, nó nên gọi method `shutdown()`. Bạn cũng có thể sử dụng method `shutdown()` của interface `AmazonSQSRequester`.

Temporary Queue Client cũng cung cấp một cách để loại bỏ các host queue mồ côi (orphaned host queue). Đối với mỗi queue nhận được API call trong một khoảng thời gian (mặc định là năm phút), client sử dụng API action `TagQueue` để gắn tag cho queue vẫn đang được sử dụng.

> **Lưu ý (Note)**
>
> Bất kỳ API action nào được thực hiện trên một queue đều đánh dấu queue đó là không nhàn rỗi (non-idle), bao gồm cả action `ReceiveMessage` không trả về message nào.

Background thread sử dụng các API action `ListQueues` và `ListTags` để kiểm tra tất cả các queue có tiền tố đã được cấu hình, xóa bất kỳ queue nào chưa được gắn tag trong ít nhất năm phút. Bằng cách này, nếu một client không tắt (shut down) một cách sạch sẽ, các client đang hoạt động khác sẽ dọn dẹp thay cho nó. Để giảm việc trùng lặp công việc, tất cả các client có cùng tiền tố giao tiếp với nhau thông qua một work queue nội bộ dùng chung được đặt tên theo tiền tố đó.
