// Configure le plugin HashiCorp Vault : credential AppRole + configuration globale.
// role_id / secret_id sont générés par le conteneur vault via init.sh (volume partagé).
import jenkins.model.*
import com.cloudbees.plugins.credentials.*
import com.cloudbees.plugins.credentials.domains.Domain
import com.datapipe.jenkins.vault.credentials.VaultAppRoleCredential
import com.datapipe.jenkins.vault.configuration.GlobalVaultConfiguration
import com.datapipe.jenkins.vault.configuration.VaultConfiguration
import hudson.util.Secret

def approleDir   = new File('/var/jenkins_vault')
def roleIdFile   = new File(approleDir, 'role_id')
def secretIdFile = new File(approleDir, 'secret_id')

if (!roleIdFile.exists() || !secretIdFile.exists()) {
    println '[INIT] Vault : role_id/secret_id introuvables dans /var/jenkins_vault, skip.'
    return
}

try {
    // --- Credential AppRole (créé ou mis à jour) ---
    def credId = 'vault-approle'
    def store  = SystemCredentialsProvider.getInstance().getStore()
    def cred   = new VaultAppRoleCredential(
        CredentialsScope.GLOBAL, credId, 'AppRole Jenkins -> Vault (genere par vault/init.sh)',
        roleIdFile.text.trim(), Secret.fromString(secretIdFile.text.trim()), 'approle')

    def existing = store.getCredentials(Domain.global()).find { it.id == credId }
    if (existing) {
        store.updateCredentials(Domain.global(), existing, cred)
    } else {
        store.addCredentials(Domain.global(), cred)
    }

    // --- Configuration globale du plugin ---
    def cfg = new VaultConfiguration()
    cfg.setVaultUrl(System.getenv('VAULT_ADDR') ?: 'http://vault:8200')
    cfg.setVaultCredentialId(credId)
    cfg.setEngineVersion(2)

    def global = GlobalConfiguration.all().get(GlobalVaultConfiguration)
    global.setConfiguration(cfg)
    global.save()

    println "[INIT] Vault configuré → ${cfg.getVaultUrl()} (credential ${credId})"
} catch (e) {
    println "[INIT] Vault : échec de la configuration automatique — ${e}"
}
