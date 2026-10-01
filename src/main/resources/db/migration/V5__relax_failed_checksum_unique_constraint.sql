-- Allow duplicate checksums for FAILED documents to enable re-ingestion
DROP INDEX IF EXISTS ux_documents_checksum;
