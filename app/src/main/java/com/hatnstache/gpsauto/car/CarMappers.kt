package com.hatnstache.gpsauto.car

import android.content.Context
import androidx.car.app.model.CarIcon
import androidx.car.app.model.DateTimeWithZone
import androidx.car.app.model.Distance
import androidx.car.app.navigation.model.Maneuver
import androidx.car.app.navigation.model.Step
import androidx.car.app.navigation.model.TravelEstimate
import androidx.core.graphics.drawable.IconCompat
import com.hatnstache.gpsauto.nav.Instructions
import com.hatnstache.gpsauto.nav.RouteStep
import java.time.ZonedDateTime
import kotlin.math.roundToInt

/** Converts our route data into Android Auto's navigation models. */
object CarMappers {
    fun icon(context: Context, res: Int): CarIcon =
        CarIcon.Builder(IconCompat.createWithResource(context, res)).build()

    fun distance(meters: Double): Distance {
        val miles = meters / 1609.344
        return when {
            miles < 0.1 -> {
                val ft = meters * 3.28084
                val rounded = if (ft < 100) (ft / 10).roundToInt() * 10 else (ft / 50).roundToInt() * 50
                Distance.create(rounded.coerceAtLeast(10).toDouble(), Distance.UNIT_FEET)
            }
            miles < 10 -> Distance.create(miles, Distance.UNIT_MILES_P1)
            else -> Distance.create(miles, Distance.UNIT_MILES)
        }
    }

    fun step(context: Context, s: RouteStep): Step {
        val b = Step.Builder(s.instruction).setManeuver(maneuver(context, s))
        if (s.name.isNotBlank()) b.setRoad(s.name)
        return b.build()
    }

    private fun maneuver(context: Context, s: RouteStep): Maneuver {
        val m = s.modifier ?: ""
        val left = m.contains("left")
        val type = when (s.type) {
            "arrive" -> Maneuver.TYPE_DESTINATION
            "depart" -> Maneuver.TYPE_DEPART
            "roundabout", "rotary", "roundabout turn" -> Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CCW
            "merge" -> Maneuver.TYPE_MERGE_SIDE_UNSPECIFIED
            "on ramp" -> if (left) Maneuver.TYPE_ON_RAMP_NORMAL_LEFT else Maneuver.TYPE_ON_RAMP_NORMAL_RIGHT
            "off ramp" -> if (left) Maneuver.TYPE_OFF_RAMP_NORMAL_LEFT else Maneuver.TYPE_OFF_RAMP_NORMAL_RIGHT
            "fork" -> if (left) Maneuver.TYPE_KEEP_LEFT else Maneuver.TYPE_KEEP_RIGHT
            else -> when (m) {
                "uturn" -> Maneuver.TYPE_U_TURN_LEFT
                "sharp right" -> Maneuver.TYPE_TURN_SHARP_RIGHT
                "right" -> Maneuver.TYPE_TURN_NORMAL_RIGHT
                "slight right" -> Maneuver.TYPE_TURN_SLIGHT_RIGHT
                "slight left" -> Maneuver.TYPE_TURN_SLIGHT_LEFT
                "left" -> Maneuver.TYPE_TURN_NORMAL_LEFT
                "sharp left" -> Maneuver.TYPE_TURN_SHARP_LEFT
                else -> Maneuver.TYPE_STRAIGHT
            }
        }
        val b = Maneuver.Builder(type).setIcon(icon(context, Instructions.iconRes(s)))
        if (type == Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CCW) {
            b.setRoundaboutExitNumber((s.exit ?: 1).coerceAtLeast(1))
        }
        return b.build()
    }

    fun estimate(meters: Double, seconds: Double): TravelEstimate {
        val secs = seconds.toLong().coerceAtLeast(0)
        val arrival = ZonedDateTime.now().plusSeconds(secs)
        return TravelEstimate.Builder(distance(meters), DateTimeWithZone.create(arrival))
            .setRemainingTimeSeconds(secs)
            .build()
    }
}
