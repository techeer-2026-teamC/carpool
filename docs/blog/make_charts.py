#!/usr/bin/env python3
"""블로그용 차트 생성 — 캐시·인덱스·낙관적 락. 측정값 기반."""
import os
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib import font_manager as fm
import matplotlib.patches as mpatches

# 한글 폰트 (맑은 고딕)
FONT = "/mnt/c/Windows/Fonts/malgun.ttf"
if os.path.exists(FONT):
    fm.fontManager.addfont(FONT)
    plt.rcParams["font.family"] = fm.FontProperties(fname=FONT).get_name()
plt.rcParams["axes.unicode_minus"] = False

OUT = os.path.join(os.path.dirname(__file__), "images")
os.makedirs(OUT, exist_ok=True)

C_MISS = "#e15759"   # red
C_HIT = "#4e79a7"    # blue
C_BEFORE = "#e15759"
C_AFTER = "#59a14f"  # green
C_GRAY = "#bab0ac"


# ---------------------------------------------------------------------------
# 1) 캐시 MISS vs HIT (데이터 양별) — 로그 스케일 그룹 막대
# ---------------------------------------------------------------------------
def chart_cache():
    vols = ["1만 건", "10만 건", "100만 건"]
    miss = [42.234, 46.099, 2009.02]
    hit = [99.96, 82.51, 80.94]
    x = range(len(vols))
    w = 0.36

    fig, ax = plt.subplots(figsize=(8, 5))
    b1 = ax.bar([i - w/2 for i in x], miss, w, label="캐시 MISS (DB 경로)", color=C_MISS)
    b2 = ax.bar([i + w/2 for i in x], hit, w, label="캐시 HIT (Redis)", color=C_HIT)
    ax.set_yscale("log")
    ax.set_ylabel("응답 시간 (ms, 로그 스케일)")
    ax.set_title("게시글 목록 캐시 — 데이터 양별 MISS vs HIT", fontsize=13, fontweight="bold")
    ax.set_xticks(list(x)); ax.set_xticklabels(vols)
    ax.legend(loc="upper left")
    for b in list(b1) + list(b2):
        v = b.get_height()
        ax.text(b.get_x() + b.get_width()/2, v * 1.05,
                f"{v:,.0f}ms" if v >= 100 else f"{v:.0f}ms",
                ha="center", va="bottom", fontsize=9, fontweight="bold")
    ax.annotate("100만 건: 미스 한 건이 2초\n→ 캐시가 ~80ms로 평탄화 (약 25배)",
                xy=(2 - w/2, 2009), xytext=(0.45, 1500),
                fontsize=9.5, color=C_MISS, fontweight="bold",
                arrowprops=dict(arrowstyle="->", color=C_MISS))
    ax.text(0.99, 0.02, "small 2vCPU/2GB · HikariCP30 · k6 50VU/60s · upcoming-posts TTL 5분",
            transform=ax.transAxes, ha="right", va="bottom", fontsize=7.5, color="gray")
    fig.tight_layout()
    p = os.path.join(OUT, "cache_miss_vs_hit.png")
    fig.savefig(p, dpi=150); plt.close(fig)
    print("saved", p)


