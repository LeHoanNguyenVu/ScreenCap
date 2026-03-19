# 📸 SceenCap - Trợ Lý Chụp & Phân Tích Màn Hình

## 🎯 Mục Tiêu Dự Án (Project Vision)
SceenCap là một ứng dụng Android Tiện ích hệ thống (System Utility). Ứng dụng giải quyết "nỗi đau" của người dùng khi cần chụp, cắt và tra cứu nhanh một thông tin/hình ảnh nhỏ trên màn hình mà không muốn trải qua các bước lưu ảnh rườm rà.

## ✨ Tính Năng Cốt Lõi (Core Features)
1. **Floating Assistant (Trợ lý nổi):** Một "Assistive Touch" lơ lửng trên màn hình, cho phép gọi tính năng bất cứ lúc nào (kể cả khi ở ngoài màn hình chính hay đang mở app khác).
2. **Region Capture (Vẽ để chụp):** Đóng băng màn hình và cho phép người dùng khoanh vùng/cắt (crop) chính xác khu vực cần lấy.
3. **Basic Editor (Chỉnh sửa nhanh):** Tích hợp công cụ xoay, lật, thêm văn bản trực tiếp.
4. **Visual Search (Tìm kiếm thị giác):** Quét vùng ảnh vừa cắt để tìm kiếm thông tin, link mua hàng (tương tự Google Lens).
5. **Gen AI Edit (Trợ lý AI):** Nhập prompt để AI tự động chỉnh sửa hoặc mở rộng bức ảnh (Tích hợp Gemini Canvas).

## 🛠 Công Nghệ Sử Dụng (Tech Stack)
- **Ngôn ngữ:** Kotlin (Native Android)
- **Môi trường:** Android Studio, Minimum API 26, Target API 34+
- **Core APIs:** `MediaProjection`, `VirtualDisplay`, `ImageReader`, `WindowManager`.
- **Thiết kế UI:** XML, Material Design (Floating Bubbles).