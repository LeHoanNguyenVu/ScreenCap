# 🚀 TIẾN ĐỘ & QUY TRÌNH CODE: SCEENCAP

## 1. Trạng thái hiện tại: Đã hoàn thiện 99% Core Flow & Rich Features
Dự án đã cực kỳ ổn định. Toàn bộ các kênh chia sẻ dữ liệu ra bên ngoài (Share, AI, Lens) đã được gia cố bảo mật và cấp quyền đúng chuẩn Android mới nhất.

## 2. Cấu trúc File Logic & Tính năng mới (Cập nhật)

### 🧩 Core Service
* **`FloatingService.kt`:** 
  - Điều phối trung tâm. Quản lý Ngôi sao nổi với hiệu ứng Animator "ép xung" đồ họa để chụp ảnh màn hình ngầm.
  - Tích hợp thêm Menu mở rộng: Chụp ảnh (`btn_capture`) và Quét QR trực tiếp (`btn_menu_qr`).

### 📸 Camera & Scanning
* **`ScannerActivity.kt` (MỚI):** 
  - Sử dụng CameraX để quét QR/Barcode thực tế từ camera sau.
  - Hỗ trợ Flash, hiệu ứng Scan Line chạy liên tục.
  - **Smart Actions:** Tự động nhận diện loại dữ liệu (URL -> Mở trình duyệt, Wi-Fi -> Copy Pass, Text -> Tìm Google).

### 🎨 Image Processing & Intelligence (Rich Features)
* **`CropPreviewActivity.kt`:** Hiện là trung tâm xử lý dữ liệu với các tính năng:
  - **Đa ngôn ngữ OCR:** Không chỉ Latinh, đã hỗ trợ thêm Tiếng Nhật, Trung, Hàn thông qua Google ML Kit.
  - **Offline Translation:** Tự động nhận diện ngôn ngữ và dịch sang Tiếng Việt ngay trong app (không cần Internet).
  - **Quét QR từ ảnh:** Cho phép người dùng chụp/cắt một mã QR trên màn hình và giải mã ngay lập tức.
  - **Google Lens Search:** Tích hợp sâu với Google Search App để tìm kiếm thông tin hình ảnh.
  - **AI Edit (Gemini):** Tích hợp FileProvider để gửi ảnh và Prompt sang app Gemini, biến SceenCap thành cầu nối cho AI.
  - **Hệ thống Help UX:** Các icon `ib_help_...` đi kèm bảng mô tả `tvHelpDescription` giúp người dùng mới dễ dàng tiếp cận tính năng.

## 3. Các Bug & Rào cản đã vượt qua (Cập nhật)
1. ✅ **FileProvider Security:** Đã cấu hình `filepaths.xml` và `FileProvider` để chia sẻ ảnh an toàn giữa các App (Zalo, Gemini, Google Lens).
2. ✅ **Fix Lỗi Share/AI/Lens:** Đã thêm `ClipData` và gỡ bỏ `finish()` sớm để đảm quyền truy cập URI không bị ngắt quãng trên Android 13/14+.
3. ✅ **Xử lý bộ nhớ:** Sử dụng `cacheDir` và timestamp để quản lý ảnh tạm, tránh xung đột dữ liệu.
4. ✅ **Bàn phím đè UI:** Xử lý `dispatchTouchEvent` và `hideKeyboard` để đóng bàn phím êm ái khi người dùng chạm ra ngoài vùng nhập AI Prompt.
5. ✅ **Furigana Handling:** Thêm cảnh báo khi quét tiếng Nhật có Furigana để người dùng chủ động điều chỉnh vùng cắt.

## 4. Công việc còn lại
* [ ] **Tối ưu hóa dung lượng:** Kiểm tra việc giải phóng bộ nhớ của `capturedBitmap` sau khi hoàn tất chu trình.
* [ ] **Cải thiện UI/UX:** Làm đẹp hơn các Dialog thông báo kết quả QR để trông hiện đại hơn.
* [ ] **Build & Test:** Kiểm tra độ ổn định trên các dòng máy khác nhau (Samsung OneUI, Pixel).
