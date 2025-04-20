package ru.surfstudio.otp_autofill

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.util.Base64
import androidx.annotation.RequiresApi
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.* // Required for Arrays.copyOfRange

private const val HASH_TYPE = "SHA-256"
private const val NUM_HASHED_BYTES = 9
private const val NUM_BASE64_CHAR = 11

/**
 * Computes the app signature hash for SMS Retriever API.
 * Based on Google's example:
 * https://github.com/googlearchive/android-credentials/blob/master/sms-verification/android/app/src/main/java/com/google/samples/smartlock/sms_verify/AppSignatureHelper.java
 *
 * Updated to use modern signing certificate APIs on Android P and above.
 */
class AppSignatureHelper(context: Context) : ContextWrapper(context) {

    /**
     * Get all the app signatures for the current package
     * @return list of application signatures
     */
    @RequiresApi(Build.VERSION_CODES.P)
    @SuppressLint("PackageManagerGetSignatures") // Needed for API < 28
    fun getAppSignatures(): List<String> {
        val packageName = packageName
        val packageManager = packageManager
        val signatures: List<Signature> = try {
            // Use new API for Android P and above
            val signingInfo = packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_SIGNING_CERTIFICATES // Use GET_SIGNING_CERTIFICATES flag
            ).signingInfo
            // Check if the app is signed via APK Signature Scheme v2 or v3.
            if (signingInfo!!.hasMultipleSigners()) {
                // If multiple signers, include all for hash calculation
                signingInfo.apkContentsSigners?.toList() ?: emptyList()
            } else {
                // If single signer or v1 signing scheme
                signingInfo.signingCertificateHistory?.toList() ?: emptyList()
            }
        } catch (e: PackageManager.NameNotFoundException) {
            // Should not happen for the app's own package, but handle anyway
            System.err.println("Unable to find package to obtain signature.")
            return emptyList()
        } catch (e: Exception) {
            // Catch other potential exceptions during signature retrieval
            System.err.println("Exception while obtaining signature: $e")
            return emptyList()
        }

        // For each signature create a compatible hash
        return signatures.mapNotNull { signature ->
            hash(packageName, signature.toByteArray()) // Pass byte array to hash function
        }
    }

    /**
     * Creates the SMS Retriever hash from the app's package name and certificate signature.
     *
     * @param packageName The package name of the application.
     * @param signatureByteArray The byte array representation of the app's signature.
     * @return The SMS Retriever hash string, or null if hashing fails.
     */
    private fun hash(packageName: String, signatureByteArray: ByteArray): String? {
        val appInfo = "$packageName ".toByteArray(StandardCharsets.UTF_8) + signatureByteArray
        return try {
            val messageDigest = MessageDigest.getInstance(HASH_TYPE)
            messageDigest.update(appInfo)
            var hashSignature = messageDigest.digest()

            // Truncate buffer to required size
            hashSignature = Arrays.copyOfRange(hashSignature, 0, NUM_HASHED_BYTES)

            // Encode as Base64
            var base64Hash = Base64.encodeToString(hashSignature, Base64.NO_PADDING or Base64.NO_WRAP)
            // Ensure the hash is exactly NUM_BASE64_CHAR characters long
            if (base64Hash.length > NUM_BASE64_CHAR) {
                base64Hash = base64Hash.substring(0, NUM_BASE64_CHAR)
            }
            base64Hash
        } catch (e: NoSuchAlgorithmException) {
            System.err.println("No Such Algorithm Exception $HASH_TYPE: $e")
            null
        }
    }
}