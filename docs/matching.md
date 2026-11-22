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
