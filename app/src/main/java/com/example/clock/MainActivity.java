package com.example.clock;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.os.BatteryManager;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import android.icu.util.ChineseCalendar;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Calendar;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
/**
 * Created by mulan on 21/1/21.
 */
public class MainActivity extends Activity implements View.OnClickListener, FlipLayout.FlipOverListener {

    private TextView dateInfoTextView;
    private FlipLayout bit_hour;
    private FlipLayout bit_minute;
    private FlipLayout bit_second;
    private BatteryIconView batteryIconView;
    private TextView batteryPercentageTextView;
    private boolean batteryReceiverRegistered;
    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, Intent intent) {
            updateBatteryLevel(intent);
        }
    };
    private Calendar oldNumber = Calendar.getInstance();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        this.dateInfoTextView = findViewById(R.id.date_info_text);
        this.bit_second = (FlipLayout) findViewById(R.id.bit_flip_3);
        this.bit_minute = (FlipLayout) findViewById(R.id.bit_flip_2);
        this.bit_hour = (FlipLayout) findViewById(R.id.bit_flip_1);
        batteryIconView = findViewById(R.id.battery_icon);
        batteryPercentageTextView = findViewById(R.id.battery_percentage);

        bit_hour.flip(oldNumber.get(Calendar.HOUR_OF_DAY),24,TimeTAG.hour);
        bit_minute.flip( oldNumber.get(Calendar.MINUTE),60,TimeTAG.min);
        bit_second.flip(oldNumber.get(Calendar.SECOND),60,TimeTAG.sec);
        updateDateInfo();

        IntentFilter batteryFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        updateBatteryLevel(registerReceiver(batteryReceiver, batteryFilter));
        batteryReceiverRegistered = true;

        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                start();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateDateInfo();
                    }
                });
            }
        }, 1000, 1000);//每一秒执行一次
//        bit_hour.addFlipOverListener(this);
//        bit_minute.addFlipOverListener(this);
//        bit_second.addFlipOverListener(this);
    }

    private void updateBatteryLevel(Intent batteryStatus) {
        if (batteryStatus == null) {
            return;
        }
        int level = batteryStatus.getIntExtra("level", -1);
        int scale = batteryStatus.getIntExtra("scale", -1);
        if (level < 0 || scale <= 0) {
            return;
        }

        int percentage = Math.max(0, Math.min(100, (level * 100 + scale / 2) / scale));
        batteryIconView.setBatteryLevel(percentage);
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
        batteryIconView.setCharging(status == BatteryManager.BATTERY_STATUS_CHARGING);
        batteryPercentageTextView.setText(percentage + "%");
        int color = getResources().getColor(
                percentage < 20 ? R.color.battery_low : R.color.sky_blue);
        batteryPercentageTextView.setTextColor(color);
    }

    private void updateDateInfo() {
        LocalDate today = LocalDate.now();
        String dateText = today.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日", Locale.CHINA));

        java.util.Calendar utilCalendar = java.util.Calendar.getInstance(Locale.CHINA);
        utilCalendar.setTimeInMillis(System.currentTimeMillis());
        ChineseCalendar chineseCalendar = new ChineseCalendar(Locale.CHINA);
        chineseCalendar.setTimeInMillis(utilCalendar.getTimeInMillis());

        int lunarMonth = chineseCalendar.get(ChineseCalendar.MONTH) + 1;
        int lunarDay = chineseCalendar.get(ChineseCalendar.DAY_OF_MONTH);
        boolean isLeapMonth = chineseCalendar.get(ChineseCalendar.IS_LEAP_MONTH) == 1;
        String lunarText = "农历" + formatLunarMonth(lunarMonth, isLeapMonth) + formatLunarDay(lunarDay);

        String weekText = today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.CHINA);
        dateInfoTextView.setText(dateText + " " + lunarText + " " + weekText);
    }

    private String formatLunarMonth(int month, boolean isLeapMonth) {
        String[] months = {"正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "腊"};
        String monthText = months[Math.max(0, Math.min(month - 1, months.length - 1))];
        if (isLeapMonth) {
            return "闰" + monthText + "月";
        }
        return monthText + "月";
    }

    private String formatLunarDay(int day) {
        if (day == 1) {
            return "初一";
        }
        if (day == 10) {
            return "初十";
        }
        if (day == 15) {
            return "十五";
        }
        if (day <= 9) {
            return "初" + getLunarDigit(day);
        }
        if (day <= 19) {
            return "十" + getLunarDigit(day - 10);
        }
        if (day == 20) {
            return "二十";
        }
        if (day <= 29) {
            return "廿" + getLunarDigit(day - 20);
        }
        return "三十";
    }

    private String getLunarDigit(int value) {
        switch (value) {
            case 1:
                return "一";
            case 2:
                return "二";
            case 3:
                return "三";
            case 4:
                return "四";
            case 5:
                return "五";
            case 6:
                return "六";
            case 7:
                return "七";
            case 8:
                return "八";
            case 9:
                return "九";
            default:
                return String.valueOf(value);
        }
    }



    @Override
    public void onClick(View v) {

            start();

        }

    @Override
    public void onFLipOver(FlipLayout flipLayout) {
//        if(flipLayout.isFlipping()){
//            flipLayout.smoothFlip(1, true);
//        }
    }

    @Override
    protected void onDestroy() {
        if (batteryReceiverRegistered) {
            unregisterReceiver(batteryReceiver);
            batteryReceiverRegistered = false;
        }
        super.onDestroy();
    }

    public void  start(){
        Calendar now = Calendar.getInstance();
        int nhour = now.get(Calendar.HOUR_OF_DAY);
        int nminute = now.get(Calendar.MINUTE);
        int nsecond = now.get(Calendar.SECOND);

        int ohour = oldNumber.get(Calendar.HOUR_OF_DAY);
        int ominute = oldNumber.get(Calendar.MINUTE);
        int osecond = oldNumber.get(Calendar.SECOND);

        oldNumber = now;

        int hour = nhour - ohour;
        int minute = nminute - ominute;
        int second = nsecond- osecond;

       
        if (hour >= 1||hour==-23) {
            bit_hour.smoothFlip(1, 24,TimeTAG.hour,false);

        }

        if (minute >=1||minute==-59) {
            bit_minute.smoothFlip(1, 60,TimeTAG.min,false);

        }

        if (second >=1||second==-59) {
            bit_second.smoothFlip(1, 60,TimeTAG.sec,false);
        }//当下一秒变为0时减去上一秒是-59



    }
}
