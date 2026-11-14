#!/bin/sh
# =============================================================================
# Initialisation de Vault (mode dev) pour Jenkins :
#   1. Active l'auth AppRole + policy lecture seule "jenkins-ci"
#   2. Génère (une seule fois) le secret_id Jenkins dans le volume partagé
#   3. Crée les secrets du pipeline s'ils n'existent pas encore
# Relancé à chaque "docker compose up" : idempotent.
# =============================================================================
set -eu

APPROLE_DIR=/vault/approle
ROLE=jenkins-ci

echo "[vault-init] Attente de Vault sur ${VAULT_ADDR}..."
until vault status >/dev/null 2>&1; do sleep 1; done

# --- 1. AppRole + policy ------------------------------------------------------
vault auth list | grep -q '^approle/' || vault auth enable approle
vault policy write "$ROLE" /vault/config/jenkins-policy.hcl
vault write "auth/approle/role/$ROLE" \
    token_policies="$ROLE" token_ttl=1h token_max_ttl=4h secret_id_ttl=0 >/dev/null
vault write "auth/approle/role/$ROLE/role-id" role_id="$ROLE" >/dev/null

# --- 2. secret_id stable (persisté dans le volume, survit au redémarrage) -----
mkdir -p "$APPROLE_DIR"
[ -s "$APPROLE_DIR/secret_id" ] || cat /proc/sys/kernel/random/uuid > "$APPROLE_DIR/secret_id"
echo "$ROLE" > "$APPROLE_DIR/role_id"
chmod 644 "$APPROLE_DIR/role_id" "$APPROLE_DIR/secret_id"

SECRET_ID=$(cat "$APPROLE_DIR/secret_id")
vault write "auth/approle/role/$ROLE/custom-secret-id" secret_id="$SECRET_ID" >/dev/null 2>&1 \
    || echo "[vault-init] secret_id déjà enregistré"
# Vérifie que Jenkins pourra réellement s'authentifier
vault write auth/approle/login role_id="$ROLE" secret_id="$SECRET_ID" >/dev/null
echo "[vault-init] AppRole '$ROLE' OK"

# --- 3. Secrets du pipeline (jamais écrasés s'ils existent déjà) -------------
seed() {
    path=$1; shift
    if vault kv get "$path" >/dev/null 2>&1; then
        echo "[vault-init] $path existe déjà — conservé"
    else
        vault kv put "$path" "$@" >/dev/null
        echo "[vault-init] $path créé"
    fi
}

seed secret/ci/dockerhub \
    username="${DOCKERHUB_USERNAME:-moustaphisene}" \
    password="${DOCKERHUB_TOKEN:-CHANGE_ME}"
seed secret/ci/nvd \
    api_key="${NVD_API_KEY:-CHANGE_ME}"
seed secret/ci/keycloak-uat \
    url="${KEYCLOAK_URL_UAT:-http://host.docker.internal:8080}" \
    client_secret="${KEYCLOAK_CLIENT_SECRET_UAT:-CHANGE_ME}"
seed secret/ci/keycloak-preprod \
    url="${KEYCLOAK_URL_PREPROD:-http://host.docker.internal:8080}" \
    client_secret="${KEYCLOAK_CLIENT_SECRET_PREPROD:-CHANGE_ME}"

echo "[vault-init] Terminé"
