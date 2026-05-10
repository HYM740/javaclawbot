package memory;

import org.slf4j.*;

import java.util.*;
import java.util.regex.*;

/**
 * 查询扩展
 *
 * 对齐 OpenClaw 的 query-expansion.ts
 *
 * 功能：
 * - 从查询中提取关键词
 * - 扩展同义词和相关词
 * - 优化 FTS 查询
 */
public class MemoryQueryExpansion {

    private static final Logger log = LoggerFactory.getLogger(MemoryQueryExpansion.class);

    /** 停用词（中英文） */
    private static final Set<String> STOP_WORDS = Set.of(
            // 英文停用词
            "a", "an", "the", "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did", "will", "would", "could", "should",
            "may", "might", "must", "shall", "can", "need", "dare", "ought", "used",
            "to", "of", "in", "for", "on", "with", "at", "by", "from", "as", "into",
            "through", "during", "before", "after", "above", "below", "between",
            "and", "but", "or", "nor", "so", "yet", "both", "either", "neither",
            "not", "only", "own", "same", "than", "too", "very", "just", "also",
            "i", "me", "my", "myself", "we", "our", "ours", "ourselves",
            "you", "your", "yours", "yourself", "yourselves",
            "he", "him", "his", "himself", "she", "her", "hers", "herself",
            "it", "its", "itself", "they", "them", "their", "theirs", "themselves",
            "what", "which", "who", "whom", "this", "that", "these", "those",
            "am", "is", "are", "was", "were", "be", "been", "being",
            // 中文停用词
            "的", "了", "和", "是", "就", "都", "而", "及", "与", "着",
            "或", "一个", "没有", "我们", "你们", "他们", "它们", "这个", "那个",
            "这些", "那些", "自己", "什么", "怎么", "如何", "为什么", "哪", "哪里"
    );

    /** 英文单词模式 */
    private static final Pattern WORD_PATTERN = Pattern.compile("[a-zA-Z][a-zA-Z0-9_-]*");

