from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "store" / "assets" / "application-copilot-promo-440x280.png"
ICON = ROOT / "extension" / "icons" / "yuqi-128.png"
FONT = "/System/Library/Fonts/SFNS.ttf"


def font(size):
    return ImageFont.truetype(FONT, size=size)


canvas = Image.new("RGB", (440, 280), "#0F766E")
draw = ImageDraw.Draw(canvas)

draw.rectangle((0, 216, 440, 280), fill="#142C3A")
draw.rectangle((0, 0, 10, 216), fill="#F97316")
draw.rounded_rectangle((34, 42, 150, 158), radius=18, fill="#F7FAFA")

icon = Image.open(ICON).convert("RGBA")
icon.thumbnail((88, 88), Image.Resampling.LANCZOS)
canvas.paste(icon, (48, 56), icon)

draw.text((178, 51), "YUQI.SITE", font=font(18), fill="#BDF4E4")
draw.text((178, 82), "APPLICATION", font=font(27), fill="#FFFFFF")
draw.text((178, 113), "COPILOT", font=font(27), fill="#FFFFFF")

draw.rounded_rectangle((34, 181, 406, 220), radius=19, fill="#FFFFFF")
draw.text((68, 190), "REVIEW  ·  FILL  ·  YOU SUBMIT", font=font(16), fill="#173B4A")
draw.text((34, 239), "PRIVATE PROFILE + RESUME", font=font(14), fill="#BDF4E4")

OUTPUT.parent.mkdir(parents=True, exist_ok=True)
canvas.save(OUTPUT, format="PNG", optimize=True)
print(OUTPUT)
