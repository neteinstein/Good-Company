# Testing on Google Play

Two things live here: what to paste into **App content → App access** for Google's reviewers, and a manual QA
checklist to run on the **internal testing** track before promoting a build.

## App access (Play Console → App content → App access)

Choose **"All or some functionality is restricted"**, because the AI features need an API key, and paste:

> No account or login is needed. All data stays on the device.
>
> To explore quickly: on the first screen, tap "Explore with sample friends". This loads a set of fictional
> friends, circles, meetups and plans. They can be removed in Settings → Your data → Sample friends.
>
> Optional AI features (talking points, weekly plans, gift and message ideas) are off by default and use the
> user's own Anthropic API key, entered in Settings → AI ideas. They are a bring-your-own-key feature: the app
> has no account or key of its own to share. Everything else works without a key.
>
> Optional Google Drive backup (Settings → Your data → Google Drive) signs in with the reviewer's own Google account and asks for a
> backup password.

If you want reviewers to see the AI features, create a dedicated Anthropic API key with a low spend limit, add
it to the instructions above ("Settings → AI ideas → turn on AI features, paste this key"), and revoke it once
the review is done. Never use your personal key.

No demonstration video is needed: the Play build requests no restricted permission.

## Internal-track QA checklist

Install from the internal testing link on a real phone (Android 12+), signed in with a tester account.

**First run**
- [ ] The intro pager plays; permission primers appear *before* each system dialog (notifications, location).
- [ ] "Explore with sample friends" loads the sample set; Today, People, Circles and Plans all have content.
- [ ] The app follows the device language; Settings → Language switches between the five languages.

**People**
- [ ] Add a friend with the system contact picker (several at once); no `READ_CONTACTS` prompt appears.
- [ ] Import a `.vcf` file from Settings.
- [ ] Open a friend: edit tastes, add a kid, an important moment and a gift idea; change their closeness.
- [ ] The catch-up story opens and shows their news.

**Logging and plans**
- [ ] Log a meetup from Today and from a friend's page: in person, with a place from the current location.
- [ ] Undo appears after deleting a timeline entry.
- [ ] Create a plan with a date and time, add it to the calendar, then answer "How did it go?".
- [ ] Create a circle and check "When did the circle last meet?".

**Outside the app**
- [ ] A reminder notification arrives at the chosen hour; its quick actions work.
- [ ] The "Who's next" widget shows on the home screen and updates after logging a meetup.
- [ ] Share a link from the browser to Good Company and save it to a friend's news.
- [ ] App-icon shortcuts open the Log sheet (and are hidden while the app lock is on).

**Privacy and data**
- [ ] App lock with fingerprint / PIN locks the app when it goes to the background.
- [ ] Private lock-screen notifications hide names.
- [ ] Drive backup: sign in, set a password, back up, then restore on a second device with the password.
- [ ] With AI off, no AI button, section or hint appears anywhere. With a key and AI on, talking points load.

**Release checks**
- [ ] The installed package is `org.goodcompany.app` (Settings → Apps → Good Company → App details).
- [ ] The app's permission list shows no contacts, call log, SMS or install-packages permission.
