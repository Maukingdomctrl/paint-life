"""Writes an invented Paint Life backup for screenshots. Nothing here is real data."""
import json, sys, random
from datetime import datetime, timedelta, timezone, date, time

out = sys.argv[1]
now_ms = int(sys.argv[2])  # the emulator's "now", epoch ms
Z = "Asia/Kolkata"
IST = timezone(timedelta(hours=5, minutes=30))
now = datetime.fromtimestamp(now_ms / 1000, IST)
today = now.date()
rnd = random.Random(7)

def ms(d, hh, mm=0, ss=0):
    return int(datetime.combine(d, time(hh, mm, ss), IST).timestamp() * 1000)

def day(n):  # n days ago
    return today - timedelta(days=n)

DAYS = 24
records, priorities, ex, bests, quiet, entries = [], [], [], [], [], []

titles = ["Morning walk", "Reply to Meera", "Finish chapter 4", "Pay electricity bill", "Call Aai", "Tidy the desk",
          "Plan the week", "Stretch after work", "Read 20 pages", "Water the plants", "Book dentist", "Cook dal",
          "Write journal", "Sort photos", "Review budget"]
for n in range(DAYS, -1, -1):
    d = day(n)
    count = 3 if n % 5 else 2
    for pos in range(count):
        done = (n > 0 and rnd.random() < 0.75) or (n == 0 and pos == 0)
        created = ms(d, 7, 5 + pos)
        completed = ms(d, 11 + pos * 3, 10) if done else None
        priorities.append(dict(id=f"p{n}_{pos}", date=d.isoformat(), position=pos, title=titles[(n * 3 + pos) % len(titles)],
                               isCompleted=done, completedAt=completed, completedZoneId=Z if done else None, carriedFromId=None,
                               createdAt=created, createdZoneId=Z, updatedAt=completed or created, updatedZoneId=Z))
records.append(dict(id="r0", date=day(1).isoformat(), note="Good day. Legs tired, mind clear.", carryOverDismissed=False,
                    createdAt=ms(day(1), 21, 0), createdZoneId=Z, updatedAt=ms(day(1), 21, 0), updatedZoneId=Z))

# Exercise: step-ups most days; three wins and one near miss in the league.
for n in range(DAYS, -1, -1):
    if n % 6 == 4 or n == 0:
        continue
    d = day(n)
    reps = rnd.choice([90, 110, 120, 140, 150, 160, 180, 200])
    active = reps * rnd.randint(2100, 2600)
    s = ms(d, 7, 45)
    ex.append(dict(id=f"v{n}", type="VOLUME", status="COMPLETED", challengeLevel=None, startedAt=s, zoneId=Z, endedAt=s + active + 60_000,
                   endedZoneId=Z, repetitions=reps, activeDurationMillis=active, notes=None, updatedAt=s + active + 60_000, updatedZoneId=Z))
wins = {}
for level, n, t in [(1, 17, 29 * 60_000 + 12_400), (2, 11, 28 * 60_000 + 31_800), (3, 5, 27 * 60_000 + 40_200)]:
    s = ms(day(n), 18, 0)
    wins[level] = s + t
    ex.append(dict(id=f"t{level}", type="TIMED_CHALLENGE", status="COMPLETED", challengeLevel=level, startedAt=s, zoneId=Z, endedAt=s + t,
                   endedZoneId=Z, repetitions=1000, activeDurationMillis=t, notes=None, updatedAt=s + t, updatedZoneId=Z))
    bests.append(dict(key=f"timed_level_{level}", kind="TIMED_CHALLENGE_TIME", challengeLevel=level, value=t, sessionId=f"t{level}", achievedAt=s + t, zoneId=Z))
s = ms(day(2), 18, 0); t = 27 * 60_000 + 21_500  # slower than Coco's 27:00
ex.append(dict(id="t4a", type="TIMED_CHALLENGE", status="COMPLETED", challengeLevel=4, startedAt=s, zoneId=Z, endedAt=s + t, endedZoneId=Z,
               repetitions=1000, activeDurationMillis=t, notes=None, updatedAt=s + t, updatedZoneId=Z))
bests.append(dict(key="timed_level_4", kind="TIMED_CHALLENGE_TIME", challengeLevel=4, value=t, sessionId="t4a", achievedAt=s + t, zoneId=Z))
ex.sort(key=lambda e: e["startedAt"])

levels = []
for level in range(1, 11):
    row = dict(level=level, isUnlocked=level <= 4, unlockedAt=None, unlockedZoneId=None, beatenAt=None, beatenZoneId=None)
    if level in wins:
        row.update(beatenAt=wins[level], beatenZoneId=Z)
    if level - 1 in wins:
        row.update(unlockedAt=wins[level - 1], unlockedZoneId=Z)
    levels.append(row)

