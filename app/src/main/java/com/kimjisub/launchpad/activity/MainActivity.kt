package com.kimjisub.launchpad.activity

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.DialogInterface
import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.view.animation.Animation
import android.view.animation.Animation.AnimationListener
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AlertDialog.Builder
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver
import com.anjlab.android.iab.v3.TransactionDetails
import com.github.clans.fab.FloatingActionMenu.OnMenuToggleListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.kimjisub.design.PackView
import com.kimjisub.design.dialog.FileExplorerDialog
import com.kimjisub.design.dialog.FileExplorerDialog.OnEventListener
import com.kimjisub.design.panel.MainPackPanel
import com.kimjisub.launchpad.BuildConfig
import com.kimjisub.launchpad.R.*
import com.kimjisub.launchpad.adapter.UnipackAdapter
import com.kimjisub.launchpad.adapter.UnipackAdapter.EventListener
import com.kimjisub.launchpad.adapter.UnipackItem
import com.kimjisub.launchpad.db.AppDataBase
import com.kimjisub.launchpad.db.ent.UnipackENT
import com.kimjisub.launchpad.db.ent.UnipackOpenENT
import com.kimjisub.launchpad.manager.BillingManager
import com.kimjisub.launchpad.manager.BillingManager.BillingEventListener
import com.kimjisub.launchpad.manager.ThemeResources
import com.kimjisub.launchpad.midi.MidiConnection.controller
import com.kimjisub.launchpad.midi.MidiConnection.driver
import com.kimjisub.launchpad.midi.MidiConnection.removeController
import com.kimjisub.launchpad.midi.controller.MidiController
import com.kimjisub.launchpad.network.Networks.FirebaseManager
import com.kimjisub.launchpad.tool.UnipackAutoMapper
import com.kimjisub.launchpad.unipack.Unipack
import com.kimjisub.manager.FileManager
import com.kimjisub.manager.Log
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.*
import org.jetbrains.anko.design.snackbar
import java.io.File
import java.util.*

class MainActivity : BaseActivity() {

	private val db: AppDataBase by lazy { AppDataBase.getInstance(this)!! }
	private var billingManager: BillingManager? = null


	private var list: ArrayList<UnipackItem> = ArrayList()

	private var lastPlayIndex = -1
	private var updateProcessing = false

