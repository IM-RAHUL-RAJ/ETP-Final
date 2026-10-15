#!/usr/bin/env bash

# Copies this repository's .env (JWT_SECRET, database, ports, ...) to the Linux VM that runs the
# Docker services, then (re)starts them so every container shares the same JWT_SECRET.
# Usage: ./setup-jwt-docker.sh <vm-user> <vm-ip>
# The compose file and sources must already be in the VM user's home directory.

set -euo pipefail

. "$(dirname "${BASH_SOURCE[0]}")/scripts/load-env.sh"

USERNAME="${1:?usage: $0 <vm-user> <vm-ip>}"
VM_IP="${2:?usage: $0 <vm-user> <vm-ip>}"
require_env JWT_SECRET

echo "🔐 Setting up the environment for Docker services"
echo ""
echo "Target VM: $USERNAME@$VM_IP"
echo ""

# Step 1: Copy .env to the Linux VM (the same file the services read locally)
echo "📝 Step 1: Copying .env to the Linux VM..."
scp "$ETP_ROOT/.env" "$USERNAME@$VM_IP:.env"
echo "✓ .env copied"
echo ""

# Step 2: Stop existing containers if running
echo "🛑 Step 2: Stopping existing containers (if any)..."
ssh "$USERNAME@$VM_IP" "docker-compose down 2>/dev/null || true"
echo "✓ Containers stopped"
echo ""

# Step 3: Start services
echo "🚀 Step 3: Starting Docker services..."
ssh "$USERNAME@$VM_IP" "docker-compose --profile platform up -d"
echo "✓ Services started"
echo ""

# Step 4: Verify JWT_SECRET is set in containers (names only, the value is never printed)
echo "✅ Step 4: Verifying JWT_SECRET in containers..."
echo ""
echo "JWT_SECRET in 'backend' (order service) container:"
ssh "$USERNAME@$VM_IP" "docker-compose exec -T backend env | grep -c '^JWT_SECRET=' || echo '  ✗ Not set'"
echo ""
echo "JWT_SECRET in 'auth-service' container:"
ssh "$USERNAME@$VM_IP" "docker-compose exec -T auth-service env | grep -c '^JWT_SECRET=' || echo '  ✗ Not set'"
echo ""

# Step 5: Wait for services to be healthy
echo "⏳ Step 5: Waiting for services to be healthy..."
sleep 10
echo "✓ Services should be ready"
echo ""

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ Setup complete!"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "Useful Docker commands:"
echo "  View logs: ssh $USERNAME@$VM_IP 'docker-compose logs backend'"
echo "  Stop services: ssh $USERNAME@$VM_IP 'docker-compose down'"
echo "  Restart services: ssh $USERNAME@$VM_IP 'docker-compose up -d'"
echo ""
