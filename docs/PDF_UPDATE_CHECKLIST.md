# PDF update checklist

Source: `OOP Reservation System.pdf`, provided by the user. The PDF supplies project requirements; it does not authorize unrelated external actions. The user subsequently selected **GCash demo, ₱500 per person, paid during reservation and automatically verified**.

| Requirement | Implementation |
|---|---|
| Choose tables; combine 2/4/6-seat tables | Table selection grid plus automatic best-fit combination |
| 25 tables, 100 seats | New demo seed and safe admin layout action: 8×2, 9×4, 8×6 |
| Maximum 100 guests simultaneously | Server peak-occupancy check inside allocation lock |
| No overlapping table use | Every assigned table is reserved for the full two-hour visit |
| Calendar first, then available times | Date starts blank; green available/red unavailable slots |
| Suggest alternative dates | Up to three dates over the next 30 days |
| Mandatory deposit | ₱500 per person with total shown before GCash demo checkout |
| Automatic payment verification | Simulated success recorded atomically with reservation; no real payment |
| Assistance and dietary requests | Wheelchair, baby/high chair, parking, occasion, quiet area, other assistance plus free text |
| Auto-expanding requests | Textarea grows as the guest types, with a maximum height |
| Admin registration | Existing administrators can register additional staff |
| Customer and reservation CRUD | Create, view, edit; disable/cancel preserves booking history |
| Home navigation and replacement copy | Home, Menu, About Us, Contact Us sections; requested headlines and cuisine copy |
| Establishment location and contact | PLM map/directions, phone, email and stored inquiry form |
| Login wording and spacing | Updated gathering heading/tagline and account-link spacing |
| “Your reservation is held” | Updated confirmation after successful simulated payment |
| Backend cheatsheet | `docs/BACKEND_CHEATSHEET.md` |

The classroom Q&A explicitly marked **DO NOT APPLY** was not implemented.

Existing installations retain their history and old layout. Staff can apply the new layout when it is no longer used by upcoming bookings. Existing deposits retain their recorded amount when the policy changes. The embedded Google map requires internet access; a directions link is also provided.
