# 交付清单与发布流程

> 课程提交要求：报告打印版 + 电子版压缩包（【学号_姓名】命名，含报告电子版与工程资料）交班长/学习委员。

## 1. 最终交付物

| 交付物 | 路径 | 状态 |
|---|---|---|
| 课程报告 Word | `report/课程报告.docx`（页数与体积以实际生成为准） | ✅ 已生成 |
| 课程报告 PDF（打印用） | `report/课程报告.pdf` | ✅ 已生成 |
| 工程源码 | `backend/`、`frontend/`（含 README、docs、scripts） | 已完成 |
| SDD 材料 | `docs/SPEC.md`、`docs/api.md`、`docs/eval-plan.md`、`skills/lanchat-sdd/SKILL.md`、`docs/ai-devlog.md` | 已完成 |
| 测试与压测数据 | `backend/src/test/`（160 用例全绿）、`tools/bench/`、`assets/bench/` | ✅ 完成 |
| 界面截图 | `assets/screenshots/final-*.png`（生产单服务 2880×1800，7 张） | ✅ 完成 |
| 打包压缩包 | `学号_姓名.zip`（含 jar/报告/源码/资料） | ✅ 已打包 |
| GitHub 仓库 | `https://github.com/Versifine/lanchat` + Release v1.0.0（报告 docx/pdf） | ✅ 已发布（2026-11-18，用户批准） |

## 2. 提交前检查清单

- [ ] 报告：封面信息填写完整（学院/专业班级/姓名/学号/任课教师/日期）
- [ ] 报告：目录页码回读 13+ 条、正文页码从 1 开始、图表编号连续
- [ ] 报告：数据与 `assets/bench/*.csv`、`surefire-reports` 一致
- [ ] 报告：AI 工程化章节含真实审批与迭代记录（见 `docs/ai-devlog.md`）
- [ ] 代码：`mvn -f backend/pom.xml test` 全绿；`npm --prefix frontend run build` 成功
- [ ] 代码：`scripts/build.sh` 一键构建成功，`scripts/run.sh` 单服务可访问 http://localhost:8081
- [ ] 数据：交付前清理 `data/`（测试账号/消息/上传文件），确认首启自动建库
- [ ] 截图：登录、注册、群聊、私聊、文件传输、暗色、重连、Swagger 各一张（真实单服务环境）
- [ ] 压缩包：按【学号_姓名】命名，含报告 docx/pdf + 源码 + docs + 报告图表；不含 `target/`、`node_modules/`、`.venv/`、`data/`、`.git/`
- [ ] GitHub：公开仓库、README 完善、Release v1.0、发布地址写入报告

## 3. 打包命令（待最终执行）

```bash
cd /home/versi/Projects/Java
mkdir -p /tmp/package
cp -r backend frontend docs skills scripts report assets README.md /tmp/package/
rm -rf /tmp/package/backend/target /tmp/package/frontend/node_modules /tmp/package/frontend/dist \
       /tmp/package/report/.venv /tmp/package/assets/screenshots
cp "report/课程报告.docx" "report/课程报告.pdf" /tmp/package/
cd /tmp/package && zip -qr "【学号_姓名】.zip" . -x '*.DS_Store'
cp "【学号_姓名】.zip" ~/
```

## 4. GitHub 发布（高风险动作，需用户明确同意）

1. ~~用户确认仓库名与可见性~~ 已完成：`Versifine/lanchat`，public（2026-11-18 用户批准）；
2. `gh repo create Versifine/lanchat --public --source=. --remote=origin --push`；
3. 打 tag `v1.0.0` 并创建 Release（附运行说明）；
4. 把仓库地址写进报告第 2 章"开发环境"。

**审批记录**：见 `docs/ai-devlog.md` 人工审批表；未获明确同意前不执行第 2-3 步。

## 5. 运行说明（写进 README/附录）

- 环境：JDK 21+、Node 20+（可选，若直接用已构建 jar 则只需 JDK）
- 一键运行：`./scripts/run.sh` → http://localhost:8081
- 开发模式：`./scripts/dev.sh`（后端 :8081 + 前端 :5173）
- 测试：`source scripts/env.sh && mvn -f backend/pom.xml test`
- 数据位置：仓库根 `data/`（SQLite + uploads，首次启动自动创建）
