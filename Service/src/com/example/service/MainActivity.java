package com.example.service;

import org.alljoyn.bus.BusAttachment;
import org.alljoyn.bus.BusException;
import org.alljoyn.bus.BusListener;
import org.alljoyn.bus.BusObject;
import org.alljoyn.bus.Mutable;
import org.alljoyn.bus.Mutable.ShortValue;
import org.alljoyn.bus.SessionOpts;
import org.alljoyn.bus.SessionPortListener;
import org.alljoyn.bus.Status;
import org.alljoyn.bus.annotation.BusMethod;

import com.example.service.R;

import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.ListView;


public class MainActivity extends ActionBarActivity {
	static {
		System.loadLibrary("alljoyn_java");
	}
	
	//private static final int MESSAGE_PING = 1;
	private static final int MESSAGE_PING_REPLY = 2;
	//private static final int MESSAGE_POST_TOAST =3;
	private ArrayAdapter<String> mListViewArrayAdapter;
	private ListView mListView;
	
	private Handler mHandler = new Handler(){
		@Override
		public void handleMessage(Message msg){
			switch(msg.what){
			case MESSAGE_PING_REPLY:
				String reply = (String) msg.obj;
				mListViewArrayAdapter.add("reply: " +reply);
				Log.d("hoge","reply: " +reply);
				break;
			default:
					break;
			}
		}
		
	};
	
	private SimpleService mSimpleService;
	private Handler mBusHandler;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        mListViewArrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
        mListView = (ListView) findViewById(R.id.ListView);
        mListView.setAdapter(mListViewArrayAdapter);
        
        HandlerThread busThread = new HandlerThread("BusHandler");
        busThread.start();
        mBusHandler = new BusHandler(busThread.getLooper());
        
        mSimpleService = new SimpleService();
        mBusHandler.sendEmptyMessage(BusHandler.CONNECT);
        Log.d("hoge","service起動");
    }

    
	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    protected void onDestroy(){
    	super.onDestroy();
    	mBusHandler.sendEmptyMessage(BusHandler.DISCONNECT);
    }
    
    class SimpleService implements SimpleInterface, BusObject{
		public String Ping(String inStr) {
		
			sendUiMessage(MESSAGE_PING_REPLY,inStr);
			return inStr;
		}
		
		private void sendUiMessage(int what, Object obj){
			mHandler.sendMessage(mHandler.obtainMessage(what, obj));
		}
    	
    }
    
    class BusHandler extends Handler{
    	
    	private static final String SERVICE_NAME = "org.alljoyn.bus.samples.simple";
    	private static final short CONTACT_PORT=42;
    	
    	private BusAttachment mBus;
    	
    	public static final int CONNECT = 1;
    	public static final int DISCONNECT = 2;
    	
    	public BusHandler(Looper looper){
    		super(looper);
    	}
    	
    	@Override
    	public void handleMessage(Message msg){
    		switch(msg.what){
    			case CONNECT :{
    				Log.d("hoge","service_CONNECT呼ばれる");
    				org.alljoyn.bus.alljoyn.DaemonInit.PrepareDaemon(getApplicationContext());
    				
    				mBus = new BusAttachment(getPackageName(),BusAttachment.RemoteMessage.Receive);
    				mBus.registerBusListener(new BusListener());
    				Status status = mBus.registerBusObject(mSimpleService, "/Service");
    				
    				Log.d("hoge","registerBusObject: "+status);
    				if(status != Status.OK){
    					Log.d("hoge","service_registerbusobjectダメ");
    					finish();
    					return;
    				}
    				    				
    				status = mBus.connect();
    				Log.d("hoge","connect: "+status);
    				if(status != Status.OK){
    					Log.d("hoge","service_connectだめ");
    					finish();
    					return;
    				}
    				
    				
    				Mutable.ShortValue contactPort = new Mutable.ShortValue(CONTACT_PORT);
    				
    				SessionOpts sessionOpts = new SessionOpts();
    				sessionOpts.traffic = SessionOpts.TRAFFIC_MESSAGES;
    				sessionOpts.isMultipoint = false;
    				sessionOpts.proximity = SessionOpts.PROXIMITY_ANY;
    				
    				sessionOpts.transports = SessionOpts.TRANSPORT_ANY + SessionOpts.TRANSPORT_WFD;
    				
    				status = mBus.bindSessionPort(contactPort, sessionOpts, new SessionPortListener(){
    					@Override
    					public boolean acceptSessionJoiner(short sessionPort, String joiner, SessionOpts sessionOpts){
    						if(sessionPort == CONTACT_PORT){
    							return true;
    						} else{
    							return false;
    						}
    					}
    				});
    				
    				int flag = BusAttachment.ALLJOYN_REQUESTNAME_FLAG_REPLACE_EXISTING | BusAttachment.ALLJOYN_REQUESTNAME_FLAG_DO_NOT_QUEUE;
    				
    				status = mBus.requestName(SERVICE_NAME, flag);
    				if(status == Status.OK){
    					status = mBus.advertiseName(SERVICE_NAME, sessionOpts.transports);
    					
    					if(status != Status.OK){
    						status = mBus.releaseName(SERVICE_NAME);
    						Log.d("hoge","advertisenameだめ");
    						finish();
    						return;
    					}
    				}
    				Log.d("hoge","service_connect終わり");
    				break;
    			}
    			case DISCONNECT :{
    				Log.d("hoge","service_disconnect呼ばれる");
    				mBus.unregisterBusObject(mSimpleService);
    				mBus.disconnect();
    				mBusHandler.getLooper().quit();
    				break;
    			}
    			
    			default:
    				break;
    		}
     	}
    }
}