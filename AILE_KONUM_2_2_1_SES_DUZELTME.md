# Aile Konum 2.2.1 — Çaldır sesi onarımı

Bu dal mevcut başarılı v2.2 dalının kopyasından üretilmiştir. GPS, Firebase, şifreleme, menüler ve harita dosyaları değiştirilmedi.

Çaldırma, Android Ringtone API ile telefondaki varsayılan arama zilini oynatmayı dener; başarısız olursa v2.1.1'deki sentetik uyarı sesini yeniden kullanır. Titreşim ses çalma başarısından bağımsızdır. Arama sırasında durdurma ve uzaktan durdurma korunur. Telefondan işitilir ses çıkması üretici kısıtlamalarına göre ayrıca gerçek cihazda doğrulanmalıdır.

GitHub Actions debug ve unsigned release derler. Mevcut yüklü v2.2'nin üzerine kurulmak için unsigned release, **v2.2'nin orijinal özel imza anahtarıyla** imzalanmalıdır. Anahtarı GitHub'a koymayın.
