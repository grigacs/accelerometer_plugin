package org.apache.cordova.devicemotion;

import java.util.List;
import java.util.ArrayList;

import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import android.os.Handler;
import android.os.Looper;

public class CordovaAccelerator extends CordovaPlugin implements SensorEventListener {

    public static final int STOPPED = 0;
    public static final int STARTING = 1;
    public static final int RUNNING = 2;
    public static final int ERROR_FAILED_TO_START = 3;

    private ArrayList dataX = new ArrayList();
    private ArrayList dataY = new ArrayList();
    private ArrayList dataZ = new ArrayList();

    private long timestamp;
    private int status;
    private int accuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH;

    private SensorManager sensorManager;
    private Sensor mSensor;
    private int frequency = 0;


    private CallbackContext callbackContext;

    private Handler mainHandler=null;
    private Runnable mainRunnable =new Runnable() {
        public void run() {
            CordovaAccelerator.this.timeout();
        }
    };

    private boolean mStopHandler = false;

    private Handler mHandler = new Handler();
    private Runnable runnable = new Runnable(){
        @Override
        public void run(){
            if(!mStopHandler){

                if(frequency == 0){

                }else{
                    win();
                    removeElements();
                    mHandler.postDelayed(this, frequency);
                }
            }
        }
    };

    /**
     * Create an accelerometer listener.
     */

    public CordovaAccelerator() {
        this.timestamp = 0;
        this.setStatus(CordovaAccelerator.STOPPED);
        mHandler.postDelayed(runnable, 2000);
    }

    private void removeElements(){
      if(!dataX.isEmpty() || !dataY.isEmpty() || !dataZ.isEmpty()) {
        dataX.clear();
        dataY.clear();
        dataZ.clear();
      }
    }

