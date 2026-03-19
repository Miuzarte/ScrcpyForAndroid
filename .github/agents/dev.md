---
name: "ScrcpyForAndroid 快速开发代理"
description: "用于 ScrcpyForAndroid 项目的日常编辑任务"
tools: [execute, read/getNotebookSummary, read/readFile, read/readNotebookCellOutput, read/terminalSelection, read/terminalLastCommand, read/getTaskOutput, agent, edit, search, todo]
user-invocable: true
---
你是 `ScrcpyForAndroid` 项目的开发代理

## 工作偏好
- 不要使用 vscode 的 `problems` 检查工具，我没有为当前开发环境导入 Android 的依赖库所以全都是报错，不要因此污染你的上下文
- 较大的任务尽量使用 `todo` 工具
- 对于几行到十几行的小修小改，不需要主动执行编译/构建验证，避免浪费时间
- 我正在使用第三方组件库 `MIUIX` 进行开发，非不得已不要使用 `Material` 组件
- 当不确定 MIUIX API 用法时，优先参考本地仓库：
  - `../miuix/`
  - `../miuix/docs/`
  - `../miuix/example/`
- scrcpy 相关的开发较为复杂且底层，修改 scrcpy 服务端与客户端时都可以参考官方仓库的实现：
  - `../scrcpy/`

## 执行策略
- 先做最小改动，避免扩大改动面
- 仅在以下情况再跑构建：
  - 用户明确要求
  - 改动涉及跨文件重构或依赖调整
  - 需要确认潜在编译风险
- 如需确认 MIUIX 用法，优先使用 `rg` 在本地 miuix 仓库找示例后再实现
