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
package com.pitchedapps.frost.services

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.BaseBundle
import android.os.PersistableBundle
import com.pitchedapps.frost.facebook.requests.RequestAuth
import com.pitchedapps.frost.facebook.requests.fbAuth
import com.pitchedapps.frost.facebook.requests.markNotificationRead
import com.pitchedapps.frost.utils.EnumBundle
import com.pitchedapps.frost.utils.EnumBundleCompanion
import com.pitchedapps.frost.utils.EnumCompanion
import com.pitchedapps.frost.utils.L
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Created by Allan Wang on 28/12/17.
 */

/**
 * Private helper data
 */
private enum class FrostRequestCommands : EnumBundle<FrostRequestCommands> {

    NOTIF_READ {

        override fun invoke(auth: RequestAuth, bundle: PersistableBundle) {
            val id = bundle.getLong(ARG_0, -1L)
            val success = auth.markNotificationRead(id).invoke()
            L.d { "Marked notif $id as read: $success" }
        }

        override fun propagate(bundle: BaseBundle) =
            FrostRunnable.prepareMarkNotificationRead(
                bundle.getLong(ARG_0),
                bundle.getCookie()
            )
    };

    override val bundleContract: EnumBundleCompanion<FrostRequestCommands>
        get() = Companion

    /**
     * Call request with arguments inside bundle
     */
    abstract fun invoke(auth: RequestAuth, bundle: PersistableBundle)

    /**
     * Return bundle builder given arguments in the old bundle
     * Must not write to old bundle!
     */
    abstract fun propagate(bundle: BaseBundle): BaseBundle.() -> Unit

    companion object : EnumCompanion<FrostRequestCommands>("frost_arg_commands", values())
}

private const val ARG_COMMAND = "frost_request_command"
private const val ARG_COOKIE = "frost_request_cookie"
private const val ARG_0 = "frost_request_arg_0"
private const val ARG_1 = "frost_request_arg_1"
private const val ARG_2 = "frost_request_arg_2"
private const val ARG_3 = "frost_request_arg_3"

private fun BaseBundle.getCookie(): String = getString(ARG_COOKIE)!!
private fun BaseBundle.putCookie(cookie: String) = putString(ARG_COOKIE, cookie)

/**
 * Singleton handler for running requests in [FrostRequestService]
 * Requests are typically completely decoupled from the UI,
 * and are optional enhancers.
 *
 * Nothing guarantees the completion time, or whether it even executes at all
 *
 * Design:
 * prepare function - creates a bundle binder
 * actor function   - calls the service with the given arguments
 *
 * Global:
 * propagator       - given a bundle with a command, extracts and executes the requests
 */
object FrostRunnable {

    fun prepareMarkNotificationRead(id: Long, cookie: String): BaseBundle.() -> Unit = {
        FrostRequestCommands.NOTIF_READ.put(this)
        putLong(ARG_0, id)
        putCookie(cookie)
    }

    fun markNotificationRead(context: Context, id: Long, cookie: String): Boolean {
        if (id <= 0) {
            L.d { "Invalid notification id $id for marking as read" }
            return false
        }
        return schedule(
            context, FrostRequestCommands.NOTIF_READ,
            prepareMarkNotificationRead(id, cookie)
        )
    }

    fun propagate(context: Context, intent: Intent?) {
        val extras = intent?.extras ?: return
        val command = FrostRequestCommands[intent] ?: return
        intent.removeExtra(ARG_COMMAND) // reset
        L.d { "Propagating command ${command.name}" }
        val builder = command.propagate(extras)
        schedule(context, command, builder)
    }

    private fun schedule(
        context: Context,
        command: FrostRequestCommands,
        bundleBuilder: PersistableBundle.() -> Unit
    ): Boolean {
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        val serviceComponent = ComponentName(context, FrostRequestService::class.java)
        val bundle = PersistableBundle()
        bundle.bundleBuilder()
        bundle.putString(ARG_COMMAND, command.name)

        if (bundle.getCookie().isNullOrBlank()) {
            L.e { "Scheduled frost request with empty cookie" }
            return false
        }

        val builder = JobInfo.Builder(REQUEST_SERVICE_BASE + command.ordinal, serviceComponent)
            .setMinimumLatency(0L)
            .setExtras(bundle)
            .setOverrideDeadline(2000L)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
        val result = scheduler.schedule(builder.build())
        if (result <= 0) {
            L.eThrow("FrostRequestService scheduler failed for ${command.name}")
            return false
        }
        L.d { "Scheduled ${command.name}" }
        return true
    }
}

class FrostRequestService : BaseJobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        super.onStartJob(params)
        val bundle = params?.extras
        if (bundle == null) {
            L.eThrow("Launched ${this::class.java.simpleName} without param data")
            return false
        }
        val cookie = bundle.getCookie()
        if (cookie.isNullOrBlank()) {
            L.eThrow("Launched ${this::class.java.simpleName} without cookie")
            return false
        }
        val command = FrostRequestCommands[bundle]
        if (command == null) {
            L.eThrow("Launched ${this::class.java.simpleName} without command")
            return false
        }
        launch(Dispatchers.IO) {
            try {
                val auth = fbAuth.fetch(cookie).await()
                command.invoke(auth, bundle)
                L.d {
                    "Finished frost service for ${command.name} in ${System.currentTimeMillis() - startTime} ms"
                }
            } catch (e: Exception) {
                L.e(e) { "Failed frost service for ${command.name} in ${System.currentTimeMillis() - startTime} ms" }
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }
}
