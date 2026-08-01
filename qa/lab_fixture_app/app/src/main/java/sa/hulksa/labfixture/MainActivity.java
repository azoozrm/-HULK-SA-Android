package sa.hulksa.labfixture;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** A deterministic, product-independent focus and callback fixture for lab qualification. */
public final class MainActivity extends Activity {
    private static final String TAG = "HULK_FIXTURE";
    private static final int WIFI = 1001;
    private static final int SCHEDULE = 1002;
    private static final int CONCURRENT = 1003;
    private static final int R1_PRIMARY = 1101;
    private static final int R1_PAUSE = 1102;
    private static final int R1_RESUME = 1103;
    private static final int R1_CANCEL = 1104;
    private static final int R1_PRIORITY = 1105;
    private static final int R1_DELETE = 1106;
    private static final int R2_PRIMARY = 1201;
    private static final int R2_PAUSE = 1202;
    private static final int R2_RESUME = 1203;
    private static final int R2_CANCEL = 1204;
    private static final int R2_PRIORITY = 1205;
    private static final int R2_DELETE = 1206;
    private static final int LIVE_1 = 1301;
    private static final int LIVE_2 = 1302;

    private final Map<String, AtomicInteger> revisions = new LinkedHashMap<>();
    private String launchToken;
    private String fault;
    private TextView evidence;
    private long originBytes;
    private long repositoryBytes;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        launchToken = valueOr(getIntent().getStringExtra("launch_token"), "missing-launch-token");
        fault = valueOr(getIntent().getStringExtra("fixture_fault"), "none");
        setContentView(buildUi());
        wireFocusGraph();
        findViewById(WIFI).requestFocus();
        emitState("ready");
    }

    private View buildUi() {
        LinearLayout root = column();
        root.setPadding(32, 24, 32, 24);
        root.setBackgroundColor(Color.rgb(20, 24, 32));
        root.setContentDescription("qa-page:fixture qa-launch-token:" + launchToken);

        evidence = new TextView(this);
        evidence.setTextColor(Color.WHITE);
        evidence.setTextSize(18);
        evidence.setPadding(12, 12, 12, 12);
        root.addView(evidence, matchWrap());

        LinearLayout toolbar = row();
        toolbar.addView(button(WIFI, "toolbar-wifi", "wifi"));
        toolbar.addView(button(SCHEDULE, "toolbar-schedule", "schedule"));
        toolbar.addView(button(CONCURRENT, "toolbar-concurrent", "concurrent"));
        root.addView(toolbar, matchWrap());

        root.addView(downloadRow(1, R1_PRIMARY, R1_PAUSE, R1_RESUME, R1_CANCEL, R1_PRIORITY, R1_DELETE), matchWrap());
        root.addView(downloadRow(2, R2_PRIMARY, R2_PAUSE, R2_RESUME, R2_CANCEL, R2_PRIORITY, R2_DELETE), matchWrap());

        LinearLayout live = row();
        live.addView(button(LIVE_1, "live-item-1", "live-select"));
        live.addView(button(LIVE_2, "live-item-2", "live-select"));
        root.addView(live, matchWrap());

        return root;
    }

    private LinearLayout downloadRow(int row, int primary, int pause, int resume, int cancel, int priority, int delete) {
        LinearLayout layout = row();
        layout.setContentDescription("download-row-" + row);
        layout.addView(button(primary, "row-" + row + "-primary", "primary"));
        layout.addView(button(pause, "row-" + row + "-pause", "pause"));
        layout.addView(button(resume, "row-" + row + "-resume", "resume"));
        layout.addView(button(cancel, "row-" + row + "-cancel", "cancel"));
        layout.addView(button(priority, "row-" + row + "-priority", "priority"));
        layout.addView(button(delete, "row-" + row + "-delete", "delete"));
        return layout;
    }

    private Button button(int id, String label, String action) {
        Button button = new Button(this);
        button.setId(id);
        button.setText(label);
        button.setContentDescription(label);
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);
        button.setSingleLine(true);
        button.setOnFocusChangeListener((view, focused) -> {
            if (focused) Log.i(TAG, "FOCUS focused=" + label + " launch=" + launchToken);
        });
        button.setOnClickListener(view -> execute(action));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, 76, 1f);
        params.setMargins(6, 6, 6, 6);
        button.setLayoutParams(params);
        return button;
    }

    private void wireFocusGraph() {
        linkHorizontal(WIFI, SCHEDULE, CONCURRENT);
        linkHorizontal(R1_PRIMARY, R1_PAUSE, R1_RESUME, R1_CANCEL, R1_PRIORITY, R1_DELETE);
        linkHorizontal(R2_PRIMARY, R2_PAUSE, R2_RESUME, R2_CANCEL, R2_PRIORITY, R2_DELETE);
        linkHorizontal(LIVE_1, LIVE_2);

        down(WIFI, R1_PRIMARY); down(SCHEDULE, R1_PAUSE); down(CONCURRENT, R1_CANCEL);
        down(R1_PRIMARY, R2_PRIMARY); down(R1_PAUSE, R2_PAUSE); down(R1_RESUME, R2_RESUME);
        down(R1_CANCEL, R2_CANCEL); down(R1_PRIORITY, R2_PRIORITY); down(R1_DELETE, R2_DELETE);
        down(R2_PRIMARY, LIVE_1); down(R2_PAUSE, LIVE_1); down(R2_RESUME, LIVE_1);
        down(R2_CANCEL, LIVE_2); down(R2_PRIORITY, LIVE_2); down(R2_DELETE, LIVE_2);

        up(R1_PRIMARY, WIFI); up(R1_PAUSE, SCHEDULE); up(R1_RESUME, SCHEDULE);
        up(R1_CANCEL, CONCURRENT); up(R1_PRIORITY, CONCURRENT); up(R1_DELETE, CONCURRENT);
        up(R2_PRIMARY, R1_PRIMARY); up(R2_PAUSE, R1_PAUSE); up(R2_RESUME, R1_RESUME);
        up(R2_CANCEL, R1_CANCEL); up(R2_PRIORITY, R1_PRIORITY); up(R2_DELETE, R1_DELETE);
        up(LIVE_1, R2_PRIMARY); up(LIVE_2, R2_CANCEL);
    }

    private void execute(String action) {
        int revision = revisions.computeIfAbsent(action, ignored -> new AtomicInteger()).incrementAndGet();
        if ("primary".equals(action)) {
            if (!"server_stopped".equals(fault) && !"repository_only".equals(fault)) originBytes += 4096;
            if (!"origin_only".equals(fault) && !"server_stopped".equals(fault)) repositoryBytes += 4096;
        }
        String marker = Process.myPid() + " " + launchToken + " " + action + " " + revision;
        Log.i(TAG, "MARKER " + marker);
        emitState(action);
    }

    private void emitState(String reason) {
        String server = "server_stopped".equals(fault) ? "stopped" : "running";
        String text = "qa-page:fixture qa-launch-token:" + launchToken
            + " qa-process:" + Process.myPid()
            + " fixture_server=" + server
            + " bytes_served=" + originBytes
            + " bytes_persisted=" + repositoryBytes
            + " reason=" + reason;
        evidence.setText(text);
        evidence.setContentDescription(text);
        Log.i(TAG, "STATE " + text);
    }

    private void linkHorizontal(int... ids) {
        for (int i = 0; i < ids.length; i++) {
            View view = findViewById(ids[i]);
            view.setNextFocusLeftId(ids[Math.max(0, i - 1)]);
            view.setNextFocusRightId(ids[Math.min(ids.length - 1, i + 1)]);
        }
    }
    private void down(int from, int to) { findViewById(from).setNextFocusDownId(to); }
    private void up(int from, int to) { findViewById(from).setNextFocusUpId(to); }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }
    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }
    private ViewGroup.LayoutParams matchWrap() {
        return new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
    private static String valueOr(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }
}
