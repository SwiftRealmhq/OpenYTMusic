#!/usr/bin/env bash
# Diagnóstico del cap de 1 MB — Selene
# Uso: bash diag-cap.sh
# Prueba Despacito (video de sello, normalmente capado) y Rick Astley (control)
# con todos los clientes, y verifica si los streams sirven más allá de 1 MB.

set -u
VID_LABEL="kJQP7kiw5Fk"   # Despacito (VEVO/sello)
VID_RICK="dQw4w9WgXcQ"   # Rick Astley (canal del artista, control)

echo "════════════════════════════════════════════════════════════"
echo "🌙 Diagnóstico del cap de 1 MB — desde TU IP"
echo "════════════════════════════════════════════════════════════"
echo "IP pública: $(curl -s --max-time 8 https://api.ipify.org 2>/dev/null || echo '?')"
echo ""

test_video() {
  local VID="$1" LABEL="$2"
  echo "── $LABEL ($VID) ──────────────────────────────"
  python3 - "$VID" <<'EOF'
import json, subprocess, sys, urllib.request

VID = sys.argv[1]

CLIENTS = [
    ("ANDROID", "ANDROID", "20.01.35", "com.google.android.youtube/20.01.35 (Linux; U; Android 13) gzip", None),
    ("IOS", "IOS", "20.01.35", "com.google.ios.youtube/20.01.35 (iPhone14,3; U; CPU iOS 17_4 like Mac OS X)", None),
    ("TVHTML5", "TVHTML5", "7.20240327.13.00.00", "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version", None),
    ("ANDROID_MUSIC", "ANDROID_MUSIC", "6.09.52", "com.google.android.apps.youtube.music/6.09.52 (Linux; U; Android 13) gzip", 33),
    ("ANDROID_UNPLUGGED", "ANDROID_UNPLUGGED", "7.09.1", "com.google.android.apps.youtube.unplugged/7.09.1 (Linux; U; Android 12) gzip", 31),
    ("WEB", "WEB", "2.20250620.01.00", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36", None),
]

def fetch(clientName, clientVersion, ua, sdk):
    client = {"clientName": clientName, "clientVersion": clientVersion, "gl": "US", "hl": "en"}
    if sdk: client["androidSdkVersion"] = sdk
    body = json.dumps({"context": {"client": client}, "videoId": VID})
    out = subprocess.run(['curl', '-s', '--max-time', '15', 'https://www.youtube.com/youtubei/v1/player',
        '-H', 'Content-Type: application/json', '-H', 'User-Agent: ' + ua, '-d', body],
        capture_output=True, text=True).stdout
    return json.loads(out)

def range_test(url):
    try:
        req = urllib.request.Request(url, headers={'Range': 'bytes=1048576-2097151', 'User-Agent': 'Mozilla/5.0'})
        r = urllib.request.urlopen(req, timeout=15)
        return "LIBRE (206: sirve más allá de 1MB ✅)"
    except Exception as e:
        code = getattr(e, 'code', '?')
        if code == 403: return "CAPADO (403 más allá de 1MB ❌)"
        if code == 416: return "archivo < 1MB (416)"
        return "ERROR %s" % e

for name, cname, cver, ua, sdk in CLIENTS:
    try:
        d = fetch(cname, cver, ua, sdk)
    except Exception as e:
        print("  %-16s → falló el fetch: %s" % (name, e)); continue
    if 'error' in d:
        print("  %-16s → %s" % (name, d['error'].get('message', 'error')))
        continue
    ps = d.get('playabilityStatus', {})
    st = ps.get('status')
    if st != 'OK':
        print("  %-16s → status=%s (%s)" % (name, st, ps.get('reason', '')))
        continue
    f = d.get('streamingData', {})
    plain = ciphered = None
    for fmt in f.get('adaptiveFormats', []):
        if fmt.get('mimeType', '').startswith('audio'):
            if fmt.get('url') and not plain: plain = fmt['url']
            if fmt.get('signatureCipher') and not ciphered: ciphered = fmt['signatureCipher']
    if plain:
        print("  %-16s → OK · URL directa · %s" % (name, range_test(plain)))
    elif ciphered:
        print("  %-16s → OK · URL CIFRADA (requiere descifrado JS)" % name)
    else:
        print("  %-16s → OK · sin streams de audio" % name)
EOF
  echo ""
}

test_video "$VID_RICK" "Control: Rick Astley (canal del artista)"
test_video "$VID_LABEL" "Despacito (sello VEVO)"

echo "════════════════════════════════════════════════════════════"
echo "Interpretación rápida:"
echo "  · Si RICK sale LIBRE y DESPACITO capado → el cap es POR VIDEO"
echo "    (música de sellos). Afecta a todos → hay que usar el cliente"
echo "    WEB con descifrado de firmas (nuestra carta bajo la manga)."
echo "  · Si AMBOS salen capados → TU IP está marcada (VPN/datacenter/"
echo "    operador). Probá desde otra red (celular con datos móviles)."
echo "  · Si algún cliente distinto de ANDROID sale LIBRE en DESPACITO"
echo "    → ese es el que implementamos como plan B. 🎯"
echo "════════════════════════════════════════════════════════════"