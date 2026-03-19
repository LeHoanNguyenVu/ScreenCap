# 🚀 TIẾN ĐỘ & QUY TRÌNH CODE: SCEENCAP

## 1. Trạng thái hiện tại: Đã hoàn thiện 90% Core Flow
Dự án đã có thể chạy mượt mà một vòng đời hoàn chỉnh (End-to-End Flow):
`Lướt web -> Bấm Ngôi sao -> Bấm Chụp -> Vẽ khung sơ bộ -> Tinh chỉnh khung 4 góc -> Xác nhận -> Quét chữ (OCR) -> Lưu/Copy -> Trở về lướt web (Ngôi sao sống sót).`

## 2. Cấu trúc File Logic (Cập nhật mới nhất)
* **`FloatingService.kt`:** * *Vai trò:* Trái tim của ứng dụng. Quản lý Ngôi sao nổi và Lõi Máy quay (`MediaProjection`, `VirtualDisplay`, `ImageReader`).
  * *Tuyệt chiêu:* Giữ máy quay thường trực. Sử dụng `ValueAnimator` nhấp nháy độ mờ (Alpha) của Ngôi sao trong 500ms để "Ép xung" Card đồ họa, ép HyperOS phải nhả khung hình (Fix lỗi kẹt "Đang nháy máy"). Có khóa an toàn 2.5s chống đứng máy.
* **`CropActivity.kt` & `CropOverlayView.kt`:** * *Vai trò:* Màn hình và công cụ để người dùng đục lỗ (PorterDuff.Mode.CLEAR) lấy khung chọn sơ bộ đầu tiên trên tấm nền ảnh full màn hình.
* **`CropPreviewActivity.kt` & `CropAdjustView.kt`:**
  * *Vai trò:* Trạm điều khiển cuối cùng. Chứa Cỗ máy cắt chuyên nghiệp `CropAdjustView` (tính toán tọa độ dời 4 mép, vẽ 4 góc Bracket trắng đè ngoài, crop ảnh theo thời gian thực).
  * *Logic OCR:* Gọi `TextRecognition` từ Google ML Kit mỗi khi ảnh mới được xác nhận cắt.
  * *Logic Thoát (UX):* Dùng `CropActivity.instance?.finish()` và `finish()` để đóng màn hình êm ái, KHÔNG dùng `finishAffinity()` để bảo toàn mạng sống cho `FloatingService`.

## 3. Các Bug "Sát thủ" đã tiêu diệt
1. **Lỗi lặp Toast & Chụp dính Menu:** Giải quyết bằng Cờ (Flags), Khóa an toàn (`isCaptureProcessing`) và Delay 300ms.
2. **Lỗi "Hình vuông đen" khi ấn Back:** Tắt Hardware Acceleration (`LAYER_TYPE_SOFTWARE`) cho Custom View và viết hàm `reset()`.
3. **Lỗi màn hình lười (Lazy Display) của Xiaomi HyperOS:** Xử lý bằng kỹ thuật "Ép xung" tàng hình (Alpha Animator).
4. **Lỗi Ngôi sao tàng hình & Chết yểu:** Chuyển từ cơ chế `finishAffinity` sang "đóng thủ công" từng Activity. Đăng ký `projectionCallback` để lắng nghe khi hệ thống tịch thu quyền.
5. **Lỗi UX Cắt ảnh lơ lửng:** Nâng cấp từ các nút vẽ đè chắp vá thành một Custom View (`CropAdjustView`) xử lý hình học và tọa độ `RectF` tiêu chuẩn.

## 4. Công việc tiếp theo
* [ ] **Tích hợp FileProvider:** Hoàn thiện tính năng của nút `📤 SHARE` để có thể chia sẻ trực tiếp bức ảnh cắt sang Zalo/Messenger một cách an toàn (tránh lỗi bảo mật `FileUriExposedException` của Android).
* [ ] **Kiểm tra UI/UX tổng thể:** Tinh chỉnh padding, margin, màu sắc nếu cần.
* [ ] **Tối ưu hóa tài nguyên (Refactor Code):** Dọn dẹp code rác, cắm thêm Try/Catch ở những điểm nhạy cảm.
* [ ] Thêm các tính năng phụ trợ (nếu có idea mới).