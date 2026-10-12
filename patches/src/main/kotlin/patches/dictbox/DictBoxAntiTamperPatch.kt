package patches.dictbox

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.util.MethodUtil
import java.util.logging.Logger

@Suppress("unused")
val dictBoxAntiTamperPatch = bytecodePatch(
    name = "Bypass Anti-Tamper",
    description = "Bypasses the signature check library (libyrf.so) and routes text intents (Define/Translate) directly to definition lookup.",
    default = true,
) {
    category("Dict Box")
    compatibleWith(DictBoxConstants.COMPATIBILITY_DICTBOX)

    execute {
        val logger = Logger.getLogger(this::class.java.name)
        var antiTamperPatched = false
        var onResumePatched = false

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
                            antiTamperPatched = true
                            logger.info("Successfully neutered DictBoxApp.<clinit> to bypass anti-tamper.")
                        }
                    }
                }
            } else if (classDef.type == "Lcom/grandsons/dictbox/MainActivity;") {
                val mutableClass by lazy { mutableClassDefBy(classDef) }
                for (method in classDef.methods) {
                    if (method.name == "onResume" && method.returnType == "V" && method.parameterTypes.isEmpty()) {
                        val mutableMethod by lazy {
                            mutableClass.methods.first { MethodUtil.methodSignaturesMatch(it, method) }
                        }
                        val implementation = method.implementation ?: continue
                        val instructions = implementation.instructions.toList()

                        for ((index, instruction) in instructions.withIndex()) {
                            val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                                ?: continue

                            if (reference.definingClass == "Lcom/grandsons/dictbox/MainActivity;" &&
                                reference.name == "l2"
                            ) {
                                val startRange = (index - 15).coerceAtLeast(0)
                                for (guardIdx in startRange until index) {
                                    val guardIns = instructions[guardIdx]
                                    if (guardIns.opcode == Opcode.IF_EQZ) {
                                        mutableMethod.replaceInstruction(guardIdx, "nop")
                                        onResumePatched = true
                                    }
                                }
                                logger.info("Successfully hooked MainActivity.onResume to route text intents directly to l2.")
                                break
                            }
                        }
                    }
                }
            }
        }

        if (!antiTamperPatched) {
            logger.warning("Could not find DictBoxApp.<clinit> to apply anti-tamper patch.")
        }
        if (!onResumePatched) {
            logger.warning("Could not find l2 call guards in MainActivity.onResume.")
        }
    }
}
