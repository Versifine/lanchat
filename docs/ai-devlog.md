# LanChat AI 开发日志

> 本文件记录 AI 工程化开发过程：需求变更、规格迭代、agent 分工、人工审批与验收结果。
> 课程要求"高风险动作要人工审批，如果不正确，回到需求，重新定义边界"，这里的记录就是证据。

## 2026-11-18

### 09:xx 需求澄清（人工）

- 用户提供课程报告说明，明确选题自由、两条路线（传统工程 / AI 工程化）、报告要求。
- 讨论后确定路线：**传统软件工程 + AI 工程化（SDD）结合**；报告需写开发环境与软件开发流程。

### 10:00–10:30 方案迭代（人工审批：否决 RPC 框架方案）

- 初版方案：轻量级 RPC 框架（BIO/NIO/虚拟线程对比）。用户反馈"老师看不懂这些"，要求做**老师看得懂的小应用**。
- 决策：改为局域网聊天室。
- 记录：这属于课程要求的"方案不正确 → 回到需求、重新定义边界"的真实案例。

### 10:40–10:50 架构迭代（人工审批：改为前后端分离）

- 初版：Swing 桌面客户端 + 自写 TCP 协议 + BIO 多线程。
- 用户要求：monorepo、前后端分离、前端漂亮组件库、后端简单写、后端整洁架构启发、数据库 SQLite。
- 决策（v2）：
  - 前端 Vue 3 + Vite + Element Plus + Pinia + Axios（纯 JS）
  - 后端 Spring Boot 3.5.16 + Java 21，单 Maven 模块 + 包分层，domain/application/adapter/infrastructure
  - SQLite（sqlite-jdbc + JdbcTemplate + SQL），WAL 模式
  - REST + WebSocket（JWT 认证）；文件 HTTP multipart 上传 + SHA-256
  - 生产形态：前端构建产物由后端托管，一键单服务
- 清理 v0.1 的 Swing 脚手架（未提交的中间产物），重写 SPEC/契约。

### 10:49 环境准备（自动，无需审批）

- 本地安装 Temurin JDK 21.0.13 与 Maven 3.9.16 到 `~/.local/opt`（无 root 权限，用户目录安装）。
- 核实：Node 24.19 / npm 11.17 / pnpm 11.22 可用；npm 官方源与 npmmirror 可达；Spring Boot 3.5.16、springdoc 2.10.0、jjwt 0.12.7、sqlite-jdbc 3.54.1.0 可拉取。
- 版本选择理由：JDK 21 LTS（生态稳定、课程通用）、Spring Boot 3.5.x（Java 21 兼容）、Element Plus 2.14（中文文档、组件全）。

### 10:55 SDD 材料（AI 产出，待人工审阅）

- 产出 `docs/SPEC.md`（v1.0）、`docs/api.md`（契约 v1）、`docs/eval-plan.md`（评测集）、`docs/ui-design.md`、`skills/lanchat-sdd/SKILL.md`。
- 下一步：Lead 分配共享任务，后端/前端 agent 并行实现，测试 agent 编写评测集与压测工具。

## 人工审批记录

| 日期 | 动作 | 审批人 | 结果 |
|---|---|---|---|
| 2026-11-18 | 否决 RPC 框架选题，改为小应用 | 用户 | ✅ 改为局域网聊天室 |
| 2026-11-18 | 改为前后端分离 + SQLite + 整洁架构 | 用户 | ✅ 采纳 v2 架构 |
| 待定 | 创建 GitHub 公开仓库并推送代码 | 用户 | ⏳ 待审批 |
| 待定 | 生成并提交最终报告 docx / zip | 用户 | ⏳ 待审批 |

## 待补充

- 每个开发节点的 agent 产出、测试结果、压测数据。
- 至少一次"验收不通过 → 回需求重定义边界"的迭代记录。
