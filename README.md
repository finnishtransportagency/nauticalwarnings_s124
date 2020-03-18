This module is extracted from a proprietary Spring Boot application and does not work as is.

directory nauticalwarnings contains a module that reads nautical warnings from Digitraffic, converts them to S-124 and sends them to Baltic nautical warnings service

directory common contains common libraries used in Winwis that the nauticalwarnings also depend on

file nw-sea-areas-converted.json is a resource file that GeoLocationMatcher references to in order to map the location into the areas defined in the S-124 schema

file s124_template.xml is a resource file that NauticalWarningsService uses as a template where to map Digitraffic nautical warning data to