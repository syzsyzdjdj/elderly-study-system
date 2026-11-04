/**
 * ChatTTS 浏览器桥接脚本
 * 用法: node tts_bridge.js "<要朗读的文本>" <输出文件.wav>
 * 原理: 用无头浏览器打开本地 Gradio 界面(127.0.0.1:7860)，
 *       填入文本 → 点击"生成" → 等待音频产出 → 下载保存。
 * 适用于任何 ChatTTS Gradio 部署（无需其暴露 REST API）。
 */
const { chromium } = require('playwright-core');
const fs = require('fs');

const TTS_URL = process.env.TTS_URL || 'http://127.0.0.1:7860/';
const CHROME = process.env.CHROME_PATH || 'C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe';

(async () => {
  const text = process.argv[2];
  const outPath = process.argv[3];
  if (!text || !outPath) {
    console.error('用法: node tts_bridge.js <text> <out.wav>');
    process.exit(2);
  }

  const browser = await chromium.launch({ executablePath: CHROME, headless: true });
  try {
    const page = await browser.newPage();
    await page.goto(TTS_URL, { waitUntil: 'domcontentloaded', timeout: 30000 });
    await page.waitForSelector('textarea', { timeout: 60000 });

    // 主文本输入框（界面第一个 textarea）
    const ta = page.locator('textarea').first();
    await ta.fill(text);

    // 点击"生成"按钮
    const btn = page.locator('button:has-text("生成")').first();
    await btn.click();

    // 轮询等待音频产出：输出区出现 /file= 下载链接即完成（首次生成需加载模型，最长等 240 秒）
    const deadline = Date.now() + 240000;
    let src = null;
    while (Date.now() < deadline) {
      await page.waitForTimeout(3000);
      const count = await page.locator('a[href*="/file="]').count();
      if (count > 0) {
        src = await page.locator('a[href*="/file="]').first().getAttribute('href');
        break;
      }
    }
    if (!src) {
      console.error('音频生成超时');
      process.exit(3);
    }

    // href 可能是完整地址或相对路径
    const url = src.startsWith('http') ? src : TTS_URL.replace(/\/$/, '') + src;
    const resp = await page.request.get(url);
    if (!resp.ok()) {
      console.error('下载音频失败: HTTP ' + resp.status());
      process.exit(4);
    }
    fs.writeFileSync(outPath, await resp.body());
    console.log('OK ' + outPath);
  } finally {
    await browser.close();
  }
})().catch((e) => {
  console.error('BRIDGE_ERR ' + e.message);
  process.exit(1);
});
