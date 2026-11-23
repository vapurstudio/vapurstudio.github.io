# HyperKit — HyperOS 4 için Good Lock alternatifi

Root gerektirmeyen, modüler bir özelleştirme uygulaması. Samsung Good Lock'taki popüler modüllerin
HyperOS'ta (Xiaomi / Redmi / POCO) üçüncü taraf bir uygulamanın yapabildiği kadarını sunar.

| Modül | Good Lock karşılığı | Ne yapar |
|---|---|---|
| **Kenar Hareketleri** | One Hand Operation+ | Sol/sağ kenarda ayarlanabilir tutamak. 4 hareket (içeri, çapraz yukarı, çapraz aşağı, kaydır-tut) × 17 eylem: geri, ana ekran, son uygulamalar, önceki uygulama, bildirimler, Kontrol Merkezi, kilitle, ekran görüntüsü, güç menüsü, bölünmüş ekran, medya, ses paneli, fener, kafein. Konum / yükseklik / genişlik / görünürlük / mesafe ayarı, titreşim. |
| **Ses Asistanı** | Sound Assistant | Ses tuşları her zaman medyayı kontrol eder; müzik çalarken uzun basarak parça geçme; tüm ses düzeyleri tek ekranda. |
| **Hızlı Kutucuklar** | QuickStar | Kontrol Merkezi kutucukları: Kafein (ekranı açık tut), Kilitle, Ekran görüntüsü, Güç menüsü, Animasyonlar aç/kapa, Medya tuşları. Uygulamadan tek dokunuşla eklenir. |
| **Gizli Ayarlar** | — | Kullanım istatistikleri, bildirim günlüğü, ağ bilgisi (*#*#4636#*#*), otomatik başlatma, arka plan kısıtları, HyperOS izin yöneticisi, CIT donanım testi vb. Uzun basarak ana ekrana kısayol. |
| **Sistem Ayarlayıcı** | Home Up / NiceShot ayarları | Animasyon ölçekleri, durum çubuğu simgesi gizleme (icon_blacklist), ekran zaman aşımı, yazı boyutu. |

## Kurulum
1. APK'yı kurun, uygulamayı açın.
2. **Ayarlar → Ek ayarlar → Erişilebilirlik → İndirilen uygulamalar → HyperKit** hizmetini açın
   (Kenar Hareketleri, Ses Asistanı ve Kilitle/Ekran görüntüsü/Güç/Kafein kutucukları için).
3. HyperOS hizmeti öldürmesin diye: **Otomatik başlatma** açık, **Pil tasarrufu → Kısıtlama yok**,
   Son uygulamalarda HyperKit'i kilitleyin.
4. Sistem Ayarlayıcı için bir kez (USB hata ayıklama açık):
   ```
   adb shell pm grant com.vapurstudio.hyperkit android.permission.WRITE_SECURE_SETTINGS
   ```

## Bilinen sınırlar
- Tam ekran hareketleri açıkken sistem geri hareketi kenarlarda önceliklidir; Android, uygulamaların
  kenar başına hareket hariç tutma alanını ~200 dp ile sınırlar. Tutamakları kısa tutun.
- Ses tuşu özellikleri yalnızca ekran açıkken çalışır (erişilebilirlik API sınırı). Aramalar etkilenmez.
- `icon_blacklist` HyperOS'ta sürüme göre yalnızca bazı simgeler için uygulanır.
- Gizli ekranların bir kısmı bölge/sürüme bağlıdır; bulunamayanlar listede soluk görünür.

## Derleme
```
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease      # imzalamak için signingConfig ekleyin
```
minSdk 31, targetSdk 35, harici bağımlılık yok (yalnızca Android framework + Kotlin).

## Gizlilik
Ağ izni yoktur; hiçbir veri cihazdan çıkmaz. Erişilebilirlik hizmeti ekran içeriğini okumaz
(`canRetrieveWindowContent=false`), yalnızca hareket tutamaklarını çizer ve ses tuşlarını dinler.
