# ExamInsight Windows 桌面客户端

这是现有 ExamInsight Web 系统的独立 Electron 桌面壳，不修改 Vue 或 Spring Boot 架构。

## 本地运行

```powershell
npm install
npm start
```

## 生成 Windows x64 安装程序

```powershell
npm run dist
```

安装程序输出到 `desktop/dist/`。桌面客户端需要联网，并依赖云端服务 `http://47.99.134.139/` 持续运行。
