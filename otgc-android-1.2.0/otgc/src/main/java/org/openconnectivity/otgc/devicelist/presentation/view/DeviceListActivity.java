/*
 * *****************************************************************
 *
 *  Copyright 2018 DEKRA Testing and Certification, S.A.U. All Rights Reserved.
 *
 *  ******************************************************************
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  ******************************************************************
 */
package org.openconnectivity.otgc.devicelist.presentation.view;

import android.app.AlertDialog;
import android.arch.lifecycle.ViewModelProvider;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Intent;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.iotivity.base.OcProvisioning;
import org.openconnectivity.otgc.R;
import org.openconnectivity.otgc.common.presentation.view.RecyclerWithSwipeFragment;
import org.openconnectivity.otgc.common.presentation.viewmodel.CommonError;
import org.openconnectivity.otgc.common.presentation.viewmodel.Response;
import org.openconnectivity.otgc.common.presentation.viewmodel.Status;
import org.openconnectivity.otgc.common.presentation.viewmodel.ViewModelError;
import org.openconnectivity.otgc.devicelist.presentation.viewmodel.DeviceListViewModel;
import org.openconnectivity.otgc.devicelist.presentation.viewmodel.SharedViewModel;
import org.openconnectivity.otgc.login.presentation.view.LoginActivity;
import org.openconnectivity.otgc.logviewer.presentation.view.LogViewerActivity;
import org.openconnectivity.otgc.settings.presentation.view.SettingsActivity;
import org.openconnectivity.otgc.wlanscan.presentation.view.WlanScanActivity;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import dagger.android.DispatchingAndroidInjector;
import dagger.android.support.HasSupportFragmentInjector;
import timber.log.Timber;

public class DeviceListActivity extends AppCompatActivity implements SensorEventListener, HasSupportFragmentInjector {

    private final static String NOT_SUPPORTED_MESSAGE = "Sorry, sensor not available for this device.";

    @Inject
    DispatchingAndroidInjector<Fragment> dispatchingAndroidInjector;

    @Inject
    ViewModelProvider.Factory mViewModelFactory;

    @BindView(R.id.progress_bar)
    ProgressBar mProgressBar;
    @BindView(R.id.toolbar)
    Toolbar mToolbar;
    @BindView(R.id.temperature)
    TextView mTemperature;
    @BindView(R.id.set_temperature)
    TextView setTemperature;
    @BindView(R.id.time)
    TextView time;
    @BindView(R.id.humidity)
    TextView mHumidity;
    @BindView(R.id.tempSeekBar)
    SeekBar seekBar;

    private DeviceListViewModel mViewModel;
    private Float ambient_temperature;
    private Float setTempRoom = new Float(20.0);
    private SensorManager mSensorManager;
    private Sensor mSensorTemperature;
    private Sensor humidity;

    // TODO: Refactor to avoid AlertDialog object
    private AlertDialog mConnectToWifiDialog = null;

    String verifyPin = "";

    OcProvisioning.PinCallbackListener randomPinCallbackListener = () -> {
        Timber.d("Inside randomPinListener");
        final Object lock = new Object();
        runOnUiThread(() -> {
            AlertDialog.Builder alertDialog = new AlertDialog.Builder(new ContextThemeWrapper(DeviceListActivity.this, R.style.AppTheme));
            alertDialog.setTitle(DeviceListActivity.this.getString(R.string.devices_dialog_insert_randompin_title));
            final EditText input = new EditText(DeviceListActivity.this);
            alertDialog.setView(input);
            alertDialog.setCancelable(false);
            alertDialog.setPositiveButton(DeviceListActivity.this.getString(R.string.devices_dialog_insert_randompin_yes_option), (dialog, which) -> {
                dialog.dismiss();
                try {
                    synchronized (lock) {
                        verifyPin = input.getText().toString();
                        lock.notifyAll();
                    }
                } catch (Exception e) {
                    Timber.e(e);
                }
            });
            alertDialog.setNegativeButton(DeviceListActivity.this.getString(R.string.devices_dialog_insert_randompin_no_option), (dialog, which) -> {
                dialog.dismiss();
                try {
                    synchronized (lock) {
                        lock.notifyAll();
                    }
                } catch (Exception e) {
                    Timber.e(e);
                }
            }).show();
        });
        synchronized (lock) {
            try {
                lock.wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Timber.e(e);
            }
        }
        Timber.d("Verify after submit = %s", verifyPin);
        return verifyPin;
    };

