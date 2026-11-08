# LTUDM-Nhom02
Đồ án học phần Lập trình ứng dụng mạng. Đề tài: Đặt món và màn hình bếp thời gian thực 

Danh sách thành viên:
  1. Lê Nguyễn Băng Châu - 3124410023 - lenguyenbangchau511@gmail.com
  2. Dương Gia Huy - 3124410112 - huydg6906@gmail.com
  3. Trần Giang Tuấn Kiệt - 3124410178 - tuankiet21950@gmail

Đồ án hiện có 3 module: 
    ClientApp (AuthGUI, StaffGUI, CustomerGUI, KitchenGUI, PosGUI, AdminGUI, các lớp BUS và API CLIENT
    ServerApp (SocketServer, ClientHandler, SocketJsonMapper, các lớp BUS, DAO  
    dtoShared (SocketPacket, SocketAction, các DTO và component dùng chung)

## Về kiến trúc tổng quan 
Hệ thống sử dụng TCP Socket theo mô hình nhiều Client – một Server, giao tiếp trên cổng 8888.

## CHIỀU ĐI
GUI -> ClientBUS (tạo DTO, SocketPacket) -> SocketJsonMapper (Chuyển Packet thành JSON) -> TCP Socket (8888) -> ClientHandler (Đọc JSON giải thành Packet) -> BUS Server -> DAO -> MYSQL

## CHIỀU VỀ
MYSQL -> DAO -> BUS SERVER -> ClientHandler (packet thành json) -> SocketClient.listenFromServer() (nhận Json giải mã Packet) -> Blocking Queue (hàng đợi packet) -> Bus client (pollPacket(), cập nhật dữ liệu) -> Timer -> GUI