	private val adapter: UnipackAdapter by lazy {
		val adapter = UnipackAdapter(list, object : EventListener {
			override fun onViewClick(item: UnipackItem, v: PackView) {
				if (!item.moving) togglePlay(item)
			}

			override fun onViewLongClick(item: UnipackItem, v: PackView) {}
			override fun onPlayClick(item: UnipackItem, v: PackView) {
				if (!item.moving) pressPlay(item)
			}
		})
		adapter.registerAdapterDataObserver(object : AdapterDataObserver() {
			override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
				super.onItemRangeInserted(positionStart, itemCount)
				LL_errItem.visibility = if (list.size == 0) View.VISIBLE else View.GONE
			}

			override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
				super.onItemRangeRemoved(positionStart, itemCount)
				LL_errItem.visibility = if (list.size == 0) View.VISIBLE else View.GONE
			}
		})

		adapter
	}

	private val storeAnimator: ValueAnimator by lazy {
		val animator = ObjectAnimator.ofObject(ArgbEvaluator(), colors.orange, colors.red)
		animator.duration = 300
		animator.repeatCount = Animation.INFINITE
		animator.repeatMode = ValueAnimator.REVERSE
		animator.addUpdateListener {
			val color = it.animatedValue as Int
			FAM_floatingMenu.menuButtonColorNormal = color
			FAM_floatingMenu.menuButtonColorPressed = color
			FAB_store.colorNormal = color
			FAB_store.colorPressed = color
		}
		animator
	}

	private val firebase_storeCount: FirebaseManager by lazy {
		val firebaseManager = FirebaseManager("storeCount")
		firebaseManager.setEventListener(object : ValueEventListener {
			override fun onDataChange(dataSnapshot: DataSnapshot) {
				val data: Long? = dataSnapshot.getValue(Long::class.java)
				val prev = preference.prevStoreCount
				runOnUiThread { blink(data != prev) }
			}

			override fun onCancelled(databaseError: DatabaseError) {}
		})
		firebaseManager
	}

	private val midiController: MidiController by lazy {
		object : MidiController() {
			override fun onAttach() {
				Log.driverCycle("MainActivity onConnected()")
				updateLP()
			}

			override fun onDetach() {
				Log.driverCycle("MainActivity onDisconnected()")
			}

			override fun onPadTouch(x: Int, y: Int, upDown: Boolean, velo: Int) {
				if (!((x == 3 || x == 4) && (y == 3 || y == 4))) {
					if (upDown) driver.sendPadLED(x, y, intArrayOf(40, 61)[(Math.random() * 2).toInt()]) else driver.sendPadLED(x, y, 0)
				}
			}

			override fun onFunctionkeyTouch(f: Int, upDown: Boolean) {
				if (f == 0 && upDown) {
					if (havePrev()) {
						togglePlay(lastPlayIndex - 1)
						RV_recyclerView.smoothScrollToPosition(lastPlayIndex)
					} else showSelectLPUI()
				} else if (f == 1 && upDown) {
					if (haveNext()) {
						togglePlay(lastPlayIndex + 1)
						RV_recyclerView.smoothScrollToPosition(lastPlayIndex)
					} else showSelectLPUI()
				} else if (f == 2 && upDown) {
					if (haveNow()) list[lastPlayIndex].packView!!.onPlayClick()
				}
			}

			override fun onChainTouch(c: Int, upDown: Boolean) {}
			override fun onUnknownEvent(cmd: Int, sig: Int, note: Int, velo: Int) {
				if ((cmd == 7) && (sig == 46) && (note == 0) && (velo == -9)) updateLP()
			}
		}
	}

	// =============================================================================================


	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(layout.activity_main)


		val divider = DividerItemDecoration(this@MainActivity, DividerItemDecoration.VERTICAL)
		divider.setDrawable(resources.getDrawable(drawable.border_divider))
		RV_recyclerView.addItemDecoration(divider)
		RV_recyclerView.setHasFixedSize(false)
		RV_recyclerView.layoutManager = LinearLayoutManager(this@MainActivity)
		RV_recyclerView.adapter = adapter


		initPanel()
		loadAdmob()
		billingManager = BillingManager(this@MainActivity, object : BillingEventListener {
			override fun onProductPurchased(productId: String, details: TransactionDetails?) {}
			override fun onPurchaseHistoryRestored() {}
			override fun onBillingError(errorCode: Int, error: Throwable?) {}
			override fun onBillingInitialized() {}
			override fun onRefresh() {
				P_total.data.premium.set(billingManager!!.purchaseRemoveAds || billingManager!!.purchaseProTools)
				if (billingManager!!.showAds) {
					if (checkAdsCooltime()) {
						updateAdsCooltime()
						showAdmob()
					}
					/*todo ad
					AdRequest adRequest = new AdRequest.Builder().build();
					b.adView.loadAd(adRequest);*/
				}
				/*todo ad
				 else
					b.adView.setVisibility(View.GONE);*/
			}
		})
		startMain()
		updatePanel(true)
	}

	@SuppressLint("StaticFieldLeak")
	private fun startMain() {
		SRL_swipeRefreshLayout.setOnRefreshListener { this.update() }
		FAB_reconnectLaunchpad.setOnClickListener {
			startActivity<LaunchpadActivity>()
		}
		FAB_loadUniPack.setOnClickListener {
			FileExplorerDialog(this@MainActivity, preference.fileExplorerPath,
				object : OnEventListener {
					override fun onFileSelected(filePath: String) {
						loadUnipack(File(filePath))
					}

					override fun onPathChanged(folderPath: String) {
						preference.fileExplorerPath = folderPath
					}
				})
				.show()
		}
		FAB_store.setOnClickListener { startActivityForResult<FBStoreActivity>(0) }
		FAB_store.setOnLongClickListener { false }
		FAB_setting.setOnClickListener { startActivity<SettingActivity>() }
		FAM_floatingMenu.setOnMenuToggleListener(object : OnMenuToggleListener {
			var handler = Handler()
			var runnable: Runnable = Runnable { FAM_floatingMenu.close(true) }

			override fun onMenuToggle(opened: Boolean) {
				if (opened) handler.postDelayed(runnable, 5000) else handler.removeCallbacks(runnable)
			}
		})
		P_pack.onEventListener = object : MainPackPanel.OnEventListener {
			override fun onStarClick(v: View) {
				val item = selected
				if (item != null) {
					Thread(Runnable {
						val unipackENT: UnipackENT? = db.unipackDAO()!!.find(item.unipack.F_project.name)
						unipackENT!!.pin = !unipackENT.pin
						db.unipackDAO()!!.update(unipackENT)
					}).start()
				}
			}

			override fun onBookmarkClick(v: View) {
				val item = selected
				if (item != null) {
					Thread(Runnable {
						val unipackENT: UnipackENT? = db.unipackDAO()!!.find(item.unipack.F_project.name)
						unipackENT!!.bookmark = !unipackENT.bookmark
						db.unipackDAO()!!.update(unipackENT)
					}).start()
				}
			}

			override fun onEditClick(v: View) {}
			override fun onStorageClick(v: View) {
				val item = selected
				if (item != null) {
					item.moving = true
					val source = File(item.path)
					val isInternal = FileManager.isInternalFile(this@MainActivity, source)
					val target = File(if (isInternal) F_UniPackRootExt else F_UniPackRootInt, source.name)
					(object : AsyncTask<String?, String?, String?>() {
						override fun onPreExecute() {
							super.onPreExecute()
							P_pack.data.moving.set(true)
						}

						override fun doInBackground(vararg params: String?): String? {
							FileManager.moveDirectory(source, target)
							return null
						}

						override fun onPostExecute(result: String?) {
							super.onPostExecute(result)
							update()
						}
					}).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
				}
			}

			override fun onYoutubeClick(v: View) {
				val item = selected
				if (item != null)
					browse("https://www.youtube.com/results?search_query=UniPad+" + item.unipack.title)
			}

			override fun onWebsiteClick(v: View) {
				val item = selected
				if (item != null) {
					val website: String? = item.unipack.website
					if (website != null)
						browse(website)
				}
			}

			override fun onFuncClick(v: View) {
				val item = selected
				if (item != null) Builder(this@MainActivity)
					.setTitle(getString(string.warning))
					.setMessage(getString(string.doYouWantToRemapProject))
					.setPositiveButton(getString(string.accept)) { _: DialogInterface?, _: Int -> autoMapping(item.unipack) }.setNegativeButton(
						getString(string.cancel),
						null
					)
					.show()
			}

			override fun onDeleteClick(v: View) {
				val item = selected
				if (item != null) Builder(this@MainActivity)
					.setTitle(getString(string.warning))
					.setMessage(getString(string.doYouWantToDeleteProject))
					.setPositiveButton(getString(string.accept)) { _: DialogInterface?, _: Int -> deleteUnipack(item.unipack) }.setNegativeButton(
						getString(string.cancel),
						null
					)
					.show()
			}
		}
		LL_errItem.setOnClickListener { startActivity<FBStoreActivity>() }
		checkThings()
		update(false)
	}

	private fun checkThings() {
		versionCheck()
	}

	private fun update() {
		update(true)
	}

	@SuppressLint("StaticFieldLeak")
	private fun update(animateNew: Boolean) {
		lastPlayIndex = -1
		if (updateProcessing) return
		SRL_swipeRefreshLayout.isRefreshing = true
		updateProcessing = true
		togglePlay(null)
		updatePanel(true)
		(object : AsyncTask<String?, String?, String?>() {
			var I_curr = ArrayList<UnipackItem>()
			var I_added = ArrayList<UnipackItem>()
			var I_removed = ArrayList(list)

			override fun doInBackground(vararg params: String?): String? {
				try {
					for (file: File in getUnipackDirList()) {
						if (!file.isDirectory) continue
						val path: String? = file.path
						val unipack = Unipack(file, false)
						val unipackENT = db.unipackDAO()!!.getOrCreate(unipack.F_project.name)
						val packItem = UnipackItem(unipack, (path)!!, unipackENT.bookmark, animateNew)
						I_curr.add(packItem)
					}
					for (item: UnipackItem in I_curr) {
						var index = -1
						var i = 0
						for (item2: UnipackItem in I_removed) {
							if ((item2.path == item.path)) {
								index = i
								break
							}
							i++
						}
						if (index != -1) I_removed.removeAt(index) else I_added.add(0, item)
					}
				} catch (e: Exception) {
					e.printStackTrace()
				}
				return null
			}

			override fun onPostExecute(result: String?) {
				super.onPostExecute(result)
				for (F_added: UnipackItem in I_added) {
					var i = 0
					val targetTime = FileManager.getInnerFileLastModified(F_added.unipack.F_project)
					for (item: UnipackItem in list) {
						val testTime = FileManager.getInnerFileLastModified(item.unipack.F_project)
						if (targetTime > testTime) break
						i++
					}
					list.add(i, F_added)
					adapter.notifyItemInserted(i)
					P_total.data.unipackCapacity.set(list.size.toString())
				}
				for (F_removed: UnipackItem in I_removed) {
					var i = 0
					for (item: UnipackItem in list) {
						if ((item.path == F_removed.path)) {
							val I = i
							list.removeAt(I)
							adapter.notifyItemRemoved(I)
							P_total.data.unipackCount.set(list.size.toString())
							break
						}
						i++
					}
				}
				if (I_added.size > 0) RV_recyclerView.smoothScrollToPosition(0)
				SRL_swipeRefreshLayout.isRefreshing = false
				updateProcessing = false
			}
		}).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
	}

	// ============================================================================================= UniPack Work


	private fun deleteUnipack(unipack: Unipack) {
		FileManager.deleteDirectory(unipack.F_project)
		update()
	}

	@SuppressLint("StaticFieldLeak")
	private fun autoMapping(unipack: Unipack) {
		UnipackAutoMapper(unipack, object : UnipackAutoMapper.Listener {
			val progressDialog: ProgressDialog = ProgressDialog(this@MainActivity)
			override fun onStart() {
				progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
				progressDialog.setTitle(getString(string.analyzing))
				progressDialog.setMessage(getString(string.wait_a_sec))
				progressDialog.setCancelable(false)
				progressDialog.show()
			}

			override fun onGetWorkSize(size: Int) {
				progressDialog.max = size
			}

			override fun onProgress(progress: Int) {
				progressDialog.progress = progress
			}

			override fun onDone() {
				if (progressDialog.isShowing) progressDialog.dismiss()

				Builder(this@MainActivity)
					.setTitle(getString(string.success))
					.setMessage(getString(string.remapDone))
					.setPositiveButton(getString(string.accept), null)
					.show()
			}

			override fun onException(throwable: Throwable) {
				if (progressDialog.isShowing) progressDialog.dismiss()


				Builder(this@MainActivity)
					.setTitle(getString(string.failed))
					.setMessage(getString(string.remapFail))
					.setPositiveButton(getString(string.accept), null)
					.show()
			}
		})
	}

	@SuppressLint("StaticFieldLeak")
	private fun loadUnipack(F_UniPackZip: File) {
		(object : AsyncTask<String?, String?, String?>() {
			var progressDialog = ProgressDialog(this@MainActivity)
			var msg1: String? = null
			var msg2: String? = null
			override fun onPreExecute() {
				progressDialog.setTitle(getString(string.analyzing))
				progressDialog.setMessage(getString(string.wait_a_sec))
				progressDialog.setCancelable(false)
				progressDialog.show()
				super.onPreExecute()
			}

			override fun doInBackground(vararg params: String?): String? {
				val name: String = F_UniPackZip.name
				val name_ = name.substring(0, name.lastIndexOf("."))
				val F_UniPack: File = FileManager.makeNextPath(F_UniPackRootExt, name_, "/")
				try {
					FileManager.unZipFile(F_UniPackZip.path, F_UniPack.path)
					val unipack = Unipack(F_UniPack, true)
					if (unipack.errorDetail == null) {
						msg1 = getString(string.analyzeComplete)
						msg2 = unipack.toString(this@MainActivity)
					} else if (unipack.criticalError) {
						msg1 = getString(string.analyzeFailed)
						msg2 = unipack.errorDetail
						FileManager.deleteDirectory(F_UniPack)
					} else {
						msg1 = getString(string.warning)
						msg2 = unipack.errorDetail
					}
				} catch (e: Exception) {
					msg1 = getString(string.analyzeFailed)
					msg2 = e.toString()
					FileManager.deleteDirectory(F_UniPack)
				}
				return null
			}

			override fun onProgressUpdate(vararg progress: String?) {}
			override fun onPostExecute(result: String?) {
				update()
				alert(msg1!!, msg2!!) {
					positiveButton(string.accept) { it.dismiss() }
				}.show()
				progressDialog.dismiss()
				super.onPostExecute(result)
			}
		}).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
	}

	// ============================================================================================= List Manage


	private fun togglePlay(i: Int) {
		togglePlay(list[i])
	}

	@SuppressLint("SetTextI18n")
	fun togglePlay(target: UnipackItem?) {
		try {
			var i = 0
			for (item: UnipackItem in list) {
				val packView = item.packView
				if (target != null && (item.path == target.path)) {
					item.toggle = !item.toggle
					lastPlayIndex = i
				} else item.toggle = false
				packView?.toggle(item.toggle)
				i++
			}
			showSelectLPUI()
			updatePanel(false)
		} catch (e: ConcurrentModificationException) {
			e.printStackTrace()
		}
	}

	fun pressPlay(item: UnipackItem) {
		Thread(Runnable { db.unipackOpenDAO()!!.insert(UnipackOpenENT(item.unipack.F_project.name, Date())) }).start()
		startActivity<PlayActivity>("path" to item.path)
		removeController((midiController))
	}

	private val selectedIndex: Int
		get() {
			var index = -1
			var i = 0
			for (item: UnipackItem in list) {
				if (item.toggle) {
					index = i
					break
				}
				i++
			}
			return index
		}

	private val selected: UnipackItem?
		get() {
			var ret: UnipackItem? = null
			val playIndex = selectedIndex
			if (playIndex != -1) ret = list[playIndex]
			return ret
		}

	// ============================================================================================= panel


	@SuppressLint("SetTextI18n")
	private fun initPanel() {
		P_total.data.logo.set(resources.getDrawable(drawable.custom_logo))
		P_total.data.version.set(BuildConfig.VERSION_NAME)
	}

	private fun updatePanel(hardWork: Boolean) {
		val selectedIndex = selectedIndex
		val animation: Animation = AnimationUtils.loadAnimation(this@MainActivity, if (selectedIndex != -1) anim.panel_in else anim.panel_out)
		animation.setAnimationListener(object : AnimationListener {
			override fun onAnimationStart(animation: Animation?) {
				P_pack.visibility = View.VISIBLE
				P_pack.alpha = 1f
			}

			override fun onAnimationEnd(animation: Animation?) {
				P_pack.visibility = if (selectedIndex != -1) View.VISIBLE else View.INVISIBLE
				P_pack.alpha = if (selectedIndex != -1) 1.toFloat() else 0.toFloat()
			}

			override fun onAnimationRepeat(animation: Animation?) {}
		})
		if (selectedIndex == -1) updatePanelMain(hardWork) else updatePanelPack(list[selectedIndex])
		val visibility = P_pack.visibility
		if (((visibility == View.VISIBLE && selectedIndex == -1)
					|| (visibility == View.INVISIBLE && selectedIndex != -1))
		) P_pack.startAnimation(animation)
	}

	@SuppressLint("StaticFieldLeak")
	private fun updatePanelMain(hardWork: Boolean) {
		P_total.data.unipackCount.set(list.size.toString())
		db.unipackOpenDAO()!!.count!!.observe(this, Observer { integer: Int? -> P_total.data.openCount.set(integer.toString()) })
		P_total.data.padTouchCount.set(getString(string.measuring))
		val packageName: String? = preference.selectedTheme
		try {
			val resources = ThemeResources(this@MainActivity, packageName, false)
			P_total.data.selectedTheme.set(resources.name)
		} catch (e: Exception) {
			P_total.data.selectedTheme.set(getString(string.theme_name))
		}
		if (hardWork) (object : AsyncTask<String?, String?, String?>() {
			override fun doInBackground(vararg params: String?): String? {
				val fileSize = FileManager.byteToMB(FileManager.getFolderSize(F_UniPackRootExt)) + " MB"
				publishProgress(fileSize)
				return null
			}

			override fun onProgressUpdate(vararg values: String?) {
				P_total.data.unipackCapacity.set(values[0])
			}
		}).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
	}

	@SuppressLint("StaticFieldLeak", "SetTextI18n")
	private fun updatePanelPack(item: UnipackItem) {
		val unipack = item.unipack
		Thread(Runnable {
			var unipackENT: UnipackENT = db.unipackDAO()!!.find(item.unipack.F_project.name)!!
			var flagColor: Int = if (unipack.criticalError) colors.red else colors.skyblue
			item.flagColor = flagColor

			P_pack.data.star.set(unipackENT.pin)
			P_pack.data.bookmark.set(unipackENT.bookmark)
		}).start()
		db.unipackOpenDAO()!!.getCount(item.unipack.F_project.name)!!.observe(
			this,
			Observer { integer: Int? -> P_pack.data.openCount.set(integer.toString()) })
		P_pack.data.storage.set(!FileManager.isInternalFile(this@MainActivity, unipack.F_project))
		P_pack.data.title.set(unipack.title)
		P_pack.data.subtitle.set(unipack.producerName)
		P_pack.data.path.set(item.path)
		P_pack.data.scale.set("${unipack.buttonX} × ${unipack.buttonY}")
		P_pack.data.chainCount.set(unipack.chain.toString())
		P_pack.data.soundCount.set(getString(string.measuring))
		P_pack.data.ledCount.set(getString(string.measuring))
		P_pack.data.fileSize.set(getString(string.measuring))
		P_pack.data.padTouchCount.set(getString(string.measuring))
		P_pack.data.websiteExist.set(unipack.website != null)
		(object : AsyncTask<String?, String?, String?>() {
			var handler = Handler()
			override fun doInBackground(vararg params: String?): String? {
				val fileSize = FileManager.byteToMB(FileManager.getFolderSize(unipack.F_project)) + " MB"
				handler.post { if ((P_pack.data.path.get() == item.path)) P_pack.data.fileSize.set(fileSize) }
				try {
					val unipackDetail = Unipack(item.unipack.F_project, true)
					item.unipack = unipackDetail
					publishProgress(fileSize)
					handler.post {
						if ((P_pack.data.path.get() == item.path)) {
							P_pack.data.soundCount.set(unipackDetail.soundCount.toString())
							P_pack.data.ledCount.set(unipackDetail.ledTableCount.toString())
						}
					}
				} catch (e: Exception) {
					e.printStackTrace()
				}
				return null
			}
		}).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
	}

	// ============================================================================================= Check


	private fun versionCheck() {
		val thisVersion = BuildConfig.VERSION_NAME
		val currVersionJson = FirebaseRemoteConfig.getInstance().getString("android_version")
		if (currVersionJson.isNotEmpty()) {
			val gson: Gson = GsonBuilder().create()
			val currVersionList: List<String> = gson.fromJson(currVersionJson, object : TypeToken<List<String?>?>() {}.type)
			if (!currVersionList.contains(thisVersion))
				CL_root.snackbar("${getString(string.newVersionFound)}\n$thisVersion → ${currVersionList[0]}", getString(string.update)) {
					browse("https://play.google.com/store/apps/details?id=$packageName")
				}
		}
	}

	private fun blink(bool: Boolean) {
		if (bool) storeAnimator.start() else storeAnimator.end()
	}

	// ============================================================================================= Controller


	private fun updateLP() {
		showWatermark()
		showSelectLPUI()
	}

	private fun haveNow(): Boolean {
		return 0 <= lastPlayIndex && lastPlayIndex <= list.size - 1
	}

	private fun haveNext(): Boolean {
		return lastPlayIndex < list.size - 1
	}

	private fun havePrev(): Boolean {
		return 0 < lastPlayIndex
	}

	private fun showSelectLPUI() {
		if (havePrev()) driver.sendFunctionkeyLED(0, 63) else driver.sendFunctionkeyLED(0, 5)
		if (haveNow()) driver.sendFunctionkeyLED(2, 61) else driver.sendFunctionkeyLED(2, 0)
		if (haveNext()) driver.sendFunctionkeyLED(1, 63) else driver.sendFunctionkeyLED(1, 5)
	}

	private fun showWatermark() {
		driver.sendPadLED(3, 3, 61)
		driver.sendPadLED(3, 4, 40)
		driver.sendPadLED(4, 3, 40)
		driver.sendPadLED(4, 4, 61)
	}

	// ============================================================================================= Activity


	override fun onBackPressed() {
		if (selectedIndex != -1) togglePlay(null) else super.onBackPressed()
	}

	override fun onResume() {
		super.onResume()
		checkThings()
		Handler().postDelayed({ update() }, 1000)
		controller = midiController
		firebase_storeCount.attachEventListener(true)
	}

	public override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
		super.onActivityResult(requestCode, resultCode, intent)
		when (requestCode) {
			0 -> checkThings()
			else -> {
			}
		}
	}

	override fun onPause() {
		super.onPause()
		controller = midiController
		firebase_storeCount.attachEventListener(false)
	}

	override fun onDestroy() {
		super.onDestroy()
		removeController(midiController)
	}
}