    OcProvisioning.DisplayPinListener displayPinListener = pin -> {
        Timber.d("Inside displayPinListener");
        runOnUiThread(() -> {
            AlertDialog.Builder alertDialog = new AlertDialog.Builder(new ContextThemeWrapper(DeviceListActivity.this, R.style.AppTheme));
            alertDialog.setTitle(DeviceListActivity.this.getString(R.string.devices_dialog_show_randompin_title));
            alertDialog.setMessage(pin);
            alertDialog.setCancelable(false);
            alertDialog.setPositiveButton(
                    DeviceListActivity.this.getString(R.string.devices_dialog_show_randompin_yes_option),
                    (dialog, which) -> dialog.dismiss()).show();
        });
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_devices);

        ButterKnife.bind(this);
        initViews();
        initViewModel();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mSensorTemperature, SensorManager.SENSOR_DELAY_NORMAL);
        mViewModel.checkIfIsConnectedToWifi();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_devices, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_item_rfotm:
                onRfotmPressed();
                break;
            case R.id.menu_item_rfnop:
                onRfnopPressed();
                break;
            case R.id.menu_item_log:
                onLogPressed();
                break;
            case R.id.menu_item_settings:
                onSettingsPressed();
                break;
            case R.id.buttonDeactivate:
                onLogoutPressed();
                break;
            default:
                break;
        }

        return true;
    }

    @Override
    public DispatchingAndroidInjector<Fragment> supportFragmentInjector() {
        return dispatchingAndroidInjector;
    }

    @OnClick(R.id.floating_button_device_scan)
    protected void onScanPressed() {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.devices_fragment);
        if (fragment instanceof RecyclerWithSwipeFragment) {
            ((RecyclerWithSwipeFragment) fragment).onSwipeRefresh();
        }
    }

    private void initViews() {
        setSupportActionBar(mToolbar);

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            mSensorTemperature = mSensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE); // requires API level 14.
        }

        if (mSensorTemperature == null) {
            mTemperature.setTextSize(20);
            mTemperature.setText(NOT_SUPPORTED_MESSAGE);
        }
        timeThread();
        tempToSet(setTemperature, "Set to: ", setTempRoom.toString());
        getHumiditySensorData();
        seekBarListener();


    }

    private void seekBarListener() {
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                i /= 1;
                i *= 1;
                Float progress = Float.valueOf(i);
                setTempRoom = progress / 10;
                tempToSet(setTemperature, "Set to: ", setTempRoom.toString());
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if(setTempRoom > ambient_temperature){
                    setTemperature.setTextColor(Color.RED);
                } else if(setTempRoom < ambient_temperature){
                    setTemperature.setTextColor(Color.rgb(0,191,255));
                } else {
                    setTemperature.setTextColor(Color.WHITE);
                }
            }
        });
    }


    private void timeThread() {
        final String[] currentDateTimeString = {DateFormat.getDateTimeInstance().format(new Date())};
        time.setText(currentDateTimeString[0]);

        Thread t = new Thread() {
            @Override
            public void run() {
                while (!isInterrupted()) {
                    try {

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                currentDateTimeString[0] = new SimpleDateFormat("dd/MM/yy HH:mm").format(new Date());
                                time.setText(currentDateTimeString[0]);
                            }
                        });
                        Thread.sleep(60000);  //1000ms = 1 sec
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        };

        t.start();
    }

    private void initViewModel() {
        mViewModel = ViewModelProviders.of(this, mViewModelFactory).get(DeviceListViewModel.class);
        mViewModel.getError().observe(this, this::handleError);

        mViewModel.getInit().observe(this, success -> {
            if (success != null && success) {
                mViewModel.setRandomPinListener(randomPinCallbackListener);
                mViewModel.setDisplayPinListener(displayPinListener);
                mViewModel.retrieveDeviceId();
            }
        });
        mViewModel.getRfotmResponse().observe(this, this::processRfotmResponse);
        mViewModel.getRfnopResponse().observe(this, this::processRfnopResponse);
        mViewModel.getLogoutResponse().observe(this, this::processLogoutResponse);
        mViewModel.getConnectedResponse().observe(this, this::processConnectedResponse);

        mViewModel.initializeIotivityStack();

        SharedViewModel sharedViewModel = ViewModelProviders.of(this, mViewModelFactory).get(SharedViewModel.class);
        sharedViewModel.getLoading().observe(this, this::processing);
        sharedViewModel.getDisconnected().observe(this, isDisconnected -> {
            processing(false);
            goToWlanConnectSSID();
        });
    }

    private void handleError(ViewModelError error) {
        if (error.getType().equals(CommonError.NETWORK_DISCONNECTED)) {
            processing(false);
            goToWlanConnectSSID();
        }
    }

    private void processRfotmResponse(Response<Void> response) {
        switch (response.status) {
            case LOADING:
                mProgressBar.setVisibility(View.VISIBLE);
                break;
            case SUCCESS:
                mProgressBar.setVisibility(View.GONE);
                mViewModel.retrieveDeviceId();
                onScanPressed();
                break;
            default:
                mProgressBar.setVisibility(View.GONE);
                Toast.makeText(this, R.string.devices_error_rfotm_failed, Toast.LENGTH_SHORT).show();
                break;
        }
    }

    private void processRfnopResponse(Response<Void> response) {
        switch (response.status) {
            case LOADING:
                mProgressBar.setVisibility(View.VISIBLE);
                break;
            case SUCCESS:
                mProgressBar.setVisibility(View.GONE);
                break;
            default:
                mProgressBar.setVisibility(View.GONE);
                Toast.makeText(this, R.string.devices_error_rfnop_failed, Toast.LENGTH_SHORT).show();
                break;
        }
    }

    private void processing(boolean isProcessing) {
        mProgressBar.setVisibility(isProcessing ? View.VISIBLE : View.GONE);
    }

    private void processLogoutResponse(Response<Void> response) {
        if (response.status.equals(Status.SUCCESS)) {
            startActivity(new Intent(DeviceListActivity.this, LoginActivity.class));
            finish();
        }
    }

    private void processConnectedResponse(Response<Boolean> response) {
        if (response.status.equals(Status.SUCCESS)
                && response.data != null && !response.data) {
            goToWlanConnectSSID();
        }
    }

    private void onSettingsPressed() {
        Intent settingsIntent = new Intent().setClass(DeviceListActivity.this, SettingsActivity.class);
        startActivity(settingsIntent);
    }

    private void onLogoutPressed() {
        Timber.d("Deactivate option selected...");
        mViewModel.logout();
    }

    private void onRfotmPressed() {
        mViewModel.setRfotmMode();
    }

    private void onRfnopPressed() {
        mViewModel.setRfnopMode();
    }

    private void onLogPressed() {
        Intent intent = new Intent(this, LogViewerActivity.class);
        startActivity(intent);
    }

    private void goToWlanConnectSSID() {
        if (mConnectToWifiDialog == null) {
            mConnectToWifiDialog = new AlertDialog.Builder(this)
                    .setTitle(R.string.devices_dialog_wifi_title)
                    .setMessage(R.string.devices_dialog_wifi_message)
                    .setPositiveButton(R.string.devices_dialog_wifi_positive_button_text, (dialog, which) -> {
                        dialog.dismiss();
                        mConnectToWifiDialog = null;
                        startActivity(new Intent(DeviceListActivity.this, WlanScanActivity.class));
                    }).setNegativeButton(R.string.devices_dialog_wifi_negative_button_text,
                            (dialog, which) -> /*dialog.dismiss()*/mConnectToWifiDialog = null
                    ).create();
            mConnectToWifiDialog.show();
        }
    }


    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
    }


    @Override
    public void onSensorChanged(SensorEvent event) {
        String tempStr;
        if (event.sensor.getType() == Sensor.TYPE_AMBIENT_TEMPERATURE) {
            ambient_temperature = event.values[0];
            if (Math.abs(ambient_temperature) < 10)
                tempStr = String.valueOf(ambient_temperature).substring(0, 3) + getResources().getString(R.string.celsius);
            else
                tempStr = String.valueOf(ambient_temperature).substring(0, 4) + getResources().getString(R.string.celsius);

            tempToSet(mTemperature, "", tempStr);
        } else if (event.sensor.getType() == Sensor.TYPE_RELATIVE_HUMIDITY) {
            float ambient_humidity = event.values[0];
            mHumidity.setText(String.format("%.3f %%", ambient_humidity));
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do something here if sensor accuracy changes.
    }

    private void setTemp(TextView setTemperature, String s, String s2) {
        setTemperature.setText(s + s2);
    }

    private void tempToSet(TextView setTemperature, String s, String s2) {
        setTemp(setTemperature, s, s2);
    }

    private void getHumiditySensorData() {
        humidity = mSensorManager.getDefaultSensor(Sensor.TYPE_RELATIVE_HUMIDITY);
        if (humidity == null) {
            mHumidity.setTextSize(20);
            mHumidity.setText(NOT_SUPPORTED_MESSAGE);
        } else {
            mHumidity.setText(String.format("%.3f %%", humidity.getPower()));
        }
    }
}
