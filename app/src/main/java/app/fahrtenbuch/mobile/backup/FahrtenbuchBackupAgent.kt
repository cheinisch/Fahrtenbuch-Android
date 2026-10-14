package app.fahrtenbuch.mobile.backup

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.os.ParcelFileDescriptor
import android.util.Log
import app.fahrtenbuch.mobile.data.api.SessionStore
import app.fahrtenbuch.mobile.data.local.OfflineDatabase

/**
 * Android-Backup (Google-Konto bzw. Gerätewechsel) nur, wenn der Nutzer es in den
 * Einstellungen erlaubt hat. Was gesichert wird, legen backup_rules.xml und
 * data_extraction_rules.xml fest: Datenbank und Einstellungen, nie Tokens oder Geräte-ID.
 */
class FahrtenbuchBackupAgent : BackupAgent() {

    override fun onFullBackup(data: FullBackupDataOutput) {
        val deviceTransfer = (data.transportFlags and FLAG_DEVICE_TO_DEVICE_TRANSFER) != 0
        // Gerätewechsel per Kabel/WLAN startet der Nutzer selbst – den immer erlauben
        if (!deviceTransfer && !SessionStore(this).googleBackupEnabled) {
            Log.i(TAG, "Google backup disabled by user, nothing backed up")
            return
        }
        // WAL-Inhalte in die Datenbankdatei schreiben, damit die Sicherung vollständig ist
        runCatching {
            OfflineDatabase.get(this).openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        }.onFailure { Log.w(TAG, "WAL checkpoint failed", it) }
        super.onFullBackup(data)
    }

    override fun onRestoreFinished() {
        super.onRestoreFinished()
        // Wiederhergestellt wird der Stand ohne Anmeldung: Tokens/Geräte-ID gibt es nicht im Backup
        SessionStore(this).clearTokens()
    }

    // Key/Value-Backup wird nicht verwendet (fullBackupOnly)
    override fun onBackup(oldState: ParcelFileDescriptor?, data: BackupDataOutput?, newState: ParcelFileDescriptor?) = Unit
    override fun onRestore(data: BackupDataInput?, appVersionCode: Int, newState: ParcelFileDescriptor?) = Unit

    private companion object {
        const val TAG = "FahrtenbuchBackup"
    }
}
