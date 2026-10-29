package com.campuspedia.domain;

import java.util.List;

/** 问答响应：答案 + 溯源引用列表。 */
public record ChatResponse(String answer, List<Source> sources) {}
