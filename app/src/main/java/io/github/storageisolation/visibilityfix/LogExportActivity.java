package io.github.storageisolation.visibilityfix;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class LogExportActivity extends Activity {
    private TextView status;
    private Button export;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        panel.setPadding(pad, pad, pad, pad);
        export = new Button(this);
        export.setText(R.string.export_logs);
        status = new TextView(this);
        status.setText(R.string.ready);
        status.setTextIsSelectable(true);
        panel.addView(export);
        panel.addView(status);
        setContentView(panel);
        export.setOnClickListener((View v) -> saveLogs());
    }

    private void saveLogs() {
        export.setEnabled(false);
        status.setText(R.string.working);
        String filename = "FixRedirectStorage-" +
                new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".log";
        String destination = "/storage/emulated/0/Download/" + filename;
        new Thread(() -> {
            try {
                // Filename is generated from a fixed ASCII prefix and numeric timestamp.
                String script = "{ "
                        + "echo '=== Device ==='; date; cat /proc/uptime; "
                        + "echo '=== Installed APK ==='; pm path io.github.storageisolation.visibilityfix; "
                        + "echo '=== LSPosed ==='; cat /data/adb/modules/zygisk_lsposed/module.prop; "
                        + "echo '=== LSPosed module entries ==='; "
                        + "grep -iE 'SIVisibilityFix|visibilityfix|FixRedirectStorage' "
                        + "/data/adb/lspd/log/modules_*.log /data/adb/lspd/log/verbose_*.log 2>/dev/null; "
                        + "echo '=== Android logcat entries ==='; "
                        + "logcat -b all -d 2>/dev/null | grep -iE 'SIVisibilityFix|visibilityfix|FixRedirectStorage'; "
                        + "echo '=== End ==='; } > '" + destination + "' 2>&1; "
                        + "test -s '" + destination + "'";
                Process process = new ProcessBuilder("su", "-c", script)
                        .redirectErrorStream(true).start();
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                try (InputStream input = process.getInputStream()) {
                    byte[] buffer = new byte[4096];
                    int size;
                    while ((size = input.read(buffer)) != -1) output.write(buffer, 0, size);
                }
                int exit = process.waitFor();
                String message = output.toString(StandardCharsets.UTF_8.name()).trim();
                runOnUiThread(() -> {
                    export.setEnabled(true);
                    status.setText(exit == 0 ? getString(R.string.export_success, destination)
                            : getString(R.string.export_failed,
                                    "root 命令退出码 " + exit + " " + message));
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    export.setEnabled(true);
                    status.setText(getString(R.string.export_failed, e.toString()));
                });
            }
        }, "log-export").start();
    }
}
