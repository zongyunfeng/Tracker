package me.yunfeng.tracker

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.constraintlayout.motion.widget.TransitionAdapter
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.bumptech.glide.Glide
import kotlinx.android.synthetic.main.activity_main.*
import me.yunfeng.eventsdk.EventEmitter

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val viewModel = ViewModelProviders
            .of(this)
            .get(SwipeViewModel::class.java)

        viewModel
            .modelStream
            .observe(this, Observer {
                bindCard(it)
            })

        motionLayout.setTransitionListener(object : TransitionAdapter() {

            override fun onTransitionCompleted(motionLayout: MotionLayout, currentId: Int) {
                val imgUrl: String? = viewModel.getTopUrl()
                when (currentId) {
                    R.id.offScreenPass -> {
                        imgUrl?.apply { EventEmitter.logEvent(Event.ImgSwiped(imgUrl, false)) }
                        motionLayout.progress = 0f
                        motionLayout.setTransition(R.id.rest, R.id.like)
                        viewModel.swipe()
                    }
                    R.id.offScreenLike -> {
                        imgUrl?.apply { EventEmitter.logEvent(Event.ImgSwiped(imgUrl, true)) }
                        motionLayout.progress = 0f
                        motionLayout.setTransition(R.id.rest, R.id.pass)
                        viewModel.swipe()
                    }
                }
            }

        })

        likeButton.setOnClickListener {
            motionLayout.transitionToState(R.id.like)
        }

        passButton.setOnClickListener {
            motionLayout.transitionToState(R.id.pass)
        }
    }

    private fun bindCard(model: SwipeModel) {
        Glide.with(this).load(model.top.ingUrl).into(iv_top_card)
        Glide.with(this).load(model.bottom.ingUrl).into(iv_bottom_card)
    }

}
