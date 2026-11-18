# Supercat rules (owner's definitive logic)

Saved as the owner stated it. These rules are shared by Purrcast, Zamoled and Maps and Cats and checked against `docs/hero-cat-vectors.json`.

## The owner's words
- The streak is observed in any one of the 3 apps; all share the same streak days, whichever app is highest.
- The supercat only appears on the **10th day** of the streak.
- After that day it grows **happier each day**, normally, but still **reacts to the weather** at the user's location.
- If the streak breaks, the supercat can **save it, up to 7 days** at most.
- If all 7 days are used, the supercat goes back to the **normal Google Noto cat**, keeping a **sad face** until the **3rd day** of the streak, where it shows **confidence**.
- The special wallpaper must look **way better than any other wallpaper**.
- **Special effects (animation)** when the streak is **more than 20 days**.

## How it is built (my calls are marked)
- **Shared streak** is `Streak.adopt` (highest alive sibling wins), already live.
- **Saving** is the Streak Shield: one save bridges 1 to 7 missed days, then recharges after 7 visit days in a row.
- **Phase** from `count`, `ready` (shield), `charge`:
  - `HERO`: `count >= 10` and the shield is ready.
  - otherwise the normal Noto cat: `recoveryDay = ready ? count : charge + 1`; days 1 and 2 are `SAD` (Noto crying cat), day 3 onward `CONFIDENT` (Noto smirking cat). *(my call: confident cat = the smirk cat, no extra asset)*
  - A spent shield means the cat is normal until it recharges (7 days) and the streak is still 10 or more, then the supercat is back.
- **Happier each day** (`joy`): day 10 = 0, 11-13 = 1, 14-20 = 2, 21-29 = 3, 30+ = 4. Joy 1 turns smirk/confident weather moods into smile; joy 2+ turns smile/smirk/confident into grin. *(my call: the tiers)*
- **Weather always wins**: angry, sad, shocked, scared and loaf (night) are never changed by joy.
- **Animation** when `count > 20`: living effects on the special wallpaper and the Cat Club card (no animation when the phone's remove-animations setting is on).
- The cat still needs all three apps installed and the "Show hero cat" switch on *(my call, as before)*.
