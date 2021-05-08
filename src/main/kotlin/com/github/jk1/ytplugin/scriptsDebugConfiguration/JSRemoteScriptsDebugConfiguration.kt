package com.github.jk1.ytplugin.scriptsDebugConfiguration

import com.github.jk1.ytplugin.ComponentAware
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configuration.EmptyRunProfileState
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.LocatableConfigurationBase
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.RunConfigurationWithSuppressedDefaultRunAction
import com.intellij.javascript.JSRunProfileWithCompileBeforeLaunchOption
import com.intellij.javascript.debugger.*
import com.intellij.javascript.debugger.execution.RemoteUrlMappingBean
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.util.InvalidDataException
import com.intellij.ui.IdeBorderFactory
import com.intellij.util.SmartList
import com.intellij.util.ui.FormBuilder
import com.intellij.util.xmlb.SkipEmptySerializationFilter
import com.intellij.util.xmlb.XmlSerializer
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.XCollection
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.jetbrains.debugger.wip.BrowserChromeDebugProcess
import org.apache.http.HttpHost
import org.apache.http.conn.HttpInetSocketAddress
import org.jdom.Element
import org.jetbrains.debugger.DebuggableRunConfiguration
import org.jetbrains.debugger.connection.VmConnection
import java.awt.BorderLayout
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URL
import javax.swing.JComponent
import javax.swing.JPanel


private const val DEFAULT_PORT = 9229
private val SERIALIZATION_FILTER = SkipEmptySerializationFilter()

class JSRemoteScriptsDebugConfiguration(project: Project, factory: ConfigurationFactory, name: String)
    : LocatableConfigurationBase<Element>(project, factory, name),
        RunConfigurationWithSuppressedDefaultRunAction,
        JSRunProfileWithCompileBeforeLaunchOption,
        DebuggableRunConfiguration {
    @Attribute
    var host: String? = null
        set(value) {
            field = value
        }

    @Attribute
    var port: Int = DEFAULT_PORT

    @Property(surroundWithTag = false)
    @XCollection
    var mappings: MutableList<RemoteUrlMappingBean> = SmartList()
        private set

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> {
        return WipRemoteDebugConfigurationSettingsEditor()
    }

    override fun getState(executor: Executor, env: ExecutionEnvironment): RunProfileState? {
        return EmptyRunProfileState.INSTANCE
    }

    override fun clone(): RunConfiguration {
        val configuration = super.clone() as JSRemoteScriptsDebugConfiguration
        configuration.host = host
        configuration.port = port
        configuration.mappings = SmartList(mappings)
        return configuration
    }

    @Throws(InvalidDataException::class)
    override fun readExternal(element: Element) {
        super<LocatableConfigurationBase>.readExternal(element)

        XmlSerializer.deserializeInto(this, element)
        if (port <= 0) {
            port = DEFAULT_PORT
        }
    }

    override fun writeExternal(element: Element) {
        super<LocatableConfigurationBase>.writeExternal(element)

        XmlSerializer.serializeInto(this, element, SERIALIZATION_FILTER)
    }

    override fun computeDebugAddress(state: RunProfileState): InetSocketAddress {
        return host?.let {
            HttpInetSocketAddress(HttpHost(it), InetAddress.getLoopbackAddress(), port)
        } ?: HttpInetSocketAddress(null, InetAddress.getLoopbackAddress(), port)
    }

    override fun createDebugProcess(socketAddress: InetSocketAddress,
                                    session: XDebugSession,
                                    executionResult: ExecutionResult?,
                                    environment: ExecutionEnvironment): XDebugProcess {
        val debugProcess: JavaScriptDebugProcess<VmConnection<*>> =
                createWipDebugProcess(socketAddress, session, executionResult)
        return debugProcess
    }

    private fun createWipDebugProcess(socketAddress: InetSocketAddress,
                                      session: XDebugSession,
                                      executionResult: ExecutionResult?): BrowserChromeDebugProcess {
        val connection = WipConnection()
        val finder = RemoteDebuggingFileFinder(createUrlToLocalMap(mappings), LocalFileSystemFileFinder())

        // TODO process should be NodeChromeDebugProcess depending on PageConnection.type
        val process = BrowserChromeDebugProcess(session, finder, connection, executionResult)
        connection.open(socketAddress)
        return process
    }

    private inner class WipRemoteDebugConfigurationSettingsEditor : SettingsEditor<JSRemoteScriptsDebugConfiguration>() {
        private val filesMappingPanel: ScriptsLocalFilesMappingPanel

        init {
            filesMappingPanel = object : ScriptsLocalFilesMappingPanel(project, BorderLayout()) {
                override fun initUI() {
                    add(mappingTreePanel)
                    super.initUI()
                }
            }
        }

        override fun resetEditorFrom(configuration: JSRemoteScriptsDebugConfiguration) {
            filesMappingPanel.resetEditorFrom(configuration.mappings, true)
        }

        override fun applyEditorTo(configuration: JSRemoteScriptsDebugConfiguration) {
            val repositories = ComponentAware.of(project).taskManagerComponent.getAllConfiguredYouTrackRepositories()
            if (repositories.isNotEmpty()) {
                configuration.host = URL(repositories[0].url).host
                configuration.port = URL(repositories[0].url).port
            }
            filesMappingPanel.applyEditorTo(configuration)
        }

        override fun createEditor(): JComponent {
            val protocolPanel = JPanel(VerticalFlowLayout())
            filesMappingPanel.initUI()
            return FormBuilder.createFormBuilder()
                    .addComponent(protocolPanel, IdeBorderFactory.TITLED_BORDER_TOP_INSET)
                    .panel
        }
    }

}