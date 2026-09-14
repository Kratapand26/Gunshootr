package patches.dictbox

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.util.MethodUtil
import java.util.logging.Logger

@Suppress("unused")
val dictBoxAntiTamperPatch = bytecodePatch(
    name = "Bypass Anti-Tamper",
    description = "Bypasses the signature check library (libyrf.so) that crashes the app upon modification by Morphe.",
    default = true,
) {
    category("Dict Box")
    compatibleWith("com.grandsons.dictsharp")

    execute {
        val logger = Logger.getLogger(this::class.java.name)
        var patched = false

        classDefForEach { classDef ->
            if (classDef.type == "Lcom/grandsons/dictbox/DictBoxApp;") {
                val mutableClass by lazy { mutableClassDefBy(classDef) }
                for (method in classDef.methods) {
                    if (method.name == "<clinit>") {
                        val mutableMethod by lazy {
                            mutableClass.methods.first { MethodUtil.methodSignaturesMatch(it, method) }
                        }
                        val implementation = method.implementation ?: continue
                        val instructions = implementation.instructions.toList()

                        if (instructions.isNotEmpty()) {
                            mutableMethod.replaceInstruction(0, "return-void")
                            for (i in 1 until instructions.size) {
                                mutableMethod.replaceInstruction(i, "nop")
                            }
                            patched = true
                            logger.info("Successfully neutered DictBoxApp.<clinit> to bypass anti-tamper.")
                        }
                    }
                }
            }
        }

        if (!patched) {
            logger.warning("Could not find DictBoxApp.<clinit> to apply anti-tamper patch.")
        }
    }
}
