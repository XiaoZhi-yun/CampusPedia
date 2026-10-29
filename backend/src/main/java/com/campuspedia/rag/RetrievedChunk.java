package com.campuspedia.rag;

public record RetrievedChunk(String vectorId, String content, long documentId, String fileName,
                             String category, String clauseRef, String sourceUrl, double score) {}
