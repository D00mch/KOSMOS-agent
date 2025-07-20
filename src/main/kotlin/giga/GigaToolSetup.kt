package com.dumch.giga

import com.dumch.giga.GigaRequest.Property
import com.dumch.tool.InputParamDescription
import com.dumch.tool.ToolSetup
import java.util.HashMap
import kotlin.reflect.KCallable
import kotlin.reflect.full.declaredMembers
import kotlin.reflect.full.findAnnotation


inline fun <reified Input> ToolSetup<Input>.toGiga(): GigaRequest.Function {
    val toolSetup = this
    return GigaRequest.Function(
        name = toolSetup.name,
        description = toolSetup.description,
        parameters = GigaRequest.Parameters(
            "object",
            properties = HashMap<String, Property>().apply {
                val clazz = Input::class
                for (kProperty: KCallable<*> in clazz.declaredMembers) {
                    val annotation = kProperty.findAnnotation<InputParamDescription>() ?: continue
                    val description = annotation.value
                    val type = kProperty.returnType.toString().substringAfterLast(".").lowercase()
                    val gigaProperty = GigaRequest.Property(type, description)
                    put(kProperty.name, gigaProperty)
                }
            }
        )
    )
}
