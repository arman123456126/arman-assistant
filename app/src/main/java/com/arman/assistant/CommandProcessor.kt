package com.arman.assistant

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import android.telephony.SmsManager
import androidx.core.content.ContextCompat

/**
 * Parses a recognized voice command (Bangla or English) and performs the
 * matching phone action using standard Android APIs.
 *
 * Design note: several actions (Wi-Fi, Bluetooth off) can no longer be
 * toggled silently by third-party apps since Android 10-13 for privacy
 * reasons. In those cases we open the relevant system panel instead of
 * pretending to do something we can't - see comments below.
 */
object CommandProcessor {

    private var torchOn = false

    fun process(rawText: String, context: Context): String {
        val text = rawText.trim()
        val lower = text.lowercase()

        return when {
            containsAny(lower, text, "call ", "কল কর", "ফোন কর", "ডায়াল কর") ->
                handleCall(text, context)

            containsAny(lower, text, "message ", "sms ", "মেসেজ", "খুদে বার্তা") ->
                handleMessage(text, context)

            containsAny(lower, text, "open ", "launch ", "খোল", "চালু কর") ->
                handleOpenApp(text, context)

            containsAny(lower, text, "alarm", "অ্যালার্ম") ->
                handleAlarm(text, context)

            containsAny(lower, text, "remind", "রিমাইন্ডার", "মনে করিয়ে") ->
                handleReminder(text, context)

            containsAny(lower, text, "flashlight", "torch", "ফ্ল্যাশ") ->
                handleFlashlight(lower, context)

            containsAny(lower, text, "wifi", "ওয়াইফাই", "ওয়াই-ফাই") ->
                handleWifi(context)

            containsAny(lower, text, "bluetooth", "ব্লুটুথ") ->
                handleBluetooth(lower, context)

            else ->
                "দুঃখিত, কমান্ডটি বুঝতে পারিনি। Sorry, I didn't understand that command."
        }
    }

    private fun containsAny(lower: String, original: String, vararg keys: String): Boolean =
        keys.any { lower.contains(it.lowercase()) || original.contains(it) }

    // ---------------- Call ----------------
    private fun handleCall(text: String, context: Context): String {
        val name = stripFirstMatch(text, listOf("call", "কল কর", "ফোন কর", "ডায়াল কর")).trim()
        if (name.isEmpty()) return "কার নাম বলবেন? Whom should I call?"

        val number = ContactHelper.findNumber(context, name)
            ?: return "\"$name\" নামে কোনো কন্ট্যাক্ট পাওয়া যায়নি। No contact named \"$name\" found."

        val hasCallPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        val intent = if (hasCallPermission) {
            Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
        } else {
            // Falls back to opening the dialer pre-filled; user taps call themselves.
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)

        return if (hasCallPermission) "$name-কে কল করছি। Calling $name."
        else "$name-এর ডায়ালার খুলছি, কল বাটনে চাপুন। Opening dialer for $name."
    }

    // ---------------- SMS ----------------
    private fun handleMessage(text: String, context: Context): String {
        val remainder = stripFirstMatch(text, listOf("message", "sms", "মেসেজ", "খুদে বার্তা")).trim()
        if (remainder.isEmpty()) return "কাকে কী মেসেজ পাঠাবো? Who should I message, and with what?"

        val parts = remainder.split(" ", limit = 2)
        val name = parts.getOrNull(0) ?: ""
        val body = parts.getOrNull(1) ?: ""
        if (name.isEmpty() || body.isEmpty()) {
            return "এভাবে বলুন: \"message Rahim on my way\". Try: \"message Rahim on my way\"."
        }

        val number = ContactHelper.findNumber(context, name)
            ?: return "\"$name\" নামে কোনো কন্ট্যাক্ট পাওয়া যায়নি। No contact named \"$name\" found."

        val hasSmsPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        return if (hasSmsPermission) {
            SmsManager.getDefault().sendTextMessage(number, null, body, null, null)
            "$name-কে মেসেজ পাঠানো হয়েছে। Message sent to $name."
        } else {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).apply {
                putExtra("sms_body", body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "$name-এর জন্য মেসেজ অ্যাপ খুলছি। Opening messages app for $name."
        }
    }

    // ---------------- Open app ----------------
    private fun handleOpenApp(text: String, context: Context): String {
        val appName = stripFirstMatch(text, listOf("open", "launch", "খোল", "চালু কর")).trim()
        if (appName.isEmpty()) return "কোন অ্যাপ খুলবো? Which app should I open?"

        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val match = apps.firstOrNull { appInfo ->
            pm.getApplicationLabel(appInfo).toString().contains(appName, ignoreCase = true)
        }

        if (match == null) return "\"$appName\" অ্যাপ খুঁজে পাইনি। Couldn't find an app called \"$appName\"."

        val launchIntent = pm.getLaunchIntentForPackage(match.packageName)
            ?: return "\"$appName\" খোলা যাচ্ছে না। Can't launch \"$appName\"."
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)

        val label = pm.getApplicationLabel(match).toString()
        return "$label খুলছি। Opening $label."
    }

