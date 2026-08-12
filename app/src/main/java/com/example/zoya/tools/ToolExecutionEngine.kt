package com.example.zoya.tools

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.AudioManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

class ToolExecutionEngine(private val context: Context) {

    companion object {
        private const val TAG = "ToolExecutionEngine"
    }

    /**
     * Builds the JSON array of tool function declarations for Gemini Live setup frame.
     */
    fun getToolDeclarations(): JSONArray {
        val declarations = JSONArray()

        // 1. openApp
        declarations.put(createFunctionDeclaration(
            name = "openApp",
            description = "Opens a specified application on the device by its app name or package name.",
            properties = mapOf(
                "appName" to mapOf("type" to "STRING", "description" to "The name of the app to open (e.g. YouTube, Instagram, WhatsApp, Gallery, Calculator, Chrome, Settings).")
            ),
            required = listOf("appName")
        ))

        // 2. openCamera
        declarations.put(createFunctionDeclaration(
            name = "openCamera",
            description = "Opens the device camera application.",
            properties = emptyMap()
        ))

        // 3. openGallery
        declarations.put(createFunctionDeclaration(
            name = "openGallery",
            description = "Opens the device photo gallery or photos application.",
            properties = emptyMap()
        ))

        // 4. openYouTube
        declarations.put(createFunctionDeclaration(
            name = "openYouTube",
            description = "Opens YouTube application.",
            properties = emptyMap()
        ))

        // 5. openInstagram
        declarations.put(createFunctionDeclaration(
            name = "openInstagram",
            description = "Opens Instagram application.",
            properties = emptyMap()
        ))

        // 6. openWifiSettings
        declarations.put(createFunctionDeclaration(
            name = "openWifiSettings",
            description = "Opens the Wi-Fi settings page on the phone.",
            properties = emptyMap()
        ))

        // 7. openBluetoothSettings
        declarations.put(createFunctionDeclaration(
            name = "openBluetoothSettings",
            description = "Opens the Bluetooth settings page on the phone.",
            properties = emptyMap()
        ))

        // 8. openAppSettings
        declarations.put(createFunctionDeclaration(
            name = "openAppSettings",
            description = "Opens the system settings app.",
            properties = emptyMap()
        ))

        // 9. setVolume
        declarations.put(createFunctionDeclaration(
            name = "setVolume",
            description = "Adjusts or sets the media device volume.",
            properties = mapOf(
                "direction" to mapOf("type" to "STRING", "description" to "Direction: 'UP', 'DOWN', 'MUTE', or 'SET'."),
                "levelPercentage" to mapOf("type" to "INTEGER", "description" to "Optional volume percentage from 0 to 100 when direction is SET.")
            ),
            required = listOf("direction")
        ))

        // 10. findContact
        declarations.put(createFunctionDeclaration(
            name = "findContact",
            description = "Searches contacts for a person's name and returns matching contacts and phone numbers.",
            properties = mapOf(
                "contactName" to mapOf("type" to "STRING", "description" to "Name or keyword of the contact to search for.")
            ),
            required = listOf("contactName")
        ))

        // 11. callContact
        declarations.put(createFunctionDeclaration(
            name = "callContact",
            description = "Initiates a phone call to a contact name or phone number.",
            properties = mapOf(
                "contactName" to mapOf("type" to "STRING", "description" to "Name of the contact or specific phone number to call.")
            ),
            required = listOf("contactName")
        ))

        // 12. sendWhatsAppMessage
        declarations.put(createFunctionDeclaration(
            name = "sendWhatsAppMessage",
            description = "Opens WhatsApp conversation or deep link with a prepared message for a contact.",
            properties = mapOf(
                "contactName" to mapOf("type" to "STRING", "description" to "Name of the recipient contact or phone number."),
                "message" to mapOf("type" to "STRING", "description" to "Text message content to send.")
            ),
            required = listOf("contactName", "message")
        ))

        // 13. composeEmail
        declarations.put(createFunctionDeclaration(
            name = "composeEmail",
            description = "Opens an email app with pre-filled recipient, subject, and body content.",
            properties = mapOf(
                "recipient" to mapOf("type" to "STRING", "description" to "Email address or contact name."),
                "subject" to mapOf("type" to "STRING", "description" to "Subject line of the email."),
                "body" to mapOf("type" to "STRING", "description" to "Message body text of the email.")
            ),
            required = listOf("recipient")
        ))

        val toolsObj = JSONObject().apply {
            put("functionDeclarations", declarations)
        }

        return JSONArray().apply { put(toolsObj) }
    }

