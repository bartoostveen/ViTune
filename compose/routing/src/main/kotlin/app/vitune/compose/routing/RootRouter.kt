package app.vitune.compose.routing

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

typealias TransitionScope<T> = AnimatedContentTransitionScope<T>
typealias TransitionSpec<T> = TransitionScope<T>.() -> ContentTransform

private val defaultTransitionSpec: TransitionSpec<Route?> = {
    when {
        isStacking -> defaultStacking
        isStill -> defaultStill
        else -> defaultUnstacking
    }
}

@Composable
fun RouteHandler(
    modifier: Modifier = Modifier,
    listenToGlobalEmitter: Boolean = true,
    transitionSpec: TransitionSpec<Route?> = defaultTransitionSpec,
    content: @Composable RouteHandlerScope.() -> Unit
) {
    var child: Route? by rememberSaveable { mutableStateOf(null) }

    RouteHandler(
        child = child,
        setChild = { child = it },
        listenToGlobalEmitter = listenToGlobalEmitter,
        transitionSpec = transitionSpec,
        modifier = modifier,
        content = content
    )
}

interface Router {
    operator fun Route0.invoke()
    operator fun <P0> Route1<P0>.invoke(p0: P0)
    operator fun <P0, P1> Route2<P0, P1>.invoke(p0: P0, p1: P1)
    operator fun <P0, P1, P2> Route3<P0, P1, P2>.invoke(p0: P0, p1: P1, p2: P2)
    operator fun <P0, P1, P2, P3> Route4<P0, P1, P2, P3>.invoke(p0: P0, p1: P1, p2: P2, p3: P3)

    val pop: () -> Unit
    val push: (Route?) -> Unit
}

@Stable
class RootRouter : Router {
    private inline fun route(block: RouteHandlerScope.() -> Unit?) = current?.block() ?: Unit

    var current: RouteHandlerScope? by mutableStateOf(null)

    override val pop = {
        route {
            pop()
        }
    }

    override val push = { route: Route? ->
        route {
            replace(route)
        }
    }

    override operator fun Route0.invoke() = push(this)

    override operator fun <P0> Route1<P0>.invoke(p0: P0) = route {
        args[0] = p0
        push(this@invoke)
    }

    override operator fun <P0, P1> Route2<P0, P1>.invoke(p0: P0, p1: P1) = route {
        args[0] = p0
        args[1] = p1
        push(this@invoke)
    }

    override operator fun <P0, P1, P2> Route3<P0, P1, P2>.invoke(p0: P0, p1: P1, p2: P2) = route {
        args[0] = p0
        args[1] = p1
        args[2] = p2
        push(this@invoke)
    }

    override operator fun <P0, P1, P2, P3> Route4<P0, P1, P2, P3>.invoke(
        p0: P0,
        p1: P1,
        p2: P2,
        p3: P3
    ) = route {
        args[0] = p0
        args[1] = p1
        args[2] = p2
        args[3] = p3
        push(this@invoke)
    }
}

@JvmInline
value class RootRouterOwner internal constructor(val router: RootRouter)

val LocalRouteHandler = compositionLocalOf<RootRouter?> { null }

@Composable
fun ProvideRootRouter(content: @Composable RootRouterOwner.() -> Unit) {
    val currentRouteHandler = LocalRouteHandler.current
    val content = remember { movableContentOf(content) }
    val handler by remember { derivedStateOf { currentRouteHandler ?: RootRouter() } }

    CompositionLocalProvider(LocalRouteHandler provides handler) {
        content(RootRouterOwner(handler))
    }
}

@Composable
private fun RouteHandler(
    child: Route?,
    setChild: (Route?) -> Unit,
    modifier: Modifier = Modifier,
    listenToGlobalEmitter: Boolean = true,
    transitionSpec: TransitionSpec<Route?> = defaultTransitionSpec,
    content: @Composable RouteHandlerScope.() -> Unit
) = ProvideRootRouter {
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val parameters = rememberSaveable { arrayOfNulls<Any?>(4) }

    if (listenToGlobalEmitter && child == null) {
        OnGlobalRoute { (route, args) ->
            args.forEachIndexed(parameters::set)
            setChild(route)
        }
    }

    var predictiveBackProgress: Float? by remember { mutableStateOf(null) }
    CallbackPredictiveBackHandler(
        enabled = child != null,
        onStart = { predictiveBackProgress = 0f },
        onProgress = { predictiveBackProgress = it },
        onFinish = {
            predictiveBackProgress = null
            setChild(null)
        },
        onCancel = {
            predictiveBackProgress = null
        }
    )

    fun Route?.scope() = RouteHandlerScope(
        child = this,
        args = parameters,
        replace = setChild,
        pop = { backDispatcher?.onBackPressed() },
        root = router
    )

    val transitionState = remember { SeekableTransitionState(child) }

    if (predictiveBackProgress == null) {
        LaunchedEffect(child) {
            if (transitionState.currentState != child) transitionState.animateTo(child)
        }
    } else {
        LaunchedEffect(predictiveBackProgress) {
            transitionState.seekTo(
                fraction = predictiveBackProgress ?: 0f,
                targetState = null
            )
        }
    }

    rememberTransition(
        transitionState = transitionState,
        label = null
    ).AnimatedContent(
        transitionSpec = transitionSpec,
        modifier = modifier
    ) {
        val scope = remember(it) { it.scope() }

        LaunchedEffect(predictiveBackProgress, scope) {
            if (predictiveBackProgress == null && scope.child == null) router.current = scope
        }

        scope.content()
    }
}
