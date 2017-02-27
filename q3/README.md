Rest endpoints(?):
* Check-in (user/venue)
* Where is user A right now?
* What users are at venue X right now?

business rules:
1. A user can only be at one venue at a time. If user A checks in at venue X and then at venue Y, they are no longer at venue X.
2. A check-in only “lasts” for at most 3 hours. If user A checks in at venue X and then does nothing for 3 hours, they are no longer at venue X.

Authentication & Authorization?
Timezones?
Storage!
Precompute anything?

Discuss:
* Time/space tradeoffs — are you pre-calculating or caching anything to save time later, or recalculating values to save space?
* Storage — If you're using a database, describe what kind (sql, mongo, etc), how the data will be organized, and any indexes. If storage is being spread out over multiple databases (e.g. via sharding), how is that done?
* Scalability / Availability — If you are going to have multiple servers in your service, describe how you will distribute work between them. What would happen if you suddenly had 100x more requests? What would occur if one or more servers failed (in whatever way they could fail)?
