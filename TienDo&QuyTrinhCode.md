# 🚀 Nhật Ký Tiến Độ (Development Progress)

## 📌 Giai Đoạn 1: Xây Dựng Lõi Chụp Màn Hình (Core Capture Engine)
*Mục tiêu: Gọi được Floating Button, xin quyền hệ thống và chụp ra được 1 tấm ảnh Bitmap toàn màn hình.*

### ✅ Những Bước Đã Hoàn Thành (Done)
- [x] **Setup Dự án:** Khởi tạo project Kotlin, xử lý lỗi tương thích giao diện Edge-to-Edge.
- [x] **Cơ chế Cấp quyền:** Cấu hình `SYSTEM_ALERT_WINDOW` để hiển thị đè lên các ứng dụng khác.
- [x] **Floating UI (Giao diện nổi):**
    - Khởi tạo `FloatingService` chạy ngầm bằng `WindowManager`.
    - Code logic Kéo thả (Drag & Drop) mượt mà.
    - Xử lý UX: Tính toán tọa độ và dùng `ValueAnimator` tạo hiệu ứng "Snap to Edge" (Hít vào cạnh màn hình).
    - Thiết kế Menu dạng "Bong bóng pop-up" hiện đại (Nút Chụp và Nút Tắt).
- [x] **Luồng Xin quyền Quay màn hình (Smart Flow):**
    - Khởi tạo `CaptureActivity` (Màn hình tàng hình) để gọi hộp thoại `MediaProjection`.
    - Thiết lập cơ chế "Cache Token" (Lưu thẻ bài): Chỉ xin quyền 1 lần duy nhất cho mỗi phiên bật app, các lần sau bấm chụp trực tiếp.

### 🚧 Đang Xử Lý (In Progress)
- [ ] **Chụp và đúc ảnh (VirtualDisplay & ImageReader):** - Đang tối ưu hóa luồng chụp ảnh trên các dòng máy Trung Quốc (HyperOS/MIUI) chạy Android 14.
    - Xử lý vấn đề kích thước màn hình lẻ và cơ chế cấp quyền Foreground Service nghiêm ngặt của Xiaomi.

## 📍 Giai Đoạn 2 (Sắp tới): Đóng Băng & Cắt Ảnh (Freeze & Crop)
- Chuyển Bitmap vừa chụp sang một Activity mới làm hình nền.
- Viết custom View (Canvas) để vẽ khung hình chữ nhật cắt điểm ảnh theo tọa độ ngón tay.