    /**
     * Sets the context of the Command. This can then be used to do things like
     * get file paths associated with the Activity.
     *
     * @param cordova The context of the main Activity.
     * @param webView The associated CordovaWebView.
     */

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        this.sensorManager = (SensorManager) cordova.getActivity().getSystemService(Context.SENSOR_SERVICE);
    }

    /**
     * Executes the request.
     *
     * @param action        The action to execute.
     * @param args          The exec() arguments.
     * @param callbackId    The callback id used when calling back into JavaScript.
     * @return              Whether the action was valid.
     */
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
        if (action.equals("start")) {
            try{
                String freq = args.getString(0);
                this.frequency = Integer.parseInt(freq);
            }catch(JSONException e){

            }
            this.callbackContext = callbackContext;
            if (this.status != CordovaAccelerator.RUNNING) {
                // If not running, then this is an async call, so don't worry about waiting
                // We drop the callback onto our stack, call start, and let start and the sensor callback fire off the callback down the road
                this.start();
            }
        }
        else if (action.equals("stop")) {
            if (this.status == CordovaAccelerator.RUNNING) {
                this.stop();
            }
        }else {
            // Unsupported action
            return false;
        }


        PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT, "");
        result.setKeepCallback(true);
        callbackContext.sendPluginResult(result);



        return true;
    }

    /**
     * Called by AccelBroker when listener is to be shut down.
     * Stop listener.
     */
    public void onDestroy() {
        this.stop();
    }

    /**
     * Start listening for acceleration sensor.
     *
     * @return          status of listener
     */
    private int start() {
        // If already starting or running, then restart timeout and return
        if ((this.status == CordovaAccelerator.RUNNING) || (this.status == CordovaAccelerator.STARTING)) {
            startTimeout();
            return this.status;
        }

        this.setStatus(CordovaAccelerator.STARTING);

        // Get accelerometer from sensor manager
        List<Sensor> list = this.sensorManager.getSensorList(Sensor.TYPE_ACCELEROMETER);

        // If found, then register as listener
        if ((list != null) && (list.size() > 0)) {
            this.mSensor = list.get(0);
            if (this.sensorManager.registerListener(this, this.mSensor, 0)) {
                this.setStatus(CordovaAccelerator.STARTING);
                // CB-11531: Mark accuracy as 'reliable' - this is complementary to
                // setting it to 'unreliable' 'stop' method
                this.accuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH;
            } else {
                this.setStatus(CordovaAccelerator.ERROR_FAILED_TO_START);
                this.fail(CordovaAccelerator.ERROR_FAILED_TO_START, "Device sensor returned an error.");
                return this.status;
            };

        } else {
            this.setStatus(CordovaAccelerator.ERROR_FAILED_TO_START);
            this.fail(CordovaAccelerator.ERROR_FAILED_TO_START, "No sensors found to register accelerometer listening to.");
            return this.status;
        }

        startTimeout();

        return this.status;
    }
    private void startTimeout() {
        // Set a timeout callback on the main thread.
        stopTimeout();
        mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.postDelayed(mainRunnable, 2000);
        mHandler = new Handler(Looper.getMainLooper());
        mHandler.postDelayed(runnable, frequency);
    }
    private void stopTimeout() {
        if(mainHandler!=null){
            mainHandler.removeCallbacks(mainRunnable);
        }if(mHandler!=null){
            mHandler.removeCallbacks(runnable);
        }
    }

    /**
     * Stop listening to acceleration sensor.
     */
    private void stop() {
        stopTimeout();
        if (this.status != CordovaAccelerator.STOPPED) {
            this.sensorManager.unregisterListener(this);
        }
        this.setStatus(CordovaAccelerator.STOPPED);
        this.accuracy = SensorManager.SENSOR_STATUS_UNRELIABLE;
    }

    /**
     * Returns latest cached position if the sensor hasn't returned newer value.
     *
     * Called two seconds after starting the listener.
     */
    private void timeout() {

        if (this.status == CordovaAccelerator.STARTING &&
                this.accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_HIGH) {
            // call win with latest cached position
            // but first check if cached position is reliable
            this.timestamp = System.currentTimeMillis();

            this.win();
        }
    }

    /**
     * Called when the accuracy of the sensor has changed.
     *
     * @param sensor
     * @param accuracy
     */
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Only look at accelerometer events
        if (sensor.getType() != Sensor.TYPE_ACCELEROMETER) {
            return;
        }

        // If not running, then just return
        if (this.status == CordovaAccelerator.STOPPED) {
            return;
        }
        this.accuracy = accuracy;
    }

    /**
     * Sensor listener event.
     *
     * @param SensorEvent event
     */
    public void onSensorChanged(SensorEvent event) {
        // Only look at accelerometer events
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) {
            return;
        }

        // If not running, then just return
        if (this.status == CordovaAccelerator.STOPPED) {
            return;
        }
        this.setStatus(CordovaAccelerator.RUNNING);

       if ( this.accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_HIGH ) {

            // Save time that event was received
            this.timestamp = System.currentTimeMillis();
            this.dataX.add(event.values[0]);
            this.dataY.add(event.values[1]);
            this.dataZ.add(event.values[2]);
       }
    }

    /**
     * Called when the view navigates.
     */
    @Override
    public void onReset() {
        if (this.status == CordovaAccelerator.RUNNING) {
            this.stop();
        }
    }

    // Sends an error back to JS
    private void fail(int code, String message) {
        // Error object
        JSONObject errorObj = new JSONObject();
        try {
            errorObj.put("code", code);
            errorObj.put("message", message);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        PluginResult err = new PluginResult(PluginResult.Status.ERROR, errorObj);
        err.setKeepCallback(true);
        callbackContext.sendPluginResult(err);
    }

    private void win() {

        // Success return object
        PluginResult result = new PluginResult(PluginResult.Status.OK, this.getAccelerationJSON());
        result.setKeepCallback(true);
        callbackContext.sendPluginResult(result);
    }



    private void setStatus(int status) {
        this.status = status;
    }
    private JSONObject getAccelerationJSON() {
        JSONObject r = new JSONObject();
        try {
            r.put("timestamp", this.timestamp);
            r.put("dataX", this.dataX);
            r.put("dataY", this.dataY);
            r.put("dataZ", this.dataZ);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return r;
    }
}
