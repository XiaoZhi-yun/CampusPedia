package com.campuspedia.rag;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class RecursiveSplitter {

    private static final String[] SEPARATORS = {"\n\n", "\n", "。", "；", "，", " "};

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
                out.add(text.substring(i, Math.min(i + chunkSize, text.length())));
            }
            return;
        }
        String sep = SEPARATORS[sepIdx];
        String[] parts = text.split(Pattern.quote(sep), -1);
        StringBuilder buf = new StringBuilder();
        for (String part : parts) {
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
