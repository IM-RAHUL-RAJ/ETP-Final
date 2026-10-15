#!/usr/bin/env bash

# JWT Debug Utility - helps diagnose JWT validation issues

JWT_TOKEN="$1"
. "$(dirname "${BASH_SOURCE[0]}")/../../scripts/load-env.sh"
require_env JWT_SECRET ORDER_SERVICE_URL || exit 1

if [ -z "$JWT_TOKEN" ]; then
  echo "Usage: $0 <jwt-token>"
  echo ""
  echo "Example:"
  echo "  $0 'eyJhbGciOiJIUzI1NiIs...'"
  exit 1
fi

echo "=== JWT Debug Utility ==="
echo ""
echo "Token: ${JWT_TOKEN:0:50}..."
echo ""

# Split JWT into parts
IFS='.' read -r HEADER PAYLOAD SIGNATURE <<< "$JWT_TOKEN"

echo "1️⃣  HEADER (base64url decoded):"
while [ $((${#HEADER} % 4)) -ne 0 ]; do HEADER="${HEADER}="; done
echo "$HEADER" | base64 -d 2>/dev/null | jq '.' || echo "  (cannot decode)"
echo ""

echo "2️⃣  PAYLOAD (base64url decoded):"
while [ $((${#PAYLOAD} % 4)) -ne 0 ]; do PAYLOAD="${PAYLOAD}="; done
DECODED=$(echo "$PAYLOAD" | base64 -d 2>/dev/null)
echo "$DECODED" | jq '.' || echo "  (cannot decode)"
echo ""

echo "3️⃣  SIGNATURE:"
echo "  ${SIGNATURE:0:50}..."
echo ""

echo "4️⃣  Verify Signature with Expected Secret:"
echo "  Secret: '$JWT_SECRET'"
echo ""

# Create expected signature using OpenSSL/Node
echo "  To verify, Trade API should compute HMACSHA256 with the same secret."
echo "  If verification fails, it means Trade API is using a different secret."
echo ""

echo "5️⃣  Next Steps:"
echo "  ✓ Verify JWT_SECRET is set on Linux VM:"
echo "    ssh admin@${ORDER_SERVICE_URL#*://} 'echo \$JWT_SECRET'"
echo ""
echo "  ✓ Check Trade API logs for JWT validation errors"
echo ""
echo "  ✓ Ensure Trade API was restarted AFTER setting JWT_SECRET:"
echo "    export JWT_SECRET='$JWT_SECRET'"
echo "    java -jar target/trade-api.jar"
echo ""
