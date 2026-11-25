# Deploy ObraLink on Azure (single VM)

A low-cost demo/acceptance environment: one Ubuntu VM running Postgres, the Spring Boot
app and [Caddy](https://caddyserver.com/) (automatic HTTPS) with Docker Compose.
One script creates everything inside a single resource group; deleting that group
removes it all.

```
Internet ──► NSG (22, 80, 443) ──► VM ─┬─ caddy  :80/:443  (Let's Encrypt)
                                       ├─ app    :8080     (internal only)
                                       └─ db     :5432     (internal only)
```

> Intended for **demo data** (the `DataSeeder` accounts). The seeded logins and
> passwords are public in [`docs/contas-de-teste.md`](../../docs/contas-de-teste.md),
> including an administrator: don't put real personal data on this environment.

## What gets created

All in resource group `rg-<NAME>` (default `rg-obraslink-demo`):

| Resource | Default |
|---|---|
| VM | `Standard_D2als_v6` (2 vCPU / 4 GB), Ubuntu 24.04 LTS, 30 GB Standard SSD |
| Public IP | Standard, static, DNS name `<NAME>.<LOCATION>.cloudapp.azure.com` |
| Network | VNet `10.10.0.0/16` + NSG allowing TCP 22, 80, 443 inbound |
| Budget | Optional monthly cost alert (only if `BUDGET_EMAIL` is set) |

On first boot, [`cloud-init.yaml.tpl`](cloud-init.yaml.tpl) hardens SSH, installs Docker,
clones this repository and runs `docker compose up -d --build`.

## Prerequisites

- An Azure subscription and the [Azure CLI](https://learn.microsoft.com/cli/azure/install-azure-cli), logged in:
  `az login --tenant <tenant-id>`, then `az account list -o table` to get the subscription id.
- `bash`, `ssh`, `ssh-keygen`, `git`, `curl` (Linux/macOS, or Git Bash on Windows).
- The repository must be clonable **without credentials** over https (public repo), or set
  `REPO_URL` to such a URL. On a new subscription, register the providers once:
  `az provider register --namespace Microsoft.Compute --subscription <id>` (same for `Microsoft.Network`).

## Deploy

From the repository root:

```bash
SUBSCRIPTION=<subscription-id> BUDGET_EMAIL=<alerts@example.com> ./deploy/azure/deploy.sh
```

It takes about 10–15 minutes and ends by printing the URL plus the SSH, redeploy and
teardown commands. The SSH key is created at `~/.ssh/<NAME>` if it doesn't exist.

### Settings

Override any of these with environment variables:

| Variable | Default | Notes |
|---|---|---|
| `SUBSCRIPTION` | *(required)* | Target subscription id. |
| `LOCATION` | `brazilsouth` | Azure region. |
| `NAME` | `obraslink-demo` | Prefix for resource names and the DNS label. The DNS label must be unique in the region. |
| `RESOURCE_GROUP` | `rg-$NAME` | |
| `DNS_LABEL` | `$NAME` | Site becomes `https://$DNS_LABEL.$LOCATION.cloudapp.azure.com`. |
| `VM_SIZE` | `Standard_D2als_v6` | See [Choosing a VM size](#choosing-a-vm-size). |
| `DISK_CONTROLLER` | `NVMe` | v6 sizes require `NVMe`; B-series and older sizes need `SCSI`. |
| `OS_DISK_GB` | `30` | |
| `ADMIN_USER` | `azureuser` | |
| `SSH_KEY` | `~/.ssh/$NAME` | Private key path; `.pub` is uploaded. |
| `REPO_URL` | `git remote get-url origin` | Must be `https://` and public. |
| `REPO_BRANCH` | `main` | |
| `SSH_GEO_COUNTRY` | `BR` | Only IPs registered to this country can reach SSH. Set to empty (`SSH_GEO_COUNTRY=`) to disable. |
| `SSH_GEO_REGISTRY_URL` | LACNIC delegated stats | Must be the registry that covers `SSH_GEO_COUNTRY` (LACNIC, ARIN, RIPE NCC, APNIC or AFRINIC). |
| `BUDGET_AMOUNT` | `60` | Monthly amount (subscription currency). |
| `BUDGET_EMAIL` | *(empty)* | Alerts at 50 % / 100 % of actual and 100 % of forecast. Empty skips the budget. |

## Choosing a VM size

The app, the Maven build and Postgres need **about 4 GB of RAM**: 1 GB sizes fail during the build.

Free-trial and new subscriptions often **can't create B-series VMs** in popular regions. The
script checks before creating anything. To see what your subscription can create in a region
(sizes it can't use are left out):

```bash
az vm list-skus -l brazilsouth --resource-type virtualMachines --subscription <id> \
  --query "[?capabilities[?name=='MemoryGB' && value=='4']].name" -o tsv
```

Reference prices (Linux, pay-as-you-go, late 2026; check the
[pricing calculator](https://azure.microsoft.com/pricing/calculator/)):

| Size | Region | ≈ per hour | ≈ 2 weeks |
|---|---|---|---|
| `Standard_D2als_v6` | Brazil South | $0.13 | $43 |
| `Standard_B2als_v2` (`DISK_CONTROLLER=SCSI`) | Chile Central | $0.05 | $18 |
| `Standard_B2als_v2` (`DISK_CONTROLLER=SCSI`) | Mexico Central | $0.04 | $14 |

Add about $3–5 per two weeks for the disk, the static IP and traffic.

## Security model

- **SSH**: keys only (password and root login disabled) and [fail2ban](https://github.com/fail2ban/fail2ban)
  (5 failures in 10 min → 1 h ban). Port 22 is also limited to `SSH_GEO_COUNTRY` by an
  nftables set built from the registry's delegated stats, refreshed weekly (`ssh-geo.timer`).
  If the list can't be downloaded, the existing rules are kept.
- **Locked out of SSH** (abroad, VPN, foreign mobile IP)? These don't use SSH:
  - `az vm run-command invoke -g <rg> -n vm-<NAME> --command-id RunShellScript --scripts "<cmd>" --subscription <id>`
  - Azure Portal → the VM → **Serial console** (boot diagnostics are enabled by the script).
- **Database**: not published outside Docker. The password is random, generated on the VM in
  `/opt/obraslink/deploy/.env` (mode 600), never in git.
- **App**: reachable only through Caddy over HTTPS; plain HTTP redirects to HTTPS.
- Give a teammate SSH access by adding their **public** key:
  `az vm user update -g <rg> -n vm-<NAME> -u azureuser --ssh-key-value "<contents of their .pub>" --subscription <id>`

## Day-to-day

```bash
H=azureuser@<NAME>.<LOCATION>.cloudapp.azure.com; K=~/.ssh/<NAME>

ssh -i $K $H /opt/obraslink/deploy/redeploy.sh        # git pull + rebuild + restart
ssh -i $K $H 'cd /opt/obraslink/deploy && docker compose ps'
ssh -i $K $H 'cd /opt/obraslink/deploy && docker compose logs --tail 100 app'

# Reset to fresh demo data (DataSeeder only seeds an empty database)
ssh -i $K $H 'cd /opt/obraslink/deploy && docker compose down && docker volume rm obraslink_postgres_data && docker compose up -d'
```

Pushing to the branch does **not** deploy by itself: run `redeploy.sh`.

On the VM:

```
/opt/obraslink/app      clone of the repository
/opt/obraslink/deploy   compose.yml, Caddyfile, .env, redeploy.sh
/var/log/obraslink-first-deploy.log   output of the first build
```

## Teardown

Deletes the VM, disk, IP, network and budget. **Irreversible**, including the database:

```bash
az group delete -n rg-<NAME> --subscription <subscription-id>
```

## Troubleshooting

| Symptom | Check |
|---|---|
| `... is not available to this subscription` | Pick another size or region ([Choosing a VM size](#choosing-a-vm-size)). |
| `DnsRecordInUse` / label taken | Set a different `DNS_LABEL`. |
| VM creation fails on the disk controller | Match `DISK_CONTROLLER` to the size (`NVMe` for v6, `SCSI` for B-series). |
| Site doesn't load after the script ends | `ssh ... 'sudo cloud-init status --long; tail -50 /var/log/obraslink-first-deploy.log'` |
| Browser shows a certificate error | Caddy is still getting the certificate (ports 80/443 must be open): `docker compose logs caddy`. |
| SSH times out | Your IP may be outside `SSH_GEO_COUNTRY`; use `az vm run-command` (above). |
| `REMOTE HOST IDENTIFICATION HAS CHANGED` after recreating | The new VM has a new host key: `ssh-keygen -R <NAME>.<LOCATION>.cloudapp.azure.com`. |
| Script warns the SSH country filter is not active | The registry list couldn't be downloaded; SSH is open (key-only + fail2ban). Retry: `az vm run-command invoke ... --scripts 'systemctl start ssh-geo'`. |
