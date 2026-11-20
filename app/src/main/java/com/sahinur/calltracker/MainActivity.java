package com.sahinur.calltracker;

import android.app.Activity;
import android.os.Bundle;
import android.Manifest;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.CallLog;
import android.widget.TextView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.view.View;
import java.util.Date;

public class MainActivity extends Activity {

    TextView result;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(30, 40, 30, 30);

        TextView title = new TextView(this);
        title.setText("SAHINUR");
        title.setTextSize(28);

        result = new TextView(this);
        result.setTextSize(16);
        result.setText("Call history permission required.");

        Button button = new Button(this);
        button.setText("Allow & View Call History");

        layout.addView(title);
        layout.addView(button);
        layout.addView(result);

        setContentView(layout);

        button.setOnClickListener(v -> {
            if (checkSelfPermission(Manifest.permission.READ_CALL_LOG)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    new String[]{Manifest.permission.READ_CALL_LOG}, 100);
            } else {
                showCallHistory();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 100 && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            showCallHistory();
        } else {
            result.setText("Call history permission denied.");
        }
    }

    private void showCallHistory() {
        StringBuilder data = new StringBuilder();

        try (Cursor cursor = getContentResolver().query(
                CallLog.Calls.CONTENT_URI,
                null, null, null,
                CallLog.Calls.DATE + " DESC")) {

            if (cursor != null) {
                int numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER);
                int dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE);
                int typeIndex = cursor.getColumnIndex(CallLog.Calls.TYPE);
                int durationIndex = cursor.getColumnIndex(CallLog.Calls.DURATION);

                while (cursor.moveToNext()) {
                    String number = cursor.getString(numberIndex);
                    long date = cursor.getLong(dateIndex);
                    int type = cursor.getInt(typeIndex);
                    long duration = cursor.getLong(durationIndex);

                    String callType = "Other";
                    if (type == CallLog.Calls.INCOMING_TYPE) {
                        callType = "Incoming";
                    } else if (type == CallLog.Calls.OUTGOING_TYPE) {
                        callType = "Outgoing";
                    } else if (type == CallLog.Calls.MISSED_TYPE) {
                        callType = "Missed";
                    }

                    data.append(callType)
                        .append(" | ").append(number)
                        .append("\n").append(new Date(date))
                        .append("\nDuration: ").append(duration)
                        .append(" seconds\n\n");
                }
            }
        } catch (Exception e) {
            data.append("Error: ").append(e.getMessage());
        }

        result.setText(data.length() == 0
                ? "No call history found."
                : data.toString());
    }
}
