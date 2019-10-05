package com.kimjisub.launchpad.activity

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.os.Process
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest.Builder
import com.google.android.gms.ads.InterstitialAd
import com.kimjisub.launchpad.R.anim
import com.kimjisub.launchpad.R.string
import com.kimjisub.launchpad.manager.ColorManager
import com.kimjisub.launchpad.manager.Constant
import com.kimjisub.launchpad.manager.PreferenceManager
import com.kimjisub.manager.FileManager
import com.kimjisub.manager.Log
import com.vungle.warren.InitCallback
import com.vungle.warren.Vungle
import org.jetbrains.anko.startActivity
import java.io.File
import java.util.*

open class BaseActivity : AppCompatActivity() {

	companion object {

		private var interstitialAd: InterstitialAd? = null


		var activityList = ArrayList<Activity>()
		internal fun onStartActivity(activity: Activity) {
			activityList.add(activity)
			printActivityLog(activity.localClassName + " start")
		}

		internal fun onFinishActivity(activity: Activity) {
			var exist = false
			val size = activityList.size
			for (i in 0 until size) {
				if (activityList[i] === activity) {
					activityList[i].finish()
					activityList.removeAt(i)
					exist = true
					break
				}
			}
			printActivityLog(activity.localClassName + " finish" + if (exist) "" else " error")
		}

		internal fun restartApp(activity: Activity) {
			val size = activityList.size
			for (i in size - 1 downTo 0) {
				activityList[i].finish()
				activityList.removeAt(i)
			}
			activity.startActivity<MainActivity>()
			printActivityLog(activity.localClassName + " requestRestart")
			Process.killProcess(Process.myPid())
		}

		internal fun printActivityLog(log: String) {
			val str = StringBuilder("ACTIVITY STACK - $log[")
			val size = activityList.size
			for (i in 0 until size) {
				val activity = activityList[i]
				str.append(", ").append(activity.localClassName)
			}
			Log.activity("$str]")
		}

		fun requestRestart(context: Context) {
			AlertDialog.Builder(context)
				.setTitle(context.getString(string.requireRestart))
				.setMessage(context.getString(string.doYouWantToRestartApp))
				.setPositiveButton(context.getString(string.restart)) { dialog: DialogInterface, which: Int ->
					restartApp(context as Activity)
					dialog.dismiss()
				}
				.setNegativeButton(context.getString(string.cancel)) { dialog: DialogInterface, which: Int ->
					dialog.dismiss()
					(context as Activity).finish()
				}
				.show()
		}
	}

	val preference: PreferenceManager by lazy { PreferenceManager(applicationContext) }

	////////////////////////////////////////////////////////////////////////////////////////////////
	val F_UniPackRootExt: File by lazy {
		val externalPath = FileManager.getExternalUniPackRoot()

		//todo thread
		FileManager.makeDirWhenNotExist(externalPath)
		FileManager.makeNomedia(externalPath)
		externalPath
	}
	val F_UniPackRootInt: File by lazy {
		FileManager.getInternalUniPackRoot(applicationContext)
	}

	// ============================================================================================= Function

	fun getUnipackDirList(): Array<File> {
		val projectFiles: Array<File> = F_UniPackRootExt.listFiles() + F_UniPackRootInt.listFiles()
		return FileManager.sortByTime(projectFiles)
	}

	// ============================================================================================= Ads


	fun checkAdsCooltime(): Boolean {
		val prevTime = preference.PrevAdsShowTime
		val currTime = System.currentTimeMillis()
		return currTime < prevTime || currTime - prevTime >= Constant.ADSCOOLTIME
	}

	fun updateAdsCooltime() {
		val currTime = System.currentTimeMillis()
		preference.PrevAdsShowTime = currTime
	}

	fun initVungle() {
		if (!Vungle.isInitialized()) {
			Log.vungle("isInitialized() == false")
			Log.vungle("init start")
			Vungle.init(Constant.VUNGLE.APPID, applicationContext, object : InitCallback {
				override fun onSuccess() {
					// Initialization has succeeded and SDK is ready to load an ad or play one if there
					// is one pre-cached already

					Log.vungle("init onSuccess()")
				}

				override fun onError(throwable: Throwable) {
					// Initialization error occurred - throwable.getLocalizedMessage() contains error message

					Log.vungle("init onError() == " + throwable.localizedMessage)
				}

				override fun onAutoCacheAdAvailable(placementId: String?) {
					// Callback to notify when an ad becomes available for the auto-cached placement
					// NOTE: This callback works only for the auto-cached placement. Otherwise, please use
					// LoadAdCallback with loadAd API for loading placements.

					Log.vungle("init onAutoCacheAdAvailable()")
				}
			})
		} else {
			Log.vungle("isInitialized() == true")
		}
	}

	fun showAdmob() {
		Log.admob("showAdmob ================================")
		val isLoaded = interstitialAd!!.isLoaded
		Log.admob("isLoaded: $isLoaded")
		if (isLoaded) {
			interstitialAd!!.show()
			loadAdmob()
			Log.admob("show!")
		} else {
			interstitialAd!!.adListener = object : AdListener() {
				override fun onAdLoaded() {
					interstitialAd!!.show()
					loadAdmob()
					Log.admob("show!")
				}
			}
		}
	}

	internal fun loadAdmob() {
		Log.admob("loadAdmob ================================")
		interstitialAd = InterstitialAd(this)
		interstitialAd!!.adUnitId = Constant.ADMOB.MAIN_START
		interstitialAd!!.loadAd(
			Builder()
				.addTestDevice("36C3684AAD25CDF5A6360640B20DC084")
				.build()
		)
	}

	// ============================================================================================= Show Things, Get Resources


	val colors by lazy { ColorManager(this) }


	// ============================================================================================= Activity Cycle


	override fun startActivity(intent: Intent) {
		super.startActivity(intent)
		overridePendingTransition(anim.activity_in, anim.activity_out)
	}

	public override fun onCreate(savedInstanceState: Bundle?) {
		Log.activity("onCreate " + this.localClassName)
		super.onCreate(savedInstanceState)

		/*Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
			@Override
			public void uncaughtException(Thread paramThread, Throwable paramThrowable) {
				paramThrowable.printStackTrace();
			}
		});*/

		onStartActivity(this)
	}

	public override fun onStart() {
		Log.activity("onStart " + this.localClassName)
		super.onStart()
	}

	public override fun onResume() {
		Log.activity("onResume " + this.localClassName)
		super.onResume()
		this.volumeControlStream = AudioManager.STREAM_MUSIC
		initVungle()
	}

	public override fun onPause() {
		Log.activity("onPause " + this.localClassName)
		super.onPause()
	}

	public override fun onStop() {
		Log.activity("onStop " + this.localClassName)
		super.onStop()
	}

	public override fun onRestart() {
		Log.activity("onRestart " + this.localClassName)
		super.onRestart()
	}

	public override fun onDestroy() {
		Log.activity("onDestroy " + this.localClassName)
		super.onDestroy()
		onFinishActivity(this)
	}
}