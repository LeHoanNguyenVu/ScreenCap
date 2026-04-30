package com.example.sceencap

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Bottom Sheet hiển thị 59 ngôn ngữ + thanh tìm kiếm realtime.
 * Khi user chọn ngôn ngữ → callback onLanguageSelected → tự động đóng.
 */
class LanguageBottomSheetFragment : BottomSheetDialogFragment() {

    var onLanguageSelected: ((code: String, name: String) -> Unit)? = null

    data class Language(val code: String, val flag: String, val name: String)

    companion object {
        // 10 ngôn ngữ ưu tiên lên đầu danh sách
        private val PRIORITY_LANGUAGES = listOf(
            Language("vi", "🇻🇳", "Tiếng Việt"),
            Language("en", "🇺🇸", "Tiếng Anh"),
            Language("ja", "🇯🇵", "Tiếng Nhật"),
            Language("zh", "🇨🇳", "Tiếng Trung"),
            Language("ko", "🇰🇷", "Tiếng Hàn"),
            Language("fr", "🇫🇷", "Tiếng Pháp"),
            Language("de", "🇩🇪", "Tiếng Đức"),
            Language("es", "🇪🇸", "Tiếng Tây Ban Nha"),
            Language("th", "🇹🇭", "Tiếng Thái"),
            Language("id", "🇮🇩", "Tiếng Indonesia"),
        )

        // 49 ngôn ngữ còn lại sắp xếp theo alphabet
        private val OTHER_LANGUAGES = listOf(
            Language("af", "🇿🇦", "Tiếng Afrikaans"),
            Language("sq", "🇦🇱", "Tiếng Albania"),
            Language("ar", "🇸🇦", "Tiếng Ả Rập"),
            Language("be", "🇧🇾", "Tiếng Belarus"),
            Language("bn", "🇧🇩", "Tiếng Bengal"),
            Language("bg", "🇧🇬", "Tiếng Bulgaria"),
            Language("ca", "🏳️", "Tiếng Catalan"),
            Language("hr", "🇭🇷", "Tiếng Croatia"),
            Language("cs", "🇨🇿", "Tiếng Séc"),
            Language("da", "🇩🇰", "Tiếng Đan Mạch"),
            Language("nl", "🇳🇱", "Tiếng Hà Lan"),
            Language("eo", "🏳️", "Tiếng Esperanto"),
            Language("et", "🇪🇪", "Tiếng Estonia"),
            Language("fi", "🇫🇮", "Tiếng Phần Lan"),
            Language("gl", "🏳️", "Tiếng Galician"),
            Language("ka", "🇬🇪", "Tiếng Georgia"),
            Language("el", "🇬🇷", "Tiếng Hy Lạp"),
            Language("gu", "🇮🇳", "Tiếng Gujarati"),
            Language("ht", "🇭🇹", "Tiếng Creole Haiti"),
            Language("he", "🇮🇱", "Tiếng Do Thái"),
            Language("hi", "🇮🇳", "Tiếng Hindi"),
            Language("hu", "🇭🇺", "Tiếng Hungary"),
            Language("is", "🇮🇸", "Tiếng Iceland"),
            Language("ga", "🇮🇪", "Tiếng Ireland"),
            Language("it", "🇮🇹", "Tiếng Ý"),
            Language("kn", "🇮🇳", "Tiếng Kannada"),
            Language("lv", "🇱🇻", "Tiếng Latvia"),
            Language("lt", "🇱🇹", "Tiếng Lithuania"),
            Language("mk", "🇲🇰", "Tiếng Macedonia"),
            Language("ms", "🇲🇾", "Tiếng Mã Lai"),
            Language("mt", "🇲🇹", "Tiếng Malta"),
            Language("mr", "🇮🇳", "Tiếng Marathi"),
            Language("no", "🇳🇴", "Tiếng Na Uy"),
            Language("fa", "🇮🇷", "Tiếng Ba Tư"),
            Language("pl", "🇵🇱", "Tiếng Ba Lan"),
            Language("pt", "🇵🇹", "Tiếng Bồ Đào Nha"),
            Language("ro", "🇷🇴", "Tiếng Romania"),
            Language("ru", "🇷🇺", "Tiếng Nga"),
            Language("sk", "🇸🇰", "Tiếng Slovak"),
            Language("sl", "🇸🇮", "Tiếng Slovenia"),
            Language("sw", "🇰🇪", "Tiếng Swahili"),
            Language("sv", "🇸🇪", "Tiếng Thụy Điển"),
            Language("tl", "🇵🇭", "Tiếng Tagalog"),
            Language("ta", "🇮🇳", "Tiếng Tamil"),
            Language("te", "🇮🇳", "Tiếng Telugu"),
            Language("tr", "🇹🇷", "Tiếng Thổ Nhĩ Kỳ"),
            Language("uk", "🇺🇦", "Tiếng Ukraine"),
            Language("ur", "🇵🇰", "Tiếng Urdu"),
            Language("cy", "🏴󠁧󠁢󠁷󠁬󠁳󠁿", "Tiếng Wales"),
        )

        val ALL_LANGUAGES = PRIORITY_LANGUAGES + OTHER_LANGUAGES
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_language_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.rv_languages)
        val searchView = view.findViewById<SearchView>(R.id.search_language)

        val adapter = LanguageAdapter(ALL_LANGUAGES.toMutableList()) { language ->
            onLanguageSelected?.invoke(language.code, language.name)
            dismiss()
        }

        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter

        // Tìm kiếm realtime
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                val query = newText?.trim()?.lowercase() ?: ""
                val filtered = if (query.isEmpty()) {
                    ALL_LANGUAGES.toMutableList()
                } else {
                    ALL_LANGUAGES.filter {
                        it.name.lowercase().contains(query) || it.code.lowercase().contains(query)
                    }.toMutableList()
                }
                adapter.updateList(filtered)
                return true
            }
        })
    }

    // -------------------------------------------------------------------------
    // Adapter nội bộ
    // -------------------------------------------------------------------------

    inner class LanguageAdapter(
        private var items: MutableList<Language>,
        private val onClick: (Language) -> Unit
    ) : RecyclerView.Adapter<LanguageAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvFlag: TextView = view.findViewById(R.id.tv_language_flag)
            val tvName: TextView = view.findViewById(R.id.tv_language_name)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_language, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val lang = items[position]
            holder.tvFlag.text = lang.flag
            holder.tvName.text = lang.name
            holder.itemView.setOnClickListener { onClick(lang) }
        }

        override fun getItemCount() = items.size

        fun updateList(newList: MutableList<Language>) {
            items = newList
            notifyDataSetChanged()
        }
    }
}
