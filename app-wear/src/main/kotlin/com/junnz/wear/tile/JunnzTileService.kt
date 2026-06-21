package com.junnz.wear.tile

import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.*
import androidx.wear.protolayout.LayoutElementBuilders.*
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.tiles.SuspendingTileService
import com.junnz.shared.domain.Reminder
import com.junnz.shared.domain.ReminderStatus
import com.junnz.wear.sync.WatchReminderCache
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@OptIn(ExperimentalHorologistApi::class)
@AndroidEntryPoint
class JunnzTileService : SuspendingTileService() {

    @Inject
    lateinit var reminderCache: WatchReminderCache

    override suspend fun resourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ResourceBuilders.Resources {
        return ResourceBuilders.Resources.Builder()
            .setVersion(RESOURCES_VERSION)
            .build()
    }

    override suspend fun tileRequest(requestParams: RequestBuilders.TileRequest): TileBuilders.Tile {
        val pending = reminderCache.reminders.value
            .filter { it.status == ReminderStatus.PENDING }
            .take(3)

        val layout = buildTileLayout(pending)

        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(60_000L)
            .setTileTimeline(
                TimelineBuilders.Timeline.fromLayoutElement(layout)
            )
            .build()
    }

    private fun buildTileLayout(reminders: List<Reminder>): LayoutElement {
        val columnBuilder = Column.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)

        // Header
        columnBuilder.addContent(
            Text.Builder()
                .setText("Junnz")
                .setFontStyle(
                    FontStyle.Builder()
                        .setSize(sp(14f))
                        .setColor(argb(0xFFF4A623.toInt()))
                        .setWeight(FONT_WEIGHT_BOLD)
                        .build()
                )
                .build()
        )

        if (reminders.isEmpty()) {
            columnBuilder.addContent(
                Text.Builder()
                    .setText("No pending reminders")
                    .setFontStyle(
                        FontStyle.Builder()
                            .setSize(sp(12f))
                            .setColor(argb(0xFF9E9E9E.toInt()))
                            .build()
                    )
                    .build()
            )
        } else {
            reminders.forEach { reminder ->
                columnBuilder.addContent(
                    Text.Builder()
                        .setText("• ${reminder.text.take(30)}")
                        .setFontStyle(
                            FontStyle.Builder()
                                .setSize(sp(11f))
                                .setColor(argb(0xFFFFFFFF.toInt()))
                                .build()
                        )
                        .setMaxLines(1)
                        .build()
                )
            }
        }

        return Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
            .addContent(columnBuilder.build())
            .build()
    }

    companion object {
        private const val RESOURCES_VERSION = "1"
    }
}
