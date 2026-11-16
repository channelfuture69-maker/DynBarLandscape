# Dynamic Bar Landscape (modul LSPosed, v0.1 eksploratif)

Mencoba mempertahankan **Dynamic Bar statis** (view `island_resident_bar` milik SystemUI Transsion)
agar tetap tampil saat layar landscape.

> **Status jujur:** perilaku SystemUI saat landscape belum diketahui pasti (kodenya obfuskasi).
> Versi ini memakai hook generik di level View/Window dan mencatat log mendetail ke LSPosed.
> Jika bar belum bertahan, kirim log `[DynBarLand]` agar hook bisa dipertajam.

## Cara build (pilih salah satu)
1. **GitHub Actions** (tanpa instalasi apa pun): unggah folder ini ke repositori GitHub, buka tab
   *Actions* -> *Build APK* -> unduh artifact `DynBarLandscape-debug`.
2. **Android Studio**: buka folder ini, lalu `Build > Build APK(s)` (modul `app`).
3. **Terminal**: `gradle :app:assembleDebug` (Gradle 8.7+, JDK 17, Android SDK 34).

APK hasil build ada di `app/build/outputs/apk/debug/`.

## Pemakaian
1. Pasang APK, aktifkan di **LSPosed**, centang cakupan **Sistem UI (com.android.systemui)**.
2. Mulai ulang SystemUI: `su -c killall com.android.systemui` (atau reboot).
3. Buka aplikasinya untuk saklar dan disclaimer, atau pasang modul pendamping
   `DynBarLandscape-WebUI.zip` (KernelSU/MMRL WebUI) untuk tombol di WebUI.
4. Putar layar ke landscape. Lihat tab **Log** LSPosed, cari awalan `[DynBarLand]`.

## Pengaturan (Settings.Global)
| Kunci | Arti | Bawaan |
|---|---|---|
| `dynbar_land_static` | 1 = pertahankan bar di landscape, 0 = nonaktif | 1 |
| `dynbar_land_aggressive` | 1 = blokir pelepasan window bar saat landscape (eksperimental) | 0 |

Ubah manual: `su -c settings put global dynbar_land_static 0`

## Disclaimer
Bar yang tetap tampil di landscape dapat **menutupi elemen di belakangnya** (judul video, tombol,
bagian game). Mode agresif berisiko membuat SystemUI tidak stabil; matikan jika ada masalah.

## Yang dilakukan hook
- `View.setVisibility`: jika yang disembunyikan adalah `island_resident_bar` saat landscape, paksa VISIBLE.
- `WindowManagerGlobal.removeView`: mencatat (dan pada mode agresif memblokir) pelepasan window bar.
- `WindowManagerGlobal.addView`: mencatat parameter window bar (gravity, flag, ukuran) untuk
  menentukan cara memposisikannya mengikuti kamera di versi berikutnya.
