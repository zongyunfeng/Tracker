package me.yunfeng.tracker

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.MutableLiveData

class SwipeViewModel : ViewModel() {

    private val stream = MutableLiveData<SwipeModel>()

    val modelStream: LiveData<SwipeModel>
        get() = stream

    private val data = listOf(
        SwipeImgModel("http://ww1.sinaimg.cn/large/0065oQSqly1g2pquqlp0nj30n00yiq8u.jpg"),
        SwipeImgModel("https://ws1.sinaimg.cn/large/0065oQSqgy1fze94uew3jj30qo10cdka.jpg"),
        SwipeImgModel("https://ws1.sinaimg.cn/large/0065oQSqly1fytdr77urlj30sg10najf.jpg"),
        SwipeImgModel("https://ws1.sinaimg.cn/large/0065oQSqly1fymj13tnjmj30r60zf79k.jpg")
    )
    private var currentIndex = 0

    private val topCard
        get() = data[currentIndex % data.size]
    private val bottomCard
        get() = data[(currentIndex + 1) % data.size]

    init {
        updateStream()
    }

    fun swipe() {
        currentIndex += 1
        updateStream()
    }

    fun getTopUrl(): String? {
        return stream.value?.top?.ingUrl
    }

    private fun updateStream() {
        stream.value = SwipeModel(
            top = topCard,
            bottom = bottomCard
        )
    }

}