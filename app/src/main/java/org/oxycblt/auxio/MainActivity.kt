package org.oxycblt.auxio

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import androidx.core.view.updatePadding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.oxycblt.auxio.databinding.ActivityMainBinding
import org.oxycblt.auxio.playback.PlaybackViewModel
import org.oxycblt.auxio.playback.state.DeferredPlayback
import org.oxycblt.auxio.ui.UISettings
import org.oxycblt.auxio.util.isNight
import org.oxycblt.auxio.util.systemBarInsetsCompat
import timber.log.Timber as L
// Tambahkan import ini
import java.io.File
import java.io.FileOutputStream

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val playbackModel: PlaybackViewModel by viewModels()
    @Inject lateinit var uiSettings: UISettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // --- TAMBAHKAN FUNGSI INI ---
        copyAssetsToMusicFolder()
        // ----------------------------

        setupTheme()
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupEdgeToEdge(binding.root)
        L.d("Activity created")
    }

    // --- FUNGSI UNTUK MENYALIN MUSIK DARI ASSETS ---
    private fun copyAssetsToMusicFolder() {
        // Kita simpan di folder internal agar aplikasi punya izin akses penuh
        val folder = getExternalFilesDir(null) 
        val assetManager = assets
        
        try {
            // Mengambil semua file di folder assets
            val files = assetManager.list("") ?: return
            for (filename in files) {
                // Filter hanya file musik (tambah ekstensi lain jika perlu)
                if (filename.endsWith(".mp3") || filename.endsWith(".flac") || filename.endsWith(".m4a")) {
                    val outFile = File(folder, filename)
                    
                    // Cek jika file sudah ada agar tidak menyalin ulang setiap kali buka aplikasi
                    if (!outFile.exists()) { 
                        L.d("Copying asset: $filename")
                        assetManager.open(filename).use { input ->
                            FileOutputStream(outFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            L.e(e, "Gagal menyalin file dari assets")
        }
    }

    override fun onResume() {
        super.onResume()

        startService(
            Intent(this, AuxioService::class.java)
                .setAction(AuxioService.ACTION_START)
                .putExtra(AuxioService.INTENT_KEY_START_ID, IntegerTable.START_ID_ACTIVITY)
        )

        if (!startIntentAction(intent)) {
            playbackModel.playDeferred(DeferredPlayback.RestoreState(false))
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        startIntentAction(intent)
    }

    private fun setupTheme() {
        AppCompatDelegate.setDefaultNightMode(uiSettings.theme)
        if (isNight && uiSettings.useBlackTheme) {
            setTheme(uiSettings.accent.blackTheme)
        } else {
            setTheme(uiSettings.accent.theme)
        }
    }

    private fun setupEdgeToEdge(contentView: View) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        contentView.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.systemBarInsetsCompat
            view.updatePadding(left = bars.left, right = bars.right)
            insets
        }
    }

    private fun startIntentAction(intent: Intent?): Boolean {
        if (intent == null) return false

        if (intent.getBooleanExtra(KEY_INTENT_USED, false)) return true
        intent.putExtra(KEY_INTENT_USED, true)

        val action =
            when (intent.action) {
                Intent.ACTION_VIEW -> DeferredPlayback.Open(intent.data ?: return false)
                Auxio.INTENT_KEY_SHORTCUT_SHUFFLE -> DeferredPlayback.ShuffleAll
                else -> {
                    L.w("Unexpected intent ${intent.action}")
                    return false
                }
            }
        playbackModel.playDeferred(action)
        return true
    }

    private companion object {
        const val KEY_INTENT_USED = BuildConfig.APPLICATION_ID + ".key.FILE_INTENT_USED"
    }
}
