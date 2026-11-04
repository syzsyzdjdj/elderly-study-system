package com.elderly.spark.wordseg;

import com.huaban.analysis.jieba.JiebaSegmenter;
import org.apache.spark.api.java.function.MapPartitionsFunction;
import org.apache.spark.sql.Row;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * jieba 分词 + 过滤（mapPartitions 内每分区创建一个 JiebaSegmenter：
 * WordDictionary 懒加载非线程安全，分区内单线程初始化可避免竞争）。
 * 过滤规则：纯中文、长度>=2、去掉停用虚词。
 */
public class JiebaTokenizer implements MapPartitionsFunction<Row, String> {

    private static final long serialVersionUID = 1L;

    /** 常见停用虚词（保留 手机/支付/诈骗 等内容词） */
    private static final Set<String> STOPWORDS = new HashSet<String>(Arrays.asList(
            "请问", "一下", "可以", "应该", "怎么", "什么", "为什么", "能不能", "会不会",
            "我", "你", "他", "她", "我们", "你们", "他们", "这个", "那个", "这些", "哪些",
            "的", "了", "是", "吗", "呢", "啊", "呀", "哦", "嗯", "吧", "被", "把", "在",
            "和", "与", "或", "就", "都", "还", "要", "想", "需要", "没有", "不是", "就是",
            "现在", "然后", "时候", "地方", "东西", "事情", "如果", "因为", "所以", "但是",
            "请问一下", "怎么办", "怎么样", "好不好", "行不行", "哪里", "为什么", "多少",
            "告诉", "知道", "麻烦"    , "谢谢", "你好", "您好"));

    /** 纯中文且长度>=2 */
    private static final Pattern CN_WORD = Pattern.compile("^(?:[\\u4e00-\\u9fa5]{2,}|[A-Za-z0-9][A-Za-z0-9._-]{1,})$");

    @Override
    public Iterator<String> call(Iterator<Row> rows) throws Exception {
        JiebaSegmenter segmenter = new JiebaSegmenter();
        List<String> result = new ArrayList<String>();
        while (rows.hasNext()) {
            String question = rows.next().getString(0);
            if (question == null || question.isEmpty()) {
                continue;
            }
            Set<String> documentWords = new HashSet<String>();
            for (String token : segmenter.sentenceProcess(question)) {
                String t = token.trim().toLowerCase(java.util.Locale.ROOT);
                if (t.length() >= 2 && CN_WORD.matcher(t).matches() && !STOPWORDS.contains(t)) {
                    documentWords.add(t);
                }
            }
            result.addAll(documentWords);
        }
        return result.iterator();
    }

    /**
     * 文档级分词：保留文档对应关系，返回空格分隔的词串（供文本分类 TF-IDF 使用）。
     * 过滤规则与词频统计一致（纯中文、长度>=2、去停用词）。
     */
    public static String toDocument(String text, JiebaSegmenter segmenter) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String token : segmenter.sentenceProcess(text)) {
            String t = token.trim().toLowerCase(java.util.Locale.ROOT);
            if (t.length() >= 2 && CN_WORD.matcher(t).matches() && !STOPWORDS.contains(t)) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(t);
            }
        }
        return sb.toString();
    }
}
