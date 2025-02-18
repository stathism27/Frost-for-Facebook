/*
 * Copyright 2018 Allan Wang
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.pitchedapps.frost.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import ca.allanwang.kau.kpref.activity.CoreAttributeContract
import ca.allanwang.kau.kpref.activity.KPrefActivity
import ca.allanwang.kau.kpref.activity.KPrefAdapterBuilder
import ca.allanwang.kau.ui.views.RippleCanvas
import ca.allanwang.kau.utils.finishSlideOut
import ca.allanwang.kau.utils.materialDialog
import ca.allanwang.kau.utils.setMenuIcons
import ca.allanwang.kau.utils.startActivityForResult
import ca.allanwang.kau.utils.startLink
import ca.allanwang.kau.utils.string
import ca.allanwang.kau.utils.tint
import ca.allanwang.kau.utils.withSceneTransitionAnimation
import com.afollestad.materialdialogs.list.listItems
import com.mikepenz.community_material_typeface_library.CommunityMaterial
import com.mikepenz.google_material_typeface_library.GoogleMaterial
import com.pitchedapps.frost.R
import com.pitchedapps.frost.enums.Support
import com.pitchedapps.frost.settings.getAppearancePrefs
import com.pitchedapps.frost.settings.getBehaviourPrefs
import com.pitchedapps.frost.settings.getDebugPrefs
import com.pitchedapps.frost.settings.getExperimentalPrefs
import com.pitchedapps.frost.settings.getFeedPrefs
import com.pitchedapps.frost.settings.getNotificationPrefs
import com.pitchedapps.frost.settings.getSecurityPrefs
import com.pitchedapps.frost.settings.sendDebug
import com.pitchedapps.frost.utils.L
import com.pitchedapps.frost.utils.Prefs
import com.pitchedapps.frost.utils.REQUEST_RESTART
import com.pitchedapps.frost.utils.cookies
import com.pitchedapps.frost.utils.frostChangelog
import com.pitchedapps.frost.utils.frostNavigationBar
import com.pitchedapps.frost.utils.launchNewTask
import com.pitchedapps.frost.utils.loadAssets
import com.pitchedapps.frost.utils.setFrostTheme
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch

/**
 * Created by Allan Wang on 2017-06-06.
 */
class SettingsActivity : KPrefActivity() {

    var resultFlag = Activity.RESULT_CANCELED

    companion object {
        private const val REQUEST_RINGTONE = 0b10111 shl 5
        const val REQUEST_NOTIFICATION_RINGTONE = REQUEST_RINGTONE or 1
        const val REQUEST_MESSAGE_RINGTONE = REQUEST_RINGTONE or 2
        const val ACTIVITY_REQUEST_TABS = 29
        const val ACTIVITY_REQUEST_DEBUG = 53
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (fetchRingtone(requestCode, resultCode, data)) return
        when (requestCode) {
            ACTIVITY_REQUEST_TABS -> {
                if (resultCode == Activity.RESULT_OK)
                    shouldRestartMain()
                return
            }
            ACTIVITY_REQUEST_DEBUG -> {
                val url = data?.extras?.getString(DebugActivity.RESULT_URL)
                if (resultCode == Activity.RESULT_OK && url?.isNotBlank() == true)
                    sendDebug(url, data.getStringExtra(DebugActivity.RESULT_BODY))
                return
            }
        }
        reloadList()
    }

    /**
     * Fetch ringtone and save uri
     * Returns [true] if consumed, [false] otherwise
     */
    private fun fetchRingtone(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode and REQUEST_RINGTONE != REQUEST_RINGTONE || resultCode != Activity.RESULT_OK) return false
        val uri = data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        val uriString: String = uri?.toString() ?: ""
        if (uri != null) {
            try {
                grantUriPermission("com.android.systemui", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                L.e(e) { "grantUriPermission" }
            }
        }
        when (requestCode) {
            REQUEST_NOTIFICATION_RINGTONE -> {
                Prefs.notificationRingtone = uriString
                reloadByTitle(R.string.notification_ringtone)
            }
            REQUEST_MESSAGE_RINGTONE -> {
                Prefs.messageRingtone = uriString
                reloadByTitle(R.string.message_ringtone)
            }
        }
        return true
    }