# ---------------------------------------------------------------------------
# 2) 인덱스 before/after — 두 패널 (메인 쿼리 + 연쇄 회복)
# ---------------------------------------------------------------------------
def chart_index():
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(11, 5))

    # 좌: list_posts p95
    labels = ["10만 건\n인덱스 없음", "5만 건\n인덱스 없음", "1만 건\n복합 인덱스"]
    vals = [4720, 2830, 303]
    colors = [C_BEFORE, C_BEFORE, C_AFTER]
    bars = ax1.bar(labels, vals, color=colors)
    ax1.set_ylabel("list_posts p95 (ms)")
    ax1.set_title("복합 인덱스 적용 — 목록 조회 p95", fontsize=12, fontweight="bold")
    for b in bars:
        v = b.get_height()
        ax1.text(b.get_x()+b.get_width()/2, v+80,
                 f"{v/1000:.2f}s" if v >= 1000 else f"{v:.0f}ms",
                 ha="center", va="bottom", fontsize=10, fontweight="bold")
    ax1.text(0.5, 0.9, "4.72s → 303ms (약 15배)", transform=ax1.transAxes,
             ha="center", fontsize=11, color=C_AFTER, fontweight="bold")

    # 우: 연쇄 회복 (무관 API)
    api = ["list_posts", "tags", "location_poll"]
    before = [4720, 1150, 1150]
    after = [303, 9, 4]
    x = range(len(api)); w = 0.36
    ax2.bar([i-w/2 for i in x], before, w, label="인덱스 전", color=C_BEFORE)
    ax2.bar([i+w/2 for i in x], after, w, label="인덱스 후", color=C_AFTER)
    ax2.set_yscale("log")
    ax2.set_ylabel("p95 (ms, 로그)")
    ax2.set_title("연쇄 회복 — 무관 API까지 함께 빨라짐", fontsize=12, fontweight="bold")
    ax2.set_xticks(list(x)); ax2.set_xticklabels(api)
    ax2.legend()
    ax2.text(0.5, -0.18, "HikariCP는 공유 자원 — 느린 쿼리 하나가 전체 API를 마비시킨다",
             transform=ax2.transAxes, ha="center", fontsize=8.5, color="gray")
    fig.suptitle("인덱스 도입 효과 (k6 02_read_heavy, 100VU)", fontsize=13, fontweight="bold")
    fig.tight_layout(rect=[0, 0.03, 1, 0.96])
    p = os.path.join(OUT, "index_before_after.png")
    fig.savefig(p, dpi=150); plt.close(fig)
    print("saved", p)


# ---------------------------------------------------------------------------
# 3) 낙관적 락 — Lost Update 시퀀스 + SCALE별 결과 (개념)
# ---------------------------------------------------------------------------
def chart_lock():
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(12, 5))

    # 좌: Lost Update 시퀀스 다이어그램
    ax1.set_title("Lost Update — 락이 없으면", fontsize=12, fontweight="bold")
    ax1.set_xlim(0, 10); ax1.set_ylim(0, 10); ax1.axis("off")
    # 두 트랜잭션 레인
    ax1.text(2.5, 9.3, "요청 A", ha="center", fontsize=11, fontweight="bold", color=C_HIT)
    ax1.text(7.5, 9.3, "요청 B", ha="center", fontsize=11, fontweight="bold", color=C_MISS)
    ax1.plot([2.5, 2.5], [0.5, 9], color=C_HIT, lw=1.5)
    ax1.plot([7.5, 7.5], [0.5, 9], color=C_MISS, lw=1.5)
    steps = [
        (8.2, 2.5, "current=2 읽음", C_HIT),
        (6.8, 7.5, "current=2 읽음", C_MISS),
        (5.2, 2.5, "+1 → 3 저장", C_HIT),
        (3.6, 7.5, "+1 → 3 저장", C_MISS),
    ]
    for y, xc, txt, col in steps:
        ax1.annotate(txt, xy=(xc, y), xytext=(xc + (1.6 if xc < 5 else -1.6), y),
                     ha="left" if xc < 5 else "right", va="center", fontsize=9.5, color=col,
                     arrowprops=dict(arrowstyle="-|>", color=col))
        ax1.plot(xc, y, "o", color=col, ms=6)
    ax1.annotate("B가 A의 갱신을 덮어씀\n정원 3인데 4명 탑승!", xy=(7.5, 3.6),
                 xytext=(4.5, 1.4), ha="center", fontsize=10, color="black", fontweight="bold",
                 arrowprops=dict(arrowstyle="->", color="black"))

    # 우: SCALE별 결과 (개념 — @Version + 재시도)
    scales = ["SCALE 10", "SCALE 100", "SCALE 1000"]
    success = [3, 3, 3]
    rejected = [7, 97, 997]
    x = range(len(scales))
    ax2.bar(x, success, color=C_AFTER, label="201 성공 (= 정원 3)")
    ax2.bar(x, rejected, bottom=success, color=C_GRAY, label="409 (정원 마감/충돌)")
    ax2.set_yscale("log")
    ax2.set_ylabel("신청 수 (로그)")
    ax2.set_title("@Version + 재시도 — 동시 신청해도 성공은 항상 3", fontsize=12, fontweight="bold")
    ax2.set_xticks(list(x)); ax2.set_xticklabels(scales)
    ax2.legend(loc="upper left")
    for i, s in enumerate(success):
        ax2.text(i, s, "3", ha="center", va="bottom", fontsize=11, fontweight="bold", color=C_AFTER)
    ax2.text(0.5, -0.18, "지수+지터 백오프(50ms×1.5)로 재시도 — 예상 누적 ~406ms(5회) · 개념/예상치",
             transform=ax2.transAxes, ha="center", fontsize=8, color="gray")
    fig.suptitle("낙관적 락 — 정원 초과 동시 신청 차단", fontsize=13, fontweight="bold")
    fig.tight_layout(rect=[0, 0.03, 1, 0.96])
    p = os.path.join(OUT, "optimistic_lock.png")
    fig.savefig(p, dpi=150); plt.close(fig)
    print("saved", p)


