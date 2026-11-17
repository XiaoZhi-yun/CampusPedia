package com.campuspedia.rag;
/**
 * 文档正文抽取，隶属 RAG 检索链路层（rag 包），把上传文件的字节流转为纯文本。
 *
 * <p>上游：{@code KnowledgeService.vectorize} 按文件名调用；下游：文本交给
 * {@code Chunker}/{@code RecursiveSplitter} 切片。支持 md/markdown/txt/pdf/doc/docx，
 * 扫描件 PDF 与无文本 DOC 明确报错，避免把空内容写入向量库。</p>
 */

import com.campuspedia.common.BizException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 按扩展名分派到对应解析器，统一输出可供切片的纯文本。
 *
 * <p>抽不出文本（扫描件 PDF、空 DOC）时直接抛 {@code BizException} 终止向量化，
 * 因为把空串或乱码写进向量库只会污染检索结果。</p>
 */
@Component
public class DocumentLoader {

    /**
     * 依扩展名抽取正文。
     *
     * @param fileName 文件名（含扩展名），大小写不敏感
     * @param bytes    文件原始字节
     * @return 抽取出的纯文本
     * @throws BizException 格式不支持或内容无有效文本时抛出
     */
    public String load(String fileName, byte[] bytes) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".md") || lower.endsWith(".txt") || lower.endsWith(".markdown")) {
            return decodeText(bytes);
        }
        if (lower.endsWith(".pdf")) {
            return extractPdf(bytes);
        }
        if (lower.endsWith(".docx")) {
            return extractDocx(bytes);
        }
        if (lower.endsWith(".doc")) {
            return extractDoc(bytes);
        }
        // 未命中白名单直接拒绝，避免二进制内容被当文本误写入向量库
        throw new BizException("不支持的格式：" + fileName + "（支持 md/txt/pdf/doc/docx）");
    }

    private String decodeText(byte[] bytes) {
        // 国内文档常见 GBK/GB2312：用替换字符 U+FFFD 判定解码错误，逐个编码回退
        for (String enc : List.of("UTF-8", "GBK", "GB2312", "UTF-16")) {
            String s = new String(bytes, Charset.forName(enc));
            if (!s.contains("\uFFFD")) {
                return s;
            }
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private String extractPdf(byte[] bytes) {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(doc);
            if (text.isBlank()) {
                // 扫描件无文本层，明确报错提示转文字版，避免静默入库
                throw new BizException("PDF 无文本（可能为扫描件，请转文字版）");
            }
            return text;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("PDF 解析失败：" + e.getMessage());
        }
    }

    private String extractDoc(byte[] bytes) {
        try (HWPFDocument doc = new HWPFDocument(new ByteArrayInputStream(bytes))) {
            String text = new WordExtractor(doc).getText();
            if (text.isBlank()) throw new BizException("DOC 无文本");
            return text;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("DOC 解析失败（建议转存为 docx）：" + e.getMessage());
        }
    }

    private String extractDocx(byte[] bytes) {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph p : doc.getParagraphs()) {
                sb.append(p.getText()).append("\n");
            }
            for (XWPFTable table : doc.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        sb.append(cell.getText()).append(" ");
                    }
                    sb.append("\n");
                }
            }
            return sb.toString();
        } catch (Exception e) {
            throw new BizException("DOCX 解析失败：" + e.getMessage());
        }
    }
}
