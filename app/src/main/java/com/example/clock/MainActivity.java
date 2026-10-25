package com.example.clock;

import android.app.Activity;
import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ActivityInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import android.icu.util.ChineseCalendar;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
/**
 * Created by mulan on 21/1/21.
 */
public class MainActivity extends Activity implements View.OnClickListener, FlipLayout.FlipOverListener {

    private static final int LOCATION_PERMISSION_REQUEST = 1001;
    private static final String TAG = "MainActivity";
    private static final long WEATHER_REFRESH_INTERVAL = 30 * 60 * 1000L;
    private TextView dateInfoTextView;
    private FlipLayout bit_hour;
    private FlipLayout bit_minute;
    private FlipLayout bit_second;
    private BatteryIconView batteryIconView;
    private TextView batteryPercentageTextView;
    private WeatherIconView weatherIconView;
    private TextView weatherInfoTextView;
    private DayProgressView dayProgressView;
    private View weatherStatusView;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private TextToSpeech textToSpeech;
    private boolean textToSpeechReady;
    private boolean activityResumed;
    private int pendingAnnouncementHour = -1;
    private final ExecutorService weatherExecutor = Executors.newSingleThreadExecutor();
    private final Runnable locationTimeout = () -> {
        if (locationListener != null) {
            stopLocationUpdates();
        }
    };
    private final Runnable weatherRefresh = new Runnable() {
        @Override
        public void run() {
            requestWeatherLocation();
            clockHandler.postDelayed(this, WEATHER_REFRESH_INTERVAL);
        }
    };
    private int weatherRequestId;
    private boolean batteryReceiverRegistered;
    private boolean locationPermissionRequested;
    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            start();
            updateDateInfo();
            scheduleNextClockTick();
        }
    };
    private final Runnable dayProgressTick = new Runnable() {
        @Override
        public void run() {
            updateDayProgress();
            clockHandler.postDelayed(this, 30 * 1000L);
        }
    };
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
        weatherIconView = findViewById(R.id.weather_icon);
        weatherInfoTextView = findViewById(R.id.weather_info);
        dayProgressView = findViewById(R.id.day_progress);
        weatherStatusView = findViewById(R.id.weather_status);
        weatherStatusView.setContentDescription("天气数据来源 Open-Meteo");
        weatherStatusView.setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://open-meteo.com/"))));
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        textToSpeech = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS) {
                Log.e(TAG, "Unable to initialize the default text-to-speech engine: " + status);
                return;
            }
            if (textToSpeech == null) {
                return;
            }

            int languageStatus = textToSpeech.setLanguage(Locale.SIMPLIFIED_CHINESE);
            if (languageStatus == TextToSpeech.LANG_MISSING_DATA
                    || languageStatus == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "Chinese text-to-speech language is unavailable: " + languageStatus);
                return;
            }

            textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {
                    Log.d(TAG, "Hourly announcement started");
                }

                @Override
                public void onDone(String utteranceId) {
                    Log.d(TAG, "Hourly announcement completed");
                }

                @Override
                public void onError(String utteranceId) {
                    Log.e(TAG, "Hourly announcement failed");
                }
            });
            textToSpeechReady = true;
            Log.i(TAG, "Default TTS engine ready: " + textToSpeech.getDefaultEngine());
            if (activityResumed && pendingAnnouncementHour >= 0) {
                int hour = pendingAnnouncementHour;
                pendingAnnouncementHour = -1;
                announceHour(hour);
            }
        });

        oldNumber = Calendar.getInstance();
        bit_hour.flip(oldNumber.get(Calendar.HOUR_OF_DAY),24,TimeTAG.hour);
        bit_minute.flip( oldNumber.get(Calendar.MINUTE),60,TimeTAG.min);
        bit_second.flip(oldNumber.get(Calendar.SECOND),60,TimeTAG.sec);
        updateDateInfo();

        IntentFilter batteryFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        updateBatteryLevel(registerReceiver(batteryReceiver, batteryFilter));
        batteryReceiverRegistered = true;

