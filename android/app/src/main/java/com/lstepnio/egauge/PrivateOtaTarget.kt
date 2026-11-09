package com.lstepnio.egauge

import android.content.Context
import java.io.InputStream
import java.security.AlgorithmParameters
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import org.json.JSONObject

internal class PrivateOtaTarget private constructor(
    val board: String,
    val boardTag: Long,
    private val key: ECPublicKey
) {
    fun readBundle(input: InputStream) = DevUpdateBundle.read(input, key, board, boardTag)

    companion object {
        fun load(context: Context): PrivateOtaTarget? {
            if (!BuildConfig.DEBUG || !BuildConfig.PRIVATE_OTA_PROVISIONED) return null
            return runCatching {
                val target = context.assets.open("private-ota-target.json").bufferedReader().use { it.readText() }
                val pem = context.assets.open("private-ota-public.pem").bufferedReader().use { it.readText() }
                parse(target, pem)
            }.getOrNull()
        }

        fun parse(target: String, pem: String): PrivateOtaTarget {
            val json = JSONObject(target)
            require(json.keys().asSequence().toSet() == setOf("board", "boardTag"))
            val board = json.get("board") as? String ?: error("Invalid private update board label")
            val tag = when (val value = json.get("boardTag")) {
                is Int -> value.toLong()
                is Long -> value
                else -> error("Private update tag must be an unsigned integer")
            }
            require(board.isNotBlank() && board != DevUpdateBundle.REFERENCE_BOARD)
            require(tag in 1..0xffffffffL && tag != DevUpdateBundle.REFERENCE_BOARD_TAG)
            val key = HostedFirmwareCatalogCodec.publicKey(pem) as ECPublicKey
            val curve = AlgorithmParameters.getInstance("EC").run {
                init(ECGenParameterSpec("secp256r1"))
                getParameterSpec(ECParameterSpec::class.java)
            }
            require(
                key.params.curve == curve.curve && key.params.generator == curve.generator &&
                    key.params.order == curve.order && key.params.cofactor == curve.cofactor
            ) { "Private update key must use P-256" }
            return PrivateOtaTarget(board, tag, key)
        }
    }
}
