# HyperKit — HyperOS 4 için Good Lock alternatifi

Root gerektirmeyen, modüler bir özelleştirme uygulaması. Samsung Good Lock'taki popüler modüllerin
HyperOS'ta (Xiaomi / Redmi / POCO) üçüncü taraf bir uygulamanın yapabildiği kadarını sunar.

İlk açılışta **mod seçimi** yapılır (sonradan ana ekrandan değiştirilebilir):

- **Standart mod** – root gerekmez, her HyperOS telefonda çalışır.
- **Root modu** – Magisk / KernelSU / APatch ile; tüm izinler otomatik verilir (ADB gerekmez) ve ek araçlar açılır.

### Make up – görünüm
| Modül | Good Lock karşılığı | Ne yapar |
|---|---|---|
| **Kilit Ekranı** | LockStar | Sistem kilit ekranının üstünde kendi kilit ekranınız: 6 saat stili (ince, kalın, üst üste, dış hatlı, analog, minimal), renk/boyut/konum, fotoğraf / AMOLED / gradyan arka plan, karartma, mesaj, bildirim kartları veya simgeler (metin gizlenebilir), müzik kontrolleri, pil/şarj bilgisi, sol-sağ kısayollar (kamera, fener, uygulama), çift dokunarak kapatma. Yukarı kaydırınca HyperOS PIN/parmak izi ister – güvenlik değişmez. Arama/alarm gelince kendini kapatır. |
| **Saat Yüzü** | ClockFace / AOD | Şarjdayken çalışan, ekran yanığına karşı kayan, loş saat ekran koruyucusu (DreamService). |
| **Kenar Aydınlatma** | Edge Lighting+ | Bildirim gelince ekran kenarında dönen parlama; uygulama rengi / mavi / gökkuşağı, kalınlık, süre. |
| **Wonderland** | Wonderland | Kendi fotoğrafınızdan, telefon eğildikçe kayan 3B derinlikli canlı duvar kağıdı. |
| **Hızlı Kutucuklar** | QuickStar | Kafein, Kilitle, Ekran görüntüsü, Güç menüsü, Animasyonlar, Medya tuşları. |
| **Sistem Ayarlayıcı** | QuickStar / Theme Park / NavStar | Animasyon hızı, durum çubuğu simgeleri, hareket çizgisini gizleme, Material You sistem rengi, ekran zaman aşımı, yazı boyutu, hizmeti açık tutma. |

### Life up – kullanım
| Modül | Good Lock karşılığı | Ne yapar |
|---|---|---|
| **Kenar Hareketleri** | One Hand Operation+ | Sol/sağ tutamak, 4 hareket × 18 eylem (root modunda “uygulamayı kapat” dahil). |
| **Ses Asistanı** | Sound Assistant | Ses tuşları medyayı kontrol eder, uzun basarak parça geçme, tüm ses düzeyleri. |
| **Bildirim Geçmişi** | NotiStar | Silinenler dahil tüm bildirimler cihazda saklanır; arama ve uygulama filtresi. |
| **Uygulama Profilleri** | Display Assistant + uygulama sesi | Uygulamaya özel döndürme, parlaklık, medya sesi, ekranı açık tutma; çıkınca geri alınır. |
| **Gizli Ayarlar** | RegiStar (kısmen) | 15 gizli Android/HyperOS ekranı, ana ekran kısayolu. |

### Root
| Modül | Good Lock karşılığı | Ne yapar |
|---|---|---|
| **Root Araçları** | Battery Guardian, Display Assistant | Yenileme hızı kilidi, ekran yoğunluğu (15 sn otomatik geri alma), şarj sınırı, hareket çizgisi + klavye boşluğu kaldırma, Sistem Arayüzü / kurtarma / bootloader yeniden başlatma. |
| **Uygulama Yöneticisi** | MultiStar | Uygulama dondur/çöz, zorla durdur, önbellek temizle, yüzen pencerede aç. |

### Uyarlanamayanlar
- **Keys Cafe** (klavye) ayrı bir klavye (IME) uygulaması gerektirir.
- **Home Up** (başlatıcı): HyperOS başlatıcısı dışarıdan özelleştirilemez.
- **Edge Touch, NiceCatch, Camera Assistant**: sistem imzası gerektirir.
- **Kapalı ekranda kenar aydınlatma**: Android buna izin vermez.

## Kurulum
1. APK'yı kurun, uygulamayı açın.
2. **Ayarlar → Ek ayarlar → Erişilebilirlik → İndirilen uygulamalar → HyperKit** hizmetini açın
   (Kenar Hareketleri, Ses Asistanı ve Kilitle/Ekran görüntüsü/Güç/Kafein kutucukları için).
3. Kilit ekranı, kenar aydınlatma ve bildirim geçmişi için **Bildirim erişimi** verin (uygulama yönlendirir).
4. HyperOS hizmeti öldürmesin diye: **Otomatik başlatma** açık, **Pil tasarrufu → Kısıtlama yok**,
   Son uygulamalarda HyperKit'i kilitleyin.
5. Standart modda Sistem Ayarlayıcı için bir kez (USB hata ayıklama açık):
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
