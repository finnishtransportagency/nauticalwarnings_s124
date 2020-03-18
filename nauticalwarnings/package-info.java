/**
 * Integration to Digitraffic Nautical Warnings
 * - We periodically fetch Nautical Warnings from digitraffic open APIs
 * - We store them in local database
 * - We remove any warnings that have disappeared
 * - We converts warnings to S124 and offer them as part of WINVIS services
 * - We also send email notifications to interested parties
 */
package fi.liikennevirasto.winvis.nauticalwarnings;