#!/usr/bin/env bash
# Redis 3단계 도입기 측정 (small 2cpu/2g). DB INSERT → Write-Behind → 인가 Redis 캐시.
# 부하: 06b_sat.js (SCALE=100, interval=10ms ≈ offered 10k/s) — DB 병목을 드러내는 적당한 포화.
set -u
ROOT=/home/pjj1122/Projects/carpool/Backend; cd "$ROOT"
PROM=http://localhost:8080/actuator/prometheus
CF="docker-compose.yml:docker-compose.small.yml"
RES="$ROOT/k6/results/journey"; mkdir -p "$RES"; SUM="$RES/summary.txt"; : > "$SUM"
ctr(){ curl -s "$PROM" 2>/dev/null | awk '/^carpool_location_updates_total\{/{print $2}'; }
# HikariCP 스냅: active pending acquire_sum acquire_count timeout (병목 증명용)
hk(){ curl -s "$PROM" 2>/dev/null | awk '
  /^hikaricp_connections_active\{/{a=$2}
  /^hikaricp_connections_pending\{/{p=$2}
  /^hikaricp_connections_acquire_seconds_sum/{s=$2}
  /^hikaricp_connections_acquire_seconds_count/{c=$2}
  /^hikaricp_connections_timeout_total/{t=$2}
  END{printf "%s %s %s %s %s", a+0,p+0,s+0,c+0,t+0}'; }

measure(){ # branch label
  local br=$1 label=$2 out="$RES/$2"; mkdir -p "$out"
  echo "== [$label] $br ==" | tee -a "$SUM"
  git checkout "$br" -q
  COMPOSE_FILE="$CF" docker compose build app >"$out/build.log" 2>&1 || { echo "$label BUILD_FAIL"|tee -a "$SUM"; return; }
  COMPOSE_FILE="$CF" docker compose up -d --force-recreate app >>"$out/build.log" 2>&1
  local ok=0; for i in $(seq 1 60); do [ "$(curl -s -o /dev/null -w '%{http_code}' $PROM 2>/dev/null)" = 200 ] && { ok=1; break; }; sleep 2; done
  [ $ok = 1 ] || { echo "$label NOT_READY"|tee -a "$SUM"; return; }
  docker exec carpool-redis redis-cli FLUSHALL >/dev/null 2>&1
  docker exec carpool-db psql -U user -d carpool -c "TRUNCATE ride_locations" >/dev/null 2>&1
  sleep 3
  local idle=$(docker stats --no-stream --format '{{.MemUsage}}' carpool-app | grep -oE '^[0-9.]+')
  ( while true; do
      m=$(docker stats --no-stream --format '{{.MemUsage}}' carpool-app 2>/dev/null | grep -oE '^[0-9.]+')
      echo "$m|$(ctr)|$(hk)"; sleep 2
    done ) >"$out/series.log" 2>/dev/null & local sp=$!
  k6 run -e SCALE=100 -e SEND_INTERVAL_MS=10 -e FLOOD_SEC=40 k6/scenarios/06b_sat.js >"$out/k6.log" 2>&1
  kill "$sp" 2>/dev/null; sleep 1
  local pm=$(awk -F'|' '$1!=""{print $1}' "$out/series.log" | sort -n | tail -1)
  local tps=$(awk -F'|' '$2!=""{c[++n]=$2} END{best=0;for(i=1;i+5<=n;i++){d=(c[i+5]-c[i])/10;if(d>best)best=d}printf "%.0f",best}' "$out/series.log")
  local p95=$(grep ws_connect_duration "$out/k6.log" | grep -oE 'p\(95\)=[0-9.]+' | tail -1 | cut -d= -f2)
  local rows=$(docker exec carpool-db psql -U user -d carpool -tAc "SELECT count(*) FROM ride_locations" 2>/dev/null)
  # HikariCP 병목 지표: 3번째 필드(공백구분 a p s c t)에서 peak active/pending, acquire 평균대기(Δsum/Δcount), timeout Δ
  local hkstat=$(awk -F'|' '$3!=""{split($3,h," "); a=h[1];p=h[2];s=h[3];c=h[4];t=h[5];
      if(a>amax)amax=a; if(p>pmax)pmax=p;
      if(s0=="" ){s0=s;c0=c;t0=t}; s1=s;c1=c;t1=t}
    END{dc=c1-c0; acq=(dc>0)?(s1-s0)/dc*1000:0;
      printf "active_peak=%d pending_peak=%d acq_avg=%.3fms timeout_delta=%d", amax,pmax,acq,t1-t0}' "$out/series.log")
  echo "$label | server_tps=$tps | $hkstat | connect_p95=${p95}ms | peak_mem=${pm}MiB | idle_mem=${idle}MiB | db_rows=$rows" | tee -a "$SUM"
}

measure exp/redis-s1 1-db-insert
measure exp/redis-s2 2-write-behind
measure develop      3-auth-cache
git checkout develop -q
echo "JOURNEY DONE" | tee -a "$SUM"