    private fun createFunctionDeclaration(
        name: String,
        description: String,
        properties: Map<String, Map<String, Any>>,
        required: List<String> = emptyList()
    ): JSONObject {
        val propsObj = JSONObject()
        properties.forEach { (propName, propDef) ->
            propsObj.put(propName, JSONObject(propDef))
        }

        val parametersObj = JSONObject().apply {
            put("type", "OBJECT")
            put("properties", propsObj)
            if (required.isNotEmpty()) {
                val reqArray = JSONArray()
                required.forEach { reqArray.put(it) }
                put("required", reqArray)
            }
        }

        return JSONObject().apply {
            put("name", name)
            put("description", description)
            put("parameters", parametersObj)
        }
    }

    /**
     * Executes the function call received from Gemini Live and returns a structured JSON result.
     */
    fun executeTool(functionName: String, args: JSONObject): JSONObject {
        Log.d(TAG, "Executing tool: $functionName with args: $args")
        return try {
            when (functionName) {
                "openApp" -> openAppByName(args.optString("appName"))
                "openCamera" -> openCamera()
                "openGallery" -> openGallery()
                "openYouTube" -> openYouTube()
                "openInstagram" -> openInstagram()
                "openWifiSettings" -> openWifiSettings()
                "openBluetoothSettings" -> openBluetoothSettings()
                "openAppSettings" -> openAppSettings()
                "setVolume" -> setVolume(args.optString("direction"), args.optInt("levelPercentage", -1))
                "findContact" -> findContact(args.optString("contactName"))
                "callContact" -> callContact(args.optString("contactName"))
                "sendWhatsAppMessage" -> sendWhatsAppMessage(args.optString("contactName"), args.optString("message"))
                "composeEmail" -> composeEmail(
                    recipient = args.optString("recipient"),
                    subject = args.optString("subject", ""),
                    body = args.optString("body", "")
                )
                else -> createResult(false, functionName, "Unknown function: $functionName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing tool $functionName", e)
            createResult(false, functionName, "Exception: ${e.message}")
        }
    }

    // 1. OPEN APP BY NAME / PACKAGE
    private fun openAppByName(appNameInput: String): JSONObject {
        if (appNameInput.isBlank()) {
            return createResult(false, "openApp", "App name is empty")
        }

        val nameLower = appNameInput.lowercase().trim()

        val knownPackages = mapOf(
            "youtube" to "com.google.android.youtube",
            "instagram" to "com.instagram.android",
            "whatsapp" to "com.whatsapp",
            "chrome" to "com.android.chrome",
            "gallery" to "com.google.android.apps.photos",
            "photos" to "com.google.android.apps.photos",
            "calculator" to "com.google.android.calculator",
            "settings" to "com.android.settings",
            "gmail" to "com.google.android.gm",
            "camera" to "camera_intent"
        )

        val directPackage = knownPackages[nameLower]

        if (directPackage == "camera_intent") {
            return openCamera()
        }

        if (directPackage != null) {
            val intent = context.packageManager.getLaunchIntentForPackage(directPackage)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return createResult(true, "openApp", "Opened $appNameInput")
            }
        }

        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)

