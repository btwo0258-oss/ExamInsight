const { app, BrowserWindow, session, shell } = require('electron')
const path = require('node:path')

const APP_URL = 'http://47.99.134.139/'
const APP_ORIGIN = new URL(APP_URL).origin
const ALLOWED_PERMISSIONS = new Set(['media', 'clipboard-sanitized-write', 'fullscreen'])

function isAppUrl(rawUrl) {
  try {
    return new URL(rawUrl).origin === APP_ORIGIN
  } catch {
    return false
  }
}

function openExternalUrl(rawUrl) {
  try {
    const url = new URL(rawUrl)
    if (url.protocol === 'https:' || url.protocol === 'mailto:') {
      void shell.openExternal(url.toString())
    }
  } catch {
    // Ignore malformed links from remote content.
  }
}

function offlinePage() {
  const retryUrl = JSON.stringify(APP_URL)
  return `data:text/html;charset=UTF-8,${encodeURIComponent(`<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <title>ExamInsight - 无法连接</title>
  <style>
    * { box-sizing: border-box; }
    body { margin: 0; min-height: 100vh; display: grid; place-items: center; background: #f5f7fb; color: #172033; font-family: "Microsoft YaHei", sans-serif; }
    main { width: min(520px, calc(100vw - 40px)); padding: 48px; text-align: center; background: white; border: 1px solid #e6eaf0; border-radius: 20px; box-shadow: 0 18px 50px rgba(20, 35, 70, .08); }
    h1 { margin: 0 0 14px; font-size: 28px; }
    p { margin: 0 0 26px; color: #667085; line-height: 1.7; }
    button { border: 0; border-radius: 10px; padding: 12px 28px; color: white; background: #2368f5; font-size: 16px; cursor: pointer; }
    button:hover { background: #1856d8; }
  </style>
</head>
<body>
  <main>
    <h1>暂时无法连接 ExamInsight</h1>
    <p>请检查网络连接，或确认云端服务正在运行，然后重试。</p>
    <button type="button" onclick="location.href=${retryUrl}">重新连接</button>
  </main>
</body>
</html>`)}`
}

function configureSessionPermissions() {
  const appSession = session.defaultSession
  appSession.setPermissionCheckHandler((_webContents, permission, requestingOrigin) => (
    requestingOrigin === APP_ORIGIN && ALLOWED_PERMISSIONS.has(permission)
  ))
  appSession.setPermissionRequestHandler((_webContents, permission, callback, details) => {
    callback(isAppUrl(details.requestingUrl) && ALLOWED_PERMISSIONS.has(permission))
  })
}

function createWindow() {
  const mainWindow = new BrowserWindow({
    width: 1440,
    height: 900,
    minWidth: 1100,
    minHeight: 700,
    show: false,
    autoHideMenuBar: true,
    backgroundColor: '#f5f7fb',
    title: 'ExamInsight',
    icon: path.join(__dirname, 'build', 'icon.png'),
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      sandbox: true,
      webSecurity: true,
      allowRunningInsecureContent: false,
      spellcheck: false
    }
  })

  mainWindow.once('ready-to-show', () => mainWindow.show())

  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    if (isAppUrl(url)) {
      void mainWindow.loadURL(url)
    } else {
      openExternalUrl(url)
    }
    return { action: 'deny' }
  })

  mainWindow.webContents.on('will-navigate', (event, url) => {
    if (isAppUrl(url)) return
    event.preventDefault()
    openExternalUrl(url)
  })

  mainWindow.webContents.on('did-fail-load', (_event, errorCode, _description, url, isMainFrame) => {
    if (isMainFrame && errorCode !== -3 && isAppUrl(url)) {
      void mainWindow.loadURL(offlinePage())
    }
  })

  void mainWindow.loadURL(APP_URL)
}

const hasSingleInstanceLock = app.requestSingleInstanceLock()

if (!hasSingleInstanceLock) {
  app.quit()
} else {
  app.on('second-instance', () => {
    const [mainWindow] = BrowserWindow.getAllWindows()
    if (!mainWindow) return
    if (mainWindow.isMinimized()) mainWindow.restore()
    mainWindow.show()
    mainWindow.focus()
  })

  app.whenReady().then(() => {
    app.setAppUserModelId('com.examinsight.desktop')
    configureSessionPermissions()
    createWindow()

    app.on('activate', () => {
      if (BrowserWindow.getAllWindows().length === 0) createWindow()
    })
  })

  app.on('window-all-closed', () => app.quit())
}
