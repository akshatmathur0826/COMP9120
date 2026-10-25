#!/usr/bin/env python3
"""Generate seed-large.sql: a larger, deterministic synthetic dataset for analytics.

Every transaction is submitted through the running Spring Boot API, so each stored
assessment, rule match and alert comes from the real rule engine, not hand-written
rows. Alerts are then reviewed through the API, timestamps are aligned with the
simulated event times, and the whole fraud_detection schema is exported as SQL.

The compact seed (seed.sql) is loaded first and kept unchanged, so the demo IDs
(cards 1001-1003, alerts 9001-9003) still exist; generated activity uses new cards.

Usage (from the project root, with Postgres running):
    createdb fraud_large_gen
    psql -d fraud_large_gen -f schema.sql
    # start the backend against that database, e.g. on port 8080:
    #   cd backend && DB_NAME=fraud_large_gen DB_USER=$USER sh mvnw spring-boot:run
    python3 tools/generate_large_seed.py --database fraud_large_gen --api http://localhost:8080/api

The script RESETS the target database (it reloads seed.sql), so it refuses to run
unless the database name contains "gen".
"""
import argparse
import csv
import io
import json
import random
import subprocess
import sys
import urllib.error
import urllib.request
from datetime import date, datetime, time, timedelta, timezone
from pathlib import Path
from zoneinfo import ZoneInfo

ROOT = Path(__file__).resolve().parent.parent
SYDNEY = ZoneInfo("Australia/Sydney")
START, END = date(2026, 6, 1), date(2026, 10, 20)
RNG = random.Random(9120)

FIRST = ["Aarav", "Bella", "Caleb", "Daisy", "Ethan", "Freya", "George", "Hana", "Isaac", "Jade",
         "Kai", "Lily", "Mason", "Nina", "Oscar", "Priya", "Quinn", "Ruby", "Sam", "Tara",
         "Umar", "Vera", "Will", "Xin", "Yusuf", "Zara", "Leo", "Maya", "Noah", "Olivia",
         "Jack", "Grace", "Liam", "Chloe", "Ravi", "Mei", "Tom", "Sofia", "Hugo", "Amira"]
LAST = ["Patel", "Smith", "Nguyen", "Brown", "Wilson", "Taylor", "Chen", "Kumar", "Martin", "Lee",
        "White", "Singh", "Walker", "Hall", "Young", "Khan", "King", "Wright", "Scott", "Green"]

# category: (merchant names, typical amount range AUD)
CATEGORIES = {
    "GROCERIES": (["Coastline Fresh", "Market Lane Grocer", "Daily Basket"], (12, 180)),
    "DINING": (["Laneway Noodles", "Bondi Brunch Co", "Harbour Pizza"], (9, 110)),
    "FUEL": (["Southern Fuel", "Highway Fuel Stop"], (35, 120)),
    "TRANSPORT": (["Metro Rideshare", "City Transit Top-up"], (4, 60)),
    "PHARMACY": (["Wellness Pharmacy"], (8, 85)),
    "ONLINE_RETAIL": (["ShopStream Online", "ParcelPoint Marketplace"], (15, 380)),
    "ENTERTAINMENT": (["Starlight Cinemas", "Tune Subscription"], (12, 140)),
    "ELECTRONICS": (["Circuit Electronics", "Gadget Depot"], (60, 1600)),
    "TRAVEL": (["Southern Cross Airlines", "Harbourview Hotels"], (120, 2600)),
    "LUXURY": (["Opal Jewellers", "Maison Leather"], (300, 3200)),
}
EVERYDAY = ["GROCERIES", "DINING", "FUEL", "TRANSPORT", "PHARMACY", "ONLINE_RETAIL", "ENTERTAINMENT"]
NEW_LOCATIONS = [("Perth", "AU"), ("Adelaide", "AU"), ("Canberra", "AU"), ("Hobart", "AU"),
                 ("Gold Coast", "AU"), ("Darwin", "AU"), ("Auckland", "NZ"), ("Singapore", "SG"),
                 ("London", "GB")]
NEW_ANALYSTS = [(3, "Mia Thompson"), (4, "Daniel Okafor"), (5, "Sarah Lim")]
ANALYST_WEIGHTS = {1: 3, 2: 2, 3: 3, 4: 1, 5: 1}  # deliberately uneven workload

