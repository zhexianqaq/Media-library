#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
生成「抖音解析图库」的测试数据，覆盖技术方案 §3 里的三种目录结构。

用法：
    python3 tools/make_test_data.py                # 只生成到本地 tools/_testdata
    python3 tools/make_test_data.py --push         # 生成后 adb push 到 /sdcard/抖音解析_test
    python3 tools/make_test_data.py --push --stress 10000    # 额外造 1 万个文件压测

依赖：Pillow（pip install pillow）；有 ffmpeg 时会生成真实可播放的 mp4，没有就生成同名占位文件。

生成内容：
  抖音解析_test/
    小美测评/                    # 新版结构：UP主 / 作品 / 角色-序号.ext
      今天给大家分享一个好物 #好物推荐/     # 作品名 = 简介前 15 字
        好物推荐-001.jpg
        好物推荐-002.jpg
        好物推荐-003.mp4
        好物推荐-正文.jpg          # 1080×20000 超长简介图
        meta.json                  # 可选增强
    ...
    老张vlog2/
      视频/clip.mp4               # 旧版结构：作者/视频|图片/文件名
      图片/photo.jpg
"""
import argparse
import json
import os
import random
import shutil
import subprocess
import sys

try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError:  # pragma: no cover
    print("需要 Pillow：pip install pillow", file=sys.stderr)
    sys.exit(1)

ROOT_NAME = "抖音解析_test"
HERE = os.path.dirname(os.path.abspath(__file__))
STAGE = os.path.join(HERE, "_testdata")
PAYLOAD = os.path.join(STAGE, ROOT_NAME)

PALETTE = [
    (255, 86, 118), (37, 244, 238), (86, 156, 255),
    (255, 191, 0), (150, 111, 214), (0, 200, 150),
]


def ensure_dir(path):
    os.makedirs(path, exist_ok=True)


def nomedia(path):
    """三层目录都要有 .nomedia，验证「相册看不到」"""
    open(os.path.join(path, ".nomedia"), "wb").close()


def make_image(path, size, label, color=None, fmt=None):
    color = color or random.choice(PALETTE)
    img = Image.new("RGB", size, color)
    draw = ImageDraw.Draw(img)
    draw.rectangle([0, 0, size[0] - 1, size[1] - 1], outline=(255, 255, 255), width=3)
    try:
        font = ImageFont.load_default()
    except Exception:
        font = None
    draw.text((12, 12), label, fill=(255, 255, 255), font=font)
    if size[1] > 2000:  # 长图：顶部和底部都画点内容，方便验证滚动与子采样
        for y in range(40, size[1] - 40, 900):
            draw.text((12, y), f"{label}  y={y}", fill=(255, 255, 255), font=font)
    img.save(path, format=fmt or "JPEG", quality=88, **({"optimize": True} if (fmt or "JPEG") == "JPEG" else {}))


def make_gif(path, size=(360, 360)):
    frames = []
    for i, color in enumerate(PALETTE[:4]):
        frame = Image.new("RGB", size, color)
        ImageDraw.Draw(frame).text((10, 10), f"GIF frame {i}", fill=(255, 255, 255))
        frames.append(frame)
    frames[0].save(path, save_all=True, append_images=frames[1:], duration=200, loop=0)


def make_video(path, seconds=4, size="360x640", color="black"):
    """有 ffmpeg 就生成真视频，否则写占位文件（会被扫描到但可能播放失败，用于测试错误分支）"""
    ffmpeg = shutil.which("ffmpeg")
    if ffmpeg:
        for codec in ("libx264", "mpeg4"):
            cmd = [
                ffmpeg, "-y", "-f", "lavfi",
                "-i", f"color=c={color}:s={size}:d={seconds}",
                "-f", "lavfi", "-i", f"sine=frequency=440:duration={seconds}",
                "-c:v", codec, "-pix_fmt", "yuv420p", "-c:a", "aac", "-shortest",
                "-loglevel", "error", path,
            ]
            if subprocess.call(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL) == 0:
                return True
    with open(path, "wb") as f:
        f.write(b"\x00\x00\x00\x18ftypmp42" + os.urandom(2048))
    return False


def build_douyin():
    """新版：UP主 / 简介前15字 / 角色-序号.ext"""
    cases = [
        ("小美测评", "今天给大家分享一个超好用的好物 #好物推荐", "好物推荐", 4),
        ("小美测评", "开箱明哥同款耳机 #数码开箱", "数码开箱", 3),
        ("阿泽的日常", "说说最近的一些思考和感受 #日常记录", "日常记录", 2),
        ("🐱猫猫观察日记👀", "我家猫今天又拆家了 #萌宠 #布偶猫", "布偶猫", 2),
    ]
    for creator, desc, role, count in cases:
        work_dir = os.path.join(PAYLOAD, creator, desc[:15])
        ensure_dir(work_dir)
        nomedia(work_dir)
        for i in range(1, count + 1):
            ext = "mp4" if i == count and count > 2 else "jpg"
            name = f"{role}-{i:03d}.{ext}"
            path = os.path.join(work_dir, name)
            if ext == "mp4":
                make_video(path)
            else:
                make_image(path, (1080, 1440), f"{creator} / {role} #{i}")
        # 简介长图（超长图，验证不 OOM）
        make_image(
            os.path.join(work_dir, f"{role}-正文.jpg"),
            (1080, 20000),
            f"{desc}\n长图正文测试",
        )
        # 重名正文图
        if role == "好物推荐":
            make_image(os.path.join(work_dir, f"{role}-正文-2.jpg"), (1080, 8000), "正文重名")
        # meta.json（第三期功能，验证读取与搜索）
        with open(os.path.join(work_dir, "meta.json"), "w", encoding="utf-8") as f:
            json.dump(
                {
                    "v": 1,
                    "source": "douyin",
                    "author": creator,
                    "desc": desc,
                    "tags": [t.strip("#") for t in desc.split() if t.startswith("#")],
                    "url": "https://v.douyin.com/xxxxxxx/",
                    "downloadedAt": 1731700000000,
                },
                f,
                ensure_ascii=False,
            )


def build_wechat():
    """公众号结构：<公众号名>/<标题前15字>/<角色>-001.jpg"""
    creator = "某某科技观察"
    work_dir = os.path.join(PAYLOAD, creator, "一文看懂端侧推理的现")
    ensure_dir(work_dir)
    nomedia(work_dir)
    for i in range(1, 4):
        make_image(os.path.join(work_dir, f"端侧推理-{i:03d}.png"), (1080, 1080),
                   f"公众号配图 #{i}", fmt="PNG")
    make_image(os.path.join(work_dir, "端侧推理-正文.jpg"), (1080, 6000), "公众号长图")


def build_legacy():
    """旧版：<作者>/视频/xxx.mp4、<作者>/图片/xxx.jpg"""
    creator = os.path.join(PAYLOAD, "老张vlog2")
    video_dir = os.path.join(creator, "视频")
    image_dir = os.path.join(creator, "图片")
    ensure_dir(video_dir)
    ensure_dir(image_dir)
    nomedia(video_dir)
    nomedia(image_dir)
    make_video(os.path.join(video_dir, "clip.mp4"), seconds=5)
    make_video(os.path.join(video_dir, "clip2.mp4"), seconds=3)
    make_image(os.path.join(image_dir, "photo.jpg"), (1080, 1440), "旧版图片")
    make_image(os.path.join(image_dir, "photo2.jpg"), (720, 1280), "旧版竖图")


def build_edge_cases():
    """边界数据：不匹配的文件名、四位序号、动图、隐藏文件、0 字节、.part、emoji"""
    work_dir = os.path.join(PAYLOAD, "边界测试UP", "各种奇怪文件名都要能显示")
    ensure_dir(work_dir)
    nomedia(work_dir)

    make_image(os.path.join(work_dir, "随便起的名字.jpg"), (800, 800), "不匹配规则")
    make_image(os.path.join(work_dir, "角色-1000.jpg"), (800, 800), "四位序号")
    make_image(os.path.join(work_dir, "A-B-003.jpg"), (800, 800), "角色带短横线")
    make_gif(os.path.join(work_dir, "动图-001.gif"))
    make_image(os.path.join(work_dir, "😀表情包-001.jpg"), (600, 600), "emoji 文件名")

    # 应被忽略的
    open(os.path.join(work_dir, "空的-001.jpg"), "wb").close()          # 0 字节
    open(os.path.join(work_dir, "下载中-001.mp4.part"), "wb").write(b"x" * 1024)
    open(os.path.join(work_dir, "临时-001.tmp"), "wb").write(b"x" * 1024)
    open(os.path.join(work_dir, ".隐藏文件.jpg"), "wb").write(b"x" * 1024)


def build_stress(n):
    """压测：n 个文件，分散在若干作品里（默认 10000 个 / 1000 个作品）"""
    if n <= 0:
        return
    per_work = 10
    works = max(1, n // per_work)
    print(f"  压测数据：{works} 个作品 × {per_work} 个文件 ...")
    tiny = Image.new("RGB", (64, 64), (60, 60, 70))
    for w in range(works):
        creator = os.path.join(PAYLOAD, f"压测UP{w // 50:02d}")
        work_dir = os.path.join(creator, f"压测作品{w:04d}")
        ensure_dir(work_dir)
        if w % 50 == 0:
            nomedia(creator)
        nomedia(work_dir)
        for i in range(per_work):
            path = os.path.join(work_dir, f"压测角色-{i:03d}.jpg")
            tiny.save(path, quality=60)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--push", action="store_true", help="生成后通过 adb 推到 /sdcard/抖音解析_test")
    parser.add_argument("--stress", type=int, default=0, help="额外生成 N 个压测文件")
    parser.add_argument("--keep", action="store_true", help="不清理上次生成的内容")
    args = parser.parse_args()

    if not args.keep and os.path.isdir(STAGE):
        shutil.rmtree(STAGE)
    ensure_dir(PAYLOAD)
    nomedia(PAYLOAD)

    print("生成新版结构（抖音 / 公众号） ...")
    build_douyin()
    build_wechat()
    print("生成旧版结构（作者/视频|图片） ...")
    build_legacy()
    print("生成边界数据 ...")
    build_edge_cases()
    build_stress(args.stress)

    total = sum(len(files) for _, _, files in os.walk(PAYLOAD))
    print(f"完成：{PAYLOAD}（{total} 个文件）")

    if args.push:
        adb = shutil.which("adb")
        if not adb:
            print("未找到 adb，请手动复制：", file=sys.stderr)
            print(f"  adb push \"{PAYLOAD}\" /sdcard/", file=sys.stderr)
            return 1
        if shutil.which("adb") and subprocess.call([adb, "shell", "rm", "-rf", f"/sdcard/{ROOT_NAME}"]) != 0:
            print("清空设备目录失败（可以忽略）", file=sys.stderr)
        # 中文路径直接 push 目录本身
        rc = subprocess.call([adb, "push", PAYLOAD, "/sdcard/"])
        print("push 完成" if rc == 0 else "push 失败", file=sys.stderr if rc else sys.stdout)
        print(f"提示：把图库设置里的根目录改成 /storage/emulated/0/{ROOT_NAME} 即可浏览测试数据")
        return rc
    else:
        print("未指定 --push，仅生成到本地。可执行：")
        print(f'  adb push "{PAYLOAD}" /sdcard/')
    return 0


if __name__ == "__main__":
    sys.exit(main())
