# TunGate 🚪

**TunGate — a WireGuard & AmneziaWG client for macOS**, a functional desktop port of
[wgtunnel/android](https://github.com/wgtunnel/android) (the wgslc WireGuard Android client),
built with Kotlin + Compose Multiplatform.

**تون‌گیت — کلاینت WireGuard و AmneziaWG برای مک؛ پورت کارکردی اپ اندروید
wgtunnel با Kotlin و Compose Multiplatform.**

## English

### What it does
- Import WireGuard / **AmneziaWG** configs from `.conf` file, pasted text, or a **QR image**
- Tunnel list with add/**edit (full form: keys, endpoint, AllowedIPs, DNS, MTU,
  PersistentKeepalive and all AmneziaWG anti-DPI params Jc/Jmin/Jmax/S1/S2/H1/H2)**/delete
- Endpoint **ping latency** shown next to each tunnel (like the Android client)
- Stale system **HTTP/HTTPS proxies are disabled while connected** and restored on
  disconnect — otherwise a leftover local proxy silently bypasses the tunnel (browser
  still filtered while other apps work)
- One-toggle connect: a userspace **amneziawg-go** engine creates a utun, applies the
  interface addresses, installs AllowedIPs routes (wg-quick style: endpoint bypass +
  half-routes for `0.0.0.0/0`), and sets DNS from the config
- Full-tunnel or split-tunnel exactly as your `.conf` defines it
- In-app logs; automatic teardown if the app is killed (heartbeat in the privileged helper)

### Why userspace (and not a system VPN extension)
macOS NetworkExtension requires a paid Apple Developer ID. TunGate instead uses a
**LaunchDaemon helper + the MIT-licensed amneziawg-go engine**, so it works from a local
build. Administrator access is asked **once** (UAC-style prompt); after that every
connect is silent, including after app updates/reinstalls.

### Install
1. Download `TunGate-1.1.0.dmg` from [Releases](https://github.com/HELBOYCODER/TunGate-mac/releases).
2. Drag **TunGate** to Applications.
3. Unsigned local build — first launch: right-click → **Open**, or
   `xattr -dr com.apple.quarantine /Applications/TunGate.app`

### Build from source
```bash
# 1) tunnel engine (Go 1.25+, MIT amneziawg-go)
cd tun-go && go build -o ../vendor/tungatun .
# 2) app (JDK 17+, Gradle 8.10)
gradle packageDistributionForCurrentOS
# output: build/compose/binaries/main/dmg/TunGate-<version>.dmg
```
Pushing a `v*` tag triggers the GitHub Actions workflow that builds the Go engine and
the DMG and attaches them to the release.

### Data & logs
- Tunnels: `~/Library/Application Support/TunGate/tunnels.json`
- Helper/engine logs: `~/Library/Application Support/TunGate/helper/`
- Remove the helper: quit the app, or
  `sudo launchctl bootout system/com.tungate.helper && sudo rm /Library/LaunchDaemons/com.tungate.helper.plist`

## فارسی

### کارکردها
- ایمپورت کانفیگ WireGuard / **AmneziaWG** از فایل `.conf`، متن، یا **تصویر QR**
- لیست تونل‌ها با افزودن/**ویرایش کامل (کلیدها، endpoint، AllowedIPs، DNS، MTU،
  PersistentKeepalive و همه پارامترهای ضد-DPI AmneziaWG یعنی Jc/Jmin/Jmax/S1/S2/H1/H2)**/حذف
- نمایش **پینگ endpoint** کنار هر تونل (مثل نسخه اندروید)
- **پروکسی‌های HTTP/HTTPS باقی‌مانده از اپ‌های دیگر هنگام اتصال خاموش** و هنگام قطع
  بازگردانی می‌شوند؛ در غیر این صورت مرورگر بی‌سروصدا از تونل رد نمی‌شود
- تم رنگی اپ مطابق آیکون (فیروزه‌ای/سرمه‌ای/کهربایی)
- اتصال با یک دکمه: موتور کاربری **amneziawg-go** یک utun می‌سازد، آدرس‌ها را ست می‌کند،
  مسیرهای AllowedIPs را (سبک wg-quick: دورزدن endpoint + مسیرهای نصفه برای `0.0.0.0/0`)
  اضافه و DNS کانفیگ را اعمال می‌کند
- تونل کامل یا split، دقیقاً همان‌که کانفیگ شما تعریف کرده
- لاگ داخلی؛ اگر اپ کشته شود، تونل حداکثر تا ۳ دقیقه خودکار جمع می‌شود

### چرا موتور کاربری؟
NetworkExtension مک به Developer ID پولی نیاز دارد. تون‌گیت به‌جای آن از
**هلپر LaunchDaemon + موتور MIT امپه amneziawg-go** استفاده می‌کند و با بیلد محلی کار
می‌کند. دسترسی ادمین **فقط یک بار** خواسته می‌شود؛ بعد از آن هر اتصال (حتی بعد از
آپدیت/نصب مجدد) بی‌صداست.

### نصب
1. `TunGate-1.1.0.dmg` را از Releases بگیرید
2. **TunGate** را به Applications بکشید
3. بار اول: راست‌کلیک → Open (یا `xattr -dr com.apple.quarantine /Applications/TunGate.app`)

## License
MIT — engine based on [AmneziaWG/amneziawg-go](https://github.com/amnezia-vpn/amneziawg-go) (MIT);
inspired by [wgtunnel/android](https://github.com/wgtunnel/android).
