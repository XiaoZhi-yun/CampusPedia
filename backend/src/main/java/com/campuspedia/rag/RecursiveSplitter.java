package com.campuspedia.rag;
/**
 * 通用递归切片器，隶属 RAG 检索链路层（rag 包），按分隔符优先级把长文本切成定长带重叠的块。
 *
 * <p>上游：{@code KnowledgeService.vectorize} 对非法规类文档使用；下游：切出的文本被编码入库。
 * 与 {@code Chunker} 互补：后者保条款完整，本类保块长可控，重叠窗口用于缓解语义被边界截断。</p>
 */

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 先按自然分隔符（段落、句、逗号）递归切分，实在切不动再按固定窗口硬切。
 *
 * <p>优先在语义边界断开可减少跨块断裂；相邻块保留 {@code overlap} 重叠字符，
 * 让落在边界附近的措辞仍能被某一块完整覆盖。</p>
 */
@Component
public class RecursiveSplitter {

    private static final String[] SEPARATORS = {"\n\n", "\n", "。", "；", "，", " "};

    /**
     * 把长文本切分为带重叠的块。
     *
     * @param text      原文；为 {@code null} 或空白时返回空列表
     * @param chunkSize 单块字符上限
     * @param overlap   相邻块重叠字符数，须小于 {@code chunkSize}
     * @return 切片文本列表（已 trim）
     */
    public List<String> split(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        splitRecursive(text.trim(), chunkSize, overlap, 0, chunks);
        return chunks;
    }

    private void splitRecursive(String text, int chunkSize, int overlap, int sepIdx, List<String> out) {
        if (text.length() <= chunkSize) {
            out.add(text);
            return;
        }
        if (sepIdx >= SEPARATORS.length) {
            for (int i = 0; i < text.length(); i += Math.max(1, chunkSize - overlap)) {
                // 分隔符用尽后按固定窗口硬切，步长减去 overlap 以保留相邻块重叠
                out.add(text.substring(i, Math.min(i + chunkSize, text.length())));
            }
            return;
        }
        // 按优先级取当前分隔符：本级切不动的超长片段再交给下一级或硬切
        String sep = SEPARATORS[sepIdx];
        // -1 保留尾部空段，避免末尾分隔符之后的内容被丢弃
        String[] parts = text.split(Pattern.quote(sep), -1);
        StringBuilder buf = new StringBuilder();
        for (String part : parts) {
            if (part.length() > chunkSize) {
                if (buf.length() > 0) {
                    out.add(buf.toString().trim());
                    String tail = buf.substring(Math.max(0, buf.length() - overlap));
                    buf = new StringBuilder(tail);
                }
                splitRecursive(part, chunkSize, overlap, sepIdx + 1, out);
                continue;
            }
            if (buf.length() + part.length() + sep.length() > chunkSize && buf.length() > 0) {
                out.add(buf.toString().trim());
                String tail = buf.substring(Math.max(0, buf.length() - overlap));
                buf = new StringBuilder(tail);
            }
            if (buf.length() > 0) {
                buf.append(sep);
            }
            buf.append(part);
        }
        if (buf.length() > 0) {
            out.add(buf.toString().trim());
        }
    }
}