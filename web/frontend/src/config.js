// Android serves this bundle from app assets, so the remote API origin is
// supplied by the native wrapper. Browsers keep the original same-origin defaults.
const nativeServer = window.DowntifyNative?.getServerUrl?.() || ''
const nativeURL = nativeServer ? new URL(nativeServer) : null
const config = {
  PROTOCOL: process.env.PROTOCOL || nativeURL?.protocol || window.location.protocol,
  WS_PROTOCOL:
    process.env.WS_PROTOCOL ||
    (nativeURL?.protocol || window.location.protocol) === 'https:'
      ? 'wss:'
      : 'ws:',
  BACKEND: process.env.BACKEND || nativeURL?.hostname || window.location.hostname,
  PORT: process.env.PORT || nativeURL?.port || window.location.port,
  WS_PORT: process.env.WS_PORT || window.location.port,
  BASEURL: process.env.BASEURL || (nativeURL?.pathname === '/' ? '' : nativeURL?.pathname) || '',
}

export default config
