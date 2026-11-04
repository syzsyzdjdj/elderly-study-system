
# -*- coding: utf-8 -*-
"""
ChatTTS 直连语音服务 —— 不经过 Gradio 网页界面、不需要浏览器，直接调用本地 ChatTTS-Enhanced 生成语音。

用法:
    E:\\Dlab\\python.exe tts_server.py [--port 5050]

接口:
    GET  /ping       就绪探针 -> {"status": "ok"}
    POST /generate   {"text": "..."} -> audio/wav 字节流
    POST /shutdown   退出服务（释放内存）

参数与 webui 默认一致：种子音色 2、speed_0、oral_2、laugh_0、break_0、
temperature 0.3、top_P 0.7、top_K 20，不开增强/降噪。
长文本(>60字)自动按 60 字切分逐段推理后用 ffmpeg 合并（与 webui 行为一致）。

默认常驻并在监听端口前完成首次推理预热；TTS_IDLE_TIMEOUT=0 表示不退出。
TTS_APP_ROOT 指向 ChatTTS-Enhanced 根目录，HF_HOME 指向模型缓存目录。
"""
import os
import sys
import io
import json
import time
import threading
import argparse
import logging
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

# ---------- 环境准备（必须在导入 torch / processors 之前） ----------
E_ROOT = os.path.abspath(os.environ.get('TTS_APP_ROOT', os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
os.environ.setdefault('HF_HOME', os.path.join(E_ROOT, 'models'))
os.chdir(E_ROOT)  # Enhanced 的音色和其他相对资源以应用目录为基准
if E_ROOT not in sys.path:
    sys.path.insert(0, E_ROOT)  # 让 utils / processors / modules 包可导入
FFMPEG_BIN = os.path.join(E_ROOT, 'ffmpeg', 'bin')
if os.path.isdir(FFMPEG_BIN):   # 长文本合并用 ffmpeg，系统 PATH 上没有则补上
    os.environ['PATH'] = FFMPEG_BIN + os.pathsep + os.environ.get('PATH', '')

from processors.model_processor import load_chat_tts
from processors.params.process_params import AudioPreProcessParams, TextProcessParams, EnhanceProcessParams
from processors.text_processor import batch_or_split_text
from processors.audio_processor import audio_pre_processor

import numpy as np
from scipy.io import wavfile

# 日志走 stdout：后端拉起时会重定向到 backend/logs/tts-server.log，独立运行时直接看控制台
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(message)s',
    handlers=[logging.StreamHandler(sys.stdout)])
log = logging.getLogger('tts-server')

# 与 webui 默认参数一致 —— 但针对老年用户调得更口语化、更易听清
# 口语化参数说明（取值范围 0~6）:
#   oral  (口语化):  数值越高越像真人说话，会添加"这个/啊/就/等"等自然连接词
#   laugh (笑声音):  偶尔加一声轻笑，增加亲切感（老人喜欢）
#   break (停顿度):  句子之间加自然停顿，让老人有时间消化
#   temperature/top_P/top_K: 控制采样随机性，稍高则更多样、更自然
#
# ============ 如需进一步调节，改这里或环境变量 ============
GEN_PARAMS = dict(
    audio_profile_path=2,      # Audio Seed：种子音色（稳定音色）
    speed_slider=0,            # [speed_0] 语速：0 正常，负数稍慢
    temperature=0.5,           # 采样温度：0.3保守 → 0.5自然
    top_P=0.8,                  # 核采样阈值：0.7 → 0.8
    top_K=20,
    refine_oral=4,              # [oral_4] 默认2 → 4 更口语化，加"啊/就/这个"
    refine_laugh=1,             # [laugh_1] 默认0 → 1 偶尔轻笑
    refine_break=2,             # [break_2] 默认0 → 2 句子间自然停顿
    refine_text_flag=False,
    nums2text_switch=False,
    concatenate_audio=False,
    emb_upload=False,
    emb_upload_path='',
    srt_flag=False,
    batch_processing=False,
    segment_length=60,
)
# 环境变量覆盖（不想改代码就改这里，格式如: ORAL=5 LAUGH=0 BREAK=1）
_overrides = os.environ
for _k, _kk in [('oral','refine_oral'),('laugh','refine_laugh'),('break','refine_break'),
                ('temp','temperature'),('topp','top_P'),('topk','top_K'),('speed','speed_slider')]:
    if _k in _overrides:
        try:
            GEN_PARAMS[_kk] = float(_overrides[_k]) if '.' in _overrides[_k] else int(_overrides[_k])
            log.info('参数覆盖 %s=%s（来自环境变量）', _kk, GEN_PARAMS[_kk])
        except ValueError:
            pass
# ========================================================

# 参数版本号：改动上面任何参数后 +1，旧缓存自动失效、用新参数重生成
PARAMS_VERSION = 2

# ---------- 设备：显式用 CUDA（ChatTTS 自带的 select_device 可能误判），失败自动回退 CPU ----------
import torch
import processors.model_processor as _mp

def _choose_device():
    if torch.cuda.is_available():
        try:
            free = torch.cuda.get_device_properties(0).total_memory - torch.cuda.memory_reserved(0)
            if free > 1.5 * 1024 ** 3:   # 模型约 1.1GB，留足余量
                return 'cuda'
        except Exception:
            pass
    return 'cpu'

DEVICE = os.environ.get('TTS_DEVICE', '') or _choose_device()


def _load_chat():
    """加载模型到 DEVICE，失败回退 CPU 重载"""
    from modules import ChatTTS
    _mp.chat = ChatTTS.Chat()
    try:
        _mp.chat.load_models(device=DEVICE)
        log.info('模型已加载到 %s', DEVICE)
    except Exception as e:
        log.warning('加载到 %s 失败(%s)，回退 CPU', DEVICE, e)
        _mp.chat = ChatTTS.Chat()
        _mp.chat.load_models(device='cpu')


_mp.load_chat = _load_chat   # 让 load_chat_tts() 走我们的加载逻辑

# 可选音色：key -> (显示名, E:\data 下的音色文件或 None=默认种子音色)
VOICES = {
    'default': ('默认音色', None),
    'elderly': ('中老年人', '中老年人.pt'),
    'female':  ('女声', '女声.pt'),
    'male1':   ('男声1', '男1.pt'),
    'male':    ('男声', '男声.pt'),
    'stable':  ('稳定音色', '稳定音色.pt'),
}

CHAT = None            # ChatTTS 实例（启动时加载一次）
GEN_LOCK = threading.Lock()
IN_FLIGHT = {}         # (text, voice) -> {'event', 'wav', 'err'}，相同请求合并为一次生成
IN_FLIGHT_LOCK = threading.Lock()
LAST_REQUEST_TS = time.time()
IDLE_TIMEOUT = int(os.environ.get('TTS_IDLE_TIMEOUT', '0'))  # 生产环境默认模型常驻


def _now():
    global LAST_REQUEST_TS
    LAST_REQUEST_TS = time.time()


def idle_watchdog(server):
    """空闲超时自动退出，释放模型占用的内存"""
    if IDLE_TIMEOUT <= 0:
        return
    while True:
        time.sleep(60)
        if time.time() - LAST_REQUEST_TS > IDLE_TIMEOUT:
            log.info('空闲超过 %d 秒，自动退出', IDLE_TIMEOUT)
            threading.Thread(target=server.shutdown, daemon=True).start()
            return


def generate_wav_dedup(text, voice):
    """相同 (text, voice, PARAMS_VERSION) 的并发请求合并：只生成一次，其余等同一个结果"""
    key = (text, voice, PARAMS_VERSION)
    with IN_FLIGHT_LOCK:
        entry = IN_FLIGHT.get(key)
        owner = entry is None
        if owner:
            entry = {'event': threading.Event(), 'wav': None, 'err': None}
            IN_FLIGHT[key] = entry
    if owner:
        try:
            with GEN_LOCK:   # 串行生成：torch 推理不宜并发，且避免显存翻倍
                entry['wav'] = generate_wav_bytes(text, voice)
        except Exception as e:
            entry['err'] = str(e)
        finally:
            entry['event'].set()
            with IN_FLIGHT_LOCK:
                IN_FLIGHT.pop(key, None)
    else:
        entry['event'].wait()
    _now()  # 长推理结束后重新计算空闲时间
    if entry['err']:
        raise RuntimeError(entry['err'])
    return entry['wav']


def resolve_voice(voice):
    """voice key/显示名 -> audio_profile_path（.pt 文件名或种子数字），未知则默认音色"""
    key = (voice or '').strip()
    if key and key not in VOICES:
        # 允许直接传显示名
        for k, (name, _f) in VOICES.items():
            if key == name:
                key = k
                break
    if key not in VOICES:
        key = 'default'
    _name, profile = VOICES[key]
    return profile if profile else GEN_PARAMS['audio_profile_path']


def generate_wav_bytes(text, voice=None):
    """文本 -> wav 字节流。镜像 webui 的 generate_audio 流程。"""
    text_process_params = TextProcessParams(
        batch_processing=False,
        txt_file='',
        split_text_flag=False,
        text=text,
        segment_length=GEN_PARAMS['segment_length'])
    text_segments = batch_or_split_text(text_process_params)

    audio_params = AudioPreProcessParams(
        text_segments=text_segments,
        audio_profile_path=resolve_voice(voice),
        speed_slider=GEN_PARAMS['speed_slider'],
        temperature=GEN_PARAMS['temperature'],
        top_P=GEN_PARAMS['top_P'],
        top_K=GEN_PARAMS['top_K'],
        refine_oral=GEN_PARAMS['refine_oral'],
        refine_laugh=GEN_PARAMS['refine_laugh'],
        refine_break=GEN_PARAMS['refine_break'],
        refine_text_flag=GEN_PARAMS['refine_text_flag'],
        nums2text_switch=GEN_PARAMS['nums2text_switch'],
        concatenate_audio=GEN_PARAMS['concatenate_audio'],
        emb_upload=GEN_PARAMS['emb_upload'],
        emb_upload_path=GEN_PARAMS['emb_upload_path'],
        srt_flag=GEN_PARAMS['srt_flag'],
        batch_processing=GEN_PARAMS['batch_processing'])

    enhance_params = EnhanceProcessParams(
        enhance_audio=False,   # 与 webui 默认一致：不做增强/降噪
        denoise_audio=False,
        nfe=1.0,
        solver='default',
        tau=0.1)

    original, _enhanced, _info = audio_pre_processor(audio_params, enhance_params, CHAT)
    if original is None:
        raise RuntimeError('音频生成为空（批量模式返回 None）')

    sample_rate, audio_data = original
    audio_data = np.asarray(audio_data)
    if audio_data.dtype != np.int16:
        audio_data = np.clip(audio_data, -1.0, 1.0)
        audio_data = (audio_data * 32767).astype(np.int16)

    buf = io.BytesIO()
    wavfile.write(buf, int(sample_rate), audio_data)
    return buf.getvalue()


class Handler(BaseHTTPRequestHandler):
    server_version = 'ChatTTSDirect/1.0'

    def log_message(self, fmt, *args):
        log.info('%s %s', self.address_string(), fmt % args)

    def _json(self, code, obj):
        body = json.dumps(obj, ensure_ascii=False).encode('utf-8')
        self.send_response(code)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path == '/ping':
            self._json(200, {'status': 'ok'})
        elif self.path == '/voices':
            self._json(200, {'voices': [{'key': k, 'name': v[0]} for k, v in VOICES.items()]})
        else:
            self._json(404, {'error': 'not found'})

    def do_POST(self):
        _now()
        if self.path == '/shutdown':
            self._json(200, {'status': 'bye'})
            threading.Thread(target=self.server.shutdown, daemon=True).start()
            return
        if self.path != '/generate':
            self._json(404, {'error': 'not found'})
            return
        try:
            length = int(self.headers.get('Content-Length', 0))
            data = json.loads(self.rfile.read(length).decode('utf-8'))
            text = (data.get('text') or '').strip()
            if not text:
                self._json(400, {'error': 'text is empty'})
                return
            if len(text) > 5000:
                self._json(400, {'error': 'text too long (max 5000)'})
                return
            voice = (data.get('voice') or 'default').strip()
            log.info('开始生成 (%d 字, 音色 %s): %s', len(text), voice, text[:30])
            t0 = time.time()
            wav = generate_wav_dedup(text, voice)
            log.info('生成完成，%.1f 秒，%.0f KB', time.time() - t0, len(wav) / 1024)
            self.send_response(200)
            self.send_header('Content-Type', 'audio/wav')
            self.send_header('Content-Length', str(len(wav)))
            self.end_headers()
            self.wfile.write(wav)
        except Exception as e:
            log.exception('生成失败: %s', e)
            self._json(500, {'error': str(e)})


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--port', type=int, default=5050)
    parser.add_argument('--host', default=os.environ.get('TTS_BIND_HOST', '127.0.0.1'))
    args = parser.parse_args()

    global CHAT
    log.info('加载 ChatTTS 模型（HF_HOME=%s）...', os.environ['HF_HOME'])
    t0 = time.time()
    CHAT = load_chat_tts()
    log.info('模型加载完成，%.0f 秒', time.time() - t0)
    if os.environ.get('TTS_WARMUP', '1') != '0':
        generate_wav_dedup('您好，欢迎学习。', 'default')
        log.info('首次推理预热完成')

    server = ThreadingHTTPServer((args.host, args.port), Handler)
    threading.Thread(target=idle_watchdog, args=(server,), daemon=True).start()
    log.info('TTS 直连服务已就绪: http://%s:%d (空闲超时 %d 秒，0=常驻)', args.host, args.port, IDLE_TIMEOUT)
    server.serve_forever()


if __name__ == '__main__':
    main()
