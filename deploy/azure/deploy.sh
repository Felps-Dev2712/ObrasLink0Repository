#!/usr/bin/env bash
# Provision a single Azure VM that runs ObraLink (Postgres + Spring Boot + Caddy/HTTPS).
# Usage (from the repository root):
#   SUBSCRIPTION=<subscription-id> ./deploy/azure/deploy.sh
# Every setting below can be overridden with an environment variable. See README.md.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

: "${SUBSCRIPTION:?Set SUBSCRIPTION to the target Azure subscription id (az account list -o table)}"
LOCATION="${LOCATION:-brazilsouth}"
NAME="${NAME:-obraslink-demo}"                       # resource-name prefix and DNS label
RESOURCE_GROUP="${RESOURCE_GROUP:-rg-$NAME}"
DNS_LABEL="${DNS_LABEL:-$NAME}"
VM_SIZE="${VM_SIZE:-Standard_D2als_v6}"
VM_IMAGE="${VM_IMAGE:-Canonical:ubuntu-24_04-lts:server:latest}"
DISK_CONTROLLER="${DISK_CONTROLLER:-NVMe}"           # v6 sizes are NVMe-only; use SCSI for B/older sizes
OS_DISK_GB="${OS_DISK_GB:-30}"
ADMIN_USER="${ADMIN_USER:-azureuser}"
SSH_KEY="${SSH_KEY:-$HOME/.ssh/$NAME}"
REPO_URL="${REPO_URL:-$(git -C "$SCRIPT_DIR" remote get-url origin)}"
REPO_BRANCH="${REPO_BRANCH:-main}"
SSH_GEO_COUNTRY="${SSH_GEO_COUNTRY-BR}"              # empty string disables the country filter
SSH_GEO_REGISTRY_URL="${SSH_GEO_REGISTRY_URL:-https://ftp.lacnic.net/pub/stats/lacnic/delegated-lacnic-latest}"
BUDGET_AMOUNT="${BUDGET_AMOUNT:-60}"
BUDGET_EMAIL="${BUDGET_EMAIL:-}"                     # empty = no budget is created

DOMAIN="$DNS_LABEL.$LOCATION.cloudapp.azure.com"
RG_ARGS=(-g "$RESOURCE_GROUP")
TAG_ARGS=(--tags app=obraslink)
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

# az only accepts --subscription after the command name, so append it.
az_sub() { az "$@" --subscription "$SUBSCRIPTION" --only-show-errors; }
# az on Windows (Git Bash) needs Windows-style paths for file arguments.
native_path() { if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else printf '%s' "$1"; fi; }
log() { printf '\n==> %s\n' "$*"; }
die() { echo "ERROR: $*" >&2; exit 1; }

# Values end up in sed, YAML and shell lines of cloud-init: allow only safe characters.
# The URL pattern also rejects embedded credentials (user:token@host).
require() { # <variable name> <regex> <hint>
  [[ ${!1} =~ $2 ]] || die "$1 is invalid (value not shown, it may hold a secret): expected $3"
}
require LOCATION             '^[a-z0-9]+$'                          'an Azure region name, e.g. brazilsouth'
require NAME                 '^[A-Za-z0-9][A-Za-z0-9-]{0,40}$'      'letters, digits and dashes'
require DNS_LABEL            '^[a-z][a-z0-9-]{1,61}[a-z0-9]$'       'lowercase letters, digits and dashes'
require ADMIN_USER           '^[a-z_][a-z0-9_-]{0,31}$'             'a Linux user name'
require REPO_URL             '^https://[A-Za-z0-9._~/:%+-]+$'       'a public https clone URL without credentials or query string'
require REPO_BRANCH          '^[A-Za-z0-9._/-]+$'                   'a branch name'
require SSH_GEO_COUNTRY      '^([A-Z]{2})?$'                        'a two-letter country code, or empty'
require SSH_GEO_REGISTRY_URL '^https://[A-Za-z0-9._~/:%+-]+$'       'an https URL without query string'
require BUDGET_AMOUNT        '^[0-9]+$'                             'a whole number'
require BUDGET_EMAIL         '^([^"\\[:space:]]+@[^"\\[:space:]]+)?$' 'an email address, or empty'