# Quiet Time: most days complete, a couple ended early, a few missed. Today done.
for n in range(DAYS, -1, -1):
    d = day(n)
    if n in (20, 13, 8):
        continue
    s = ms(d, 6, 40)
    if s > now_ms:
        continue
    if n in (15, 3):
        a = 180_000 if n == 15 else 240_000
        quiet.append(dict(id=f"q{n}", status="ENDED_EARLY", startedAt=s, zoneId=Z, endedAt=s + a, endedZoneId=Z, targetDurationMillis=300_000,
                          activeDurationMillis=a, updatedAt=s + a, updatedZoneId=Z))
    else:
        quiet.append(dict(id=f"q{n}", status="COMPLETED", startedAt=s, zoneId=Z, endedAt=s + 300_000, endedZoneId=Z, targetDurationMillis=300_000,
                          activeDurationMillis=300_000, updatedAt=s + 300_000, updatedZoneId=Z))

# My Time: actual entries for the last 16 days (so the observation week is long over), today up to now.
def add(kind, cat, start, end, note=None, pid=""):
    if kind == "ACTUAL":
        end = min(end, (now_ms // 300_000) * 300_000)
    if end - start < 60_000:
        return
    entries.append(dict(id=f"{kind[0].lower()}{len(entries)}{pid}", kind=kind, categoryId=cat, startAt=start, endAt=end, zoneId=Z, endZoneId=Z,
                        note=note, createdAt=min(end, now_ms) if kind == "ACTUAL" else start - 3_600_000, createdZoneId=Z,
                        updatedAt=min(end, now_ms) if kind == "ACTUAL" else start - 3_600_000, updatedZoneId=Z))

for n in range(16, -1, -1):
    d = day(n)
    weekend = d.weekday() >= 5
    j = rnd.choice([0, 0, 15, 30])  # a little variety
    add("ACTUAL", "sleep", ms(d - timedelta(days=1), 23, j), ms(d, 7 if not weekend else 8, 0))
    wake = 8 if weekend else 7
    add("ACTUAL", "personal_care", ms(d, wake, 0), ms(d, wake, 45))
    add("ACTUAL", "exercise", ms(d, wake, 45), ms(d, wake + 1, 15), "Step-ups")
    if weekend:
        add("ACTUAL", "household", ms(d, 10, 0), ms(d, 12, 0), "Groceries, laundry")
        add("ACTUAL", "family", ms(d, 12, 30), ms(d, 15, 0), "Lunch together")
        add("ACTUAL", "rest", ms(d, 15, 0), ms(d, 16, 0))
        add("ACTUAL", "learning", ms(d, 16, 30), ms(d, 18, 0), "Kotlin course")
        add("ACTUAL", "entertainment", ms(d, 19, 0), ms(d, 21, 30), "Film")
        add("ACTUAL", "phone_internet", ms(d, 21, 30), ms(d, 22, 15 + j // 2))
    else:
        add("ACTUAL", "work", ms(d, 9, 0), ms(d, 13, 0), "Clinic")
        add("ACTUAL", "personal_care", ms(d, 13, 0), ms(d, 13, 45), "Lunch")
        add("ACTUAL", "work", ms(d, 14, 0), ms(d, 17, 30), "Clinic")
        add("ACTUAL", "phone_internet", ms(d, 17, 30), ms(d, 18, 0 + j))
        add("ACTUAL", "family", ms(d, 18, 30), ms(d, 20, 0), "Dinner")
        add("ACTUAL", "learning", ms(d, 20, 0), ms(d, 20, 45), "Reading")
        add("ACTUAL", "entertainment", ms(d, 21, 0), ms(d, 22, 15))
entries = [e for e in entries if e["startAt"] < now_ms]

# A plan for today and tomorrow.
for n in (0, -1):
    d = day(n)
    for cat, a, b in [("exercise", (7, 45), (8, 15)), ("work", (9, 0), (13, 0)), ("work", (14, 0), (17, 30)), ("family", (18, 30), (20, 0)),
                      ("learning", (20, 0), (21, 0)), ("rest", (21, 0), (22, 30))]:
        add("PLANNED", cat, ms(d, *a), ms(d, *b))

seed_flags = dict(personal_care=False, exercise=False, work=False, learning=False, household=False, family=False, rest=False,
                  entertainment=True, phone_internet=True, sleep=False, other=False)
backup = dict(
    format="paint-life-backup", formatVersion=1, appVersion="demo", databaseVersion=6, exportedAt=now_ms, exportedZoneId=Z,
    flags=dict(firstLaunchDate=day(DAYS).isoformat(), exerciseSafetyRead=True, victorySeenUpTo=max(wins.values()), observationComplete=True,
               insightsPeriod="Week", progressPeriod="ThisMonth"),
    dailyRecords=records, priorities=priorities, challengeLevels=levels, exerciseSessions=ex, personalBests=bests, quietSessions=quiet,
    categories=[dict(id=k, isDistraction=v) for k, v in seed_flags.items()], timeEntries=entries, schedules=[],
)
with open(out, "w", encoding="utf-8") as f:
    json.dump(backup, f, indent=1)
print(len(priorities), "priorities,", len(ex), "exercise,", len(quiet), "quiet,", len(entries), "entries")
