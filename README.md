# AgentT

原生 Android (Kotlin + Jetpack Compose) Agent 应用。

设计风格与 UI 图标复刻自 [TIN](https://github.com/nnn669/TIN)。

## 当前进度（测试包 v0.1）

- [x] 工作区页面（旧聊天列表，Agent 式展示，左上角设置入口）
- [x] 左侧设置抽屉（首项：供应商；其余占位，暂不实现功能）
- [ ] 供应商 / 模型 / 对话等功能

## 构建

推送 main 分支后 GitHub Actions 自动：先跑单元测试验证，通过后构建 debug APK 并上传 artifact。
