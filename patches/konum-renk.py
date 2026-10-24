from pathlib import Path

p = Path('aile_konum_v2/app/src/main/java/com/omer/ailekonum2/MainActivity.java')
s = p.read_text(encoding='utf-8')

def replace_once(before, after):
    global s
    if s.count(before) != 1:
        raise RuntimeError('Beklenen kaynak yapısı değişmiş: ' + before[:60])
    s = s.replace(before, after)

replace_once(
    'private TextView locationText, statusText, diagnosticText;',
    'private TextView locationText, statusText, diagnosticText, freshnessText;'
)
replace_once(
    'detach(); map=null; locationText=null; statusText=null; diagnosticText=null; remoteStatus=null;',
    'detach(); map=null; locationText=null; statusText=null; diagnosticText=null; freshnessText=null; remoteStatus=null;'
)
replace_once(
    '''        statusText=text("Bağlantı kuruluyor…",17);column.addView(statusText);
        locationText=text("Gerçek konum bekleniyor…",16);column.addView(locationText);''',
    '''        freshnessText=text("● KONUM BEKLENİYOR",19);
        freshnessText.setTextColor(0xffb45309);
        freshnessText.setTypeface(null,android.graphics.Typeface.BOLD);
        column.addView(freshnessText);
        statusText=text("Bağlantı kuruluyor…",16);column.addView(statusText);
        locationText=text("Gerçek konum bekleniyor…",16);column.addView(locationText);'''
)
replace_once(
    r'''            statusText.setText((fresh?"● KONUM GÜNCEL":"⚠ KONUM ESKİ / BEKLENİYOR")+
                "\nGönderen bağlantısı: "+(senderOnline?"BAĞLI":"BAĞLI DEĞİL")+''',
    '''            if(freshnessText!=null){
                freshnessText.setText(fresh?"● KONUM GÜNCEL":lastGpsAt>0?"● KONUM ESKİ":"● KONUM BEKLENİYOR");
                freshnessText.setTextColor(fresh?0xff16803c:lastGpsAt>0?0xffb91c1c:0xffb45309);
            }
            statusText.setText("Gönderen bağlantısı: "+(senderOnline?"BAĞLI":"BAĞLI DEĞİL")+'''
)
p.write_text(s, encoding='utf-8')
print('Yeşil/güncel, kırmızı/eski ve turuncu/bekleniyor durumu hazır.')


# 2.1.1 fix: Android 6+ notification-policy access must be declared in
# AndroidManifest.xml for the app to appear in Do Not Disturb access settings.
manifest = Path('aile_konum_v2/app/src/main/AndroidManifest.xml')
ms = manifest.read_text(encoding='utf-8')
old_perm = '<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />'
new_perm = old_perm + '\n    <uses-permission android:name="android.permission.ACCESS_NOTIFICATION_POLICY" />'
if ms.count(old_perm) != 1 or "android.permission.ACCESS_NOTIFICATION_POLICY" in ms:
    raise RuntimeError('Ses izin manifesti beklenmedik; kaynakları kontrol edin')
manifest.write_text(ms.replace(old_perm, new_perm), encoding='utf-8')

gradle = Path('aile_konum_v2/app/build.gradle')
gs = gradle.read_text(encoding='utf-8')
if gs.count('versionCode 2') != 1 or gs.count("versionName '2.1.0'") != 1:
    raise RuntimeError('Versiyon sayıları beklenen biçimde değil')
gs = gs.replace('versionCode 2', 'versionCode 3').replace("versionName '2.1.0'", "versionName '2.1.1'")
gradle.write_text(gs, encoding='utf-8')
print('v2.1.1: Rahatsız Etmeyin erişimi için manifest izni eklendi; sürüm yükseltildi.')
