package com.learn.profile

import android.content.Context
import android.content.Intent
import com.learn.media.MediaActivity
import com.learn.media.export.Media

object ProfileTalk {
    fun profile() {
        Media.toast()
    }

    fun toMedia(context: Context) {
        context.startActivity(Intent(context, MediaActivity::class.java))
    }
}