UPDATE document_definition_versions
SET status = 'RETIRED',
    retired_at = '2026-10-16 15:06:00',
    active_marker = NULL
WHERE id = '018f5a00-0000-7000-8000-0000000000b2';

UPDATE document_definitions
SET enabled = FALSE,
    updated_at = '2026-10-16 15:06:00'
WHERE id = '018f5a00-0000-7000-8000-0000000000b1';
