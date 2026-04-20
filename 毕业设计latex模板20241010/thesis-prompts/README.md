# 论文提示词包（适用于 VSCode + LaTeX + Copilot）

本目录用于整理《基于 Spring Boot 与 Vue 3 的企业供应链协同管理系统的设计与实现》毕业论文写作所需的提示词模板，便于在 VSCode 中直接复制给 Copilot 使用。

## 目录结构

```text
thesis-prompts/
├─ README.md
├─ 00-master-prompt.md
├─ 01-project-facts.md
├─ chapters/
│  ├─ 01-绪论.md
│  ├─ 02-理论基础与相关技术.md
│  ├─ 03-系统分析与设计.md
│  ├─ 04-系统详细设计与实现.md
│  ├─ 05-核心功能实现.md
│  ├─ 06-系统测试.md
│  └─ 07-总结与展望.md
├─ latex/
│  ├─ latex-prompts.md
│  └─ templates/
│     └─ chapter-template.tex
└─ polish/
   └─ polish-prompts.md
```

## 推荐使用顺序

### 场景一：准备写某一章正文

1. 先复制 `00-master-prompt.md` 中的总控提示词；
2. 再复制 `01-project-facts.md` 中的项目事实说明；
3. 最后复制 `chapters/` 目录下对应章节的提示词；
4. 将三段一起发送给 Copilot；
5. 生成后再使用 `polish/polish-prompts.md` 中的润色提示词统一语言风格。

### 场景二：已经有正文，需要转成 LaTeX

1. 先将正文发送给 Copilot；
2. 再使用 `latex/latex-prompts.md` 中“正文转 LaTeX”提示词；
3. 若需要统一章节格式，可参考 `latex/templates/chapter-template.tex`。

### 场景三：需要降重或统一学术风格

直接使用 `polish/polish-prompts.md` 中相应提示词。

## 使用建议

- 建议在 VSCode 中固定保留一个“主对话”，专门用于论文写作；
- 每次生成内容时，都要附带项目事实说明，避免 Copilot 写成通用模板；
- 每一章生成后，建议再追加一句：

```text
请检查上述内容是否存在与项目实际不符之处，如有请改写为更贴合本系统实现的表述。
```

## 适用论文定位

本提示词包默认适配：

- 计算机科学与技术 / 软件工程类本科毕业论文
- 管理信息系统、企业协同系统、供应链系统方向
- 前后端分离架构项目论文

## 当前项目对应的核心特征

- Spring Boot 2.7 + Vue 3 + MySQL
- JWT 身份认证与基于角色的访问控制（RBAC）
- WebSocket / STOMP 实时消息通知
- 多角色协同：顾客、销售、仓库、生产、采购、供应商、质检、系统管理员
- 订单、库存、生产、采购、供应、质检、质量追溯闭环
- 生产周计划与采购周计划自动生成

## 写作风格统一要求

建议在每次写作请求后附加如下约束：

```text
请使用正式、严谨的学术语言撰写内容，避免口语化表达；请尽量结合系统真实实现展开论述，不要虚构未实现的模块或技术。
```

