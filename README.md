 # Ứng dụng TVGlasses

## Chức năng chính:

1. Phát stream từ Camera
- Camera là RTSP stream
- Cấu hình bằng **RTSP URL**, có thể đọc hướng dẫn của hãng, tìm kiếm Google hoặc hỏi ChatGPT để biết cách lấy URL từ cam.
2. Phát danh sách videos trong thiết bị
- File video cần là mp4
- Kích thước video tốt nhất là khớp với kích thước màn hình
- Ứng dụng sẽ luôn chạy Fullscreen với scale mode là Cover, không có khoảng đen

## Cách thức hoạt động

- Ứng dụng được phân phối dạng APK, dành cho các thiết bị Android ARM, hỗ trợ tốt nhất trên Android TV Box
- [https://github.com/thanhtunguet/tvGlasses/releases/download/latest/app-release.apk](https://github.com/thanhtunguet/tvGlasses/releases/download/latest/app-release.apk)
- Ứng dụng sẽ khởi chạy thành default launcher của thiết bị
- Ứng dụng sẽ tự khởi động khi thiết bị reboot

## Các phím chức năng:

- **Open view mode**: Chuyển sang chế độ view (Camera hoặc Video)
- **Open Apps**: mở danh sách app hệ thống, cho phép truy cập các ứng dụng khác
- **Set as default launcher**: set ứng dụng thành Launcher của máy
- **Manage videos**: mở trang quản lý videos, xóa hoặc tải video từ USB
- **Set password**: Đặt mật khẩu đơn giản cho Màn hình cài đặt, hạn chế truy cập
- **Update**: Tải file APK mới khi có cập nhật
