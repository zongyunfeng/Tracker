package me.yunfeng.analytics_annotation_compiler

import com.google.auto.service.AutoService
import com.google.common.base.CaseFormat
import com.squareup.kotlinpoet.*
import me.eugeniomarletti.kotlin.metadata.KotlinClassMetadata
import me.eugeniomarletti.kotlin.metadata.KotlinMetadataUtils
import me.eugeniomarletti.kotlin.metadata.kotlinMetadata
import me.eugeniomarletti.kotlin.metadata.modality
import me.eugeniomarletti.kotlin.metadata.shadow.metadata.ProtoBuf
import me.eugeniomarletti.kotlin.processing.KotlinAbstractProcessor
import me.yunfeng.analytics_annotation.AnalyticsEvent
import java.io.File
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic

@AutoService(Processor::class)
class AnalyticsEventProcessor : KotlinAbstractProcessor(), KotlinMetadataUtils {
    companion object {
        private const val EVENT_PARAMETER_NAME = "event"
        private const val EVENT_NAME_PARAMETER_NAME = "name"
        private const val EVENT_PARAM_PARAMETER_NAME = "params"
        private const val LOG_EVENT_FUNCTION_NAME = "logEvent"

        private val ANNOTATION = AnalyticsEvent::class.java
        private val EVENT_TRACKER_CLASS = ClassName("me.yunfeng.eventsdk", "EventEmitter")
        private val BUNDLE_CLASS = ClassName("android.os", "Bundle")
        private val BUNDLE_OF_FUNCTION = ClassName("androidx.core.os", "bundleOf")
    }

    override fun getSupportedAnnotationTypes() = setOf(ANNOTATION.canonicalName)

    override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latestSupported()

    override fun process(
        annotations: Set<TypeElement>,
        roundEnv: RoundEnvironment
    ): Boolean {
        val outputDir = generatedDir
        return if (outputDir == null) {
            messager.printMessage(
                Diagnostic.Kind.ERROR,
                "找不到指定输出目录"
            )
            false
        } else {
            val annotatedElements = roundEnv.getElementsAnnotatedWith(ANNOTATION)

            for (annotatedElement in annotatedElements) {
                // 判断是不是sealed类
                val analyticsElement = getAnalyticsElement(annotatedElement) ?: continue

                // 获取所有内部声明的子类
                val declaredAnalyticsEvents = getAnalyticsEvents(analyticsElement)

                if (declaredAnalyticsEvents.isEmpty()) {
                    // 没有内部声明的子类就跳过
                    messager.printMessage(
                        Diagnostic.Kind.WARNING,
                        "$analyticsElement 没有内部类"
                    )
                    continue
                }

                // 生成代码
                generateCode(analyticsElement, declaredAnalyticsEvents, outputDir)
            }
            true
        }
    }

    private fun getAnalyticsElement(element: Element): TypeElement? {
        val kotlinMetadata = element.kotlinMetadata
        if (kotlinMetadata !is KotlinClassMetadata || element !is TypeElement) {
            messager.printMessage(
                Diagnostic.Kind.WARNING,
                "$element 不是kotlin类"
            )
            return null
        }
        val proto = kotlinMetadata.data.classProto
        if (proto.modality != ProtoBuf.Modality.SEALED) {
            // 不是sealed类
            messager.printMessage(
                Diagnostic.Kind.WARNING,
                "$element 不是kotlin sealed类"
            )
            return null
        }
        return element
    }

