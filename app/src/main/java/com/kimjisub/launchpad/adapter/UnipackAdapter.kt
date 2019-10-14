package com.kimjisub.launchpad.adapter

import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.LinearLayout.LayoutParams
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter
import com.kimjisub.design.PackView
import com.kimjisub.design.PackView.OnEventListener
import com.kimjisub.launchpad.R.*
import com.kimjisub.launchpad.db.ent.UnipackENT
import com.kimjisub.launchpad.unipack.Unipack
import com.kimjisub.manager.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class UnipackItem(
	var unipack: Unipack,
	val unipackENT: LiveData<UnipackENT>,
	var isNew: Boolean
) {
	var unipackENTObserver: Observer<UnipackENT>? = null

	var packView: PackView? = null
	var flagColor: Int = 0
	var toggle: Boolean = false
	var moving: Boolean = false
}

class UnipackHolder(
	var packView: PackView
) : RecyclerView.ViewHolder(packView) {
	var realPosition = -1
}

class UnipackAdapter(private val list: ArrayList<UnipackItem>, private val eventListener: EventListener) : Adapter<UnipackHolder>() {
	var i =0
	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UnipackHolder {
		val packView = PackView(parent.context)
		val lp = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
		packView.layoutParams = lp
		return UnipackHolder(packView)
	}

	override fun onBindViewHolder(holder: UnipackHolder, position: Int) {
		val context = holder.packView.context

		val item = list[holder.adapterPosition]
		val packView = holder.packView

		// 이전 데이터에 매핑된 뷰를 제거합니다.
		try {
			list[holder.realPosition].packView = null
		} catch (ignored: Exception) {
		}

		// 새롭게 할당될 데이터에 뷰를 할당하고 홀더에도 해당 포지션을 등록합니다.
		item.packView = packView
		holder.realPosition = holder.adapterPosition

		////////////////////////////////////////////////////////////////////////////////////////////////

		var viewingTitle: String = item.unipack.title!!
		var viewingSubtitle: String = item.unipack.producerName!!
		if (item.unipack.criticalError) {
			item.flagColor = ContextCompat.getColor(context, color.red)
			viewingTitle = context.getString(string.errOccur)
			viewingSubtitle = item.unipack.F_project.path
		} else
			item.flagColor = ContextCompat.getColor(context, color.skyblue)

		packView.apply {
			animate = false
			toggleColor = ContextCompat.getColor(context, color.red)
			untoggleColor = item.flagColor
			title = viewingTitle
			subtitle = viewingSubtitle
			option1Name = context.getString(string.LED_)
			option1 = item.unipack.keyLEDExist
			option2Name = context.getString(string.autoPlay_)
			option2 = item.unipack.autoPlayExist
			bookmark = false

			item.unipackENT.observeOnce(Observer {
				bookmark = it.bookmark
			})

			setOnEventListener(object : OnEventListener {
				override fun onViewClick(v: PackView) {
					eventListener.onViewClick(item, v)
				}

				override fun onViewLongClick(v: PackView) {
					eventListener.onViewLongClick(item, v)
				}

				override fun onPlayClick(v: PackView) {
					eventListener.onPlayClick(item, v)
				}
			})
			toggle(item.toggle)
			animate = true
			var a: Animation? = AnimationUtils.loadAnimation(context, anim.pack_in)
			if (item.isNew) a = AnimationUtils.loadAnimation(context, anim.pack_new_in)
			item.isNew = false
			animation = a
		}


	}

	override fun getItemCount() = list.size

	interface EventListener {
		fun onViewClick(item: UnipackItem, v: PackView)
		fun onViewLongClick(item: UnipackItem, v: PackView)
		fun onPlayClick(item: UnipackItem, v: PackView)
	}

	fun <T> LiveData<T>.observeOnce(observer: Observer<T>) {
		observeForever(object : Observer<T> {
			override fun onChanged(t: T?) {
				observer.onChanged(t)
				removeObserver(this)
			}
		})

		/*
		observeForever(object : Observer<T> {
			override fun onChanged(t: T?) {
				observer.onChanged(t)
				removeObserver(this)
			}
		})
		 */
	}

}