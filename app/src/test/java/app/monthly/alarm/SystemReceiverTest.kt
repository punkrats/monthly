package app.monthly.alarm

import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.monthly.MonthlyApp
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.w3c.dom.Element

@RunWith(AndroidJUnit4::class)
class SystemReceiverTest {

  private val app: MonthlyApp = ApplicationProvider.getApplicationContext()

  /** Delivery runs on its own thread; polls until it marks the check. */
  private fun delivered(within: Long): Boolean {
    val deadline = System.currentTimeMillis() + within
    while (System.currentTimeMillis() < deadline) {
      if (app.settings.lastCheck != null) return true
      Thread.sleep(20)
    }
    return false
  }

  /** Opening SQLite the first time is slow; keep it off the timed part. */
  @Before
  fun openDatabase() {
    app.repository.snapshot()
  }

  /** Actions of the SystemReceiver filter in the app manifest. */
  private fun manifestActions(): Set<String> {
    val ns = "http://schemas.android.com/apk/res/android"
    val factory = DocumentBuilderFactory.newInstance()
    factory.isNamespaceAware = true
    val manifest = File("src/main/AndroidManifest.xml")
    val receivers = factory.newDocumentBuilder().parse(manifest)
      .getElementsByTagName("receiver")
    val receiver = (0 until receivers.length)
      .map { receivers.item(it) as Element }
      .single { it.getAttributeNS(ns, "name") == ".alarm.SystemReceiver" }
    val actions = receiver.getElementsByTagName("action")
    return (0 until actions.length)
      .map { (actions.item(it) as Element).getAttributeNS(ns, "name") }
      .toSet()
  }

  @Test
  fun acceptsExactlyTheManifestActions() {
    assertEquals(manifestActions(), SystemReceiver.ACTIONS)
  }

  @Test
  fun deliversOnASystemBroadcast() {
    app.sendBroadcast(
      Intent(Intent.ACTION_TIME_CHANGED).setPackage(app.packageName)
    )
    shadowOf(Looper.getMainLooper()).idle()

    assertTrue(delivered(within = 5_000))
  }

  @Test
  fun ignoresAnExplicitIntentWithAnotherAction() {
    SystemReceiver().onReceive(app, Intent("app.monthly.SPOOF"))

    assertFalse(delivered(within = 1_000))
  }

  @Test
  fun ignoresAnIntentWithoutAction() {
    SystemReceiver().onReceive(app, Intent())

    assertFalse(delivered(within = 1_000))
  }
}
