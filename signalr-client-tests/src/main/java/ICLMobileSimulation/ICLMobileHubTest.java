package ICLMobileSimulation;

import com.google.gson.Gson;

import org.apache.commons.io.IOUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import microsoft.aspnet.signalr.client.Action;
import microsoft.aspnet.signalr.client.ConnectionState;
import microsoft.aspnet.signalr.client.ErrorCallback;
import microsoft.aspnet.signalr.client.LogLevel;
import microsoft.aspnet.signalr.client.Logger;
import microsoft.aspnet.signalr.client.hubs.HubConnection;
import microsoft.aspnet.signalr.client.hubs.HubProxy;
import microsoft.aspnet.signalr.client.hubs.SubscriptionHandler;
import microsoft.aspnet.signalr.client.hubs.SubscriptionHandler1;
import microsoft.aspnet.signalr.client.transport.WebsocketTransport;

public class ICLMobileHubTest {
	static HubProxy mHub;
	static HubConnection mConnection;
	static String server_url = "http://127.0.0.1:4520";
	static String hubName = "ICLMobileHub";
	static String user = "ICLUser1";
	static String token = "cb3e047c-e79c-4ff3-b7e7-ca13a3640c66";
	static Timer _keepAliveTimer;
	static int keepAliveInterval = 5000;

	public static void main(String args[]) throws IOException {
		SystemLog("Main is start");

		String[] authInfo = getAuthorizeSession(server_url, user, token);
		initConnection(server_url, authInfo[1]);
		startConnection();

		System.in.read();
	}

	private static void initConnection(String host, String session) {
		SystemLog("initConnection...");
		try {
			mConnection = new HubConnection(host + "/signalr", "session="
					+ session, true, new Logger() {
				@Override
				public void log(String msg, LogLevel logLevel) {

				}
			});
			mHub = mConnection.createHubProxy(hubName);
		} catch (Exception e) {
			SystemLog("Error getting in creating hub proxy.");
		}

		mConnection.connected(new Runnable() {
			@Override
			public void run() {
				SystemLog("-----> CONNECTED <-----");
			}
		});
		mConnection.error(new ErrorCallback() {
			@Override
			public void onError(Throwable throwable) {
				SystemLog("BASE.ERROR-> " + throwable.getMessage());
			}
		});
		mConnection.closed(new Runnable() {
			@Override
			public void run() {
				SystemLog("-----> DISCONNECTED <-----");
				if (_keepAliveTimer != null) _keepAliveTimer.cancel();
			}
		});
		mConnection.reconnecting(new Runnable() {
			@Override
			public void run() {
				SystemLog("RECONNECTING...");
			}
		});
		mConnection.reconnected(new Runnable() {
			@Override
			public void run() {
				SystemLog("-----> RECONNECTED <-----");
			}
		});

		mHub.on("SyncEvent", new SubscriptionHandler() {
			@Override
			public void run() {
				SystemLog("\tSync Event receive -> \n");
			}
		});

		mHub.on("SyncString", new SubscriptionHandler1<String>() {
			@Override
			public void run(String msg) {
				SystemLog("\tSyncString receive -> " + msg.split(",").length + "\n");
			}
		}, String.class);

		mHub.on("SyncStringArray", new SubscriptionHandler1<String[]>() {
			@Override
			public void run(String[] msg) {
				SystemLog("\tSyncStringArray receive -> " + msg.length + "\n");
			}
		}, String[].class);

		mHub.on("SyncMasterCameras", new SubscriptionHandler1<MasterCameraInfo[]>() {
			@Override
			public void run(MasterCameraInfo[] msg) {
				SystemLog("\tSyncMasterCameras receive -> " + msg.length + "\n");
			}
		}, MasterCameraInfo[].class);
	}

	private static void startConnection() {
		mConnection.start(new WebsocketTransport(new Logger() {
			@Override
			public void log(String s, LogLevel logLevel) {
				if(s.contains("onFragment")) {
					//if(s.contains("Listener message")) SystemLog(s + "\n");
					//if(s.contains("Invoked message")) SystemLog(s + "\n");
				}
			}
		})).done(new Action<Void>() {
			@Override
			public void run(Void obj) throws Exception {
				SystemLog("CONNECTING DONE.");
				KeepAliveConnection();

				getSampleMasterCameraObject();
				pullMasterCameras1();
				getSampleMasterCameraList();
				getSampleString();
				getSampleStringArray();
				startHubMethodHandler();
				SystemLog("\n");
			}
		}).onError(new ErrorCallback() {
			@Override
			public void onError(Throwable throwable) {
				SystemLog("CONN.ERROR-> " + throwable.getMessage());
			}
		});
	}

