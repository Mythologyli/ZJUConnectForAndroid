package cx.myth.zjuconnect;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import mobile.Mobile;

public class MyVpnService extends VpnService {
    private ParcelFileDescriptor tun;
    private final ExecutorService executors = Executors.newFixedThreadPool(1);
    private final BroadcastReceiver stopReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Objects.equals(intent.getAction(), "cx.myth.zjuconnect.STOP_VPN")) {
                stop();
                stopSelf();
            }
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(this);
        localBroadcastManager.registerReceiver(stopReceiver, new IntentFilter("cx.myth.zjuconnect.STOP_VPN"));

        new Thread(() -> {
            String ip = Mobile.login(intent.getStringExtra("server"), intent.getStringExtra("username"), intent.getStringExtra("password"));
            String dnsServer = intent.getStringExtra("dns_server");
            if (ip.equals("")) {
                localBroadcastManager.sendBroadcast(new Intent("cx.myth.zjuconnect.LOGIN_FAILED"));
                stopSelf();
                return;
            }

            localBroadcastManager.sendBroadcast(new Intent("cx.myth.zjuconnect.LOGIN_SUCCEEDED"));

            try {
                Builder builder = new Builder().addAddress(ip, 8).addRoute("10.0.0.0", 8).addDnsServer(dnsServer).setMtu(1400);
                tun = builder.establish();

                executors.submit(() -> {
                    Mobile.startStack(tun.getFd());
                    localBroadcastManager.sendBroadcast(new Intent("cx.myth.zjuconnect.STACK_STOPPED"));
                    stop();
                    stopSelf();
                });
            } catch (Exception e) {
                e.printStackTrace();
                localBroadcastManager.sendBroadcast(new Intent("cx.myth.zjuconnect.LOGIN_FAILED"));
                stopSelf();
            }
        }).start();

        return START_STICKY;
    }

    public void stop() {
        if (tun != null) {
            try {
                tun.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (executors != null) {
            executors.shutdownNow();
        }
    }

    @Override
    public void onDestroy() {
        stop();

        super.onDestroy();
    }
}