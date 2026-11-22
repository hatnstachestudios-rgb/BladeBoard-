package com.hatnstache.gpsauto.media

import android.service.notification.NotificationListenerService

/**
 * Exists only so the user can grant GPS Auto "notification access", which Android requires
 * before an app may see and control other apps' music. It reads no notifications.
 */
class MediaListenerService : NotificationListenerService()
