package com.pitchedapps.frost.facebook

import android.support.annotation.StringRes
import com.mikepenz.community_material_typeface_library.CommunityMaterial
import com.mikepenz.google_material_typeface_library.GoogleMaterial
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.material_design_iconic_typeface_library.MaterialDesignIconic
import com.pitchedapps.frost.R
import com.pitchedapps.frost.web.FrostWebViewClient
import com.pitchedapps.frost.web.FrostWebViewClientMenu
import io.reactivex.subjects.BehaviorSubject

enum class FbTab(@StringRes val titleId: Int, val icon: IIcon, relativeUrl: String, val webClient: ((refreshObservable: BehaviorSubject<Boolean>) -> FrostWebViewClient)? = null) {
    FEED(R.string.feed, CommunityMaterial.Icon.cmd_newspaper, ""),
    FEED_MOST_RECENT(R.string.most_recent, GoogleMaterial.Icon.gmd_grade, "/?sk=h_chr"),
    FEED_TOP_STORIES(R.string.top_stories, GoogleMaterial.Icon.gmd_star, "/?sk=h_nor"),
    PROFILE(R.string.profile, CommunityMaterial.Icon.cmd_account, "me"),
    EVENTS(R.string.events, GoogleMaterial.Icon.gmd_event, "events/upcoming"),
    FRIENDS(R.string.friends, GoogleMaterial.Icon.gmd_people, "friends/center/requests"),
    MESSAGES(R.string.messages, MaterialDesignIconic.Icon.gmi_comments, "messages"),
    NOTIFICATIONS(R.string.notifications, MaterialDesignIconic.Icon.gmi_globe, "notifications"),
    ACTIVITY_LOG(R.string.activity_log, GoogleMaterial.Icon.gmd_list, "me/allactivity"),
    PAGES(R.string.pages, GoogleMaterial.Icon.gmd_flag, "pages"),
    GROUPS(R.string.groups, GoogleMaterial.Icon.gmd_group, "groups"),
    SAVED(R.string.saved, GoogleMaterial.Icon.gmd_bookmark, "saved"),
    BIRTHDAYS(R.string.birthdays, GoogleMaterial.Icon.gmd_cake, "events/birthdays"),
    CHAT(R.string.chat, GoogleMaterial.Icon.gmd_chat, "buddylist"),
    PHOTOS(R.string.photos, GoogleMaterial.Icon.gmd_photo, "me/photos"),
    SETTINGS(R.string.settings, GoogleMaterial.Icon.gmd_settings, "settings"),
    MENU(R.string.menu, GoogleMaterial.Icon.gmd_menu, "settings", { FrostWebViewClientMenu(it) })
    ;

    val url = "$FB_URL_BASE$relativeUrl"
}

fun defaultTabs(): List<FbTab> = listOf(FbTab.FEED, FbTab.MESSAGES, FbTab.NOTIFICATIONS, FbTab.MENU)
fun defaultDrawers(): List<FbTab> = listOf(FbTab.ACTIVITY_LOG, FbTab.PAGES, FbTab.GROUPS, FbTab.SAVED)