import fs from 'node:fs';
import path from 'node:path';
import https from 'node:https';
import http from 'node:http';
import tls from 'node:tls';
import { pipeline } from 'node:stream/promises';

// 下载构建依赖；使用临时文件，完成后才替换目标文件。
const [source, destination] = process.argv.slice(2);
if (!source || !destination) throw new Error('用法：node tools/download.mjs URL 目标路径');
fs.mkdirSync(path.dirname(destination), { recursive: true });
// 可选复用本机代理；HTTPS 仍保持证书校验。
const proxy = process.env.TASK_WIDGET_HTTPS_PROXY ? new URL(process.env.TASK_WIDGET_HTTPS_PROXY) : null;
const agent = proxy ? new https.Agent() : undefined;
if (agent) agent.createConnection = (options, callback) => {
  const tunnel = http.request({ hostname: proxy.hostname, port: proxy.port, method: 'CONNECT', path: `${options.host}:${options.port || 443}` });
  tunnel.on('connect', (response, socket) => {
    if (response.statusCode !== 200) { socket.destroy(); callback(new Error(`代理连接失败：${response.statusCode}`)); return; }
    const secure = tls.connect({ socket, servername: options.servername || options.host });
    const failed = error => callback(error);
    secure.once('secureConnect', () => { secure.removeListener('error', failed); callback(null, secure); });
    secure.once('error', failed);
  });
  tunnel.on('error', callback);
  tunnel.setTimeout(60000, () => tunnel.destroy(new Error('代理连接超时')));
  tunnel.end();
};
async function request(url, start, redirects = 0) {
  if (redirects > 8) throw new Error('下载重定向次数过多');
  return new Promise((resolve, reject) => {
    const transport = url.startsWith('https:') ? https : http;
    const req = transport.get(url, { agent: url.startsWith('https:') ? agent : undefined, headers: { 'User-Agent': 'task-widget-build', ...(start ? { Range: `bytes=${start}-` } : {}) } }, response => {
      if ([301, 302, 303, 307, 308].includes(response.statusCode)) {
        response.resume();
        resolve(request(new URL(response.headers.location, url).href, start, redirects + 1));
      } else if (![200, 206].includes(response.statusCode)) {
        response.resume();
        reject(new Error(`下载失败：HTTP ${response.statusCode} ${url}`));
      } else resolve(response);
    });
    req.setTimeout(60000, () => req.destroy(new Error('下载连接超时')));
    req.on('error', reject);
  });
}
for (let attempt = 1; attempt <= 3; attempt++) {
  try {
    const start = fs.existsSync(destination + '.part') ? fs.statSync(destination + '.part').size : 0;
    const response = await request(source, start);
    console.log(`下载 ${source}（${response.headers['content-length'] || '?'} 字节）`);
    await pipeline(response, fs.createWriteStream(destination + '.part', { flags: response.statusCode === 206 ? 'a' : 'w' }));
    fs.renameSync(destination + '.part', destination);
    console.log(`已保存 ${destination}`);
    break;
  } catch (error) {
    if (attempt === 3) throw error;
    console.log(`第 ${attempt} 次下载失败：${error.message}，准备重试`);
  }
}
