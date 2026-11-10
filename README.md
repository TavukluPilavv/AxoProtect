# AxoProtect

Paper 1.21.1+ icin dunyaya bagli bolge koruma eklentisi.

## Komut

`/dunya <dunya_adi> normal <blokkırma|blokkoyma> <aç|kapa>`

Ornekler:

- `/dunya flatroom normal blokkırma kapa`
- `/dunya flatroom normal blokkoyma kapa`
- `/dunya flatroom normal blokkırma aç`
- `/dunya flatroom normal blokkoyma aç`

Komut girildiginde oyuncuya secim baltasi verilir. Sol tik ilk noktayi, sag tik ikinci noktayi secer. Iki nokta da secilince bolge otomatik kaydedilir ve secim baltasi envanterden kaldirilir. Bolgeler ve kurallar yeniden baslatmalardan sonra saklanir.

Dunyalar Bukkit tarafindan yuklenmis olmalidir. Multiverse-Core dunyalari da Bukkit'e yuklendikleri icin isimleriyle kullanilir.

## Izinler

- `axoprotect.admin` - yonetim komutu
- `axoprotect.bypass` - tum korumalari asar
- `axoprotect.blokkırma.aç`, `axoprotect.blokkırma.kapa`
- `axoprotect.blokkoyma.aç`, `axoprotect.blokkoyma.kapa`
- `axoprotect.wand` - secim baltasi

Varsayilan olarak OP'lere verilir. LuckPerms ile ornek:

```
/lp group moderator permission set axoprotect.admin true
/lp group builder permission set axoprotect.bypass true
```

## TrapPlugin notu

TrapPlugin 1.0.0 JAR'indaki config yapisi `traps` altinda saklaniyor. AxoProtect varsayilan olarak TrapPlugin entegrasyonunu otomatik varsaymaz; TrapPlugin'in kurulu oldugu dunyayi `config.yml` icindeki `integrations.trapplugin-worlds` listesine ekleyin. Bu dunyalarda AxoProtect koruma eventlerini atlar ve TrapPlugin'in kendi chunk/izin kontrollerine dokunmaz.

## Mesajlar

AxoProtect kendi mesajlarini kontrol eder. EssentialsX, LuckPerms veya hosting panelinin baska eklentiler/Proxy tarafindan oyuncuya gonderdigi tum bildirimleri genel olarak engellemek Bukkit API ile garanti edilemez. Bunlar ilgili eklenti veya hosting/proxy ayarindan kapatilmalidir.

## Derleme

GitHub Actions her push ve manuel calistirmada Java 21 + Maven ile `AxoProtect.jar` olusturur. Actions > Build AxoProtect > Artifacts bolumunden indirebilirsiniz.
