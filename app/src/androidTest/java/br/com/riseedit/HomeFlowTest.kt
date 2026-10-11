package br.com.riseedit

import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import br.com.riseedit.core.*
import java.io.File
import android.net.Uri

class HomeFlowTest {
    @Test fun newProjectReachesImportScreen() {
        val i=InstrumentationRegistry.getInstrumentation()
        val context=i.targetContext
        val source=File(context.cacheDir,"home-ui-source.mp4")
        i.context.assets.open("shaky.mp4").use {input ->source.outputStream().use {input.copyTo(it)}}
        val clip=Clip(uri=Uri.fromFile(source).toString(),name="Cena principal",sourceDurationMs=3000)
        val demo=Project(listOf(clip),name="Projeto de teste",texts=listOf(TextLayer(text="Minha história",endMs=2500)),
            audio=listOf(AudioClip(uri=clip.uri,name="Trilha",sourceDurationMs=3000,volume=0.2f)),
            overlays=listOf(VideoOverlay(clip=clip.copy(endMs=1500),offsetMs=500)))
        ProjectStore(context).save(demo)
        context.startActivity(context.packageManager.getLaunchIntentForPackage(context.packageName)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        fun find(node: AccessibilityNodeInfo?,text: String): AccessibilityNodeInfo? {
            if(node==null) return null
            if(node.text?.toString()?.contains(text)==true || node.contentDescription?.toString()?.contains(text)==true) return node
            for(index in 0 until node.childCount) find(node.getChild(index),text)?.let {return it}
            return null
        }
        fun scrollable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if(node==null) return null
            if(node.isScrollable && node.actionList.any {it.id==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD}) return node
            for(index in 0 until node.childCount) scrollable(node.getChild(index))?.let {return it}
            return null
        }
        fun screenshot(name: String) {
            val bitmap=i.uiAutomation.takeScreenshot();assertNotNull(bitmap)
            val file=File(context.getExternalFilesDir(null),name)
            file.outputStream().use {bitmap!!.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            i.uiAutomation.executeShellCommand("cp ${file.absolutePath} /sdcard/Download/$name").use {fd ->java.io.FileInputStream(fd.fileDescriptor).use {it.readBytes()}}
        }
        fun dump(node: AccessibilityNodeInfo?): String {
            if(node==null) return "<no active window>"
            return "[${node.text} / ${node.contentDescription}]" + (0 until node.childCount).joinToString {dump(node.getChild(it))}
        }
        fun waitFor(text: String,scroll: Boolean=false): AccessibilityNodeInfo {
            val deadline=System.currentTimeMillis()+10000
            while(System.currentTimeMillis()<deadline) {
                val root=i.uiAutomation.rootInActiveWindow
                // Recover only the emulator launcher ANR; never dismiss a Rise Edit error.
                if(find(root,"Pixel Launcher isn't responding")!=null) {
                    var close=find(root,"Close app")
                    while(close!=null && !close.isClickable) close=close.parent
                    close?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                find(root,text)?.let {return it}
                if(scroll) scrollable(root)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                Thread.sleep(200)
            }
            screenshot("home-failure-test.png")
            throw AssertionError("Missing UI: $text; ${dump(i.uiAutomation.rootInActiveWindow)}")
        }
        fun click(text: String,scroll: Boolean=false) {var node: AccessibilityNodeInfo?=waitFor(text,scroll);while(node!=null && !node.isClickable) node=node.parent;assertNotNull(node);assertTrue(node!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))}
        waitFor("RISE EDIT");screenshot("home-test.png");click("Novo Projeto");click("Criar e importar");waitFor("Importar vídeos");waitFor("Projetos")
        screenshot("editor-empty-test.png");click("Projetos");click("Projeto de teste",scroll=true);waitFor("Estabilização Rise")
        Thread.sleep(2000);screenshot("editor-timeline-test.png");click("Projetos")
    }
}