TABLES = ["customer", "account", "card", "merchant", "device", "location", "analyst", "fraud_rule",
          "payment_transaction", "risk_assessment", "rule_match", "fraud_alert", "alert_review"]
SEQUENCES = {"customer": "customer_id", "account": "account_id", "card": "card_id",
             "merchant": "merchant_id", "device": "device_id", "location": "location_id",
             "analyst": "analyst_id", "fraud_rule": "rule_id", "payment_transaction": "transaction_id",
             "risk_assessment": "assessment_id", "fraud_alert": "alert_id", "alert_review": "review_id"}


def q(value):
    if value is None:
        return "NULL"
    return "'" + str(value).replace("'", "''") + "'"


class Db:
    def __init__(self, args):
        self.base = ["psql", "-X", "-q", "-v", "ON_ERROR_STOP=1", "-d", args.database]
        if args.host:
            self.base += ["-h", args.host]
        if args.port:
            self.base += ["-p", str(args.port)]
        if args.user:
            self.base += ["-U", args.user]

    def run(self, sql):
        result = subprocess.run(self.base + ["-At"], input="SET search_path TO fraud_detection;\n" + sql,
                                capture_output=True, text=True)
        if result.returncode != 0:
            sys.exit(f"psql failed:\n{result.stderr}")
        return result.stdout

    def file(self, path):
        result = subprocess.run(self.base + ["-f", str(path)], capture_output=True, text=True)
        if result.returncode != 0:
            sys.exit(f"psql failed on {path}:\n{result.stderr}")

    def copy_out(self, table, order_by):
        out = self.run(f"COPY (SELECT * FROM {table} ORDER BY {order_by}) "
                       f"TO STDOUT WITH (FORMAT csv, HEADER, NULL '\\N');")
        return list(csv.reader(io.StringIO(out)))