    private fun getAnalyticsEvents(
        analyticsElement: TypeElement
    ): Map<ClassName, List<String>> {
        val analyticsEvents = mutableMapOf<ClassName, List<String>>()

        val enclosedElements = analyticsElement.enclosedElements

        val supertype = analyticsElement.asType()

        for (element in enclosedElements) {

            val type = element.asType()

            if (element !is TypeElement) {
                messager.printMessage(
                    Diagnostic.Kind.WARNING,
                    "$element 不是类"
                )
                continue
            } else if (!typeUtils.directSupertypes(type).contains(supertype)) {
                // 内部类不是继承自外部包围的sealed类
                messager.printMessage(
                    Diagnostic.Kind.WARNING,
                    "$element 不是继承自 $analyticsElement."
                )
                continue
            }
            val kotlinMetadata = element.kotlinMetadata
                    as KotlinClassMetadata

            val eventClass = element.asClassName()

            //提取参数
            val proto = kotlinMetadata.data.classProto
            val nameResolver = kotlinMetadata.data.nameResolver

            if (proto.constructorCount == 0) {
                messager.printMessage(
                    Diagnostic.Kind.WARNING,
                    "$element 没有构造函数"
                )
                continue
            }

            val mainConstructor = proto.constructorList[0]
            val eventParameters = mainConstructor.valueParameterList
                .map { valueParameter ->
                    //解析参数名
                    nameResolver.getString(valueParameter.name)
                }

            analyticsEvents[eventClass] = eventParameters
        }
        return analyticsEvents
    }

    private fun generateCode(
        analyticsElement: TypeElement,
        analyticEvents: Map<ClassName, List<String>>,
        outputDir: File
    ) {
        val className = analyticsElement.asClassName()
        val extensionFunSpecBuilder = FunSpec.builder(LOG_EVENT_FUNCTION_NAME)
            .addKdoc(
                CodeBlock.builder()
                    .addStatement(
                        "转换[%T]成事件名跟参数，并且用[%T.%L]打印出来",
                        className,
                        EVENT_TRACKER_CLASS,
                        LOG_EVENT_FUNCTION_NAME
                    )
                    .addStatement("")
                    .addStatement("自动生成，请勿修改")
                    .build()
            )
            .receiver(EVENT_TRACKER_CLASS)
            .addParameter(EVENT_PARAMETER_NAME, className)
            .addStatement("val %L: %T", EVENT_NAME_PARAMETER_NAME, String::class)
            .addStatement("val %L: %T", EVENT_PARAM_PARAMETER_NAME, BUNDLE_CLASS)
            .beginControlFlow("when (%L)", EVENT_PARAMETER_NAME)

        for ((eventName, eventParamList) in analyticEvents) {
            val codeBlock = CodeBlock.builder()
                .apply {
                    addStatement("is %T -> {", eventName)
                    indent()
                    addStatement(
                        "%L = %S",
                        EVENT_NAME_PARAMETER_NAME,
                        eventName.simpleName.convertCase(
                            CaseFormat.UPPER_CAMEL,
                            CaseFormat.LOWER_UNDERSCORE
                        )
                    )
                    if (eventParamList.isNotEmpty()) {
                        addStatement("%L = %T(", EVENT_PARAM_PARAMETER_NAME, BUNDLE_OF_FUNCTION)
                        indent()
                        for ((index, parameter) in eventParamList.withIndex()) {
                            val size = eventParamList.size
                            val separator = if (index == size - 1) {
                                ""
                            } else {
                                ","
                            }
                            addStatement(
                                "%S to %L.%L%L",
                                parameter.convertCase(
                                    CaseFormat.LOWER_CAMEL,
                                    CaseFormat.LOWER_UNDERSCORE
                                ),
                                EVENT_PARAMETER_NAME,
                                parameter,
                                separator
                            )
                        }
                        unindent()
                        addStatement(")")
                    } else {
                        addStatement("%L = %T()", EVENT_PARAM_PARAMETER_NAME, BUNDLE_CLASS)
                    }
                }
                .unindent()
                .addStatement("}")
                .build()

            extensionFunSpecBuilder.addCode(codeBlock)
        }

        extensionFunSpecBuilder.endControlFlow()
            .addStatement(
                "%L(%L, %L)",
                LOG_EVENT_FUNCTION_NAME,
                EVENT_NAME_PARAMETER_NAME,
                EVENT_PARAM_PARAMETER_NAME
            )

        FileSpec.builder(className.packageName, className.simpleName)
            .addFunction(extensionFunSpecBuilder.build())
            .build()
            .writeTo(outputDir)

    }

}

private fun String.convertCase(
    fromCase: CaseFormat,
    toCase: CaseFormat
): String {
    return fromCase.to(toCase, this)
}
