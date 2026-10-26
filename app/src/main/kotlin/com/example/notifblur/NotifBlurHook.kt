package com.example.notifblur

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class NotifBlurHook : IXposedHookLoadPackage {

    private val MAX_RADIUS = 90f
    private var logCount = 0
    private var lastLogged = -1

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.android.systemui") return

        val blurUtils = XposedHelpers.findClassIfExists(
            "com.android.systemui.statusbar.BlurUtils", lpparam.classLoader
        )
        if (blurUtils == null) {
            XposedBridge.log("NotifBlur: BlurUtils not found")
            return
        }

        XposedBridge.hookAllMethods(
            blurUtils, "supportsBlursOnWindows",
            XC_MethodReplacement.returnConstant(true)
        )

        XposedBridge.hookAllMethods(blurUtils, "blurRadiusOfRatio", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val ratio = (param.args[0] as? Float ?: return).coerceIn(0f, 1f)
                val wanted = ratio * MAX_RADIUS
                val r = param.result
                if (r is Float) {
                    if (r < wanted) {
                        param.result = wanted
                    }
                } else if (r is Int) {
                    if (r < wanted) {
                        param.result = wanted.toInt()
                    }
                }
            }
        })

        XposedBridge.hookAllMethods(blurUtils, "applyBlur", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val radius = param.args.firstOrNull { it is Int } as? Int ?: return
                if (radius != lastLogged && logCount < 40) {
                    lastLogged = radius
                    logCount++
                    XposedBridge.log("NotifBlur: applyBlur radius=$radius")
                }
            }
        })

        XposedBridge.log("NotifBlur: v2 hooks installed")
    }
}