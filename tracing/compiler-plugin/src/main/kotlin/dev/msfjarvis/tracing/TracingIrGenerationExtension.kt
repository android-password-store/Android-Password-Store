package dev.msfjarvis.tracing

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.types.isNullableAny
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

public class TracingIrGenerationExtension : IrGenerationExtension {

  override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
    val debugLogAnnotation =
      pluginContext.referenceClass(
        ClassId(FqName("dev.msfjarvis.tracing.runtime.annotations"), Name.identifier("DebugLog"))
      )!!
    val funPrintln =
      pluginContext
        .referenceFunctions(CallableId(FqName("kotlin.io"), Name.identifier("println")))
        .first {
          val parameters = it.owner.valueParameters
          parameters.size == 1 && parameters[0].type.isNullableAny()
        }
    moduleFragment.transform(
      DebugLogTransformer(pluginContext, debugLogAnnotation, funPrintln),
      null
    )
  }
}
