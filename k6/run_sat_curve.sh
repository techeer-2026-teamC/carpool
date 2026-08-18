#!/usr/bin/env bash
# Saturation curve: offered rate를 올려가며(2k→~16k) 단계별 처리량 천장(knee)을 찾는다.
# SCALE=100 고정(연결 수 고정) + SEND_INTERVAL_MS만 줄여 offered rate만 변화시킴.
# offered ≈ SCALE * 1000 / INTERVAL. 측정: carpool_location_updates_total 델타 = server_tps.
# 대상 3단계: exp/redis-s1(DB INSERT) · exp/redis-s2(Write-Behind+DB인가) · develop(Redis인가)
set -u
ROOT=/home/pjj1122/Projects/carpool/Backend; cd "$ROOT"
PROM=http://localhost:8080/actuator/prometheus
CF="docker-compose.yml:docker-compose.small.yml"
SCALE=100
FLOOD="${FLOOD:-40}"
INTERVALS=(50 33 25 20 14 10 8 6)   # offered ≈ 2k 3k 4k 5k 7.1k 10k 12.5k 16.7k
RES="$ROOT/k6/results/curve"; mkdir -p "$RES"
CSV="$RES/curve.csv"; echo "branch,interval_ms,offered_msgs,server_tps,pending_peak,active_peak,connect_p95_ms" > "$CSV"

ctr(){ curl -s "$PROM" 2>/dev/null | awk '/^carpool_location_updates_total\{/{print $2}'; }
hk(){ curl -s "$PROM" 2>/dev/null | awk '
  /^hikaricp_connections_active\{/{a=$2}
  /^hikaricp_connections_pending\{/{p=$2}
  END{printf "%s %s", a+0,p+0}'; }

run_point(){ # branch label interval
  local br=$1 label=$2 iv=$3 out="$RES/$label/iv${iv}"; mkdir -p "$out"
  local offered=$(( SCALE * 1000 / iv ))
  docker exec carpool-redis redis-cli FLUSHALL >/dev/null 2>&1
  docker exec carpool-db psql -U user -d carpool -c "TRUNCATE ride_locations" >/dev/null 2>&1
  sleep 2
  # HikariCP peak 샘플러
  ( while true; do echo "$(hk)"; sleep 1; done ) >"$out/hk.log" 2>/dev/null & local hp=$!
  k6 run -e SCALE=$SCALE -e SEND_INTERVAL_MS=$iv -e FLOOD_SEC=$FLOOD k6/scenarios/06b_sat.js >"$out/k6.log" 2>&1 &
  local k6pid=$!
  sleep 8
  local msec=$(( FLOOD > 30 ? 30 : FLOOD/2 ))
  # 처리량이 실제로 오르기 시작할 때까지 대기
  for w in $(seq 1 30); do a=$(ctr); sleep 1; b=$(ctr); awk "BEGIN{exit !( (${b:-0})-(${a:-0}) > 50 )}" && break; done
  local t0=$(ctr) ts0=$(date +%s)
  sleep $msec
  local t1=$(ctr) ts1=$(date +%s)
  wait "$k6pid" 2>/dev/null; kill "$hp" 2>/dev/null
  local el=$(( ts1 - ts0 ))
  local tps=$(awk "BEGIN{printf \"%.0f\", (${t1:-0}-${t0:-0})/$el}")
  local apeak=$(awk '{if($1>a)a=$1}END{print a+0}' "$out/hk.log")
  local ppeak=$(awk '{if($2>p)p=$2}END{print p+0}' "$out/hk.log")
  local p95=$(grep ws_connect_duration "$out/k6.log" | grep -oE 'p\(95\)=[0-9.]+' | tail -1 | cut -d= -f2)
  echo "$br,$iv,$offered,$tps,$ppeak,$apeak,${p95:-}" | tee -a "$CSV"
}

measure_branch(){ # branch label
  local br=$1 label=$2
  echo "== curve [$label] $br =="
  git checkout "$br" -q || { echo "$label CHECKOUT_FAIL"; return; }
  COMPOSE_FILE="$CF" docker compose build app >"$RES/$label-build.log" 2>&1 || { echo "$label BUILD_FAIL"; return; }
  COMPOSE_FILE="$CF" docker compose up -d --force-recreate app >>"$RES/$label-build.log" 2>&1
  local ok=0; for i in $(seq 1 60); do [ "$(curl -s -o /dev/null -w '%{http_code}' $PROM 2>/dev/null)" = 200 ] && { ok=1; break; }; sleep 2; done
  [ $ok = 1 ] || { echo "$label NOT_READY"; return; }
  sleep 3
  for iv in "${INTERVALS[@]}"; do run_point "$br" "$label" "$iv"; done
}

measure_branch exp/redis-s1 1-db-insert
measure_branch exp/redis-s2 2-write-behind
measure_branch develop      3-auth-cache
git checkout develop -q
echo "CURVE DONE -> $CSV"
column -t -s, "$CSV"