    // ---------------- Alarm ----------------
    private fun handleAlarm(text: String, context: Context): String {
        val timeRegex = Regex("""(\d{1,2})[:.]?(\d{2})?\s*(am|pm|AM|PM)?""")
        val match = timeRegex.find(text)

        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            putExtra(AlarmClock.EXTRA_MESSAGE, "AI Arman")
        }

        if (match != null) {
            var hour = match.groupValues[1].toIntOrNull() ?: 0
            val minute = match.groupValues[2].toIntOrNull() ?: 0
            val meridian = match.groupValues[3].lowercase()
            if (meridian == "pm" && hour < 12) hour += 12
            if (meridian == "am" && hour == 12) hour = 0
            intent.putExtra(AlarmClock.EXTRA_HOUR, hour)
            intent.putExtra(AlarmClock.EXTRA_MINUTES, minute)
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return "অ্যালার্ম সেট করার স্ক্রিন খুলছি, নিশ্চিত করুন। Opening alarm screen - please confirm."
    }

    // ---------------- Reminder ----------------
    private fun handleReminder(text: String, context: Context): String {
        val title = stripFirstMatch(text, listOf("remind me to", "remind", "রিমাইন্ডার", "মনে করিয়ে দাও", "মনে করিয়ে")).trim()

        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, if (title.isEmpty()) "Reminder" else title)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return "রিমাইন্ডার তৈরির স্ক্রিন খুলছি, সময়টা বসিয়ে সেভ করুন। Opening reminder screen - set the time and save."
    }

    // ---------------- Flashlight ----------------
    private fun handleFlashlight(lower: String, context: Context): String {
        val turnOn = !lower.contains("off") && !lower.contains("বন্ধ")
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        val torchId = cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return "এই ফোনে ফ্ল্যাশলাইট পাওয়া যায়নি। No flash unit found on this device."

        return try {
            cameraManager.setTorchMode(torchId, turnOn)
            torchOn = turnOn
            if (turnOn) "ফ্ল্যাশলাইট চালু করা হয়েছে। Flashlight on." else "ফ্ল্যাশলাইট বন্ধ করা হয়েছে। Flashlight off."
        } catch (e: Exception) {
            "ফ্ল্যাশলাইট নিয়ন্ত্রণ করা যায়নি। Couldn't control the flashlight."
        }
    }

    // ---------------- Wi-Fi ----------------
    // Android 10+ blocks apps from silently toggling Wi-Fi (privacy change).
    // The best a third-party app can legally do is open the quick panel.
    private fun handleWifi(context: Context): String {
        val intent = Intent(Settings.Panel.ACTION_WIFI).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return "ওয়াইফাই প্যানেল খুলছি, ট্যাপ করে অন/অফ করুন। Opening Wi-Fi panel - tap to switch it."
    }

    // ---------------- Bluetooth ----------------
    private fun handleBluetooth(lower: String, context: Context): String {
        val turnOn = !lower.contains("off") && !lower.contains("বন্ধ")
        return if (turnOn) {
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "ব্লুটুথ চালু করার অনুমতি চাইছি। Requesting to turn Bluetooth on."
        } else {
            // Android 13+ no longer allows apps to silently disable Bluetooth.
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "ব্লুটুথ সেটিংস খুলছি, বন্ধ করতে ট্যাপ করুন। Opening Bluetooth settings - tap to turn it off."
        }
    }

    // ---------------- helper ----------------
    private fun stripFirstMatch(text: String, triggers: List<String>): String {
        val lower = text.lowercase()
        for (trigger in triggers) {
            val idx = lower.indexOf(trigger.lowercase())
            if (idx >= 0) {
                return text.substring(idx + trigger.length)
            }
        }
        return text
    }
}
