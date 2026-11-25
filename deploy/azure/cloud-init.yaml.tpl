#cloud-config
# First-boot setup for the ObraLink VM (Ubuntu 24.04). Rendered by deploy.sh, which
# fills in the double-underscore placeholders below. Do not pass it to Azure unrendered.
#
# - SSH: keys only, fail2ban, and (optionally) port 22 accepted only from one
#   country's IP ranges, taken from a Regional Internet Registry's delegated stats.
# - Docker + Compose: clones the repository and starts db + app + Caddy (HTTPS).

package_update: true
packages:
  - docker.io
  - docker-compose-v2
  - nftables
  - fail2ban
  - git
  - curl
  - ca-certificates
  - python3

write_files:
  - path: /etc/ssh/sshd_config.d/10-hardening.conf
    content: |
      PasswordAuthentication no
      KbdInteractiveAuthentication no
      PermitRootLogin no

  - path: /etc/fail2ban/jail.d/sshd.local
    content: |
      [sshd]
      enabled = true
      backend = systemd
      maxretry = 5
      findtime = 10m
      bantime = 1h

  - path: /etc/default/ssh-geo
    content: |
      # ISO 3166 country code allowed to reach SSH (empty = no country filter).
      SSH_GEO_COUNTRY=__SSH_GEO_COUNTRY__
      # Delegated stats of the registry that covers that country.
      SSH_GEO_REGISTRY_URL=__SSH_GEO_REGISTRY_URL__

  # Builds an nftables table that drops SSH (port 22) unless the source IP is
  # registered to SSH_GEO_COUNTRY. If the list can't be downloaded, the current
  # rules are kept (on first boot: SSH stays open, still key-only + fail2ban).
  - path: /usr/local/sbin/ssh-geo.sh
    permissions: "0755"
    content: |
      #!/bin/bash
      set -euo pipefail
      . /etc/default/ssh-geo
      if [ -z "${SSH_GEO_COUNTRY:-}" ]; then
        nft delete table inet sshgeo 2>/dev/null || true
        echo "ssh-geo: no country set; SSH open to all IPs (key-only + fail2ban)"
        exit 0
      fi
      tmp=$(mktemp); out=$(mktemp)
      trap 'rm -f "$tmp" "$out"' EXIT
      if ! curl -fsS --retry 3 -m 120 "$SSH_GEO_REGISTRY_URL" -o "$tmp" \
         || ! grep -q "|${SSH_GEO_COUNTRY}|ipv4|" "$tmp"; then
        echo "ssh-geo: registry list unavailable or has no ${SSH_GEO_COUNTRY} ranges; SSH rules unchanged" >&2
        exit 0
      fi
      python3 - "$tmp" "$SSH_GEO_COUNTRY" > "$out" <<'PY'
      import sys, ipaddress
      path, country = sys.argv[1], sys.argv[2]
      v4, v6 = [], []
      for line in open(path):
          p = line.strip().split('|')
          if len(p) < 7 or p[1] != country or p[6] not in ('allocated', 'assigned'):
              continue
          if p[2] == 'ipv4':
              s = ipaddress.IPv4Address(p[3])
              v4.append(f"{s}-{s + int(p[4]) - 1}")
          elif p[2] == 'ipv6':
              v6.append(f"{p[3]}/{p[4]}")
      print(f"# ipv4 ranges: {len(v4)}")
      print("table inet sshgeo")
      print("delete table inet sshgeo")
      print("table inet sshgeo {")
      print("  set allow4 { type ipv4_addr; flags interval; auto-merge; elements = { " + ", ".join(v4) + " } }")
      if v6:
          print("  set allow6 { type ipv6_addr; flags interval; auto-merge; elements = { " + ", ".join(v6) + " } }")
      print("  chain input {")
      print("    type filter hook input priority -10; policy accept;")
      print("    tcp dport 22 ip saddr @allow4 accept")
      if v6:
          print("    tcp dport 22 ip6 saddr @allow6 accept")
      print("    tcp dport 22 counter drop")
      print("  }")
      print("}")
      PY
      nft -c -f "$out"
      nft -f "$out"
      echo "ssh-geo: SSH limited to ${SSH_GEO_COUNTRY} ($(sed -n 's/^# ipv4 ranges: //p' "$out") IPv4 ranges)"

  - path: /etc/systemd/system/ssh-geo.service
    content: |
      [Unit]
      Description=Limit SSH to one country's IP ranges
      Wants=network-online.target
      After=network-online.target

      [Service]
      Type=oneshot
      ExecStart=/usr/local/sbin/ssh-geo.sh

      [Install]
      WantedBy=multi-user.target

  - path: /etc/systemd/system/ssh-geo.timer
    content: |
      [Unit]
      Description=Refresh the SSH country IP ranges weekly

      [Timer]
      OnCalendar=weekly
      Persistent=true

      [Install]
      WantedBy=timers.target

  - path: /opt/obraslink/deploy/compose.yml
    content: |
      # Deploy-only compose file: Postgres stays internal (no published port),
      # Caddy terminates HTTPS and is the only service exposed.
      name: obraslink
      services:
        db:
          image: postgres:16-alpine
          restart: unless-stopped
          environment:
            POSTGRES_DB: obraslink
            POSTGRES_USER: obraslink
            POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?set in .env}
          volumes:
            - postgres_data:/var/lib/postgresql/data
          healthcheck:
            test: ["CMD-SHELL", "pg_isready -U obraslink"]
            interval: 10s
            timeout: 5s
            retries: 5

        app:
          build: ../app
          restart: unless-stopped
          environment:
            SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/obraslink
            SPRING_DATASOURCE_USERNAME: obraslink
            SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}
            SPRING_JPA_HIBERNATE_DDL_AUTO: update
            SPRING_JPA_SHOW_SQL: "false"
            LOGGING_LEVEL_COM_OBRASLINK: INFO
            SERVER_FORWARD_HEADERS_STRATEGY: framework
            OBRALINK_UPLOAD_DIR: /app/uploads
            JAVA_TOOL_OPTIONS: -XX:MaxRAMPercentage=50
          volumes:
            - uploads_data:/app/uploads
          depends_on:
            db:
              condition: service_healthy

        caddy:
          image: caddy:2-alpine
          restart: unless-stopped
          ports:
            - "80:80"
            - "443:443"
          volumes:
            - ./Caddyfile:/etc/caddy/Caddyfile:ro
            - caddy_data:/data
            - caddy_config:/config
          depends_on:
            - app

      volumes:
        postgres_data:
        uploads_data:
        caddy_data:
        caddy_config:

  - path: /opt/obraslink/deploy/Caddyfile
    content: |
      __DOMAIN__ {
          encode gzip
          reverse_proxy app:8080
      }

  - path: /opt/obraslink/deploy/redeploy.sh
    permissions: "0755"
    content: |
      #!/bin/bash
      # Pull the latest code and rebuild/restart the app.
      set -euo pipefail
      cd /opt/obraslink/app && git pull --ff-only
      cd /opt/obraslink/deploy && docker compose up -d --build
      docker image prune -f >/dev/null

runcmd:
  - systemctl restart ssh
  - systemctl enable --now fail2ban
  - systemctl daemon-reload
  - systemctl enable --now ssh-geo.service ssh-geo.timer
  - usermod -aG docker __ADMIN_USER__
  - git clone --branch __REPO_BRANCH__ __REPO_URL__ /opt/obraslink/app
  - bash -c 'umask 077; echo "POSTGRES_PASSWORD=$(openssl rand -hex 24)" > /opt/obraslink/deploy/.env'
  - chown -R __ADMIN_USER__:__ADMIN_USER__ /opt/obraslink
  - bash -c 'cd /opt/obraslink/deploy && docker compose up -d --build > /var/log/obraslink-first-deploy.log 2>&1; echo "exit=$?" >> /var/log/obraslink-first-deploy.log'
