# Policy Jenkins CI — lecture seule sur les secrets du pipeline (KV v2).
# Principe du moindre privilège : aucun droit d'écriture, aucun accès hors ci/.
path "secret/data/ci/*" {
  capabilities = ["read"]
}

path "secret/metadata/ci/*" {
  capabilities = ["read", "list"]
}
