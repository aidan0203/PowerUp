package prj.milesproductions.powerup;

import android.animation.ObjectAnimator;
import android.app.ActivityManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.commons.io.FileUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainApp";

    public int capacity, charge_now, charge_full, current, tempf;
    public float voltage, temp;
    public String status, health, mDevice;

    private Button btnBatteryUsage;
    private TextView txtCapacity, txtCharge, txtChargeTotal, txtCurrent, txtVoltage, txtTemperature, txtState, txtHealth;
    private ProgressBar mGauge;
    private Typeface mTypeface;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle("PowerUp! Battery Stats");
        toolbar.showOverflowMenu();
        setSupportActionBar(toolbar);

        txtCapacity = (TextView) findViewById(R.id.txtCapacity);
        txtCharge = (TextView) findViewById(R.id.txtCharge);
        txtChargeTotal = (TextView) findViewById(R.id.txtChargeTotal);
        txtCurrent = (TextView) findViewById(R.id.txtCurrent);
        txtVoltage = (TextView) findViewById(R.id.txtVoltage);
        txtTemperature = (TextView) findViewById(R.id.txtTemperature);
        txtState = (TextView) findViewById(R.id.txtState);
        txtHealth = (TextView) findViewById(R.id.txtHealth);
        btnBatteryUsage = (Button) findViewById(R.id.btnBatteryUsage);
        mGauge = (ProgressBar) findViewById(R.id.mGauge);

        mTypeface = Typeface.createFromAsset(getAssets(), "fonts/Calibre-Bold.otf");
        txtCapacity.setTypeface(mTypeface);

        btnBatteryUsage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                Intent powerUsageIntent = new Intent(Intent.ACTION_POWER_USAGE_SUMMARY);
                ResolveInfo resolveInfo = getPackageManager().resolveActivity(powerUsageIntent, 0);
                if(resolveInfo != null){
                    startActivity(powerUsageIntent);
                }
            }
        });

        mDevice = getDeviceName();
        if(mDevice == null) {
            System.exit(1);
        }

        getAndSetBatteryData();
        animateGauge();

        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                getAndSetBatteryData();
                handler.postDelayed(this, 2000);
            }
        }, 2000);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean s = isMyServiceRunning(AmpereMeterService.class);
        if(!s) {
            getMenuInflater().inflate(R.menu.main_svcstart, menu);
        } else {
            getMenuInflater().inflate(R.menu.main_svcstop, menu);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int selectedItem = item.getItemId();
        if(selectedItem == R.id.STARTSVC) {
            Toast.makeText(this, "Service started", Toast.LENGTH_LONG).show();
            Intent s = new Intent(MainActivity.this, AmpereMeterService.class);
            startService(s);
            invalidateOptionsMenu();
        }
        if(selectedItem == R.id.STOPSVC) {
            Toast.makeText(this, "Service stopped", Toast.LENGTH_LONG).show();
            stopService(new Intent(MainActivity.this, AmpereMeterService.class));
            NotificationManager mNotifyMgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            mNotifyMgr.cancelAll();
            invalidateOptionsMenu();
        }
        if(selectedItem == R.id.ABOUT) {
            AlertDialog.Builder mAbout = new AlertDialog.Builder(this);
            mAbout.setTitle("About");
            mAbout.setMessage("PowerUp Battery Stats v0.8\nCopyright (C) 2017 Aidan Glen Arcilla\nAll rights reserved.");
            mAbout.setCancelable(false);
            mAbout.setPositiveButton("OK",null);
            mAbout.show();
        }
        if(selectedItem == R.id.MINIMIZE) {
            finish();
        }
        if(selectedItem == R.id.EXIT) {
            boolean svc = isMyServiceRunning(AmpereMeterService.class);
            if(svc) {
                DialogInterface.OnClickListener exitInterface = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface arg0, int which) {
                        switch(which) {
                            case DialogInterface.BUTTON_POSITIVE:
                                stopService(new Intent(MainActivity.this, AmpereMeterService.class));
                                NotificationManager mNotifyMgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                                mNotifyMgr.cancelAll();
                                System.exit(0);
                                break;
                            case DialogInterface.BUTTON_NEGATIVE:
                                break;
                        }
                    }
                };
                AlertDialog.Builder mExit = new AlertDialog.Builder(this);
                mExit.setTitle("Service currently active");
                mExit.setMessage("Are you sure you want to exit? Doing so will also stop the notification meter service.");
                mExit.setCancelable(false);
                mExit.setPositiveButton("Yes", exitInterface);
                mExit.setNegativeButton("No", exitInterface);
                mExit.show();
            } else {
                System.exit(0);
            }
        }
        return super.onOptionsItemSelected(item);
    }

    public String getDeviceName() {
        try {
            Process px = Runtime.getRuntime().exec("getprop ro.product.device");
            BufferedReader pxb = new BufferedReader(new InputStreamReader(px.getInputStream()));
            return pxb.readLine();
        } catch (IOException e) {
            Log.e(TAG, "Unable to gather device model, exiting...");
            return null;
        }
    }

    private void getAndSetBatteryData() {
        getBatteryData();
        setBatteryData();
    }

    public void getBatteryData() {
        try {
            if (mDevice.equals("ExcitePrime")) {
                float prop_charge_full = 2820.0f;
                float prop_capacity = Float.parseFloat(FileUtils.fileRead("/sys/class/power_supply/battery/capacity").trim());
                float prop_temp = Float.parseFloat(FileUtils.fileRead("/sys/class/power_supply/battery/batt_temp").trim());
                String prop_status = FileUtils.fileRead("/sys/class/power_supply/battery/status").trim();
                String prop_health = FileUtils.fileRead("/sys/class/power_supply/battery/health").trim();
                float prop_voltage = Float.parseFloat(FileUtils.fileRead("/sys/class/power_supply/battery/BatterySenseVoltage").trim());
                int prop_current_charging = Integer.parseInt(FileUtils.fileRead("/sys/class/power_supply/battery/BatteryAverageCurrent").trim());
                long prop_current_discharging = Long.parseLong(FileUtils.fileRead("/sys/class/power_supply/battery/device/FG_Battery_CurrentConsumption").trim());

                charge_now = Math.round(prop_charge_full * (prop_capacity / 100f));
                charge_full = (int) prop_charge_full;
                capacity = (int) prop_capacity;
                temp = prop_temp / 10;
                voltage = prop_voltage / 1000;
                status = prop_status;
                health = prop_health;

                if (status.equals("Charging") || status.equals("Full")) {
                    current = prop_current_charging * 2;
                } else {
                    if (prop_current_discharging > 40000000) {
                        current = 0;
                    } else {
                        current = (int) prop_current_discharging / -10;
                    }
                }
            } else if (mDevice.equals("FlareS")) {
                float prop_charge_full = 1400.0f;
                float prop_capacity = Float.parseFloat(FileUtils.fileRead("/sys/class/power_supply/battery/capacity").trim());
                float prop_temp = Float.parseFloat(FileUtils.fileRead("/sys/class/power_supply/battery/batt_temp").trim());
                String prop_status = FileUtils.fileRead("/sys/class/power_supply/battery/status").trim();
                String prop_health = FileUtils.fileRead("/sys/class/power_supply/battery/health").trim();
                float prop_voltage = Float.parseFloat(FileUtils.fileRead("/sys/class/power_supply/battery/BatterySenseVoltage").trim());
                int prop_current_charging = Integer.parseInt(FileUtils.fileRead("/sys/class/power_supply/battery/BatteryAverageCurrent").trim());
                long prop_current_discharging = Long.parseLong(FileUtils.fileRead("/sys/class/power_supply/battery/device/FG_Battery_CurrentConsumption").trim());

                charge_now = Math.round(prop_charge_full * (prop_capacity / 100f));
                charge_full = (int) prop_charge_full;
                capacity = (int) prop_capacity;
                temp = prop_temp / 10;
                voltage = prop_voltage / 1000;
                status = prop_status;
                health = prop_health;

                if (status.equals("Charging") || status.equals("Full")) {
                    current = prop_current_charging;
                } else {
                    if (prop_current_discharging > 40000000) {
                        current = 0;
                    } else {
                        current = (int) prop_current_discharging / -10;
                    }
                }
            } else if (mDevice.equals("ASUS_T00I")) {
                float prop_charge_now = Float.parseFloat(FileUtils.fileRead("/sys/class/power_supply/battery/charge_now").trim());
                float prop_charge_full = Float.parseFloat(FileUtils.fileRead("/sys/class/power_supply/battery/charge_full").trim());
                float prop_capacity = Float.parseFloat(FileUtils.fileRead("/sys/class/power_supply/battery/capacity").trim());
                float prop_temp = Float.parseFloat(FileUtils.fileRead("/sys/class/power_supply/battery/temp_ambient").trim());
                String prop_status = FileUtils.fileRead("/sys/class/power_supply/battery/status").trim();
                String prop_health = FileUtils.fileRead("/sys/class/power_supply/battery/health").trim();
                float prop_voltage = Float.parseFloat(FileUtils.fileRead("/sys/class/power_supply/battery/voltage_now").trim());
                int prop_current = Integer.parseInt(FileUtils.fileRead("/sys/class/power_supply/battery/current_now").trim());

                charge_now = (int) prop_charge_now;
                charge_full = (int) prop_charge_full;
                capacity = (int) prop_capacity;
                temp = prop_temp / 10;
                voltage = prop_voltage / 1000;
                status = prop_status;
                health = prop_health;
                current = prop_current;
            } else {
                Log.e(TAG, "Exiting. Device not supported.");
                System.exit(1);
            }
        } catch (IOException e) {

        }
    }

    public void setBatteryData() {
        if(temp >= 45) {
            txtTemperature.setTextColor(Color.parseColor("#FF0000"));
        } else {
            txtTemperature.setTextColor(Color.parseColor("#666666"));
        }

        tempf = (int) (((temp * 9) / 5) + 32);
        if(status.equals("Charging") && current < 0) {
            txtCurrent.setTextColor(Color.parseColor("#FF0000"));
        } else {
            txtCurrent.setTextColor(Color.parseColor("#666666"));
        }

        txtState.setText(status);
        txtHealth.setText(health);
        txtCurrent.setText(current + " mA");
        txtCapacity.setText(capacity + "%");
        txtCharge.setText(charge_now + " mAh");
        txtChargeTotal.setText(charge_full + " mAh");
        txtVoltage.setText(voltage + " V");
        txtTemperature.setText(temp + " °C (" + tempf + " °F)");

        mGauge.setProgress(capacity);
    }

    private void animateGauge() {
        ObjectAnimator animGauge = ObjectAnimator.ofInt(mGauge, "progress", 0, capacity);
        animGauge.setDuration(1000);
        animGauge.setInterpolator(new DecelerateInterpolator());
        animGauge.start();

        mGauge.clearAnimation();
    }

    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
