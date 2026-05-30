package app.vitune.core.ui.utils

import android.content.res.Configuration
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration

val isLandscape
    @Composable
    @ReadOnlyComposable
    get() = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

@get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.M)
inline val isAtLeastAndroid6
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M

@get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.N)
inline val isAtLeastAndroid7
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

@get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
inline val isAtLeastAndroid8
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

@get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.P)
inline val isAtLeastAndroid9
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

@get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q)
inline val isAtLeastAndroid10
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

@get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
inline val isAtLeastAndroid11
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

@get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
inline val isAtLeastAndroid12
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
inline val isAtLeastAndroid13
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

@get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.CINNAMON_BUN)
inline val isAtLeastAndroid17
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN

@Composable
fun isCompositionLaunched(): Boolean {
    var isLaunched by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isLaunched = true
    }
    return isLaunched
}
