package com.example.catapulter;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class MainActivity extends AppCompatActivity {
    //Views
    TCPview mTCPview;
    CatapulterView mCatapulterView;

    //Firebase Declarations
    private FirebaseDatabase mFbDb;
    private DatabaseReference mFbDbRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getSupportActionBar().hide();

        mTCPview = findViewById(R.id.TCPview);
        mTCPview.setTCP_ViewEventListener(new TCPview.TCPviewEventListenerInterface() {
            @Override
            public void onServerMessageReceived(String msg) {
                if (msg.contains("EXTERNAL_LAUNCH_CATAPULT")){
                    mCatapulterView.launcCatapultExternally(); //Launches catapult from esp8266/Hercules
                }
            }

            @Override
            public void onServerBytesReceived(byte[] bytes) {

            }

            @Override
            public void onMessageSentToServer(String msg) {

            }
        });

        //CATAPULTER VIEW
        mCatapulterView = findViewById(R.id.catapulterView);
        mCatapulterView.setCatapulterViewInterface(new CatapulterView.CatapulterViewInterface() {
            @Override
            public void catapultLaunchEvent(String launchCommandString, double catapultPowerValue) {
                mTCPview.sendTCP_StringToServer(launchCommandString); //Sends Message to Launch Catapult to e.g. ESP8266 - "LAUNCH_CATAPULT"
            }

            @Override
            public void catapultAlienHitEvent(Integer hitCount) {
                mTCPview.sendTCP_StringToServer("ALIEN_HIT: " + String.valueOf(hitCount));
            }

            @Override
            public void catapultStartChargeEvent(String chargeStartCommandString, double catapultDutyCyclePercent) {
                mTCPview.sendTCP_StringToServer(chargeStartCommandString); // "START_CHARGE"
            }

            @Override
            public void catapultStopChargeEvent(String chargeStopCommandString) {
                mTCPview.sendTCP_StringToServer(chargeStopCommandString); //"STOP_CHARGE"
            }

            @Override
            public void catapultGetVoltageEvent(String getVoltageCommandString) {
                mTCPview.sendTCP_StringToServer("GET_VOLTAGE"); //Returns voltage as string
            }

        });

    }
}