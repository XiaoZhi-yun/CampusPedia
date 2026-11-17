package com.campuspedia.rag;
/**
 * 多轮查询改写，隶属 RAG 检索链路层（rag 包）。
 *
 * <p>上游：{@code RagChain.stream} 在检索前调用，把带上下文的短问句改写成可独立检索的问题；
 * 下游：{@code LlmService.chat} 非流式生成改写结果，改写后的问题交给 {@code HybridRetriever}。
 * 改写失败或条件不满足时原样返回，绝不阻断检索。</p>
 */

import com.campuspedia.service.LlmService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 把依赖上下文的追问改写成可独立检索的问题，提升多轮对话的召回质量。
 *
 * <p>只对短问题且有历史时才改写：长问题本身已足够完整，改写反而可能引入噪声；
 * 调用 LLM 属额外开销，异常时一律降级为原问题。</p>
 */
@Component
public class QueryRewriter {

    /** 复用非流式 chat 完成改写，避免与答案生成的流式通道耦合。 */
    private final LlmService llmService;

    /**
     * @param llmService 承担改写文本生成的 LLM 封装
     */
    public QueryRewriter(LlmService llmService) {
        this.llmService = llmService;
    }

    /**
     * 结合对话历史把当前问题改写为独立完整、可直接检索的问题。
     *
     * @param question 当前轮用户问题
     * @param history  历史对话文本，按时间顺序拼接进 Prompt
     * @return 改写后的问题；无需改写、LLM 返回空或调用失败时原样返回 {@code question}
     */
    public String rewrite(String question, List<String> history) {
        // 长问题或无历史时无需改写：省一次 LLM 调用，也避免改写引入语义漂移
        if (question == null || question.length() > 30 || history == null || history.isEmpty()) {
            return question;
        }
        String system = "你是查询改写助手。结合对话历史，把用户当前问题改写成独立完整、可直接检索的问题。只输出改写后的问题，不要解释。";
        String user = "历史对话：\n" + String.join("\n", history) + "\n当前问题：" + question;
        try {
            String rewritten = llmService.chat(system, user);
            return (rewritten == null || rewritten.isBlank()) ? question : rewritten.trim();
        } catch (Exception e) {
            // 改写只是增强，LLM 不可用时降级为原问题，保证检索链路不断
            return question;
        }
    }
}