    /** 中文词模式（简单分词：每个汉字作为独立词） */
    private static final Pattern CHINESE_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5]+");

    /** 数字模式 */
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+(?:\\.\\d+)?");

    private MemoryQueryExpansion() {
        // 工具类，禁止实例化
    }

    /**
     * 从查询中提取关键词
     *
     * @param query 查询文本
     * @return 关键词列表（去重、去停用词）
     */
    public static List<String> extractKeywords(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> keywords = new LinkedHashSet<>();

        // 提取英文单词
        Matcher wordMatcher = WORD_PATTERN.matcher(query.toLowerCase());
        while (wordMatcher.find()) {
            String word = wordMatcher.group();
            if (!STOP_WORDS.contains(word) && word.length() > 1) {
                keywords.add(word);
            }
        }

        // 提取中文词（简单分词）
        Matcher chineseMatcher = CHINESE_PATTERN.matcher(query);
        while (chineseMatcher.find()) {
            String chinese = chineseMatcher.group();
            // 对于中文，每个字符作为独立词（简化处理）
            for (char c : chinese.toCharArray()) {
                String charStr = String.valueOf(c);
                if (!STOP_WORDS.contains(charStr)) {
                    keywords.add(charStr);
                }
            }
            // 同时保留完整中文词
            if (chinese.length() > 1 && !STOP_WORDS.contains(chinese)) {
                keywords.add(chinese);
            }
        }

        // 提取数字
        Matcher numberMatcher = NUMBER_PATTERN.matcher(query);
        while (numberMatcher.find()) {
            keywords.add(numberMatcher.group());
        }

        return new ArrayList<>(keywords);
    }

    /**
     * 构建 FTS 查询
     *
     * @param query 原始查询
     * @return FTS 查询字符串，失败返回 null
     */
    public static String buildFtsQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return null;
        }

        List<String> keywords = extractKeywords(query);
        if (keywords.isEmpty()) {
            return null;
        }

        // 构建 FTS5 查询
        // 使用 AND 连接所有关键词
        StringBuilder ftsQuery = new StringBuilder();
        for (int i = 0; i < keywords.size(); i++) {
            if (i > 0) {
                ftsQuery.append(" AND ");
            }
            // 转义特殊字符
            String escaped = escapeFtsTerm(keywords.get(i));
            ftsQuery.append(escaped);
        }

        return ftsQuery.toString();
    }

    /**
     * 构建 FTS 查询（使用 OR）
     *
     * @param query 原始查询
     * @return FTS 查询字符串
     */
    public static String buildFtsQueryOr(String query) {
        if (query == null || query.trim().isEmpty()) {
            return null;
        }

        List<String> keywords = extractKeywords(query);
        if (keywords.isEmpty()) {
            return null;
        }

        StringBuilder ftsQuery = new StringBuilder();
        for (int i = 0; i < keywords.size(); i++) {
            if (i > 0) {
                ftsQuery.append(" OR ");
            }
            String escaped = escapeFtsTerm(keywords.get(i));
            ftsQuery.append(escaped);
        }

        return ftsQuery.toString();
    }

    /**
     * 转义 FTS 术语
     *
     * FTS5 特殊字符: " ' { } ( ) * ^ ~ : ? \ !
     */
    public static String escapeFtsTerm(String term) {
        if (term == null || term.isEmpty()) {
            return "";
        }

        // 使用双引号包裹整个术语
        // 如果术语中包含双引号，需要转义
        if (term.contains("\"")) {
            term = term.replace("\"", "\"\"");
        }

        return "\"" + term + "\"";
    }

    /**
     * 扩展查询（添加同义词）
     *
     * @param query 原始查询
     * @return 扩展后的关键词列表
     */
    public static List<String> expandQuery(String query) {
        List<String> keywords = extractKeywords(query);
        Set<String> expanded = new LinkedHashSet<>(keywords);

        // 简单的同义词扩展（可根据需要扩展）
        for (String keyword : keywords) {
            List<String> synonyms = getSynonyms(keyword);
            expanded.addAll(synonyms);
        }

        return new ArrayList<>(expanded);
    }

    /**
     * 获取同义词（简化实现）
     */
    private static List<String> getSynonyms(String word) {
        // 简化实现：只处理常见同义词
        Map<String, List<String>> synonymMap = new HashMap<>();

        // 英文同义词
        synonymMap.put("bug", List.of("error", "issue", "problem", "defect"));
        synonymMap.put("fix", List.of("repair", "resolve", "solve", "patch"));
        synonymMap.put("feature", List.of("function", "capability", "functionality"));
        synonymMap.put("task", List.of("job", "work", "assignment", "todo"));
        synonymMap.put("user", List.of("customer", "client", "member"));
        synonymMap.put("api", List.of("endpoint", "interface", "service"));
        synonymMap.put("config", List.of("configuration", "setting", "preference"));
        synonymMap.put("doc", List.of("document", "documentation", "docs"));
        synonymMap.put("test", List.of("testing", "spec", "specification"));
        synonymMap.put("deploy", List.of("deployment", "release", "publish"));

        // 中文同义词
        synonymMap.put("问题", List.of("bug", "错误", "异常"));
        synonymMap.put("修复", List.of("fix", "解决", "改正"));
        synonymMap.put("功能", List.of("feature", "特性", "能力"));
        synonymMap.put("任务", List.of("task", "工作", "事项"));
        synonymMap.put("用户", List.of("user", "客户", "使用者"));
        synonymMap.put("配置", List.of("config", "设置", "参数"));

        return synonymMap.getOrDefault(word.toLowerCase(), Collections.emptyList());
    }

    /**
     * 计算查询词权重
     *
     * @param keywords 关键词列表
     * @return 关键词权重映射
     */
    public static Map<String, Double> calculateKeywordWeights(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Double> weights = new HashMap<>();
        int size = keywords.size();

        // 简单权重：前面的词权重更高
        for (int i = 0; i < size; i++) {
            String keyword = keywords.get(i);
            double weight = 1.0 - (i * 0.1); // 每个位置递减 0.1
            weight = Math.max(0.5, weight); // 最小权重 0.5
            weights.put(keyword, weight);
        }

        return weights;
    }

    /**
     * 判断是否为短语查询（包含引号）
     */
    public static boolean isPhraseQuery(String query) {
        return query != null && query.contains("\"");
    }

    /**
     * 提取短语查询中的短语
     */
    public static List<String> extractPhrases(String query) {
        if (query == null) {
            return Collections.emptyList();
        }

        List<String> phrases = new ArrayList<>();
        Pattern phrasePattern = Pattern.compile("\"([^\"]+)\"");
        Matcher matcher = phrasePattern.matcher(query);

        while (matcher.find()) {
            phrases.add(matcher.group(1));
        }

        return phrases;
    }
}