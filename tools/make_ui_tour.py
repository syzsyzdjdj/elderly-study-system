"""Encode a silent screenshot tour; source screenshots remain unchanged.

Optional authoring dependencies: Pillow, imageio-ffmpeg. Not needed to run the app.
"""
import argparse
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageOps
import imageio_ffmpeg

ROOT = Path(__file__).resolve().parents[1]
SCENES = [
    ('login.jpg', '01  登录入口'),
    ('home.jpg', '02  学习首页与常用功能'),
    ('study.jpg', '03  代码绘制的课程步骤示意'),
    ('display.jpg', '04  字号、行距与对比度设置'),
    ('recommendation.jpg', '05  推荐来源与补位说明'),
    ('qa.jpg', '06  无 AI Key 的 FAQ 问答'),
    ('admin.jpg', '07  管理后台 · 隔离演示数据库'),
]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--font', type=Path, default=Path('C:/Windows/Fonts/msyh.ttc'))
    parser.add_argument('--output', type=Path, default=ROOT / 'docs/screenshots/ui-tour.mp4')
    args = parser.parse_args()
    if not args.font.is_file(): parser.error('Provide a CJK font file with --font')
    font = ImageFont.truetype(str(args.font), 23)
    small = ImageFont.truetype(str(args.font), 17)
    encoder = imageio_ffmpeg.write_frames(str(args.output), (1280, 800), fps=10,
        codec='libx264', pix_fmt_in='rgb24', output_params=['-movflags', '+faststart', '-crf', '22'],
        ffmpeg_log_level='error', macro_block_size=1)
    encoder.send(None)
    try:
        for name, label in SCENES:
            canvas = Image.new('RGB', (1280, 800), '#edf4ef')
            draw = ImageDraw.Draw(canvas)
            draw.rectangle((0, 0, 1280, 70), fill='#123f2c')
            draw.text((24, 8), label, fill='white', font=font)
            draw.text((24, 41), '真实界面截图导览 / 无声 / 演示数据 / 非连续交互录屏', fill='#d4e8dd', font=small)
            with Image.open(ROOT / 'docs/screenshots' / name) as screenshot:
                fitted = ImageOps.contain(screenshot.convert('RGB'), (1280, 730))
                canvas.paste(fitted, ((1280-fitted.width)//2, 70+(730-fitted.height)//2))
            frame = canvas.tobytes()
            for _ in range(45): encoder.send(frame)
    finally:
        encoder.close()
    print('Encoded 7 scenes, 31.5 seconds:', args.output)


if __name__ == '__main__': main()
