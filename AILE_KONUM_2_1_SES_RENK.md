# Aile Konum 2.1 — Ses kontrolü ve renkli konum durumu

Bu dal yalnızca \`release/aile-konum2-firebase\` temelinde yeni işlevler ekler. Çalışan \`main\` ve Firebase v2 dalları değiştirilmez.

- **Sesi Aç:** Göndericinin zil modunu normale çevirmeyi ve zil sesini yükseltmeyi dener.
- **Telefonu Çaldır:** Göndericide yaklaşık 60 saniye alarm üretmeye çalışır.
- **Ses Durumunu Yenile:** Göndericinin ses kontrolü yanıtını gösterir.
- **Renkli konum:** Güncel veride **yeşil**, eski veride **kırmızı**, henüz konum alınmadığında **turuncu** durum yazısı.
- Mevcut canlı konum, Firebase ve eşleştirme korunur. Sistem GPS düğmesini uzaktan değiştirme bu sürümde yoktur.

## Derleme

Bu dalın GitHub Actions akışı önce aynı v2.0 kaynak arşivini açar, \`patches/aile-konum2-ses-v21.zip.b64\` paketindeki kaynak değişikliklerini üzerine uygular, ardından \`patches/konum-renk.py\` ile yalnızca renkli durum göstergesini ekler. Arşiv SHA-256 ile doğrulanır.

Actions iki çıktı verir:
1. **Debug APK:** Yalnızca geliştirme içindir; mevcut imzalı v2.0 üzerine kurulmaz.
2. **İmzasız release APK:** Kullanıcıdaki **eski v2.0 ile aynı özel imza anahtarıyla** cihaz dışında imzalanmalıdır. **Özel anahtar GitHub'a yüklenmemelidir.**

## Firebase Rules

Ses komutları için \`aile_konum_v2/database.rules.json\` içindeki yeni kurallar Firebase Console'da yayımlanmalıdır. Ortak takip hesabı \`families/babam/control/latest\` yoluna yazabilir; gönderen okur. Mevcut canlı konum yetkileri değişmez.

## Güvenlik ve test

Babanın OPPO telefonuna kurmadan önce **eşinin telefonunda** sessiz/titreşim, ekran kilitli, mobil veri/Wi-Fi değişimi ve telefon yeniden başlatma testleri yapılmalıdır. Telefon çevrimdışıysa, Android uygulamayı zorla durdurduysa veya üretici arka planı engelliyorsa uzaktan ses komutu çalışmaz. Komutun kabul edildiği bildirimi, alarm sesinin hoparlörden gerçekten duyulduğunun garantisi değildir.