        for (ri in resolveInfos) {
            val label = ri.loadLabel(pm).toString().lowercase()
            if (label.contains(nameLower) || nameLower.contains(label)) {
                val pkgName = ri.activityInfo.packageName
                val launchIntent = pm.getLaunchIntentForPackage(pkgName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return createResult(true, "openApp", "Opened $label")
                }
            }
        }

        return createResult(false, "openApp", "App '$appNameInput' was not found on this phone.")
    }

    // 2. OPEN CAMERA
    private fun openCamera(): JSONObject {
        return try {
            val intent = Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            createResult(true, "openCamera", "Camera app opened successfully")
        } catch (e: Exception) {
            createResult(false, "openCamera", "Failed to launch camera: ${e.message}")
        }
    }

    // 3. OPEN GALLERY
    private fun openGallery(): JSONObject {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                type = "image/*"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                createResult(true, "openGallery", "Gallery opened successfully")
            } else {
                val photosIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.photos")
                if (photosIntent != null) {
                    photosIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(photosIntent)
                    createResult(true, "openGallery", "Google Photos opened successfully")
                } else {
                    createResult(false, "openGallery", "No gallery application found")
                }
            }
        } catch (e: Exception) {
            createResult(false, "openGallery", "Gallery open failed: ${e.message}")
        }
    }

    // 4. OPEN YOUTUBE
    private fun openYouTube(): JSONObject {
        val launchIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.youtube")
        return if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            createResult(true, "openYouTube", "YouTube opened successfully")
        } else {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
            createResult(true, "openYouTube", "Opened YouTube in browser")
        }
    }

    // 5. OPEN INSTAGRAM
    private fun openInstagram(): JSONObject {
        val launchIntent = context.packageManager.getLaunchIntentForPackage("com.instagram.android")
        return if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            createResult(true, "openInstagram", "Instagram opened successfully")
        } else {
            createResult(false, "openInstagram", "Instagram app is not installed on this phone.")
        }
    }

    // 6. SETTINGS
    private fun openWifiSettings(): JSONObject {
        return try {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            createResult(true, "openWifiSettings", "Wi-Fi settings opened")
        } catch (e: Exception) {
            createResult(false, "openWifiSettings", e.message ?: "Failed")
        }
    }

    private fun openBluetoothSettings(): JSONObject {
        return try {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            createResult(true, "openBluetoothSettings", "Bluetooth settings opened")
        } catch (e: Exception) {
            createResult(false, "openBluetoothSettings", e.message ?: "Failed")
        }
    }

    private fun openAppSettings(): JSONObject {
        return try {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            createResult(true, "openAppSettings", "Settings app opened")
        } catch (e: Exception) {
            createResult(false, "openAppSettings", e.message ?: "Failed")
        }
    }

    // 7. SET VOLUME
    private fun setVolume(direction: String, levelPercentage: Int): JSONObject {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return createResult(false, "setVolume", "AudioManager unavailable")

        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        when (direction.uppercase()) {
            "UP" -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                return createResult(true, "setVolume", "Increased media volume")
            }
            "DOWN" -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                return createResult(true, "setVolume", "Decreased media volume")
            }
            "MUTE" -> {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
                return createResult(true, "setVolume", "Muted media volume")
            }
            "SET" -> {
                if (levelPercentage in 0..100) {
                    val targetVol = (maxVolume * (levelPercentage / 100.0)).toInt()
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, AudioManager.FLAG_SHOW_UI)
                    return createResult(true, "setVolume", "Set volume to $levelPercentage%")
                }
            }
        }

        return createResult(false, "setVolume", "Invalid volume parameters")
    }

    // 8. FIND CONTACT
    private fun findContact(contactName: String): JSONObject {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            return createResult(false, "findContact", "PERMISSION_DENIED: READ_CONTACTS permission is required.")
        }

        val matches = JSONArray()
        var cursor: Cursor? = null
        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$contactName%")

            cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
            cursor?.let {
                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                var count = 0
                while (it.moveToNext() && count < 5) {
                    val name = if (nameIdx >= 0) it.getString(nameIdx) else ""
                    val number = if (numIdx >= 0) it.getString(numIdx) else ""
                    matches.put(JSONObject().apply {
                        put("name", name)
                        put("number", number)
                    })
                    count++
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying contacts", e)
            return createResult(false, "findContact", "Error reading contacts: ${e.message}")
        } finally {
            cursor?.close()
        }

        val res = createResult(true, "findContact", "Contact search complete")
        res.put("matches", matches)
        res.put("matchCount", matches.length())
        return res
    }

    // 9. CALL CONTACT
    private fun callContact(target: String): JSONObject {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED) {
            return createResult(false, "callContact", "PERMISSION_DENIED: CALL_PHONE permission is required.")
        }

        var phoneNumber: String? = null

        if (target.matches(Regex("^[0-9+\\s\\-()]+$"))) {
            phoneNumber = target
        } else {
            val contactRes = findContact(target)
            if (contactRes.optBoolean("success")) {
                val matches = contactRes.optJSONArray("matches")
                if (matches != null && matches.length() == 1) {
                    phoneNumber = matches.getJSONObject(0).optString("number")
                } else if (matches != null && matches.length() > 1) {
                    val matchesSummary = JSONArray()
                    for (i in 0 until matches.length()) {
                        val m = matches.getJSONObject(i)
                        matchesSummary.put("${m.optString("name")}: ${m.optString("number")}")
                    }
                    val ambiguityResult = createResult(false, "callContact", "AMBIGUOUS_CONTACTS: Multiple phone numbers found for '$target'.")
                    ambiguityResult.put("multipleMatches", matchesSummary)
                    return ambiguityResult
                }
            }
        }

        if (phoneNumber.isNullOrBlank()) {
            return createResult(false, "callContact", "Contact '$target' was not found in your phonebook.")
        }

        return try {
            val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(callIntent)
            createResult(true, "callContact", "Initiated phone call to $phoneNumber")
        } catch (e: Exception) {
            createResult(false, "callContact", "Call failed: ${e.message}")
        }
    }

    // 10. SEND WHATSAPP MESSAGE
    private fun sendWhatsAppMessage(contactName: String, message: String): JSONObject {
        var phoneNumber: String? = null

        if (contactName.matches(Regex("^[0-9+]+$"))) {
            phoneNumber = contactName
        } else {
            val contactRes = findContact(contactName)
            if (contactRes.optBoolean("success")) {
                val matches = contactRes.optJSONArray("matches")
                if (matches != null && matches.length() > 0) {
                    phoneNumber = matches.getJSONObject(0).optString("number")
                        .replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
                }
            }
        }

        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (!phoneNumber.isNullOrBlank()) {
                    val cleanNumber = if (phoneNumber.startsWith("+")) phoneNumber.substring(1) else phoneNumber
                    data = Uri.parse("https://api.whatsapp.com/send?phone=$cleanNumber&text=${Uri.encode(message)}")
                } else {
                    `package` = "com.whatsapp"
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                }
            }
            context.startActivity(intent)
            createResult(true, "sendWhatsAppMessage", "WhatsApp opened with prepared message for $contactName. Please tap Send.")
        } catch (e: Exception) {
            createResult(false, "sendWhatsAppMessage", "Failed to open WhatsApp: ${e.message}")
        }
    }

    // 11. COMPOSE EMAIL
    private fun composeEmail(recipient: String, subject: String, body: String): JSONObject {
        return try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            createResult(true, "composeEmail", "Email draft created in mail app.")
        } catch (e: Exception) {
            createResult(false, "composeEmail", "Failed to open mail app: ${e.message}")
        }
    }

    private fun createResult(success: Boolean, action: String, message: String): JSONObject {
        return JSONObject().apply {
            put("success", success)
            put("action", action)
            put("message", message)
        }
    }
}
