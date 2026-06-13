package com.example.sceencap.ui.scanner

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.sceencap.R
import java.text.DecimalFormat
import java.util.Locale

class BankTransferActivity : AppCompatActivity() {

    private lateinit var tvAccount: TextView
    private lateinit var tvBankName: TextView
    private lateinit var etAmount: EditText
    private lateinit var etMemo: EditText
    private lateinit var spBankApp: Spinner
    private lateinit var btnSubmit: Button
    private lateinit var btnClose: ImageButton
    private lateinit var imgBankLogo: ImageView

    private lateinit var btn10k: Button
    private lateinit var btn50k: Button
    private lateinit var btn100k: Button

    private var originalBin = ""
    private var originalAccount = ""
    private var originalAmount = ""
    private var originalMemo = ""

    data class BankApp(
        val name: String,
        val packageName: String?,
        val appId: String
    )

    private val bankApps = listOf(
        BankApp("Hệ thống tự chọn (Tất cả ứng dụng)", null, ""),
        BankApp("Vietcombank (VCB Digibank)", "com.VCB", "vcb"),
        BankApp("Techcombank Mobile", "com.techcombank.mobile", "tcb"),
        BankApp("MB Bank", "com.mbmobile.mbbank", "mb"),
        BankApp("BIDV SmartBanking", "com.bidv.smartbanking", "bidv"),
        BankApp("Agribank Plus", "com.vnpay.Agribank3g", "vba"),
        BankApp("VietinBank iPay", "com.vietinbank.ipay", "icb"),
        BankApp("Sacombank Pay", "com.sacombank.pay", "sgicb"),
        BankApp("ACB ONE", "com.acb.mb.online", "acb"),
        BankApp("VPBank NEO", "com.vpbank.neo", "vpb"),
        BankApp("TPBank Mobile", "com.tpb.mb.gprsandroid", "tpb")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bank_transfer)

        // Initialize Views
        tvAccount = findViewById(R.id.tv_recipient_account)
        tvBankName = findViewById(R.id.tv_recipient_bank)
        etAmount = findViewById(R.id.et_transfer_amount)
        etMemo = findViewById(R.id.et_transfer_memo)
        spBankApp = findViewById(R.id.sp_bank_app)
        btnSubmit = findViewById(R.id.btn_submit_transfer)
        btnClose = findViewById(R.id.btn_close_transfer)
        imgBankLogo = findViewById(R.id.img_bank_logo)

        btn10k = findViewById(R.id.btn_amt_10k)
        btn50k = findViewById(R.id.btn_amt_50k)
        btn100k = findViewById(R.id.btn_amt_100k)

        val rawQr = intent.getStringExtra("RAW_QR") ?: ""
        if (!parseVietQRDetails(rawQr)) {
            Toast.makeText(this, "Không thể giải mã dữ liệu VietQR", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupUI()
    }

    private fun parseVietQRDetails(qr: String): Boolean {
        if (!qr.startsWith("000201")) return false
        try {
            val mainTags = parseEMVCo(qr)
            val merchantInfoStr = mainTags["38"] ?: return false
            val merchantTags = parseEMVCo(merchantInfoStr)

            val bankInfoStr = merchantTags["01"] ?: return false
            val bankTags = parseEMVCo(bankInfoStr)

            originalBin = bankTags["00"] ?: ""
            originalAccount = bankTags["01"] ?: ""

            if (originalBin.isEmpty() || originalAccount.isEmpty()) return false

            originalAmount = mainTags["54"] ?: "0"
            
            val additionalDataStr = mainTags["62"]
            if (additionalDataStr != null) {
                val additionalTags = parseEMVCo(additionalDataStr)
                originalMemo = additionalTags["08"] ?: ""
            }

            return true
        } catch (e: Exception) {
            return false
        }
    }

    private fun setupUI() {
        tvAccount.text = originalAccount
        tvBankName.text = getBankNameByBin(originalBin)

        // Formatting initial amount
        val initAmtVal = originalAmount.toDoubleOrNull()?.toLong() ?: 0L
        if (initAmtVal > 0) {
            etAmount.setText(initAmtVal.toString())
        } else {
            etAmount.setText("0")
        }

        etMemo.setText(originalMemo)

        btnClose.setOnClickListener { finish() }

        // Quick amount buttons
        btn10k.setOnClickListener { addAmount(10000) }
        btn50k.setOnClickListener { addAmount(50000) }
        btn100k.setOnClickListener { addAmount(100000) }

        // Setup Bank Apps Spinner
        val spinnerAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            bankApps.map { it.name }
        )
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spBankApp.adapter = spinnerAdapter

        // Pre-select matching bank app if possible
        val matchingIndex = bankApps.indexOfFirst { it.appId.lowercase() == getBankCodeByBin(originalBin).lowercase() }
        if (matchingIndex >= 0) {
            spBankApp.setSelection(matchingIndex)
        }

        btnSubmit.setOnClickListener {
            performTransfer()
        }
    }

    private fun addAmount(value: Long) {
        val currentAmountStr = etAmount.text.toString().replace("[^0-9]".toRegex(), "")
        val currentAmount = currentAmountStr.toLongOrNull() ?: 0L
        val newAmount = currentAmount + value
        etAmount.setText(newAmount.toString())
    }

