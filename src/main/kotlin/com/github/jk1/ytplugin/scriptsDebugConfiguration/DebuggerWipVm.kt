package com.github.jk1.ytplugin.scriptsDebugConfiguration


import com.github.jk1.ytplugin.logger
import com.github.jk1.ytplugin.timeTracker.TrackerNotification
import com.intellij.notification.NotificationType
import com.intellij.util.io.readUtf8
import io.netty.channel.Channel
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import org.jetbrains.debugger.DebugEventListener
import org.jetbrains.debugger.MessagingLogger
import org.jetbrains.io.JsonReaderEx
import org.jetbrains.wip.node.NodeWipWorkerManager

class DebuggerWipVm(tabListener: DebugEventListener,
                    url: String?,
                    channel: Channel,
                    debugMessageQueue: MessagingLogger? = null)
    : StandaloneDebuggerWipVm(tabListener, url, channel, debugMessageQueue, workerManagerFactory = ::NodeWipWorkerManager) {

    init {
        // in browser ExecutionContextDestroyed is dispatched on page reload, we don't want to disconnect in this case
        this.commandProcessor.eventMap.add(org.jetbrains.wip.protocol.runtime.ExecutionContextDestroyedEventData.TYPE) {
            if (it.executionContextId == 1) {
                attachStateManager.detach()
            }
        }
    }

    override fun initDomains() {
        super.initDomains()
        commandProcessor.send(org.jetbrains.wip.protocol.runtime.Enable())
        enableWorkers()
    }


    override fun textFrameReceived(message: TextWebSocketFrame) {
        debugMessageQueue?.add(message.content(), "IN")
        try {
            commandProcessor.processIncomingJson(JsonReaderEx(message.content().readUtf8()))
        }
        // case required to catch exception on repeating id when breakpoint is removed on timer expire
        catch (e: IllegalArgumentException) {
            logger.debug("Execution was continued explicitly on timer expire")
            val note = "Execution could not be paused on breakpoint for longer than three minutes"
            val trackerNote = TrackerNotification()
            trackerNote.notify(note, NotificationType.INFORMATION)
        }
        finally {
            message.release()
        }
    }

}