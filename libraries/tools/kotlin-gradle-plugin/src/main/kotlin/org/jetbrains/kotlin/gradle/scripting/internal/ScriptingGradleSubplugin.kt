/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.scripting.internal

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.compile.AbstractCompile
import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.gradle.scripting.ScriptingExtension
import org.jetbrains.kotlin.gradle.tasks.GradleMessageCollector
import org.jetbrains.kotlin.script.KotlinScriptDefinition
import org.jetbrains.kotlin.scripting.compiler.plugin.ScriptDefinitionsFromClasspathDiscoverySource

class ScriptingGradleSubplugin : Plugin<Project> {
    companion object {
        fun isEnabled(project: Project) = project.plugins.findPlugin(ScriptingGradleSubplugin::class.java) != null
    }

    override fun apply(project: Project) {

        project.afterEvaluate {
            val javaPluginConvention = project.convention.findPlugin(JavaPluginConvention::class.java)
            val messageCollector = GradleMessageCollector(project.logger)
            if (javaPluginConvention?.sourceSets?.isEmpty() == false) {
                for (sourceSet: SourceSet in javaPluginConvention.sourceSets) {
                    if (sourceSet.name == "main" || sourceSet.name == "test") {
                        val classpath = sourceSet.runtimeClasspath.files.toList()
                        val definitions = ScriptDefinitionsFromClasspathDiscoverySource(classpath, emptyMap(), messageCollector).definitions
                        val kotlinSourceSet = sourceSet.getConvention(KOTLIN_DSL_NAME) as? KotlinSourceSet
                        if (kotlinSourceSet == null) {
                            project.logger.warn("kotlin scripting plugin: kotlin source set not found: $project.$sourceSet")
                            continue
                        }
                        val extensions = definitions.mapTo(arrayListOf(), KotlinScriptDefinition::fileExtension)
                        if (extensions.isNotEmpty()) {
                            project.logger.info("kotlin scripting plugin: Add new extensions to the sourceset $project.$sourceSet: $extensions")
                            kotlinSourceSet.sourceFilesExtensions(*extensions.toTypedArray())
                        }
                    }
                }
            } else {
                project.logger.warn("kotlin scripting plugin: applied to a non-JVM project $project")
            }
        }
    }
}

class ScriptingKotlinGradleSubplugin : KotlinGradleSubplugin<AbstractCompile> {
    companion object {
        const val SCRIPTING_ARTIFACT_NAME = "kotlin-scripting-compiler-embeddable"

        val SCRIPT_DEFINITIONS_OPTION = "script-definitions"
        val SCRIPT_DEFINITIONS_CLASSPATH_OPTION = "script-definitions-classpath"
        val DISABLE_SCRIPT_DEFINITIONS_FROM_CLSSPATH_OPTION = "disable-script-definitions-from-classpath"
        val LEGACY_SCRIPT_RESOLVER_ENVIRONMENT_OPTION = "script-resolver-environment"
    }

    override fun isApplicable(project: Project, task: AbstractCompile) =
        ScriptingGradleSubplugin.isEnabled(project)

    override fun apply(
        project: Project,
        kotlinCompile: AbstractCompile,
        javaCompile: AbstractCompile?,
        variantData: Any?,
        androidProjectHandler: Any?,
        kotlinCompilation: KotlinCompilation?
    ): List<SubpluginOption> {
        if (!ScriptingGradleSubplugin.isEnabled(project)) return emptyList()

        val scriptingExtension = project.extensions.findByType(ScriptingExtension::class.java)
                ?: project.extensions.create("kotlinScripting", ScriptingExtension::class.java)

        val options = mutableListOf<SubpluginOption>()

        for (scriptDef in scriptingExtension.myScriptDefinitions) {
            options += SubpluginOption(SCRIPT_DEFINITIONS_OPTION, scriptDef)
        }
        for (path in scriptingExtension.myScriptDefinitionsClasspath) {
            options += SubpluginOption(SCRIPT_DEFINITIONS_CLASSPATH_OPTION, path)
        }
        if (scriptingExtension.myDisableScriptDefinitionsFromClasspath) {
            options += SubpluginOption(DISABLE_SCRIPT_DEFINITIONS_FROM_CLSSPATH_OPTION, "true")
        }
        for (pair in scriptingExtension.myScriptResolverEnvironment) {
            options += SubpluginOption(LEGACY_SCRIPT_RESOLVER_ENVIRONMENT_OPTION, "${pair.key}=${pair.value}")
        }

        return options
    }

    override fun getCompilerPluginId() = "kotlin.scripting"
    override fun getPluginArtifact(): SubpluginArtifact =
        JetBrainsSubpluginArtifact(artifactId = SCRIPTING_ARTIFACT_NAME)
}
