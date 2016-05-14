package org.gradle.script.lang.kotlin

import com.intellij.openapi.util.JDOMUtil.loadDocument
import org.gradle.api.DefaultTask
import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.relocated.org.jdom.Document
import org.jetbrains.kotlin.relocated.org.jdom.Element
import java.io.File
import javax.inject.Inject

open class PatchIdeaConfig : DefaultTask() {

    override fun getGroup() = "IDE"

    override fun getDescription() =
        "Patches IDEA config files."

    @get:Inject
    open val classPathRegistry: ClassPathRegistry
        get() = throw NotImplementedError()

    val libraryName = "gradle-ide-support"

    @get:OutputFile
    val outputFile: File by lazy {
        project.file(".idea/libraries/${libraryName.replace('-', '_')}.xml")
    }

    @get:Input
    val classPath: List<File> by lazy {
        computeClassPath()
    }

    @TaskAction
    fun generate() {
        outputFile.writeText(
            prettyPrint(gradleIdeSupportLibrary()))

        val moduleFile = project.file(".idea/modules/${project.name}.iml")
        val module = loadDocument(moduleFile)
        patchIdeaModule(module)
        moduleFile.writeText(prettyPrint(module))
    }

    private fun patchIdeaModule(module: Document) {
        module.rootElement.getChild("component").addContent(
            // <orderEntry type="library" name="$LIBRARY_NAME" level="project" />
            Element("orderEntry").apply {
                setAttribute("type", "library")
                setAttribute("name", libraryName)
                setAttribute("level", "project")
            })
    }

    private fun gradleIdeSupportLibrary(): Document {
        /*
        <component name="libraryTable">
          <library name="$LIBRARY_NAME">
            <CLASSES>
              <root url="jar://$JAR!/" />
            </CLASSES>
            <JAVADOC />
            <SOURCES>
              <root url="jar://$JAR!/" />
            </SOURCES>
          </library>
        </component>
        */
        val classes = Element("CLASSES")
        val sources = Element("SOURCES")
        gradleJars().forEach { jar ->
            classes.addContent(rootElementFor(jar))
            sources.addContent(rootElementFor(jar))
        }

        val component = Element("component").apply {
            setAttribute("name", "libraryTable")
            addContent(Element("library").apply {
                setAttribute("name", libraryName)
                addContent(classes)
                addContent(Element("JAVADOC"))
                addContent(sources)
            })
        }
        return Document(component)
    }

    private fun gradleJars() =
        classPath.filter { it.name.startsWith("gradle-") && !it.name.contains("kotlin") }

    private fun rootElementFor(jar: File): Element {
        return Element("root").apply {
            setAttribute("url", "jar://${jar.absolutePath}!/")
        }
    }

    private fun computeClassPath() =
        KotlinScriptDefinitionProvider.selectGradleApiJars(classPathRegistry)
            .sortedBy { it.name }
}
