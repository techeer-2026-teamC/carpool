#!/usr/bin/env python3
"""README 히어로용 실시간 운행 데모 GIF 생성 (k6 07_ride_demo_scenario 흐름 재현)."""
import math, os
from PIL import Image, ImageDraw, ImageFont

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "demo-ride.gif")
S = 2                      # supersampling
W, H = 900, 520
CW, CH = W * S, H * S

KR   = "/mnt/c/Windows/Fonts/malgun.ttf"
KRB  = "/mnt/c/Windows/Fonts/malgunbd.ttf"
MONO = "/mnt/c/Windows/Fonts/consola.ttf"

def f(path, size): return ImageFont.truetype(path, size * S)
F_TITLE = f(KRB, 15); F_SUB = f(KR, 11); F_BODY = f(KR, 12); F_BODYB = f(KRB, 12)
F_SM = f(KR, 10); F_SMB = f(KRB, 10); F_TINY = f(KR, 9); F_MONO = f(MONO, 9)
F_PHASE = f(KRB, 13); F_URL = f(KR, 10); F_BIG = f(KRB, 20)

# 브랜드 팔레트 (Front/src/index.css)
BG      = (246, 245, 243)
SURFACE = (255, 255, 255)
BORDER  = (228, 226, 221)
ACCENT  = (79, 70, 229)
ACC_LT  = (99, 102, 241)
ACC_PALE= (238, 240, 252)
TEXT    = (26, 34, 51)
MUTED   = (91, 100, 114)
DIM     = (154, 161, 172)
GREEN   = (47, 122, 79)
GREEN_P = (234, 243, 238)
AMBER   = (184, 134, 11)
AMBER_P = (254, 246, 228)
RED     = (179, 73, 47)
MAPBG   = (233, 234, 231)
ROAD    = (252, 252, 251)
BLOCK   = (223, 225, 221)
PARK    = (214, 228, 216)
WATER   = (206, 219, 231)
ROUTE_PALE = (203, 206, 240)

def R(*a): return tuple(int(v * S) for v in a)

# ---------- 경로 ----------
CTRL = [(0.09,0.80),(0.19,0.64),(0.30,0.66),(0.40,0.47),(0.55,0.42),
        (0.66,0.30),(0.78,0.31),(0.88,0.18)]