class Api:
    def __init__(self, base):
        self.base = base.rstrip("/")

    def call(self, method, path, body):
        req = urllib.request.Request(self.base + path, data=json.dumps(body).encode(), method=method,
                                     headers={"Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(req) as response:
                return json.loads(response.read())
        except urllib.error.HTTPError as err:
            sys.exit(f"{method} {path} failed with {err.code}: {err.read().decode()}\nbody: {body}")


def local_time(day, rng, early=7, late=22):
    minutes = rng.randint(early * 60, late * 60)
    return datetime.combine(day, time(minutes // 60, minutes % 60, rng.randint(0, 59)), SYDNEY)


def build_reference_data(db):
    """Insert extra customers, accounts, cards, merchants, devices, locations and analysts."""
    sql, cards = [], []
    loc_ids = {"Sydney": 1, "Melbourne": 2, "Brisbane": 3}
    for i, (city, cc) in enumerate(NEW_LOCATIONS, start=4):
        sql.append(f"INSERT INTO location VALUES ({i}, {q(city)}, {q(cc)});")
        loc_ids[city] = i
    for analyst_id, name in NEW_ANALYSTS:
        email = name.lower().replace(" ", ".") + "@example.test"
        sql.append(f"INSERT INTO analyst VALUES ({analyst_id}, {q(name)}, {q(email)}, TRUE);")

    merchants, merchant_id = {}, 6
    for category, (names, _) in CATEGORIES.items():
        for name in names:
            sql.append(f"INSERT INTO merchant VALUES ({merchant_id}, {q(name)}, {q(category)}, "
                       f"{q(f'MER-GEN-{merchant_id:03d}')});")
            merchants.setdefault(category, []).append(merchant_id)
            merchant_id += 1

    device_id, account_id, card_id = 5, 104, 1004
    home_cities = ["Sydney"] * 5 + ["Melbourne"] * 4 + ["Brisbane"] * 3 + ["Perth", "Adelaide", "Canberra", "Hobart"]
    used_names = set()
    for customer_id in range(4, 44):
        while True:
            name = f"{RNG.choice(FIRST)} {RNG.choice(LAST)}"
            if name not in used_names:
                used_names.add(name)
                break
        joined = datetime(2026, 1, 1, tzinfo=timezone.utc) + timedelta(days=RNG.randint(0, 120))
        sql.append(f"INSERT INTO customer VALUES ({customer_id}, {q(name)}, "
                   f"{q(name.lower().replace(' ', '.') + f'.{customer_id}@example.test')}, {q(joined.isoformat())});")
        devices = []
        for _ in range(RNG.choice([1, 1, 2])):
            sql.append(f"INSERT INTO device VALUES ({device_id}, {q(f'dev_c{customer_id:02d}_{len(devices) + 1}')}, "
                       f"{q(joined.isoformat())});")
            devices.append(device_id)
            device_id += 1
        home = loc_ids[RNG.choice(home_cities)]
        for n in range(2 if RNG.random() < 0.2 else 1):
            sql.append(f"INSERT INTO account VALUES ({account_id}, {customer_id}, "
                       f"{q(f'ACCT-{customer_id:03d}-{n + 1}')}, {q(joined.isoformat())}, 'ACTIVE');")
            for _ in range(2 if RNG.random() < 0.25 else 1):
                sql.append(f"INSERT INTO card VALUES ({card_id}, {account_id}, {q(f'tok_gen_{card_id}')}, "
                           f"'ACTIVE', {q(joined.isoformat())});")
                cards.append({"card_id": card_id, "account_id": account_id, "devices": devices, "home": home,
                              "rate": RNG.uniform(0.25, 1.1),
                              "likes": RNG.sample(EVERYDAY, 4)})
                card_id += 1
            account_id += 1

    unfamiliar = []
    for n in range(20):
        sql.append(f"INSERT INTO device VALUES ({device_id}, {q(f'dev_unknown_{n + 1:02d}')}, '2026-05-01T00:00:00+00');")
        unfamiliar.append(device_id)
        device_id += 1

    db.run("\n".join(sql))
    return cards, merchants, unfamiliar, loc_ids


def plan_events(cards, merchants, unfamiliar, loc_ids):
    """Return chronologically ordered (time, card, payload-without-requestId, scenario)."""
    events, blocked_cards = [], set()
    far_away = [loc_ids[c] for c in ("Perth", "Darwin", "Auckland", "Singapore", "London")]
    unfamiliar_pool = list(unfamiliar)
    RNG.shuffle(unfamiliar_pool)

    def add(at, card, category, amount, device, location, outcome="APPROVED", scenario="normal"):
        events.append((at.astimezone(timezone.utc), card["card_id"], {
            "cardId": card["card_id"], "merchantId": RNG.choice(merchants[category]), "deviceId": device,
            "locationId": location, "amount": round(amount, 2), "authOutcome": outcome}, scenario))

    days = (END - START).days + 1
    for card in cards:
        takeover_day = START + timedelta(days=RNG.randint(30, days - 2)) if RNG.random() < 0.14 else None
        last_day = takeover_day or END
        for offset in range((last_day - START).days + 1):
            day = START + timedelta(days=offset)
            for _ in range(sum(1 for _ in range(3) if RNG.random() < card["rate"] / 3)):
                category = RNG.choice(card["likes"]) if RNG.random() < 0.98 else RNG.choice(["ELECTRONICS", "TRAVEL", "LUXURY"])
                low, high = CATEGORIES[category][1]
                amount = min(high, low + (high - low) * RNG.random() ** 2.2)
                device = card["devices"][0] if RNG.random() < 0.8 else RNG.choice(card["devices"])
                location = card["home"] if RNG.random() < 0.9 else RNG.choice(list(loc_ids.values())[:6])
                outcome = "DECLINED" if RNG.random() < 0.025 else "APPROVED"
                add(local_time(day, RNG), card, category, amount, device, location, outcome)

        if RNG.random() < 0.12:  # legitimate one-off big purchase on a known device (false positive)
            day = START + timedelta(days=RNG.randint(20, (last_day - START).days))
            add(local_time(day, RNG), card, RNG.choice(["TRAVEL", "ELECTRONICS"]), RNG.uniform(5000, 6800),
                card["devices"][0], card["home"], scenario="legit_large")

        if RNG.random() < 0.12:  # velocity burst: rapid small attempts
            at = local_time(START + timedelta(days=RNG.randint(10, (last_day - START).days)), RNG, 9, 21)
            scenario = RNG.choice(["card_testing", "subscription_burst"])
            for i in range(RNG.randint(5, 7)):
                add(at + timedelta(seconds=55 * i + RNG.randint(0, 20)), card,
                    "ONLINE_RETAIL" if scenario == "card_testing" else "TRANSPORT", RNG.uniform(2, 25),
                    card["devices"][0], card["home"],
                    "DECLINED" if scenario == "card_testing" and RNG.random() < 0.4 else "APPROVED", scenario)

        if RNG.random() < 0.08:  # repeated declines (R5 only, below the alert threshold)
            at = local_time(START + timedelta(days=RNG.randint(10, (last_day - START).days)), RNG, 9, 21)
            for i in range(RNG.randint(3, 4)):
                add(at + timedelta(seconds=70 * i), card, "ONLINE_RETAIL", RNG.uniform(60, 300),
                    card["devices"][0], card["home"], "DECLINED", "decline_streak")

        if takeover_day:  # account takeover: unfamiliar device, far away, escalating amounts
            device = unfamiliar_pool.pop() if unfamiliar_pool else RNG.choice(unfamiliar)
            at = local_time(takeover_day, RNG, 0, 5)
            location = RNG.choice(far_away)
            add(at, card, "ELECTRONICS", RNG.uniform(1200, 3800), device, location, scenario="takeover")
            add(at + timedelta(minutes=RNG.randint(4, 25)), card, "LUXURY", RNG.uniform(5000, 9500),
                device, location, scenario="takeover")
            if RNG.random() < 0.5:
                base = at + timedelta(minutes=40)
                for i in range(3):
                    add(base + timedelta(seconds=60 * i), card, "LUXURY", RNG.uniform(4000, 8000),
                        device, location, "DECLINED", "takeover")
            blocked_cards.add(card["card_id"])

    events.sort(key=lambda e: (e[0], e[1]))
    return events, blocked_cards


REVIEW_NOTES = {
    "takeover": ("Unrecognised device overseas; customer did not authorise. Card blocked and reissued.", "RESOLVED"),
    "card_testing": ("Pattern consistent with card testing; customer confirmed unauthorised attempts.", "RESOLVED"),
    "subscription_burst": ("Customer confirmed repeated transit top-ups; legitimate activity.", "DISMISSED"),
    "legit_large": ("Customer confirmed the purchase by phone.", "DISMISSED"),
    "normal": ("Within this customer's normal spending pattern after review.", "DISMISSED"),
    "decline_streak": ("Customer confirmed repeated declined attempts were their own.", "DISMISSED"),
}


def review_alerts(api, alerts):
    analyst_ids = [a for a, w in ANALYST_WEIGHTS.items() for _ in range(w)]
    open_cutoff = datetime.combine(END - timedelta(days=6), time(0), timezone.utc)
    for alert_id, occurred_at, scenario in alerts:
        analyst = RNG.choice(analyst_ids)
        if occurred_at >= open_cutoff:  # recent alerts stay in the queue
            if RNG.random() < 0.4:
                api.call("PATCH", f"/alerts/{alert_id}/status", {"newStatus": "INVESTIGATING", "analystId": analyst})
            continue
        note, final = REVIEW_NOTES[scenario]
        if final == "DISMISSED" and RNG.random() < 0.5:
            api.call("PATCH", f"/alerts/{alert_id}/status", {"newStatus": "DISMISSED", "analystId": analyst, "note": note})
            continue
        api.call("PATCH", f"/alerts/{alert_id}/status",
                 {"newStatus": "INVESTIGATING", "analystId": analyst, "note": "Reviewing transaction context."})
        api.call("PATCH", f"/alerts/{alert_id}/status", {"newStatus": final, "analystId": analyst, "note": note})


def align_timestamps(db, blocked_cards):
    """API writes use 'now'; move generated rows onto the simulated timeline."""
    db.run(f"""
        UPDATE payment_transaction SET recorded_at = occurred_at + INTERVAL '1 second'
        WHERE transaction_id > 10023;
        UPDATE risk_assessment ra SET assessed_at = t.occurred_at + INTERVAL '1 second'
        FROM payment_transaction t WHERE t.transaction_id = ra.transaction_id AND t.transaction_id > 10023;
        UPDATE fraud_alert fa SET created_at = t.occurred_at + INTERVAL '2 seconds'
        FROM risk_assessment ra JOIN payment_transaction t ON t.transaction_id = ra.transaction_id
        WHERE ra.assessment_id = fa.assessment_id AND fa.alert_id > 9003;
        -- review steps happen hours after the alert, in order, deterministically per alert
        UPDATE alert_review r SET changed_at = fa.created_at
               + INTERVAL '1 hour' * (1 + fa.alert_id % 5)
               + INTERVAL '1 hour' * (CASE WHEN r.new_status = 'INVESTIGATING' THEN 0 ELSE 6 + fa.alert_id % 30 END)
        FROM fraud_alert fa WHERE fa.alert_id = r.alert_id AND fa.alert_id > 9003;
        UPDATE fraud_alert fa SET reviewed_at = r.changed_at
        FROM alert_review r
        WHERE r.alert_id = fa.alert_id AND fa.alert_id > 9003 AND r.new_status = fa.status
          AND fa.status IN ('RESOLVED', 'DISMISSED');
        UPDATE card SET status = 'BLOCKED' WHERE card_id IN ({",".join(map(str, sorted(blocked_cards))) or "NULL"});
    """)


def export(db, out_path):
    lines = [
        "-- Large deterministic seed profile for analytics (generated by tools/generate_large_seed.py).",
        "-- Includes everything in seed.sql plus ~40 customers and several thousand transactions",
        "-- whose assessments, rule matches and alerts were produced by the real rule engine.",
        "-- Run after schema.sql:  psql -d fraud_demo -f seed-large.sql   (resets fraud_detection data)",
        "",
        "SET search_path TO fraud_detection;",
        "BEGIN;",
        "",
        "TRUNCATE TABLE " + ", ".join(reversed(TABLES)) + " RESTART IDENTITY CASCADE;",
    ]
    order = {"rule_match": "assessment_id, rule_id"}
    for table in TABLES:
        rows = db.copy_out(table, order.get(table, SEQUENCES.get(table, "1")))
        header, data = rows[0], rows[1:]
        lines.append(f"\n-- {table}: {len(data)} rows")
        for start in range(0, len(data), 500):
            chunk = data[start:start + 500]
            values = ",\n".join("(" + ", ".join("NULL" if v == "\\N" else q(v) for v in row) + ")" for row in chunk)
            lines.append(f"INSERT INTO {table} ({', '.join(header)}) VALUES\n{values};")
    lines.append("")
    for table, column in SEQUENCES.items():
        lines.append(f"SELECT setval(pg_get_serial_sequence('fraud_detection.{table}', '{column}'), "
                     f"(SELECT MAX({column}) FROM {table}), TRUE);")
    lines += ["", "COMMIT;", "", "ANALYZE;", ""]
    out_path.write_text("\n".join(lines))


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--database", required=True)
    parser.add_argument("--host")
    parser.add_argument("--port", type=int)
    parser.add_argument("--user")
    parser.add_argument("--api", default="http://localhost:8080/api")
    parser.add_argument("--out", default=str(ROOT / "seed-large.sql"))
    args = parser.parse_args()
    if "gen" not in args.database:
        sys.exit("Refusing to reset a database whose name does not contain 'gen' (e.g. fraud_large_gen).")

    db, api = Db(args), Api(args.api)
    db.file(ROOT / "seed.sql")
    cards, merchants, unfamiliar, loc_ids = build_reference_data(db)
    events, blocked_cards = plan_events(cards, merchants, unfamiliar, loc_ids)
    print(f"{len(cards)} new cards, {len(events)} transactions to submit ...")

    alerts, counters = [], {}
    for n, (at, card_id, payload, scenario) in enumerate(events, start=1):
        counters[card_id] = counters.get(card_id, 0) + 1
        payload = {**payload, "requestId": f"gen-{card_id}-{counters[card_id]:04d}",
                   "occurredAt": at.isoformat().replace("+00:00", "Z")}
        result = api.call("POST", "/transactions", payload)
        if result["alertId"]:
            alerts.append((result["alertId"], at, scenario))
        if n % 500 == 0:
            print(f"  {n}/{len(events)} submitted, {len(alerts)} alerts so far")

    print(f"Reviewing {len(alerts)} alerts ...")
    review_alerts(api, alerts)
    align_timestamps(db, blocked_cards)
    export(db, Path(args.out))
    print(f"Wrote {args.out}")


if __name__ == "__main__":
    main()
