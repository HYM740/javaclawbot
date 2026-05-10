package gui.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import org.slf4j.LoggerFactory
import java.awt.GraphicsEnvironment
import java.util.Locale

@OptIn(ExperimentalTextApi::class)
object CjkFontResolver {

    private val log = LoggerFactory.getLogger(CjkFontResolver::class.java)

    private val cjkFamily: FontFamily by lazy { resolve() }

    private fun resolve(): FontFamily {
        val override = System.getProperty("javaclawbot.font.override")
        val locale = if (override != null) {
            log.info("CJK font override: $override")
            Locale.forLanguageTag(override)
        } else {
            Locale.getDefault()
        }

        val os = System.getProperty("os.name").lowercase()
        val chain = fallbackChain(locale, os)

        if (chain.isEmpty()) return FontFamily.Default

        val installed = availableFonts()

        for (candidate in chain) {
            if (candidate in installed) {
                log.info("CJK font resolved: $candidate for locale=${locale.toLanguageTag()} os=$os")
                return FontFamily(candidate)
            }
        }

        log.warn("No CJK font found for locale={}, os={}; tried: {}. CJK glyphs may render incorrectly.",
            locale.toLanguageTag(), os, chain.joinToString(", "))
        return FontFamily.Default
    }

    private fun fallbackChain(locale: Locale, os: String): List<String> {
        val mac = os.contains("mac")
        val win = os.contains("win")
        return when (locale.language) {
            "zh" -> {
                val region = locale.country.uppercase()
                when {
                    region in listOf("TW", "HK", "MO") -> zhTwChain(mac, win)
                    else -> zhCnChain(mac, win)
                }
            }
            "ja" -> jaChain(mac, win)
            "ko" -> koChain(mac, win)
            else -> emptyList()
        }
    }

    private fun zhCnChain(mac: Boolean, win: Boolean): List<String> = buildList {
        if (mac) add("PingFang SC")
        if (win) add("Microsoft YaHei")
        addAll(listOf("Noto Sans CJK SC", "Source Han Sans SC", "WenQuanYi Micro Hei"))
    }

    private fun zhTwChain(mac: Boolean, win: Boolean): List<String> = buildList {
        if (mac) add("PingFang TC")
        if (win) add("Microsoft JhengHei")
        add("Noto Sans CJK TC")
    }

    private fun jaChain(mac: Boolean, win: Boolean): List<String> = buildList {
        if (mac) add("Hiragino Sans")
        if (win) add("Yu Gothic")
        addAll(listOf("Noto Sans CJK JP", "IPAexGothic"))
    }

    private fun koChain(mac: Boolean, win: Boolean): List<String> = buildList {
        if (mac) add("Apple SD Gothic Neo")
        if (win) add("Malgun Gothic")
        add("Noto Sans CJK KR")
    }

    private fun availableFonts(): Set<String> {
        val fonts = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames
        return fonts.toSet()
    }

    fun get(): FontFamily = cjkFamily
}
