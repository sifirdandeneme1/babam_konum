# Aile Konum 2.2 — Arayüz / Zil / İzinler

## İlkeler
- `main` ve çalışan `feature/aile-konum2-ses-renk` dalı değiştirilmez.
- Firebase kimlik doğrulaması, şifreleme, konum servisi, yeniden başlatma mantığı ve mevcut veritabanı yolları korunur.
- Telefonun seçili zil melodisi ve titreşim, uzaktan çaldır komutuyla etkinleşir; yeni `STOP_RING` komutu ve gönderici ekrandaki yerel durdurma işlevi eklenir.
- Arama sırasında durdurma için Android ses odağı değişikliği ve AudioManager mode izlenir. Bazı üretici ROM'larında garanti edilemez.
- Yeni konum geldiğinde harita elle incelenirken otomatik merkeze dönmez; ayrı Konuma Git ve tam ekran vardır.
- Gönderici rolde İzinler ve Kurulum sekmesinden konum, arka plan konumu, telefon konumu, bildirim, pil ve DND ayarlarına yönlendirilir. İzinler kendiliğinden verilmez.
- Firebase veritabanı kurallarında `STOP_RING` bir şifreli kontrol dizesi olarak eski yol üzerinden işlenir; yeni kurallar gerekmemektedir.
- Uzaktan müdahalesiz APK yükleme ve Direct Boot bu sürümde yoktur: eski canlı konum takibini riske atmamak için ertelenmiştir.

## Kurulum
`aile-konum2-source.zip` temiz kaynak arşivi açılır; `patches/aile-konum2-v22-overlay.b64` ZIP olarak çözümlenerek dosyaların üzerine yazılır. Ardından Gradle `assembleDebug assembleRelease` çalıştırır. Eski sürüm üzerine güncelleme yapmak için imzasız release APK, **eski sürümün orijinal özel anahtarı** ile cihaz dışı imzalanır; özel anahtar GitHub'a kesinlikle konmaz.

## Test planı
1. Eski v2.1.1 güncelleme şeklinde, uygulama verisi kaybolmadan kurulur.
2. Eşinin telefonunda gönderici oturumu korunur; konum, pil ve heartbeat güncellenir.
3. Sessizde ve ekran kilitliyken uzaktan Sesi Aç, Melodiyle Çaldır, Durdur test edilir.
4. Çalarken gerçek arama yapılır; telefonun normal arama ekranına geçip sesi susturduğu doğrulanır. Farklı Android/OPPO davranışları için kesin garanti verilmez.
5. İzinler sekmesindeki ayarlar manuel onayla yapılır, OPPO otomatik başlatma otomatik ölçülmez.
6. Tam ekran harita, kaydırma, yakınlaştırma ve Konuma Git gerçek telefonla kontrol edilir.

Konum verisi hassastır; yalnızca rıza ve aile güvenliği amacıyla kullanılmalıdır. Sürüm herhangi bir gizleme, uzaktan GPS anahtarı açma ya da kullanıcı onaysız APK kurma işlevi eklemez.