log "Settings"
cat <<EOF
  location        $LOCATION
  resource group  $RESOURCE_GROUP
  vm size         $VM_SIZE
  domain          https://$DOMAIN
  repository      $REPO_URL ($REPO_BRANCH)
  ssh key         $SSH_KEY
  ssh country     ${SSH_GEO_COUNTRY:-<any>}
  budget          ${BUDGET_EMAIL:+\$$BUDGET_AMOUNT/month, alerts to }${BUDGET_EMAIL:-<none>}
EOF

log "Checking that $VM_SIZE can be created in $LOCATION (takes ~1 minute)"
# Without --all, list-skus leaves out sizes this subscription cannot create in the region.
available=$(az_sub vm list-skus -l "$LOCATION" --resource-type virtualMachines \
  --query "[?name=='$VM_SIZE'].name" -o tsv)
[ -n "$available" ] || die "$VM_SIZE is not available to this subscription in $LOCATION. See README.md > Choosing a VM size."

if [ ! -f "$SSH_KEY" ]; then
  log "Generating SSH key $SSH_KEY"
  mkdir -p "$(dirname "$SSH_KEY")"
  ssh-keygen -t ed25519 -N "" -C "$NAME" -f "$SSH_KEY" -q
fi

log "Resource group"
az_sub group create -n "$RESOURCE_GROUP" -l "$LOCATION" "${TAG_ARGS[@]}" -o none

if [ -n "$BUDGET_EMAIL" ]; then
  log "Budget (\$$BUDGET_AMOUNT/month, alerts at 50% and 100% actual, 100% forecast)"
  year=$(date -u +%Y); month=$(date -u +%m)
  notification() { # <threshold> <Actual|Forecasted>
    printf '{"enabled":true,"operator":"GreaterThanOrEqualTo","threshold":%s,"thresholdType":"%s","contactEmails":["%s"]}' \
      "$1" "$2" "$BUDGET_EMAIL"
  }
  cat > "$TMP_DIR/budget.json" <<EOF
{"properties":{"category":"Cost","amount":$BUDGET_AMOUNT,"timeGrain":"Monthly",
 "timePeriod":{"startDate":"$year-$month-01T00:00:00Z","endDate":"$((year + 1))-$month-01T00:00:00Z"},
 "notifications":{"actual50":$(notification 50 Actual),"actual100":$(notification 100 Actual),"forecast100":$(notification 100 Forecasted)}}}
EOF
  # az rest takes the subscription from the URL, not from --subscription.
  az rest --only-show-errors --method put \
    --url "https://management.azure.com/subscriptions/$SUBSCRIPTION/resourceGroups/$RESOURCE_GROUP/providers/Microsoft.Consumption/budgets/$NAME-budget?api-version=2023-05-01" \
    --body @"$(native_path "$TMP_DIR/budget.json")" -o none
fi

log "Network: NSG (22, 80, 443), virtual network, static public IP"
az_sub network nsg create "${RG_ARGS[@]}" -n "nsg-$NAME" -l "$LOCATION" -o none
priority=100
for port in 22 80 443; do
  az_sub network nsg rule create "${RG_ARGS[@]}" --nsg-name "nsg-$NAME" -n "allow-$port" \
    --priority "$priority" --protocol Tcp --destination-port-ranges "$port" \
    --access Allow --direction Inbound -o none
  priority=$((priority + 10))
done
az_sub network vnet create "${RG_ARGS[@]}" -n "vnet-$NAME" -l "$LOCATION" \
  --address-prefixes 10.10.0.0/16 --subnet-name snet-app --subnet-prefixes 10.10.0.0/24 \
  --network-security-group "nsg-$NAME" -o none
