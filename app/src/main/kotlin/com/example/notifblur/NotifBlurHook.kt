package com.example.notifblur

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class NotifBlurHook : IXposedHookLoadPackage {

    private val FALLBACK_RADIUS_DP = 22f

    private var prefs: XSharedPreferences? = null
    @Volatile private var lastCheck = 0L

    @Volatile private var alphaOn = Prefs.DEF_ALPHA_ON
    @Volatile private var alphaPct = Prefs.DEF_ALPHA_PCT
    @Volatile private var edgeOn = Prefs.DEF_EDGE_ON
    @Volatile private var edgeAlpha = Prefs.DEF_EDGE_ALPHA
    @Volatile private var edgeDp10 = Prefs.DEF_EDGE_DP10
    @Volatile private var glowOn = Prefs.DEF_GLOW_ON
    @Volatile private var glowDp = Prefs.DEF_GLOW_DP
    @Volatile private var color = Prefs.DEF_COLOR

    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()
    private val rect = RectF()

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.android.systemui") return

        prefs = XSharedPreferences(Prefs.PKG, Prefs.FILE)
        refresh(true)

        hookCards(lpparam.classLoader)
        XposedBridge.log("NotifBlur: v7 alpha + edge hooks installed")
    }

    // قراءة الإعدادات: مرة كل ثانية على الأكتر
    private fun refresh(force: Boolean = false) {
        val p = prefs ?: return
        val now = SystemClock.uptimeMillis()
        if (!force && now - lastCheck < 1000L) return
        lastCheck = now
        try {
            if (p.hasFileChanged()) p.reload()
            alphaOn = p.getBoolean(Prefs.ALPHA_ON, Prefs.DEF_ALPHA_ON)
            alphaPct = p.getInt(Prefs.ALPHA_PCT, Prefs.DEF_ALPHA_PCT)
            edgeOn = p.getBoolean(Prefs.EDGE_ON, Prefs.DEF_EDGE_ON)
            edgeAlpha = p.getInt(Prefs.EDGE_ALPHA, Prefs.DEF_EDGE_ALPHA)
            edgeDp10 = p.getInt(Prefs.EDGE_DP10, Prefs.DEF_EDGE_DP10)
            glowOn = p.getBoolean(Prefs.GLOW_ON, Prefs.DEF_GLOW_ON)
            glowDp = p.getInt(Prefs.GLOW_DP, Prefs.DEF_GLOW_DP)
            color = p.getInt(Prefs.COLOR, Prefs.DEF_COLOR)
        } catch (t: Throwable) {
            XposedBridge.log("NotifBlur: prefs error $t")
        }
    }

    private fun hookCards(cl: ClassLoader) {
        val bg = XposedHelpers.findClassIfExists(
            "com.android.systemui.statusbar.notification.row.NotificationBackgroundView", cl
        )
        if (bg == null) {
            XposedBridge.log("NotifBlur: NotificationBackgroundView not found")
            return
        }

        XposedHelpers.findAndHookMethod(
            View::class.java, "setAlpha", Float::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!bg.isInstance(param.thisObject)) return
                    val a = param.args[0] as? Float ?: return
                    refresh()
                    if (alphaOn) param.args[0] = a * (alphaPct / 100f)
                }
            }
        )

        XposedBridge.hookAllConstructors(bg, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                (param.thisObject as? View)?.alpha = 1f
            }
        })

        XposedBridge.hookAllMethods(bg, "onDraw", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                try {
                    drawEdge(param.thisObject as View, param.args[0] as Canvas)
                } catch (t: Throwable) {
                    XposedBridge.log("NotifBlur: edge error $t")
                }
            }
        })

        XposedBridge.log("NotifBlur: card hook installed")
    }

    private fun drawEdge(v: View, canvas: Canvas) {
        refresh()
        if (!edgeOn && !glowOn) return

        val d = v.resources.displayMetrics.density
        val w = v.width.toFloat()
        val bottom = readInt(v, "mActualHeight", v.height).toFloat()
        val top = readInt(v, "mBackgroundTop", 0).toFloat()
        val sw = (edgeDp10 / 10f) * d
        val gw = glowDp * d

        rect.set(sw / 2f, top + sw / 2f, w - sw / 2f, bottom - sw / 2f)
        if (rect.width() <= 0f || rect.height() <= 0f) return

        val raw: FloatArray? = try {
            XposedHelpers.getObjectField(v, "mCornerRadii") as? FloatArray
        } catch (t: Throwable) {
            null
        }
        val fb = FALLBACK_RADIUS_DP * d
        val radii: FloatArray =
            if (raw == null || raw.size < 8 || raw.all { it <= 0f }) FloatArray(8) { fb } else raw

        path.reset()
        path.addRoundRect(rect, radii, Path.Direction.CW)

        if (glowOn && gw > 0f) {
            for (i in 0..2) {
                glow.strokeWidth = gw * 2f * (1f - i * 0.33f)
                glow.color = Prefs.withAlpha(color, 30 + i * 25)
                canvas.drawPath(path, glow)
            }
        }

        if (edgeOn && sw > 0f) {
            val bright = Prefs.withAlpha(color, edgeAlpha)
            val soft = Prefs.withAlpha(color, edgeAlpha / 3)
            edge.shader = LinearGradient(
                0f, top, w, bottom,
                intArrayOf(bright, soft, bright),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            edge.strokeWidth = sw
            canvas.drawPath(path, edge)
        }
    }

    private fun readInt(o: Any, name: String, def: Int): Int =
        try {
            XposedHelpers.getIntField(o, name)
        } catch (t: Throwable) {
            def
        }
}
