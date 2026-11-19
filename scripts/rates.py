"""Builds rates.json: EGP per unit of common currencies, and gold per gram in EGP.

Run once a day by .github/workflows/rates.yml. Only the Python standard library is used.
"""
import json
import os
import sys
import urllib.request
from datetime import datetime, timezone

CODES = ["USD", "EUR", "SAR", "AED", "KWD", "GBP", "QAR", "TRY", "JOD", "OMR", "BHD", "CHF", "CAD", "AUD", "CNY", "JPY", "ILS", "LYD", "TND", "DZD", "MAD", "MRU", "SDG", "IQD", "LBP", "SYP", "YER"]
OUNCE_GRAMS = 31.1034768


def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "pocketta-rates", "Accept": "application/json"})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.loads(r.read().decode("utf-8"))


def fx_open_er():
    d = get("https://open.er-api.com/v6/latest/EGP")
    per_egp = d["rates"]
    return {c: 1 / per_egp[c] for c in CODES if per_egp.get(c)}


def fx_fawaz():
    d = get("https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.json")["usd"]
    egp = d["egp"]
    out = {c: egp / d[c.lower()] for c in CODES if d.get(c.lower())}
    out["USD"] = egp
    return out


def gold_usd_ounce():
    try:
        return float(get("https://api.gold-api.com/price/XAU")["price"])
    except Exception as e:  # noqa: BLE001
        print("gold-api failed:", e, file=sys.stderr)
    d = get("https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.json")["usd"]
    return 1 / float(d["xau"])


def previous_prices(today):
    """Yesterday's prices, read from the file this job published last time, so the app can show up/down arrows."""
    repo = os.environ.get("GITHUB_REPOSITORY", "architectahmedgaber-sys/pocketta")
    try:
        old = get("https://raw.githubusercontent.com/%s/rates/rates.json" % repo)
    except Exception as e:  # noqa: BLE001
        print("no previous file:", e, file=sys.stderr)
        return None
    if str(old.get("updated", ""))[:10] == today:
        return old.get("previous")  # same day again: keep what the first run of the day saved
    return {"rates": old.get("rates", {}), "gold": old.get("gold", {})}


def main():
    rates = {}
    for source in (fx_open_er, fx_fawaz):
        try:
            rates = source()
            if rates.get("USD", 0) > 1:
                break
        except Exception as e:  # noqa: BLE001
            print(source.__name__, "failed:", e, file=sys.stderr)
    if rates.get("USD", 0) <= 1:
        sys.exit("no currency rates")
    out = {
        "updated": datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC"),
        "base": "EGP",
        "rates": {k: round(v, 4) for k, v in sorted(rates.items())},
    }
    try:
        g24 = gold_usd_ounce() / OUNCE_GRAMS * rates["USD"]
        out["gold"] = {"24": round(g24, 2), "21": round(g24 * 21 / 24, 2), "18": round(g24 * 18 / 24, 2)}
    except Exception as e:  # noqa: BLE001
        print("gold failed:", e, file=sys.stderr)
    prev = previous_prices(out["updated"][:10])
    if prev:
        out["previous"] = prev
    with open("rates.json", "w") as f:
        json.dump(out, f, indent=1)
    print(json.dumps(out))


if __name__ == "__main__":
    main()
