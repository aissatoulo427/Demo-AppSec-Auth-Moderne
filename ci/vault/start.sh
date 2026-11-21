#!/bin/sh
# =============================================================================
# Point d'entrée du conteneur Vault (mode dev) :
#   1. Démarre Vault en arrière-plan
#   2. Rejoue init.sh (AppRole, policy, secrets)
#
# Le mode dev stocke tout en mémoire : chaque redémarrage du conteneur (reboot
# du PC, redémarrage de Docker...) efface l'AppRole et les secrets. En rejouant
# init.sh ici, Vault est réinitialisé automatiquement à chaque démarrage et
# Jenkins retrouve le même secret_id (persisté dans le volume vault_approle).
# =============================================================================
set -eu

docker-entrypoint.sh server -dev &
VAULT_PID=$!
trap 'kill -TERM "$VAULT_PID" 2>/dev/null' TERM INT

if ! sh /vault/config/init.sh; then
    echo "[vault-init] Échec de l'initialisation, arrêt de Vault"
    kill -TERM "$VAULT_PID"
    wait "$VAULT_PID" || true
    exit 1
fi

wait "$VAULT_PID"