def catmull(pts, n=340):
    p = [pts[0]] + list(pts) + [pts[-1]]
    out = []
    for i in range(len(p) - 3):
        p0, p1, p2, p3 = p[i], p[i+1], p[i+2], p[i+3]
        for j in range(n // (len(p) - 3)):
            t = j / (n // (len(p) - 3)); t2, t3 = t*t, t*t*t
            x = .5*((2*p1[0])+(-p0[0]+p2[0])*t+(2*p0[0]-5*p1[0]+4*p2[0]-p3[0])*t2+(-p0[0]+3*p1[0]-3*p2[0]+p3[0])*t3)
            y = .5*((2*p1[1])+(-p0[1]+p2[1])*t+(2*p0[1]-5*p1[1]+4*p2[1]-p3[1])*t2+(-p0[1]+3*p1[1]-3*p2[1]+p3[1])*t3)
            out.append((x, y))
    out.append(pts[-1]); return out
ROUTE = catmull(CTRL)
_cum = [0.0]
for i in range(1, len(ROUTE)):
    _cum.append(_cum[-1] + math.dist(ROUTE[i-1], ROUTE[i]))
_TOT = _cum[-1]
def at(t):
    """0~1 진행도 -> (x,y) 정규화 좌표"""
    t = max(0.0, min(1.0, t)); d = t * _TOT
    lo, hi = 0, len(_cum) - 1
    while lo < hi:
        m = (lo + hi) // 2
        if _cum[m] < d: lo = m + 1
        else: hi = m
    i = max(1, lo); seg = _cum[i] - _cum[i-1]
    r = 0 if seg == 0 else (d - _cum[i-1]) / seg
    a, b = ROUTE[i-1], ROUTE[i]
    return (a[0] + (b[0]-a[0])*r, a[1] + (b[1]-a[1])*r)

# ---------- 레이아웃 ----------
PAD = 14
CARD = (PAD, PAD, W - PAD, H - PAD)
CHROME_H = 34
PANEL_W = 268
MAP = (CARD[0]+1, CARD[1]+CHROME_H, CARD[2]-PANEL_W, CARD[3]-1)
def mx(nx): return MAP[0] + nx * (MAP[2]-MAP[0])
def my(ny): return MAP[1] + ny * (MAP[3]-MAP[1])

PICKUP  = 0.0
DROPOFF = 1.0
PAX = [
    {"name": "김서연", "start": (0.02, 0.93), "hue": (219, 119, 74)},
    {"name": "이도현", "start": (0.20, 0.94), "hue": (74, 139, 190)},
    {"name": "박민준", "start": (0.03, 0.60), "hue": (140, 110, 190)},
]

# ---------- 프레임 구성 ----------
FPS = 12
P1, P2, P3, P4, P5 = 14, 20, 21, 36, 22          # 총 113 프레임 ≈ 9.4초
BOUND = [P1, P1+P2, P1+P2+P3, P1+P2+P3+P4, P1+P2+P3+P4+P5]
TOTAL = BOUND[-1]
PHASES = [("PHASE 1", "위치 공유 시작"), ("PHASE 2", "출발점 집결"),
          ("PHASE 3", "운행 시작 · 탑승"), ("PHASE 4", "목적지 이동"),
          ("PHASE 5", "하차 · 운행 종료")]
def phase_of(i):
    for k, b in enumerate(BOUND):
        if i < b: return k, (i - (BOUND[k-1] if k else 0)) / max(1, (b - (BOUND[k-1] if k else 0)))
    return 4, 1.0
def ease(t): return t*t*(3-2*t)

# ---------- 정적 배경 ----------
def draw_map_base(d):
    d.rounded_rectangle(R(*MAP), radius=0, fill=MAPBG)
    # 블록
    blocks = [(.04,.05,.20,.16),(.26,.03,.19,.13),(.50,.06,.16,.14),(.72,.02,.22,.11),
              (.03,.26,.14,.15),(.22,.22,.18,.13),(.46,.22,.14,.12),(.66,.44,.20,.14),
              (.06,.48,.15,.14),(.28,.44,.13,.12),(.44,.60,.18,.16),(.70,.62,.22,.18),
              (.02,.70,.16,.15),(.22,.72,.16,.14),(.86,.30,.11,.16)]
    for k,(x,y,w,h) in enumerate(blocks):
        col = PARK if k in (3, 9) else BLOCK
        d.rounded_rectangle(R(mx(x),my(y),mx(x+w),my(y+h)), radius=int(3*S), fill=col)
    d.rounded_rectangle(R(mx(.80),my(.74),mx(.99),my(.99)), radius=int(4*S), fill=WATER)
    # 도로
    for y in (.20, .42, .58, .70, .88):
        d.line(R(MAP[0], my(y), MAP[2], my(y)), fill=ROAD, width=int(7*S))
    for x in (.18, .42, .62, .82):
        d.line(R(mx(x), MAP[1], mx(x), MAP[3]), fill=ROAD, width=int(7*S))

def draw_route(d):
    pts = [(mx(x), my(y)) for x, y in ROUTE]
    flat = R(*[v for p in pts for v in p])
    d.line(flat, fill=(255,255,255), width=int(9*S), joint="curve")
    d.line(flat, fill=ROUTE_PALE, width=int(5*S), joint="curve")

def pin(d, x, y, color, label=None, big=True):
    r = (8 if big else 6)
    px, py = mx(x), my(y)
    d.polygon(R(px-r*0.62, py-r*0.5, px+r*0.62, py-r*0.5, px, py+r*0.95), fill=color)
    d.ellipse(R(px-r, py-r*1.55, px+r, py+r*0.45), fill=color)
    d.ellipse(R(px-r*0.38, py-r*0.93, px+r*0.38, py-r*0.17), fill=(255,255,255))
    if label:
        w = d.textlength(label, font=F_SMB)
        bx0, by0 = px*S - w/2 - 6*S, py*S - r*1.55*S - 20*S
        d.rounded_rectangle((bx0, by0, bx0+w+12*S, by0+15*S), radius=int(7*S), fill=(255,255,255))
        d.text((bx0+6*S, by0+2.5*S), label, font=F_SMB, fill=TEXT)

def car(d, x, y, ang, n_on):
    px, py = mx(x)*S, my(y)*S
    r = 15*S
    d.ellipse((px-r-4*S, py-r-4*S, px+r+4*S, py+r+4*S), fill=(255,255,255))
    d.ellipse((px-r, py-r, px+r, py+r), fill=ACCENT)
    # 차 글리프
    ca, sa = math.cos(ang), math.sin(ang)
    def rp(dx, dy): return (px + dx*ca - dy*sa, py + dx*sa + dy*ca)
    body = [rp(-8.2*S,-3.0*S), rp(-8.2*S,2.2*S), rp(8.2*S,2.2*S), rp(8.2*S,-1.0*S),
            rp(3.6*S,-1.0*S), rp(1.0*S,-5.0*S), rp(-5.2*S,-5.0*S)]
    d.polygon(body, fill=(255,255,255))
    d.polygon([rp(-4.4*S,-4.0*S), rp(0.6*S,-4.0*S), rp(2.6*S,-1.4*S), rp(-4.4*S,-1.4*S)], fill=ACCENT)
    for wx in (-4.6*S, 4.6*S):
        wcx, wcy = rp(wx, 2.8*S)
        d.ellipse((wcx-2.0*S, wcy-2.0*S, wcx+2.0*S, wcy+2.0*S), fill=(255,255,255))
    if n_on:
        bx, by = px + 13*S, py - 13*S
        d.ellipse((bx-8*S, by-8*S, bx+8*S, by+8*S), fill=GREEN, outline=(255,255,255), width=int(2*S))
        t = str(n_on); w = d.textlength(t, font=F_SMB)
        d.text((bx-w/2, by-6.5*S), t, font=F_SMB, fill=(255,255,255))

def dot(d, x, y, color, r=6, ring=0.0, initial=""):
    px, py = mx(x)*S, my(y)*S
    if ring > 0:
        rr = (r + 4 + 12*ring) * S
        a = int(120 * (1-ring))
        ov = Image.new("RGBA", (int(rr*2)+4, int(rr*2)+4), (0,0,0,0))
        ImageDraw.Draw(ov).ellipse((2,2,rr*2,rr*2), outline=color+(a,), width=int(2*S))
        return ov, (int(px-rr), int(py-rr))
    d.ellipse((px-(r+2.5)*S, py-(r+2.5)*S, px+(r+2.5)*S, py+(r+2.5)*S), fill=(255,255,255))
    d.ellipse((px-r*S, py-r*S, px+r*S, py+r*S), fill=color)
    if initial:
        w = d.textlength(initial, font=F_TINY)
        d.text((px-w/2, py-5.5*S), initial, font=F_TINY, fill=(255,255,255))

def badge(d, x, y, text, fg, bg, font=None):
    font = font or F_SM
    w = d.textlength(text, font=font)
    d.rounded_rectangle((x*S, y*S, x*S+w+13*S, y*S+17*S), radius=int(8.5*S), fill=bg)
    d.text((x*S+6.5*S, y*S+2.5*S), text, font=font, fill=fg)
    return (w/S + 13)

# ---------- 프레임 렌더 ----------
def render(i):
    img = Image.new("RGB", (CW, CH), BG)
    d = ImageDraw.Draw(img)
    ph, t = phase_of(i)
    te = ease(t)

    # 카드 + 브라우저 크롬
    d.rounded_rectangle(R(CARD[0]+1, CARD[1]+2, CARD[2]+1, CARD[3]+2), radius=int(11*S), fill=(236,234,230))
    d.rounded_rectangle(R(*CARD), radius=int(11*S), fill=SURFACE, outline=BORDER, width=int(1*S))
    d.rectangle(R(CARD[0]+1, CARD[1]+CHROME_H-1, CARD[2]-1, CARD[1]+CHROME_H), fill=BORDER)
    for k, c in enumerate([(232,138,128),(233,192,120),(146,196,146)]):
        cx = CARD[0] + 18 + k*13
        d.ellipse(R(cx-4, CARD[1]+CHROME_H/2-4, cx+4, CARD[1]+CHROME_H/2+4), fill=c)
    d.rounded_rectangle(R(CARD[0]+68, CARD[1]+9, CARD[0]+330, CARD[1]+26), radius=int(8*S), fill=BG)
    d.text(R(CARD[0]+80, CARD[1]+12), "carpool.duckdns.org/rides", font=F_URL, fill=MUTED)
    lw = d.textlength("● LIVE", font=F_SMB)
    d.text((CARD[2]*S-PANEL_W*S+16*S, (CARD[1]+11)*S), "● LIVE", font=F_SMB, fill=RED)

    # 지도
    mimg = Image.new("RGB", (int((MAP[2]-MAP[0])*S)+2, int((MAP[3]-MAP[1])*S)+2), MAPBG)
    md = ImageDraw.Draw(img)
    draw_map_base(d)
    draw_route(d)

    # 진행도
    if ph <= 2: prog = 0.0
    elif ph == 3: prog = te * 0.955
    else: prog = 0.955
    cx, cy = at(prog)
    ang = 0.0
    a2 = at(min(1.0, prog + 0.01)); a1 = at(max(0.0, prog - 0.01))
    ang = math.atan2((a2[1]-a1[1]) * (MAP[3]-MAP[1]), (a2[0]-a1[0]) * (MAP[2]-MAP[0]))

    # 지나온 경로 (진한색)
    if prog > 0.004:
        n = max(2, int(prog * len(ROUTE)))
        pts = [(mx(x), my(y)) for x, y in ROUTE[:n]] + [(mx(cx), my(cy))]
        d.line(R(*[v for p in pts for v in p]), fill=ACCENT, width=int(5*S), joint="curve")

    # 위치 브레드크럼 (스트리밍 표현)
    if ph >= 3:
        for k in range(1, 9):
            bt = prog - k*0.022
            if bt <= 0: break
            bx, by = at(bt)
            a = 1 - k/9
            rr = (3.4*a + 1.2) * S
            col = tuple(int(ACCENT[j]*a + ROUTE_PALE[j]*(1-a)) for j in range(3))
            d.ellipse((mx(bx)*S-rr, my(by)*S-rr, mx(bx)*S+rr, my(by)*S+rr), fill=col)

    pin(d, *at(0.0), GREEN, "출발 · 강남역")
    pin(d, *at(1.0), RED, "도착 · 판교역")

    # 승객 상태
    boarded = [False]*3; dropped = [False]*3
    if ph == 2:
        for k in range(3): boarded[k] = t > (0.20 + k*0.24)
    elif ph == 3:
        boarded = [True]*3
    elif ph == 4:
        boarded = [True]*3
        for k in range(3): dropped[k] = t > (0.14 + k*0.20)

    overlays = []
    for k, p in enumerate(PAX):
        if ph == 0:
            px, py = p["start"]
            if t > k*0.22:
                overlays.append(dot(d, px, py, p["hue"], ring=((t - k*0.22)*2.2) % 1.0))
            dot(d, px, py, p["hue"], initial=p["name"][0])
        elif ph == 1:
            sx, sy = p["start"]; ex, ey = at(0.0)
            u = ease(max(0.0, min(1.0, (t - k*0.06) / 0.75)))
            dot(d, sx+(ex-sx)*u, sy+(ey-sy)*u, p["hue"], initial=p["name"][0])
        elif ph == 2:
            if not boarded[k]:
                ex, ey = at(0.0)
                off = [(-0.028,0.018),(0.028,0.020),(0.0,0.036)][k]
                dot(d, ex+off[0], ey+off[1], p["hue"], initial=p["name"][0])
        elif ph == 4 and dropped[k]:
            ex, ey = at(1.0)
            off = [(-0.052,0.050),(0.026,0.062),(-0.012,0.086)][k]
            dot(d, ex+off[0], ey+off[1], p["hue"], initial=p["name"][0])

    n_on = sum(1 for k in range(3) if boarded[k] and not dropped[k])
    if ph >= 2:
        car(d, cx, cy, ang, n_on)
    for ov, pos in overlays:
        img.paste(ov, pos, ov)

    # PHASE 칩
    tag, label = PHASES[ph]
    txt = f"{tag}  ·  {label}"
    tw = d.textlength(txt, font=F_PHASE)
    bx, by = MAP[0]+14, MAP[3]-38
    d.rounded_rectangle(R(bx, by, bx+tw/S+26, by+26), radius=int(13*S), fill=TEXT)
    d.text(R(bx+13, by+5), txt, font=F_PHASE, fill=(255,255,255))
    # 진행 점
    for k in range(5):
        dx = MAP[2]-16-(4-k)*13
        on = k <= ph
        d.ellipse(R(dx-3.5, MAP[3]-26, dx+3.5, MAP[3]-19),
                  fill=(ACCENT if on else (255,255,255)), outline=(ACCENT if on else DIM), width=int(1.2*S))

    # ---------- 사이드 패널 ----------
    PX = MAP[2] + 1
    d.rectangle(R(PX, MAP[1], CARD[2]-1, CARD[3]-1), fill=SURFACE)
    d.line(R(PX, MAP[1], PX, CARD[3]-1), fill=BORDER, width=int(1*S))
    x = PX + 18; y = MAP[1] + 16

    d.text(R(x, y), "실시간 운행", font=F_TITLE, fill=TEXT)
    y += 21
    d.text(R(x, y), "강남역 → 판교역 · 08:20 출발", font=F_SM, fill=MUTED)
    y += 22
    d.line(R(x, y, CARD[2]-18, y), fill=BORDER, width=int(1*S)); y += 14

    # 드라이버
    d.ellipse(R(x, y, x+30, y+30), fill=ACC_PALE)
    d.text(R(x+9, y+7), "정", font=F_BODYB, fill=ACCENT)
    d.text(R(x+40, y+1), "정우진", font=F_BODYB, fill=TEXT)
    badge(d, x+40, y+16, "드라이버", ACCENT, ACC_PALE, F_TINY)
    d.text(R(x+96, y+17), "아반떼 · 흰색 · ★ 4.8", font=F_TINY, fill=MUTED)
    y += 42
    d.line(R(x, y, CARD[2]-18, y), fill=BORDER, width=int(1*S)); y += 12

    d.text(R(x, y), f"탑승자 {sum(1 for b in boarded if b)}/3", font=F_SMB, fill=MUTED)
    y += 18
    for k, p in enumerate(PAX):
        d.ellipse(R(x, y, x+24, y+24), fill=p["hue"])
        w = d.textlength(p["name"][0], font=F_SM)
        d.text((x*S+12*S-w/2, y*S+5*S), p["name"][0], font=F_SM, fill=(255,255,255))
        d.text(R(x+33, y+4), p["name"], font=F_BODY, fill=TEXT)
        if dropped[k]:   st, fg, bg = "하차 완료", MUTED, BG
        elif boarded[k]: st, fg, bg = "탑승 중", GREEN, GREEN_P
        elif ph >= 1:    st, fg, bg = "집결 중", AMBER, AMBER_P
        else:            st, fg, bg = "대기", MUTED, BG
        w2 = d.textlength(st, font=F_TINY)
        badge(d, (CARD[2]-18) - (w2/S + 13), y+4, st, fg, bg, F_TINY)
        y += 30

    y += 4
    d.line(R(x, y, CARD[2]-18, y), fill=BORDER, width=int(1*S)); y += 12
    d.text(R(x, y), "STOMP  /topic/ride/12", font=F_SMB, fill=MUTED); y += 17

    sent = 3 + i * 4
    lat = 37.4979 + (at(prog)[1] * -0.0004) + 0.0331 * (1 - prog)
    lng = 127.0276 + prog * 0.0827
    lines = [
        (f'{{"lat":{lat:0.5f},"lng":{lng:0.5f}}}', TEXT),
        (f'{{"lat":{lat-0.0006:0.5f},"lng":{lng-0.0011:0.5f}}}', MUTED),
        (f'{{"lat":{lat-0.0013:0.5f},"lng":{lng-0.0022:0.5f}}}', DIM),
    ]
    d.rounded_rectangle(R(x, y, CARD[2]-18, y+56), radius=int(7*S), fill=BG)
    for k, (ln, col) in enumerate(lines):
        d.text(R(x+9, y+8 + k*15), ln, font=F_MONO, fill=col)
    y += 64
    d.text(R(x, y), f"수신 {sent:,} msg", font=F_SM, fill=MUTED)
    d.text(R(x+96, y), "DB 접근 0회", font=F_SMB, fill=GREEN)

    if ph == 4 and t > 0.55:
        by0 = CARD[3] - 46
        d.rounded_rectangle(R(x, by0, CARD[2]-18, by0+30), radius=int(8*S), fill=ACCENT)
        tw2 = d.textlength("드라이버 평가하기", font=F_BODYB)
        d.text(((x+ (CARD[2]-18-x)/2)*S - tw2/2, (by0+7)*S), "드라이버 평가하기", font=F_BODYB, fill=(255,255,255))

    return img.resize((W, H), Image.LANCZOS)

frames = [render(i) for i in range(TOTAL)]

# 전 구간 대표 프레임 + 브랜드 색 스와치로 공용 팔레트 구성 (색 뭉개짐 방지)
keys = [frames[k] for k in (2, 20, 40, 60, 80, 100, 110)]
mont = Image.new("RGB", (W, H * (len(keys) + 1)), BG)
for k, fr in enumerate(keys):
    mont.paste(fr, (0, k * H))
sw = ImageDraw.Draw(mont)
brand = [ACCENT, ACC_LT, ACC_PALE, TEXT, MUTED, DIM, BORDER, BG, SURFACE,
         GREEN, GREEN_P, AMBER, AMBER_P, RED, MAPBG, ROAD, BLOCK, PARK, WATER,
         ROUTE_PALE] + [p["hue"] for p in PAX]
for k, c in enumerate(brand):
    sw.rectangle((k * 40, len(keys) * H, k * 40 + 40, len(keys) * H + H), fill=c)
base = mont.quantize(colors=110, method=Image.Quantize.MEDIANCUT)
pal = [fr.quantize(colors=110, palette=base, dither=Image.Dither.NONE) for fr in frames]
os.makedirs(os.path.dirname(OUT), exist_ok=True)
pal[0].save(OUT, save_all=True, append_images=pal[1:], duration=int(1000/FPS),
            loop=0, optimize=True, disposal=1)
print("saved", OUT, os.path.getsize(OUT)//1024, "KB", TOTAL, "frames")
