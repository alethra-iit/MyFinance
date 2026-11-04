# MyFinance

Aplikasi Android pencatat keuangan pribadi (Kotlin, Jetpack Compose, Material 3, Room, MVVM, offline).

## Build APK lewat GitHub Actions
1. Buat repository baru di GitHub, lalu upload seluruh isi folder ini (termasuk folder `.github`).
2. Push ke branch `main` (atau jalankan manual: tab **Actions → Build APK MyFinance → Run workflow**).
3. Setelah selesai, buka run tersebut → bagian **Artifacts** → unduh `MyFinance-APK` (berisi `MyFinance-debug.apk`).
4. Untuk membuat Release otomatis, push tag, misalnya `git tag v1.0.0 && git push origin v1.0.0`.

## Build lokal
Buka folder ini di Android Studio (Koala atau lebih baru, JDK 17) lalu Run, atau `gradle assembleDebug`.

## Struktur
- `data/` Room: entity, DAO, database, repository
- `ui/` ViewModel, navigasi, komponen, tema
- `ui/screens/` Beranda, Tambah Transaksi, Riwayat, Catatan Belanja, Laporan, Profil
- `util/` format Rupiah & tanggal, backup JSON, ekspor XLSX/PDF, pengingat harian
