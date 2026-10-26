package com.example.notifblur

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

object Prefs {
    const val PKG = "com.example.notifblur"
    const val FILE = "settings"

    const val ALPHA_ON = "alpha_on"
    const val ALPHA_PCT = "alpha_pct"
    const val EDGE_ON = "edge_on"
    const val EDGE_ALPHA = "edge_alpha"