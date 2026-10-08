# Aile Konum v3

Aile içinde açık rıza ile kullanılan iki telefonlu konum ve uzaktan konum kontrolü uygulaması.

## Özellikler

- Aynı APK iki telefona kurulur.
- Babanın telefonu: **Konum Gönderen**
- Kullanıcının telefonu: **Takip Eden**
- 4 haneli kısa süreli eşleştirme PIN'i.
- Eşleştirme sonrası 256-bit rastgele anahtar ile AES-GCM şifreli haberleşme.
- Uzaktan:
  - Telefonun sistem Konum hizmetini açma
  - Telefonun sistem Konum hizmetini kapatma
  - Canlı takibi başlatma
  - Canlı takibi durdurma
  - Pil / konum / ADB yetki durumunu görme
- Canlı takipte yaklaşık 3-5 saniyelik GPS güncellemeleri.
- Haritada işaretçi yeni konuma yumuşak hareket eder.

## Özel ADB yetkisi

Babanın telefonuna APK kurulduktan sonra bir kez:

```bat
adb shell pm grant com.omer.ailekonum android.permission.WRITE_SECURE_SETTINGS
```

Kontrol:

```bat
adb shell dumpsys package com.omer.ailekonum | findstr /I WRITE_SECURE_SETTINGS
```

OPPO/ColorOS cihazlarında Geliştirici seçeneklerinde **Permission monitoring / İzin izleme** engelinin kapatılması gerekebilir.

## İlk kurulum

1. APK'yı iki telefona kur.
2. Babanın telefonunda **Konum Gönderen** seç.
3. Konum iznini ver; Android 11'de uygulama ayarından **Her zaman izin ver** seçeneğini aç.
4. Pil optimizasyonunu kaldır.
5. Yukarıdaki ADB komutuyla Secure Settings yetkisini bir kez ver.
6. Kendi telefonunda **Takip Eden** seç.
7. Babanın telefonunda görünen 4 haneli kodu gir.
8. Kumanda ekranından önce **Durumu Yenile**, sonra Konumu Aç/Kapat ve canlı takip komutlarını test et.

## Notlar

- Uzaktan komut ve konum taşıması için ntfy.sh kullanılır. Konum ve komut içeriği uygulama içinde AES-GCM ile şifrelenir.
- 4 haneli PIN kalıcı anahtar değildir; yalnızca kısa süreli ilk eşleştirme için kullanılır.
- Arka planda uzaktan komut alabilmek için babanın telefonda düşük öncelikli bir foreground-service bildirimi görünür.
- Telefon internetsizse uzaktan komut ulaşmaz.
- Sistem Konumunu aç/kapatma davranışı ROM/OEM'e göre değişebilir. OPPO A54 Android 11 üzerinde ADB özel yetkisinden sonra cihazda test edilmelidir.
