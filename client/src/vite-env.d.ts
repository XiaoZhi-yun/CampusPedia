/**
 * Vite 环境类型声明：补齐构建工具与第三方模块的类型，不产生运行时代码。
 * 所属层：client/src 类型声明层。
 * 上下游：vite/client 提供 import.meta.env 等注入类型；markdown-it 无自带类型，
 *   以空模块声明消除 TS 报错，供 Markdown 渲染相关组件引用。
 */
/// <reference types="vite/client" />
/** 为未附带类型的 markdown-it 做宽松模块声明，使 TS 允许以默认导入使用。 */
declare module 'markdown-it'
