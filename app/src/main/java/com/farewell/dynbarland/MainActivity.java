package com.farewell.dynbarland;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/**
 * Layar pengaturan sederhana. Nilai disimpan di Settings.Global (ditulis lewat root) sehingga bisa
 * dibaca hook di SystemUI dan juga bisa diubah dari WebUI modul pendamping.
 */
public class MainActivity extends Activity {

    private TextView status;
    private Switch swEnable;
    private Switch swAggressive;
    private boolean updating = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Dynamic Bar Landscape");
        title.setTextSize(22);
        root.addView(title);

        TextView warn = new TextView(this);
        warn.setText("Disclaimer: saat diaktifkan, Dynamic Bar statis tetap tampil ketika layar landscape. "
                + "Bar ini dapat menutupi elemen di belakangnya (misalnya judul video, tombol, atau "
                + "bagian game). Gunakan saklar di bawah untuk menonaktifkannya kapan saja.");
        warn.setTextColor(Color.parseColor("#B26A00"));
        warn.setPadding(0, pad, 0, pad);
        root.addView(warn);

        swEnable = new Switch(this);
        swEnable.setText("Pertahankan Dynamic Bar statis saat landscape");
        root.addView(swEnable);

        swAggressive = new Switch(this);
        swAggressive.setText("Mode agresif (blokir pelepasan window; eksperimental)");
        root.addView(swAggressive);

        TextView hint = new TextView(this);
        hint.setText("Mode agresif lebih kuat tetapi berisiko. Jika SystemUI bermasalah, matikan saklar ini.");
        hint.setPadding(0, pad / 2, 0, pad);
        root.addView(hint);

        status = new TextView(this);
        root.addView(status);

        TextView help = new TextView(this);
        help.setText("\nLangkah: aktifkan modul di LSPosed dengan cakupan SystemUI, lalu mulai ulang "
                + "SystemUI (killall com.android.systemui) atau reboot. Log hook ada di tab Log LSPosed "
                + "dengan awalan [DynBarLand].");
        root.addView(help);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);

        refresh();

        swEnable.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                if (!updating) {
                    write(MainHook.KEY_ENABLE, checked ? 1 : 0);
                }
            }
        });
        swAggressive.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                if (!updating) {
                    write(MainHook.KEY_AGGRESSIVE, checked ? 1 : 0);
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        updating = true;
        swEnable.setChecked(Settings.Global.getInt(getContentResolver(), MainHook.KEY_ENABLE, 1) == 1);
        swAggressive.setChecked(Settings.Global.getInt(getContentResolver(), MainHook.KEY_AGGRESSIVE, 0) == 1);
        updating = false;
    }

    private void write(final String key, final int value) {
        status.setText("Menyimpan...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean ok;
                try {
                    Process p = Runtime.getRuntime().exec(
                            new String[]{"su", "-c", "settings put global " + key + " " + value});
                    ok = p.waitFor() == 0;
                } catch (Exception e) {
                    ok = false;
                }
                final boolean result = ok;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        status.setText(result ? "Tersimpan. Berlaku langsung pada hook."
                                : "Gagal menyimpan (izinkan akses root untuk aplikasi ini).");
                        refresh();
                    }
                });
            }
        }).start();
    }
}
