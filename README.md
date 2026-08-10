# AgentT

原生 Android (Kotlin + Jetpack Compose) Agent 应用。

设计风格与 UI 图标复刻自 [TIN](https://github.com/nnn669/TIN)。

## 当前进度

- [x] 工作区页面（TIN 聊天页排版：空会话空态 + 底部输入栏）
- [x] 左侧设置抽屉（首项：供应商）
- [x] 供应商页：添加/编辑/删除，测试 API 连接
- [ ] 对话、模型、搜索等功能

## 构建

```bash
gradle assembleDebug
```

GitHub Actions 推送 main 分支后自动构建 debug APK 并上传 artifact。
