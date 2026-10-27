package com.bladeboard.launcher.media

import android.service.notification.NotificationListenerService

/**
 * Exists only so the user can grant Bladeboard "notification access", which Android requires
 * before a launcher may see and control other apps' media sessions. It reads no notifications.
 */
class MediaListenerService : NotificationListenerService()
