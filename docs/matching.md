# Matching rules

## Required profile data

An eligible profile has gender, biography, at least one interest tag, an adult
birth date, and an approximate city/neighborhood with coordinates. A photo is
optional for browsing but required before sending a like. Orientation defaults
to bisexual when unspecified. Exact date of birth and raw coordinates are private;
public profiles show age, approximate place and distance instead.

Age is calculated from date of birth, not a stored age that becomes stale.
Matcha accepts adult profiles aged 18-120. This is an implementation policy for
this dating application; the subject itself requires age-based search but does
not prescribe a registration age field or numerical age limits.

## Public popularity

`min(100, 5 * received_likes + distinct_profile_visitors)`

Directed likes and visitor-target pairs are unique in PostgreSQL. Repeated visits
from the same account do not inflate the score. Self-likes/visits are prohibited.
The score is calculated from current rows, so removal of a like is reflected
without stale cached values. Scores range from 0 through 100.

## Approximate location

GPS is optional and requires explicit consent before the browser is asked for it.
Users can instead select an approximate city, or manually enter an approximate
city/neighborhood and coordinates. Coordinates are rounded to two decimal places
before storage (roughly neighborhood scale); no IP-based location is used.
Manual entry remains available when GPS is denied, unavailable or fails.

## Discovery compatibility and ranking

Suggested profiles must be complete, verified, different from the current user,
and unblocked in both directions. Preferences must match in both directions:
bisexual includes every supported gender; homosexual requires the same gender;
heterosexual requires a different gender. The supported gender choices are woman,
man and nonbinary. Unspecified orientation defaults to bisexual.

Recommendations prioritize the same geographic area (within 25 km). Within each
area group, order by `10 * common_tags + popularity / 10 - min(distance_km,1000) / 20`,
highest first. Distance and then account ID resolve ties. This uses all three
required criteria while retaining geographic priority.

Users can override recommendation ordering with age, distance, popularity or
common-interest count and choose ascending/descending order. Filters combine age
and popularity ranges, city/neighborhood text, maximum distance and minimum
shared-tag count. SQL values are bound parameters; sort fields and directions
come from allowlists. Pagination includes an ID tie-break and a `has_more` flag.

Advanced search combines the same filters with up to ten specific interest tags.
Every selected tag must be present. Search intentionally allows profiles outside
the suggestion compatibility rules; it still excludes self, incomplete/unverified
profiles and blocked relationships in either direction.
