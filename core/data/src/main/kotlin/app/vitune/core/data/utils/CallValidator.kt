package app.vitune.core.data.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.XmlResourceParser
import android.os.Process
import androidx.annotation.XmlRes
import java.security.MessageDigest

/**
 * Stateful caller validator for Android intents, based on XML caller data
 */
class CallValidator(
    context: Context,
    @XmlRes callerList: Int
) {
    private val packageManager = context.packageManager

    private val whitelist = runCatching {
        context.resources.getXml(callerList)
    }.getOrNull()?.let(Whitelist::parse) ?: Whitelist()
    private val systemSignature = getPackageInfo("android")?.signature

    private val cache = mutableMapOf<Pair<String, Int>, Boolean>()

    fun canCall(pak: String, uid: Int) = cache.getOrPut(pak to uid) cache@{
        val info = getPackageInfo(pak) ?: return@cache false
        if (info.applicationInfo?.uid != uid) return@cache false
        val signature = info.signature ?: return@cache false

        val permissions = info.requestedPermissions?.filterIndexed { index, _ ->
            info
                .requestedPermissionsFlags
                ?.getOrNull(index)
                ?.let { it and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0 } == true
        }

        when {
            uid == Process.myUid() -> true
            uid == Process.SYSTEM_UID -> true
            signature == systemSignature -> true
            whitelist.isWhitelisted(pak, signature) -> true
            permissions != null && Manifest.permission.MEDIA_CONTENT_CONTROL in permissions -> true
            permissions != null && Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE in permissions -> true
            else -> false
        }
    }

    @Suppress("DEPRECATION") // backwards compat
    private fun getPackageInfo(
        pak: String,
        flags: Int = PackageManager.GET_SIGNATURES or PackageManager.GET_PERMISSIONS
    ) = runCatching {
        packageManager.getPackageInfo(
            /* packageName = */ pak,
            /* flags = */ flags
        )
    }.getOrNull()

    @Suppress("DEPRECATION") // backwards compat
    private val PackageInfo.signature
        get() = signatures?.let { signatures ->
            if (signatures.size != 1) null
            else signatures.firstOrNull()?.toByteArray()?.sha256
        }

    @Suppress("ImplicitDefaultLocale") // not relevant
    private val ByteArray.sha256: String?
        get() = runCatching {
            val md = MessageDigest.getInstance("SHA256")
            md.update(this)
            md.digest()
        }.getOrNull()?.joinToString(":") { String.format("%02x", it) }
}

@JvmInline
value class Whitelist(private val map: WhitelistMap = mapOf()) {
    companion object {
        fun parse(parser: XmlResourceParser) = Whitelist(
            buildMap {
                runCatching {
                    var event = parser.next()

                    while (event != XmlResourceParser.END_DOCUMENT) {
                        if (event == XmlResourceParser.START_TAG && parser.name == "signature")
                            putV2Tag(parser)

                        event = parser.next()
                    }
                }
            }
        )

        private fun MutableMap<String, Set<Key>>.putV2Tag(parser: XmlResourceParser) =
            runCatching {
                val pak = parser.getAttributeValue(
                    /* p0 = */ null,
                    /* p1 = */ "package"
                )
                val keys = buildSet {
                    var event = parser.next()
                    while (event != XmlResourceParser.END_TAG) {
                        add(
                            Key(
                                release = parser.getAttributeBooleanValue(
                                    /* namespace = */ null,
                                    /* attribute = */ "release",
                                    /* defaultValue = */ false
                                ),
                                signature = parser
                                    .nextText()
                                    .replace(WHITESPACE_REGEX, "")
                                    .lowercase()
                            )
                        )

                        event = parser.next()
                    }
                }

                put(
                    key = pak,
                    value = keys
                )
            }
    }

    fun isWhitelisted(pak: String, signature: String) =
        map[pak]?.first { it.signature == signature } != null

    data class Key(
        val signature: String,
        val release: Boolean
    )
}

typealias WhitelistMap = Map<String, Set<Whitelist.Key>>

private val WHITESPACE_REGEX = "[\\s\\n]".toRegex()
