# 📸 ScreenCap - Smart Floating Capture, OCR, Translation & VietQR Deep Link

**ScreenCap** is a smart utility application that runs as a **Floating Widget** on the Android platform. It enables users to capture screens, crop images, scan QR codes, perform Optical Character Recognition (OCR), translate text into multiple languages, and make bank transfers using VietQR codes seamlessly and quickly without interrupting their mobile experience or cluttering their photo gallery.

---

## ✨ Key Features

### 1. 🌟 Smart Floating Widget
* A compact floating camera button that always displays on top of other applications. It can be dragged freely and auto-minimizes to the edge of the screen when inactive.
* Triggers primary app features instantly with a single tap.

### 2. 📸 Pro Screen Capture & Crop Engine
* **One-touch Capture:** Instantly captures the screen using `MediaProjection` and `VirtualDisplay`, working reliably across all Android versions including high-security systems (e.g., HyperOS / Android 14+).
* **Smart Cropping (Pro Crop View):**
  * Quick-drawn selection overlay for initial cropping.
  * Fine-tune the cropped region with 4 corner handles for pixel-perfect accuracy (similar to Google Photos).
  * The red floating camera icon **automatically hides** during crop mode, preventing it from obstructing screen content and ensuring a clean image.

### 3. 🧠 Smart OCR (Optical Character Recognition) & Translation
* **Offline OCR:** Integrates Google ML Kit for 100% offline text recognition.
* **Auto-Language Detection OCR:** Simultaneously runs multiple text recognizers (English, Japanese, Chinese, Korean) in parallel to extract text with high accuracy, eliminating the need to manually choose the source language.
* **Smart Translation:**
  * Auto-detects the source language of the scanned text.
  * Allows quick translation to target languages with intelligent filtering (automatically hides the detected source language from the target language selection dropdown).
* **Furigana & Japanese Character Warning:** Automatically detects Japanese text and displays a warning to ensure optimal translation formatting.

### 4. 🔗 QR Code Scanner & Instant VietQR Payments
* **Flexible Scanning:** Scan QR codes directly using the device camera or from a cropped screen capture.
* **Smart QR Actions:** Categorizes QR content automatically:
  * URL links: Provides quick actions to open in the browser.
  * Raw text: Copy text to clipboard or search on Google.
* **Direct VietQR Deep Link (App-to-App):**
  * Automatically parses Vietnamese banking QR codes (extracting beneficiary bank BIN, account number, amount, and payment memo).
  * Displays a custom transfer form with quick amount selectors (10k, 50k, 100k, etc.) and allows editing the payment memo.
  * **Direct Transfer (Browser Bypass):** Uses custom URI schemes (`vietqr://`) and universal App Links (`https://qr.vietqr.net/2/`) targeting the specific banking app (Vietcombank, Techcombank, MB Bank, BIDV, Agribank, ACB, VPBank, TPBank, etc.). The target banking app opens directly and pre-fills the transfer details automatically for instant user confirmation.

---

## 🛠️ Tech Stack

* **Language:** Kotlin
* **System Permissions:**
  * `SYSTEM_ALERT_WINDOW` (Render floating widgets over other apps).
  * `FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_MEDIA_PROJECTION` (Background projection service with safe RAM usage).
  * `CAMERA` (For QR code scanning using the camera).
* **AI & Processing Libraries:**
  * `com.google.mlkit:text-recognition` (Offline Text OCR).
  * `com.google.mlkit:barcode-scanning` (QR code parsing).
* **Custom UI/UX:**
  * Custom Views for coordinate-based drawing (`CropOverlayView`, `CropAdjustView`).
  * Smooth transition animations with light/dark theme support.

---

## 🚀 Installation & Usage

### Installation
1. Clone the repository to your local machine:
   ```bash
   git clone https://github.com/LeHoanNguyenVu/ScreenCap.git
   ```
2. Open the project in **Android Studio**.
3. Connect your Android device (or emulator) supporting API 29+.
4. Click **Run** to install the application.

### How to Use
1. **Grant Permissions:** Allow "Display over other apps" and screen recording permissions when prompted.
2. **Floating Widget:** A red/pink floating camera icon will appear on your screen.
3. **Capture & Crop:**
   * Tap the floating icon to enter crop mode.
   * Draw and drag the 4 corner handles to select the desired area.
   * Tap **Save** (download image), **Copy** (copy recognized text), **Translate** (translate scanned text), or **QR Scan** (decode QR code).
4. **VietQR Payment:**
   * Scan a bank transfer QR code.
   * Select your banking app from the dropdown list.
   * Modify the transfer amount or memo (if needed) and click **Transfer**. Your banking app will launch directly with all billing details pre-filled.
