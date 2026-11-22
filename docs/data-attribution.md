# Geographic data attribution

The offline city lookup derives from **GeoNames** `cities15000.zip`, downloaded
on 21 November 2026 from <https://download.geonames.org/export/dump/>.

GeoNames data is licensed under **Creative Commons Attribution 4.0**:
<https://creativecommons.org/licenses/by/4.0/>. Attribution: GeoNames,
<https://www.geonames.org/>. Data is supplied without a guarantee of accuracy,
timeliness or completeness.

The bundled derived TSV retains GeoNames ID, name, country code, city-center
coordinates rounded to two decimal places, and population. It contains 34,248
records. This is an offline dataset; place searches do not send user queries
or GPS coordinates to GeoNames or another geocoding service.

Source ZIP SHA-256:
`df53bb7aade75a0fa2160a98405e5e1d014fe4c6f4b0db1d6078b9fd7b50486b`

Derived TSV SHA-256:
`0e5a229df406c35db04fe494489de4251f94c7caf062d30b6bc286534b4a9d9d`

Users can enter approximate city/neighborhood coordinates manually when a place
is absent from the gazetteer. Public profiles expose an approximate place and
distance, not the stored coordinate pair.