	private static String[] getAuthorizeSession(final String host,final String user, final String token) {
		String[] authInfo = new String[2];
		try {
			String query = "username=" + user + "&token=" + token;
			HttpResponseMessage response = executeAuthorizeSession(host + "/login", query);
			if (response.StatusCode == HttpURLConnection.HTTP_OK) {
				String authRes = response.StatusDetail;
				authInfo = authRes.split(";");
				SystemLog("Get Auth Session " + authInfo[0] + ", " + authInfo[1]);
			} else {
				SystemLog("Error in authorize session [" + response.StatusCode + "] , " + response.StatusDetail);
			}
		} catch (Exception e) {
			SystemLog("Exception " + e.getMessage());
		}
		return authInfo;
	}

	public static HttpResponseMessage executeAuthorizeSession(String targetUrl, String postParams) throws Exception {
		URL url;
		String encoding = "UTF-8";
		HttpURLConnection conn = null;
		HttpResponseMessage responseMessage = new HttpResponseMessage();
		try {
			//Create connection
			url = new URL(targetUrl);
			conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("POST");
			conn.setUseCaches(false);
			conn.setDoInput(true);
			conn.setDoOutput(true);

			//send the POST param
			OutputStream outStream = conn.getOutputStream();
			BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outStream, encoding));
			writer.write(postParams);
			writer.flush();
			writer.close();
			conn.connect();

