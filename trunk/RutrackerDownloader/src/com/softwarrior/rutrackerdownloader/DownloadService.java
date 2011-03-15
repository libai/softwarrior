package com.softwarrior.rutrackerdownloader;

import java.io.FileInputStream;
import java.util.Timer;
import java.util.TimerTask;

import com.admob.android.ads.AdListener;
import com.admob.android.ads.AdManager;
import com.admob.android.ads.AdView;
import com.admob.android.ads.SimpleAdListener;
import com.mobclix.android.sdk.MobclixAdView;
import com.mobclix.android.sdk.MobclixAdViewListener;
import com.mobclix.android.sdk.MobclixMMABannerXLAdView;
import com.softwarrior.libtorrent.LibTorrent;
import com.softwarrior.rutrackerdownloader.RutrackerDownloaderApp.ActivityResultType;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class DownloadService extends Service {
    
	private static final LibTorrent mLibTorrent =  new LibTorrent();
	
	private NotificationManager mNM;
    private int mStartId = 0;
    
    private final IBinder mBinder = new LocalBinder();
    //-----------------------------------------------------------------------------
    public boolean SetSession(int ListenPort, int UploadLimit, int DownloadLimit){return mLibTorrent.SetSession(ListenPort, UploadLimit, DownloadLimit);}
    //-----------------------------------------------------------------------------
    //enum proxy_type
    //{
	//0 - none, // a plain tcp socket is used, and the other settings are ignored.
	//1 - socks4, // socks4 server, requires username.
	//2 - socks5, // the hostname and port settings are used to connect to the proxy. No username or password is sent.
	//3 - socks5_pw, // the hostname and port are used to connect to the proxy. the username and password are used to authenticate with the proxy server.
	//4 - http, // the http proxy is only available for tracker and web seed traffic assumes anonymous access to proxy
	//5 - http_pw // http proxy with basic authentication uses username and password
    //};
    //-----------------------------------------------------------------------------
    public boolean SetProxy(int Type, String HostName, int Port, String UserName, String Password){return mLibTorrent.SetProxy(Type, HostName, Port, UserName, Password);}       
    //-----------------------------------------------------------------------------
    public boolean AddTorrent(String SavePath, String TorentFile){return mLibTorrent.AddTorrent(SavePath, TorentFile);}
    //-----------------------------------------------------------------------------
    public boolean PauseSession(){return mLibTorrent.PauseSession();}
    //-----------------------------------------------------------------------------
    public boolean ResumeSession(){return mLibTorrent.ResumeSession();}
    //-----------------------------------------------------------------------------
    public boolean RemoveTorrent(){return mLibTorrent.RemoveTorrent();}
    //-----------------------------------------------------------------------------
    public int GetTorrentProgress(){return mLibTorrent.GetTorrentProgress();}    
    //-----------------------------------------------------------------------------
    //enum state_t
    //{
	//0 queued_for_checking,
	//1 checking_files,
	//2 downloading_metadata,
	//3 downloading,
	//4 finished,
	//5 seeding,
	//6 allocating,
	//7 checking_resume_data
    //}
    // + 8 paused
    // + 9 queued
    //-----------------------------------------------------------------------------
    public int GetTorrentState(){return mLibTorrent.GetTorrentState();}        	
    //-----------------------------------------------------------------------------
    //static char const* state_str[] =
    //{"checking (q)", "checking", "dl metadata", "downloading", "finished", "seeding", "allocating", "checking (r)"};
    //-----------------------------------------------------------------------------
    public String GetTorrentStatusText(){return mLibTorrent.GetTorrentStatusText();}
    //-----------------------------------------------------------------------------        
    public String GetSessionStatusText(){return mLibTorrent.GetSessionStatusText();}
    //----------------------------------------------------------------------------- 
    public String GetTorrentFiles(){return mLibTorrent.GetTorrentFiles();}
    //-----------------------------------------------------------------------------
	//0 - piece is not downloaded at all
	//1 - normal priority. Download order is dependent on availability
	//2 - higher than normal priority. Pieces are preferred over pieces with the same availability, but not over pieces with lower availability
	//3 - pieces are as likely to be picked as partial pieces.
	//4 - pieces are preferred over partial pieces, but not over pieces with lower availability
	//5 - currently the same as 4
	//6 - piece is as likely to be picked as any piece with availability 1
	//7 - maximum priority, availability is disregarded, the piece is preferred over any other piece with lower priority
    public boolean SetTorrentFilesPriority(byte[] FilesPriority){return mLibTorrent.SetTorrentFilesPriority(FilesPriority);}
    //-----------------------------------------------------------------------------

        
    public class LocalBinder extends Binder {
    	DownloadService getService() {
            return DownloadService.this;
        }
    }    
            
    void StopServiceSelf()
    {
		hideNotification();            
		Log.d(RutrackerDownloaderApp.TAG, "StopServiceSelf");            
		stopSelf(mStartId);
    }
         
    @Override
    public void onCreate() {
        mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        Toast.makeText(this, R.string.service_created,Toast.LENGTH_SHORT).show();        
//      mInvokeIntent = new Intent(this, Controller.class); //This is who should be launched if the user selects our persistent notification.
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
		mStartId = startId;
		String txt = null;
		if ((flags & Service.START_FLAG_REDELIVERY) == 0) {
		    txt = "New startId=" + mStartId;
		} else {
		    txt = "Re-delivered startId=" + mStartId;
		}
		Log.d(RutrackerDownloaderApp.TAG, "Service Starting: " + txt);
	    showNotification(getString(R.string.service_created)); 
		
		int listenPort = DownloadPreferencesScreen.GetListenPort(getApplicationContext());
		int uploadLimit = DownloadPreferencesScreen.GetUploadLimit(getApplicationContext());
		int downloadLimit =  DownloadPreferencesScreen.GetDownloadLimit(getApplicationContext());
		
		SetSession(listenPort, uploadLimit, downloadLimit);
		//-----------------------------------------------------------------------------
	    //enum proxy_type
	    //{
		//0 - none, // a plain tcp socket is used, and the other settings are ignored.
		//1 - socks4, // socks4 server, requires username.
		//2 - socks5, // the hostname and port settings are used to connect to the proxy. No username or password is sent.
		//3 - socks5_pw, // the hostname and port are used to connect to the proxy. the username and password are used to authenticate with the proxy server.
		//4 - http, // the http proxy is only available for tracker and web seed traffic assumes anonymous access to proxy
		//5 - http_pw // http proxy with basic authentication uses username and password
	    //};
	    //-----------------------------------------------------------------------------
		int type = DownloadPreferencesScreen.GetProxyType(getApplicationContext());
		String hostName = DownloadPreferencesScreen.GetHostName(getApplicationContext());
		int port = DownloadPreferencesScreen.GetPortNumber(getApplicationContext());
		String userName = DownloadPreferencesScreen.GetUserName(getApplicationContext());
		String password = DownloadPreferencesScreen.GetUserPassword(getApplicationContext());

		SetProxy(type, hostName, port, userName, password);       
    					
        return START_REDELIVER_INTENT; //START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        RemoveTorrent();
        hideNotification();
        // Tell the user we stopped.
        Toast.makeText(DownloadService.this, R.string.service_destroyed, Toast.LENGTH_SHORT).show();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    //Show a notification while this service is running.
    public void showNotification(String text) {
        // Set the icon, scrolling text and timestamp
        Notification notification = new Notification(R.drawable.ic_stat_notify, text, System.currentTimeMillis());
        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, Controller.class), 0);
        // Set the info for the views that show in the notification panel.
        notification.setLatestEventInfo(this, getText(R.string.service_label),text, contentIntent);
        // We show this for as long as our service is processing a command.
        notification.flags |= Notification.FLAG_ONGOING_EVENT;        
        // Send the notification.
        // We use a string id because it is a unique number.  We use it later to cancel.
        mNM.notify(R.string.service_created, notification);
    }
    
    private void hideNotification() {
        mNM.cancel(R.string.service_created);
    }    
    // ----------------------------------------------------------------------
    public static class Controller extends FullWakeActivity implements AdListener, MobclixAdViewListener {
        
    	private volatile boolean mIsBound = false;
        private volatile boolean mIsBoundService = false; 
        private DownloadService mBoundService = null;

        private volatile boolean mStopProgress = false;
        private volatile int mTorrentProgress = 0;
        private volatile int mTorrentState = 0;
        private volatile String mTorrentStatus = new String();
        private volatile String mSessionStatus = new String();
        
        private ProgressBar mProgress;
        private SharedPreferences mPrefs;

        private Handler mHandler = new Handler();        
        
        private Button mButtonStart;
        private Button mButtonStop;
        private Button mButtonPause;
        private Button mButtonResume;
        private Button mButtonSelectFiles;
        private TextView mTextViewTorrentState; 
        private TextView mTextViewCommonStatus;

    	StopHandler mStopHandler = null;
    	Message mStopMessage = null;

		private Timer mAdRefreshTimer;
		private static final int mAdRefreshTime = 30000; //30 seconds
		private static final int SELECT_FILE_ACTIVITY = 222;		

        //Mobclix
		private MobclixMMABannerXLAdView mAdviewBanner;
		//AdMob 
	  	private AdView mAdView;
    	
        enum ControllerState{
        	Undefined, Started, Stopped, Paused
        }          
        enum TorrentState {
        	queued_for_checking, checking_files, downloading_metadata, downloading, finished, seeding, allocating, checking_resume_data, paused, queued
        }
        enum MenuType{
        	About, Help, Preferences, FileManager, Exit;
        }
        
        private volatile ControllerState mControllerState = ControllerState.Undefined;

    	@Override
		public void onCreate(Bundle savedInstanceState) {
    		super.onCreate(savedInstanceState);
            setContentView(R.layout.service);
            //--------------Mobclix-----------------------
	        mAdviewBanner = (MobclixMMABannerXLAdView) findViewById(R.id.advertising_banner_view);
	        mAdviewBanner.addMobclixAdViewListener(this);            
            //--------------AdMob-----------------------
	        mAdRefreshTimer = new Timer();
	        mAdRefreshTimer.schedule(new AdRefreshTimerTask(), mAdRefreshTime, mAdRefreshTime);
	        AdManager.setPublisherId("a14d5a500187b19");
//	        AdManager.setTestDevices(new String[] { AdManager.TEST_EMULATOR, "92D0B17743FC28D496804E97F99B6D10" });        
//	        AdManager.setTestAction("video_int");

	        mAdView = (AdView) findViewById(R.id.ad);
	        mAdView.setAdListener(new AdvertisingListener());	        
		    //-----------------------------------------
            mProgress = (ProgressBar) findViewById(R.id.progress_horizontal);

            mButtonStart = (Button)findViewById(R.id.ButtonStartDownload);
            mButtonStop = (Button)findViewById(R.id.ButtonStopDownload);
            mButtonPause = (Button)findViewById(R.id.ButtonPauseDownload);
            mButtonResume = (Button)findViewById(R.id.ButtonResumeDownload); 
            mButtonSelectFiles = (Button)findViewById(R.id.ButtonSelectFiles);
            mTextViewTorrentState = (TextView)findViewById(R.id.TextViewTorrentState);
            mTextViewCommonStatus = (TextView)findViewById(R.id.TextViewCommonStatus);
            
            // Start lengthy operation in a background thread
            new Thread(new Runnable() {
                public void run() {
                	while (!mStopProgress) {
						if((mIsBoundService && mControllerState == ControllerState.Started) ||
						   (mIsBoundService && mControllerState == ControllerState.Paused)) {
								mTorrentProgress = mBoundService.GetTorrentProgress();
								mTorrentState = mBoundService.GetTorrentState();
								mTorrentStatus = mBoundService.GetTorrentStatusText();
								mSessionStatus = mBoundService.GetSessionStatusText();
								mHandler.post(new Runnable() {
									public void run() {
										mProgress.setProgress(mTorrentProgress);
										SetTorrentState();
										SetCommonStatus();
									}
							});
						}
					}
                }
            }).start();
            RestoreControllerState();
		    doBindService();
		    if(RutrackerDownloaderApp.ExitState) RutrackerDownloaderApp.CloseApplication(this);
		    RutrackerDownloaderApp.AnalyticsTracker.trackPageView("/DownloadServiceControler");
        }
	    private class AdRefreshTimerTask extends TimerTask {			
			@Override
			public void run() {
				mAdView.requestFreshAd();
        		mAdviewBanner.getAd();
			}	    	
	    }	    
    	void RestoreControllerState(){
            mPrefs = getSharedPreferences(Controller.class.getName(), MODE_PRIVATE);
            int controllerState = mPrefs.getInt(ControllerState.class.getName(),ControllerState.Undefined.ordinal());
            mControllerState = ControllerState.values()[controllerState]; 
            SetControllerState(mControllerState);
    	}

    	void SetControllerState(ControllerState controllerState){
    		mControllerState = controllerState; 
            switch (mControllerState) {
			case Started:{
			       mButtonStart.setEnabled(false);
			       mButtonStop.setEnabled(true);
			       mButtonPause.setEnabled(true);
			       mButtonResume.setEnabled(false);
			       mButtonSelectFiles.setEnabled(true);
			       if(mIsBoundService)
			    	   mBoundService.showNotification(getString(R.string.text_download_started));
			} break;
			case Paused:{
			       mButtonStart.setEnabled(false);
			       mButtonStop.setEnabled(true);
			       mButtonPause.setEnabled(false);
			       mButtonResume.setEnabled(true);
			       if(mIsBoundService)
			    	   mBoundService.showNotification(getString(R.string.text_download_paused));
			} break;
			case Stopped:
					if(mIsBoundService)
						mBoundService.showNotification(getString(R.string.text_download_stopped));
			case Undefined:
			default: {
			       mButtonStart.setEnabled(true);
			       mButtonStop.setEnabled(false);
			       mButtonPause.setEnabled(false);
			       mButtonResume.setEnabled(false);
			       mButtonSelectFiles.setEnabled(false);
			       mTextViewTorrentState.setText(R.string.text_torrent_state_undefined);
			       mTextViewCommonStatus.setText(R.string.text_common_status);
			       mProgress.setProgress(0);
			} break;
			}
    	}

    	void SetTorrentState(){
    		if(mTorrentState > 0 && mTorrentState < 10){
	    		TorrentState state = TorrentState.values()[mTorrentState];
	    		String txt_state = null;
	    		switch (state){
				case queued_for_checking: txt_state = getString(R.string.text_torrent_state_queued_for_checking); break; 
				case checking_files: txt_state = getString(R.string.text_torrent_state_checking_files); break;
				case downloading_metadata: txt_state = getString(R.string.text_torrent_state_downloading_metadata); break;
				case downloading: txt_state = getString(R.string.text_torrent_state_downloading); break;
				case finished: txt_state = getString(R.string.text_torrent_state_finished); break;
				case seeding: txt_state = getString(R.string.text_torrent_state_seeding); break;
				case allocating: txt_state = getString(R.string.text_torrent_state_allocating); break;
				case paused: txt_state = getString(R.string.text_torrent_state_checking_resume_data); break;
				case queued: txt_state = getString(R.string.text_torrent_state_checking_resume_data); break;
				default: txt_state = getString(R.string.text_torrent_state_undefined); break;
				}
	    		if(!mTextViewTorrentState.getText().equals(txt_state)){
		            mTextViewTorrentState.setText(txt_state);
	    		}
    		}
    		else {
    			String txt_state = getString(R.string.text_torrent_state_undefined);
	    		if(!mTextViewTorrentState.getText().equals(txt_state)){
		            mTextViewTorrentState.setText(txt_state);
	    		}
    		}
    	}
    	
    	void SetCommonStatus()
    	{
			if(mTorrentStatus != null && mSessionStatus != null)
				mTextViewCommonStatus.setText(mTorrentStatus + mSessionStatus);
			else
				mTextViewCommonStatus.setText(R.string.text_common_status);    		
    	}

    	
    	void SaveControllerState(){
            SharedPreferences.Editor ed = mPrefs.edit();
            ed.putInt(ControllerState.class.getName(), mControllerState.ordinal());
            ed.commit();
    	}
    	
    	@Override
    	protected void onResume() {
    		super.onResume();
    		if(RutrackerDownloaderApp.ExitState) RutrackerDownloaderApp.CloseApplication(this);
    	}
    	
    	@Override
    	protected void onPause() {
    		super.onPause();
    		SaveControllerState();
	    	if(mAdRefreshTimer != null){
	    		mAdRefreshTimer.cancel(); 
	    		mAdRefreshTimer = null;
	    	}
    	}

    	@Override
	    protected void onRestart() {
	    	super.onRestart();
	    	mAdRefreshTimer = new Timer();
	    	mAdRefreshTimer.schedule(new AdRefreshTimerTask(), 0, mAdRefreshTime);
	    }


    	@Override
        protected void onDestroy() {
    		mStopProgress = true;
            super.onDestroy();            
            doUnbindService();
    		//RutrackerDownloaderApp.AnalyticsTracker.dispatch();
        }        
        
		public void OnClickButtonStartDownload(View v) {
        	if(mIsBoundService){
        		String savePath = DownloadPreferencesScreen.GetTorrentSavePath(this); 
        		String torentFile = DownloadPreferencesScreen.GetFullTorrentFileName(this);
        		try{
        			FileInputStream fis = new FileInputStream(torentFile); 
            		fis.close();  		
            	}
            	catch(Exception ex){
                    Toast.makeText(Controller.this, R.string.error_torrent_file_absent, Toast.LENGTH_SHORT).show();
                    return;
            	}
        		mBoundService.AddTorrent(savePath, torentFile);
        		SetControllerState(ControllerState.Started);
        	}
		}
		
        public void OnClickButtonStopDownload(View v) {
        	if(mIsBoundService){
        		SetControllerState(ControllerState.Stopped);
        		mBoundService.RemoveTorrent();    		    
    	    	mStopHandler = new StopHandler();
    			mStopMessage =  mStopHandler.obtainMessage();
    			mStopHandler.sendMessageDelayed(mStopMessage, 500);
        	}
        } 
        
        private class StopHandler extends Handler {	
    	    @Override
    		public void handleMessage(Message message) {
    	    	SetControllerState(ControllerState.Stopped);
    	    }
        } 

		public void OnClickButtonPauseDownload(View v) {
        	if(mIsBoundService){
        		mBoundService.PauseSession();
        		SetControllerState(ControllerState.Paused);
        	}
		}
       
        public void OnClickButtonResumeDownload(View v) {
        	if(mIsBound && mIsBoundService){
        		mBoundService.ResumeSession();
        		SetControllerState(ControllerState.Started);
        	}
        }

        public void OnClickButtonSelectFiles(View v) {
        	if(mIsBoundService){
	        	Intent intent = new Intent(Intent.ACTION_VIEW);
	        	//TorrentFilesList.FillTorrentFiles(mBoundService.GetTorrentFiles());
	        	intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
	        	intent.setClassName(this, TorrentFilesList.class.getName());
	        	startActivityForResult(intent,SELECT_FILE_ACTIVITY);
        	}
        }
        
        private ServiceConnection mConnection = new ServiceConnection() {
           
        	public void onServiceConnected(ComponentName className, IBinder service) {
                mBoundService = ((DownloadService.LocalBinder)service).getService(); 
                if(mBoundService == null) mIsBoundService = false;
                else mIsBoundService = true;
                if(mIsBoundService){
                    Toast.makeText(Controller.this, R.string.service_connected, Toast.LENGTH_SHORT).show();
                }
            }

            public void onServiceDisconnected(ComponentName className) {
            	mIsBoundService = false;
            	mBoundService = null;
                Toast.makeText(Controller.this, R.string.service_disconnected, Toast.LENGTH_SHORT).show();
                SetControllerState(ControllerState.Stopped);
            }
        };
        
        void doBindService() {
        	if (!mIsBound) {
	            bindService(new Intent(Controller.this, DownloadService.class), mConnection, Context.BIND_AUTO_CREATE);
	            mIsBound = true;
        	}
        }
        
        void doUnbindService() {
            if (mIsBound) {
             	// Detach our existing connection.
                unbindService(mConnection);
                mIsBound = false;
            }
        }

        @Override
    	protected void onActivityResult(int requestCode, int resultCode, Intent data) {        	
			if(requestCode == SELECT_FILE_ACTIVITY){
                if(mIsBoundService){
//                	int count = TorrentFilesList.TorrentFilesPriority.size();
//                	byte[] priorities = new byte[count];
//                	for(int i=0;i<count;i++) priorities[i] = (byte)TorrentFilesList.TorrentFilesPriority.get(i);	           
//                	mBoundService.SetTorrentFilesPriority(priorities);	    		                	
                }
			}
			else{
	        	switch(ActivityResultType.getValue(resultCode))
	    		{
	    		case RESULT_DOWNLOADER:
	    		case RESULT_PREFERENCES:
	    		case RESULT_EXIT:
	    			setResult(resultCode);
	    			finish();
	    			return;
	    		};
			}
    	}

		private class AdvertisingListener extends SimpleAdListener {
			@Override
			public void onFailedToReceiveAd(AdView adView){
				super.onFailedToReceiveAd(adView);
			}		
			@Override
			public void onFailedToReceiveRefreshedAd(AdView adView){
				super.onFailedToReceiveRefreshedAd(adView);
			}		
			@Override
			public void onReceiveAd(AdView adView){
				super.onReceiveAd(adView);
			}
			@Override
			public void onReceiveRefreshedAd(AdView adView){
				super.onReceiveRefreshedAd(adView);
			}			
		}
		
		public void onFailedToReceiveAd(AdView adView) {
			Log.v(RutrackerDownloaderApp.TAG, "AdMob onFailedToReceiveAd");
		}
		public void onFailedToReceiveRefreshedAd(AdView adView) {
			Log.v(RutrackerDownloaderApp.TAG, "AdMob onFailedToReceiveRefreshedAd");
		}
		public void onReceiveAd(AdView adView){
			Log.v(RutrackerDownloaderApp.TAG, "AdMob onReceiveAd");
		}
		public void onReceiveRefreshedAd(AdView adView){
			Log.v(RutrackerDownloaderApp.TAG, "AdMob onReceiveRefreshedAd");
		}        
    	@Override
    	public boolean onCreateOptionsMenu(Menu menu) {
    		super.onCreateOptionsMenu(menu);
    		menu.add(Menu.NONE, MenuType.About.ordinal(), MenuType.About.ordinal(), R.string.menu_about); 
    		menu.add(Menu.NONE, MenuType.Help.ordinal(), MenuType.Help.ordinal(), R.string.menu_help); 
    		menu.add(Menu.NONE, MenuType.Preferences.ordinal(), MenuType.Preferences.ordinal(), R.string.menu_preferences);
    		menu.add(Menu.NONE, MenuType.FileManager.ordinal(), MenuType.FileManager.ordinal(), R.string.menu_file_manager);
    		menu.add(Menu.NONE, MenuType.Exit.ordinal(), MenuType.Exit.ordinal(), R.string.menu_exit);
    		return true;
    	}
    	
    	@Override
    	public boolean onMenuItemSelected(int featureId, MenuItem item) {
    		super.onMenuItemSelected(featureId, item);
    		MenuType type = MenuType.values()[item.getItemId()];
    		switch(type)
    		{
    		case About:{
    			RutrackerDownloaderApp.AboutActivity(this);
    		} break;
    		case Help:{
    			RutrackerDownloaderApp.HelpActivity(this);
    		} break;
    		case Preferences:{
    			RutrackerDownloaderApp.PreferencesScreenActivity(this);
    		} break;
    		case FileManager:{
    			RutrackerDownloaderApp.FileManagerActivity(this);
    		} break;
    		case Exit:{
    			RutrackerDownloaderApp.CloseApplication(this);
    		} break;
    		}
    		return true;
    	}
		//Mobclix
		public String keywords()	{ return null;}
		public String query()		{ return null;}
		public void onAdClick(MobclixAdView arg0) {
			Log.v(RutrackerDownloaderApp.TAG, "Ad clicked!");
		}
		public void onCustomAdTouchThrough(MobclixAdView adView, String string) {
			Log.v(RutrackerDownloaderApp.TAG, "The custom ad responded with '" + string + "' when touched!");
		}
		public boolean onOpenAllocationLoad(MobclixAdView adView, int openAllocationCode) {
			Log.v(RutrackerDownloaderApp.TAG, "The ad request returned open allocation code: " + openAllocationCode);
			return false;
		}
		public void onSuccessfulLoad(MobclixAdView view) {
			Log.v(RutrackerDownloaderApp.TAG, "The ad request was successful!");
			view.setVisibility(View.VISIBLE);
		}
		public void onFailedLoad(MobclixAdView view, int errorCode) {
			Log.v(RutrackerDownloaderApp.TAG, "The ad request failed with error code: " + errorCode);
			view.setVisibility(View.GONE);
		}				    	
    }
}