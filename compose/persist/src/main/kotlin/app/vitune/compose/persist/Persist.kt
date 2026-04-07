package app.vitune.compose.persist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SnapshotMutationPolicy
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.structuralEqualityPolicy
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Suppress("UNCHECKED_CAST")
@Composable
fun <T> persist(
    tag: String,
    initialValue: T,
    policy: SnapshotMutationPolicy<T> = structuralEqualityPolicy()
): MutableState<T> {
    val persistMap = LocalPersistMap.current

    return remember(persistMap) {
        persistMap?.map?.getOrPut(tag) { mutableStateOf(initialValue, policy) } as? MutableState<T>
            ?: mutableStateOf(initialValue, policy)
    }
}

@Composable
fun <T> persistList(tag: String): MutableState<ImmutableList<T>> =
    persist(tag = tag, initialValue = persistentListOf())

@Composable
fun <T> persist(tag: String): MutableState<T?> = persist(tag = tag, initialValue = null)
