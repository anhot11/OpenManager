/*
 * Copyright (c) 2026 OpenManager
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.commit
import java8.nio.file.Path
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.util.extraPath
import me.zhanghai.android.files.util.putArgs

class AudioPlayerActivity : AppActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        findViewById<View>(android.R.id.content)
        if (savedInstanceState == null) {
            val path = intent.extraPath ?: run {
                finish()
                return
            }
            val fragment = AudioPlayerDialogFragment().putArgs(AudioPlayerDialogFragment.Args(path))
            supportFragmentManager.commit {
                add(fragment, AudioPlayerDialogFragment::class.java.name)
            }
        }
    }

    companion object {
        fun start(context: Context, path: Path) {
            val intent = Intent(context, AudioPlayerActivity::class.java).apply {
                extraPath = path
            }
            context.startActivity(intent)
        }
    }
}
