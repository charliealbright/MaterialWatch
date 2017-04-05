package jankapotamus.material;

import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.concurrent.TimeUnit;

/**
 * Created by Charlie on 6/29/15.
 */
public class ListenerService extends WearableListenerService {

    private static final int CONNECTION_TIME_OUT_MS = 3000;

    private float mBatteryLevel;
    private String mNodeID;

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        mNodeID = messageEvent.getSourceNodeId();
        if (messageEvent.getPath().equals("/Battery")) {
            getBatteryLevel();
            reply();
        } else {
            //possibly send error message
        }
    }

    private void getBatteryLevel() {
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, intentFilter);
        mBatteryLevel = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
    }

    private void reply() {
        DataMap batteryInfo = new DataMap();
        batteryInfo.putFloat("batteryLevel", mBatteryLevel);
        GoogleApiClient client = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .build();
        client.blockingConnect(CONNECTION_TIME_OUT_MS, TimeUnit.MILLISECONDS);
        Wearable.MessageApi.sendMessage(client, mNodeID, "/Battery", batteryInfo.toByteArray());
        client.disconnect();
    }
}