az_sub network public-ip create "${RG_ARGS[@]}" -n "pip-$NAME" -l "$LOCATION" \
  --sku Standard --allocation-method Static --dns-name "$DNS_LABEL" -o none

log "Rendering cloud-init"
cloud_init="$TMP_DIR/cloud-init.yaml"
sed -e "s|__DOMAIN__|$DOMAIN|g" \
    -e "s|__REPO_URL__|$REPO_URL|g" \
    -e "s|__REPO_BRANCH__|$REPO_BRANCH|g" \
    -e "s|__ADMIN_USER__|$ADMIN_USER|g" \
    -e "s|__SSH_GEO_COUNTRY__|$SSH_GEO_COUNTRY|g" \
    -e "s|__SSH_GEO_REGISTRY_URL__|$SSH_GEO_REGISTRY_URL|g" \
    "$SCRIPT_DIR/cloud-init.yaml.tpl" > "$cloud_init"
if grep -q '__[A-Z_]*__' "$cloud_init"; then
  grep -n '__[A-Z_]*__' "$cloud_init" >&2
  die "unrendered placeholder left in cloud-init"
fi

log "Virtual machine ($VM_SIZE)"
az_sub vm create "${RG_ARGS[@]}" -n "vm-$NAME" -l "$LOCATION" \
  --size "$VM_SIZE" --image "$VM_IMAGE" \
  --admin-username "$ADMIN_USER" --authentication-type ssh \
  --ssh-key-values "$(native_path "$SSH_KEY.pub")" \
  --custom-data "$(native_path "$cloud_init")" \
  --os-disk-size-gb "$OS_DISK_GB" --storage-sku StandardSSD_LRS --disk-controller-type "$DISK_CONTROLLER" \
  --vnet-name "vnet-$NAME" --subnet snet-app --nsg "" --public-ip-address "pip-$NAME" \
  --os-disk-delete-option Delete --nic-delete-option Delete \
  "${TAG_ARGS[@]}" -o none
az_sub vm boot-diagnostics enable "${RG_ARGS[@]}" -n "vm-$NAME" -o none   # enables the Serial Console

# Poll over HTTPS rather than SSH: SSH may be blocked by the country filter for the operator.
log "Waiting for first boot and HTTPS (packages, Maven build, certificate): usually 5-10 minutes"
code=000
for _ in $(seq 1 90); do
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "https://$DOMAIN/login" || true)
  [ "$code" = 200 ] && break
  sleep 10
done

log "VM status (via az vm run-command, works without SSH)"
status=$(az_sub vm run-command invoke "${RG_ARGS[@]}" -n "vm-$NAME" --command-id RunShellScript \
  --scripts 'cloud-init status; tail -n 1 /var/log/obraslink-first-deploy.log 2>/dev/null; journalctl -u ssh-geo --no-pager -o cat | grep "ssh-geo:" | tail -n 1' \
  --query "value[0].message" -o tsv || echo "(run-command failed)")
printf '%s\n' "$status"
if [ -n "$SSH_GEO_COUNTRY" ] && ! grep -q "SSH limited to $SSH_GEO_COUNTRY" <<<"$status"; then
  echo "WARNING: the SSH country filter is NOT active (SSH open to all IPs, still key-only + fail2ban)." >&2
  echo "         Retry with: az vm run-command invoke ... --scripts 'systemctl start ssh-geo'" >&2
fi
[ "$code" = 200 ] || echo "WARNING: https://$DOMAIN/login answered $code. See README.md > Troubleshooting." >&2

log "Done"
cat <<EOF
  URL       https://$DOMAIN   (GET /login -> $code)
  SSH       ssh -i $SSH_KEY $ADMIN_USER@$DOMAIN
  Redeploy  ssh -i $SSH_KEY $ADMIN_USER@$DOMAIN /opt/obraslink/deploy/redeploy.sh
  Teardown  az group delete --subscription $SUBSCRIPTION -n $RESOURCE_GROUP
EOF
