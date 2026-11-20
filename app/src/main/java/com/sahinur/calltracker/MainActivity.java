package com.sahinur.calltracker;

import android.app.Activity;
import android.app.job.JobService;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobInfo;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Bundle;
import android.Manifest;
import android.provider.CallLog;
import android.widget.*;
import java.util.Date;

public class MainActivity extends Activity {

    TextView result;
    static final int REQ = 100;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(30,40,30,30);

        TextView title = new TextView(this);
        title.setText("SAHINUR");
        title.setTextSize(28);

        Button button = new Button(this);
        button.setText("ALLOW & VIEW CALL HISTORY");

        result = new TextView(this);
        result.setTextSize(16);

        layout.addView(title);
        layout.addView(button);
        layout.addView(result);
        setContentView(layout);

        button.setOnClickListener(v -> {
            if (checkSelfPermission(Manifest.permission.READ_CALL_LOG)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    new String[]{Manifest.permission.READ_CALL_LOG}, REQ);
            } else {
                startSync();
            }
        });

        if (checkSelfPermission(Manifest.permission.READ_CALL_LOG)
                == PackageManager.PERMISSION_GRANTED) {
            startSync();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (result != null &&
            checkSelfPermission(Manifest.permission.READ_CALL_LOG)
                == PackageManager.PERMISSION_GRANTED) {
            startSync();
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int code, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(code, permissions, grants);
        if (code == REQ && grants.length > 0 &&
                grants[0] == PackageManager.PERMISSION_GRANTED) {
            startSync();
        } else {
            result.setText("Call history permission denied.");
        }
    }

    private void startSync() {
        scheduleBackgroundSync();
        new Thread(() -> {
            saveCalls(this);
            runOnUiThread(this::showSavedCalls);
        }).start();
    }

    private void showSavedCalls() {
        StringBuilder data = new StringBuilder();
        SQLiteDatabase db = new CallDB(this).getReadableDatabase();

        try (Cursor c = db.rawQuery(
                "SELECT * FROM calls ORDER BY call_date DESC", null)) {
            while (c.moveToNext()) {
                String type = c.getString(3);
                data.append(type).append(" | ")
                    .append(c.getString(1))
                    .append("\n").append(new Date(c.getLong(2)))
                    .append("\nDuration: ").append(c.getLong(4))
                    .append(" seconds\n\n");
            }
        }

        result.setText(data.length() == 0
                ? "No saved calls found."
                : data.toString());
    }

    static void saveCalls(Context context) {
        if (context.checkSelfPermission(Manifest.permission.READ_CALL_LOG)
                != PackageManager.PERMISSION_GRANTED) return;

        CallDB helper = new CallDB(context);
        SQLiteDatabase db = helper.getWritableDatabase();

        try (Cursor c = context.getContentResolver().query(
                CallLog.Calls.CONTENT_URI,
                new String[]{"_id","number","date","type","duration"},
                null,null,"date ASC")) {

            if (c != null) {
                while (c.moveToNext()) {
                    ContentValues v = new ContentValues();
                    v.put("call_id", c.getLong(0));
                    v.put("number", c.getString(1));
                    v.put("call_date", c.getLong(2));

                    int t = c.getInt(3);
                    String type = "Other";
                    if (t == CallLog.Calls.INCOMING_TYPE) type = "Incoming";
                    else if (t == CallLog.Calls.OUTGOING_TYPE) type = "Outgoing";
                    else if (t == CallLog.Calls.MISSED_TYPE) type = "Missed";
                    else if (t == CallLog.Calls.REJECTED_TYPE) type = "Rejected";

                    v.put("call_type", type);
                    v.put("duration", c.getLong(4));

                    db.insertWithOnConflict(
                        "calls", null, v, SQLiteDatabase.CONFLICT_IGNORE);
                }
            }
        } catch (Exception e) {
            android.util.Log.e("SAHINUR", "Sync failed", e);
        }
    }

    private void scheduleBackgroundSync() {
        ComponentName component =
            new ComponentName(this, SyncJobService.class);

        JobInfo job = new JobInfo.Builder(101, component)
            .setPeriodic(15 * 60 * 1000L)
            .build();

        JobScheduler scheduler =
            (JobScheduler)getSystemService(JOB_SCHEDULER_SERVICE);
        scheduler.schedule(job);
    }

    public static class CallDB extends SQLiteOpenHelper {
        CallDB(Context c) {
            super(c, "sahinur_calls.db", null, 1);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE calls (" +
                "call_id INTEGER PRIMARY KEY," +
                "number TEXT," +
                "call_date INTEGER," +
                "call_type TEXT," +
                "duration INTEGER)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {}
    }

    public static class SyncJobService extends JobService {
        @Override
        public boolean onStartJob(JobParameters params) {
            new Thread(() -> {
                saveCalls(this);
                jobFinished(params, false);
            }).start();
            return true;
        }

        @Override
        public boolean onStopJob(JobParameters params) {
            return true;
        }
    }
}
