#!/bin/bash
# WSL2 IP가 재시작마다 바뀌므로 Prometheus 타겟을 자동 업데이트하는 스크립트
# 사용법: ./update-prometheus-ip.sh

WSL_IP=$(ip addr show eth0 2>/dev/null | grep 'inet ' | awk '{print $2}' | cut -d/ -f1)

if [ -z "$WSL_IP" ]; then
    echo "ERROR: WSL2 eth0 IP를 찾을 수 없습니다."
    exit 1
fi

sed -i "s/targets: \[.*:8080\]/targets: ['${WSL_IP}:8080']/" prometheus.yml
echo "prometheus.yml 업데이트 완료: ${WSL_IP}:8080"

# Prometheus 컨테이너 재시작 (설정 반영)
docker restart carpool-prometheus 2>/dev/null && echo "Prometheus 재시작 완료" || echo "Prometheus가 실행 중이지 않습니다."