//        bit_hour.addFlipOverListener(this);
//        bit_minute.addFlipOverListener(this);
//        bit_second.addFlipOverListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityResumed = true;
        pendingAnnouncementHour = -1;
        oldNumber = Calendar.getInstance();
        bit_hour.flip(oldNumber.get(Calendar.HOUR_OF_DAY), 24, TimeTAG.hour);
        bit_minute.flip(oldNumber.get(Calendar.MINUTE), 60, TimeTAG.min);
        bit_second.flip(oldNumber.get(Calendar.SECOND), 60, TimeTAG.sec);
        start();
        updateDateInfo();
        updateDayProgress();
        clockHandler.removeCallbacks(dayProgressTick);
        clockHandler.postDelayed(dayProgressTick, 30 * 1000L);
        scheduleNextClockTick();
        requestWeatherLocation();
        scheduleWeatherRefresh();
    }

    @Override
    protected void onPause() {
        activityResumed = false;
        pendingAnnouncementHour = -1;
        if (textToSpeech != null) {
            textToSpeech.stop();
        }
        clockHandler.removeCallbacks(clockTick);
        clockHandler.removeCallbacks(dayProgressTick);
        clockHandler.removeCallbacks(weatherRefresh);
        stopLocationUpdates();
        super.onPause();
    }

    private void scheduleWeatherRefresh() {
        clockHandler.removeCallbacks(weatherRefresh);
        clockHandler.postDelayed(weatherRefresh, WEATHER_REFRESH_INTERVAL);
    }

    private void updateDayProgress() {
        Calendar now = Calendar.getInstance();
        long elapsedToday = (((now.get(Calendar.HOUR_OF_DAY) * 60L + now.get(Calendar.MINUTE)) * 60L
                + now.get(Calendar.SECOND)) * 1000L) + now.get(Calendar.MILLISECOND);
        dayProgressView.setProgress(elapsedToday / (24f * 60 * 60 * 1000));
    }

    private void requestWeatherLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (!locationPermissionRequested) {
                locationPermissionRequested = true;
                requestPermissions(new String[] {
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION
                }, LOCATION_PERMISSION_REQUEST);
            }
            return;
        }

        Location lastLocation = getLastKnownLocation();
        if (lastLocation != null && System.currentTimeMillis() - lastLocation.getTime() < 30 * 60 * 1000L) {
            fetchWeather(lastLocation);
            return;
        }

        requestSingleLocationUpdate();
    }

    private Location getLastKnownLocation() {
        Location bestLocation = null;
        try {
            for (String provider : locationManager.getProviders(true)) {
                Location candidate = locationManager.getLastKnownLocation(provider);
                if (candidate != null && (bestLocation == null || candidate.getTime() > bestLocation.getTime())) {
                    bestLocation = candidate;
                }
            }
        } catch (SecurityException e) {
            Log.w(TAG, "Location permission was revoked while reading the last location", e);
        }
        return bestLocation;
    }

    private void requestSingleLocationUpdate() {
        stopLocationUpdates();
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                stopLocationUpdates();
                fetchWeather(location);
            }

            @Override
            public void onProviderEnabled(String provider) {
            }

            @Override
            public void onStatusChanged(String provider, int status, android.os.Bundle extras) {
            }
        };

        try {
            boolean requested = false;
            for (String provider : locationManager.getProviders(true)) {
                locationManager.requestLocationUpdates(provider, 0, 0, locationListener, Looper.getMainLooper());
                requested = true;
            }
            if (requested) {
                clockHandler.postDelayed(locationTimeout, 20000);
            }
        } catch (SecurityException e) {
            stopLocationUpdates();
            Log.w(TAG, "Location permission was revoked while requesting updates", e);
        }
    }

    private void stopLocationUpdates() {
        clockHandler.removeCallbacks(locationTimeout);
        if (locationListener != null) {
            locationManager.removeUpdates(locationListener);
            locationListener = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            boolean granted = false;
            for (int result : grantResults) {
                granted |= result == PackageManager.PERMISSION_GRANTED;
            }
            if (granted) {
                requestWeatherLocation();
            }
        }
    }

    private void fetchWeather(Location location) {
        final int requestId = ++weatherRequestId;
        weatherExecutor.execute(() -> {
            try {
                WeatherData weather = loadWeather(location);
                runOnUiThread(() -> {
                    if (requestId == weatherRequestId && !isFinishing()) {
                        weatherIconView.setWeatherCode(weather.weatherCode, weather.isDay);
                        weatherIconView.setContentDescription(describeWeather(weather.weatherCode, weather.isDay));
                        weatherInfoTextView.setTextColor(weatherIconView.getTemperatureColor());
                        weatherInfoTextView.setText(String.format(Locale.CHINA,
                                "%.0f°C", weather.temperature));
                    }
                });
            } catch (IOException | JSONException e) {
                Log.e(TAG, "Unable to load current weather", e);
                runOnUiThread(() -> {
                    if (requestId == weatherRequestId && !isFinishing()) {
                        weatherInfoTextView.setText("");
                    }
                });
            }
        });
    }

    private WeatherData loadWeather(Location location) throws IOException, JSONException {
        String address = String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.5f&longitude=%.5f&current=temperature_2m,weather_code,is_day&timezone=auto",
                location.getLatitude(), location.getLongitude());
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setRequestMethod("GET");
        try {
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("Open-Meteo returned HTTP " + responseCode);
            }
            try (InputStream inputStream = connection.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                JSONObject current = new JSONObject(response.toString()).getJSONObject("current");
                return new WeatherData(current.getDouble("temperature_2m"), current.getInt("weather_code"),
                        current.getInt("is_day") == 1);
            }
        } finally {
            connection.disconnect();
        }
    }

    private String describeWeather(int weatherCode, boolean isDay) {
        String description;
        switch (weatherCode) {
            case 0: description = "晴朗"; break;
            case 1: description = "少云"; break;
            case 2: description = "多云"; break;
            case 3: description = "阴"; break;
            case 45:
            case 48: description = "雾"; break;
            case 51:
            case 56:
            case 61:
            case 80: description = "小雨"; break;
            case 53:
            case 57:
            case 63:
            case 81: description = "中雨"; break;
            case 55:
            case 65:
            case 82:
            case 67: description = "大雨"; break;
            case 66: description = "小雨"; break;
            case 71:
            case 73:
            case 75:
            case 77:
            case 85:
            case 86: description = "降雪"; break;
            case 95:
            case 96:
            case 99: description = "雷雨"; break;
            default: description = "天气"; break;
        }
        return isDay ? description : "夜间" + description;
    }

    private static class WeatherData {
        final double temperature;
        final int weatherCode;
        final boolean isDay;

        WeatherData(double temperature, int weatherCode, boolean isDay) {
            this.temperature = temperature;
            this.weatherCode = weatherCode;
            this.isDay = isDay;
        }
    }

    private void scheduleNextClockTick() {
        clockHandler.removeCallbacks(clockTick);
        long delay = 1000 - (System.currentTimeMillis() % 1000);
        clockHandler.postDelayed(clockTick, delay);
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
        weatherRequestId++;
        stopLocationUpdates();
        weatherExecutor.shutdownNow();
        if (textToSpeech != null) {
            textToSpeech.shutdown();
            textToSpeech = null;
        }
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

        if (nhour != ohour) {
            bit_hour.smoothFlip(1, 24,TimeTAG.hour,false);
            announceHour(nhour);
        }

        if (nminute != ominute) {
            bit_minute.smoothFlip(1, 60,TimeTAG.min,false);

        }

        if (nsecond != osecond) {
            bit_second.smoothFlip(1, 60,TimeTAG.sec,false);
        }



    }

    private void announceHour(int hour) {
        if (!activityResumed) {
            return;
        }
        if (!textToSpeechReady) {
            pendingAnnouncementHour = hour;
            return;
        }

        String announcement = String.format(Locale.SIMPLIFIED_CHINESE, "现在是%d点整", hour);
        int result = textToSpeech.speak(announcement, TextToSpeech.QUEUE_FLUSH, null,
                "hour-" + System.currentTimeMillis());
        if (result == TextToSpeech.ERROR) {
            Log.e(TAG, "Unable to speak hourly announcement");
        }
    }
}