def chart_retry():
    pols = ["none", "immediate", "fixed", "exp", "exp+jitter"]
    success = [9.4, 39.3, 85.0, 91.8, 93.1]      # %
    retr = [0.00, 3.02, 0.88, 0.49, 0.40]        # 요청당 재시도
    med = [81.45, 101.78, 34.41, 34.6, 34.55]
    p95 = [191.16, 162.65, 241.92, 444.38, 533.93]
    mx = [747.57, 303.64, 401.85, 586.38, 744.5]
    colors = [C_GRAY, "#e15759", "#f1a340", "#7fb069", C_HIT]

    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(13, 5))

    # 좌: 성공률(막대) + 재시도/req(꺾은선, 보조축)
    x = range(len(pols))
    bars = ax1.bar(x, success, color=colors)
    ax1.set_ylabel("성공률 (%)")
    ax1.set_ylim(0, 105)
    ax1.set_title("성공률 vs 요청당 재시도", fontsize=12, fontweight="bold")
    ax1.set_xticks(list(x)); ax1.set_xticklabels(pols, rotation=10)
    for b, s in zip(bars, success):
        ax1.text(b.get_x()+b.get_width()/2, s+1.5, f"{s:.1f}%", ha="center", fontsize=9, fontweight="bold")
    ax1b = ax1.twinx()
    ax1b.plot(list(x), retr, "o-", color="black", lw=2, label="재시도/req")
    ax1b.set_ylabel("요청당 재시도 횟수")
    ax1b.set_ylim(0, 3.5)
    for xi, r in zip(x, retr):
        ax1b.text(xi, r+0.12, f"{r:.2f}", ha="center", fontsize=8.5, color="black")
    ax1b.legend(loc="center right")

    # 우: 대기시간 med/p95/max
    w = 0.26
    ax2.bar([i-w for i in x], med, w, label="median", color="#7fb069")
    ax2.bar([i for i in x], p95, w, label="p95", color="#f1a340")
    ax2.bar([i+w for i in x], mx, w, label="max", color="#e15759")
    ax2.set_ylabel("사용자 대기시간 (ms)")
    ax2.set_title("사용자 대기시간 (재시도 포함)", fontsize=12, fontweight="bold")
    ax2.set_xticks(list(x)); ax2.set_xticklabels(pols, rotation=10)
    ax2.legend()
    ax2.text(0.5, -0.22, "jitter: 성공률↑·재시도↓ 대신 꼬리(p95/max)↑ — 경합 총량과 꼬리지연의 트레이드오프",
             transform=ax2.transAxes, ha="center", fontsize=8.5, color="gray")

    fig.suptitle("재시도 정책 스윕 — 단일 row 50VU 30s 극단 경합 (정책 상대비교용)", fontsize=13, fontweight="bold")
    fig.tight_layout(rect=[0, 0.04, 1, 0.95])
    p = os.path.join(OUT, "retry_policy_sweep.png")
    fig.savefig(p, dpi=150); plt.close(fig)
    print("saved", p)


if __name__ == "__main__":
    chart_cache()
    chart_index()
    chart_lock()
    chart_retry()
    print("DONE")