    private fun performTransfer() {
        val amountStr = etAmount.text.toString().replace("[^0-9]".toRegex(), "")
        val memoStr = etMemo.text.toString().trim()

        val generatedQr = generateVietQR(originalBin, originalAccount, amountStr, memoStr)
        val selectedApp = bankApps[spBankApp.selectedItemPosition]
        var launched = false

        if (selectedApp.packageName != null) {
            launched = launchAppIfInstalled(this, selectedApp.packageName, generatedQr)
        }

        if (!launched) {
            // Trình chọn của hệ thống sử dụng custom scheme vietqr:// để tránh mở Trình duyệt
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("vietqr://$generatedQr"))
                startActivity(intent)
            } catch (e: Exception) {
                // Nếu thất bại hoàn toàn (hiếm khi), mở link NAPAS chính thức
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://qr.vietqr.net/2/$generatedQr"))
                    startActivity(intent)
                } catch (ex: Exception) {
                    Toast.makeText(this, "Không thể mở ứng dụng thanh toán: ${ex.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun launchAppIfInstalled(context: Context, packageName: String, generatedQr: String): Boolean {
        // Cách 1: Thử bằng scheme vietqr:// trực tiếp vào app
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("vietqr://$generatedQr"))
            intent.setPackage(packageName)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return true
        } catch (e: Exception) {
            // Cách 2: Thử bằng App Link chính thức của NAPAS trực tiếp vào app
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://qr.vietqr.net/2/$generatedQr"))
                intent.setPackage(packageName)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (ex: Exception) {
                return false
            }
        }
    }

    private fun generateVietQR(bin: String, account: String, amount: String, description: String): String {
        val sb = StringBuilder()
        sb.append("000201") // Payload Format Indicator
        sb.append("010212") // Point of Initiation Method: Dynamic

        // Merchant Information (Tag 38)
        val merchantInfo = java.lang.StringBuilder()
        merchantInfo.append("0010A000000727") // GUID

        // Bank Account Info (Tag 38.01)
        val bankInfo = java.lang.StringBuilder()
        bankInfo.append("0006").append(bin)
        bankInfo.append("01").append(String.format(Locale.US, "%02d", account.length)).append(account)

        val bankInfoStr = bankInfo.toString()
        merchantInfo.append("01").append(String.format(Locale.US, "%02d", bankInfoStr.length)).append(bankInfoStr)
        merchantInfo.append("0208QRIBFTTA") // Service Code

        val merchantInfoStr = merchantInfo.toString()
        sb.append("38").append(String.format(Locale.US, "%02d", merchantInfoStr.length)).append(merchantInfoStr)

        // Transaction Currency (Tag 53)
        sb.append("5303704")

        // Transaction Amount (Tag 54)
        if (amount.isNotEmpty() && amount != "0") {
            sb.append("54").append(String.format(Locale.US, "%02d", amount.length)).append(amount)
        }

        // Country Code (Tag 58)
        sb.append("5802VN")

        // Additional Data (Tag 62)
        if (description.isNotEmpty()) {
            val addInfo = java.lang.StringBuilder()
            val cleanDesc = removeAccents(description).uppercase(Locale.US)
            addInfo.append("08").append(String.format(Locale.US, "%02d", cleanDesc.length)).append(cleanDesc)
            val addInfoStr = addInfo.toString()
            sb.append("62").append(String.format(Locale.US, "%02d", addInfoStr.length)).append(addInfoStr)
        }

        // CRC16 (Tag 63)
        sb.append("6304")
        val crc = calculateCRC16(sb.toString())
        sb.append(String.format(Locale.US, "%04X", crc))

        return sb.toString()
    }

    private fun removeAccents(src: String): String {
        val temp = java.text.Normalizer.normalize(src, java.text.Normalizer.Form.NFD)
        val pattern = java.util.regex.Pattern.compile("\\p{InCombiningDiacriticalMarks}+")
        return pattern.matcher(temp).replaceAll("")
            .replace("đ", "d")
            .replace("Đ", "D")
            .replace("[^A-Za-z0-9 ]".toRegex(), "")
    }

    private fun calculateCRC16(data: String): Int {
        var crc = 0xFFFF
        val polynomial = 0x1021
        for (b in data.toByteArray(Charsets.US_ASCII)) {
            for (i in 0 until 8) {
                val bit = (b.toInt() shr (7 - i) and 1) == 1
                val c15 = (crc shr 15 and 1) == 1
                crc = (crc shl 1) and 0xFFFF
                if (c15 xor bit) {
                    crc = crc xor polynomial
                }
            }
        }
        return crc
    }

    private fun parseEMVCo(qr: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        var index = 0
        while (index + 4 <= qr.length) {
            val tag = qr.substring(index, index + 2)
            val lengthStr = qr.substring(index + 2, index + 4)
            val length = lengthStr.toIntOrNull() ?: break
            index += 4
            if (index + length > qr.length) break
            val value = qr.substring(index, index + length)
            result[tag] = value
            index += length
        }
        return result
    }

    private fun getBankNameByBin(bin: String): String {
        return when (bin) {
            "970436" -> "Vietcombank (VCB)"
            "970407" -> "Techcombank (TCB)"
            "970422" -> "MB Bank"
            "970418" -> "BIDV"
            "970405" -> "Agribank"
            "970415" -> "VietinBank"
            "970403" -> "Sacombank"
            "970416" -> "ACB"
            "970432" -> "VPBank"
            "970423" -> "TPBank"
            "970441" -> "VIB"
            "970429" -> "SCB"
            "970443" -> "SHB"
            "970428" -> "Nam A Bank"
            "970437" -> "HDBank"
            "970454" -> "MSB"
            "970448" -> "OCB"
            "970439" -> "Shinhan Bank"
            else -> "Ngân hàng liên kết (BIN: $bin)"
        }
    }

    private fun getBankCodeByBin(bin: String): String {
        return when (bin) {
            "970436" -> "vcb"
            "970407" -> "tcb"
            "970422" -> "mb"
            "970418" -> "bidv"
            "970405" -> "vba"
            "970415" -> "icb"
            "970403" -> "sgicb"
            "970416" -> "acb"
            "970432" -> "vpb"
            "970423" -> "tpb"
            else -> ""
        }
    }
}
