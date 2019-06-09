package com.kimjisub.launchpad;

import android.os.Bundle;

import com.kimjisub.launchpad.databinding.ActivityImportpackBinding;
import com.kimjisub.launchpad.manager.PreferenceManager;
import com.kimjisub.launchpad.networks.api.UniPadApi;
import com.kimjisub.launchpad.networks.api.vo.UnishareVO;

import java.io.File;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ImportPackByUrlActivity extends BaseActivity {
	ActivityImportpackBinding b;

	File F_UniPackRoot;
	File F_UniPackZip;
	File F_UniPack;

	String code;

	void initVar() {
		F_UniPackRoot = new File(PreferenceManager.IsUsingSDCard.getPath(ImportPackByUrlActivity.this));
		//UnipackZipPath
		//UnipackPath

		code = getIntent().getData().getQueryParameter("code");
		log("code: " + code);
		setStatus(Status.prepare, code);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		b = setContentViewBind(R.layout.activity_importpack);
		initVar();


		UniPadApi.getService().makeUrl_get(code).enqueue(new Callback<UnishareVO>() {
			@Override
			public void onResponse(Call<UnishareVO> call, Response<UnishareVO> response) {
				if (response.isSuccessful()) {
					UnishareVO unishareVO = response.body();
					setStatus(Status.prepare, code + "\n" + unishareVO.title + "\n" + unishareVO.producerName);
					log("title: " + unishareVO.title);
					log("producerName: " + unishareVO.producerName);
					F_UniPackZip = FileManager.makeNextPath(F_UniPackRoot, unishareVO.title + " #" + code, ".zip");
					F_UniPack = FileManager.makeNextPath(F_UniPackRoot, unishareVO.title + " #" + code, "/");
					new DownloadTask(response.body()).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
					addCount(code);
				} else {
					switch (response.code()) {
						case 404:
							log("404 Not Found");
							setStatus(Status.notFound, "Not Found");
							break;
					}
				}
			}

			@Override
			public void onFailure(Call<UnishareVO> call, Throwable t) {
				log("server error");
				setStatus(Status.failed, "server error\n" + t.getMessage());
			}
		});
	}

	void addCount(String code) {
		UniPadApi.getService().makeUrl_addCount(code).enqueue(new Callback<ResponseBody>() {
			@Override
			public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
			}

			@Override
			public void onFailure(Call<ResponseBody> call, Throwable t) {
			}
		});
	}

	void setStatus(Status status, String msg) {
		runOnUiThread(() -> {
			switch (status) {
				case prepare:
					TV_title.setText(R.string.wait);
					TV_message.setText(msg);
					break;
				case downloading:
					TV_title.setText(R.string.downloading);
					TV_message.setText(msg);
					break;
				case analyzing:
					TV_title.setText(R.string.analyzing);
					TV_message.setText(msg);
					break;
				case success:
					TV_title.setText(R.string.success);
					TV_message.setText(msg);
					break;
				case notFound:
					TV_title.setText(R.string.unipackNotFound);
					TV_message.setText(msg);
					break;
				case failed:
					TV_title.setText(R.string.failed);
					TV_message.setText(msg);
					break;
			}
		});
	}

	void log(String msg) {
		runOnUiThread(() -> TV_info.append(msg + "\n"));
	}

	void delayFinish() {
		log("delayFinish()");
		new Handler().postDelayed(() -> restartApp(this), 3000);
	}

	@Override
	public void onResume() {
		super.onResume();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}


	enum Status {prepare, downloading, analyzing, success, notFound, failed}

	class DownloadTask extends AsyncTask<String, String, String> {
		String code;
		String title;
		String producerName;
		String url;
		int fileSize;
		int downloadCount;

		public DownloadTask(UnishareVO unishareVO) {
			this.code = unishareVO.code;
			this.title = unishareVO.title;
			this.producerName = unishareVO.producerName;
			this.url = unishareVO.url;
			this.fileSize = unishareVO.fileSize;
			this.downloadCount = unishareVO.downloadCount;
		}

		@Override
		protected void onPreExecute() {
			log("Download Task onPreExecute()");
			super.onPreExecute();
		}

		@Override
		protected String doInBackground(String[] params) {
			log("Download Task doInBackground()");

			try {

				java.net.URL downloadUrl = new URL(url);
				HttpURLConnection conexion = (HttpURLConnection) downloadUrl.openConnection();
				conexion.setConnectTimeout(5000);
				conexion.setReadTimeout(5000);

				int fileSize_ = conexion.getContentLength();
				fileSize = fileSize_ == -1 ? fileSize : fileSize_;
				log("fileSize : " + fileSize);

				InputStream input = new BufferedInputStream(downloadUrl.openStream());
				OutputStream output = new FileOutputStream(F_UniPackZip);

				byte data[] = new byte[1024];
				long total = 0;
				int count;
				int progress = 0;
				log("Download start");
				while ((count = input.read(data)) != -1) {
					total += count;
					progress++;
					if (progress % 100 == 0) {

						setStatus(ImportPackByUrlActivity.Status.downloading, (int) ((float) total / fileSize * 100) + "%\n" + FileManager.byteToMB(total) + " / " + FileManager.byteToMB(fileSize) + "MB");
					}
					output.write(data, 0, count);
				}
				log("Download End");

				output.flush();
				output.close();
				input.close();

				log("Analyzing Start");
				setStatus(ImportPackByUrlActivity.Status.analyzing, code + "\n" + title + "\n" + producerName);

				try {
					FileManager.unZipFile(F_UniPackZip.getPath(), F_UniPack.getPath());
					Unipack unipack = new Unipack(F_UniPack, true);
					if (unipack.CriticalError) {
						Log.err(unipack.ErrorDetail);
						setStatus(ImportPackByUrlActivity.Status.failed, unipack.ErrorDetail);
						FileManager.deleteDirectory(F_UniPack);
					} else
						setStatus(ImportPackByUrlActivity.Status.success, unipack.getInfoText(ImportPackByUrlActivity.this));

					log("Analyzing End");
				} catch (Exception e) {
					e.printStackTrace();
					log("Analyzing Error");
					setStatus(ImportPackByUrlActivity.Status.failed, e.toString());
					log("DeleteFolder: UnipackPath " + F_UniPack.getPath());
					FileManager.deleteDirectory(F_UniPack);
				}

				log("DeleteFolder: UnipackZipPath " + F_UniPackZip.getPath());
				FileManager.deleteDirectory(F_UniPackZip);

			} catch (Exception e) {
				e.printStackTrace();
				log("Download Task doInBackground() ERROR");
				setStatus(ImportPackByUrlActivity.Status.failed, e.toString());
			}


			return null;
		}

		@Override
		protected void onProgressUpdate(String... progress) {
		}

		@Override
		protected void onPostExecute(String unused) {
			log("Download Task onPostExecute()");
			delayFinish();
		}
	}
}
