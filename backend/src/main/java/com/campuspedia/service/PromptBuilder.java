package com.campuspedia.service;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * RAG Prompt 组装：System Prompt + 参考资料（带 [来源N] 编号）+ 学生问题。
 */
@Component
public class PromptBuilder {

    private static final String SYSTEM_PROMPT = """
            你是「学事通」CampusPedia，河南理工大学校园学生事务智能问答助手。
            你的知识全部来自学校公开制度文件（学生手册等），只回答校园学生事务问题。

            回答规则：
            1. 只依据下方【参考资料】作答，严禁使用参考资料之外的信息编造。
            2. 若资料不足以回答，明确回复："根据现有制度文件暂未找到明确依据，建议咨询相关部门"，不要硬答。
            3. 回答要分点、简洁、先给结论；涉及金额/比例/时限/流程步骤的关键信息必须与原文逐字一致。
            4. 每个结论后用 [来源N] 标注出处；可引用"《×××》第×章第×条"原文。
            """;

    public String systemPrompt() {
        return SYSTEM_PROMPT;
    }

    /**
     * 组装用户消息：参考资料（按检索相似度降序编号）+ 问题。
     */
    public String userPrompt(String question, List<RetrievalResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("【参考资料】\n");
        int i = 1;
        for (RetrievalResult r : results) {
            sb.append("[来源").append(i).append("] 《").append(r.chunk().fileName()).append("》")
              .append(r.chunk().clauseRef()).append("：").append(r.chunk().text()).append("\n\n");
            i++;
        }
        sb.append("【学生问题】\n").append(question).append("\n\n");
        sb.append("请依据上述资料回答，结论后标注 [来源N]。");
        return sb.toString();
    }
}
