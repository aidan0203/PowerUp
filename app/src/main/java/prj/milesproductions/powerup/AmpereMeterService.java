package prj.milesproductions.powerup;

import android.app.ActivityManager;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;

public class AmpereMeterService extends Service {
    public Runnable mTask;
    private Handler handler;
    private int mNotificationId = 1;
    private MainActivity m;
    private NotificationManager mNotifyMgr;

    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public void onCreate() {
        super.onCreate();

        handler = new Handler();
        m = new MainActivity();
        mNotifyMgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        Intent resultIntent = new Intent(this, MainActivity.class);
        final PendingIntent resultPendingIntent = PendingIntent.getActivity(this, 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        m.mDevice = m.getDeviceName();
        m.getBatteryData();

        Notification mNotif = new NotificationCompat.Builder(this)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setWhen(0)
            .setContentIntent(resultPendingIntent)
            .setContentTitle(m.status + " (" + m.capacity + "%)")
            .setContentText("C: " + m.charge_now + " / " + m.charge_full + " mAh (" + m.current + " mA) T: " + m.temp + " °C")
            .setPriority(Notification.PRIORITY_LOW)
            .build();

        mNotifyMgr.notify(mNotificationId, mNotif);

        mTask = new Runnable() {
            @Override
            public void run() {
                boolean s = isMyServiceRunning(AmpereMeterService.class);
                if(s) {
                    m.getBatteryData();

                    Notification mNotif = new NotificationCompat.Builder(AmpereMeterService.this)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setOngoing(true)
                        .setWhen(0)
                        .setContentIntent(resultPendingIntent)
                        .setContentTitle(m.status + " (" + m.capacity + "%)")
                        .setContentText("C: " + m.charge_now + " / " + m.charge_full + " mAh (" + m.current + " mA) T: " + m.temp + " °C")
                        .setPriority(Notification.PRIORITY_LOW)
                        .build();

                    mNotifyMgr.notify(mNotificationId, mNotif);
                    handler.postDelayed(this, 5000);
                } else {
                    handler.removeCallbacks(mTask);
                }
            }
        };
        handler.postDelayed(mTask, 5000);
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }
}
