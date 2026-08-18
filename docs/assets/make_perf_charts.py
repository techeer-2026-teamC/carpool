#!/usr/bin/env python3
"""README용 실시간 위치 성능 차트 — blog_websocket_location.md 실측값 기반."""
import os
import matplotlib; matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib import font_manager as fm

FONT = "/mnt/c/Windows/Fonts/malgun.ttf"
fm.fontManager.addfont(FONT)
plt.rcParams["font.family"] = fm.FontProperties(fname=FONT).get_name()
plt.rcParams["axes.unicode_minus"] = False
plt.rcParams["axes.edgecolor"] = "#e4e2dd"
plt.rcParams["text.color"] = "#1a2233"
plt.rcParams["axes.labelcolor"] = "#5b6472"
plt.rcParams["xtick.color"] = "#5b6472"
plt.rcParams["ytick.color"] = "#5b6472"

OUT = os.path.dirname(os.path.abspath(__file__))
os.makedirs(OUT, exist_ok=True)
BG = "#ffffff"
RED, AMBER, GREEN, ACCENT, DIM = "#c0503a", "#c98a1e", "#2f7a4f", "#4f46e5", "#9aa1ac"

fig, axes = plt.subplots(1, 3, figsize=(16.5, 4.6), facecolor=BG,
                         gridspec_kw={"width_ratios": [1, 1.15, 0.8]})
for ax in axes:
    ax.set_facecolor(BG)
    for sp in ("top", "right"): ax.spines[sp].set_visible(False)
    ax.grid(axis="y", color="#efeeeb", lw=1, zorder=0)
    ax.set_axisbelow(True)

# ── (1) 단계별 처리량
ax = axes[0]
labels = ["① DB INSERT\n+ DB 인가", "② Write-Behind\n+ DB 인가", "③ Write-Behind\n+ Redis 인가"]
vals = [4792, 5226, 11772]
bars = ax.bar(labels, vals, color=[RED, AMBER, GREEN], width=0.6, zorder=3)
for b, v in zip(bars, vals):
    ax.text(b.get_x()+b.get_width()/2, v+280, f"{v:,}", ha="center", fontsize=11.5, fontweight="bold")
ax.set_ylim(0, 14200)
ax.set_ylabel("처리량 (msg/s)")
ax.set_title("① 단계별 처리량 — hot path에서 DB 들어내기", fontsize=12.5, fontweight="bold", pad=12)
ax.annotate("", xy=(2, 12600), xytext=(0, 12600),
            arrowprops=dict(arrowstyle="->", color=ACCENT, lw=2))
ax.text(1, 12900, "약 2.4배 (+146%)", ha="center", fontsize=11, fontweight="bold", color=ACCENT)
ax.text(0.5, -0.30, "메시지당 DB 접근  2회 → 0회   ·   HikariCP pending 370 → 0   ·   커넥션 획득대기 129ms → 0.009ms",
        transform=ax.transAxes, ha="center", fontsize=9, color="#5b6472")

# ── (2) saturation curve
ax = axes[1]
offered = [2000, 3000, 4000, 5000, 7000, 10000, 12500, 16666]
s1 = [1964, 2944, 3131, 1894, 2711, 2948, 1727, 2850]
s2 = [1857, 2941, 3770, 4484, 4210, 4103, 4148, 2935]
s3 = [1961, 2934, 3751, 4685, 4747, 7858, 5509, 6767]
for y, c, lb in ((s1, RED, "① DB INSERT"), (s2, AMBER, "② Write-Behind"), (s3, GREEN, "③ + Redis 인가")):
    ax.plot(offered, y, "-o", color=c, lw=2.4, ms=5.5, label=lb, zorder=3)
ax.set_xlabel("요청 유입량 offered (msg/s)")
ax.set_ylabel("서버 처리량 (msg/s)")
ax.set_title("② 지속 처리량 천장 — saturation curve", fontsize=12.5, fontweight="bold", pad=12)
ax.legend(frameon=False, loc="upper left", fontsize=10)
ax.set_ylim(0, 9200)
ax.axhline(7858, color=GREEN, ls=":", lw=1.4)
ax.text(16800, 7858, " ~7.9k", va="center", fontsize=9.5, color=GREEN, fontweight="bold")
ax.axhline(4484, color=AMBER, ls=":", lw=1.4)
ax.text(16800, 4484, " ~4.5k", va="center", fontsize=9.5, color=AMBER, fontweight="bold")
ax.axhline(3131, color=RED, ls=":", lw=1.4)
ax.text(16800, 3050, " ~3.1k", va="center", fontsize=9.5, color=RED, fontweight="bold")
ax.text(0.5, -0.30, "③만 유입량이 올라도 처리량이 따라 오름  ·  ①·②는 커넥션 풀(30) 포화로 천장에 막힘",
        transform=ax.transAxes, ha="center", fontsize=9, color="#5b6472")

# ── (3) 1000명 동시 연결
ax = axes[2]
b = ax.bar(["튜닝 전", "튜닝 후"], [442, 79], color=[RED, GREEN], width=0.5, zorder=3)
for bb, v in zip(b, [442, 79]):
    ax.text(bb.get_x()+bb.get_width()/2, v+12, f"{v}건", ha="center", fontsize=11.5, fontweight="bold")
ax.set_ylim(0, 540)
ax.set_ylabel("중단된 이터레이션 (건)")
ax.set_title("③ 1,000명 동시 연결 안정성", fontsize=12.5, fontweight="bold", pad=12)
ax.text(0.5, 0.62, "-82%", transform=ax.transAxes, ha="center", fontsize=17,
        fontweight="bold", color=ACCENT)
ax.text(0.5, -0.30, "원인: STOMP ClientInboundChannel corePoolSize=1 (단일 스레드)\n→ 스레드 풀 + Tomcat threads 튜닝",
        transform=ax.transAxes, ha="center", fontsize=9, color="#5b6472")

fig.text(0.5, 0.005, "측정 환경: Docker small (2 vCPU / 2GB) · k6 06_ride_location_load.js / 06b_sat.js · HikariCP 30",
         ha="center", fontsize=8.5, color="#9aa1ac")
fig.tight_layout(rect=[0, 0.075, 1, 1])
p = os.path.join(OUT, "perf-websocket.png")
fig.savefig(p, dpi=140, facecolor=BG); plt.close(fig)
print("saved", p, os.path.getsize(p)//1024, "KB")
