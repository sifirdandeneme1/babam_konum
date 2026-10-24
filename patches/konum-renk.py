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
