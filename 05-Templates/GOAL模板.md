---
type: goal
id: G{{序号}}
title: {{主题}}深化
driver: survival | promotion | decision
priority: P0 | P1 | P2
deadline: {{date:YYYY-MM-DD}}
status: active | completed | archived
created: {{date:YYYY-MM-DD}}
updated: {{date:YYYY-MM-DD}}
evidence: "[[{{驱动事件链接}}]]"
---

# GOAL-{{主题}}-{{季度}}

## 负债描述
- 驱动事件: {{链接到具体事件}}
- 根治标准: {{可量化的完成标准}}

## 缺口矩阵
| 能力 | 当前 EMRG 证据 | 缺口等级 |
|-----|---------------|---------|
| ... | ... | 🔴高 / 🟡中 |

## 退出条件
- [ ] {{可验证交付物}}（验证通过标准：...）
- [ ] 未完成则于 {{deadline}} 自动移入 `.agent/goals/_archive/`
- [ ] 或用户主动延期至 YYYY-MM-DD（需说明原因）

## 错误档案关联（由错误归档 Skill 维护）
| 错误 ID | 错误类型 | 关联概念 | 预防措施 |
|--------|---------|---------|---------|

## 进度更新记录
- {{date:YYYY-MM-DD}}: 创建，缺口矩阵初始化
