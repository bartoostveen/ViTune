@file:Suppress("UNCHECKED_CAST")

package app.vitune.compose.routing

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.parcelize.Parcelize

@Parcelize
@Immutable
sealed class Route : Parcelable {
    abstract val tag: String

    override fun equals(other: Any?) = when {
        this === other -> true
        other is Route -> tag == other.tag
        else -> false
    }

    override fun hashCode() = tag.hashCode()

    protected fun global(args: Array<Any?>) = globalRouteFlow.tryEmit(
        RouteRequest(
            route = this,
            args = args
        )
    )

    protected suspend fun ensureGlobal(args: Array<Any?>) {
        globalRouteFlow.subscriptionCount.first { it > 0 }
        globalRouteFlow.emit(
            RouteRequest(
                route = this,
                args = args
            )
        )
    }
}

@Immutable
class Route0(override val tag: String) : Route() {
    @Composable
    context(scope: RouteHandlerScope)
    operator fun invoke(content: @Composable () -> Unit) {
        if (this == scope.child) content()
    }

    fun global() = global(emptyArray())
    suspend fun ensureGlobal() = ensureGlobal(emptyArray())
}

@Immutable
class Route1<P0>(override val tag: String) : Route() {
    @Composable
    context(scope: RouteHandlerScope)
    operator fun invoke(content: @Composable (P0) -> Unit) {
        if (this == scope.child) content(scope.args[0] as P0)
    }

    fun global(p0: P0) = global(arrayOf(p0))
    suspend fun ensureGlobal(p0: P0) = ensureGlobal(arrayOf(p0))
}

@Immutable
class Route2<P0, P1>(override val tag: String) : Route() {
    @Composable
    context(scope: RouteHandlerScope)
    operator fun invoke(content: @Composable (P0, P1) -> Unit) {
        if (this == scope.child) content(
            scope.args[0] as P0,
            scope.args[1] as P1
        )
    }

    @Suppress("unused") // not true
    fun global(p0: P0, p1: P1) = global(arrayOf(p0, p1))

    @Suppress("unused") // not true
    suspend fun ensureGlobal(p0: P0, p1: P1) = ensureGlobal(arrayOf(p0, p1))
}

@Immutable
class Route3<P0, P1, P2>(override val tag: String) : Route() {
    @Composable
    context(scope: RouteHandlerScope)
    operator fun invoke(content: @Composable (P0, P1, P2) -> Unit) {
        if (this == scope.child) content(
            scope.args[0] as P0,
            scope.args[1] as P1,
            scope.args[2] as P2
        )
    }

    @Suppress("unused") // not true
    fun global(p0: P0, p1: P1, p2: P2) = global(arrayOf(p0, p1, p2))

    @Suppress("unused") // not true
    suspend fun ensureGlobal(p0: P0, p1: P1, p2: P2) = ensureGlobal(arrayOf(p0, p1, p2))
}

@Immutable
class Route4<P0, P1, P2, P3>(override val tag: String) : Route() {
    @Composable
    context(scope: RouteHandlerScope)
    operator fun invoke(content: @Composable (P0, P1, P2, P3) -> Unit) {
        if (this == scope.child) content(
            scope.args[0] as P0,
            scope.args[1] as P1,
            scope.args[2] as P2,
            scope.args[3] as P3
        )
    }

    fun global(p0: P0, p1: P1, p2: P2, p3: P3) = global(arrayOf(p0, p1, p2, p3))
    suspend fun ensureGlobal(p0: P0, p1: P1, p2: P2, p3: P3) = ensureGlobal(arrayOf(p0, p1, p2, p3))
}