			// handle issues
			responseMessage.StatusCode = conn.getResponseCode();
			if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
				try {
					String jsonStr = IOUtils.toString(conn.getInputStream(), encoding);
					UserSessionInfo userInfo = new Gson().fromJson(jsonStr, UserSessionInfo.class);
					responseMessage.StatusDetail = userInfo.UserId + ";" + userInfo.UserSessionId;
				} catch (Exception e) {
				}
			}else if(conn.getErrorStream() != null){
				responseMessage.StatusDetail = IOUtils.toString(conn.getErrorStream(), encoding);
			}
		} catch (Exception ex) {
			throw ex;
		} finally {
			if (conn != null) {
				conn.disconnect();
			}
		}
		return responseMessage;
	}

	@SuppressWarnings("unchecked")
	private static void invoke(final String method, @SuppressWarnings("rawtypes") Action success, final ErrorCallback error, Object... args) {
		invoke(null, method, success, error, args);
	}

	private static <E> void invoke(final Class<E> resultClass,final String method, Action<E> success, final ErrorCallback error,Object... args) {
		if (mHub == null) {
			error.onError(new Throwable("Hub connection cannot be null."));
		} else if (mConnection.getState() != ConnectionState.Connected) {
			error.onError(new Throwable(
					"Hub connection is in the 'Disconnected' state."));
		} else {
			mHub.invoke(resultClass, method, args).done(success)
					.onError(new ErrorCallback() {
						@Override
						public void onError(Throwable throwable) {
							// error.onError(TryGetErrorCode(throwable));
						}
					});
		}
	}
	
	private static void KeepAliveConnection() {
		if (_keepAliveTimer != null)
			_keepAliveTimer.cancel();
		_keepAliveTimer = new Timer();
		_keepAliveTimer.scheduleAtFixedRate(new TimerTask() {
			@SuppressWarnings("rawtypes")
			@Override
			public void run() {
				try {
					SystemLog("Sending KeepAlive -> ConnId: " + mConnection.getConnectionId());
					invoke("KeepAlive", new Action() {
						@Override
						public void run(Object o) throws Exception {
							//SystemLog("Sending KeepAlive done.");
						}
					}, new ErrorCallback() {
						@Override
						public void onError(Throwable throwable) {
							SystemLog("Error on sending KeepALive => " + throwable.getMessage());
						}
					});
				} catch (Exception e) {
					SystemLog("keepALive Exception: " + e.getMessage());
				}
			}
		}, 1000, keepAliveInterval);
	}

	private static void pullMasterCameras1() {
		SystemLog("\tStart pullMasterCameras1 -> ");
		if (mHub != null) {
			invoke(MasterCameraInfo[].class, "GetMasterCameras",
					new Action<MasterCameraInfo[]>() {
						@Override
						public void run(MasterCameraInfo[] result) throws Exception {
							SystemLog("\tReceived getMasterCameraInfo result");
							if (result.length > 0) {
								List<MasterCameraInfo> mCameraInfo = Arrays.asList(result);
								SystemLog("\tReceived MasterCameraInfo count " + mCameraInfo.size() + "\n");
							}
						}
					}, new ErrorCallback() {
						@Override
						public void onError(Throwable error) {
							SystemLog("\tError on getMasterCameraInfo => " + error.getMessage());
						}
					});
		}
	}

	private static void pullMasterCameras2() {
		SystemLog("	Start pullMasterCameras2 -> ");
		if (mHub != null) {
			mHub.invoke(MasterCameraInfo[].class, "GetSampleMasterCameraInfo")
					.done(new Action<MasterCameraInfo[]>() {
						@Override
						public void run(MasterCameraInfo[] result)throws Exception {
							SystemLog("	Received getMasterCameraInfo result");
							if (result.length > 0) {
								List<MasterCameraInfo> mCameraInfo = Arrays.asList(result);
								SystemLog("	Received MasterCameraInfo count " + mCameraInfo.size());
							}
						}
					}).onError(new ErrorCallback() {
				@Override
				public void onError(Throwable error) {
					SystemLog("Error on getMasterCameraInfo => " + error.getMessage());
				}
			});
		}
	}

	private static void pullLiveVideo(int cameraId){
		SystemLog("	Start pull LiveVideo -> ");
		if (mHub != null) {
			invoke(VideoInfo.class, "PullLiveVideos",
					new Action<VideoInfo>() {
						@Override
						public void run(final VideoInfo videoInfo) throws Exception {
							SystemLog("	Received pull live video result");
							if (videoInfo != null)
								SystemLog("	LiveVideo result ID:" + videoInfo.CameraId + ", Url:" + videoInfo.StreamUrl);
						}
					}, new ErrorCallback() {
						@Override
						public void onError(Throwable error) {
							SystemLog("Failed pull live video result Error => " + error.toString());
						}
					}, cameraId);
		}
	}

	private static void getSampleMasterCameraObject() {
		SystemLog("\tGet Sample MasterCameraObject -> ");
		if (mHub != null) {
			invoke(MasterCameraInfo.class, "GetSampleMasterCameraObject",
					new Action<MasterCameraInfo>() {
						@Override
						public void run(MasterCameraInfo result) throws Exception {
							SystemLog("\tReceived MasterCameraObject result");
							if (result != null) {
								SystemLog("\tReceived MasterCameraObject count " + result.toString() + "\n");
							}
						}
					}, new ErrorCallback() {
						@Override
						public void onError(Throwable error) {
							SystemLog("\tError on MasterCameraObject => " + error.getMessage());
						}
					});
		}
	}

	private static void getSampleMasterCameraList() {
		SystemLog("\tGet Sample MasterCameraList -> ");
		if (mHub != null) {
			invoke(MasterCameraInfo[].class, "GetSampleMasterCameraList",
					new Action<MasterCameraInfo[]>() {
						@Override
						public void run(MasterCameraInfo[] result) throws Exception {
							SystemLog("\tReceived MasterCameraList result");
							if (result.length > 0) {
								List<MasterCameraInfo> mCameraInfo = Arrays.asList(result);
								SystemLog("\tReceived MasterCameraList count " + mCameraInfo.size() + "\n");
							}
						}
					}, new ErrorCallback() {
						@Override
						public void onError(Throwable error) {
							SystemLog("\tError on MasterCameraList => " + error.getMessage());
						}
					});
		}
	}
	
	private static void getSampleString(){
		SystemLog("\tStart get SampleString -> ");
		if (mHub != null) {
			invoke(String.class, "GetSampleString",
					new Action<String>() {
						@Override
						public void run(String result) throws Exception {
							SystemLog("\tReceived SampleString result");
							if (result.length() > 0) {
								String[] resultArr = result.split(",");
								SystemLog("\tReceived SampleString count " + resultArr.length + "\n");
								new Timer().schedule(new TimerTask() {
									@Override
									public void run() {
										//getSampleStringArray();
									}
								}, 5000);
							}
						}
					}, new ErrorCallback() {
						@Override
						public void onError(Throwable error) {
							SystemLog("\tError on SampleString => " + error.getMessage());
						}
					});
		}
		
	}

	private static void getSampleStringArray(){
		SystemLog("\tStart get SampleArray -> ");
		if (mHub != null) {
			invoke(String[].class, "GetSampleArray",
					new Action<String[]>() {
						@Override
						public void run(String[] result) throws Exception {
							SystemLog("\tReceived SampleArray result");
							if (result.length > 0) {
								SystemLog("\tReceived SampleArray size " + result.length + "\n");
								new Timer().schedule(new TimerTask() {
									@Override
									public void run() {
										//getSampleString();
									}
								}, 5000);
							}
						}
					}, new ErrorCallback() {
						@Override
						public void onError(Throwable error) {
							SystemLog("\tError on SampleArray => " + error.getMessage());
						}
					});
		}
	}

	private static void startHubMethodHandler(){
		SystemLog("\tStart HubMethodHandler -> ");
		if (mHub != null) {
			invoke("StartHubMethodHandler", new Action() {
				@Override
				public void run(Object obj) throws Exception {
					//SystemLog("\tStart HubMethodHandler Done");
				}
			}, new ErrorCallback() {
				@Override
				public void onError(Throwable error) {
					SystemLog("\tError on HubMethodHandler => " + error.getMessage());
				}
			});
		}
	}

	private static void SystemLog(String msg){
		System.out.println(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()) + "\t\t" +msg);
	}

}
