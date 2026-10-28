package app.getyoink.mobile

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import android.graphics.Color

class MainActivity : TauriActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(Color.rgb(29, 32, 33))
    )
    super.onCreate(savedInstanceState)
  }
}
