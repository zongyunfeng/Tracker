package me.yunfeng.tracker

import me.yunfeng.analytics_annotation.AnalyticsEvent

@AnalyticsEvent
sealed class Event {
    data class ImgTapped(val imgUrl: String) : Event()

}