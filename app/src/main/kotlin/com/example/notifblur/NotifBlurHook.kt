package com.example.notifblur

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class NotifBlurHook : IXposedHookLoadPackage {

    private val CARD_ALPHA = 0.55f      // شفافية الكارت (1 = بدون تغيير)

    private val EDGE_ALPHA = 255        // لمعان الحافة (0-255)
    private val EDGE_DP = 1.2f          // سمك الحافة
    private val GLOW_DP = 4f            // عمق التوهج الداخلي
    private val FALLBACK_RADIUS_DP = 22f

    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()
    private val rect = RectF()

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.android.systemui") return
        val cl = lpparam.classLoader

        hookCards(cl)
        XposedBridge.log("NotifBlur: alpha + edge hooks installed")
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
                    param.args[0] = a * CARD_ALPHA
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
        val d = v.resources.displayMetrics.density
        val w = v.width.toFloat()
        val bottom = readInt(v, "mActualHeight", v.height).toFloat()
        val top = readInt(v, "mBackgroundTop", 0).toFloat()
        val sw = EDGE_DP * d
        val gw = GLOW_DP * d

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

        // توهج داخلي (3 طبقات)
        for (i in 0..2) {
            glow.strokeWidth = gw * 2f * (1f - i * 0.33f)
            glow.color = Color.argb(30 + i * 25, 255, 255, 255)
            canvas.drawPath(path, glow)
        }

        // حافة لامعة: ساطعة في الأركان وأهدى في النص
        val bright = Color.argb(EDGE_ALPHA, 255, 255, 255)
        val soft = Color.argb(EDGE_ALPHA / 3, 255, 255, 255)
        edge.shader = LinearGradient(
            0f, top, w, bottom,
            intArrayOf(bright, soft, bright),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        edge.strokeWidth = sw
        canvas.drawPath(path, edge)
    }

    private fun readInt(o: Any, name: String, def: Int): Int =
        try {
            XposedHelpers.getIntField(o, name)
        } catch (t: Throwable) {
            def
        }
}
