package com.campuspedia.rag;

public record VectorHit(String id, String content, String metadataJson, double similarity) {}
