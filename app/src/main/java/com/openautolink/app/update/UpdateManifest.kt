package com.openautolink.app.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UpdateManifest(
    @SerialName("latest_version_code") val latestVersionCode: Int,
    @SerialName("latest_version_name") val latestVersionName: String,
    @SerialName("apk_url") val apkUrl: String,
    @SerialName("changelog") val changelog: String = "",
    @SerialName("min_android_sdk") val minAndroidSdk: Int = 32,
)
