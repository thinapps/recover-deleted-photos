# Scanning

Recover Deleted Photos scans Android MediaStore locally for the selected media type.

It can inspect only media records that Android exposes through MediaStore and cannot perform forensic recovery of overwritten, inaccessible, or permanently deleted files.

- normal and trashed media exposed by the device are included
- files in `Pictures/Recovered` and `Music/Recovered` are excluded from future scans
- zero-size entries and entries without a MIME type are excluded
- large result sets are read in pages ordered by date and MediaStore ID
- duplicate content URIs are ignored, so each scan result is counted once
- the live counter reflects the number of unique results found during the scan
- progress callbacks and final count animations are ignored or cancelled when the Scan view is destroyed

MediaStore IDs are used only as stable tie-breakers when multiple items share the same date. The complete content URI is used for deduplication, so identical numeric IDs from different media collections remain separate results.