    override fun kPrefCoreAttributes(): CoreAttributeContract.() -> Unit = {
        textColor = { Prefs.textColor }
        accentColor = { Prefs.accentColor }
    }

    override fun onCreateKPrefs(savedInstanceState: Bundle?): KPrefAdapterBuilder.() -> Unit = {
        subItems(R.string.appearance, getAppearancePrefs()) {
            descRes = R.string.appearance_desc
            iicon = GoogleMaterial.Icon.gmd_palette
        }

        subItems(R.string.behaviour, getBehaviourPrefs()) {
            descRes = R.string.behaviour_desc
            iicon = GoogleMaterial.Icon.gmd_trending_up
        }

        subItems(R.string.newsfeed, getFeedPrefs()) {
            descRes = R.string.newsfeed_desc
            iicon = CommunityMaterial.Icon2.cmd_newspaper
        }

        subItems(R.string.notifications, getNotificationPrefs()) {
            descRes = R.string.notifications_desc
            iicon = GoogleMaterial.Icon.gmd_notifications
        }

        subItems(R.string.security, getSecurityPrefs()) {
            descRes = R.string.security_desc
            iicon = GoogleMaterial.Icon.gmd_lock
        }

//        subItems(R.string.network, getNetworkPrefs()) {
//            descRes = R.string.network_desc
//            iicon = GoogleMaterial.Icon.gmd_network_cell
//        }

        // todo add donation?

        plainText(R.string.about_frost) {
            descRes = R.string.about_frost_desc
            iicon = GoogleMaterial.Icon.gmd_info
            onClick = {
                startActivityForResult<AboutActivity>(9, bundleBuilder = {
                    withSceneTransitionAnimation(this@SettingsActivity)
                })
            }
        }

        plainText(R.string.help_translate) {
            descRes = R.string.help_translate_desc
            iicon = GoogleMaterial.Icon.gmd_translate
            onClick = { startLink(R.string.translation_url) }
        }

        plainText(R.string.replay_intro) {
            iicon = GoogleMaterial.Icon.gmd_replay
            onClick = { launchNewTask<IntroActivity>(cookies(), true) }
        }

        subItems(R.string.experimental, getExperimentalPrefs()) {
            descRes = R.string.experimental_desc
            iicon = CommunityMaterial.Icon.cmd_flask_outline
        }

        subItems(R.string.debug_frost, getDebugPrefs()) {
            descRes = R.string.debug_frost_desc
            iicon = CommunityMaterial.Icon.cmd_android_debug_bridge
            visible = { Prefs.debugSettings }
        }
    }

    fun shouldRestartMain() {
        setFrostResult(REQUEST_RESTART)
    }

    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {
        setFrostTheme(true)
        super.onCreate(savedInstanceState)
        animate = Prefs.animate
        themeExterior(false)
    }

    fun themeExterior(animate: Boolean = true) {
        if (animate) bgCanvas.fade(Prefs.bgColor)
        else bgCanvas.set(Prefs.bgColor)
        if (animate) toolbarCanvas.ripple(Prefs.headerColor, RippleCanvas.MIDDLE, RippleCanvas.END)
        else toolbarCanvas.set(Prefs.headerColor)
        frostNavigationBar()
    }

    override fun onBackPressed() {
        if (!super.backPress()) {
            setResult(resultFlag)
            launch(NonCancellable) {
                loadAssets()
                finishSlideOut()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_settings, menu)
        toolbar.tint(Prefs.iconColor)
        setMenuIcons(
            menu, Prefs.iconColor,
            R.id.action_email to GoogleMaterial.Icon.gmd_email,
            R.id.action_changelog to GoogleMaterial.Icon.gmd_info
        )
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_email -> materialDialog {
                title(R.string.subject)
                listItems(items = Support.values().map { string(it.title) }) { _, index, _ ->
                    Support.values()[index].sendEmail(this@SettingsActivity)
                }
            }
            R.id.action_changelog -> frostChangelog()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    fun setFrostResult(flag: Int) {
        resultFlag = resultFlag or flag
    }
}
