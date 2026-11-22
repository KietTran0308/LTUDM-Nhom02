# LTUDM-Nhom02 — Hệ thống Đặt món và Màn hình bếp thời gian thực

Đồ án học phần **Lập trình ứng dụng mạng** — Khoa Công nghệ Thông tin, Trường Đại học Sài Gòn (SGU).  
**Đề tài 23:** Đặt món và màn hình bếp thời gian thực (ứng dụng cho **Nhà hàng An Nhiên** theo mô hình Client–Server).

---

## Danh sách thành viên
1. **Lê Nguyễn Băng Châu** – `3124410023` – lenguyenbangchau511@gmail.com – [GitHub](https://github.com/lenguyenbangchau511)
2. **Dương Gia Huy** – `3124410112` – huydg6906@gmail.com – [GitHub](https://github.com/HUYDG69)
3. **Trần Giang Tuấn Kiệt** – `3124410178` – tuankiet21950@gmail.com – [GitHub](https://github.com/KietTran0308)

---

## Cấu trúc dự án (5 Module Maven)
Hệ thống được phát triển bằng **Java 21**, giao diện **Java Swing/AWT** và quản lý thư viện phụ thuộc qua **Apache Maven**:

* **`dtoShared`**: Thư viện dùng chung khi biên dịch các ứng dụng, chứa gói tin (`SocketPacket`, `SocketAction`), các đối tượng dữ liệu (`OrderDTO`, `BillDTO`, `TableDTO`, `UserDTO`,...), kết nối mạng (`SocketClient`, `ImageSocketClient`), mã hóa (`RSAKeyExchange`, `AESGCMUtil`) và các UI component tùy chỉnh (`CButton`, `CDialog`, `CTable`, `CToast`,...).
* **`ServerApp`**: Máy chủ đa luồng (`SocketServer`, `ClientHandler`, `SocketJsonMapper`) lắng nghe kết nối TCP cổng `8888`, xác thực, phân quyền, xử lý nghiệp vụ (`BUS`) và truy xuất cơ sở dữ liệu MySQL qua JDBC (`DAO`).
* **`ClientApp`**: Gồm giao diện và BUS cho 3 vai trò vận hành trực tiếp:
    * **Nhân viên phục vụ** (`StaffGUI`, `StaffBUS`): Xem sơ đồ bàn, chọn bàn có phiên đang hoạt động để bàn giao thiết bị cho khách và nhận yêu cầu hỗ trợ.
    * **Khách hàng tại bàn** (`CustomerGUI`, `ClientTabletBUS`): Xem thực đơn theo danh mục, quản lý giỏ hàng, gửi đơn gọi món, theo dõi trạng thái từng món theo thời gian thực và gọi hỗ trợ.
    * **Nhân viên bếp** (`KitchenGUI`, `KitchenBUS`): Tiếp nhận danh sách đơn trong ngày và cập nhật tiến độ từng dòng món (`PENDING` $\rightarrow$ `RECEIVED` $\rightarrow$ `COOKING` $\rightarrow$ `COMPLETED` $\rightarrow$ `SERVED`).
* **`PosApp`**: Ứng dụng thu ngân tại quầy (`PosGUI`, `PosBUS`) dùng để mở phiên bàn, xem tạm tính, tạo/tra cứu khách thành viên để tích điểm, áp mã giảm giá, thanh toán và đóng phiên bàn.
* **`AdminApp`**: Ứng dụng quản trị (`AdminGUI`, `AdminBUS`) dùng để quản lý thực đơn, tài khoản người dùng, điểm thưởng thành viên, voucher và thống kê doanh thu / món bán chạy.
* **`database/`**: Chứa kịch bản khởi tạo CSDL (`01_schema.sql` gồm 13 bảng và `02_seed_data.sql` nạp dữ liệu mẫu ban đầu).

---

## Về kiến trúc tổng quan
Hệ thống sử dụng **TCP Socket** theo mô hình **Nhiều Client – Một Server** (`Executors.newCachedThreadPool()`), giao tiếp trên cổng mặc định **8888**:

* **Bảo mật đường truyền:** Bắt tay trao đổi khóa phiên bằng **RSA-2048 (OAEP + SHA-256)** kèm kiểm tra dấu vân tay khóa công khai; mã hóa toàn bộ thông điệp JSON và từng khối ảnh nhị phân (tối đa 8.192 bytes/khối) bằng **AES-256-GCM**. Mật khẩu người dùng được băm bằng **PBKDF2-HMAC-SHA256** (210.000 vòng lặp, salt 16 byte).
* **Truyền ảnh nhị phân:** Client mở một kết nối TCP riêng (`ImageSocketClient` / lệnh `IMG/2`) để nhận kích thước ảnh và ghép các khối byte đã giải mã, tránh làm nghẽn luồng thông báo nghiệp vụ.

### CHIỀU ĐI (Client $\rightarrow$ Server)
```text
GUI -> ClientBUS (tạo DTO, SocketPacket) -> SocketJsonMapper (Chuyển Packet thành JSON UTF-8) -> Mã hóa AES-GCM -> TCP Socket (8888) -> ClientHandler (Giải mã, đọc JSON giải thành Packet) -> BUS Server -> DAO -> MySQL
```

### CHIỀU VỀ & ĐỒNG BỘ THỜI GIAN THỰC (Server $\rightarrow$ Client)
```text
MySQL -> DAO -> BUS Server -> ClientHandler / SocketServer (Packet thành JSON -> Mã hóa AES-GCM) -> SocketClient.listenFromServer() (Giải mã, nhận JSON giải mã Packet) -> BlockingQueue (hàng đợi packet) -> Swing Timer -> BUS Client (pollPacket(), cập nhật dữ liệu) -> GUI
```
*(Ngoài phản hồi trực tiếp cho client gửi yêu cầu, Server quản lý danh sách kết nối theo phiên bàn và vai trò để chủ động đẩy các sự kiện `ORDER_CREATED`, `ITEM_STATUS_CHANGED`, `ORDER_COMPLETED`, `SESSION_CLOSED` đến đúng thiết bị liên quan.)*

---

## Các lệnh và sự kiện giao thức chính (`SocketAction`)

| Lệnh hoặc sự kiện | Chiều truyền | Nội dung trao đổi |
| :--- | :--- | :--- |
| `LOGIN`, `LOGIN_STAFF`, `REGISTER`, `LOGOUT` | Client $\rightarrow$ Server | `LOGIN_STAFF` đăng nhập nhân viên phục vụ/bếp; `LOGIN` đăng nhập POS/Admin; `REGISTER` tạo tài khoản thành viên; `LOGOUT` kết thúc phiên trên kết nối. |
| `REQUEST_PASSWORD_RESET`, `CONFIRM_PASSWORD_RESET` | Client $\rightarrow$ Server | Gửi mã OTP 6 chữ số qua email (SMTP) và xác minh OTP để đặt lại mật khẩu mới. |
| `LIST_TABLES`, `ENTER_CUSTOMER_MODE` | Phục vụ $\rightarrow$ Server | Lấy danh sách bàn và bàn giao thiết bị cho một bàn có phiên đang hoạt động (gỡ quyền nhân viên, gắn kết nối với phiên bàn). |
| `GET_MENU_AND_CATEGORIES`, `ORDER_SUBMIT` | Thiết bị tại bàn $\rightarrow$ Server | Lấy danh mục/món ăn mở bán và gửi các dòng món trong giỏ xuống bếp; nhận lại đơn đã lưu cùng danh sách theo dõi. |
| `GET_TABLE_TRACKING`, `VIEW_SESSION_BILL`, `APPLY_TABLE_VOUCHER` | Thiết bị tại bàn $\rightarrow$ Server | Theo dõi trạng thái từng món, xem bản tạm tính tổng hợp trong phiên và áp dụng mã khuyến mãi cho phiên bàn. |
| `GET_KITCHEN_QUEUE`, `UPDATE_ITEM_STATUS` | Màn hình bếp $\rightarrow$ Server | Lấy danh sách đơn trong ngày và cập nhật trạng thái từng dòng món (bao gồm xác nhận đã phục vụ `SERVED`). |
| `ORDER_CREATED`, `ITEM_STATUS_CHANGED`, `ORDER_COMPLETED`, `SESSION_CLOSED` | Server $\rightarrow$ Client liên quan | Sự kiện đẩy thời gian thực: báo đơn mới cho bếp, cập nhật tiến độ từng món đến bàn/bếp/phục vụ, báo hoàn tất đơn và đóng phiên sau thanh toán. |
| `GET_ALL_TABLES`, `OPEN_TABLE_SESSION` | POS $\rightarrow$ Server | Lấy danh sách toàn bộ bàn hiển thị sơ đồ tại quầy và mở phiên phục vụ mới cho bàn trống. |
| `GET_TABLE_BILL_PREVIEW`, `MARK_ITEM_SERVED`, `PAY_TABLE_BILL` | POS $\rightarrow$ Server | Lấy bản tạm tính kèm mã phiên bản đối chiếu (`checkoutRevision`), xác nhận thu tiền, cộng điểm thành viên và đóng phiên bàn. |
| `ADMIN_GET_FOODS`, `ADMIN_GET_CATEGORIES`, `ADMIN_ADD_FOOD`, `ADMIN_UPDATE_FOOD`, `ADMIN_DELETE_FOOD` | AdminApp $\rightarrow$ Server | Quản lý danh mục và thực đơn (xem, thêm, cập nhật, xóa mềm món ăn). |
| `ADMIN_GET_BILLS` | AdminApp $\rightarrow$ Server | Tra cứu danh sách hóa đơn đã thanh toán phục vụ thống kê doanh thu theo ngày và Top món bán chạy. |
| `ADMIN_GET_ACCOUNTS`, `ADMIN_CREATE_ACCOUNT`, `ADMIN_UPDATE_ACCOUNT`, `ADMIN_LOCK_ACCOUNT` | AdminApp $\rightarrow$ Server | Quản lý tài khoản người dùng (cấp tài khoản mới, cập nhật thông tin, khóa/mở khóa). |
| `ADMIN_GET_POINTS`, `ADMIN_GET_POINT_HISTORY` | AdminApp $\rightarrow$ Server | Tra cứu số dư điểm tích lũy, hạng thẻ thành viên và lịch sử giao dịch điểm. |
| `ADMIN_GET_VOUCHERS`, `ADMIN_ADD_VOUCHER`, `ADMIN_UPDATE_VOUCHER`, `ADMIN_TOGGLE_VOUCHER` | AdminApp $\rightarrow$ Server | Quản lý mã khuyến mãi (tạo mới, cập nhật điều kiện và bật/tắt trạng thái áp dụng). |

---

## Hướng dẫn cài đặt và chạy chương trình

### 1. Yêu cầu môi trường
* **Java Development Kit (JDK):** Phiên bản 21.
* **Apache Maven:** Phiên bản 3.9.9 (hoặc dùng Maven tích hợp trong IntelliJ IDEA).
* **Cơ sở dữ liệu:** MySQL Server 8.0+ (hoặc MariaDB tương thích).
* **Mạng:** Mở cổng TCP `8888` trên tường lửa nếu chạy qua mạng LAN hoặc VPS.

### 2. Khởi tạo Cơ sở dữ liệu
Tạo cơ sở dữ liệu `annhien_datmon` với bảng mã `utf8mb4_unicode_ci`:
```sql
CREATE DATABASE IF NOT EXISTS annhien_datmon
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;
USE annhien_datmon;
```
Sau đó chạy lần lượt 2 tệp trong thư mục `database/`:
1. `database/01_schema.sql` (khởi tạo 13 bảng dữ liệu và ràng buộc).
2. `database/02_seed_data.sql` (nạp dữ liệu mẫu: vai trò, tài khoản, thực đơn, bàn và voucher).

### 3. Cấu hình & Khởi chạy
* **Cấu hình Server (`ServerApp`):** Thiết lập biến môi trường hoặc tham số `-D` cho `DB_URL`, `DB_USER`, `DB_PASSWORD` (và thông tin SMTP Gmail nếu sử dụng chức năng gửi mã OTP quên mật khẩu).
* **Cách 1 — Chạy trực tiếp trên IntelliJ IDEA:**
    1. Mở thư mục gốc dự án để IntelliJ tự nạp `pom.xml` cho cả 5 module.
    2. Chạy lớp chính `ServerApp` trước để mở cổng `8888`.
    3. Lần lượt chạy `PosApp`, `ClientApp`, `AdminApp`.
* **Cách 2 — Đóng gói JAR và chạy qua dòng lệnh:**
  ```bash
  mvn clean package -DskipTests
  java -jar ServerApp/target/ServerApp-1.0-SNAPSHOT-all.jar
  ```
  Khởi chạy các ứng dụng Client (truyền IP máy chủ nếu chạy khác máy):
  ```bash
  java -Dserver.host=<DIA_CHI_IP_SERVER> -Dserver.port=8888 -jar PosApp/target/PosApp-1.0-SNAPSHOT-all.jar
  java -Dserver.host=<DIA_CHI_IP_SERVER> -Dserver.port=8888 -jar ClientApp/target/ClientApp-1.0-SNAPSHOT-all.jar
  java -Dserver.host=<DIA_CHI_IP_SERVER> -Dserver.port=8888 -jar AdminApp/target/AdminApp-1.0-SNAPSHOT-all.jar
  ```
  *(Lưu ý: Khi khởi tạo cặp khóa RSA mới trên máy chủ lần đầu, sao chép chuỗi `SERVER_RSA_SHA256` in trên console của Server và cập nhật vào cấu hình `ServerConfig.SERVER_RSA_SHA256` phía Client.)*

### 4. Tài khoản thử nghiệm (Demo) & Kịch bản vận hành
| Vai trò | Email đăng nhập | Mật khẩu | Chức năng chính |
| :--- | :--- | :--- | :--- |
| **Quản trị viên / Thu ngân** | `annhien_admin@gmail.com` | `AnNhien@Admin2026` | Vận hành `PosApp` (mở bàn, thanh toán, tích điểm) và `AdminApp` (quản trị dữ liệu, thống kê). |
| **Nhân viên phục vụ** | `annhien_staff@gmail.com` | `AnNhien@Staff2026` | Đăng nhập `ClientApp`, chọn bàn đang mở phiên và bàn giao thiết bị cho khách. |
| **Nhân viên bếp** | `annhien_kitchen@gmail.com` | `AnNhien@Kitchen2026` | Đăng nhập `ClientApp`, nhận đơn thời gian thực và cập nhật tiến độ từng món. |
| **Khách hàng thành viên** | `annhien_customer@gmail.com` | `AnNhien@Customer2026` | Dùng SĐT/Email để tích điểm thành viên khi thanh toán tại `PosApp`. |

**Trình tự vận hành chuẩn một lượt phục vụ:**
1. Đăng nhập **PosApp** bằng tài khoản `ADMIN` $\rightarrow$ chọn bàn trống để **Mở phiên**.
2. Đăng nhập **ClientApp** bằng tài khoản `STAFF` $\rightarrow$ chọn bàn vừa mở phiên $\rightarrow$ nhấn **Bàn giao thiết bị cho khách** (`ENTER_CUSTOMER_MODE`).
3. Trên giao diện khách hàng (**ClientApp**), chọn món vào giỏ và nhấn **Xác nhận & Gửi xuống bếp** (`ORDER_SUBMIT`).
4. Đăng nhập một cửa sổ **ClientApp** khác bằng tài khoản `KITCHEN` $\rightarrow$ quan sát đơn mới hiển thị tự động và cập nhật trạng thái món qua từng công đoạn chế biến.
5. Quay lại **PosApp**, chọn bàn đang phục vụ $\rightarrow$ nhập SĐT/Email thành viên và mã giảm giá (nếu có) $\rightarrow$ nhấn **Xuất hóa đơn** và **Xác nhận đã nhận tiền** để hoàn tất thanh toán và đóng phiên bàn.