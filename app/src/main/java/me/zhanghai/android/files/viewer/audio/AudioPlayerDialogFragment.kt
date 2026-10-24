/*
 * Copyright (c) 2026 OpenManager
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.audio

import android.app.Dialog
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.graphics.Color
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java8.nio.file.Path
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import me.zhanghai.android.files.R
import me.zhanghai.android.files.provider.common.newInputStream
import me.zhanghai.android.files.provider.linux.isLinuxPath
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.ParcelableParceler
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.show
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class AudioPlayerDialogFragment : AppCompatDialogFragment() {
    private val args by args<Args>()

    private var mediaPlayer: MediaPlayer? = null
    private var tempFile: File? = null
    private var isPlaying = false
    private var isLooping = false
    private var speedIndex = 0
    private val speeds = floatArrayOf(1.0f, 1.25f, 1.5f, 2.0f, 0.75f)

    private val handler = Handler(Looper.getMainLooper())
    private var isUserTracking = false

    private lateinit var tvTitle: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var tvElapsed: TextView
    private lateinit var tvDuration: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var ivPlayPause: ImageView
    private lateinit var btnLoop: ImageButton
    private lateinit var btnSpeed: TextView

    private val progressRunnable = object : Runnable {
        override fun run() {
            mediaPlayer?.let { player ->
                if (player.isPlaying && !isUserTracking) {
                    val current = player.currentPosition
                    seekBar.progress = current
                    tvElapsed.text = formatTime(current)
                }
                handler.postDelayed(this, 250)
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_audio_player, null)

        tvTitle = view.findViewById(R.id.tvAudioTitle)
        tvSubtitle = view.findViewById(R.id.tvAudioSubtitle)
        tvElapsed = view.findViewById(R.id.tvAudioElapsed)
        tvDuration = view.findViewById(R.id.tvAudioDuration)
        seekBar = view.findViewById(R.id.seekBarAudio)
        ivPlayPause = view.findViewById(R.id.ivAudioPlayPause)
        btnLoop = view.findViewById(R.id.btnAudioLoop)
        btnSpeed = view.findViewById(R.id.btnAudioSpeed)

        val btnClose = view.findViewById<ImageButton>(R.id.btnAudioClose)
        val btnRewind = view.findViewById<ImageButton>(R.id.btnAudioRewind)
        val btnForward = view.findViewById<ImageButton>(R.id.btnAudioForward)
        val btnPlayPause = view.findViewById<FrameLayout>(R.id.btnAudioPlayPause)

        val fileName = args.path.fileName.toString()
        tvTitle.text = fileName
        tvTitle.isSelected = true
        val ext = fileName.substringAfterLast('.', "AUDIO").uppercase(Locale.ROOT)
        tvSubtitle.text = "$ext Audio Player"

        btnClose.setOnClickListener { dismiss() }

        btnPlayPause.setOnClickListener {
            togglePlayPause()
        }

        btnRewind.setOnClickListener {
            mediaPlayer?.let { player ->
                val newPos = (player.currentPosition - 10000).coerceAtLeast(0)
                player.seekTo(newPos)
                seekBar.progress = newPos
                tvElapsed.text = formatTime(newPos)
            }
        }

        btnForward.setOnClickListener {
            mediaPlayer?.let { player ->
                val newPos = (player.currentPosition + 10000).coerceAtMost(player.duration)
                player.seekTo(newPos)
                seekBar.progress = newPos
                tvElapsed.text = formatTime(newPos)
            }
        }

        btnLoop.setOnClickListener {
            isLooping = !isLooping
            mediaPlayer?.isLooping = isLooping
            val tint = if (isLooping) Color.parseColor("#2196F3") else Color.parseColor("#666666")
            btnLoop.imageTintList = ColorStateList.valueOf(tint)
        }

        btnSpeed.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                speedIndex = (speedIndex + 1) % speeds.size
                val speed = speeds[speedIndex]
                btnSpeed.text = "${speed}x"
                mediaPlayer?.let { player ->
                    try {
                        val params = player.playbackParams
                        params.speed = speed
                        player.playbackParams = params
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    tvElapsed.text = formatTime(progress)
                }
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {
                isUserTracking = true
            }

            override fun onStopTrackingTouch(sb: SeekBar?) {
                isUserTracking = false
                sb?.let {
                    mediaPlayer?.seekTo(it.progress)
                }
            }
        })

        initMediaPlayer()

        return MaterialAlertDialogBuilder(context)
            .setView(view)
            .create()
    }

    private fun initMediaPlayer() {
        val path = args.path
        val context = requireContext()
        val player = MediaPlayer()
        mediaPlayer = player

        try {
            val audioFile: File
            if (path.isLinuxPath) {
                audioFile = path.toFile()
            } else {
                val temp = File(context.cacheDir, "audio_preview_${System.currentTimeMillis()}.${path.fileName.toString().substringAfterLast('.', "tmp")}")
                path.newInputStream().use { input ->
                    FileOutputStream(temp).use { output ->
                        input.copyTo(output)
                    }
                }
                tempFile = temp
                audioFile = temp
            }

            player.setDataSource(audioFile.absolutePath)
            player.setOnPreparedListener { mp ->
                val duration = mp.duration
                seekBar.max = duration
                tvDuration.text = formatTime(duration)
                mp.start()
                isPlaying = true
                ivPlayPause.setImageResource(R.drawable.ic_pause_24dp)
                handler.post(progressRunnable)
            }
            player.setOnCompletionListener {
                if (!isLooping) {
                    isPlaying = false
                    ivPlayPause.setImageResource(R.drawable.ic_play_arrow_24dp)
                    seekBar.progress = 0
                    tvElapsed.text = formatTime(0)
                }
            }
            player.setOnErrorListener { _, what, extra ->
                Toast.makeText(context, "Error al reproducir audio ($what, $extra)", Toast.LENGTH_SHORT).show()
                dismiss()
                true
            }
            player.prepareAsync()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "No se pudo abrir el audio: ${e.message}", Toast.LENGTH_SHORT).show()
            dismiss()
        }
    }

    private fun togglePlayPause() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                isPlaying = false
                ivPlayPause.setImageResource(R.drawable.ic_play_arrow_24dp)
            } else {
                player.start()
                isPlaying = true
                ivPlayPause.setImageResource(R.drawable.ic_pause_24dp)
                handler.post(progressRunnable)
            }
        }
    }

    private fun formatTime(millis: Int): String {
        val totalSec = millis / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format(Locale.ROOT, "%02d:%02d", min, sec)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        releasePlayer()
        if (activity is AudioPlayerActivity) {
            activity?.finish()
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        releasePlayer()
        if (activity is AudioPlayerActivity) {
            activity?.finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
    }

    private fun releasePlayer() {
        handler.removeCallbacksAndMessages(null)
        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaPlayer = null
        tempFile?.delete()
        tempFile = null
    }

    companion object {
        fun show(path: Path, fragmentManager: FragmentManager) {
            AudioPlayerDialogFragment().putArgs(Args(path)).show(fragmentManager, null)
        }
    }

    @Parcelize
    class Args(val path: @WriteWith<ParcelableParceler> Path) : ParcelableArgs
}
