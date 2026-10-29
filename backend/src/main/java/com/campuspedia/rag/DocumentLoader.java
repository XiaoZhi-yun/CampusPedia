package com.campuspedia.rag;

import com.campuspedia.common.BizException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class DocumentLoader {

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
        throw new BizException("不支持的格式：" + fileName + "（支持 md/txt/pdf/docx）");
    }

    private String decodeText(byte[] bytes) {
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
                throw new BizException("PDF 无文本（可能为扫描件，请转文字版）");
            }
            return text;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("PDF 解析失败：" + e.getMessage());
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
