#!/bin/bash
# ==============================================================================
# RED Sovereign - Service Health Check Script
# فحص صحة جميع خدمات النظام الموزعة
# ==============================================================================

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

HEALTHY=0
TOTAL=5

echo -e "${YELLOW}=== بدء فحص صحة خدمات RED Sovereign ===${NC}"

# 1. فحص خادم الباك إند
echo -n "1. فحص Backend Server (Spring Boot)... "
if curl -s -f http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo -e "${GREEN}[OK]${NC}"
    ((HEALTHY++))
else
    echo -e "${RED}[FAIL]${NC}"
fi

# 2. فحص قاعدة بيانات PostgreSQL
echo -n "2. فحص PostgreSQL... "
if pg_isready -h localhost -p 5432 -U red_user > /dev/null 2>&1 || nc -z localhost 5432 2>/dev/null; then
    echo -e "${GREEN}[OK]${NC}"
    ((HEALTHY++))
else
    echo -e "${RED}[FAIL]${NC}"
fi

# 3. فحص Redis Cache
echo -n "3. فحص Redis Cache... "
if redis-cli ping 2>/dev/null | grep -q "PONG" || nc -z localhost 6379 2>/dev/null; then
    echo -e "${GREEN}[OK]${NC}"
    ((HEALTHY++))
else
    echo -e "${RED}[FAIL]${NC}"
fi

# 4. فحص تخزين MinIO / S3
echo -n "4. فحص MinIO Storage... "
if curl -s -f http://localhost:9000/minio/health/live > /dev/null 2>&1; then
    echo -e "${GREEN}[OK]${NC}"
    ((HEALTHY++))
else
    echo -e "${RED}[FAIL]${NC}"
fi

# 5. فحص خادم الوسائط والمكالمات media-sfu
echo -n "5. فحص Media SFU (WebRTC)... "
if curl -s -f http://localhost:3000/health > /dev/null 2>&1; then
    echo -e "${GREEN}[OK]${NC}"
    ((HEALTHY++))
else
    echo -e "${RED}[FAIL]${NC}"
fi

echo -e "==========================================="
if [ $HEALTHY -eq $TOTAL ]; then
    echo -e "${GREEN}النتيجة: جميع الخدمات ($HEALTHY/$TOTAL) تعمل بكفاءة!${NC}"
    exit 0
else
    echo -e "${YELLOW}النتيجة: $HEALTHY من أصل $TOTAL خدمات تعمل.${NC}"
    exit 1
fi