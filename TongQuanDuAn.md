# TONG QUAN DU AN: SCEENCAP

## 1. Muc tieu ung dung
SceenCap la mot cong cu tien ich (Utility App) hoat dong duoi dang Cua so noi (Floating Widget). Ung dung giup nguoi dung giai quyet bai toan: Trich xuat thong tin (Hinh anh, Van ban) tu man hinh mot cach sieu toc ma khong lam gian doan trai nghiem su dung dien thoai va khong lam rac thu vien anh.

## 2. Tinh nang Cot loi (Da hoan thien)
* Ngoi sao Lo lung (Floating Widget): Nut goi ung dung luon noi tren man hinh, co the di chuyen va thu gon.
* Chup anh Sieu toc (One-touch Capture): Bat tron khung hinh man hinh ngay lap tuc nho suc manh cua MediaProjection va VirtualDisplay. Vuot qua duoc co che chan khung hinh (Lazy Display) cua HyperOS/Android 14.
* Cat anh Chuyen nghiep (Pro Crop Engine):
  - Buoc 1: Ve nhanh de lay vung chon so bo (CropOverlayView).
  - Buoc 2: Tinh chinh lai vung cat bang 4 goc vuong boc sat mep anh (CropAdjustView), hieu ung nen toi chuan Google Photos. Co nut "Xac nhan cat" ro rang.
* Nhan dien chu viet (OCR - Optical Character Recognition): Tich hop Google ML Kit (chay Offline 100%). Tu dong doc va trich xuat chu viet co trong vung anh vua cat ra thanh van ban thuan (Text).
* Hanh dong Nhanh (Quick Actions):
  - Copy: Sao chep doan chu vua quet vao bo nho dem (Clipboard).
  - Luu: Xuat anh da cat vao thu muc Pictures/SceenCap.
  - Huy: Thoat tien trinh em ai, tra lai tu do cho nguoi dung, Ngoi sao van song.
  - Share: (Dang cho tich hop FileProvider).

## 3. Ngan xep Cong nghe (Tech Stack)
* Ngon ngu: Kotlin.
* Quyen He thong: SYSTEM_ALERT_WINDOW (Ve de), FOREGROUND_SERVICE_MEDIA_PROJECTION (Quay man hinh ngam).
* Loi Do hoa: Canvas, Paint, Custom Views (tinh toan toa do RectF, touch events).
* Tri tue Nhan tao: com.google.mlkit:text-recognition:16.0.1.
* Quan ly Vong doi: Xu ly triet de co che finish() va MediaProjection.Callback de chong tran RAM va chong bi he dieu hanh "kill" app ngam.