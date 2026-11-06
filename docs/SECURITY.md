# Izin, Keamanan & Privasi — NAUY Personal Hub

## 1. Prinsip

- **Lokal dulu.** Tidak ada server, akun cloud, analitik, atau SDK iklan. Data lokasi dan vault tidak pernah dikirim
  ke pihak ketiga. Satu-satunya lalu lintas jaringan: tile peta OpenStreetMap (saat membuka peta) dan reverse
  geocoding Android (opsional, untuk label alamat).
- **Android Auto Backup dimatikan** (`allowBackup=false`, `fullBackupContent=false`, `data_extraction_rules.xml`
  mengecualikan semua domain) sehingga database tidak tersalin ke Google Drive atau saat transfer perangkat.
  Gunakan backup terenkripsi bawaan aplikasi.

## 2. Izin Android

| Izin | Kapan diminta | Jika ditolak |
|---|---|---|
| Lokasi presisi/perkiraan | Onboarding / saat menambah lokasi | Tracker tidak bisa bekerja; vault tetap bisa dipakai |
| Lokasi latar belakang | Setelah izin presisi, dengan penjelasan; Android 11+ membuka halaman sistem → pilih **Izinkan sepanjang waktu** | Deteksi hanya saat aplikasi terbuka (tombol perbarui manual) |
| Notifikasi (Android 13+) | Onboarding / Setelan → Notifikasi | Kejadian tetap tercatat di Notification Center di dalam aplikasi |
| Biometrik | Tidak perlu dialog (izin normal) | PIN tetap tersedia |
| Boot completed | Otomatis (izin normal) | — |

Status semua izin terlihat di **Setelan → Privacy & Security** lengkap dengan tombol "Perbaiki".

## 3. Arsitektur deteksi lokasi (hemat baterai)

1. Setiap lokasi aktif didaftarkan sebagai geofence OS (`GeofencingClient`, ENTER/EXIT, radius OS minimal 100 m,
   responsivitas 60 dtk). Tidak ada polling GPS terus-menerus dan **tidak ada foreground service**.
2. Saat OS melaporkan transisi, `GeofenceReceiver` memasukkan fix pemicu ke `PresenceEngine`.
3. Jika engine menunggu konfirmasi (dwell/exit delay), `ConfirmWorker` (WorkManager) dijadwalkan untuk mengambil
   satu fix baru setelah jeda tersebut.
4. `PeriodicCheckWorker` (default 30 mnt, bisa dimatikan) memeriksa izin/GPS, mendaftarkan ulang geofence, dan
   mengambil satu fix *balanced power* sebagai jaring pengaman.
5. `BootReceiver` (BOOT_COMPLETED, MY_PACKAGE_REPLACED) mendaftarkan ulang geofence karena Android menghapusnya
   saat restart. Geofence juga disinkron ulang tiap aplikasi dibuka dan bisa dipicu manual.

### Keterbatasan sistem
- Saat layanan lokasi dimatikan, Android menghapus semua geofence (error `GEOFENCE_NOT_AVAILABLE`); aplikasi
  memberi notifikasi "GPS tidak aktif" dan mendaftarkan ulang saat aktif kembali (pengecekan berkala / saat dibuka).
- Doze/App Standby dapat menunda callback beberapa menit. Waktu tetap akurat karena engine memakai waktu fix
  pertama yang melewati batas.
- Beberapa vendor (Xiaomi/MIUI, Oppo/ColorOS, Vivo, Huawei, Samsung) mematikan aplikasi latar belakang secara agresif.
  Kecualikan NAUY Hub dari optimasi baterai / izinkan *autostart*.
- Akurasi di dalam gedung bisa 30–100 m. Gunakan radius ≥ 100 m dan toleransi 30–50 m.
- Jika izin latar belakang dicabut, Android menghentikan proses aplikasi; peringatan muncul saat pengecekan berikutnya / saat dibuka.

## 4. Kriptografi vault

```
DEK  = 32 byte acak (SecureRandom)                 — kunci data vault, hanya di memori saat terbuka

pinWrappedDek = AES-GCM( PBKDF2-HMAC-SHA256(PIN, salt16, 120 000 iter),
                         AES-GCM( Keystore["hub_device_wrap"], DEK ) )
bioWrappedDek = AES-GCM( Keystore["hub_bio_wrap"], DEK )   — hanya bisa dipakai lewat BiometricPrompt(CryptoObject)

akun.payload  = IV(12) || AES-256-GCM( DEK, JSON{title, username, email, password, url, notes}, AAD )
```

- **Lapisan Keystore di bawah PIN** membuat PIN tidak bisa di-brute-force secara offline dari salinan database:
  kunci `hub_device_wrap` tidak bisa diekspor dari perangkat (hardware-backed bila tersedia).
- **Percobaan PIN online** dibatasi: 5 percobaan bebas, lalu lockout 30 dtk × 2ⁿ (maks. 1 jam).
- **Biometrik**: kunci `hub_bio_wrap` dibuat dengan `setUserAuthenticationRequired(true)`,
  `AUTH_BIOMETRIC_STRONG`, timeout 0 (setiap pemakaian butuh sidik jari) dan
  `setInvalidatedByBiometricEnrollment(true)`. Jika sidik jari baru didaftarkan, kunci batal → biometrik otomatis
  dinonaktifkan dan pengguna masuk dengan PIN.
- **Sesi**: DEK di-zero-kan saat terkunci (auto-lock inaktivitas, aplikasi ke background, tombol kunci).
  Selama pemilih file (backup/ekspor) terbuka, sesi ditahan agar tidak terkunci di tengah proses.
- **Tampilan**: password tersembunyi default, `FLAG_SECURE` di layar vault (bisa dimatikan), clipboard ditandai
  `EXTRA_IS_SENSITIVE` dan dihapus otomatis.
- **Log**: tidak ada PIN, password, atau kunci yang ditulis ke Logcat.
- Catatan jujur: String Kotlin/JVM tidak bisa di-wipe dari memori; plaintext akun hanya ada sementara saat vault terbuka.

## 5. Backup terenkripsi (`.nhb`)

```json
{ "format": "nauy-hub-backup", "version": 1, "kdf": "PBKDF2-HMAC-SHA256", "iterations": 210000,
  "salt": "<base64>", "cipher": "AES-256-GCM", "data": "<base64 IV||ciphertext||tag>" }
```

- Kunci diturunkan dari **password backup** (min. 8 karakter, terpisah dari PIN) agar bisa dipulihkan di HP baru.
- Isi: lokasi, riwayat aktivitas, catatan, dan (opsional, jika vault terbuka) akun vault. Tidak ada plaintext di file.
- Restore mengganti data lokal setelah konfirmasi; akun vault dienkripsi ulang dengan kunci perangkat baru.

## 6. Data uji vs data produksi

- Seeder data dummy (`DevTools`) hanya ada di source set **debug**; build rilis berisi stub kosong.
- Semua data dari simulator/seeder diberi sumber `SIMULATED` (label "Simulasi") dan dapat dihapus sekaligus
  tanpa menyentuh data asli. Build debug memakai application id terpisah (`.debug`).
