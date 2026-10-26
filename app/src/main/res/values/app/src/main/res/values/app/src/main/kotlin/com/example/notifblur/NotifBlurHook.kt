package com.example.notifblur

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class NotifBlurHook : IXposedHookLoadPackage {

    private val MIN_RADIUS = 60

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.android.systemui") return

        val cl = lpparam.classLoader

        val blurUtils = XposedHelpers.findClassIfExists(
            "com.android.systemui.statusbar.BlurUtils", cl
        )

        if (blurUtils == null) {
            XposedBridge.log("NotifBlur: BlurUtils not found - class name differs on XOS 16")
            return
        }

        blurUtils.declaredMethods.forEach {
            XposedBridge.log("NotifBlur: BlurUtils.${it.name}(${it.parameterTypes.joinToString { p -> p.simpleName }})")
        }

        XposedBridge.hookAllMethods(blurUtils, "applyBlur", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val idx = param.args.indexOfFirst { it is Int }
                if (idx == -1) return
                val radius = param.args[idx] as Int
                if (radius in 1 until MIN_RADIUS) {
                    param.args[idx] = MIN_RADIUS
                }
            }
        })

        XposedBridge.log("NotifBlur: hooks installed")
    }
}
