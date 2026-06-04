#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8747}"
OUTPUT_FILE="${OUTPUT_FILE:-/tmp/tripper-demo-plan-response.html}"

curl -fsS \
  -X POST "${BASE_URL}/travel/journey/plan" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "from=Barcelona" \
  --data-urlencode "to=Bordeaux" \
  --data-urlencode "transportPreference=driving" \
  --data-urlencode "departureDate=2026-06-01" \
  --data-urlencode "returnDate=2026-06-08" \
  --data-urlencode "dailyBudget=220" \
  --data-urlencode "travelers[0].name=Ingrid" \
  --data-urlencode "travelers[0].about=Loves history, museums, and medieval architecture." \
  --data-urlencode "travelers[1].name=Claude" \
  --data-urlencode "travelers[1].about=Enjoys local food, wine, markets, and countryside routes." \
  --data-urlencode "brief=Relaxed road trip through countryside, history, food, and wine. Avoid rushed days and include scenic stops." \
  -o "${OUTPUT_FILE}"

printf 'Demo request submitted to %s\n' "${BASE_URL}/travel/journey/plan"
printf 'Response saved to %s\n' "${OUTPUT_FILE}"

if grep -q 'Planning your journey' "${OUTPUT_FILE}"; then
  printf 'Processing page detected. The agent process was started.\n'
else
  printf 'Response did not look like the standard processing page. Inspect the saved HTML.\n'
fi
